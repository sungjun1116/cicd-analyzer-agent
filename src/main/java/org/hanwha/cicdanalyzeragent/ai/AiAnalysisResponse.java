package org.hanwha.cicdanalyzeragent.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.hanwha.cicdanalyzeragent.analysis.ErrorType;

public record AiAnalysisResponse(
        ErrorType errorType,
        String rootCause,
        @JsonDeserialize(using = FlexibleStringDeserializer.class)
        String affectedFile,
        String suggestion,
        double confidence,
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<String> evidence
) {
}
