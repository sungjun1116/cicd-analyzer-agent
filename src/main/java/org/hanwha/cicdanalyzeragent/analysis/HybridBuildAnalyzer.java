package org.hanwha.cicdanalyzeragent.analysis;

import org.hanwha.cicdanalyzeragent.config.AnalyzerProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class HybridBuildAnalyzer implements BuildLogAnalyzer {

    private final RuleBasedAnalyzer ruleBasedAnalyzer;

    private final ObjectProvider<SpringAiBuildLogAnalyzer> springAiAnalyzerProvider;

    private final AnalyzerProperties analyzerProperties;

    private final AnalysisMerger analysisMerger;

    public HybridBuildAnalyzer(
            RuleBasedAnalyzer ruleBasedAnalyzer,
            ObjectProvider<SpringAiBuildLogAnalyzer> springAiAnalyzerProvider,
            AnalyzerProperties analyzerProperties,
            AnalysisMerger analysisMerger
    ) {
        this.ruleBasedAnalyzer = ruleBasedAnalyzer;
        this.springAiAnalyzerProvider = springAiAnalyzerProvider;
        this.analyzerProperties = analyzerProperties;
        this.analysisMerger = analysisMerger;
    }

    @Override
    public AnalysisResult analyze(String log, String jobName, int buildNumber) {
        AnalysisResult ruleResult = ruleBasedAnalyzer.analyze(log, jobName, buildNumber);
        if (!analyzerProperties.isSpringAiEnabled()) {
            return ruleResult;
        }

        SpringAiBuildLogAnalyzer springAiAnalyzer = springAiAnalyzerProvider.getIfAvailable();
        if (springAiAnalyzer == null) {
            return ruleResult;
        }

        try {
            AnalysisResult aiResult = springAiAnalyzer.analyze(log, jobName, buildNumber);
            if (aiResult.confidence() <= 0.0 && aiResult.errorType() == ErrorType.UNKNOWN) {
                return ruleResult;
            }
            return analysisMerger.merge(ruleResult, aiResult);
        } catch (RuntimeException ex) {
            return ruleResult;
        }
    }

    @Override
    public String name() {
        SpringAiBuildLogAnalyzer springAiAnalyzer = springAiAnalyzerProvider.getIfAvailable();
        if (analyzerProperties.isSpringAiEnabled() && springAiAnalyzer != null) {
            return "hybrid(" + ruleBasedAnalyzer.name() + "+" + springAiAnalyzer.name() + ")";
        }
        return "hybrid(" + ruleBasedAnalyzer.name() + ")";
    }
}
