package org.hanwha.cicdanalyzeragent.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuleBasedAnalyzerTest {

    private final RuleBasedAnalyzer analyzer = new RuleBasedAnalyzer();

    @Test
    void detectsCompileError() {
        AnalysisResult result = analyzer.analyze(
                "/app/src/main/java/App.java:42: error: cannot find symbol",
                "test-job",
                1
        );

        assertThat(result.errorType()).isEqualTo(ErrorType.COMPILE_ERROR);
        assertThat(result.affectedFile()).isEqualTo("/app/src/main/java/App.java:42");
        assertThat(result.analyzerChain()).containsExactly("rule_based");
    }

    @Test
    void detectsDependencyError() {
        AnalysisResult result = analyzer.analyze(
                "[ERROR] Could not resolve dependencies for project sample",
                "test-job",
                2
        );

        assertThat(result.errorType()).isEqualTo(ErrorType.DEPENDENCY_ERROR);
        assertThat(result.confidence()).isEqualTo(0.85);
    }

    @Test
    void fallsBackToUnknown() {
        AnalysisResult result = analyzer.analyze("plain log line", "test-job", 3);

        assertThat(result.errorType()).isEqualTo(ErrorType.UNKNOWN);
        assertThat(result.confidence()).isZero();
    }
}
