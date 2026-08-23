package net.maddkraft.maddprestige.core.authorization;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Structured public-semantic authorization blocker. The diagnostic text is retained for logs and compatibility only;
 * presentation must use {@link #kind()} and {@link #facts()}.
 */
public record AuthorizationBlocker(
        AuthorizationBlockerKind kind,
        Map<String, String> facts,
        String diagnostic) {
    private static final Pattern FACT_NAME = Pattern.compile("[a-z][a-z0-9_]{0,31}");

    public AuthorizationBlocker {
        kind = Objects.requireNonNull(kind, "blocker kind");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        Objects.requireNonNull(facts, "blocker facts").forEach((name, value) -> {
            if (name == null || !FACT_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid blocker fact name: " + name);
            }
            copy.put(name, Objects.requireNonNull(value, "blocker fact value"));
        });
        facts = java.util.Collections.unmodifiableMap(copy);
        diagnostic = Objects.requireNonNull(diagnostic, "blocker diagnostic");
    }

    public static AuthorizationBlocker of(
            AuthorizationBlockerKind kind,
            String diagnostic,
            Object... nameValuePairs) {
        if (nameValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Blocker facts must be name/value pairs");
        }
        LinkedHashMap<String, String> facts = new LinkedHashMap<>();
        for (int index = 0; index < nameValuePairs.length; index += 2) {
            facts.put(String.valueOf(nameValuePairs[index]), String.valueOf(nameValuePairs[index + 1]));
        }
        return new AuthorizationBlocker(kind, facts, diagnostic);
    }

    /** Compatibility-only blocker for callers that still construct internal plans from diagnostic text. */
    public static AuthorizationBlocker unknown(String diagnostic) {
        return of(AuthorizationBlockerKind.UNKNOWN, diagnostic);
    }

    public static List<String> diagnostics(List<AuthorizationBlocker> blockers) {
        return List.copyOf(blockers).stream().map(AuthorizationBlocker::diagnostic).toList();
    }

    public static List<AuthorizationBlocker> unknownAll(List<String> diagnostics) {
        return List.copyOf(diagnostics).stream().map(AuthorizationBlocker::unknown).toList();
    }
}
