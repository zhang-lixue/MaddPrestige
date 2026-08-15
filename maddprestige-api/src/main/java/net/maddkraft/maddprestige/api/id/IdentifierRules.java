package net.maddkraft.maddprestige.api.id;

import java.util.Objects;
import java.util.regex.Pattern;

public final class IdentifierRules {
    public static final int MAX_LENGTH = 64;
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    private IdentifierRules() {
    }

    public static String requireValid(String value, String type) {
        Objects.requireNonNull(value, type + " value");
        if (!SAFE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(type + " must match " + SAFE_ID.pattern() + ": " + value);
        }
        return value;
    }
}
