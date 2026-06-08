package org.hanwha.cicdanalyzeragent.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hanwha.cicdanalyzeragent.analysis.ErrorType;
import org.junit.jupiter.api.Test;

class AiAnalysisResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsEmptyArrayAffectedFileAndSingleStringEvidence() throws Exception {
        AiAnalysisResponse response = objectMapper.readValue("""
                {
                  "errorType": "NetworkError",
                  "rootCause": "연결이 거절됨",
                  "affectedFile": [],
                  "suggestion": "서비스 상태를 확인하세요.",
                  "confidence": 0.95,
                  "evidence": "[ERROR] Connection refused"
                }
                """, AiAnalysisResponse.class);

        assertThat(response.errorType()).isEqualTo(ErrorType.NETWORK_ERROR);
        assertThat(response.affectedFile()).isNull();
        assertThat(response.evidence()).containsExactly("[ERROR] Connection refused");
    }

    @Test
    void joinsArrayAffectedFileValues() throws Exception {
        AiAnalysisResponse response = objectMapper.readValue("""
                {
                  "errorType": "CompileError",
                  "rootCause": "컴파일 실패",
                  "affectedFile": ["src/App.java", "src/Config.java"],
                  "suggestion": "컴파일 오류를 확인하세요.",
                  "confidence": 0.8,
                  "evidence": ["cannot find symbol"]
                }
                """, AiAnalysisResponse.class);

        assertThat(response.affectedFile()).isEqualTo("src/App.java, src/Config.java");
    }
}
