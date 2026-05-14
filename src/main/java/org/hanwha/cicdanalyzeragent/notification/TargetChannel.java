package org.hanwha.cicdanalyzeragent.notification;

public record TargetChannel(String channelId, String channelName) {

    public boolean hasChannelId() {
        return channelId != null && !channelId.isBlank();
    }

    public boolean hasChannelName() {
        return channelName != null && !channelName.isBlank();
    }
}
