# CI/CD Analyzer Agent

Jenkins 빌드 실패 이벤트를 수신하고, 빌드 로그를 분석한 뒤 Mattermost로 알림을 전송하는 Spring Boot 기반 CI/CD 분석 Agent입니다.

이 프로젝트는 기존 Python FastAPI 기반 `build-analyzer`를 Spring Boot + Spring AI 구조로 마이그레이션하는 중입니다. 상세 계획은 [docx/spring-ai-migration-plan.md](docx/spring-ai-migration-plan.md)를 참고하세요.

## 주요 기능

- Jenkins Webhook 수신
- Webhook payload의 `error_log` 우선 사용
- `error_log`가 없을 경우 Jenkins API에서 `consoleText` 조회
- Rule 기반 빌드 실패 유형 분류
- Spring AI `ChatClient` 기반 LLM 보강 분석
- Mattermost Bot API 알림 전송
- Job 이름 기반 Mattermost 채널 라우팅
- Actuator health/metrics/prometheus endpoint 제공
- `.env` 파일 import 지원

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| Language | JDK 25 |
| Framework | Spring Boot 3.5.14 |
| AI | Spring AI 1.1.6 |
| Build | Gradle Groovy DSL |
| HTTP Client | Spring `RestClient` |
| API Docs | springdoc-openapi |
| Monitoring | Spring Boot Actuator, Micrometer, Prometheus |
| Test | JUnit 5, AssertJ, Spring Boot Test |

## 분석 흐름

```text
Jenkins Webhook
  -> JenkinsWebhookController
  -> BuildFailureProcessor
  -> JenkinsLogClient
  -> HybridBuildAnalyzer
       -> RuleBasedAnalyzer
       -> SpringAiBuildLogAnalyzer
  -> BuildFailureMessageRenderer
  -> MattermostClient
```

Rule 기반 분석은 항상 먼저 실행됩니다. Spring AI 분석이 활성화되어 있으면 LLM 결과로 원인과 해결책을 보강하고, AI 호출 실패 시 Rule 결과로 fallback합니다.

## 지원 에러 유형

| 유형 | 설명 |
| --- | --- |
| `CompileError` | Java 컴파일 오류, symbol 누락 등 |
| `TestFailure` | JUnit/TestNG 테스트 실패 |
| `DependencyError` | Maven/Gradle/npm 의존성 해결 실패 |
| `OOMError` | JVM heap, Metaspace, GC overhead 오류 |
| `NetworkError` | DNS, timeout, connection refused 등 |
| `PermissionError` | 권한, 인증, 403/401 오류 |
| `DiskSpaceError` | 디스크 공간 부족 |
| `Unknown` | Rule로 분류되지 않은 오류 |

## 요구사항

- JDK 25
- Gradle Wrapper 사용 권장
- Spring AI 분석 사용 시 Ollama 서버
- Jenkins 로그 fallback 사용 시 Jenkins API token
- Mattermost 알림 사용 시 Mattermost Bot token

## 설정

애플리케이션은 루트 `.env`를 자동으로 import합니다.

```yaml
spring:
  config:
    import: optional:file:.env[.properties]
```

`.env` 예시:

```properties
SERVER_PORT=8000

ANALYZER_SECONDARY_STRATEGY=spring-ai
ANALYZER_MAX_LOG_CHARS=8000

OLLAMA_BASE_URL=http://localhost:11434
AI_MODEL=qwen3.5:4b

JENKINS_URL=https://jenkins.example.com
JENKINS_USER=ci-agent
JENKINS_TOKEN=your_jenkins_api_token
JENKINS_LOG_MAX_SIZE=100000
JENKINS_TIMEOUT_SECONDS=30

MATTERMOST_URL=https://mattermost.example.com/api/v4
MATTERMOST_TOKEN=your_bot_token
MATTERMOST_CHANNEL_ID=
MATTERMOST_DEFAULT_CHANNEL_ID=your_default_channel_id
MATTERMOST_DEFAULT_CHANNEL=build-alerts
MATTERMOST_TIMEOUT_SECONDS=10
```

Spring AI를 임시로 끄려면:

```properties
ANALYZER_SECONDARY_STRATEGY=none
```

## 실행

테스트:

```bash
./gradlew test
```

애플리케이션 실행:

```bash
./gradlew bootRun
```

기본 포트는 `8000`입니다.

```bash
curl http://localhost:8000/health
```

## Ollama 사용

Spring AI 분석을 사용하려면 Ollama 서버와 모델이 준비되어 있어야 합니다.

```bash
ollama serve
ollama pull qwen3.5:4b
```

다른 모델을 사용할 경우 `.env`에서 `AI_MODEL` 값을 변경하세요.

## API

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/` | 서비스 정보 |
| `GET` | `/health` | 간단한 health 정보 |
| `GET` | `/config` | 민감 정보 제외 설정 확인 |
| `GET` | `/actuator/health` | Spring Boot Actuator health |
| `GET` | `/actuator/metrics` | Metrics |
| `GET` | `/actuator/prometheus` | Prometheus scrape endpoint |
| `GET` | `/swagger-ui.html` | Swagger UI |
| `POST` | `/webhook/jenkins` | Jenkins 실패 이벤트 비동기 처리 |
| `POST` | `/webhook/jenkins/sync` | Jenkins 실패 이벤트 동기 분석 |
| `POST` | `/webhook/analyze/{jobName}/{buildNumber}` | Jenkins API로 특정 빌드 로그 조회 후 분석 |

## Webhook payload

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
    "message": "Fix build"
  },
  "error_log": "[ERROR] Failed to compile: cannot find symbol"
}
```

동기 분석 예시:

```bash
curl -X POST http://localhost:8000/webhook/jenkins/sync \
  -H "Content-Type: application/json" \
  -d '{
    "job_name": "my-service",
    "build": {
      "number": 123,
      "status": "FAILURE",
      "url": "https://jenkins.example.com/job/my-service/123/",
      "duration_ms": 120000
    },
    "error_log": "[ERROR] /app/src/main/java/App.java:42: error: cannot find symbol"
  }'
```

응답 예시:

```json
{
  "job_name": "my-service",
  "build_number": 123,
  "error_type": "CompileError",
  "root_cause": "CompileError 감지됨: error: cannot find symbol",
  "affected_file": "/app/src/main/java/App.java:42",
  "suggestion": "컴파일 에러: import 경로, 누락된 클래스/메서드, 의존성 설정을 확인하세요.",
  "confidence": 0.85,
  "analyzer_chain": ["rule_based"],
  "evidence": [],
  "alternative_error_type": null
}
```

## Mattermost 알림

Mattermost 알림은 Bot API를 사용합니다.

- `MATTERMOST_URL`: Mattermost API base URL, 예: `https://mattermost.example.com/api/v4`
- `MATTERMOST_TOKEN`: Bot token
- `MATTERMOST_CHANNEL_ID`: 전체 알림을 강제로 보낼 채널 ID
- `MATTERMOST_DEFAULT_CHANNEL_ID`: 기본 채널 ID
- `MATTERMOST_DEFAULT_CHANNEL`: 기본 채널 이름

채널 라우팅은 `application.yaml`의 `build-analyzer.mattermost.channel-routing`에서 설정합니다.

## 프로젝트 구조

```text
src/main/java/org/hanwha/cicdanalyzeragent/
  ai/              Spring AI 응답 모델, 프롬프트, 읽기 전용 tool
  analysis/        Rule 분석기, Spring AI 분석기, Hybrid 병합
  config/          설정 바인딩, Async, RestClient 설정
  jenkins/         Jenkins 로그 수집
  notification/    Mattermost 알림, 채널 라우팅
  processing/      빌드 실패 처리 orchestration
  report/          Mattermost 메시지 렌더링
  web/             서비스 정보, health/config endpoint
  webhook/         Jenkins webhook controller와 DTO
```

## 개발 메모

- `build.gradle`은 Groovy DSL을 사용합니다.
- 외부 HTTP 호출은 `RestClient`를 사용합니다.
- `spring-boot-starter-webflux`는 사용하지 않습니다.
- `.env`는 `.gitignore` 대상입니다.
- 현재 RAG, Chat Memory, 실제 이력 저장소 연동은 후속 단계입니다.
