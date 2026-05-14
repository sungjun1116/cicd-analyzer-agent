package org.hanwha.cicdanalyzeragent.jenkins;

public record FetchedLog(
        String fullLog,
        String errorLines,
        int logSize,
        boolean truncated
) {
}
