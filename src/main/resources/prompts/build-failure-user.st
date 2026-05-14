Job: {{jobName}} #{{buildNumber}}

Rule 기반 1차 분석:
- errorType: {{ruleErrorType}}
- rootCause: {{ruleRootCause}}
- affectedFile: {{ruleAffectedFile}}

빌드 로그:
```
{{log}}
```

위 정보를 바탕으로 실패 유형, 근본 원인, 영향 파일, 해결 방안, 신뢰도, 근거를 반환하세요.
