package org.hanwha.cicdanalyzeragent.ai;

import org.hanwha.cicdanalyzeragent.analysis.ErrorType;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class BuildAnalysisTools {

    @Tool(description = "Return a short runbook hint for a CI build error type. This tool is read-only.")
    public String getRunbook(String errorType, String buildTool) {
        ErrorType type = ErrorType.from(errorType);
        return switch (type) {
            case DEPENDENCY_ERROR -> "Check dependency coordinates, repository availability, credentials, and recent artifact publishing failures.";
            case OOM_ERROR -> "Check JVM heap settings, container memory limits, Gradle daemon memory, and recent memory-heavy tests.";
            case NETWORK_ERROR -> "Check DNS, firewall rules, external service availability, proxy settings, and repository uptime.";
            case PERMISSION_ERROR -> "Check token validity, bot permissions, repository access, and expired credentials.";
            case DISK_SPACE_ERROR -> "Check workspace cleanup, artifact retention, Docker image cleanup, and disk quota.";
            case TEST_FAILURE -> "Check failed assertions, test data, mocks, flaky tests, and recently changed test dependencies.";
            case COMPILE_ERROR -> "Check imports, generated sources, annotation processors, dependency scopes, and changed Java APIs.";
            case UNKNOWN -> "Inspect the raw log tail and compare with recent failures for the same job.";
        };
    }

    @Tool(description = "Return a placeholder for recent similar failures. This tool is read-only.")
    public String getRecentSimilarFailures(String jobName, String errorType) {
        return "No persisted failure history is available yet for job=" + jobName + ", errorType=" + errorType + ".";
    }
}
