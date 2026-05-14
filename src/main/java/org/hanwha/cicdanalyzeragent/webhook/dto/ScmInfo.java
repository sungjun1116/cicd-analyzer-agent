package org.hanwha.cicdanalyzeragent.webhook.dto;

public record ScmInfo(
        String branch,
        String commit,
        String author,
        String message
) {
}
