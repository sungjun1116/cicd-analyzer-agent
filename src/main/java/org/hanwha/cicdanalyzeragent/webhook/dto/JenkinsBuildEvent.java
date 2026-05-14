package org.hanwha.cicdanalyzeragent.webhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JenkinsBuildEvent(
        @JsonProperty("job_name") @NotBlank String jobName,
        @Valid @NotNull BuildInfo build,
        String timestamp,
        ScmInfo scm,
        @JsonProperty("error_log") String errorLog
) {
}
