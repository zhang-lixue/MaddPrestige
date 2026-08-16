package net.maddkraft.maddprestige.api.metric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public record MetricValue(MetricValueType type, String canonical) implements Comparable<MetricValue> {
    private static final Pattern SHORT_DURATION = Pattern.compile("([0-9]+)(ms|s|m|h|d)");

    public MetricValue {
        type = Objects.requireNonNull(type, "metric value type");
        canonical = canonicalize(type, Objects.requireNonNull(canonical, "canonical value"));
    }

    public static MetricValue parse(MetricValueType type, String text) {
        return new MetricValue(type, text);
    }

    public static MetricValue integer(long value) {
        return new MetricValue(MetricValueType.INTEGER, Long.toString(value));
    }

    public static MetricValue decimal(String value) {
        return new MetricValue(MetricValueType.EXACT_DECIMAL, value);
    }

    public static MetricValue count(long value) {
        return new MetricValue(MetricValueType.COUNT, Long.toString(value));
    }

    public static MetricValue duration(Duration value) {
        return new MetricValue(MetricValueType.DURATION, Objects.requireNonNull(value, "duration").toString());
    }

    public static MetricValue bool(boolean value) {
        return new MetricValue(MetricValueType.BOOLEAN, Boolean.toString(value));
    }

    public BigDecimal asNumber() {
        return switch (type) {
            case INTEGER, COUNT -> new BigDecimal(new BigInteger(canonical));
            case EXACT_DECIMAL, CURRENCY_AMOUNT -> ExactDecimal.parse(canonical).asBigDecimal();
            case DURATION -> BigDecimal.valueOf(Duration.parse(canonical).toMillis());
            default -> throw new IllegalStateException(type + " is not numeric");
        };
    }

    public MetricValue subtract(MetricValue baseline) {
        requireSameType(baseline);
        if (!type.isNumeric()) {
            throw new IllegalStateException("Cannot subtract " + type + " values");
        }
        return fromNumber(type, asNumber().subtract(baseline.asNumber()));
    }

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
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Metric value cannot be empty");
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
