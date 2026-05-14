package org.hanwha.cicdanalyzeragent.analysis;

public interface BuildLogAnalyzer {

    AnalysisResult analyze(String log, String jobName, int buildNumber);

    String name();
}
