package net.maddkraft.maddprestige.core.entitlement;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public record EntitlementValue(EntitlementValueType type, String canonical) {
    public EntitlementValue {
        type = Objects.requireNonNull(type, "type");
        canonical = canonicalize(type, Objects.requireNonNull(canonical, "canonical value"));
    }

    public static EntitlementValue integer(long value) {
        return new EntitlementValue(EntitlementValueType.INTEGER, Long.toString(value));
    }

    public static EntitlementValue decimal(String value) {
        return new EntitlementValue(EntitlementValueType.EXACT_DECIMAL, value);
    }

    public static EntitlementValue bool(boolean value) {
        return new EntitlementValue(EntitlementValueType.BOOLEAN, Boolean.toString(value));
    }

    public BigDecimal number() {
        return switch (type) {
            case INTEGER -> new BigDecimal(new BigInteger(canonical));
            case EXACT_DECIMAL -> ExactDecimal.parse(canonical).asBigDecimal();
            case BOOLEAN -> throw new IllegalStateException("Boolean entitlement is not numeric");
        };
    }

    public boolean booleanValue() {
        if (type != EntitlementValueType.BOOLEAN) {
            throw new IllegalStateException("Numeric entitlement is not boolean");
        }
        return Boolean.parseBoolean(canonical);
    }

    static EntitlementValue numeric(EntitlementValueType type, BigDecimal value) {
        if (type == EntitlementValueType.INTEGER) {
            return integer(value.longValueExact());
        }
        if (value.precision() > 38 || Math.max(value.scale(), 0) > 18) {
            throw new ArithmeticException("Entitlement decimal exceeds precision/scale bounds");
        }
        return decimal(value.toPlainString());
    }

    private static String canonicalize(EntitlementValueType type, String value) {
        return switch (type) {
            case INTEGER -> Long.toString(Long.parseLong(value));
            case EXACT_DECIMAL -> {
                ExactDecimal decimal = ExactDecimal.parse(value);
                if (decimal.precision() > 38 || decimal.scale() > 18) {
                    throw new IllegalArgumentException("Entitlement decimal exceeds precision/scale bounds");
                }
                yield decimal.toString();
            }
            case BOOLEAN -> {
                if (!"true".equals(value) && !"false".equals(value)) {
                    throw new IllegalArgumentException("Boolean entitlement must be exactly true or false");
                }
                yield value;
            }
        };
    }
}
