package net.maddkraft.maddprestige.api.result;

import java.util.Map;
import java.util.Objects;

public record StructuredError(String code, ErrorCategory category, String message, Map<String, String> details) {
    public StructuredError {
        code = Objects.requireNonNull(code, "code");
        category = Objects.requireNonNull(category, "category");
        message = Objects.requireNonNull(message, "message");
        details = Map.copyOf(Objects.requireNonNull(details, "details"));
    }

    public static StructuredError unavailable(String code, String message) {
        return new StructuredError(code, ErrorCategory.UNAVAILABLE, message, Map.of());
    }
}
