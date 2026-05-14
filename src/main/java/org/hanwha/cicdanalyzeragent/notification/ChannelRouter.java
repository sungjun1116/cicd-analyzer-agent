package org.hanwha.cicdanalyzeragent.notification;

import org.hanwha.cicdanalyzeragent.config.MattermostProperties;
import org.springframework.stereotype.Component;

@Component
public class ChannelRouter {

    private final MattermostProperties properties;

    public ChannelRouter(MattermostProperties properties) {
        this.properties = properties;
    }

    public TargetChannel getChannelForJob(String jobName) {
        if (hasText(properties.getChannelId())) {
            return new TargetChannel(properties.getChannelId(), null);
        }

        String jobLower = jobName == null ? "" : jobName.toLowerCase();
        for (MattermostProperties.ChannelRoutingRule rule : properties.getChannelRouting()) {
            for (String pattern : rule.getPatterns()) {
                if (pattern != null && jobLower.contains(pattern.toLowerCase())) {
                    if (hasText(rule.getChannelId())) {
                        return new TargetChannel(rule.getChannelId(), null);
                    }
                    if (hasText(rule.getChannel())) {
                        return new TargetChannel(null, rule.getChannel());
                    }
                }
            }
        }

        if (hasText(properties.getDefaultChannelId())) {
            return new TargetChannel(properties.getDefaultChannelId(), null);
        }
        return new TargetChannel(null, properties.getDefaultChannel());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
