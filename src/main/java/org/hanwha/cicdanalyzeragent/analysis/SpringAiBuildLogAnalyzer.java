package org.hanwha.cicdanalyzeragent.analysis;

import java.util.ArrayList;
import java.util.List;

import org.hanwha.cicdanalyzeragent.ai.AiAnalysisResponse;
import org.hanwha.cicdanalyzeragent.ai.BuildAnalysisPromptService;
import org.hanwha.cicdanalyzeragent.config.AnalyzerProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.ThinkOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnClass(ChatClient.class)
@ConditionalOnExpression(
        "'${build-analyzer.analyzer.secondary-strategy:spring-ai}'.equalsIgnoreCase('spring-ai')"
                + " || '${build-analyzer.analyzer.secondary-strategy:spring-ai}'.equalsIgnoreCase('llm')"
)
public class SpringAiBuildLogAnalyzer implements BuildLogAnalyzer {

    private final ChatClient chatClient;

    private final BuildAnalysisPromptService promptService;

    private final RuleBasedAnalyzer ruleBasedAnalyzer;

    private final AnalyzerProperties analyzerProperties;

    public SpringAiBuildLogAnalyzer(
            ChatClient.Builder chatClientBuilder,
            BuildAnalysisPromptService promptService,
            RuleBasedAnalyzer ruleBasedAnalyzer,
            AnalyzerProperties analyzerProperties
    ) {
        this.chatClient = chatClientBuilder.build();
        this.promptService = promptService;
        this.ruleBasedAnalyzer = ruleBasedAnalyzer;
        this.analyzerProperties = analyzerProperties;
    }

    @Override
    public AnalysisResult analyze(String log, String jobName, int buildNumber) {
        AnalysisResult ruleResult = ruleBasedAnalyzer.analyze(log, jobName, buildNumber);
        try {
            AiAnalysisResponse response = chatClient.prompt()
                    .system(promptService.systemPrompt())
                    .user(promptService.userPrompt(log, jobName, buildNumber, ruleResult))
                    .options(ollamaOptions())
                    .call()
                    .entity(AiAnalysisResponse.class);

            if (response == null) {
                return fallback(log, "Spring AI 분석 결과가 비어 있습니다.");
            }

            return new AnalysisResult(
                    response.errorType() == null ? ErrorType.UNKNOWN : response.errorType(),
                    defaultText(response.rootCause(), "원인 파악 실패"),
                    response.affectedFile(),
                    defaultText(response.suggestion(), "로그를 직접 확인하세요."),
                    normalizeConfidence(response.confidence()),
                    tail(log == null ? "" : log, 500),
                    List.of(name()),
                    response.evidence() == null ? List.of() : response.evidence(),
                    null
            );
        } catch (RuntimeException ex) {
            return fallback(log, "Spring AI 분석 실패: " + ex.getMessage());
        }
    }

    @Override
    public String name() {
        return "spring_ai";
    }

    private AnalysisResult fallback(String log, String reason) {
        return new AnalysisResult(
                ErrorType.UNKNOWN,
                reason,
                null,
                "AI 분석을 사용할 수 없습니다. Rule 기반 분석 결과를 우선 확인하세요.",
                0.0,
                tail(log == null ? "" : log, 500),
                List.of(name()),
                new ArrayList<>(),
                null
        );
    }

    private OllamaChatOptions ollamaOptions() {
        OllamaChatOptions.Builder builder = OllamaChatOptions.builder()
                .thinkOption(ThinkOption.ThinkBoolean.DISABLED)
                .format("json");

        if (analyzerProperties.getLlmKeepAlive() != null && !analyzerProperties.getLlmKeepAlive().isBlank()) {
            builder.keepAlive(analyzerProperties.getLlmKeepAlive());
        }

        if (analyzerProperties.getLlmNumPredict() > 0) {
            builder.numPredict(analyzerProperties.getLlmNumPredict());
        }

        return builder.build();
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private double normalizeConfidence(double confidence) {
        if (Double.isNaN(confidence)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(confidence, 1.0));
    }

    private String tail(String log, int maxLength) {
        if (log.length() <= maxLength) {
            return log;
        }
        return "..." + log.substring(log.length() - maxLength);
    }
}
