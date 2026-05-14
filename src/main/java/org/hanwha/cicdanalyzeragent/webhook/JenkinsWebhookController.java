package org.hanwha.cicdanalyzeragent.webhook;

import org.hanwha.cicdanalyzeragent.analysis.AnalysisResult;
import org.hanwha.cicdanalyzeragent.processing.BuildFailureProcessor;
import org.hanwha.cicdanalyzeragent.webhook.dto.AnalysisResponse;
import org.hanwha.cicdanalyzeragent.webhook.dto.BuildStatus;
import org.hanwha.cicdanalyzeragent.webhook.dto.JenkinsBuildEvent;
import org.hanwha.cicdanalyzeragent.webhook.dto.WebhookResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/webhook")
public class JenkinsWebhookController {

    private static final Logger log = LoggerFactory.getLogger(JenkinsWebhookController.class);

    private final BuildFailureProcessor processor;

    public JenkinsWebhookController(BuildFailureProcessor processor) {
        this.processor = processor;
    }

    @PostMapping("/jenkins")
    public WebhookResponse handleJenkinsWebhook(@Valid @RequestBody JenkinsBuildEvent event) {
        log.debug(
                "Received Jenkins webhook. job={}, build={}, status={}, buildUrl={}, hasPayloadErrorLog={}",
                event.jobName(),
                event.build().number(),
                event.build().status(),
                event.build().url(),
                event.errorLog() != null && !event.errorLog().isBlank()
        );

        if (event.build().status() != BuildStatus.FAILURE) {
            log.debug(
                    "Skipping Jenkins webhook because build status is not FAILURE. job={}, build={}, status={}",
                    event.jobName(),
                    event.build().number(),
                    event.build().status()
            );
            return new WebhookResponse(
                    "skipped",
                    "Build status is " + event.build().status() + ", not FAILURE",
                    event.jobName(),
                    event.build().number()
            );
        }

        processor.processFailureAsync(event);
        log.info(
                "Accepted Jenkins failure webhook for asynchronous processing. job={}, build={}",
                event.jobName(),
                event.build().number()
        );
        return new WebhookResponse(
                "accepted",
                "Build failure analysis scheduled",
                event.jobName(),
                event.build().number()
        );
    }

    @PostMapping("/jenkins/sync")
    public AnalysisResponse handleJenkinsWebhookSync(@Valid @RequestBody JenkinsBuildEvent event) {
        log.debug(
                "Received synchronous Jenkins webhook. job={}, build={}, status={}, buildUrl={}, hasPayloadErrorLog={}",
                event.jobName(),
                event.build().number(),
                event.build().status(),
                event.build().url(),
                event.errorLog() != null && !event.errorLog().isBlank()
        );

        if (event.build().status() != BuildStatus.FAILURE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Build status is " + event.build().status() + ", not FAILURE"
            );
        }

        AnalysisResult result = processor.analyzeEvent(event);
        log.info(
                "Completed synchronous Jenkins webhook. job={}, build={}, errorType={}, confidence={}",
                event.jobName(),
                event.build().number(),
                result.errorType(),
                result.confidence()
        );
        return processor.toResponse(event.jobName(), event.build().number(), result);
    }

    @PostMapping("/analyze/{jobName}/{buildNumber}")
    public AnalysisResponse analyzeBuild(@PathVariable String jobName, @PathVariable int buildNumber) {
        log.debug("Received manual Jenkins build analysis request. job={}, build={}", jobName, buildNumber);
        AnalysisResult result = processor.analyzeBuild(jobName, buildNumber);
        log.info(
                "Completed manual Jenkins build analysis request. job={}, build={}, errorType={}, confidence={}",
                jobName,
                buildNumber,
                result.errorType(),
                result.confidence()
        );
        return processor.toResponse(jobName, buildNumber, result);
    }
}
