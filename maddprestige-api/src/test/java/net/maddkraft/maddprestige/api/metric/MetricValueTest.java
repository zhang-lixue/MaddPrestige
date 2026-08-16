package net.maddkraft.maddprestige.api.metric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MetricValueTest {
    @Test
    @DisplayName("[A09] Typed numeric, duration, count, and currency values compare exactly")
    void numericFamiliesAreExact() {
        assertTrue(MetricValue.integer(10).compareTo(MetricValue.integer(9)) > 0);
        assertEquals("100.5", MetricValue.decimal("100.500").canonical());
        assertEquals("PT2H", MetricValue.parse(MetricValueType.DURATION, "2h").canonical());
        assertEquals(MetricValue.duration(Duration.ofMinutes(120)),
                MetricValue.parse(MetricValueType.DURATION, "PT2H"));
        assertEquals("0", MetricValue.count(0).canonical());
        assertEquals("250.01", MetricValue.parse(MetricValueType.CURRENCY_AMOUNT, "250.010").canonical());
    }

    @Test
    @DisplayName("[A09] Boolean/string/enum values reject coercion and only support equality")
    void nonNumericFamiliesAreStrict() {
        assertEquals("true", MetricValue.bool(true).canonical());
        assertEquals("alpha", MetricValue.parse(MetricValueType.ENUM, "alpha").canonical());
        assertEquals("Text", MetricValue.parse(MetricValueType.STRING, "Text").canonical());
        assertThrows(IllegalArgumentException.class,
                () -> MetricValue.parse(MetricValueType.BOOLEAN, "TRUE"));
        assertFalse(MetricOperator.GREATER_THAN.supports(MetricValueType.BOOLEAN));
        assertTrue(MetricOperator.EQUAL.supports(MetricValueType.ENUM));
    }

    @Test
    @DisplayName("[A09] Invalid typed targets fail instead of permissive string coercion")
    void invalidValuesFail() {
        assertThrows(NumberFormatException.class,
                () -> MetricValue.parse(MetricValueType.INTEGER, "seventy"));
        assertThrows(IllegalArgumentException.class,
                () -> MetricValue.parse(MetricValueType.EXACT_DECIMAL, "1e3"));
        assertThrows(IllegalArgumentException.class,
                () -> MetricValue.parse(MetricValueType.COUNT, "-1"));
        assertThrows(IllegalArgumentException.class,
                () -> MetricValue.parse(MetricValueType.DURATION, "two hours"));
    }
}
