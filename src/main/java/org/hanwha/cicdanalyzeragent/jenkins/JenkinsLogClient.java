package org.hanwha.cicdanalyzeragent.jenkins;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.hanwha.cicdanalyzeragent.config.JenkinsProperties;
import org.hanwha.cicdanalyzeragent.config.RestClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class JenkinsLogClient {

    private static final Logger log = LoggerFactory.getLogger(JenkinsLogClient.class);

    private static final Pattern ERROR_PATTERN = Pattern.compile(
            String.join("|", List.of(
                    "^\\[ERROR\\]",
                    "^ERROR:",
                    "^error:",
                    "FAILURE",
                    "FAILED",
                    "Exception:",
                    "Traceback \\(most recent call last\\)",
                    "^\\s+at\\s+[\\w.$]+\\(",
                    "^\\s+File \"",
                    "npm ERR!",
                    "SyntaxError:",
                    "TypeError:",
                    "ModuleNotFoundError:",
                    "ImportError:",
                    "CompileError:",
                    "Build failed"
            )),
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private final JenkinsProperties properties;

    private final JenkinsUrlBuilder urlBuilder;

    private final RestClient restClient;

    @Autowired
    public JenkinsLogClient(
            JenkinsProperties properties,
            JenkinsUrlBuilder urlBuilder,
            RestClient.Builder restClientBuilder
    ) {
        this(
                properties,
                urlBuilder,
                restClientBuilder.clone()
                        .requestFactory(RestClientConfig.requestFactory(
                                RestClientConfig.seconds(properties.getTimeoutSeconds()),
                                RestClientConfig.seconds(properties.getTimeoutSeconds())
                        ))
                        .build()
        );
    }

    JenkinsLogClient(
            JenkinsProperties properties,
            JenkinsUrlBuilder urlBuilder,
            RestClient restClient
    ) {
        this.properties = properties;
        this.urlBuilder = urlBuilder;
        this.restClient = restClient;
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public boolean canFetchLog(String buildUrl) {
        return hasText(buildUrl) || properties.hasBaseUrl();
    }

    public FetchedLog fetchFullLog(String jobName, Integer buildNumber, String buildUrl) {
        if (!canFetchLog(buildUrl)) {
            throw new IllegalStateException("Jenkins is not configured. Set JENKINS_URL or provide build.url in the payload.");
        }

        List<String> urls = candidateConsoleTextUrls(jobName, buildNumber, buildUrl);
        log.debug("Jenkins console log fetch candidates prepared. job={}, build={}, urls={}", jobName, buildNumber, urls);
        String fullLog = null;
        String successfulUrl = null;
        List<String> notFoundUrls = new ArrayList<>();
        for (String url : urls) {
            try {
                log.debug("Fetching Jenkins console log. job={}, build={}, url={}", jobName, buildNumber, url);
                fullLog = fetchLog(url);
                successfulUrl = url;
                break;
            } catch (HttpClientErrorException.NotFound ex) {
                notFoundUrls.add(url);
                log.debug("Jenkins console log was not found. url={}", url);
            }
        }

        if (fullLog == null) {
            throw new IllegalStateException(
                    "Jenkins console log was not found. attemptedUrls=" + notFoundUrls
                            + ". Check build.url, job_name, build.number, and Jenkins folder path."
            );
        }

        int originalSize = fullLog.length();
        boolean truncated = false;
        if (originalSize > properties.getLogMaxSize()) {
            fullLog = fullLog.substring(originalSize - properties.getLogMaxSize());
            truncated = true;
        }

        log.debug(
                "Jenkins console log fetched. job={}, build={}, url={}, originalChars={}, returnedChars={}, truncated={}",
                jobName,
                buildNumber,
                successfulUrl,
                originalSize,
                fullLog.length(),
                truncated
        );

        return new FetchedLog(fullLog, extractErrorLines(fullLog, 3), originalSize, truncated);
    }

    private String fetchLog(String url) {
        String fullLog = restClient.get()
                .uri(URI.create(url))
                .headers(headers -> {
                    if (properties.hasCredentials()) {
                        headers.setBasicAuth(properties.getUser(), properties.getToken(), StandardCharsets.UTF_8);
                    }
                })
                .retrieve()
                .body(String.class);

        if (fullLog == null) {
            fullLog = "";
        }
        return fullLog;
    }

    private List<String> candidateConsoleTextUrls(String jobName, Integer buildNumber, String buildUrl) {
        Set<String> urls = new LinkedHashSet<>();
        if (hasText(buildUrl)) {
            urls.add(urlBuilder.consoleTextUrlFromBuildUrl(buildUrl, buildNumber));
        }
        if (properties.hasBaseUrl() && hasText(jobName) && buildNumber != null) {
            urls.add(urlBuilder.consoleTextUrl(jobName, buildNumber));
        }
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("Either buildUrl or jobName/buildNumber is required.");
        }
        return new ArrayList<>(urls);
    }

    public String fetchErrorLog(String jobName, Integer buildNumber, String buildUrl) {
        return fetchFullLog(jobName, buildNumber, buildUrl).errorLines();
    }

    String extractErrorLines(String log, int contextLines) {
        String[] lines = log.split("\\R", -1);
        Set<Integer> indices = new TreeSet<>();

        for (int i = 0; i < lines.length; i++) {
            if (ERROR_PATTERN.matcher(lines[i]).find()) {
                int start = Math.max(0, i - contextLines);
                int end = Math.min(lines.length, i + contextLines + 1);
                for (int j = start; j < end; j++) {
                    indices.add(j);
                }
            }
        }

        if (indices.isEmpty()) {
            int start = Math.max(0, lines.length - 50);
            return String.join("\n", List.of(lines).subList(start, lines.length));
        }

        List<String> result = new ArrayList<>();
        int previous = -2;
        for (Integer index : indices) {
            if (index > previous + 1 && !result.isEmpty()) {
                result.add("...");
            }
            result.add(lines[index]);
            previous = index;
        }
        return String.join("\n", result);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
