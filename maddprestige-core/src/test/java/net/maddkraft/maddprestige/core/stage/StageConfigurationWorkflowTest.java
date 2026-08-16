package net.maddkraft.maddprestige.core.stage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.rank.ManagedRankState;
import net.maddkraft.maddprestige.api.rank.RankAdapter;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.api.rank.RankProjectionResult;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.core.config.BackupMetadata;
import net.maddkraft.maddprestige.core.config.ConfigDraft;
import net.maddkraft.maddprestige.core.config.ConfigurationService;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StageConfigurationWorkflowTest {
    @Test
    @DisplayName("[A05] Missing external group blocks canonical apply and rank contract exposes no creation operation")
    void missingGroupBlocksApply() {
        ProviderRegistry providers = new ProviderRegistry();
        CatalogAdapter adapter = new CatalogAdapter(Set.of("existing"));
        var registration = providers.register("test-owner", adapter);
        providers.activate(registration);
        StageConfigurationWorkflow workflow = new StageConfigurationWorkflow(new ConfigurationService());
        var candidate = workflow.prepare(draft("missing"), Map.of(), Optional.empty(), providers)
                .toCompletableFuture().join();
        assertTrue(candidate.validation().hasErrors());
        assertTrue(candidate.validation().findings().stream()
                .anyMatch(finding -> finding.code().equals("rank.group.not_found")));
        assertThrows(IllegalStateException.class, () -> workflow.apply(new ConfigRevisionId("revision_1"), candidate,
                Set.of(), backup(), providers));
        assertFalse(workflow.active().isPresent());
        assertTrue(java.util.Arrays.stream(RankAdapter.class.getMethods())
                .noneMatch(method -> method.getName().toLowerCase().contains("create")));
    }

    @Test
    @DisplayName("[A05][A08] Valid targets still require semantic acknowledgements before canonical activation")
    void validTargetsActivateThroughCanonicalService() {
        ProviderRegistry providers = new ProviderRegistry();
        CatalogAdapter adapter = new CatalogAdapter(Set.of("existing"));
        var registration = providers.register("test-owner", adapter);
        providers.activate(registration);
        StageConfigurationWorkflow workflow = new StageConfigurationWorkflow(new ConfigurationService());
        var candidate = workflow.prepare(draft("existing"), Map.of(), Optional.empty(), providers)
                .toCompletableFuture().join();
        assertFalse(candidate.validation().hasErrors(), candidate.validation().toString());
        workflow.apply(new ConfigRevisionId("revision_1"), candidate,
                candidate.validation().findings().stream().map(finding -> finding.code())
                        .collect(java.util.stream.Collectors.toSet()), backup(), providers);
        assertTrue(workflow.active().orElseThrow().configuration().active());
    }

    @Test
    @DisplayName("[A05] Provider generation/activation changes after validation block canonical apply")
    void providerBindingChangeBlocksApply() {
        ProviderRegistry providers = new ProviderRegistry();
        CatalogAdapter adapter = new CatalogAdapter(Set.of("existing"));
        var registration = providers.register("test-owner", adapter);
        providers.activate(registration);
        StageConfigurationWorkflow workflow = new StageConfigurationWorkflow(new ConfigurationService());
        var candidate = workflow.prepare(draft("existing"), Map.of(), Optional.empty(), providers)
                .toCompletableFuture().join();
        providers.deactivate(registration);
        assertThrows(IllegalStateException.class, () -> workflow.apply(new ConfigRevisionId("revision_1"), candidate,
                candidate.validation().findings().stream().map(finding -> finding.code())
                        .collect(java.util.stream.Collectors.toSet()), backup(), providers));
        assertTrue(workflow.active().isEmpty());
    }

    @Test
    @DisplayName("[A05][A49] Synchronous and exceptional rank target failures become validation findings")
    void normalizesBothRankTargetFailureShapes() {
        for (FailureMode mode : List.of(FailureMode.SYNCHRONOUS, FailureMode.ASYNCHRONOUS)) {
            ProviderRegistry providers = new ProviderRegistry();
            var registration = providers.register("test-owner", new CatalogAdapter(Set.of("existing"), mode));
            providers.activate(registration);

            var candidate = new StageConfigurationWorkflow(new ConfigurationService())
                    .prepare(draft("existing"), Map.of(), Optional.empty(), providers)
                    .toCompletableFuture().join();

            assertTrue(candidate.validation().findings().stream().anyMatch(finding ->
                    finding.code().equals("stage.rank_targets.unavailable")), mode.name());
        }
    }

    @Test
    @DisplayName("[D-047] Atomic active snapshots retain exact pins and failed replacement leaves them unchanged")
    void activeSnapshotRetainsExactProviderPins() {
        ProviderRegistry providers = new ProviderRegistry();
        CatalogAdapter first = new CatalogAdapter(Set.of("existing"));
        var firstRegistration = providers.register("test-owner", first);
        providers.activate(firstRegistration);
        StageConfigurationWorkflow workflow = new StageConfigurationWorkflow(new ConfigurationService());
        var candidate = workflow.prepare(draft("existing"), Map.of(), Optional.empty(), providers)
                .toCompletableFuture().join();
        long validatedGeneration = candidate.providerGenerations().get(new ProviderId("rank_provider"));
        workflow.apply(new ConfigRevisionId("revision_1"), candidate, acknowledgements(candidate), backup(),
                providers);
        ActiveStageConfiguration active = workflow.activeCanonical().orElseThrow();
        assertEquals(validatedGeneration,
                active.phaseThree().providerGenerations().get(new ProviderId("rank_provider")));
        assertEquals(active.stages().revisionId(), active.phaseThree().revisionId());

        providers.unregister(firstRegistration);
        var replacement = providers.register("test-owner", new CatalogAdapter(Set.of("existing")));
        providers.activate(replacement);
        assertEquals(validatedGeneration, workflow.activePhaseThree().orElseThrow().providerGenerations()
                .get(new ProviderId("rank_provider")), "active pins cannot follow the mutable registry");

        var staleCandidate = workflow.prepare(draft("existing"), Map.of(), Optional.empty(), providers)
                .toCompletableFuture().join();
        providers.deactivate(replacement);
        assertThrows(IllegalStateException.class, () -> workflow.apply(new ConfigRevisionId("revision_2"),
                staleCandidate, acknowledgements(staleCandidate), backup(), providers));
        assertEquals(active, workflow.activeCanonical().orElseThrow(),
                "failed apply must preserve the prior atomic stage/Phase 3 snapshot and pins");
        assertThrows(IllegalArgumentException.class, () -> new ActiveStageConfiguration(active.stages(),
                new net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfigurationSnapshot(
                        new ConfigRevisionId("mixed"), active.phaseThree().configuration(),
                        active.phaseThree().providerGenerations())));
    }

    @Test
    @DisplayName("[A69] Zero-reference removal proceeds only after normal semantic acknowledgement")
    void zeroReferenceRemovalUsesNormalApplyRules() {
        ProviderRegistry providers = new ProviderRegistry();
        StageConfigurationWorkflow workflow = new StageConfigurationWorkflow(new ConfigurationService());
        var initial = workflow.prepare(internalDraft(true), Map.of(), Optional.empty(), providers)
                .toCompletableFuture().join();
        workflow.apply(new ConfigRevisionId("revision_1"), initial, acknowledgements(initial), backup(), providers);

        var removal = workflow.prepare(internalDraft(false), Map.of(), Optional.empty(), providers)
                .toCompletableFuture().join();
        assertFalse(removal.validation().hasErrors());
        assertThrows(IllegalStateException.class, () -> workflow.apply(
                new ConfigRevisionId("revision_2_unacknowledged"), removal, Set.of(), backup(), providers));
        workflow.apply(new ConfigRevisionId("revision_2"), removal, acknowledgements(removal), backup(), providers);
        assertFalse(workflow.active().orElseThrow().configuration().stages().containsKey(new StageId("second")));
    }

    private static ConfigDraft draft(String group) {
        String source = """
                schema-version: 2
                active: true
                baseline: first
                reconciliation-policy: warn-only
                stages:
                  first: {enabled: true, display-name: First, projection: none}
                  second:
                    enabled: true
                    display-name: Second
                    projection: {provider: rank_provider, group: %s}
                order: [first, second]
                """.formatted(group);
        return new ConfigDraft(UUID.randomUUID(), Optional.empty(), Map.of("progression.yml", source),
                new Actor("console", Optional.empty(), "Console"), Instant.now());
    }

    private static BackupMetadata backup() {
        return new BackupMetadata("backup", RevisionHasher.hashText("backup"), Instant.now(), true);
    }

    private static Set<String> acknowledgements(StageConfigurationCandidate candidate) {
        return candidate.validation().findings().stream().map(finding -> finding.code())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static ConfigDraft internalDraft(boolean includeSecond) {
        String secondDefinition = includeSecond
                ? "  second: {enabled: true, display-name: Second, projection: none}\n" : "";
        String order = includeSecond ? "[first, second]" : "[first]";
        String source = """
                schema-version: 2
                active: true
                baseline: first
                stages:
                  first: {enabled: true, display-name: First, projection: none}
                %sorder: %s
                """.formatted(secondDefinition, order);
        return new ConfigDraft(UUID.randomUUID(), Optional.empty(), Map.of("progression.yml", source),
                new Actor("console", Optional.empty(), "Console"), Instant.now());
    }

    private static final class CatalogAdapter implements RankAdapter {
        private final Set<String> groups;
        private final FailureMode failureMode;

        private CatalogAdapter(Set<String> groups) {
            this(groups, FailureMode.NONE);
        }

        private CatalogAdapter(Set<String> groups, FailureMode failureMode) {
            this.groups = groups;
            this.failureMode = failureMode;
        }

        @Override
        public ProviderDescriptor descriptor() {
            return new ProviderDescriptor(new ProviderId("rank_provider"), "test-owner", "1", "1", List.of(),
                    List.of(new CapabilityDescriptor("rank", "rank", "test", Map.of())));
        }

        @Override
        public ProviderHealth health() {
            return new ProviderHealth(ProviderHealthState.AVAILABLE, "available", "available", Clock.systemUTC().instant());
        }

        @Override
        public java.util.concurrent.CompletionStage<Result<Set<String>>> validateTargets(Set<String> groupNames) {
            if (failureMode == FailureMode.SYNCHRONOUS) {
                throw new IllegalStateException("synchronous target outage");
            }
            if (failureMode == FailureMode.ASYNCHRONOUS) {
                return CompletableFuture.failedFuture(new IllegalStateException("asynchronous target outage"));
            }
            return CompletableFuture.completedFuture(Result.success(groups));
        }

        @Override
        public java.util.concurrent.CompletionStage<Result<ManagedRankState>> readManagedState(
                UUID playerId, Set<String> managedGroups) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.concurrent.CompletionStage<Result<RankProjectionResult>> project(RankProjectionRequest request) {
            throw new UnsupportedOperationException();
        }
    }

    private enum FailureMode {
        NONE,
        SYNCHRONOUS,
        ASYNCHRONOUS
    }
}
