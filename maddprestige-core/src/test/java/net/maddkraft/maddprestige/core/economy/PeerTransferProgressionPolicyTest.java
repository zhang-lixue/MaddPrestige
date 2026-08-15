package net.maddkraft.maddprestige.core.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import net.maddkraft.maddprestige.api.value.ExactDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PeerTransferProgressionPolicyTest {
    @Test
    @DisplayName("[A54] Player-to-player volume gives zero progression-income credit by default")
    void safeDefaultIsZero() {
        PeerTransferProgressionPolicy policy = PeerTransferProgressionPolicy.safeDefault();
        assertFalse(policy.enabled());
        assertEquals(ExactDecimal.ZERO, policy.creditWeight());
    }
}
