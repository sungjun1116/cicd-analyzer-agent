package org.hanwha.cicdanalyzeragent.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    private static final Logger log = LoggerFactory.getLogger(RestClientConfig.class);

    @Bean
    @ConditionalOnMissingBean
    RestClient.Builder restClientBuilder(AnalyzerProperties analyzerProperties) {
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory(
                        seconds(analyzerProperties.getLlmConnectTimeoutSeconds()),
                        seconds(analyzerProperties.getLlmTimeoutSeconds())
                ));
        if (analyzerProperties.isLlmPayloadLoggingEnabled()) {
            builder.requestInterceptor(ollamaChatLoggingInterceptor(analyzerProperties.getLlmPayloadLogMaxChars()));
        }
        return builder;
    }

    public static ClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return new BufferingClientHttpRequestFactory(requestFactory);
    }

    public static Duration seconds(int seconds) {
        return Duration.ofSeconds(Math.max(1, seconds));
    }

    private ClientHttpRequestInterceptor ollamaChatLoggingInterceptor(int maxChars) {
        return (request, body, execution) -> {
            if (!isOllamaChatRequest(request)) {
                return execution.execute(request, body);
            }

            long startedAt = System.nanoTime();
            log.info(
                    "Ollama API chat request. method={}, uri={}, body={}",
                    request.getMethod(),
                    request.getURI(),
                    abbreviate(new String(body, StandardCharsets.UTF_8), maxChars)
            );

            try {
                ClientHttpResponse response = execution.execute(request, body);
                String responseBody = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                log.info(
                        "Ollama API chat response. status={}, elapsedMs={}, body={}",
                        response.getStatusCode(),
                        elapsedMs(startedAt),
                        abbreviate(responseBody, maxChars)
                );
                return response;
            } catch (IOException | RuntimeException ex) {
                log.warn(
                        "Ollama API chat request failed. method={}, uri={}, elapsedMs={}, error={}",
                        request.getMethod(),
                        request.getURI(),
                        elapsedMs(startedAt),
                        ex.getMessage(),
                        ex
                );
                throw ex;
            }
        };
    }

    private boolean isOllamaChatRequest(HttpRequest request) {
        return "/api/chat".equals(request.getURI().getPath());
    }

    private String abbreviate(String value, int maxChars) {
        int safeMaxChars = Math.max(0, maxChars);
        if (value == null || value.length() <= safeMaxChars) {
            return value;
        }
        return value.substring(0, safeMaxChars) + "...(truncated, chars=" + value.length() + ")";
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
