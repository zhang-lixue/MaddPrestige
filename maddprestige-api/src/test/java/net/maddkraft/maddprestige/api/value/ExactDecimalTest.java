package net.maddkraft.maddprestige.api.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExactDecimalTest {
    @Test
    @DisplayName("[A30] Decimal values never use binary floating-point construction")
    void retainsExactCanonicalValue() {
        ExactDecimal value = ExactDecimal.parse("12345678901234567890.0100");
        assertEquals("12345678901234567890.01", value.toString());
        assertEquals(new BigDecimal("12345678901234567890.01"), value.asBigDecimal());
        assertThrows(IllegalArgumentException.class, () -> ExactDecimal.parse("1e3"));
    }
}
