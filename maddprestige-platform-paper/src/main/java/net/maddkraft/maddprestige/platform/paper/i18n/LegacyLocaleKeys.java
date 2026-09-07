package net.maddkraft.maddprestige.platform.paper.i18n;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Exact locale-key aliases retained for external catalog compatibility. */
final class LegacyLocaleKeys {
    private static final Map<String, String> LEGACY_BY_CANONICAL = Map.of(
            "command.runtime.permission_denied", "phase7.permission_denied",
            "command.runtime.usage", "phase7.usage");
    private static final Map<String, String> CANONICAL_BY_LEGACY = Map.of(
            "phase7.permission_denied", "command.runtime.permission_denied",
            "phase7.usage", "command.runtime.usage");

    private LegacyLocaleKeys() {
    }

    static String canonicalize(String key) {
        String value = Objects.requireNonNull(key, "locale key");
        return CANONICAL_BY_LEGACY.getOrDefault(value, value);
    }

    static Optional<String> legacyForCanonical(String canonicalKey) {
        return Optional.ofNullable(LEGACY_BY_CANONICAL.get(
                Objects.requireNonNull(canonicalKey, "canonical locale key")));
    }

    static Map<String, String> aliases() {
        return LEGACY_BY_CANONICAL;
    }
}
