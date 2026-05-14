package org.hanwha.cicdanalyzeragent.analysis;

import java.util.List;

public record AnalysisResult(
        ErrorType errorType,
        String rootCause,
        String affectedFile,
        String suggestion,
        double confidence,
        String rawSnippet,
        List<String> analyzerChain,
        List<String> evidence,
        ErrorType alternativeErrorType
) {

    public AnalysisResult {
        analyzerChain = analyzerChain == null ? List.of() : List.copyOf(analyzerChain);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public static AnalysisResult of(
            ErrorType errorType,
            String rootCause,
            String affectedFile,
            String suggestion,
            double confidence,
            String rawSnippet,
            List<String> analyzerChain
    ) {
        return new AnalysisResult(
                errorType,
                rootCause,
                affectedFile,
                suggestion,
                confidence,
                rawSnippet,
                analyzerChain,
                List.of(),
                null
        );
    }
}
