package net.maddkraft.maddprestige.api.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditValueTest {
    @Test
    @DisplayName("[A57] Sensitive audit values redact by construction")
    void redactsSensitiveValue() {
        AuditValue secret = new AuditValue("database-password", true);
        assertEquals(AuditValue.REDACTED, secret.render(false));
        assertEquals("database-password", secret.render(true));
    }
}
