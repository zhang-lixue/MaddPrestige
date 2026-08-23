package net.maddkraft.maddprestige.core.admin.presentation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Locale-neutral public presentation: stable catalog key plus machine/data arguments only. */
public record MessageReference(String key, Map<String, String> arguments) {
    private static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,127}");
    private static final Pattern ARGUMENT = Pattern.compile("[a-z][a-z0-9_-]{0,31}");

    public MessageReference {
        key = Objects.requireNonNull(key, "message key");
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid message key: " + key);
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        Objects.requireNonNull(arguments, "message arguments").forEach((name, value) -> {
            if (name == null || !ARGUMENT.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid message argument name: " + name);
            }
            copy.put(name, Objects.requireNonNull(value, "message argument value"));
        });
        arguments = java.util.Collections.unmodifiableMap(copy);
    }

    public static MessageReference of(String key, Object... nameValuePairs) {
        if (nameValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Message arguments must be name/value pairs");
        }
        LinkedHashMap<String, String> arguments = new LinkedHashMap<>();
        for (int index = 0; index < nameValuePairs.length; index += 2) {
            arguments.put(String.valueOf(nameValuePairs[index]), String.valueOf(nameValuePairs[index + 1]));
        }
        return new MessageReference(key, arguments);
    }

    public Optional<String> argument(String name) {
        return Optional.ofNullable(arguments.get(name));
    }

    /** Machine-readable compatibility/debug form; production Paper rendering must use the catalog. */
    public String diagnosticForm() {
        return "[" + key + "]" + (arguments.isEmpty() ? "" : " " + arguments);
    }
}
