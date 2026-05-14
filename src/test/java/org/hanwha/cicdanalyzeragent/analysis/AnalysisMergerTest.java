package org.hanwha.cicdanalyzeragent.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class AnalysisMergerTest {

    private final AnalysisMerger merger = new AnalysisMerger();

    @Test
    void usesSecondaryWhenPrimaryIsUnknown() {
        AnalysisResult primary = AnalysisResult.of(
                ErrorType.UNKNOWN,
                "unknown",
                null,
                "check log",
                0.0,
                "snippet",
                List.of("rule_based")
        );
        AnalysisResult secondary = AnalysisResult.of(
                ErrorType.NETWORK_ERROR,
                "DNS failed",
                null,
                "check DNS",
                0.9,
                "other",
                List.of("spring_ai")
        );

        AnalysisResult merged = merger.merge(primary, secondary);

        assertThat(merged.errorType()).isEqualTo(ErrorType.NETWORK_ERROR);
        assertThat(merged.rawSnippet()).isEqualTo("snippet");
        assertThat(merged.analyzerChain()).containsExactly("rule_based", "spring_ai");
    }

    @Test
    void preservesPrimaryErrorTypeWhenRuleMatched() {
        AnalysisResult primary = AnalysisResult.of(
                ErrorType.COMPILE_ERROR,
                "compile error",
                "App.java:42",
                "check import",
                0.85,
                "snippet",
                List.of("rule_based")
        );
        AnalysisResult secondary = AnalysisResult.of(
                ErrorType.DEPENDENCY_ERROR,
                "dependency may be missing",
                null,
                "add dependency",
                0.95,
                "other",
                List.of("spring_ai")
        );

        AnalysisResult merged = merger.merge(primary, secondary);

        assertThat(merged.errorType()).isEqualTo(ErrorType.COMPILE_ERROR);
        assertThat(merged.alternativeErrorType()).isEqualTo(ErrorType.DEPENDENCY_ERROR);
        assertThat(merged.suggestion()).isEqualTo("add dependency");
        assertThat(merged.confidence()).isEqualTo(0.85 * 0.4 + 0.95 * 0.6);
    }
}
