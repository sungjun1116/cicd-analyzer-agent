package org.hanwha.cicdanalyzeragent.analysis;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class AnalysisMerger {

    public AnalysisResult merge(AnalysisResult primary, AnalysisResult secondary) {
        List<String> analyzerChain = new ArrayList<>();
        analyzerChain.addAll(primary.analyzerChain());
        analyzerChain.addAll(secondary.analyzerChain());

        if (primary.errorType() == ErrorType.UNKNOWN) {
            return new AnalysisResult(
                    secondary.errorType(),
                    secondary.rootCause(),
                    firstNonBlank(secondary.affectedFile(), primary.affectedFile()),
                    secondary.suggestion(),
                    secondary.confidence(),
                    primary.rawSnippet(),
                    analyzerChain,
                    secondary.evidence(),
                    null
            );
        }

        double mergedConfidence = Math.min((primary.confidence() * 0.4) + (secondary.confidence() * 0.6), 1.0);
        String rootCause = isMoreDetailed(secondary.rootCause(), primary.rootCause())
                ? secondary.rootCause()
                : primary.rootCause();
        ErrorType alternative = secondary.errorType() != ErrorType.UNKNOWN && secondary.errorType() != primary.errorType()
                ? secondary.errorType()
                : null;

        return new AnalysisResult(
                primary.errorType(),
                rootCause,
                firstNonBlank(primary.affectedFile(), secondary.affectedFile()),
                firstNonBlank(secondary.suggestion(), primary.suggestion()),
                mergedConfidence,
                primary.rawSnippet(),
                analyzerChain,
                secondary.evidence(),
                alternative
        );
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private boolean isMoreDetailed(String candidate, String fallback) {
        return candidate != null
                && !candidate.isBlank()
                && (fallback == null || candidate.length() > fallback.length());
    }
}
