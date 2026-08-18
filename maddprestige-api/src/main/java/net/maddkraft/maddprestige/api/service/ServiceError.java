package net.maddkraft.maddprestige.api.service;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Bounded machine-readable failure safe to expose across plugin boundaries. No component contains rendered text;
 * callers localize {@code messageKey} with the bounded safe arguments.
 *
 * @param code canonical machine failure code, 1-64 characters
 * @param messageKey canonical localization key, 1-64 characters
 * @param arguments immutable localization argument snapshot, at most 16 bounded entries
 */
public record ServiceError(String code, String messageKey, Map<String, String> arguments) {
    private static final Pattern CODE = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final int MAX_ARGUMENTS = 16;

    public ServiceError {
        code = Objects.requireNonNull(code, "error code");
        messageKey = Objects.requireNonNull(messageKey, "message key");
        arguments = Map.copyOf(Objects.requireNonNull(arguments, "error arguments"));
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Error code is outside the stable grammar");
        }
        if (!CODE.matcher(messageKey).matches() || arguments.size() > MAX_ARGUMENTS
                || arguments.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null
                        || !CODE.matcher(entry.getKey()).matches() || entry.getValue().length() > 256
                        || entry.getValue().chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("Error payload is outside the stable bounds");
        }
    }
}
