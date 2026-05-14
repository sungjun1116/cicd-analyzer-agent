package org.hanwha.cicdanalyzeragent.ai;

import java.util.List;

import org.hanwha.cicdanalyzeragent.analysis.ErrorType;

public record AiAnalysisResponse(
        ErrorType errorType,
        String rootCause,
        String affectedFile,
        String suggestion,
        double confidence,
        List<String> evidence
) {
}
