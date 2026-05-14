# Spring Boot + Spring AI 마이그레이션 계획

작성일: 2026-05-14  
대상 프로젝트: `~/Developer/study/PythonProject/build-analyzer`  
현재 시스템: FastAPI 기반 Jenkins 빌드 실패 분석 Agent

## 1. 목표

현재 Python 프로젝트의 Jenkins 빌드 실패 분석 기능을 Spring Boot 기반 서비스로 이전한다. 단순 포팅이 아니라 Spring AI를 적극 활용해 다음 역량을 추가한다.

- LLM 호출을 수동 HTTP 호출에서 Spring AI `ChatClient` 중심 구조로 전환
- 분석 결과를 Java record/class로 직접 받는 구조화 출력 적용
- 과거 실패 사례와 운영 Runbook을 검색하는 RAG 기반 보강 분석 추가
- Jenkins/SCM/이력 조회를 Spring AI Tool Calling으로 읽기 전용 보강
- Spring Boot Actuator와 Spring AI 관측성으로 모델 호출, 토큰, 지연 시간, 실패율 측정
- 기존 Rule 기반 분석은 유지해 빠르고 결정적인 1차 분류기로 사용

## 2. 현재 시스템 요약

현재 Python 구현은 다음 흐름을 가진다.

```text
Jenkins Webhook
  -> webhook.handler
  -> JenkinsLogFetcher
  -> HybridAnalyzer
       -> RuleBasedAnalyzer
       -> LLMAnalyzer(Ollama HTTP 직접 호출)
  -> MattermostNotifier
```

주요 모듈별 역할은 다음과 같다.

| Python 모듈 | 현재 역할 | Spring 이전 대상 |
| --- | --- | --- |
| `main.py` | FastAPI 앱, health/config API | `BuildAnalyzerApplication`, Actuator, Controller |
| `webhook/models.py` | Jenkins Webhook Pydantic 모델 | DTO record + Bean Validation |
| `webhook/handler.py` | Webhook 수신, 백그라운드 처리 | `JenkinsWebhookController`, `BuildFailureProcessor` |
| `log_fetcher/jenkins.py` | Jenkins console log 수집 | `JenkinsLogClient` |
| `analyzer/rule_based.py` | Regex 기반 에러 유형 분류 | `RuleBasedAnalyzer` |
| `analyzer/llm_analyzer.py` | Ollama `/api/chat` 직접 호출 | `SpringAiBuildLogAnalyzer` |
| `analyzer/hybrid_analyzer.py` | Rule + LLM 결과 병합 | `HybridBuildAnalyzer`, `AnalysisMerger` |
| `notifier/mattermost.py` | Mattermost Bot API 전송, 채널 라우팅 | `MattermostClient`, `ChannelRouter` |
| `report/formatter.py` | Markdown/Jinja2 리포트 | `BuildFailureMessageRenderer` |
| `config.py`, `config.yaml` | 환경 변수 + YAML 설정 | `@ConfigurationProperties` + `application.yml` |

현재 지원 에러 유형은 `CompileError`, `TestFailure`, `DependencyError`, `OOMError`, `NetworkError`, `PermissionError`, `DiskSpaceError`, `Unknown`이다. 이 분류 체계는 Java enum으로 유지한다.

## 3. 권장 기술 스택

2026-05-14 기준 권장안이다. Spring AI 공식 문서의 현재 stable 라인은 1.1.x이며, Spring AI 문서는 Spring Boot 3.4.x 및 3.5.x 지원을 명시한다. 따라서 안정성을 우선해 Spring Boot 3.5.x + Spring AI 1.1.x 조합으로 시작한다.

| 영역 | 권장 |
| --- | --- |
| Java | JDK 25 |
| Framework | Spring Boot 3.5.x |
| AI | Spring AI 1.1.x, 초기 버전은 1.1.6 기준 |
| Build | Maven 또는 Gradle Kotlin DSL. 팀 표준이 없으면 Maven 권장 |
| Web | Spring MVC + WebClient. 서버는 MVC, 외부 HTTP는 WebClient |
| Config | `@ConfigurationProperties`, profile 기반 `application.yml` |
| Observability | Spring Boot Actuator, Micrometer, Prometheus, Spring AI observations |
| LLM Provider | 1차 Ollama, 이후 OpenAI/Anthropic profile 추가 |
| RAG 저장소 | 초기 SimpleVectorStore 또는 PgVector. 운영은 PgVector 권장 |
| 테스트 | JUnit 5, AssertJ, MockWebServer/WireMock, Spring Boot Test |

Boot 4.x는 현재 Spring Boot stable이지만 Spring AI 1.1.x 지원 범위와 운영 안정성을 고려해 이번 마이그레이션에서는 채택하지 않는다. Spring AI 2.x가 안정화되고 Boot 4 호환성이 검증된 뒤 별도 업그레이드로 다룬다.

## 4. 목표 아키텍처

```text
Jenkins
  -> POST /webhook/jenkins
  -> JenkinsWebhookController
  -> BuildFailureProcessor
       -> JenkinsLogClient
       -> HybridBuildAnalyzer
            -> RuleBasedAnalyzer
            -> SpringAiBuildLogAnalyzer
                 -> ChatClient
                 -> Structured Output
                 -> RAG Advisor
                 -> Chat Memory Advisor
                 -> Read-only Tools
       -> BuildFailureMessageRenderer
       -> MattermostClient
```

핵심 원칙은 다음과 같다.

- Rule 기반 분석은 항상 먼저 실행한다. 빠르고 비용이 낮으며, 장애 시에도 최소 분석 결과를 보장한다.
- Spring AI 분석은 보강 역할을 맡는다. 원인 요약, 해결 제안, Unknown 재분류, 유사 사례 검색, Runbook 근거 제공에 집중한다.
- LLM이 직접 Mattermost 전송이나 재빌드 같은 부작용을 수행하지 않도록 한다. Tool Calling은 읽기 전용 조회 도구로 제한한다.
- 모든 LLM 출력은 구조화 출력과 검증 계층을 통과해야 한다.
- 민감 정보는 prompt 투입 전에 마스킹하고, 관측성 로그에는 prompt/completion 원문을 기본 비활성화한다.

## 5. Spring 패키지 구조

```text
src/main/java/com/example/buildanalyzer/
  BuildAnalyzerApplication.java

  config/
    AnalyzerProperties.java
    JenkinsProperties.java
    MattermostProperties.java
    AiProperties.java
    WebClientConfig.java
    AsyncConfig.java

  webhook/
    JenkinsWebhookController.java
    dto/
      BuildInfo.java
      ScmInfo.java
      JenkinsBuildEvent.java
      WebhookResponse.java
      AnalysisResponse.java

  processing/
    BuildFailureProcessor.java
    BuildFailureCommand.java
    ProcessingResult.java

  analysis/
    BuildLogAnalyzer.java
    HybridBuildAnalyzer.java
    RuleBasedAnalyzer.java
    SpringAiBuildLogAnalyzer.java
    AnalysisMerger.java
    AnalysisResult.java
    ErrorType.java
    ErrorRule.java

  ai/
    BuildAnalysisPromptService.java
    BuildAnalysisTools.java
    BuildAnalysisAdvisorConfig.java
    BuildAnalysisMemoryConfig.java
    RunbookRagConfig.java
    AiAnalysisResponse.java

  jenkins/
    JenkinsLogClient.java
    FetchedLog.java
    JenkinsUrlBuilder.java

  notification/
    MattermostClient.java
    ChannelRouter.java
    MattermostAttachment.java

  report/
    BuildFailureMessageRenderer.java

  history/
    FailureHistoryService.java
    FailureHistoryRepository.java

src/main/resources/
  application.yml
  prompts/
    build-failure-system.st
    build-failure-user.st
  rules/
    error-rules.yml
  runbooks/
    dependency-error.md
    oom-error.md
    network-error.md

src/test/resources/
  sample-logs/
```

## 6. API 호환 계획

기존 API와 가능한 한 같은 path와 payload를 유지한다.

| 기존 FastAPI | Spring Boot 목표 | 비고 |
| --- | --- | --- |
| `GET /` | `GET /` | 서비스 정보 |
| `GET /health` | `GET /actuator/health`, 필요 시 `GET /health` alias | Actuator 우선 |
| `GET /config` | `GET /actuator/configprops` 또는 제한된 `/config` | 민감 정보 제외 |
| `POST /webhook/jenkins` | 동일 | 비동기 처리 |
| `POST /webhook/jenkins/sync` | 동일 | 테스트/디버깅용 |
| `POST /webhook/analyze/{job}/{build}` | 동일 | Jenkins API 조회 후 분석 |
| `GET /docs` | `/swagger-ui.html` | springdoc-openapi 사용 |

Webhook payload 필드는 기존과 동일하게 유지한다.

```json
{
  "job_name": "my-service",
  "build": {
    "number": 123,
    "status": "FAILURE",
    "url": "https://jenkins.example.com/job/my-service/123/",
    "duration_ms": 120000
  },
  "timestamp": "2026-04-09T12:00:00Z",
  "scm": {
    "branch": "main",
    "commit": "abc123def",
    "author": "developer@example.com",
    "message": "Fix bug in service"
  },
  "error_log": "[ERROR] ..."
}
```

Java DTO는 Jackson snake_case 전략을 쓰거나 `@JsonProperty("job_name")`를 명시한다. 외부 계약 안정성을 위해 초기에는 `@JsonProperty`를 권장한다.

## 7. Spring AI 활용 설계

### 7.1 ChatClient 중심 LLM 분석

현재 `LLMAnalyzer`는 Ollama `/api/chat`를 직접 호출한다. Spring 이전 후에는 `ChatClient`를 사용한다.

목표 형태:

```java
AiAnalysisResponse response = chatClient.prompt()
    .system(systemPrompt)
    .user(userPrompt)
    .call()
    .entity(AiAnalysisResponse.class);
```

`AiAnalysisResponse`는 다음 필드를 가진 record로 정의한다.

```java
public record AiAnalysisResponse(
    ErrorType errorType,
    String rootCause,
    String affectedFile,
    String suggestion,
    double confidence,
    List<String> evidence
) {}
```

기존 Python의 JSON 파싱/검증 코드는 Spring AI 구조화 출력으로 대체한다. 모델이 native structured output을 지원하는 경우 `AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT`를 profile 또는 property로 켤 수 있게 한다. 지원하지 않는 provider는 Spring AI의 기본 structured output conversion을 사용한다.

### 7.2 PromptTemplate 파일화

현재 Python 상수인 `SYSTEM_PROMPT`, `USER_PROMPT_TEMPLATE`는 classpath resource로 이동한다.

```text
src/main/resources/prompts/build-failure-system.st
src/main/resources/prompts/build-failure-user.st
```

Prompt 원칙:

- 출력 스키마를 명확히 고정한다.
- 에러 유형 enum 외 값은 금지한다.
- `rootCause`, `suggestion`은 한국어로 반환한다.
- confidence 산정 기준을 prompt에 포함한다.
- 로그가 잘렸는지, rule 결과가 무엇인지, RAG 근거가 무엇인지 명시한다.
- 비밀값, 토큰, URL query parameter는 prompt 전에 마스킹한다.

### 7.3 Rule + AI 병합 전략

기존 병합 전략을 유지하되 Spring AI 근거 필드를 추가한다.

| 상황 | 최종 `errorType` | 최종 `rootCause` | 최종 `suggestion` | 최종 `confidence` |
| --- | --- | --- | --- | --- |
| Rule이 매칭됨 | Rule 우선 | AI가 더 구체적이면 AI | AI 우선 | `rule * 0.4 + ai * 0.6` |
| Rule이 Unknown | AI 우선 | AI | AI | AI |
| AI 실패/timeout | Rule | Rule | Rule | Rule |
| AI 결과 검증 실패 | Rule | Rule + 실패 사유 로그 | Rule | Rule |

AI 결과가 Rule 분류와 충돌할 때는 다음 정책을 적용한다.

- Rule confidence가 높고 명확한 패턴이면 Rule 분류 유지
- AI가 다른 분류를 제안하면 `alternativeErrorType` 또는 `evidence`에 보관
- Unknown 또는 낮은 Rule confidence에서는 AI 분류 채택
- 운영 로그에는 `rule_error_type`, `ai_error_type`, `final_error_type`을 모두 남김

### 7.4 RAG로 Runbook과 과거 장애 활용

Spring AI의 RAG Advisor를 사용해 LLM이 내부 운영 지식을 참고하도록 한다.

초기 인덱싱 대상:

- `src/main/resources/runbooks/*.md`
- 과거 Jenkins 실패 분석 결과
- Maven/Gradle/Node 빌드 실패 대응 문서
- 팀별 Job 라우팅 정보
- 자주 발생하는 dependency, network, OOM 조치 이력

구성 방향:

- 개발 초기: `SimpleVectorStore` 또는 파일 기반 VectorStore
- 운영 초기: PgVector + PostgreSQL
- Advisor: `QuestionAnswerAdvisor` 또는 `RetrievalAugmentationAdvisor`
- 검색 필터: `errorType`, `jobGroup`, `language`, `buildTool`
- 응답에는 검색된 문서 제목/ID를 `evidence`로 포함

RAG는 LLM의 추측을 줄이는 핵심 기능이다. 특히 Unknown 로그, dependency resolution 실패, 내부 Nexus/Artifactory 장애, 네트워크/권한 문제에서 효과가 크다.

### 7.5 Chat Memory로 Job별 반복 실패 학습

Spring AI Chat Memory를 사용해 같은 Jenkins Job의 최근 실패 맥락을 유지한다.

권장 사용 방식:

- conversation id: `jenkins:{jobName}`
- 저장 범위: 최근 N개 실패의 요약과 최종 조치 결과
- 저장소: 초기 in-memory, 운영은 JDBC repository
- 보관 정책: 30일 또는 최근 20건
- 민감 정보: raw log 전체 저장 금지, 요약과 fingerprint만 저장

주의할 점:

- Chat Memory는 전체 감사 이력 저장소가 아니다. 장기 이력과 통계는 별도 DB 테이블에 저장한다.
- 메모리는 분석 보강용으로만 사용하고, 최종 근거는 RAG/이력 DB에서 추적 가능해야 한다.

### 7.6 Tool Calling

Spring AI Tool Calling은 읽기 전용 컨텍스트 보강에 사용한다. LLM이 직접 외부 부작용을 만들지 않도록 제한한다.

후보 도구:

| Tool | 역할 | 부작용 |
| --- | --- | --- |
| `getJenkinsBuildMetadata(job, build)` | 빌드 duration, node, upstream job 조회 | 없음 |
| `getRecentSimilarFailures(job, errorType)` | 최근 유사 실패 fingerprint 조회 | 없음 |
| `getChangedFiles(commit)` | SCM 변경 파일 조회 | 없음 |
| `getRunbook(errorType, buildTool)` | 에러 유형별 운영 문서 조회 | 없음 |
| `getDependencyHints(groupId, artifactId)` | 내부 저장소 장애/누락 힌트 조회 | 없음 |

금지 도구:

- Mattermost 전송
- Jenkins rebuild/abort
- Git push/comment
- 티켓 생성

부작용이 필요한 기능은 별도 서비스 로직에서 명시적 정책과 승인 조건을 거쳐 수행한다.

### 7.7 Spring AI Observability

Spring AI 관측성을 통해 다음 지표를 수집한다.

- ChatClient 호출 latency
- ChatModel 호출 latency
- input/output/total token usage
- model name, provider
- advisor 실행 시간
- tool call 실행 시간
- AI 분석 실패율, fallback율

운영 설정 원칙:

- prompt/completion 원문 로깅은 기본 비활성화
- 디버깅용 profile에서만 제한적으로 활성화
- 민감 정보 마스킹 후에도 전체 로그 원문은 trace에 싣지 않음
- Mattermost 알림에는 confidence와 analyzer chain을 표시

## 8. 설정 구조

`application.yml` 예시:

```yaml
server:
  port: 8000

spring:
  application:
    name: build-analyzer
  ai:
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        options:
          model: ${AI_MODEL:qwen3.5:4b}
          temperature: 0.2

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus

build-analyzer:
  analyzer:
    secondary-strategy: spring-ai
    max-log-chars: 8000
    native-structured-output: false
    enable-rag: true
    enable-memory: true
    enable-tools: true
  jenkins:
    url: ${JENKINS_URL:}
    user: ${JENKINS_USER:}
    token: ${JENKINS_TOKEN:}
    log-max-size: 100000
  mattermost:
    url: ${MATTERMOST_URL:}
    token: ${MATTERMOST_TOKEN:}
    default-channel-id: ${MATTERMOST_DEFAULT_CHANNEL_ID:}
    default-channel: build-alerts
    channel-routing:
      - patterns: [SMT, mobile]
        channel-id: h3gx9tm4migetx7tccyon4g7qy
      - patterns: [HOM, homepage]
        channel-id: hbe3kj44g7gw3drsa73en5e51y
```

provider 교체는 Spring profile로 처리한다.

```text
application-ollama.yml
application-openai.yml
application-anthropic.yml
```

초기 구현은 Ollama를 기본값으로 둔다. 이후 OpenAI/Anthropic은 같은 `SpringAiBuildLogAnalyzer` 인터페이스를 유지한 채 dependency와 profile만 추가한다.

## 9. 의존성 계획

Maven 기준 주요 의존성:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-bom</artifactId>
      <version>1.1.6</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-ollama</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-advisors-vector-store</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-rag</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  </dependency>
  <dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
  </dependency>
</dependencies>
```

`spring-boot-starter-webflux`는 서버를 WebFlux로 바꾸기 위한 것이 아니라 `WebClient` 사용 목적이다. 팀 표준이 `RestClient`라면 제외 가능하다.

## 10. 단계별 마이그레이션 계획

### Phase 0. 기준선 고정

목표: Python 구현의 현재 동작을 명확히 고정한다.

작업:

- 현재 테스트 전체 실행 결과 기록
- `tests/fixtures/sample_logs`를 Java 테스트 리소스로 복사
- 주요 API 요청/응답 payload golden file 생성
- Rule 기반 분류 결과 golden file 생성
- Mattermost attachment 예시 golden file 생성
- 현재 `config.yaml`의 의미를 `application.yml` 매핑표로 정리

완료 기준:

- Python과 Java가 같은 sample log에 대해 같은 error type을 반환해야 할 기준표가 존재
- Webhook payload 호환 기준이 문서화됨

### Phase 1. Spring Boot 골격 구성

목표: 실행 가능한 Spring Boot 서비스 골격을 만든다.

작업:

- `pom.xml` 또는 `build.gradle.kts` 생성
- `BuildAnalyzerApplication` 작성
- `/`, `/health`, `/config` 또는 Actuator endpoint 구성
- `@ConfigurationProperties` 클래스 작성
- Jackson snake_case 처리 또는 DTO별 `@JsonProperty` 적용
- springdoc-openapi 설정

완료 기준:

- `./mvnw test` 통과
- `./mvnw spring-boot:run`으로 8000 포트 실행
- `/actuator/health` 정상 응답

### Phase 2. 기존 기능 Java 포팅

목표: Spring AI 없이도 현재 Python Rule-only 기능과 외부 연동을 대체한다.

작업:

- `ErrorType`, `AnalysisResult`, `BuildLogAnalyzer` 정의
- `RuleBasedAnalyzer` 포팅
- `JenkinsLogClient` 구현
- `MattermostClient` 구현
- `ChannelRouter` 구현
- `BuildFailureMessageRenderer` 구현
- `/webhook/jenkins`, `/webhook/jenkins/sync`, `/webhook/analyze/{job}/{build}` 구현
- 비동기 처리는 `TaskExecutor` 또는 Spring 이벤트로 구현
- 외부 HTTP timeout, retry, error handling 적용

완료 기준:

- 기존 sample log 분류 결과와 1:1 일치
- Jenkins/Mattermost는 MockWebServer 기반 테스트 통과
- AI 설정을 꺼도 운영 가능한 상태

### Phase 3. Spring AI 기반 LLM 분석 추가

목표: Python의 `LLMAnalyzer`를 Spring AI 기반 분석기로 대체한다.

작업:

- `spring-ai-starter-model-ollama` 설정
- `ChatClient` Bean 구성
- prompt 파일 분리
- `AiAnalysisResponse` 구조화 출력 적용
- `SpringAiBuildLogAnalyzer` 구현
- timeout, exception, validation fallback 구현
- 기존 `AnalysisMerger`와 연결
- `analyzerChain`에 `rule_based`, `spring_ai(ollama:model)` 기록

완료 기준:

- Ollama 사용 시 `/webhook/jenkins/sync`에서 AI 보강 결과 반환
- Ollama 장애/timeout 시 Rule 결과로 fallback
- AI 응답 파싱 실패가 서비스 장애로 전파되지 않음

### Phase 4. Spring AI 고도화

목표: Spring AI를 적극 활용해 단순 LLM 호출 이상의 분석 품질을 만든다.

작업:

- RAG 인덱싱 파이프라인 작성
- `QuestionAnswerAdvisor` 또는 `RetrievalAugmentationAdvisor` 적용
- Job별 `ChatMemory` 적용
- read-only Tool Calling 추가
- 유사 실패 fingerprint 저장
- AI 결과에 `evidence` 필드 추가
- prompt versioning 도입
- provider별 profile 추가: `ollama`, `openai`, `anthropic`

완료 기준:

- Unknown 케이스에서 RAG 근거가 포함된 제안 생성
- 동일 Job 반복 실패에서 이전 실패 요약을 활용
- Tool Calling 결과가 분석 근거에 포함
- 모든 Tool은 read-only로 제한됨

### Phase 5. 운영 안정화

목표: 운영 투입 가능한 관측성, 보안, 장애 대응을 갖춘다.

작업:

- Actuator metrics/prometheus 활성화
- Spring AI observations 확인
- AI token usage, latency dashboard 구성
- prompt/completion 원문 로깅 비활성화 검증
- secret redaction filter 구현
- idempotency key 적용: `jobName + buildNumber`
- 중복 webhook 처리 방지
- background task queue 한도 설정
- LLM circuit breaker 또는 bulkhead 적용
- Mattermost 전송 실패 retry/backoff 적용

완료 기준:

- LLM 장애 시 webhook 처리 전체가 지연/중단되지 않음
- 중복 webhook에도 Mattermost 중복 알림 방지
- Prometheus에서 AI/HTTP/notification 지표 확인

### Phase 6. 전환

목표: 운영 위험을 낮추고 Python 서비스에서 Spring 서비스로 전환한다.

전환 순서:

1. Shadow mode: Jenkins webhook을 Python과 Spring에 동시에 전송하되 Spring은 알림 전송하지 않음
2. 결과 비교: error type, confidence, suggestion 품질 비교
3. Canary: 특정 Job 그룹만 Spring 알림 활성화
4. 확대: 팀/도메인 단위로 Spring 알림 범위 확대
5. 종료: Python 서비스 read-only 전환 후 제거

완료 기준:

- 1주 이상 shadow 비교에서 치명적 회귀 없음
- Canary 그룹에서 중복/누락 알림 없음
- rollback 절차가 Jenkins webhook 설정 수준에서 가능

## 11. 테스트 전략

| 테스트 | 대상 | 도구 |
| --- | --- | --- |
| Unit | RuleBasedAnalyzer, AnalysisMerger, ChannelRouter | JUnit 5, AssertJ |
| DTO contract | JenkinsBuildEvent JSON 역직렬화 | JacksonTester |
| HTTP client | Jenkins/Mattermost client | MockWebServer 또는 WireMock |
| Web layer | Controller path/status/body | MockMvc |
| AI fallback | Ollama 장애, invalid JSON, timeout | Mock ChatModel |
| RAG | runbook retrieval, filter | Test VectorStore |
| Integration | `/webhook/jenkins/sync` end-to-end | SpringBootTest |

AI 테스트 원칙:

- 단위 테스트에서 실제 모델 호출 금지
- `ChatModel` 또는 `ChatClient`를 test double로 대체
- 모델 호출 E2E는 별도 profile과 수동/CI optional job로 분리
- Golden log fixture는 Python과 Java 양쪽에서 공유

## 12. 데이터 저장 계획

초기 포팅에는 DB가 필수는 아니다. 다만 Spring AI 활용을 확장하려면 다음 저장소가 필요하다.

| 데이터 | 초기 | 운영 권장 |
| --- | --- | --- |
| 분석 이력 | 파일 또는 in-memory | PostgreSQL |
| 유사 실패 fingerprint | in-memory | PostgreSQL |
| Chat Memory | in-memory | JDBC repository |
| Runbook VectorStore | SimpleVectorStore | PgVector |
| 알림 중복 방지 | Caffeine cache | Redis 또는 PostgreSQL unique key |

첫 운영 릴리스에서는 PostgreSQL 하나로 분석 이력, memory, vector store를 통합하는 구성이 가장 단순하다.

## 13. 보안 및 개인정보

마이그레이션 시 반드시 반영할 정책:

- Jenkins token, Mattermost token은 `application.yml`에 직접 저장하지 않음
- prompt 투입 전 secret pattern 마스킹
- URL query parameter, Authorization header, access token, password, private key 마스킹
- raw log 전체를 DB/ChatMemory/trace에 저장하지 않음
- prompt/completion 관측성 원문 로깅은 운영 profile에서 비활성화
- `/config` endpoint는 민감 값과 token 존재 여부만 표시
- Tool Calling은 read-only allowlist만 등록

## 14. 주요 리스크와 대응

| 리스크 | 영향 | 대응 |
| --- | --- | --- |
| Spring AI API 변화 | 컴파일/구현 변경 | 1.1.x stable 고정, BOM 사용 |
| LLM 지연 시간 | Webhook 처리 지연 | 비동기 처리, timeout, fallback, bulkhead |
| LLM hallucination | 잘못된 조치 제안 | Rule 우선 병합, RAG 근거 표시, confidence threshold |
| Prompt에 민감 정보 포함 | 보안 사고 | redaction filter, prompt logging off |
| Tool Calling 부작용 | 잘못된 외부 액션 | read-only tool만 허용 |
| Python과 Java 결과 차이 | 운영 회귀 | golden fixture, shadow mode 비교 |
| Mattermost 중복 알림 | 운영 피로도 증가 | idempotency key, 중복 캐시/DB unique |

## 15. 우선순위 제안

가장 현실적인 순서는 다음과 같다.

1. Phase 0-2를 먼저 끝내서 Python 기능을 Java로 동등 포팅한다.
2. Phase 3에서 Spring AI ChatClient와 구조화 출력을 붙인다.
3. Phase 4의 RAG, Memory, Tool Calling은 한 번에 모두 넣지 말고 RAG부터 추가한다.
4. 운영 전에는 Phase 5의 fallback, 중복 방지, 관측성을 반드시 완료한다.

Spring AI 활용 우선순위는 다음이 좋다.

| 우선순위 | 기능 | 이유 |
| --- | --- | --- |
| 1 | ChatClient + Structured Output | 현재 LLM 직접 호출을 가장 안전하게 대체 |
| 2 | RAG | 내부 Runbook과 과거 장애를 근거로 분석 품질 향상 |
| 3 | Observability | 토큰/지연/실패율을 운영 지표로 관리 |
| 4 | Chat Memory | Job별 반복 실패 맥락 유지 |
| 5 | Tool Calling | Jenkins/SCM/이력 조회로 고도화 |

## 16. 산출물 체크리스트

- [ ] Java Spring Boot 프로젝트 골격
- [ ] 기존 API contract 유지 문서
- [ ] Python fixture 기반 Java 테스트
- [ ] RuleBasedAnalyzer Java 포팅
- [ ] JenkinsLogClient
- [ ] MattermostClient
- [ ] SpringAiBuildLogAnalyzer
- [ ] Prompt template 파일
- [ ] RAG runbook 문서와 ingestion
- [ ] Chat Memory 설정
- [ ] Read-only Tool Calling
- [ ] Actuator/Micrometer/Spring AI 관측성
- [ ] Shadow mode 비교 리포트
- [ ] Cutover/rollback 절차

## 17. 참고 문서

- Spring AI Getting Started: https://docs.spring.io/spring-ai/reference/getting-started.html
- Spring AI ChatClient API: https://docs.spring.io/spring-ai/reference/api/chatclient.html
- Spring AI Structured Output Converter: https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html
- Spring AI Ollama Chat: https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html
- Spring AI Tool Calling: https://docs.spring.io/spring-ai/reference/api/tools.html
- Spring AI Chat Memory: https://docs.spring.io/spring-ai/reference/api/chat-memory.html
- Spring AI RAG: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html
- Spring AI Observability: https://docs.spring.io/spring-ai/reference/observability/index.html
- Spring Boot Reference: https://docs.spring.io/spring-boot/reference/
