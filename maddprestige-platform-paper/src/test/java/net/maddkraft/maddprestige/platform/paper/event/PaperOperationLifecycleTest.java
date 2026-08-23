package net.maddkraft.maddprestige.platform.paper.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.maddkraft.maddprestige.api.event.OperationEventSnapshot;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.core.plan.RankUpExecutionResult;
import net.maddkraft.maddprestige.core.plan.RankUpExecutionStatus;
import net.maddkraft.maddprestige.core.plan.RankUpPlan;
import net.maddkraft.maddprestige.platform.paper.ExecutionThread;
import net.maddkraft.maddprestige.platform.paper.PaperTaskScheduler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.Test;

class PaperOperationLifecycleTest {
    @Test
    void preAndPostCarryTheIngressRequestIdSeparatelyFromTheDurableOperationId() {
        UUID requestId = UUID.fromString("66bd269d-3394-48df-a579-ae8df7d0ff2c");
        UUID playerId = UUID.fromString("fa66b769-49cd-411c-9cc7-ad9d9dbdc34d");
        OperationId durableId = OperationId.random();
        RankUpPlan plan = mock(RankUpPlan.class);
        when(plan.requestId()).thenReturn(requestId);
        when(plan.operationId()).thenReturn(durableId);
        when(plan.playerId()).thenReturn(playerId);
        when(plan.sourceStage()).thenReturn(new StageId("first"));
        when(plan.targetStage()).thenReturn(new StageId("second"));
        when(plan.configRevision()).thenReturn(new ConfigRevisionId("revision"));
        RankUpExecutionResult result = new RankUpExecutionResult(durableId, RankUpExecutionStatus.COMPLETED,
                "complete");
        Plugin host = mock(Plugin.class);
        PaperTaskScheduler scheduler = new PaperTaskScheduler() {
            @Override
            public <T> java.util.concurrent.CompletionStage<T> submit(
                    ExecutionThread thread,
                    java.util.function.Supplier<T> task) {
                return java.util.concurrent.CompletableFuture.completedFuture(task.get());
            }
        };
        PaperOperationLifecycle lifecycle = new PaperOperationLifecycle(host, scheduler,
                Clock.fixed(Instant.parse("2026-08-17T20:00:00Z"), ZoneOffset.UTC));
        Listener listener = new Listener() { };
        List<OperationEventSnapshot> snapshots = new ArrayList<>();
        RegisteredListener registered = new RegisteredListener(listener, (ignored, event) -> {
            if (event instanceof PreRankUpEvent pre) {
                snapshots.add(pre.snapshot());
            } else if (event instanceof PostRankUpEvent post) {
                snapshots.add(post.snapshot());
            }
        }, EventPriority.NORMAL, mock(Plugin.class), false);
        PreRankUpEvent.getHandlerList().register(registered);
        PostRankUpEvent.getHandlerList().register(registered);
        try {
            lifecycle.beforeRankUp(plan);
            lifecycle.afterRankUp(plan, result);
        } finally {
            PreRankUpEvent.getHandlerList().unregister(registered);
            PostRankUpEvent.getHandlerList().unregister(registered);
        }

        assertEquals(2, snapshots.size());
        assertEquals(requestId, snapshots.get(0).correlationId());
        assertEquals(requestId, snapshots.get(1).correlationId());
        assertFalse(snapshots.get(0).durableOperationId().isPresent());
        assertEquals(durableId, snapshots.get(1).durableOperationId().orElseThrow());
        assertNotEquals(requestId, durableId.value());
    }
}
