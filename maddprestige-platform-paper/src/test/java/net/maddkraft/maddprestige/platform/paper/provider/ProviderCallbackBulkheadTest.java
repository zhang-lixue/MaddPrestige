package net.maddkraft.maddprestige.platform.paper.provider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.maddkraft.maddprestige.api.id.ProviderId;
import org.junit.jupiter.api.Test;

class ProviderCallbackBulkheadTest {
    @Test
    void logicalBudgetSurvivesGenerationReplacementAndIndependentProvidersRemainUsable() {
        ProviderCallbackBulkhead bulkheads = new ProviderCallbackBulkhead();
        ProviderId alphaId = new ProviderId("owner:alpha");
        ProviderId betaId = new ProviderId("owner:beta");
        ProviderCallbackBulkhead.Generation alphaOne = bulkheads.acquire(alphaId);
        ProviderCallbackBulkhead.Generation beta = bulkheads.acquire(betaId);

        for (int index = 0; index < ProviderCallbackBulkhead.MAX_CONCURRENT_CALLBACKS; index++) {
            assertTrue(alphaOne.tryEnter());
        }
        assertFalse(alphaOne.tryEnter());
        assertTrue(beta.tryEnter());
        assertEquals(4, bulkheads.activeCallbacks(alphaId));
        assertEquals(1, bulkheads.activeCallbacks(betaId));

        alphaOne.close();
        ProviderCallbackBulkhead.Generation alphaTwo = bulkheads.acquire(alphaId);
        assertFalse(alphaTwo.tryEnter(), "a rebind must not mint a fresh generation budget");
        beta.exit();
        assertTrue(beta.tryEnter(), "an independent provider key must remain usable");
        beta.exit();

        for (int index = 0; index < ProviderCallbackBulkhead.MAX_CONCURRENT_CALLBACKS; index++) {
            alphaOne.exit();
        }
        assertTrue(alphaTwo.tryEnter());
        alphaTwo.exit();
        alphaTwo.close();
        beta.close();
        assertEquals(0, bulkheads.stateCount());
    }

    @Test
    void repeatedRebindAndUnregisterRetainThenReclaimBlockedIdentityState() {
        ProviderCallbackBulkhead bulkheads = new ProviderCallbackBulkhead();
        ProviderId alphaId = new ProviderId("owner:alpha");
        ProviderCallbackBulkhead.Generation blocked = bulkheads.acquire(alphaId);
        assertTrue(blocked.tryEnter());
        blocked.close();

        for (int index = 0; index < 20; index++) {
            ProviderCallbackBulkhead.Generation replacement = bulkheads.acquire(alphaId);
            assertTrue(replacement.tryEnter());
            replacement.exit();
            replacement.close();
            assertEquals(1, bulkheads.stateCount(), "blocked old work must retain exactly one identity state");
        }

        blocked.exit();
        assertEquals(0, bulkheads.stateCount(), "unregistered identity is reclaimed after its last callback exits");
        ProviderCallbackBulkhead.Generation later = bulkheads.acquire(alphaId);
        assertTrue(later.tryEnter());
        later.exit();
        later.close();
        assertEquals(0, bulkheads.stateCount());
    }
}
