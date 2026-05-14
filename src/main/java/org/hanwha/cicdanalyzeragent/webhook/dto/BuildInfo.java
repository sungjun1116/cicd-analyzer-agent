package org.hanwha.cicdanalyzeragent.webhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BuildInfo(
        @NotNull Integer number,
        @NotNull BuildStatus status,
        @NotBlank String url,
        @JsonProperty("duration_ms") long durationMs
) {
}
