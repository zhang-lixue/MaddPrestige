package net.maddkraft.maddprestige.core.config;

import java.util.Objects;
import java.util.regex.Pattern;

public record ContentHash(String value) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ContentHash {
        value = Objects.requireNonNull(value, "hash");
        if (!SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException("Content hash must be lowercase SHA-256");
        }
    }
}
