package net.maddkraft.maddprestige.api.result;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public record StructuredError(String code, ErrorCategory category, String message, Map<String, String> details) {
    private static final Pattern CODE = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public StructuredError {
        code = Objects.requireNonNull(code, "code");
        category = Objects.requireNonNull(category, "category");
        message = Objects.requireNonNull(message, "message");
        details = Map.copyOf(Objects.requireNonNull(details, "details"));
        if (!CODE.matcher(code).matches() || message.isBlank() || message.length() > 1024 || details.size() > 16
                || details.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null
                        || entry.getKey().length() > 64 || entry.getValue().length() > 256)) {
            throw new IllegalArgumentException("Structured error is outside stable bounds");
        }
    }

    public static StructuredError unavailable(String code, String message) {
        return new StructuredError(code, ErrorCategory.UNAVAILABLE, message, Map.of());
    }
}
