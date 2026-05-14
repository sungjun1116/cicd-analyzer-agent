package org.hanwha.cicdanalyzeragent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "build-analyzer.analyzer.secondary-strategy=none")
class CicdAnalyzerAgentApplicationTests {

    @Test
    void contextLoads() {
    }

}
