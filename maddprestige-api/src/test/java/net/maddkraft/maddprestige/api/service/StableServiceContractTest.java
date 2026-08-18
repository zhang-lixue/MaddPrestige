package net.maddkraft.maddprestige.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import net.maddkraft.maddprestige.api.annotation.Stable;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.ProviderCallContext;
import net.maddkraft.maddprestige.api.provider.ProviderExecutionExpectation;
import net.maddkraft.maddprestige.api.provider.ProviderMetadata;
import net.maddkraft.maddprestige.api.provider.ProviderMetricDefinition;
import net.maddkraft.maddprestige.api.provider.ProviderMetricDimension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StableServiceContractTest {
    @Test
    @DisplayName("[A65][8B] Stable facade is annotated and canonical external provider IDs are bounded")
    void stableFacadeAndProviderIdsAreExplicit() {
        assertTrue(MaddPrestigeService.class.getPackage().isAnnotationPresent(Stable.class));
        assertEquals("example_plugin:progress", new ProviderId("example_plugin:progress").value());
        assertThrows(IllegalArgumentException.class, () -> new ProviderId("Example:progress"));
        assertThrows(IllegalArgumentException.class, () -> new ProviderId("example:progress:forged"));
        assertEquals("owner:" + "x".repeat(31), new ProviderId("owner:" + "x".repeat(31)).value());
        assertThrows(IllegalArgumentException.class, () -> new ProviderId("owner:" + "x".repeat(32)));
    }

    @Test
    @DisplayName("[A65][8B] Stable error payloads reject unbounded and non-canonical data")
    void serviceErrorsAreBounded() {
        assertThrows(IllegalArgumentException.class, () -> new ServiceError("Invalid Code", "message", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ServiceError("valid.code", "x".repeat(1025),
                Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ServiceError("valid.code", "message",
                java.util.stream.IntStream.range(0, 17).boxed().collect(java.util.stream.Collectors.toMap(
                        Object::toString, ignored -> "value"))));
        UUID requestId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new OperationResult(requestId,
                Optional.of(new OperationId(requestId)), OperationKind.RANK_UP,
                OperationStatus.COMPLETED, Optional.empty()));
    }

    @Test
    @DisplayName("[OR8B-06] Provider callback context carries bounded identity, execution, and live cancellation")
    void providerContextExposesFrozenExecutionContract() {
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        Instant deadline = Instant.parse("2026-08-17T20:00:00Z");
        ProviderCallContext context = new ProviderCallContext(UUID.randomUUID(), deadline, "requirements.read",
                ProviderExecutionExpectation.BOUNDED_WORKER, "court_plugin",
                Optional.of(new ProviderId("court_plugin:fixture")), cancelled::get);

        assertEquals(ProviderExecutionExpectation.BOUNDED_WORKER, context.execution());
        assertTrue(context.expired(deadline));
        assertTrue(!context.cancellationRequested());
        cancelled.set(true);
        assertTrue(context.cancellationRequested());
        assertThrows(IllegalArgumentException.class, () -> new ProviderCallContext(UUID.randomUUID(), deadline,
                "requirements.read", ProviderExecutionExpectation.BOUNDED_WORKER, "spoofed",
                Optional.of(new ProviderId("court_plugin:fixture")), () -> false));
    }

    @Test
    @DisplayName("[OR8B-API] Exact and normalized provider metric/dimension ambiguities are rejected")
    void providerMetadataRejectsAmbiguousIdentifiers() {
        ProviderMetricDefinition first = metric("court-visits", Map.of());
        assertThrows(IllegalArgumentException.class, () -> new ProviderMetadata("fixture", "fixture.display_name",
                "1.0.0", List.of(first, first)));
        assertThrows(IllegalArgumentException.class, () -> new ProviderMetadata("fixture", "fixture.display_name",
                "1.0.0", List.of(first, metric("court_visits", Map.of()))));
        Map<String, ProviderMetricDimension> dimensions = Map.of(
                "game-mode", new ProviderMetricDimension("game-mode", false, Set.of(), "game_mode.description"),
                "game_mode", new ProviderMetricDimension("game_mode", false, Set.of(), "game_mode.description"));
        assertThrows(IllegalArgumentException.class, () -> metric("court", dimensions));
        assertEquals("x".repeat(31), new ProviderMetadata("x".repeat(31), "fixture.display_name", "1.0.0",
                List.of()).localId());
        assertThrows(IllegalArgumentException.class, () -> new ProviderMetadata("x".repeat(32),
                "fixture.display_name", "1.0.0", List.of()));
    }

    private static ProviderMetricDefinition metric(
            String id,
            Map<String, ProviderMetricDimension> dimensions) {
        return new ProviderMetricDefinition(new MetricId(id), MetricValueType.COUNT,
                EnumSet.of(MetricOperator.GREATER_OR_EQUAL), EnumSet.of(MetricReadMode.CURRENT), false,
                MetricMonotonicity.MONOTONIC, MetricResetPolicy.NOT_APPLICABLE, dimensions,
                "court.display_name", "court.description", "visits", "owner_supplied");
    }
}
