package org.hanwha.cicdanalyzeragent.analysis;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class RuleBasedAnalyzer implements BuildLogAnalyzer {

    private static final int PATTERN_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.MULTILINE;

    private static final List<ErrorRule> DEFAULT_RULES = List.of(
            rule(
                    ErrorType.OOM_ERROR,
                    "(OutOfMemoryError|GC overhead limit|heap space|Java heap space|PermGen space|Metaspace)",
                    null,
                    "메모리 부족: JVM 힙 메모리 설정 조정이 필요합니다. -Xmx 옵션을 늘리거나 메모리 누수를 점검하세요."
            ),
            rule(
                    ErrorType.DISK_SPACE_ERROR,
                    "(No space left on device|Disk quota exceeded|insufficient disk space)",
                    null,
                    "디스크 공간 부족: 빌드 서버 디스크를 정리하고 오래된 빌드 아티팩트를 삭제하세요."
            ),
            rule(
                    ErrorType.NETWORK_ERROR,
                    "(Connection refused|ConnectException|SocketTimeoutException|UnknownHostException|Network is unreachable)",
                    null,
                    "네트워크 오류: 외부 서비스 상태, 방화벽, DNS, 서비스 가용성을 점검하세요."
            ),
            rule(
                    ErrorType.PERMISSION_ERROR,
                    "(Permission denied|Access denied|403 Forbidden|Authentication failed|Unauthorized)",
                    null,
                    "권한 오류: 접근 권한 및 인증 정보가 유효한지 확인하세요."
            ),
            rule(
                    ErrorType.DEPENDENCY_ERROR,
                    "(Could not resolve dependencies|Could not find artifact|Failed to collect dependencies|Cannot resolve.*artifact|Unresolved dependency)",
                    "(pom\\.xml|build\\.gradle|package\\.json)",
                    "의존성 오류: pom.xml 또는 build.gradle 설정과 Maven/Gradle 저장소 접근 가능 여부를 확인하세요."
            ),
            rule(
                    ErrorType.TEST_FAILURE,
                    "(Tests run:.*Failures:\\s*[1-9]|FAILED.*Test|AssertionError|TestNG.*FAILED|JUnit.*failed)",
                    "(\\S+Test\\.java):(\\d+)",
                    "테스트 실패: 실패한 테스트 케이스, 모킹, 예상값과 실제값 차이를 확인하세요."
            ),
            rule(
                    ErrorType.COMPILE_ERROR,
                    "(cannot find symbol|error:\\s.*symbol|compilation failed|javac.*error|Compilation failure)",
                    "(\\S+\\.java):(\\d+)",
                    "컴파일 에러: import 경로, 누락된 클래스/메서드, 의존성 설정을 확인하세요."
            )
    );

    private final List<ErrorRule> rules;

    public RuleBasedAnalyzer() {
        this(DEFAULT_RULES);
    }

    public RuleBasedAnalyzer(List<ErrorRule> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public AnalysisResult analyze(String log, String jobName, int buildNumber) {
        String safeLog = log == null ? "" : log;
        for (ErrorRule rule : rules) {
            Matcher matcher = rule.pattern().matcher(safeLog);
            if (matcher.find()) {
                String affectedFile = extractFileInfo(safeLog, rule.filePattern());
                String snippet = extractSnippet(safeLog, matcher.start(), 300);
                return AnalysisResult.of(
                        rule.type(),
                        rule.type().code() + " 감지됨: " + abbreviate(matcher.group(), 100),
                        affectedFile,
                        rule.suggestion(),
                        0.85,
                        snippet,
                        List.of(name())
                );
            }
        }

        return AnalysisResult.of(
                ErrorType.UNKNOWN,
                "알 수 없는 에러 유형",
                null,
                "로그를 직접 확인하세요. 새로운 에러 패턴일 수 있습니다.",
                0.0,
                tail(safeLog, 500),
                List.of(name())
        );
    }

    @Override
    public String name() {
        return "rule_based";
    }

    private static ErrorRule rule(ErrorType type, String pattern, String filePattern, String suggestion) {
        return new ErrorRule(
                type,
                Pattern.compile(pattern, PATTERN_FLAGS),
                filePattern == null ? null : Pattern.compile(filePattern),
                suggestion
        );
    }

    private String extractFileInfo(String log, Pattern filePattern) {
        if (filePattern == null) {
            return null;
        }
        Matcher matcher = filePattern.matcher(log);
        return matcher.find() ? matcher.group() : null;
    }

    private String extractSnippet(String log, int matchPosition, int window) {
        int start = Math.max(0, matchPosition - 100);
        int end = Math.min(log.length(), matchPosition + window);
        String snippet = log.substring(start, end);
        String[] lines = snippet.split("\\R", -1);
        int from = start > 0 && lines.length > 1 ? 1 : 0;
        int to = end < log.length() && lines.length > 1 ? lines.length - 1 : lines.length;
        if (from >= to) {
            return snippet;
        }
        return String.join("\n", List.of(lines).subList(from, to));
    }

    private String tail(String log, int maxLength) {
        if (log.length() <= maxLength) {
            return log;
        }
        return "..." + log.substring(log.length() - maxLength);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
