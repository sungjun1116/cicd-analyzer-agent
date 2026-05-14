package org.hanwha.cicdanalyzeragent.notification;

import java.util.List;
import java.util.Map;

import org.hanwha.cicdanalyzeragent.config.MattermostProperties;
import org.hanwha.cicdanalyzeragent.config.RestClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MattermostClient {

    private static final Logger log = LoggerFactory.getLogger(MattermostClient.class);

    private final MattermostProperties properties;

    private final RestClient restClient;

    public MattermostClient(MattermostProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.clone()
                .requestFactory(RestClientConfig.requestFactory(
                        RestClientConfig.seconds(properties.getTimeoutSeconds()),
                        RestClientConfig.seconds(properties.getTimeoutSeconds())
                ))
                .build();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public boolean healthCheck() {
        if (!isConfigured()) {
            log.debug("Mattermost health check skipped because Mattermost is not configured.");
            return false;
        }
        try {
            log.debug("Checking Mattermost API health. url={}", properties.getUrl());
            restClient.get()
                    .uri(properties.getUrl() + "/users/me")
                    .headers(headers -> headers.setBearerAuth(properties.getToken()))
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Mattermost API health check succeeded.");
            return true;
        } catch (RuntimeException ex) {
            log.warn("Mattermost API health check failed. url={}", properties.getUrl(), ex);
            return false;
        }
    }

    public boolean sendAttachment(TargetChannel targetChannel, MattermostAttachment attachment) {
        if (!isConfigured()) {
            log.debug("Mattermost send skipped because Mattermost is not configured.");
            return false;
        }

        String channelId = resolveChannelId(targetChannel);
        if (channelId == null || channelId.isBlank()) {
            log.warn(
                    "Mattermost send skipped because target channel could not be resolved. channelId={}, channelName={}",
                    targetChannel == null ? null : maskChannelId(targetChannel.channelId()),
                    targetChannel == null ? null : targetChannel.channelName()
            );
            return false;
        }

        Map<String, Object> payload = Map.of(
                "channel_id", channelId,
                "message", "",
                "props", Map.of("attachments", List.of(attachment.toMap()))
        );

        try {
            log.debug("Sending Mattermost attachment. channelId={}", maskChannelId(channelId));
            restClient.post()
                    .uri(properties.getUrl() + "/posts")
                    .headers(headers -> headers.setBearerAuth(properties.getToken()))
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Mattermost attachment sent. channelId={}", maskChannelId(channelId));
            return true;
        } catch (RuntimeException ex) {
            log.warn("Mattermost attachment send failed. channelId={}", maskChannelId(channelId), ex);
            return false;
        }
    }

    private String resolveChannelId(TargetChannel targetChannel) {
        if (targetChannel == null) {
            return null;
        }
        if (targetChannel.hasChannelId()) {
            return targetChannel.channelId();
        }
        if (!targetChannel.hasChannelName()) {
            return null;
        }
        return findChannelIdByName(targetChannel.channelName());
    }

    private String findChannelIdByName(String channelName) {
        try {
            log.debug("Resolving Mattermost channel by name. channelName={}", channelName);
            List<Map<String, Object>> teams = restClient.get()
                    .uri(properties.getUrl() + "/teams")
                    .headers(headers -> headers.setBearerAuth(properties.getToken()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
            if (teams == null || teams.isEmpty()) {
                return null;
            }
            Object teamId = teams.get(0).get("id");
            if (!(teamId instanceof String id) || id.isBlank()) {
                return null;
            }

            Map<String, Object> channel = restClient.get()
                    .uri(properties.getUrl() + "/teams/" + id + "/channels/name/" + channelName)
                    .headers(headers -> headers.setBearerAuth(properties.getToken()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Object channelId = channel == null ? null : channel.get("id");
            String resolvedChannelId = channelId instanceof String value ? value : null;
            log.debug("Resolved Mattermost channel by name. channelName={}, channelId={}", channelName, maskChannelId(resolvedChannelId));
            return resolvedChannelId;
        } catch (RuntimeException ex) {
            log.warn("Failed to resolve Mattermost channel by name. channelName={}", channelName, ex);
            return null;
        }
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
