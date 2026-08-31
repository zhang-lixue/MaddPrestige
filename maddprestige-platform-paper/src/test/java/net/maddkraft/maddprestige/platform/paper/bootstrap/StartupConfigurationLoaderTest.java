package net.maddkraft.maddprestige.platform.paper.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationApplicationStatus;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationHistoryStore;
import net.maddkraft.maddprestige.core.admin.config.StoredConfigurationRevision;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.persistence.admin.AtomicConfigurationFileStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupConfigurationLoaderTest {
    private static final Instant NOW = Instant.parse("2026-08-17T20:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("[Phase 9E] Production administration exposes only reachable numeric configuration")
    void productionSchemaContainsReachableNumericConfigurationOnly() {
        var schema = ProductionRuntime.productionSchema();
        assertTrue(schema.resolve("integrations.placeholderapi.output.enabled").isPresent());
        assertTrue(schema.resolve("integrations.mcmmo.enabled").isPresent());
        assertTrue(schema.resolve("integrations.craftengine.reward-maximum-quantity").isPresent());
        for (String unreachable : List.of("integrations.rank.reconciliation-policy", "competitions.enabled",
                "integrations.quickshop.progression-income-weight", "database.credentials.password",
                "prestige.reset-policy.progression-stage")) {
            assertTrue(schema.resolve(unreachable).isEmpty(), unreachable);
        }
        assertFalse(schema.resolve("milestones.*.trigger").orElseThrow()
                .allowedValues().staticValues().contains("STAGE_REACHED"));
        assertTrue(ProductionRuntime.providerLifecycleRelated(List.of("phase3.provider.unavailable")));
        assertTrue(ProductionRuntime.providerLifecycleRelated(List.of(
                "requirement.value_type.required", "requirement.reference.unknown", "requirement.group.empty")));
        assertFalse(ProductionRuntime.providerLifecycleRelated(List.of("phase3.provider.unavailable",
                "progression.invalid")));
        assertFalse(ProductionRuntime.providerLifecycleRelated(List.of("requirement.group.empty")));
    }

    @Test
    @DisplayName("[OR8B-03] Fresh seed documents remain dormant when no durable active pointer exists")
    void noPointerMeansNoRuntimeAuthority() throws Exception {
        java.nio.file.Files.createDirectories(temporaryDirectory.resolve("config"));
        java.nio.file.Files.writeString(temporaryDirectory.resolve("config").resolve("progression.yml"),
                "schema-version: 3\n");
        AtomicConfigurationFileStore snapshots = new AtomicConfigurationFileStore(
                temporaryDirectory.resolve("config"), CLOCK);

        assertTrue(StartupConfigurationLoader.load(snapshots, history(Optional.empty())).isEmpty());
    }

    @Test
    @DisplayName("[OR8B-03] Restart restores the exact APPLIED revision identity and documents repeatedly")
    void exactPointerHistoryAndDocumentsSurviveRestart() {
        AtomicConfigurationFileStore snapshots = new AtomicConfigurationFileStore(
                temporaryDirectory.resolve("config"), CLOCK);
        ConfigRevisionId revision = new ConfigRevisionId("owner_applied_revision");
        CompiledConfiguration compiled = compiled(Map.of("progression.yml", "schema-version: 3\n"));
        snapshots.prepare(revision, compiled).activate();
        StoredConfigurationRevision stored = new StoredConfigurationRevision(revision, Optional.empty(),
                Optional.empty(), compiled, new Actor("console", Optional.empty(), "CONSOLE"), "command",
                "owner apply", ValidationReport.VALID, "initial", ConfigurationApplicationStatus.APPLIED,
                NOW, Optional.of(NOW), Optional.empty());

        assertEquals(revision, StartupConfigurationLoader.load(snapshots, history(Optional.of(stored)))
                .orElseThrow().id());
        assertEquals(revision, StartupConfigurationLoader.load(new AtomicConfigurationFileStore(
                temporaryDirectory.resolve("config"), CLOCK), history(Optional.of(stored))).orElseThrow().id());
    }

    private static ConfigurationHistoryStore history(Optional<StoredConfigurationRevision> stored) {
        return new ConfigurationHistoryStore() {
            @Override
            public void append(StoredConfigurationRevision revision) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void replaceOutcome(StoredConfigurationRevision revision) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<StoredConfigurationRevision> find(ConfigRevisionId revisionId) {
                return stored.filter(value -> value.id().equals(revisionId));
            }

            @Override
            public List<StoredConfigurationRevision> recent(int limit) {
                return stored.stream().toList();
            }
        };
    }

    private static CompiledConfiguration compiled(Map<String, String> documents) {
        return new CompiledConfiguration(RevisionHasher.hashDocuments(documents), documents);
    }
}
