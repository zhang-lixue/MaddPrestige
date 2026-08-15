package net.maddkraft.maddprestige.api.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentifierTest {
    @Test
    @DisplayName("[A03][A04] Immutable identifiers accept only the approved stable syntax")
    void validatesCanonicalSyntax() {
        assertEquals("stage.one", new StageId("stage.one").value());
        assertEquals("provider-name", new ProviderId("provider-name").value());
        assertThrows(IllegalArgumentException.class, () -> new StageId("Display Name"));
        assertThrows(IllegalArgumentException.class, () -> new MetricId("../unsafe"));
    }
}
