package org.hanwha.cicdanalyzeragent.webhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WebhookResponse(
        String status,
        String message,
        @JsonProperty("job_name") String jobName,
        @JsonProperty("build_number") Integer buildNumber
) {
}
