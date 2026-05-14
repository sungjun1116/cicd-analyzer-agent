package org.hanwha.cicdanalyzeragent.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.hanwha.cicdanalyzeragent.analysis.AnalysisResult;
import org.hanwha.cicdanalyzeragent.config.AnalyzerProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
public class BuildAnalysisPromptService {

    private static final String SYSTEM_PROMPT_PATH = "classpath:prompts/build-failure-system.st";

    private static final String USER_PROMPT_PATH = "classpath:prompts/build-failure-user.st";

    private final ResourceLoader resourceLoader;

    private final AnalyzerProperties analyzerProperties;

    public BuildAnalysisPromptService(ResourceLoader resourceLoader, AnalyzerProperties analyzerProperties) {
        this.resourceLoader = resourceLoader;
        this.analyzerProperties = analyzerProperties;
    }

    public String systemPrompt() {
        return readResource(SYSTEM_PROMPT_PATH);
    }

    public String userPrompt(String log, String jobName, int buildNumber, AnalysisResult ruleResult) {
        String truncatedLog = truncate(maskSecrets(log == null ? "" : log), analyzerProperties.getMaxLogChars());
        return readResource(USER_PROMPT_PATH)
                .replace("{{jobName}}", nullToEmpty(jobName))
                .replace("{{buildNumber}}", Integer.toString(buildNumber))
                .replace("{{ruleErrorType}}", ruleResult.errorType().code())
                .replace("{{ruleRootCause}}", nullToEmpty(ruleResult.rootCause()))
                .replace("{{ruleAffectedFile}}", nullToEmpty(ruleResult.affectedFile()))
                .replace("{{log}}", truncatedLog);
    }

    private String readResource(String path) {
        Resource resource = resourceLoader.getResource(path);
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read prompt resource: " + path, ex);
        }
    }

    private String truncate(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return "...(truncated)...\n" + value.substring(value.length() - maxChars);
    }

    private String maskSecrets(String value) {
        return value
                .replaceAll("(?i)(authorization:\\s*bearer\\s+)[^\\s]+", "$1***")
                .replaceAll("(?i)(token|password|secret|api[_-]?key)=([^\\s&]+)", "$1=***")
                .replaceAll("(?i)(token|password|secret|api[_-]?key):\\s*([^\\s]+)", "$1: ***");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
