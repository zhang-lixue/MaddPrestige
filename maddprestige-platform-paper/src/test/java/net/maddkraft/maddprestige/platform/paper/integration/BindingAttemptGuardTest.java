package net.maddkraft.maddprestige.platform.paper.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BindingAttemptGuardTest {
    @Test
    void rejectsOnlyReentrantAttemptForTheSameDependencyAndAllowsRetryAfterRelease() {
        BindingAttemptGuard guard = new BindingAttemptGuard();

        assertTrue(guard.begin("mcMMO"));
        assertFalse(guard.begin("mcMMO"));
        assertTrue(guard.begin("Vault"));

        guard.end("Vault");
        guard.end("mcMMO");
        assertTrue(guard.begin("mcMMO"));
        guard.end("mcMMO");
        assertThrows(IllegalStateException.class, () -> guard.end("mcMMO"));
    }
}
