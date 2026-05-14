package org.hanwha.cicdanalyzeragent.processing;

import java.util.concurrent.CompletableFuture;

import org.hanwha.cicdanalyzeragent.analysis.AnalysisResult;
import org.hanwha.cicdanalyzeragent.analysis.HybridBuildAnalyzer;
import org.hanwha.cicdanalyzeragent.jenkins.JenkinsLogClient;
import org.hanwha.cicdanalyzeragent.notification.ChannelRouter;
import org.hanwha.cicdanalyzeragent.notification.MattermostAttachment;
import org.hanwha.cicdanalyzeragent.notification.MattermostClient;
import org.hanwha.cicdanalyzeragent.notification.TargetChannel;
import org.hanwha.cicdanalyzeragent.report.BuildFailureMessageRenderer;
import org.hanwha.cicdanalyzeragent.webhook.dto.AnalysisResponse;
import org.hanwha.cicdanalyzeragent.webhook.dto.JenkinsBuildEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BuildFailureProcessor {

    private static final Logger log = LoggerFactory.getLogger(BuildFailureProcessor.class);

    private final JenkinsLogClient jenkinsLogClient;

    private final HybridBuildAnalyzer analyzer;

    private final MattermostClient mattermostClient;

    private final ChannelRouter channelRouter;

    private final BuildFailureMessageRenderer messageRenderer;

    public BuildFailureProcessor(
            JenkinsLogClient jenkinsLogClient,
            HybridBuildAnalyzer analyzer,
            MattermostClient mattermostClient,
            ChannelRouter channelRouter,
            BuildFailureMessageRenderer messageRenderer
    ) {
        this.jenkinsLogClient = jenkinsLogClient;
        this.analyzer = analyzer;
        this.mattermostClient = mattermostClient;
        this.channelRouter = channelRouter;
        this.messageRenderer = messageRenderer;
    }

    @Async("buildFailureTaskExecutor")
    public CompletableFuture<Void> processFailureAsync(JenkinsBuildEvent event) {
        long startedAt = System.nanoTime();
        log.debug(
                "Build failure processing started. job={}, build={}, buildUrl={}",
                event.jobName(),
                event.build().number(),
                event.build().url()
        );
        try {
            AnalysisResult result = analyzeEvent(event);
            log.debug(
                    "Build failure analysis completed. job={}, build={}, errorType={}, confidence={}, analyzers={}",
                    event.jobName(),
                    event.build().number(),
                    result.errorType(),
                    result.confidence(),
                    result.analyzerChain()
            );

            log.debug("Checking Mattermost configuration and health. job={}, build={}", event.jobName(), event.build().number());
            if (mattermostClient.healthCheck()) {
                TargetChannel targetChannel = channelRouter.getChannelForJob(event.jobName());
                log.debug(
                        "Resolved Mattermost target channel. job={}, build={}, channelId={}, channelName={}",
                        event.jobName(),
                        event.build().number(),
                        maskChannelId(targetChannel.channelId()),
                        targetChannel.channelName()
                );
                MattermostAttachment attachment = messageRenderer.render(event, result);
                boolean sent = mattermostClient.sendAttachment(targetChannel, attachment);
                log.info(
                        "Mattermost notification send result. job={}, build={}, sent={}",
                        event.jobName(),
                        event.build().number(),
                        sent
                );
            } else {
                log.debug(
                        "Skipping Mattermost notification because health check failed or Mattermost is not configured. job={}, build={}",
                        event.jobName(),
                        event.build().number()
                );
            }
            log.info(
                    "Build failure processing finished. job={}, build={}, elapsedMs={}",
                    event.jobName(),
                    event.build().number(),
                    elapsedMs(startedAt)
            );
        } catch (RuntimeException ex) {
            log.error(
                    "Failed to process build failure. job={}, build={}, elapsedMs={}",
                    event.jobName(),
                    event.build().number(),
                    elapsedMs(startedAt),
                    ex
            );
        }
        return CompletableFuture.completedFuture(null);
    }

    public AnalysisResult analyzeEvent(JenkinsBuildEvent event) {
        log.debug("Resolving error log. job={}, build={}", event.jobName(), event.build().number());
        String errorLog = getErrorLog(event);
        if (errorLog == null || errorLog.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to get error log. Provide error_log in payload, build.url, or configure JENKINS_URL."
            );
        }
        log.debug(
                "Error log resolved. job={}, build={}, chars={}",
                event.jobName(),
                event.build().number(),
                errorLog.length()
        );

        long startedAt = System.nanoTime();
        log.debug("Analyzing build failure log. job={}, build={}", event.jobName(), event.build().number());
        AnalysisResult result = analyzer.analyze(errorLog, event.jobName(), event.build().number());
        log.debug(
                "Analyzer returned result. job={}, build={}, errorType={}, confidence={}, elapsedMs={}",
                event.jobName(),
                event.build().number(),
                result.errorType(),
                result.confidence(),
                elapsedMs(startedAt)
        );
        return result;
    }

    public AnalysisResult analyzeBuild(String jobName, int buildNumber) {
        if (!jenkinsLogClient.isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Jenkins API is not configured. Set JENKINS_URL."
            );
        }
        log.debug("Fetching Jenkins log for manual build analysis. job={}, build={}", jobName, buildNumber);
        String errorLog = jenkinsLogClient.fetchErrorLog(jobName, buildNumber, null);
        log.debug("Jenkins log fetched for manual build analysis. job={}, build={}, chars={}", jobName, buildNumber, errorLog.length());
        AnalysisResult result = analyzer.analyze(errorLog, jobName, buildNumber);
        log.info(
                "Manual build analysis completed. job={}, build={}, errorType={}, confidence={}",
                jobName,
                buildNumber,
                result.errorType(),
                result.confidence()
        );
        return result;
    }

    public AnalysisResponse toResponse(String jobName, int buildNumber, AnalysisResult result) {
        return new AnalysisResponse(
                jobName,
                buildNumber,
                result.errorType().code(),
                result.rootCause(),
                result.affectedFile(),
                result.suggestion(),
                result.confidence(),
                result.analyzerChain(),
                result.evidence(),
                result.alternativeErrorType() == null ? null : result.alternativeErrorType().code()
        );
    }

    private String getErrorLog(JenkinsBuildEvent event) {
        if (event.errorLog() != null && !event.errorLog().isBlank()) {
            log.debug(
                    "Using error_log from webhook payload. job={}, build={}, chars={}",
                    event.jobName(),
                    event.build().number(),
                    event.errorLog().length()
            );
            return event.errorLog();
        }
        if (!jenkinsLogClient.canFetchLog(event.build().url())) {
            log.warn(
                    "Cannot fetch Jenkins log because neither build.url nor JENKINS_URL is configured. job={}, build={}",
                    event.jobName(),
                    event.build().number()
            );
            return null;
        }
        log.debug(
                "Fetching Jenkins console log through Jenkins API. job={}, build={}, buildUrl={}",
                event.jobName(),
                event.build().number(),
                event.build().url()
        );
        return jenkinsLogClient.fetchErrorLog(
                event.jobName(),
                event.build().number(),
                event.build().url()
        );
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String maskChannelId(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return null;
        }
        if (channelId.length() <= 8) {
            return "****";
        }
        return channelId.substring(0, 4) + "..." + channelId.substring(channelId.length() - 4);
    }
}
