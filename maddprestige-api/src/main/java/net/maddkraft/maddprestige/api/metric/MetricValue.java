package net.maddkraft.maddprestige.api.metric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.maddkraft.maddprestige.api.annotation.Stable;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

/**
 * Immutable typed metric value with a deterministic canonical wire representation.
 *
 * @param type non-null value type
 * @param canonical non-null input representation, canonicalized at construction and bounded to 4096 characters
 */
@Stable
public record MetricValue(MetricValueType type, String canonical) implements Comparable<MetricValue> {
    private static final int MAX_CANONICAL_LENGTH = 4096;
    private static final Pattern SHORT_DURATION = Pattern.compile("([0-9]+)(ms|s|m|h|d)");

    public MetricValue {
        type = Objects.requireNonNull(type, "metric value type");
        canonical = canonicalize(type, Objects.requireNonNull(canonical, "canonical value"));
    }

    /**
     * Parses and canonicalizes one typed value without I/O.
     *
     * @param type non-null value type
     * @param text non-null bounded source representation
     * @return immutable canonical value
     */
    public static MetricValue parse(MetricValueType type, String text) {
        return new MetricValue(type, text);
    }

    /**
     * Creates a signed integer value.
     *
     * @param value signed 64-bit integer
     * @return immutable canonical value
     */
    public static MetricValue integer(long value) {
        return new MetricValue(MetricValueType.INTEGER, Long.toString(value));
    }

    /**
     * Creates an exact decimal without floating-point conversion.
     *
     * @param value non-null exact decimal representation
     * @return immutable canonical exact-decimal value
     */
    public static MetricValue decimal(String value) {
        return new MetricValue(MetricValueType.EXACT_DECIMAL, value);
    }

    /**
     * Creates a non-negative count.
     *
     * @param value non-negative 64-bit count
     * @return immutable canonical count
     */
    public static MetricValue count(long value) {
        return new MetricValue(MetricValueType.COUNT, Long.toString(value));
    }

    /**
     * Creates a non-negative duration.
     *
     * @param value non-null, non-negative duration
     * @return immutable ISO-8601 canonical duration
     */
    public static MetricValue duration(Duration value) {
        return new MetricValue(MetricValueType.DURATION, Objects.requireNonNull(value, "duration").toString());
    }

    /**
     * Creates a boolean value.
     *
     * @param value boolean state
     * @return immutable canonical boolean
     */
    public static MetricValue bool(boolean value) {
        return new MetricValue(MetricValueType.BOOLEAN, Boolean.toString(value));
    }

    /**
     * Returns an exact numeric projection; durations are represented as integral milliseconds.
     *
     * @return non-null exact decimal projection
     * @throws IllegalStateException when this value's type is not numeric
     */
    public BigDecimal asNumber() {
        return switch (type) {
            case INTEGER, COUNT -> new BigDecimal(new BigInteger(canonical));
            case EXACT_DECIMAL, CURRENCY_AMOUNT -> ExactDecimal.parse(canonical).asBigDecimal();
            case DURATION -> BigDecimal.valueOf(Duration.parse(canonical).toMillis());
            default -> throw new IllegalStateException(type + " is not numeric");
        };
    }

    /**
     * Subtracts a same-typed numeric baseline without mutating either value.
     *
     * @param baseline non-null value with exactly the same type
     * @return immutable difference using this value's type
     * @throws IllegalArgumentException when the types differ or the typed result is invalid
     * @throws IllegalStateException when this value's type is not numeric
     */
    public MetricValue subtract(MetricValue baseline) {
        requireSameType(baseline);
        if (!type.isNumeric()) {
            throw new IllegalStateException("Cannot subtract " + type + " values");
        }
        return fromNumber(type, asNumber().subtract(baseline.asNumber()));
    }

    /**
     * Converts an exact decimal to a compatible numeric metric type without rounding.
     *
     * @param type non-null numeric target type
     * @param value non-null exact value
     * @return immutable typed value
     * @throws ArithmeticException when an integral target would require rounding or overflow
     * @throws IllegalArgumentException when the type is non-numeric or the value violates its domain
     */
    public static MetricValue fromNumber(MetricValueType type, BigDecimal value) {
        Objects.requireNonNull(value, "value");
        return switch (type) {
            case INTEGER -> new MetricValue(type, value.toBigIntegerExact().toString());
            case COUNT -> {
                BigInteger count = value.toBigIntegerExact();
                if (count.signum() < 0) {
                    throw new IllegalArgumentException("Count cannot be negative");
                }
                yield new MetricValue(type, count.toString());
            }
            case EXACT_DECIMAL, CURRENCY_AMOUNT -> new MetricValue(type, value.toPlainString());
            case DURATION -> {
                long millis = value.longValueExact();
                if (millis < 0) {
                    throw new IllegalArgumentException("Duration cannot be negative");
                }
                yield new MetricValue(type, Duration.ofMillis(millis).toString());
            }
            default -> throw new IllegalArgumentException(type + " does not accept numeric values");
        };
    }

    /**
     * Compares two values of exactly the same type using numeric or canonical lexical semantics.
     *
     * @param other non-null same-typed value
     * @return negative, zero, or positive according to the typed ordering
     * @throws IllegalArgumentException when the types differ
     */
    @Override
    public int compareTo(MetricValue other) {
        requireSameType(other);
        return switch (type) {
            case INTEGER, EXACT_DECIMAL, DURATION, COUNT, CURRENCY_AMOUNT -> asNumber().compareTo(other.asNumber());
            case BOOLEAN -> Boolean.compare(Boolean.parseBoolean(canonical), Boolean.parseBoolean(other.canonical));
            case STRING, ENUM -> canonical.compareTo(other.canonical);
        };
    }

    private void requireSameType(MetricValue other) {
        Objects.requireNonNull(other, "other value");
        if (type != other.type) {
            throw new IllegalArgumentException("Metric value types differ: " + type + " and " + other.type);
        }
    }

    private static String canonicalize(MetricValueType type, String text) {
        if (text.isEmpty() || text.length() > MAX_CANONICAL_LENGTH) {
            throw new IllegalArgumentException("Metric value is empty or exceeds the stable bound");
        }
        return switch (type) {
            case INTEGER -> new BigInteger(text).toString();
            case COUNT -> {
                BigInteger value = new BigInteger(text);
                if (value.signum() < 0) {
                    throw new IllegalArgumentException("Count cannot be negative");
                }
                yield value.toString();
            }
            case EXACT_DECIMAL, CURRENCY_AMOUNT -> ExactDecimal.parse(text).toString();
            case DURATION -> parseDuration(text).toString();
            case BOOLEAN -> {
                if (!"true".equals(text) && !"false".equals(text)) {
                    throw new IllegalArgumentException("Boolean metric value must be exactly true or false");
                }
                yield text;
            }
            case STRING, ENUM -> text;
        };
    }

    private static Duration parseDuration(String text) {
        if (text.startsWith("P")) {
            Duration value = Duration.parse(text);
            if (value.isNegative()) {
                throw new IllegalArgumentException("Duration cannot be negative");
            }
            return value;
        }
        Matcher matcher = SHORT_DURATION.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Duration must use ISO-8601 or a value such as 2h, 30m, or 500ms");
        }
        long amount = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2)) {
            case "ms" -> Duration.ofMillis(amount);
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            default -> throw new IllegalStateException("Unexpected duration suffix");
        };
    }
}
