package org.hanwha.cicdanalyzeragent.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.hanwha.cicdanalyzeragent.config.MattermostProperties;
import org.junit.jupiter.api.Test;

class ChannelRouterTest {

    @Test
    void returnsMatchingChannelId() {
        MattermostProperties properties = new MattermostProperties();
        MattermostProperties.ChannelRoutingRule rule = new MattermostProperties.ChannelRoutingRule();
        rule.setPatterns(List.of("mobile"));
        rule.setChannelId("mobile-channel-id");
        properties.setChannelRouting(List.of(rule));
        ChannelRouter router = new ChannelRouter(properties);

        TargetChannel target = router.getChannelForJob("my-mobile-build");

        assertThat(target.channelId()).isEqualTo("mobile-channel-id");
        assertThat(target.channelName()).isNull();
    }

    @Test
    void fallsBackToDefaultChannelName() {
        MattermostProperties properties = new MattermostProperties();
        properties.setDefaultChannel("build-alerts");
        ChannelRouter router = new ChannelRouter(properties);

        TargetChannel target = router.getChannelForJob("backend-build");

        assertThat(target.channelId()).isNull();
        assertThat(target.channelName()).isEqualTo("build-alerts");
    }
}
