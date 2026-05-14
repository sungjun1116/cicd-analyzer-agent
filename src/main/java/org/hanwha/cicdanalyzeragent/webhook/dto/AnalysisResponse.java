package org.hanwha.cicdanalyzeragent.webhook.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AnalysisResponse(
        @JsonProperty("job_name") String jobName,
        @JsonProperty("build_number") int buildNumber,
        @JsonProperty("error_type") String errorType,
        @JsonProperty("root_cause") String rootCause,
        @JsonProperty("affected_file") String affectedFile,
        String suggestion,
        double confidence,
        @JsonProperty("analyzer_chain") List<String> analyzerChain,
        List<String> evidence,
        @JsonProperty("alternative_error_type") String alternativeErrorType
) {
}
