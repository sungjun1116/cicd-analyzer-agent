package org.hanwha.cicdanalyzeragent.web;

import java.util.Map;

import org.hanwha.cicdanalyzeragent.config.AnalyzerProperties;
import org.hanwha.cicdanalyzeragent.config.JenkinsProperties;
import org.hanwha.cicdanalyzeragent.config.MattermostProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceInfoController {

    private final AnalyzerProperties analyzerProperties;

    private final JenkinsProperties jenkinsProperties;

    private final MattermostProperties mattermostProperties;

    public ServiceInfoController(
            AnalyzerProperties analyzerProperties,
            JenkinsProperties jenkinsProperties,
            MattermostProperties mattermostProperties
    ) {
        this.analyzerProperties = analyzerProperties;
        this.jenkinsProperties = jenkinsProperties;
        this.mattermostProperties = mattermostProperties;
    }

    @GetMapping("/")
    Map<String, Object> root() {
        return Map.of(
                "service", "Jenkins Build Failure Analyzer",
                "version", "0.0.1-SNAPSHOT",
                "status", "running"
        );
    }

    @GetMapping("/health")
    Map<String, Object> health() {
        return Map.of(
                "status", "healthy",
                "components", Map.of(
                        "analyzer", Map.of(
                                "strategy", analyzerProperties.getSecondaryStrategy(),
                                "status", "ok"
                        ),
                        "jenkins", Map.of(
                                "configured", jenkinsProperties.isConfigured()
                        ),
                        "mattermost", Map.of(
                                "configured", mattermostProperties.isConfigured()
                        )
                )
        );
    }

    @GetMapping("/config")
    Map<String, Object> config() {
        return Map.of(
                "analyzer", Map.of(
                        "secondary_strategy", analyzerProperties.getSecondaryStrategy(),
                        "max_log_chars", analyzerProperties.getMaxLogChars(),
                        "enable_rag", analyzerProperties.isEnableRag(),
                        "enable_memory", analyzerProperties.isEnableMemory(),
                        "enable_tools", analyzerProperties.isEnableTools()
                ),
                "jenkins", Map.of(
                        "url", blankAsPlaceholder(jenkinsProperties.getUrl()),
                        "log_max_size", jenkinsProperties.getLogMaxSize()
                ),
                "mattermost", Map.of(
                        "url", blankAsPlaceholder(mattermostProperties.getUrl()),
                        "default_channel", mattermostProperties.getDefaultChannel()
                )
        );
    }

    private String blankAsPlaceholder(String value) {
        return value == null || value.isBlank() ? "(not configured)" : value;
    }
}
