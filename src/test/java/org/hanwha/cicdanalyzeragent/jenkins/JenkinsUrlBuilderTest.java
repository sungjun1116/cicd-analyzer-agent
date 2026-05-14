package org.hanwha.cicdanalyzeragent.jenkins;

import static org.assertj.core.api.Assertions.assertThat;

import org.hanwha.cicdanalyzeragent.config.JenkinsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JenkinsUrlBuilderTest {

    private JenkinsUrlBuilder urlBuilder;

    @BeforeEach
    void setUp() {
        JenkinsProperties properties = new JenkinsProperties();
        properties.setUrl("https://jenkins.example.com/");
        urlBuilder = new JenkinsUrlBuilder(properties);
    }

    @Test
    void buildsConsoleTextUrlFromJobNameAndBuildNumber() {
        String url = urlBuilder.consoleTextUrl("folder/my service", 123);

        assertThat(url)
                .isEqualTo("https://jenkins.example.com/job/folder/job/my%20service/123/consoleText");
    }

    @Test
    void buildsConsoleTextUrlFromBuildUrl() {
        String url = urlBuilder.consoleTextUrlFromBuildUrl(
                "https://jenkins.example.com/job/my-service/123/",
                123
        );

        assertThat(url)
                .isEqualTo("https://jenkins.example.com/job/my-service/123/consoleText");
    }

    @Test
    void convertsConsolePageUrlToConsoleTextUrl() {
        String url = urlBuilder.consoleTextUrlFromBuildUrl(
                "https://jenkins.example.com/job/my-service/123/console",
                123
        );

        assertThat(url)
                .isEqualTo("https://jenkins.example.com/job/my-service/123/consoleText");
    }

    @Test
    void keepsExistingConsoleTextUrl() {
        String url = urlBuilder.consoleTextUrlFromBuildUrl(
                "https://jenkins.example.com/job/my-service/123/consoleText",
                123
        );

        assertThat(url)
                .isEqualTo("https://jenkins.example.com/job/my-service/123/consoleText");
    }

    @Test
    void appendsBuildNumberWhenUrlPointsToJob() {
        String url = urlBuilder.consoleTextUrlFromBuildUrl(
                "https://jenkins.example.com/job/my-service/",
                123
        );

        assertThat(url)
                .isEqualTo("https://jenkins.example.com/job/my-service/123/consoleText");
    }
}
