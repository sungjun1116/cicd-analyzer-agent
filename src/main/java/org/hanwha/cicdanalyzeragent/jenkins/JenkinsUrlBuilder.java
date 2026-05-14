package org.hanwha.cicdanalyzeragent.jenkins;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.hanwha.cicdanalyzeragent.config.JenkinsProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

@Component
public class JenkinsUrlBuilder {

    private final JenkinsProperties properties;

    public JenkinsUrlBuilder(JenkinsProperties properties) {
        this.properties = properties;
    }

    public String consoleTextUrl(String jobName, int buildNumber) {
        String baseUrl = trimTrailingSlash(properties.getUrl());
        return baseUrl + "/" + jobPath(jobName) + "/" + buildNumber + "/consoleText";
    }

    public String consoleTextUrlFromBuildUrl(String buildUrl) {
        return consoleTextUrlFromBuildUrl(buildUrl, null);
    }

    public String consoleTextUrlFromBuildUrl(String buildUrl, Integer buildNumber) {
        String normalized = trimTrailingSlash(buildUrl);
        if (normalized.endsWith("/consoleText")) {
            return normalized;
        }
        if (normalized.endsWith("/console")) {
            return normalized.substring(0, normalized.length() - "/console".length()) + "/consoleText";
        }
        if (buildNumber != null && !pointsToBuild(normalized, buildNumber)) {
            return normalized + "/" + buildNumber + "/consoleText";
        }
        return normalized + "/consoleText";
    }

    private String jobPath(String jobName) {
        return Arrays.stream(jobName.split("/"))
                .filter(segment -> !segment.isBlank())
                .map(segment -> "job/" + UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));
    }

    private boolean pointsToBuild(String url, int buildNumber) {
        String lastSegment = lastPathSegment(url);
        return String.valueOf(buildNumber).equals(lastSegment) || BuildPermalink.contains(lastSegment);
    }

    private String lastPathSegment(String url) {
        String normalized = trimTrailingSlash(url);
        int queryStart = normalized.indexOf('?');
        if (queryStart >= 0) {
            normalized = normalized.substring(0, queryStart);
        }
        int fragmentStart = normalized.indexOf('#');
        if (fragmentStart >= 0) {
            normalized = normalized.substring(0, fragmentStart);
        }
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash < 0) {
            return normalized;
        }
        return normalized.substring(lastSlash + 1);
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static final class BuildPermalink {

        private static final Set<String> VALUES = Set.of(
                "lastBuild",
                "lastCompletedBuild",
                "lastFailedBuild",
                "lastStableBuild",
                "lastSuccessfulBuild",
                "lastUnstableBuild",
                "lastUnsuccessfulBuild"
        );

        private static boolean contains(String value) {
            return VALUES.contains(value);
        }
    }
}
