package net.maddkraft.maddprestige.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.core.config.BackupMetadata;
import net.maddkraft.maddprestige.core.config.ConfigDraft;
import net.maddkraft.maddprestige.core.config.ConfigurationService;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.core.stage.StageConfigurationWorkflow;
import net.maddkraft.maddprestige.core.stage.StageRemapPlan;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.StalePlayerStageStateException;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlitePlayerStageRepositoryTest {
    @TempDir
    Path temporaryDirectory;
    private SqlitePlayerStageRepository repository;
    private ConfigRevisionId revision;

    @BeforeEach
    void migrate() {
        Path database = temporaryDirectory.resolve("player-stage.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        new MigrationRunner(sqlite,
                new FileBackupService(database, temporaryDirectory.resolve("backups"), Clock.systemUTC()),
                Clock.systemUTC()).migrate(SqliteMigrations.phaseTwo());
        revision = new ConfigRevisionId("revision_1");
        new SqliteConfigRevisionRepository(sqlite).insert(revision, RevisionHasher.hashText("revision one"));
        repository = new SqlitePlayerStageRepository(sqlite);
    }

    @Test
    @DisplayName("[A04] UUID/current stage ID persists independently from display name and order")
    void persistsImmutableStageIdentity() {
        PlayerStageState state = state(UUID.randomUUID(), "second", false);
        repository.insert(state);
        PlayerStageState loaded = repository.find(state.playerId()).orElseThrow();
        assertEquals(new StageId("second"), loaded.stageId());
        assertEquals(revision, loaded.configRevision());
        assertEquals(1L, repository.countByStage().get(new StageId("second")));

        // A display rename or order change has no column to rewrite and cannot affect the immutable stored ID.
        assertEquals(new StageId("second"), repository.find(state.playerId()).orElseThrow().stageId());
    }

    @Test
    @DisplayName("[A04] Optimistic player-stage revision rejects concurrent stale writes")
    void rejectsStaleRevision() {
        PlayerStageState state = state(UUID.randomUUID(), "first", false);
        repository.insert(state);
        Instant later = state.updatedAt().plusSeconds(1);
        PlayerStageState replacement = state.advanceTo(new StageId("second"), revision, 1, later);
        repository.update(replacement, 0);
        assertEquals(1, repository.find(state.playerId()).orElseThrow().stateRevision());
        assertThrows(StalePlayerStageStateException.class, () -> repository.update(replacement, 0));
    }

    @Test
    @DisplayName("[A07] Import-once inserts exactly one authoritative internal record")
    void importsOnlyOnce() {
        PlayerStageState imported = state(UUID.randomUUID(), "second", true);
        assertTrue(repository.importOnce(imported));
        assertFalse(repository.importOnce(imported));
        assertTrue(repository.find(imported.playerId()).orElseThrow().importedAt().isPresent());
    }

    @Test
    @DisplayName("[A69] Complete remap intent cannot activate while a persisted player row remains referenced")
    void unexecutedRemapCannotOrphanPersistedPlayer() {
        UUID playerId = UUID.randomUUID();
        repository.insert(state(playerId, "second", false));
        ProviderRegistry providers = new ProviderRegistry();
        StageConfigurationWorkflow workflow = new StageConfigurationWorkflow(new ConfigurationService());

        var initial = workflow.prepare(draft(twoStageYaml(), Optional.empty()),
                        repository.countByStage(), Optional.empty(), providers)
                .toCompletableFuture().join();
        workflow.apply(revision, initial, acknowledgements(initial.validation()), backup(), providers);

        StageRemapPlan remap = new StageRemapPlan("mapping_revision_1",
                Map.of(new StageId("second"), new StageId("first")));
        var removal = workflow.prepare(draft(oneStageYaml(), Optional.of(revision)),
                        repository.countByStage(), Optional.of(remap), providers)
                .toCompletableFuture().join();
        assertTrue(removal.validation().hasErrors());
        assertTrue(removal.validation().findings().stream()
                .anyMatch(finding -> finding.code().equals("stage.change.remap_execution_required")));
        assertThrows(IllegalStateException.class, () -> workflow.apply(new ConfigRevisionId("revision_2"), removal,
                acknowledgements(removal.validation()), backup(), providers));
        assertEquals(revision, workflow.active().orElseThrow().revisionId());
        assertTrue(workflow.active().orElseThrow().configuration().stages().containsKey(new StageId("second")));
        assertEquals(new StageId("second"), repository.find(playerId).orElseThrow().stageId());
    }

    private PlayerStageState state(UUID playerId, String stage, boolean imported) {
        Instant now = Instant.parse("2026-08-15T00:00:00Z");
        return new PlayerStageState(playerId, new StageId(stage), 0, revision, now, now, now,
                Optional.empty(), Optional.empty(), imported ? Optional.of(now) : Optional.empty());
    }

    private static ConfigDraft draft(String source, Optional<ConfigRevisionId> baseRevision) {
        return new ConfigDraft(UUID.randomUUID(), baseRevision, Map.of("progression.yml", source),
                new Actor("console", Optional.empty(), "Console"), Instant.parse("2026-08-15T00:00:00Z"));
    }

    private static Set<String> acknowledgements(
            net.maddkraft.maddprestige.api.validation.ValidationReport validation) {
        return validation.findings().stream().map(finding -> finding.code())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static BackupMetadata backup() {
        return new BackupMetadata("backup", RevisionHasher.hashText("backup"),
                Instant.parse("2026-08-15T00:00:00Z"), true);
    }

    private static String twoStageYaml() {
        return """
                schema-version: 2
                active: true
                baseline: first
                stages:
                  first: {enabled: true, display-name: First, projection: none}
                  second: {enabled: true, display-name: Second, projection: none}
                order: [first, second]
                """;
    }

    private static String oneStageYaml() {
        return """
                schema-version: 2
                active: true
                baseline: first
                stages:
                  first: {enabled: true, display-name: First, projection: none}
                order: [first]
                """;
    }
}
