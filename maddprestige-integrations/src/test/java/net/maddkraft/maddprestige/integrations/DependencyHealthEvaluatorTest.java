package net.maddkraft.maddprestige.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DependencyHealthEvaluatorTest {
    private final DependencyHealthEvaluator evaluator = new DependencyHealthEvaluator(
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    @Test
    @DisplayName("[A50][A67] Optional dependency failures are structured and isolated")
    void classifiesDependencyState() {
        assertEquals(ProviderHealthState.NOT_INSTALLED, evaluator.evaluate(false, false, false, false).state());
        assertEquals(ProviderHealthState.UNSUPPORTED, evaluator.evaluate(true, false, true, true).state());
        assertEquals(ProviderHealthState.INACTIVE, evaluator.evaluate(true, true, false, true).state());
        assertEquals(ProviderHealthState.UNAVAILABLE, evaluator.evaluate(true, true, true, false).state());
        assertEquals(ProviderHealthState.ACTIVE, evaluator.evaluate(true, true, true, true).state());
    }
}
