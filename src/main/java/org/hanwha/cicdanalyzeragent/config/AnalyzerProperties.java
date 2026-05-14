package org.hanwha.cicdanalyzeragent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "build-analyzer.analyzer")
public class AnalyzerProperties {

    private String secondaryStrategy = "spring-ai";

    private int maxLogChars = 8000;

    private int llmConnectTimeoutSeconds = 5;

    private int llmTimeoutSeconds = 90;

    private String llmKeepAlive = "30m";

    private int llmNumPredict = 512;

    private boolean llmPayloadLoggingEnabled = true;

    private int llmPayloadLogMaxChars = 8000;

    private boolean nativeStructuredOutput = false;

    private boolean enableRag = true;

    private boolean enableMemory = true;

    private boolean enableTools = true;

    public String getSecondaryStrategy() {
        return secondaryStrategy;
    }

    public void setSecondaryStrategy(String secondaryStrategy) {
        this.secondaryStrategy = secondaryStrategy;
    }

    public int getMaxLogChars() {
        return maxLogChars;
    }

    public void setMaxLogChars(int maxLogChars) {
        this.maxLogChars = maxLogChars;
    }

    public int getLlmConnectTimeoutSeconds() {
        return llmConnectTimeoutSeconds;
    }

    public void setLlmConnectTimeoutSeconds(int llmConnectTimeoutSeconds) {
        this.llmConnectTimeoutSeconds = llmConnectTimeoutSeconds;
    }

    public int getLlmTimeoutSeconds() {
        return llmTimeoutSeconds;
    }

    public void setLlmTimeoutSeconds(int llmTimeoutSeconds) {
        this.llmTimeoutSeconds = llmTimeoutSeconds;
    }

    public String getLlmKeepAlive() {
        return llmKeepAlive;
    }

    public void setLlmKeepAlive(String llmKeepAlive) {
        this.llmKeepAlive = llmKeepAlive;
    }

    public int getLlmNumPredict() {
        return llmNumPredict;
    }

    public void setLlmNumPredict(int llmNumPredict) {
        this.llmNumPredict = llmNumPredict;
    }

    public boolean isLlmPayloadLoggingEnabled() {
        return llmPayloadLoggingEnabled;
    }

    public void setLlmPayloadLoggingEnabled(boolean llmPayloadLoggingEnabled) {
        this.llmPayloadLoggingEnabled = llmPayloadLoggingEnabled;
    }

    public int getLlmPayloadLogMaxChars() {
        return llmPayloadLogMaxChars;
    }

    public void setLlmPayloadLogMaxChars(int llmPayloadLogMaxChars) {
        this.llmPayloadLogMaxChars = llmPayloadLogMaxChars;
    }

    public boolean isNativeStructuredOutput() {
        return nativeStructuredOutput;
    }

    public void setNativeStructuredOutput(boolean nativeStructuredOutput) {
        this.nativeStructuredOutput = nativeStructuredOutput;
    }

    public boolean isEnableRag() {
        return enableRag;
    }

    public void setEnableRag(boolean enableRag) {
        this.enableRag = enableRag;
    }

    public boolean isEnableMemory() {
        return enableMemory;
    }

    public void setEnableMemory(boolean enableMemory) {
        this.enableMemory = enableMemory;
    }

    public boolean isEnableTools() {
        return enableTools;
    }

    public void setEnableTools(boolean enableTools) {
        this.enableTools = enableTools;
    }

    public boolean isSpringAiEnabled() {
        return "spring-ai".equalsIgnoreCase(secondaryStrategy)
                || "llm".equalsIgnoreCase(secondaryStrategy);
    }
}
