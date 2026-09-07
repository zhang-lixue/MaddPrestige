package net.maddkraft.maddprestige.persistence.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.AdministrationPermissions;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationAdministrationService;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationApplyKind;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationApplicationStatus;
import net.maddkraft.maddprestige.core.admin.config.PreparedConfigurationSnapshot;
import net.maddkraft.maddprestige.core.admin.config.AdministrationConfigurationWorkflow;
import net.maddkraft.maddprestige.core.admin.config.StoredConfigurationRevision;
import net.maddkraft.maddprestige.core.config.ConfigurationService;
import net.maddkraft.maddprestige.core.config.BackupMetadata;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.schema.ActiveConfigurationSchema;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.core.stage.StageTransitionBlockedException;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteFoundation;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteMigrations;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerStageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteStageRemovalIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final PermissionSubject OWNER = new PermissionSubject(
            new Actor("console", Optional.empty(), "Owner"), AdministrationPermissions.all());

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("[A42][A69] Canonical stage deletion migrates every persisted reference before activation")
    void appliesReferencedStageDeletionThroughCanonicalAdministrationPath() throws Exception {
        Path database = temporaryDirectory.resolve("stage-remap.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        new MigrationRunner(sqlite, new FileBackupService(database, temporaryDirectory.resolve("backups"), CLOCK),
                CLOCK).migrate(SqliteMigrations.throughVersionTen());
        SqliteConfigurationHistoryStore history = new SqliteConfigurationHistoryStore(sqlite);
        SqliteStageReferenceMigrationStore remaps = new SqliteStageReferenceMigrationStore(sqlite);
        AtomicConfigurationFileStore snapshots = new AtomicConfigurationFileStore(
                temporaryDirectory.resolve("configuration"), CLOCK);
        CountDownLatch remapCommitted = new CountDownLatch(1);
        CountDownLatch allowActivation = new CountDownLatch(1);
        AtomicBoolean pauseActivation = new AtomicBoolean();
        ConfigurationService canonical = new ConfigurationService();
        ConfigurationAdministrationService administration = new ConfigurationAdministrationService(canonical,
                new AdministrationConfigurationWorkflow(canonical, new ProviderRegistry(), remaps, java.util.List.of()),
                compatibilitySchema(), history, (revisionId, configuration) -> {
                    PreparedConfigurationSnapshot prepared = snapshots.prepare(revisionId, configuration);
                    return new PreparedConfigurationSnapshot() {
                        @Override
                        public net.maddkraft.maddprestige.core.config.BackupMetadata backup() {
                            return prepared.backup();
                        }

                        @Override
                        public void activate() {
                            if (pauseActivation.get()) {
                                remapCommitted.countDown();
                                try {
                                    if (!allowActivation.await(10, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException("Timed out waiting to resume activation");
                                    }
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException("Interrupted while activation was paused", exception);
                                }
                            }
                            prepared.activate();
                        }

                        @Override
                        public void restorePrevious() {
                            prepared.restorePrevious();
                        }

                        @Override
                        public void close() {
                            prepared.close();
                        }
                    };
                }, CLOCK);

        UUID setupDraft = administration.beginInitialDraft(OWNER, documents(), "setup-wizard");
        var setupPreview = administration.preview(OWNER, setupDraft).toCompletableFuture().join();
        assertFalse(setupPreview.validation().hasErrors(), setupPreview.validation().toString());
        var setupAuthority = administration.prepareAcknowledgement(OWNER, setupDraft);
        StoredConfigurationRevision initial = administration.confirmAcknowledgement(OWNER,
                setupAuthority.acknowledgementId(), "Activate three-stage test ladder").toCompletableFuture().join();

        SqlitePlayerStageRepository players = new SqlitePlayerStageRepository(sqlite);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        players.insert(state(first, initial, "b"));
        players.insert(state(second, initial, "b"));
        players.insert(state(UUID.randomUUID(), initial, "a"));

        UUID deletionDraft = administration.beginDraft(OWNER, "command");
        administration.removeStage(OWNER, deletionDraft, new StageId("b"), Optional.of(new StageId("c")));
        var deletionPreview = administration.preview(OWNER, deletionDraft)
                .toCompletableFuture().join();
        assertFalse(deletionPreview.validation().hasErrors(), deletionPreview.validation().toString());
        assertEquals(2L, deletionPreview.stageImpact().affectedPlayerReferences().get(new StageId("b")));
        assertTrue(deletionPreview.validation().findings().stream()
                .anyMatch(finding -> finding.code().equals("stage.change.remap_migration")));

        var deletionAuthority = administration.prepareAcknowledgement(OWNER, deletionDraft);
        pauseActivation.set(true);
        CompletableFuture<StoredConfigurationRevision> applying = CompletableFuture.supplyAsync(() ->
                administration.confirmAcknowledgement(OWNER, deletionAuthority.acknowledgementId(),
                        "Replace referenced stage b with c").toCompletableFuture().join());
        assertTrue(remapCommitted.await(10, TimeUnit.SECONDS),
                "test must pause after the remap commit and before snapshot/runtime activation");
        assertEquals(new StageId("c"), players.find(first).orElseThrow().stageId());
        assertTrue(canonical.active().orElseThrow().compiled().documents().get("progression.yml")
                .contains("  b:"), "old configuration must still be active at the deterministic pause");
        SqliteStageReferenceMigrationStore duringPause = new SqliteStageReferenceMigrationStore(
                new SqliteFoundation(database));
        OperationId pausedOperation = journal(sqlite, initial.id(), "pause-race");
        assertThrows(StageTransitionBlockedException.class, () -> duringPause.acquire(
                pausedOperation, new StageId("a"), new StageId("b"), initial.id(), NOW.plusSeconds(1)));
        assertThrows(StageTransitionBlockedException.class,
                () -> players.insert(state(UUID.randomUUID(), initial, "b")),
                "manual/direct stage paths cannot recreate a removed-stage reference in the pause window");
        allowActivation.countDown();
        StoredConfigurationRevision applied = applying.get(10, TimeUnit.SECONDS);

        assertEquals(new StageId("c"), players.find(first).orElseThrow().stageId());
        assertEquals(new StageId("c"), players.find(second).orElseThrow().stageId());
        assertEquals(applied.id(), players.find(first).orElseThrow().configRevision());
        assertEquals(1L, players.find(first).orElseThrow().stateRevision());
        assertFalse(canonical.active().orElseThrow().compiled().documents().get("progression.yml")
                .contains("  b:"));
        assertEquals(java.util.List.of(new StageId("a"), new StageId("c")),
                administration.listValues(OWNER, administration.beginDraft(OWNER, "verification"),
                        "progression.order").stream().map(StageId::new).toList());
        assertEquals(Optional.of(applied.id()), snapshots.currentRevision());
        assertEquals(2, history.recent(10).size());
        assertTrue(history.find(applied.id()).orElseThrow().appliedAt().isPresent());
        assertTrue(remaps.unresolved(10).isEmpty());

        SqliteStageReferenceMigrationStore afterRestart = new SqliteStageReferenceMigrationStore(
                new SqliteFoundation(database));
        assertEquals(2L, afterRestart.capture(Optional.empty()).counts().get(new StageId("c")));
        assertTrue(afterRestart.unresolved(10).isEmpty());
    }

    @Test
    @DisplayName("[A69] Failed runtime publication with failed pointer restore remains durably fenced")
    void failedPointerRestoreLeavesRemapPendingAcrossRestart() {
        Path database = temporaryDirectory.resolve("stage-restore-failure.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        new MigrationRunner(sqlite, new FileBackupService(database,
                temporaryDirectory.resolve("restore-failure-backups"), CLOCK), CLOCK)
                .migrate(SqliteMigrations.throughVersionTen());
        SqliteConfigurationHistoryStore history = new SqliteConfigurationHistoryStore(sqlite);
        SqliteStageReferenceMigrationStore remaps = new SqliteStageReferenceMigrationStore(sqlite);
        AtomicConfigurationFileStore snapshots = new AtomicConfigurationFileStore(
                temporaryDirectory.resolve("restore-failure-configuration"), CLOCK);
        AtomicBoolean failPublicationAndRestore = new AtomicBoolean();
        ConfigurationService canonical = new ConfigurationService();
        ConfigurationAdministrationService administration = new ConfigurationAdministrationService(canonical,
                new AdministrationConfigurationWorkflow(canonical, new ProviderRegistry(), remaps, java.util.List.of()),
                compatibilitySchema(), history, (revisionId, configuration) -> {
                    PreparedConfigurationSnapshot prepared = snapshots.prepare(revisionId, configuration);
                    return new PreparedConfigurationSnapshot() {
                        @Override
                        public BackupMetadata backup() {
                            if (failPublicationAndRestore.get()) {
                                return new BackupMetadata("injected-unverified-backup", RevisionHasher.hashText(""),
                                        NOW, false);
                            }
                            return prepared.backup();
                        }

                        @Override
                        public void activate() {
                            prepared.activate();
                        }

                        @Override
                        public void restorePrevious() {
                            if (failPublicationAndRestore.get()) {
                                throw new IllegalStateException("Injected pointer restore failure");
                            }
                            prepared.restorePrevious();
                        }

                        @Override
                        public void close() {
                            prepared.close();
                        }
                    };
                }, CLOCK);

        UUID setupDraft = administration.beginInitialDraft(OWNER, documents(), "setup-wizard");
        administration.preview(OWNER, setupDraft).toCompletableFuture().join();
        var setupAuthority = administration.prepareAcknowledgement(OWNER, setupDraft);
        StoredConfigurationRevision initial = administration.confirmAcknowledgement(OWNER,
                setupAuthority.acknowledgementId(), "Activate restore-failure ladder")
                .toCompletableFuture().join();
        SqlitePlayerStageRepository players = new SqlitePlayerStageRepository(sqlite);
        UUID player = UUID.randomUUID();
        players.insert(state(player, initial, "b"));

        UUID deletionDraft = administration.beginDraft(OWNER, "command");
        administration.removeStage(OWNER, deletionDraft, new StageId("b"), Optional.of(new StageId("c")));
        administration.preview(OWNER, deletionDraft).toCompletableFuture().join();
        var authority = administration.prepareAcknowledgement(OWNER, deletionDraft);
        failPublicationAndRestore.set(true);

        CompletionException failure = assertThrows(CompletionException.class, () -> administration
                .confirmAcknowledgement(OWNER, authority.acknowledgementId(), "Injected restore failure")
                .toCompletableFuture().join());
        assertEquals("config.apply.failed", ((AdministrationException) failure.getCause()).code());
        assertEquals(new StageId("c"), players.find(player).orElseThrow().stageId());
        assertTrue(snapshots.currentRevision().isPresent());
        assertFalse(snapshots.currentRevision().orElseThrow().equals(initial.id()),
                "the injected pointer restore failure leaves the prepared revision selected");
        assertTrue(canonical.active().orElseThrow().compiled().documents().get("progression.yml").contains("  b:"),
                "runtime publication failed before replacing the old in-memory configuration");
        assertEquals("MIGRATED_PENDING_CONFIG", remaps.unresolved(10).getFirst().status());

        SqliteStageReferenceMigrationStore restarted = new SqliteStageReferenceMigrationStore(
                new SqliteFoundation(database));
        OperationId restartedOperation = journal(sqlite, initial.id(), "restore-failure-race");
        assertThrows(StageTransitionBlockedException.class, () -> restarted.acquire(
                restartedOperation, new StageId("a"), new StageId("b"), initial.id(), NOW.plusSeconds(1)),
                "restart must retain the fence until the pointer/runtime mismatch is explicitly reconciled");
    }

    @Test
    @DisplayName("[A41][A69] Pre-pointer and runtime activation failures restore fallback-safe references")
    void activationFailuresReleaseOnlyAfterCoherentPriorAuthority() {
        for (String failurePoint : java.util.List.of("before-pointer", "runtime-after-pointer")) {
            Path database = temporaryDirectory.resolve("safe-activation-failure-" + failurePoint + ".db");
            SqliteFoundation sqlite = migrated(database, "safe-failure-" + failurePoint + "-backups");
            SqliteConfigurationHistoryStore history = new SqliteConfigurationHistoryStore(sqlite);
            SqliteStageReferenceMigrationStore transitions = new SqliteStageReferenceMigrationStore(sqlite);
            AtomicConfigurationFileStore snapshots = new AtomicConfigurationFileStore(
                    temporaryDirectory.resolve("safe-failure-" + failurePoint + "-configuration"), CLOCK);
            AtomicReference<String> injected = new AtomicReference<>("none");
            ConfigurationService canonical = new ConfigurationService();
            ConfigurationAdministrationService administration = new ConfigurationAdministrationService(canonical,
                    new AdministrationConfigurationWorkflow(canonical, new ProviderRegistry(), transitions,
                            java.util.List.of()),
                    compatibilitySchema(), history, (revisionId, configuration) -> {
                        PreparedConfigurationSnapshot prepared = snapshots.prepare(revisionId, configuration);
                        return new PreparedConfigurationSnapshot() {
                            @Override
                            public BackupMetadata backup() {
                                return "runtime-after-pointer".equals(injected.get())
                                        ? new BackupMetadata("injected-invalid", RevisionHasher.hashText(""),
                                                NOW, false)
                                        : prepared.backup();
                            }

                            @Override
                            public void activate() {
                                if ("before-pointer".equals(injected.get())) {
                                    throw new IllegalStateException("Injected pre-pointer activation failure");
                                }
                                prepared.activate();
                            }

                            @Override
                            public void restorePrevious() {
                                prepared.restorePrevious();
                            }

                            @Override
                            public void close() {
                                prepared.close();
                            }
                        };
                    }, CLOCK);
            StoredConfigurationRevision initial = setup(administration, documents(), "safe failure setup");
            SqlitePlayerStageRepository players = new SqlitePlayerStageRepository(sqlite);
            UUID player = UUID.randomUUID();
            players.insert(state(player, initial, "b"));
            UUID draft = administration.beginDraft(OWNER, "command");
            administration.removeStage(OWNER, draft, new StageId("b"), Optional.of(new StageId("c")));
            administration.preview(OWNER, draft).toCompletableFuture().join();
            var authority = administration.prepareAcknowledgement(OWNER, draft);
            injected.set(failurePoint);

            CompletionException failure = assertThrows(CompletionException.class, () -> administration
                    .confirmAcknowledgement(OWNER, authority.acknowledgementId(), "injected " + failurePoint)
                    .toCompletableFuture().join());

            String expectedCode = "before-pointer".equals(failurePoint)
                    ? "config.snapshot.activate_failed" : "config.apply.failed";
            assertEquals(expectedCode, ((AdministrationException) failure.getCause()).code());
            assertEquals(Optional.of(initial.id()), snapshots.currentRevision());
            assertEquals(initial.id(), canonical.active().orElseThrow().revisionId());
            assertEquals(new StageId("c"), players.find(player).orElseThrow().stageId());
            assertTrue(canonical.active().orElseThrow().compiled().documents().get("progression.yml")
                    .contains("  c:"), "fallback authority must already recognize the migrated target");
            assertTrue(transitions.transitions(10).isEmpty());
            assertEquals("0", scalar(sqlite, "SELECT COUNT(*) FROM mp_configuration_stage_reservations"));
            assertTrue(history.recent(10).stream()
                    .anyMatch(revision -> revision.status() == ConfigurationApplicationStatus.FAILED));
            players.insert(state(UUID.randomUUID(), initial, "b"));
        }
    }

    @Test
    @DisplayName("[A41][A69] Rollback omitting a referenced current stage requires and executes an explicit remap")
    void rollbackWithCurrentlyReferencedMissingStageSucceedsOnlyWithExplicitRemap() {
        Path database = temporaryDirectory.resolve("stage-rollback-remap.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        new MigrationRunner(sqlite, new FileBackupService(database, temporaryDirectory.resolve("rollback-backups"),
                CLOCK), CLOCK).migrate(SqliteMigrations.throughVersionTen());
        SqliteConfigurationHistoryStore history = new SqliteConfigurationHistoryStore(sqlite);
        SqliteStageReferenceMigrationStore remaps = new SqliteStageReferenceMigrationStore(sqlite);
        AtomicConfigurationFileStore snapshots = new AtomicConfigurationFileStore(
                temporaryDirectory.resolve("rollback-configuration"), CLOCK);
        ConfigurationService canonical = new ConfigurationService();
        AdministrationConfigurationWorkflow workflow = new AdministrationConfigurationWorkflow(canonical,
                new ProviderRegistry(), remaps, java.util.List.of());
        ConfigurationAdministrationService administration = new ConfigurationAdministrationService(canonical,
                workflow, compatibilitySchema(), history, snapshots, CLOCK);

        UUID setupDraft = administration.beginInitialDraft(OWNER, documents(), "setup-wizard");
        var setupPreview = administration.preview(OWNER, setupDraft).toCompletableFuture().join();
        StoredConfigurationRevision withB;
        if (setupPreview.validation().canApply(java.util.Set.of())) {
            withB = administration.applySetup(OWNER, setupDraft, java.util.Set.of(), "current revision with b")
                    .toCompletableFuture().join();
        } else {
            var setupAuthority = administration.prepareAcknowledgement(OWNER, setupDraft);
            withB = administration.confirmAcknowledgement(OWNER, setupAuthority.acknowledgementId(),
                    "current revision with b").toCompletableFuture().join();
        }
        StoredConfigurationRevision withoutB = seedApplied(history, new ConfigRevisionId("without_b"),
                Optional.empty(), documentsWithoutB(), "historical revision without b");

        SqlitePlayerStageRepository players = new SqlitePlayerStageRepository(sqlite);
        UUID player = UUID.randomUUID();
        players.insert(state(player, withB, "b"));
        UUID rollbackDraft = administration.beginRollback(OWNER, withoutB.id(), "gui");
        var blocked = administration.preview(OWNER, rollbackDraft).toCompletableFuture().join();
        assertTrue(blocked.validation().findings().stream()
                .anyMatch(finding -> finding.code().equals("stage.change.remap_required")));
        CompletionException unsafe = assertThrows(CompletionException.class, () ->
                administration.applyRollback(OWNER, rollbackDraft, Optional.of(withB.id()), java.util.Set.of(),
                        "unsafe rollback").toCompletableFuture().join());
        assertEquals("config.validation.blocked", ((AdministrationException) unsafe.getCause()).code());

        administration.selectStageRemap(OWNER, rollbackDraft, new StageId("b"), new StageId("c"));
        var safe = administration.preview(OWNER, rollbackDraft).toCompletableFuture().join();
        assertFalse(safe.validation().hasErrors(), safe.validation().toString());
        assertTrue(safe.validation().findings().stream()
                .anyMatch(finding -> finding.code().equals("stage.change.remap_migration")));
        var rollbackAuthority = administration.prepareAcknowledgement(OWNER, rollbackDraft);
        assertEquals(ConfigurationApplyKind.ROLLBACK, rollbackAuthority.kind());
        StoredConfigurationRevision rolledBack = administration.confirmAcknowledgement(OWNER,
                rollbackAuthority.acknowledgementId(), "rollback b to c").toCompletableFuture().join();

        assertEquals(Optional.of(withoutB.id()), rolledBack.rollbackSource());
        assertEquals(new StageId("c"), players.find(player).orElseThrow().stageId());
        assertEquals(rolledBack.id(), players.find(player).orElseThrow().configRevision());
        assertFalse(canonical.active().orElseThrow().compiled().documents().get("progression.yml")
                .contains("  b:"));
        assertTrue(remaps.unresolved(10).isEmpty());
    }

    @Test
    @DisplayName("[A41][A69] Rollback accumulates and applies two sealed source remaps across restart")
    void rollbackAppliesMultipleSourceMappingsWithoutReplacementLoss() {
        Path database = temporaryDirectory.resolve("stage-multi-remap.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        new MigrationRunner(sqlite, new FileBackupService(database, temporaryDirectory.resolve("multi-backups"),
                CLOCK), CLOCK).migrate(SqliteMigrations.throughVersionTen());
        SqliteConfigurationHistoryStore history = new SqliteConfigurationHistoryStore(sqlite);
        SqliteStageReferenceMigrationStore remaps = new SqliteStageReferenceMigrationStore(sqlite);
        AtomicConfigurationFileStore snapshots = new AtomicConfigurationFileStore(
                temporaryDirectory.resolve("multi-configuration"), CLOCK);
        ConfigurationService canonical = new ConfigurationService();
        ConfigurationAdministrationService administration = new ConfigurationAdministrationService(canonical,
                new AdministrationConfigurationWorkflow(canonical, new ProviderRegistry(), remaps, java.util.List.of()),
                compatibilitySchema(), history, snapshots, CLOCK);

        UUID setupDraft = administration.beginInitialDraft(OWNER, multiDocuments(), "setup-wizard");
        administration.preview(OWNER, setupDraft).toCompletableFuture().join();
        var setupAuthority = administration.prepareAcknowledgement(OWNER, setupDraft);
        StoredConfigurationRevision withAll = administration.confirmAcknowledgement(OWNER,
                setupAuthority.acknowledgementId(), "Activate multi-source ladder").toCompletableFuture().join();
        StoredConfigurationRevision historical = seedApplied(history, new ConfigRevisionId("without_b_and_d"),
                Optional.empty(), multiDocumentsWithoutBAndD(), "Historical revision without b and d");
        SqlitePlayerStageRepository players = new SqlitePlayerStageRepository(sqlite);
        UUID onB = UUID.randomUUID();
        UUID onD = UUID.randomUUID();
        players.insert(state(onB, withAll, "b"));
        players.insert(state(onD, withAll, "d"));

        UUID rollback = administration.beginRollback(OWNER, historical.id(), "command-and-gui");
        administration.selectStageRemap(OWNER, rollback, new StageId("b"), new StageId("c"));
        administration.selectStageRemap(OWNER, rollback, new StageId("d"), new StageId("e"));
        var preview = administration.preview(OWNER, rollback).toCompletableFuture().join();
        assertFalse(preview.validation().hasErrors(), preview.validation().toString());
        assertTrue(preview.stageRemapSeal().isPresent());
        assertEquals(Map.of(new StageId("b"), 1L, new StageId("d"), 1L),
                preview.stageImpact().affectedPlayerReferences());
        var authority = administration.prepareAcknowledgement(OWNER, rollback);
        StoredConfigurationRevision applied = administration.confirmAcknowledgement(OWNER,
                authority.acknowledgementId(), "Apply both persisted-reference replacements")
                .toCompletableFuture().join();

        assertEquals(new StageId("c"), players.find(onB).orElseThrow().stageId());
        assertEquals(new StageId("e"), players.find(onD).orElseThrow().stageId());
        assertEquals(applied.id(), players.find(onB).orElseThrow().configRevision());
        assertFalse(players.countByStage().containsKey(new StageId("b")));
        assertFalse(players.countByStage().containsKey(new StageId("d")));
        SqliteStageReferenceMigrationStore restarted = new SqliteStageReferenceMigrationStore(
                new SqliteFoundation(database));
        assertFalse(restarted.capture(Optional.empty()).counts().containsKey(new StageId("b")));
        assertFalse(restarted.capture(Optional.empty()).counts().containsKey(new StageId("d")));
        assertTrue(restarted.unresolved(10).isEmpty());
    }

    @Test
    @DisplayName("[A69] Canonical normal apply reserves referenced B and zero-reference D as one scope")
    void normalApplyPersistsCompleteMixedUnsafeStageScope() {
        Path database = temporaryDirectory.resolve("stage-zero-reference-normal.db");
        SqliteFoundation sqlite = migrated(database, "zero-normal-backups");
        SqliteConfigurationHistoryStore history = new SqliteConfigurationHistoryStore(sqlite);
        SqliteStageReferenceMigrationStore transitions = new SqliteStageReferenceMigrationStore(sqlite);
        AtomicConfigurationFileStore snapshots = new AtomicConfigurationFileStore(
                temporaryDirectory.resolve("zero-normal-configuration"), CLOCK);
        ConfigurationService canonical = new ConfigurationService();
        ConfigurationAdministrationService administration = administration(
                canonical, history, transitions, snapshots);
        StoredConfigurationRevision initial = setup(administration, multiDocuments(), "mixed normal setup");
        SqlitePlayerStageRepository players = new SqlitePlayerStageRepository(sqlite);
        players.insert(state(UUID.randomUUID(), initial, "b"));

        UUID draft = administration.beginDraft(OWNER, "command");
        administration.removeStage(OWNER, draft, new StageId("b"), Optional.of(new StageId("c")));
        administration.removeStage(OWNER, draft, new StageId("d"), Optional.empty());
        var preview = administration.preview(OWNER, draft).toCompletableFuture().join();
        assertFalse(preview.validation().hasErrors(), preview.validation().toString());
        assertEquals(Map.of(new StageId("b"), 1L), preview.stageImpact().affectedPlayerReferences());
        StoredConfigurationRevision applied = confirm(administration, draft, "mixed normal transition");

        assertEquals(Map.of("b", "REMOVED", "d", "REMOVED"), transitionStageKinds(sqlite, applied.id()));
        assertEquals("0", scalar(sqlite, "SELECT COUNT(*) FROM mp_configuration_stage_reservations"));
    }

    @Test
    @DisplayName("[A69] Canonical disablement reserves a zero-reference stage without requiring a remap")
    void normalApplyPersistsZeroReferenceDisablementScope() {
        Path database = temporaryDirectory.resolve("stage-zero-reference-disable.db");
        SqliteFoundation sqlite = migrated(database, "zero-disable-backups");
        SqliteConfigurationHistoryStore history = new SqliteConfigurationHistoryStore(sqlite);
        SqliteStageReferenceMigrationStore transitions = new SqliteStageReferenceMigrationStore(sqlite);
        AtomicConfigurationFileStore snapshots = new AtomicConfigurationFileStore(
                temporaryDirectory.resolve("zero-disable-configuration"), CLOCK);
        ConfigurationService canonical = new ConfigurationService();
        ConfigurationAdministrationService administration = administration(
                canonical, history, transitions, snapshots);
        setup(administration, multiDocuments(), "disable setup");

        UUID draft = administration.beginDraft(OWNER, "gui");
        administration.editScalar(OWNER, draft, "progression.stages.d.enabled", "false");
        administration.removeListValue(OWNER, draft, "progression.order", "d");
        var preview = administration.preview(OWNER, draft).toCompletableFuture().join();
        assertFalse(preview.validation().hasErrors(), preview.validation().toString());
        assertEquals(Set.of(new StageId("d")), preview.stageImpact().disabledStages());
        StoredConfigurationRevision applied = confirm(administration, draft, "disable zero-reference d");

        assertEquals(Map.of("d", "DISABLED"), transitionStageKinds(sqlite, applied.id()));
        assertEquals("0", scalar(sqlite, "SELECT COUNT(*) FROM mp_configuration_stage_reservations"));
    }

    @Test
    @DisplayName("[A41][A69] Rollback rejects a replacement introduced only by historical candidate authority")
    void rollbackRejectsCandidateOnlyFallbackTarget() {
        Path database = temporaryDirectory.resolve("stage-rollback-new-target.db");
        SqliteFoundation sqlite = migrated(database, "rollback-new-target-backups");
        SqliteConfigurationHistoryStore history = new SqliteConfigurationHistoryStore(sqlite);
        SqliteStageReferenceMigrationStore transitions = new SqliteStageReferenceMigrationStore(sqlite);
        AtomicConfigurationFileStore snapshots = new AtomicConfigurationFileStore(
                temporaryDirectory.resolve("rollback-new-target-configuration"), CLOCK);
        ConfigurationService canonical = new ConfigurationService();
        ConfigurationAdministrationService administration = administration(
                canonical, history, transitions, snapshots);
        StoredConfigurationRevision current = setup(administration, documents(), "rollback current");
        StoredConfigurationRevision historical = seedApplied(history, new ConfigRevisionId("historical_with_x"),
                Optional.empty(), documentsWithoutBWithX(), "historical candidate-only x");
        SqlitePlayerStageRepository players = new SqlitePlayerStageRepository(sqlite);
        players.insert(state(UUID.randomUUID(), current, "b"));

        UUID rollback = administration.beginRollback(OWNER, historical.id(), "gui");
        administration.selectStageRemap(OWNER, rollback, new StageId("b"), new StageId("x"));
        var preview = administration.preview(OWNER, rollback).toCompletableFuture().join();

        assertTrue(preview.validation().findings().stream()
                .anyMatch(finding -> finding.code().equals("stage.change.remap_fallback_invalid")));
        assertTrue(preview.validation().hasErrors());
        assertTrue(transitions.transitions(10).isEmpty());
    }

    @Test
    @DisplayName("[A41][A69] Rollback reserves zero-reference D beside referenced B")
    void rollbackPersistsCompleteMixedUnsafeStageScope() {
        Path database = temporaryDirectory.resolve("stage-rollback-zero-reference.db");
        SqliteFoundation sqlite = migrated(database, "rollback-zero-backups");
        SqliteConfigurationHistoryStore history = new SqliteConfigurationHistoryStore(sqlite);
        SqliteStageReferenceMigrationStore transitions = new SqliteStageReferenceMigrationStore(sqlite);
        AtomicConfigurationFileStore snapshots = new AtomicConfigurationFileStore(
                temporaryDirectory.resolve("rollback-zero-configuration"), CLOCK);
        ConfigurationService canonical = new ConfigurationService();
        ConfigurationAdministrationService administration = administration(
                canonical, history, transitions, snapshots);
        StoredConfigurationRevision current = setup(administration, multiDocuments(), "rollback mixed current");
        StoredConfigurationRevision historical = seedApplied(history, new ConfigRevisionId("without_b_d_zero"),
                Optional.empty(), multiDocumentsWithoutBAndD(), "historical without b and d");
        SqlitePlayerStageRepository players = new SqlitePlayerStageRepository(sqlite);
        players.insert(state(UUID.randomUUID(), current, "b"));

        UUID rollback = administration.beginRollback(OWNER, historical.id(), "command");
        administration.selectStageRemap(OWNER, rollback, new StageId("b"), new StageId("c"));
        administration.preview(OWNER, rollback).toCompletableFuture().join();
        StoredConfigurationRevision applied = confirm(administration, rollback, "rollback mixed zero-reference");

        assertEquals(Map.of("b", "REMOVED", "d", "REMOVED"), transitionStageKinds(sqlite, applied.id()));
        assertEquals(Optional.of(historical.id()), applied.rollbackSource());
    }

    @Test
    @DisplayName("[A69] Restart recovery retains pointer/runtime mismatch then releases on candidate coherence")
    void recoveryUsesHistoryPointerAndRuntimeEvidenceWithoutAgeHeuristics() {
        Path database = temporaryDirectory.resolve("stage-transition-recovery.db");
        SqliteFoundation sqlite = migrated(database, "transition-recovery-backups");
        SqliteConfigurationHistoryStore history = new SqliteConfigurationHistoryStore(sqlite);
        SqliteStageReferenceMigrationStore transitions = new SqliteStageReferenceMigrationStore(sqlite);
        AtomicConfigurationFileStore snapshots = new AtomicConfigurationFileStore(
                temporaryDirectory.resolve("transition-recovery-configuration"), CLOCK);
        ConfigurationService canonical = new ConfigurationService();
        StoredConfigurationRevision prior = seedApplied(history, new ConfigRevisionId("recovery_prior"),
                Optional.empty(), documents(), "recovery prior");
        canonical.apply(prior.id(), prior.compiled(), prior.validation(), Set.of(), verifiedBackup("prior"));
        PreparedConfigurationSnapshot priorSnapshot = snapshots.prepare(prior.id(), prior.compiled());
        priorSnapshot.activate();
        priorSnapshot.close();
        CompiledConfiguration candidate = new CompiledConfiguration(
                RevisionHasher.hashDocuments(documentsWithoutB()), documentsWithoutB());
        StoredConfigurationRevision attempted = attempted(new ConfigRevisionId("recovery_candidate"),
                Optional.of(prior.id()), candidate, "recovery candidate");
        history.append(attempted);
        SqlitePlayerStageRepository players = new SqlitePlayerStageRepository(sqlite);
        players.insert(state(UUID.randomUUID(), prior, "b"));
        var remap = transitions.capture(Optional.of(new net.maddkraft.maddprestige.core.stage.StageRemapPlan(
                "recovery-remap", Map.of(new StageId("b"), new StageId("c"))))).remap().orElseThrow();
        transitions.beginTransition(attempted.id(), Optional.of(prior.id()), candidate.contentHash(),
                Map.of(new StageId("b"), net.maddkraft.maddprestige.core.admin.config
                        .ConfigurationStageReservationKind.REMOVED), Optional.of(remap), OWNER.actor(),
                "crash after remap", NOW);
        PreparedConfigurationSnapshot candidateSnapshot = snapshots.prepare(attempted.id(), candidate);
        candidateSnapshot.activate();
        candidateSnapshot.close();
        ConfigurationAdministrationService restarted = administration(canonical, history,
                new SqliteStageReferenceMigrationStore(new SqliteFoundation(database)), snapshots);

        assertEquals(0, restarted.recoverConfigurationStageTransitions());
        assertEquals(net.maddkraft.maddprestige.core.admin.config.ConfigurationStageTransitionStatus
                .NEEDS_RECONCILIATION, transitions.transitions(10).getFirst().status());
        canonical.apply(attempted.id(), candidate, attempted.validation(), Set.of(), verifiedBackup("candidate"));
        assertEquals(1, restarted.recoverConfigurationStageTransitions());
        assertEquals(ConfigurationApplicationStatus.APPLIED,
                history.find(attempted.id()).orElseThrow().status());
        assertTrue(transitions.transitions(10).isEmpty());
        assertEquals("0", scalar(sqlite, "SELECT COUNT(*) FROM mp_configuration_stage_reservations"));
    }

    @Test
    @DisplayName("[A69] Restart before activation resolves to prior authority and releases failed-safe")
    void recoveryResolvesPreActivationCrashToCoherentPriorAuthority() {
        Path database = temporaryDirectory.resolve("stage-pre-activation-recovery.db");
        SqliteFoundation sqlite = migrated(database, "pre-activation-recovery-backups");
        SqliteConfigurationHistoryStore history = new SqliteConfigurationHistoryStore(sqlite);
        SqliteStageReferenceMigrationStore transitions = new SqliteStageReferenceMigrationStore(sqlite);
        AtomicConfigurationFileStore snapshots = new AtomicConfigurationFileStore(
                temporaryDirectory.resolve("pre-activation-recovery-configuration"), CLOCK);
        ConfigurationService canonical = new ConfigurationService();
        StoredConfigurationRevision prior = seedApplied(history, new ConfigRevisionId("pre_activation_prior"),
                Optional.empty(), documents(), "pre-activation prior");
        canonical.apply(prior.id(), prior.compiled(), prior.validation(), Set.of(), verifiedBackup("prior"));
        PreparedConfigurationSnapshot priorSnapshot = snapshots.prepare(prior.id(), prior.compiled());
        priorSnapshot.activate();
        priorSnapshot.close();
        CompiledConfiguration candidate = new CompiledConfiguration(
                RevisionHasher.hashDocuments(documentsWithoutB()), documentsWithoutB());
        StoredConfigurationRevision attempted = attempted(new ConfigRevisionId("pre_activation_candidate"),
                Optional.of(prior.id()), candidate, "pre-activation candidate");
        history.append(attempted);
        transitions.beginTransition(attempted.id(), Optional.of(prior.id()), candidate.contentHash(),
                Map.of(new StageId("d"), net.maddkraft.maddprestige.core.admin.config
                        .ConfigurationStageReservationKind.REMOVED), Optional.empty(), OWNER.actor(),
                "crash after reservation", NOW);
        ConfigurationAdministrationService restarted = administration(canonical, history,
                new SqliteStageReferenceMigrationStore(new SqliteFoundation(database)), snapshots);

        assertEquals(1, restarted.recoverConfigurationStageTransitions());
        assertEquals(ConfigurationApplicationStatus.FAILED,
                history.find(attempted.id()).orElseThrow().status());
        assertTrue(transitions.transitions(10).isEmpty());
    }

    private SqliteFoundation migrated(Path database, String backupDirectory) {
        SqliteFoundation sqlite = new SqliteFoundation(database);
        new MigrationRunner(sqlite, new FileBackupService(database,
                temporaryDirectory.resolve(backupDirectory), CLOCK), CLOCK).migrate(SqliteMigrations.throughVersionTen());
        return sqlite;
    }

    private static ConfigurationAdministrationService administration(
            ConfigurationService canonical,
            SqliteConfigurationHistoryStore history,
            SqliteStageReferenceMigrationStore transitions,
            AtomicConfigurationFileStore snapshots) {
        return new ConfigurationAdministrationService(canonical,
                new AdministrationConfigurationWorkflow(canonical, new ProviderRegistry(), transitions, java.util.List.of()),
                compatibilitySchema(), history, snapshots, CLOCK);
    }

    private static net.maddkraft.maddprestige.core.schema.SchemaRegistry compatibilitySchema() {
        var schema = net.maddkraft.maddprestige.core.schema.PrestigeLifecycleSchema.create();
        ActiveConfigurationSchema.extend(schema);
        return schema;
    }

    private static StoredConfigurationRevision setup(
            ConfigurationAdministrationService administration,
            Map<String, String> setupDocuments,
            String reason) {
        UUID draft = administration.beginInitialDraft(OWNER, setupDocuments, "setup-wizard");
        var preview = administration.preview(OWNER, draft).toCompletableFuture().join();
        if (preview.validation().canApply(Set.of())) {
            return administration.applySetup(OWNER, draft, Set.of(), reason).toCompletableFuture().join();
        }
        var authority = administration.prepareAcknowledgement(OWNER, draft);
        return administration.confirmAcknowledgement(OWNER, authority.acknowledgementId(), reason)
                .toCompletableFuture().join();
    }

    private static StoredConfigurationRevision confirm(
            ConfigurationAdministrationService administration,
            UUID draft,
            String reason) {
        var authority = administration.prepareAcknowledgement(OWNER, draft);
        return administration.confirmAcknowledgement(OWNER, authority.acknowledgementId(), reason)
                .toCompletableFuture().join();
    }

    private static Map<String, String> transitionStageKinds(
            SqliteFoundation sqlite,
            ConfigRevisionId revision) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        String sql = "SELECT stage_id, reservation_kind FROM mp_configuration_transition_stages "
                + "WHERE config_revision_id = ? ORDER BY stage_id";
        try (Connection connection = sqlite.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, revision.value());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.put(rows.getString(1), rows.getString(2));
                }
            }
            return Map.copyOf(result);
        } catch (java.sql.SQLException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String scalar(SqliteFoundation sqlite, String sql) {
        try (Connection connection = sqlite.open(); PreparedStatement statement = connection.prepareStatement(sql);
                var row = statement.executeQuery()) {
            return row.next() ? row.getString(1) : "";
        } catch (java.sql.SQLException exception) {
            throw new AssertionError(exception);
        }
    }

    private static StoredConfigurationRevision attempted(
            ConfigRevisionId id,
            Optional<ConfigRevisionId> parent,
            CompiledConfiguration compiled,
            String reason) {
        return new StoredConfigurationRevision(id, parent, Optional.empty(), compiled, OWNER.actor(), "recovery-test",
                reason, net.maddkraft.maddprestige.api.validation.ValidationReport.VALID, "recovery",
                ConfigurationApplicationStatus.ATTEMPTED, NOW, Optional.empty(), Optional.empty());
    }

    private static BackupMetadata verifiedBackup(String source) {
        return new BackupMetadata(source, RevisionHasher.hashText(source), NOW, true);
    }

    private static PlayerStageState state(UUID player, StoredConfigurationRevision revision, String stage) {
        return new PlayerStageState(player, new StageId(stage), 0, revision.id(), NOW, NOW, NOW,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Map<String, String> documents() {
        LinkedHashMap<String, String> documents = new LinkedHashMap<>();
        documents.put("progression.yml", """
                schema-version: 3
                active: true
                reconciliation-policy: warn-only
                baseline: a
                stages:
                  a:
                    enabled: true
                    display-name: A
                    projection: none
                  b:
                    enabled: true
                    display-name: B
                    projection: none
                  c:
                    enabled: true
                    display-name: C
                    projection: none
                order:
                  - a
                  - b
                  - c
                """);
        documents.put("requirements.yml", """
                schema-version: 3
                maximum-depth: 16
                requirements: {}
                trees: {}
                costs: {}
                """);
        documents.put("rewards.yml", """
                schema-version: 3
                rewards: {}
                command-actions:
                  enabled: false
                  allowed-roots: []
                  blocked-roots: [stop, restart, op, deop]
                  allowed-tokens: []
                  templates: {}
                  maximum-commands: 5
                  maximum-length: 256
                  maximum-depth: 0
                """);
        documents.put("lifecycle.yml", """
                schema-version: 4
                prestige:
                  enabled: false
                  required-stages: []
                  reset-stage: disabled
                  current-count-increment: 1
                  lifetime-count-increment: 1
                  maximum: unlimited
                  cooldown: PT0S
                  costs: []
                  rewards: []
                  external-resets: {enabled: false}
                  reset-policy:
                    progression-stage: RESET
                    active-requirement-progress: RESET
                    latched-completions: RESET
                    baselines: RESET
                    prestige-scoped-currency: RESET
                    purchased-perks: PRESERVE
                    milestone-history: PRESERVE
                    season-progress: PRESERVE
                    historical-statistics: PRESERVE
                currencies: {}
                entitlements: {}
                milestones: {}
                seasons: {}
                competition: {enabled: false}
                """);
        documents.put("integrations.yml", """
                schema-version: 5
                vault: {enabled: false}
                mcmmo: {enabled: false}
                placeholderapi: {output: {enabled: false}, inputs: {}}
                economyshopgui: {compatibility-enabled: false, progression-credit: {enabled: false}}
                quickshop: {compatibility-enabled: false, progression-credit: {enabled: false}}
                """);
        return Map.copyOf(documents);
    }

    private static Map<String, String> documentsWithoutB() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>(documents());
        result.put("progression.yml", """
                schema-version: 3
                active: true
                reconciliation-policy: warn-only
                baseline: a
                stages:
                  a:
                    enabled: true
                    display-name: A
                    projection: none
                  c:
                    enabled: true
                    display-name: C
                    projection: none
                order:
                  - a
                  - c

                """);
        return Map.copyOf(result);
    }

    private static Map<String, String> documentsWithoutBWithX() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>(documentsWithoutB());
        result.put("progression.yml", """
                schema-version: 3
                active: true
                reconciliation-policy: warn-only
                baseline: a
                stages:
                  a: {enabled: true, display-name: A, projection: none}
                  c: {enabled: true, display-name: C, projection: none}
                  x: {enabled: true, display-name: X, projection: none}
                order: [a, c, x]
                """);
        return Map.copyOf(result);
    }

    private static Map<String, String> multiDocuments() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>(documents());
        result.put("progression.yml", """
                schema-version: 3
                active: true
                reconciliation-policy: warn-only
                baseline: a
                stages:
                  a:
                    enabled: true
                    display-name: A
                    projection: none
                  b:
                    enabled: true
                    display-name: B
                    projection: none
                  c:
                    enabled: true
                    display-name: C
                    projection: none
                  d:
                    enabled: true
                    display-name: D
                    projection: none
                  e:
                    enabled: true
                    display-name: E
                    projection: none
                order:
                  - a
                  - b
                  - c
                  - d
                  - e
                """);
        return Map.copyOf(result);
    }

    private static Map<String, String> multiDocumentsWithoutBAndD() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>(multiDocuments());
        result.put("progression.yml", """
                schema-version: 3
                active: true
                reconciliation-policy: warn-only
                baseline: a
                stages:
                  a: {enabled: true, display-name: A, projection: none}
                  c: {enabled: true, display-name: C, projection: none}
                  e: {enabled: true, display-name: E, projection: none}
                order: [a, c, e]
                """);
        return Map.copyOf(result);
    }

    private static OperationId journal(SqliteFoundation sqlite, ConfigRevisionId revision, String key) {
        OperationId operationId = OperationId.random();
        String sql = "INSERT INTO mp_operations (operation_id, operation_type, target_uuid, idempotency_key, state, "
                + "expected_state_revision, config_revision_id, provider_generations, redacted_preview, "
                + "created_at, updated_at) VALUES (?, 'stage-race', ?, ?, 'PREPARED', 0, ?, '', '', ?, ?)";
        try (Connection connection = sqlite.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId.toString());
            statement.setString(2, UUID.randomUUID().toString());
            statement.setString(3, key);
            statement.setString(4, revision.value());
            statement.setString(5, NOW.toString());
            statement.setString(6, NOW.toString());
            statement.executeUpdate();
            return operationId;
        } catch (java.sql.SQLException exception) {
            throw new AssertionError(exception);
        }
    }

    private static StoredConfigurationRevision seedApplied(
            SqliteConfigurationHistoryStore history,
            net.maddkraft.maddprestige.api.id.ConfigRevisionId id,
            Optional<net.maddkraft.maddprestige.api.id.ConfigRevisionId> parent,
            Map<String, String> documents,
            String reason) {
        CompiledConfiguration compiled = new CompiledConfiguration(RevisionHasher.hashDocuments(documents), documents);
        StoredConfigurationRevision attempted = new StoredConfigurationRevision(id, parent, Optional.empty(),
                compiled, OWNER.actor(), "test-seed", reason,
                net.maddkraft.maddprestige.api.validation.ValidationReport.VALID, "seed",
                ConfigurationApplicationStatus.ATTEMPTED, NOW, Optional.empty(), Optional.empty());
        history.append(attempted);
        StoredConfigurationRevision applied = attempted.withOutcome(ConfigurationApplicationStatus.APPLIED,
                Optional.of(NOW), Optional.empty());
        history.replaceOutcome(applied);
        return applied;
    }
}
