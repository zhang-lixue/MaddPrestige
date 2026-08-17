package net.maddkraft.maddprestige.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.metric.MetricSampleStatus;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.config.ConfigCompiler;
import net.maddkraft.maddprestige.core.config.ConfigDraft;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeState;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.core.stage.StageConfigurationCompiler;
import net.maddkraft.maddprestige.core.requirement.CatchUpProfile;
import net.maddkraft.maddprestige.core.requirement.CompletionMode;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;
import net.maddkraft.maddprestige.core.requirement.RequirementDefinition;
import net.maddkraft.maddprestige.core.requirement.RequirementLeaf;
import net.maddkraft.maddprestige.core.requirement.RequirementMetricCollector;
import net.maddkraft.maddprestige.core.requirement.RequirementTarget;
import net.maddkraft.maddprestige.core.requirement.ScalingProfile;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteConfigRevisionRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteFoundation;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteMigrations;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerPrestigeRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerStageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PhaseSevenAcceptanceFixtureTest {
    private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String OWNER_PROGRESSION = """
            schema-version: 3
            active: true
            reconciliation-policy: warn-only
            baseline: wanderer
            stages:
              wanderer: {enabled: true, display-name: Wanderer, projection: none}
              curious: {enabled: true, display-name: Curious, projection: {type: group, provider: luckperms, group: curious}}
              dreamer: {enabled: true, display-name: Dreamer, projection: {type: group, provider: luckperms, group: dreamer}}
              tea_guest: {enabled: true, display-name: Tea Guest, projection: {type: group, provider: luckperms, group: tea_guest}}
              wonderlander: {enabled: true, display-name: Wonderlander, projection: {type: group, provider: luckperms, group: wonderlander}}
              madcap: {enabled: true, display-name: Madcap, projection: {type: group, provider: luckperms, group: madcap}}
            order: [wanderer, curious, dreamer, tea_guest, wonderlander, madcap]
            """;

    @Test
    @DisplayName("[A71] Exact owner ladder compiles as configuration data with immutable IDs and order")
    void ownerLadderCompilesExactly() {
        ConfigDraft draft = new ConfigDraft(UUID.randomUUID(), Optional.empty(),
                Map.of("progression.yml", OWNER_PROGRESSION),
                new Actor("SYSTEM", Optional.empty(), "Phase 7 fixture"), NOW);
        var compiled = new StageConfigurationCompiler().compile(new ConfigCompiler().compile(draft));
        assertFalse(compiled.validation().hasErrors(), compiled.validation().findings().toString());
        var ladder = compiled.configuration().orElseThrow();
        assertEquals(List.of("wanderer", "curious", "dreamer", "tea_guest", "wonderlander", "madcap"),
                ladder.order().stream().map(id -> id.value()).toList());
        assertEquals(List.of("Wanderer", "Curious", "Dreamer", "Tea Guest", "Wonderlander", "Madcap"),
                ladder.order().stream().map(ladder.stages()::get).map(stage -> stage.displayName()).toList());
        assertEquals("wanderer", ladder.baselineStage().orElseThrow().value());
        assertEquals(Set.of("curious", "dreamer", "tea_guest", "wonderlander", "madcap"),
                ladder.managedGroups(new ProviderId("luckperms")));
    }

    @Test
    @DisplayName("[A71] Exact ladder progresses, Prestiges to wanderer, and reopens from durable SQLite state")
    void ownerLadderProgressionPrestigeAndReopenAreDurable(@TempDir Path temporaryDirectory) {
        var compiled = new StageConfigurationCompiler().compile(new ConfigCompiler().compile(new ConfigDraft(
                UUID.randomUUID(), Optional.empty(), Map.of("progression.yml", OWNER_PROGRESSION),
                new Actor("SYSTEM", Optional.empty(), "A71 durable fixture"), NOW)));
        var ladder = compiled.configuration().orElseThrow();
        ConfigRevisionId revision = new ConfigRevisionId("owner_review_revision");
        Path database = temporaryDirectory.resolve("a71-owner-ladder.sqlite");
        SqliteFoundation foundation = new SqliteFoundation(database);
        new MigrationRunner(foundation,
                new FileBackupService(database, temporaryDirectory.resolve("backups"), CLOCK), CLOCK)
                .migrate(SqliteMigrations.phaseSix());
        new SqliteConfigRevisionRepository(foundation).insert(revision,
                net.maddkraft.maddprestige.core.config.RevisionHasher.hashText(OWNER_PROGRESSION));
        SqlitePlayerStageRepository stages = new SqlitePlayerStageRepository(foundation);
        SqlitePlayerPrestigeRepository prestiges = new SqlitePlayerPrestigeRepository(foundation);

        UUID playerId = UUID.randomUUID();
        PlayerStageState state = new PlayerStageState(playerId, new StageId("wanderer"), 0, revision,
                NOW, NOW, NOW, Optional.empty(), Optional.empty(), Optional.empty());
        stages.insert(state);
        PlayerPrestigeState prestige = new PlayerPrestigeState(playerId, 0, 0, 0, revision,
                new ScopeId("prestige_0"), Optional.empty(), NOW, NOW);
        prestiges.insert(prestige);
        assertEquals(Set.of(), projectedManagedGroup(ladder, state.stageId()));

        int step = 0;
        for (StageId target : ladder.order().subList(1, ladder.order().size())) {
            PlayerStageState next = state.advanceTo(target, revision, 7, NOW.plusSeconds(++step));
            stages.update(next, state.stateRevision());
            state = stages.find(playerId).orElseThrow();
            assertEquals(target, state.stageId());
            assertEquals(Set.of(target.value()), projectedManagedGroup(ladder, target));
        }

        PlayerStageState reset = state.advanceTo(new StageId("wanderer"), revision, 7,
                NOW.plusSeconds(++step));
        stages.update(reset, state.stateRevision());
        PlayerPrestigeState advanced = prestige.advance(1, 1, revision,
                new ScopeId("prestige_1"), NOW.plusSeconds(step));
        prestiges.update(advanced, prestige.stateRevision());

        SqliteFoundation reopened = new SqliteFoundation(database);
        PlayerStageState reopenedStage = new SqlitePlayerStageRepository(reopened).find(playerId).orElseThrow();
        PlayerPrestigeState reopenedPrestige = new SqlitePlayerPrestigeRepository(reopened)
                .find(playerId).orElseThrow();
        assertEquals(new StageId("wanderer"), reopenedStage.stageId());
        assertEquals(Set.of(), projectedManagedGroup(ladder, reopenedStage.stageId()));
        assertEquals(1, reopenedPrestige.currentPrestige());
        assertEquals(1, reopenedPrestige.lifetimePrestige());
        assertEquals(new ScopeId("prestige_1"), reopenedPrestige.prestigeScope());
        assertEquals(6, reopenedStage.stateRevision());
    }

    @Test
    @DisplayName("[A75] Generic provider is dormant, fail-closed, generation-pinned, rebound, and removable")
    void fakeCourtMetricUsesCompleteGenericRegistryLifecycle() {
        ProviderRegistry registry = new ProviderRegistry();
        FakeCourtMetric provider = new FakeCourtMetric(47);
        var registration = registry.register("fixture", provider);
        assertEquals(ActivationState.INACTIVE,
                registry.find(FakeCourtMetric.ID).orElseThrow().activation());
        RequirementLeaf requirement = courtRequirement();
        RequirementMetricCollector collector = new RequirementMetricCollector(registry, CLOCK);

        MetricSample dormant = collector.collect(UUID.randomUUID(), requirement,
                Map.of(FakeCourtMetric.ID, registration.generation())).toCompletableFuture().join()
                .get(requirement.id());
        assertEquals(MetricSampleStatus.UNAVAILABLE, dormant.status());
        assertEquals(0, provider.reads.get());

        registry.activate(registration);
        assertTrue(registry.acceptsEvent(registration));
        MetricSample sample = collector.collect(UUID.randomUUID(), requirement,
                Map.of(FakeCourtMetric.ID, registration.generation())).toCompletableFuture().join()
                .get(requirement.id());
        assertEquals("47", sample.value().orElseThrow().canonical());
        assertEquals(registration.generation(), sample.providerGeneration());
        assertEquals(1, provider.reads.get());

        provider.health = ProviderHealthState.UNAVAILABLE;
        MetricSample unhealthy = collector.collect(UUID.randomUUID(), requirement,
                Map.of(FakeCourtMetric.ID, registration.generation())).toCompletableFuture().join()
                .get(requirement.id());
        assertEquals(MetricSampleStatus.UNAVAILABLE, unhealthy.status());
        assertEquals(1, provider.reads.get());

        registry.unregister(registration);
        FakeCourtMetric replacement = new FakeCourtMetric(73);
        var rebound = registry.register("fixture", replacement);
        registry.activate(rebound);
        MetricSample stale = collector.collect(UUID.randomUUID(), requirement,
                Map.of(FakeCourtMetric.ID, registration.generation())).toCompletableFuture().join()
                .get(requirement.id());
        assertEquals(MetricSampleStatus.UNAVAILABLE, stale.status());
        assertEquals(0, replacement.reads.get());
        MetricSample current = collector.collect(UUID.randomUUID(), requirement,
                Map.of(FakeCourtMetric.ID, rebound.generation())).toCompletableFuture().join()
                .get(requirement.id());
        assertEquals("73", current.value().orElseThrow().canonical());
        assertEquals(registration.generation() + 1, rebound.generation());

        registry.deactivate(rebound);
        MetricSample removed = collector.collect(UUID.randomUUID(), requirement,
                Map.of(FakeCourtMetric.ID, rebound.generation())).toCompletableFuture().join()
                .get(requirement.id());
        assertEquals(MetricSampleStatus.UNAVAILABLE, removed.status());
        assertEquals(1, replacement.reads.get());
        registry.unregister(rebound);
        assertTrue(registry.find(FakeCourtMetric.ID).isEmpty());
    }

    private static final class FakeCourtMetric implements MetricProvider {
        private static final ProviderId ID = new ProviderId("fixture_court");
        private static final MetricId SCORE = new MetricId("court_score");
        private final AtomicInteger reads = new AtomicInteger();
        private final long value;
        private ProviderHealthState health = ProviderHealthState.ACTIVE;
        private final MetricDescriptor metric = new MetricDescriptor(ID, SCORE, MetricValueType.COUNT,
                MetricOperator.compatibleWith(MetricValueType.COUNT), Set.of(MetricReadMode.CURRENT), false,
                MetricMonotonicity.NON_MONOTONIC, MetricResetPolicy.NOT_APPLICABLE, Map.of(), "Court score",
                "Fake qualification metric", "points", "fixture");

        private FakeCourtMetric(long value) {
            this.value = value;
        }

        @Override
        public Collection<MetricDescriptor> metrics() {
            return List.of(metric);
        }

        @Override
        public CompletionStage<Map<MetricQuery, MetricSample>> read(
                UUID playerId, List<MetricQuery> queries, long providerGeneration) {
            reads.incrementAndGet();
            return CompletableFuture.completedFuture(Map.of(queries.getFirst(),
                    MetricSample.available(MetricValue.count(value), providerGeneration, NOW, "fixture-court")));
        }

        @Override
        public ProviderDescriptor descriptor() {
            return new ProviderDescriptor(ID, "fixture", "phase7", "test", List.of(),
                    List.of(new CapabilityDescriptor("court_score", "metric", "Fake Court score", Map.of())));
        }

        @Override
        public ProviderHealth health() {
            return new ProviderHealth(health, "fixture." + health.name().toLowerCase(),
                    "Generic fake provider", NOW);
        }
    }

    private static RequirementLeaf courtRequirement() {
        return new RequirementLeaf(RequirementDefinition.create(new RequirementId("court_threshold"),
                FakeCourtMetric.ID, FakeCourtMetric.SCORE, MetricOperator.GREATER_OR_EQUAL,
                RequirementTarget.single(MetricValue.count(40)), MeasurementScope.ABSOLUTE,
                CompletionMode.LIVE, ScalingProfile.none(), CatchUpProfile.disabled(), Map.of(), Map.of(), false));
    }

    private static Set<String> projectedManagedGroup(
            net.maddkraft.maddprestige.core.stage.StageConfiguration ladder, StageId stageId) {
        return ladder.stages().get(stageId).projection().groupName().map(Set::of).orElseGet(Set::of);
    }
}
