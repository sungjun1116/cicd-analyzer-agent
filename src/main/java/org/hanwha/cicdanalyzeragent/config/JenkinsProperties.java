package org.hanwha.cicdanalyzeragent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "build-analyzer.jenkins")
public class JenkinsProperties {

    private String url = "";

    private String user = "";

    private String token = "";

    private int logMaxSize = 100000;

    private int timeoutSeconds = 30;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getLogMaxSize() {
        return logMaxSize;
    }

    public void setLogMaxSize(int logMaxSize) {
        this.logMaxSize = logMaxSize;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isConfigured() {
        return hasBaseUrl();
    }

    public boolean hasBaseUrl() {
        return hasText(url);
    }

    public boolean hasCredentials() {
        return hasText(user) && hasText(token);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
