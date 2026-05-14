package org.hanwha.cicdanalyzeragent.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hanwha.cicdanalyzeragent.analysis.AnalysisResult;
import org.hanwha.cicdanalyzeragent.notification.MattermostAttachment;
import org.hanwha.cicdanalyzeragent.webhook.dto.JenkinsBuildEvent;
import org.hanwha.cicdanalyzeragent.webhook.dto.ScmInfo;
import org.springframework.stereotype.Component;

@Component
public class BuildFailureMessageRenderer {

    public MattermostAttachment render(JenkinsBuildEvent event, AnalysisResult result) {
        int confidencePercent = (int) Math.round(result.confidence() * 100);
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(field("에러 유형", "`" + result.errorType().code() + "`", true));
        fields.add(field("신뢰도", confidencePercent + "%", true));
        fields.add(field("원인", result.rootCause(), false));
        if (hasText(result.affectedFile())) {
            fields.add(field("파일", "`" + result.affectedFile() + "`", true));
        }
        if (result.alternativeErrorType() != null) {
            fields.add(field("AI 대체 분류", "`" + result.alternativeErrorType().code() + "`", true));
        }
        fields.add(field("해결 방법", result.suggestion(), false));

        if (hasText(result.rawSnippet())) {
            fields.add(field("로그 스니펫", "```\n" + abbreviate(result.rawSnippet(), 400) + "\n```", false));
        }
        appendScm(fields, event.scm());
        if (!result.evidence().isEmpty()) {
            fields.add(field("근거", String.join("\n", result.evidence()), false));
        }

        String footer = result.analyzerChain().isEmpty()
                ? null
                : "analysis engine: " + String.join(" + ", result.analyzerChain());

        return new MattermostAttachment(
                "Build failed: " + event.jobName() + " #" + event.build().number(),
                event.build().url(),
                "Build failed: " + event.jobName() + " #" + event.build().number() + " - " + result.errorType().code(),
                color(result.confidence()),
                fields,
                footer
        );
    }

    private Map<String, Object> field(String title, String value, boolean isShort) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("title", title);
        field.put("value", value == null ? "" : value);
        field.put("short", isShort);
        return field;
    }

    private void appendScm(List<Map<String, Object>> fields, ScmInfo scmInfo) {
        if (scmInfo == null) {
            return;
        }
        List<String> parts = new ArrayList<>();
        if (hasText(scmInfo.branch())) {
            parts.add("Branch: `" + scmInfo.branch() + "`");
        }
        if (hasText(scmInfo.commit())) {
            parts.add("Commit: `" + scmInfo.commit().substring(0, Math.min(8, scmInfo.commit().length())) + "`");
        }
        if (hasText(scmInfo.author())) {
            parts.add("Author: " + scmInfo.author());
        }
        if (!parts.isEmpty()) {
            fields.add(field("SCM", String.join(" | ", parts), false));
        }
    }

    private String color(double confidence) {
        if (confidence >= 0.8) {
            return "#FF0000";
        }
        if (confidence >= 0.5) {
            return "#FFA500";
        }
        return "#FFCC00";
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
