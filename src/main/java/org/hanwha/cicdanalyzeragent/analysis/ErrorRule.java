package org.hanwha.cicdanalyzeragent.analysis;

import java.util.regex.Pattern;

public record ErrorRule(
        ErrorType type,
        Pattern pattern,
        Pattern filePattern,
        String suggestion
) {
}
