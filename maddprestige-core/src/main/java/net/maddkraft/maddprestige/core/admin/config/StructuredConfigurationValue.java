package net.maddkraft.maddprestige.core.admin.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable schema-bound value tree used by command and future visual configuration editors. */
public record StructuredConfigurationValue(Map<String, Object> fields) {
    private static final int MAXIMUM_DEPTH = 16;
    private static final int MAXIMUM_ENTRIES = 2_304;

    public StructuredConfigurationValue {
        Counter counter = new Counter();
        fields = immutableMap(Objects.requireNonNull(fields, "structured fields"), 1, counter);
    }

    private static Map<String, Object> immutableMap(Map<String, ?> source, int depth, Counter counter) {
        requireDepth(depth);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (source.keySet().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Structured configuration key is not a safe YAML path segment");
        }
        source.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!key.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,127}")) {
                throw new IllegalArgumentException("Structured configuration key is not a safe YAML path segment");
            }
            count(counter);
            result.put(key, immutableValue(value, depth + 1, counter));
        });
        return Collections.unmodifiableMap(result);
    }

    private static Object immutableValue(Object value, int depth, Counter counter) {
        Objects.requireNonNull(value, "structured value");
        requireDepth(depth);
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> typed = new LinkedHashMap<>();
            map.forEach((key, child) -> {
                if (!(key instanceof String text)) {
                    throw new IllegalArgumentException("Structured configuration maps require string keys");
                }
                typed.put(text, child);
            });
            return immutableMap(typed, depth, counter);
        }
        if (value instanceof List<?> list) {
            ArrayList<Object> result = new ArrayList<>(list.size());
            for (Object child : list) {
                count(counter);
                result.add(immutableValue(child, depth + 1, counter));
            }
            return List.copyOf(result);
        }
        if (value instanceof String text) {
            if (text.length() > 4_096 || text.codePoints().anyMatch(character -> Character.isISOControl(character)
                    && character != '\t')) {
                throw new IllegalArgumentException("Structured configuration strings must be bounded safe text");
            }
            return text;
        }
        if (value instanceof Boolean || value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger || value instanceof BigDecimal) {
            return value;
        }
        throw new IllegalArgumentException("Unsupported structured configuration value: "
                + value.getClass().getSimpleName());
    }

    private static void requireDepth(int depth) {
        if (depth > MAXIMUM_DEPTH) {
            throw new IllegalArgumentException("Structured configuration exceeds the maximum depth");
        }
    }

    private static void count(Counter counter) {
        if (++counter.value > MAXIMUM_ENTRIES) {
            throw new IllegalArgumentException("Structured configuration exceeds the maximum entry count");
        }
    }

    private static final class Counter {
        private int value;
    }
}
