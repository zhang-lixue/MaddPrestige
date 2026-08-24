package net.maddkraft.maddprestige.platform.paper.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InitialManualProgressActivationTest {
    @Test
    void safelyDormantDiscoveryActivatesManualMetricOnlyWhenMcMmoIsAvailable() {
        assertTrue(MaddPrestigeV2Plugin.initialManualProgressActive(true, false, false));
        assertTrue(MaddPrestigeV2Plugin.initialManualProgressActive(false, true, true));
        assertFalse(MaddPrestigeV2Plugin.initialManualProgressActive(false, true, false));
        assertFalse(MaddPrestigeV2Plugin.initialManualProgressActive(false, false, true));
    }
}
