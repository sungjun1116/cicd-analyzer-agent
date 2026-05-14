package org.hanwha.cicdanalyzeragent.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "build-analyzer.mattermost")
public class MattermostProperties {

    private String url = "";

    private String token = "";

    private String channelId = "";

    private String defaultChannelId = "";

    private String defaultChannel = "build-alerts";

    private int timeoutSeconds = 10;

    private List<ChannelRoutingRule> channelRouting = new ArrayList<>();

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getDefaultChannelId() {
        return defaultChannelId;
    }

    public void setDefaultChannelId(String defaultChannelId) {
        this.defaultChannelId = defaultChannelId;
    }

    public String getDefaultChannel() {
        return defaultChannel;
    }

    public void setDefaultChannel(String defaultChannel) {
        this.defaultChannel = defaultChannel;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public List<ChannelRoutingRule> getChannelRouting() {
        return channelRouting;
    }

    public void setChannelRouting(List<ChannelRoutingRule> channelRouting) {
        this.channelRouting = channelRouting;
    }

    public boolean isConfigured() {
        return hasText(url) && hasText(token);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static class ChannelRoutingRule {

        private List<String> patterns = new ArrayList<>();

        private String channel = "";

        private String channelId = "";

        public List<String> getPatterns() {
            return patterns;
        }

        public void setPatterns(List<String> patterns) {
            this.patterns = patterns;
        }

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }

        public String getChannelId() {
            return channelId;
        }

        public void setChannelId(String channelId) {
            this.channelId = channelId;
        }
    }
}
