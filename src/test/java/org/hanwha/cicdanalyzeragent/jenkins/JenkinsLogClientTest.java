package org.hanwha.cicdanalyzeragent.jenkins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.hanwha.cicdanalyzeragent.config.JenkinsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class JenkinsLogClientTest {

    private JenkinsProperties properties;

    private RestClient.Builder restClientBuilder;

    private MockRestServiceServer server;

    private JenkinsLogClient logClient;

    @BeforeEach
    void setUp() {
        properties = new JenkinsProperties();
        properties.setUrl("https://jenkins.example.com");
        properties.setUser("ci-agent");
        properties.setToken("secret");

        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        logClient = new JenkinsLogClient(properties, new JenkinsUrlBuilder(properties), restClientBuilder.build());
    }

    @Test
    void fallsBackToJobNameAndBuildNumberWhenBuildUrlReturnsNotFound() {
        server.expect(requestTo("https://jenkins.example.com/wrong/job/url/123/consoleText"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://jenkins.example.com/job/folder/job/my-service/123/consoleText"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[ERROR] compile failed", MediaType.TEXT_PLAIN));

        FetchedLog log = logClient.fetchFullLog(
                "folder/my-service",
                123,
                "https://jenkins.example.com/wrong/job/url/123/"
        );

        assertThat(log.fullLog()).isEqualTo("[ERROR] compile failed");
        assertThat(log.errorLines()).contains("compile failed");
        server.verify();
    }

    @Test
    void fetchesBuildUrlWithoutAuthHeaderWhenCredentialsAreBlank() {
        JenkinsProperties anonymousProperties = new JenkinsProperties();
        RestClient.Builder anonymousRestClientBuilder = RestClient.builder();
        MockRestServiceServer anonymousServer = MockRestServiceServer.bindTo(anonymousRestClientBuilder).build();
        JenkinsLogClient anonymousLogClient = new JenkinsLogClient(
                anonymousProperties,
                new JenkinsUrlBuilder(anonymousProperties),
                anonymousRestClientBuilder.build()
        );

        anonymousServer.expect(requestTo("http://localhost:8080/job/HOM-%EB%B9%8C%EB%93%9C/35/consoleText"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> assertThat(request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse())
                .andRespond(withSuccess("[ERROR] anonymous log", MediaType.TEXT_PLAIN));

        FetchedLog log = anonymousLogClient.fetchFullLog(
                "HOM-빌드",
                35,
                "http://localhost:8080/job/HOM-%EB%B9%8C%EB%93%9C/35/"
        );

        assertThat(log.fullLog()).isEqualTo("[ERROR] anonymous log");
        anonymousServer.verify();
    }
}
