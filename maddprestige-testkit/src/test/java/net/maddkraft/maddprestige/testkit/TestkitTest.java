package net.maddkraft.maddprestige.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestkitTest {
    @Test
    @DisplayName("[A05][A50] Fake rank/progression providers simulate missing groups and fail-closed health")
    void simulatesProviders() {
        FakeRankProvider ranks = new FakeRankProvider(Set.of("member", "veteran"));
        assertTrue(ranks.exists("member").value().orElseThrow());
        assertFalse(ranks.exists("missing").value().orElseThrow());

        FakeProgressionProvider progression = new FakeProgressionProvider();
        UUID player = UUID.randomUUID();
        MetricId metric = new MetricId("blocks_mined");
        progression.set(player, metric, ExactDecimal.parse("42"));
        assertEquals(ExactDecimal.parse("42"), progression.sample(player, metric).value().orElseThrow());
        progression.healthSimulator().transition(ProviderHealthState.UNAVAILABLE, "fake.outage", "Injected outage");
        assertFalse(progression.sample(player, metric).isSuccess());
    }

    @Test
    @DisplayName("[A59][A60] Failure injection and operation action fakes expose exact action boundaries")
    void injectsActionFailure() {
        FailureInjector failures = new FailureInjector();
        FakeOperationAction action = new FakeOperationAction("reward.execute", failures);
        failures.failNext("reward.execute", 1, new IllegalStateException("injected"));
        assertThrows(IllegalStateException.class, () -> action.execute(OperationId.random()));
        assertEquals(1, action.execute(OperationId.random()).value().orElseThrow());
    }

    @Test
    @DisplayName("[A63] Disposable SQLite fixture creates and removes only its isolated test schema")
    void providesDisposableSqlite() throws Exception {
        java.nio.file.Path path;
        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            path = fixture.database();
            assertTrue(java.nio.file.Files.exists(path));
        }
        assertFalse(java.nio.file.Files.exists(path));
    }
}
