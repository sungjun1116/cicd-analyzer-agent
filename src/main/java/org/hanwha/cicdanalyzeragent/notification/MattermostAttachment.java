package org.hanwha.cicdanalyzeragent.notification;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MattermostAttachment(
        String title,
        String titleLink,
        String fallback,
        String color,
        List<Map<String, Object>> fields,
        String footer
) {

    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfPresent(payload, "title", title);
        putIfPresent(payload, "title_link", titleLink);
        putIfPresent(payload, "fallback", fallback);
        putIfPresent(payload, "color", color);
        if (fields != null && !fields.isEmpty()) {
            payload.put("fields", fields);
        }
        putIfPresent(payload, "footer", footer);
        return payload;
    }

    private void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value);
        }
    }
}
