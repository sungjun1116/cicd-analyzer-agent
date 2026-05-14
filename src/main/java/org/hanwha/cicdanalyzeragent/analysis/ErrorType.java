package org.hanwha.cicdanalyzeragent.analysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ErrorType {
    COMPILE_ERROR("CompileError"),
    TEST_FAILURE("TestFailure"),
    DEPENDENCY_ERROR("DependencyError"),
    OOM_ERROR("OOMError"),
    NETWORK_ERROR("NetworkError"),
    PERMISSION_ERROR("PermissionError"),
    DISK_SPACE_ERROR("DiskSpaceError"),
    UNKNOWN("Unknown");

    private final String code;

    ErrorType(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static ErrorType from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        for (ErrorType type : values()) {
            if (type.code.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
