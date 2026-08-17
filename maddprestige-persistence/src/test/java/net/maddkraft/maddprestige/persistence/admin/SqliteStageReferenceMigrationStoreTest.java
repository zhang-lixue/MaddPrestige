package net.maddkraft.maddprestige.persistence.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationStageReservationKind;
import net.maddkraft.maddprestige.core.admin.config.StageRemapExecution;
import net.maddkraft.maddprestige.core.admin.config.StageRemapSnapshot;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.config.ContentHash;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.core.stage.StageRemapPlan;
import net.maddkraft.maddprestige.core.stage.StageTransitionBlockedException;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.StalePlayerStageStateException;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteConfigRevisionRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteFoundation;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteMigrations;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerStageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteStageReferenceMigrationStoreTest {
    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ConfigRevisionId SOURCE_REVISION = new ConfigRevisionId("source_revision");
    private static final ConfigRevisionId TARGET_REVISION = new ConfigRevisionId("target_revision");

    @TempDir
    Path temporaryDirectory;
    private SqliteFoundation sqlite;
    private SqlitePlayerStageRepository players;
    private SqliteStageReferenceMigrationStore migrations;

    @BeforeEach
    void migrate() {
        Path database = temporaryDirectory.resolve("stage-remap.db");
        sqlite = new SqliteFoundation(database);
        new MigrationRunner(sqlite, new FileBackupService(database, temporaryDirectory.resolve("backups"), CLOCK),
                CLOCK).migrate(SqliteMigrations.phaseSix());
        SqliteConfigRevisionRepository revisions = new SqliteConfigRevisionRepository(sqlite);
        revisions.insert(SOURCE_REVISION, RevisionHasher.hashText("source"));
        revisions.insert(TARGET_REVISION, RevisionHasher.hashText("target"));
        players = new SqlitePlayerStageRepository(sqlite);
        migrations = new SqliteStageReferenceMigrationStore(sqlite);
    }

    @Test
    @DisplayName("[A69] Referenced-stage replacement migrates every exact row, history, journal, and restart state")
    void migratesReferencedStageAndSurvivesRestart() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        players.insert(state(first, "b"));
        players.insert(state(second, "b"));
        players.insert(state(UUID.randomUUID(), "a"));
        var captured = migrations.capture(Optional.of(plan())).remap().orElseThrow();

        var execution = transition(captured, "delete b in favor of c", NOW);

        assertEquals(new StageId("c"), players.find(first).orElseThrow().stageId());
        assertEquals(new StageId("c"), players.find(second).orElseThrow().stageId());
        assertEquals(1, players.find(first).orElseThrow().stateRevision());
        assertEquals(TARGET_REVISION, players.find(first).orElseThrow().configRevision());
        assertEquals("2", scalar("SELECT COUNT(*) FROM mp_stage_history WHERE reason LIKE '%"
                + execution.operationId() + "%'"));
        assertEquals("MIGRATED_PENDING_CONFIG", scalar("SELECT status FROM mp_stage_remap_operations"));
        terminalConfigurationOwner("APPLIED");
        migrations.markTransitionApplied(TARGET_REVISION, NOW.plusSeconds(1));
        assertTrue(migrations.unresolved(10).isEmpty());

        SqliteStageReferenceMigrationStore afterRestart = new SqliteStageReferenceMigrationStore(
                new SqliteFoundation(temporaryDirectory.resolve("stage-remap.db")));
        assertEquals(2L, afterRestart.capture(Optional.empty()).counts().get(new StageId("c")));
        assertEquals("2", scalar("SELECT COUNT(*) FROM mp_stage_remap_entries"));
    }

    @Test
    @DisplayName("[A69] A stale player CAS aborts the complete replacement without a journal")
    void staleSnapshotFailsClosed() throws Exception {
        UUID player = UUID.randomUUID();
        PlayerStageState original = state(player, "b");
        players.insert(original);
        var captured = migrations.capture(Optional.of(plan())).remap().orElseThrow();
        players.update(original.advanceTo(new StageId("c"), SOURCE_REVISION, 1, NOW.plusSeconds(1)), 0);

        assertThrows(StalePlayerStageStateException.class, () -> transition(
                captured, "stale migration", NOW.plusSeconds(2)));

        assertEquals("0", scalar("SELECT COUNT(*) FROM mp_stage_remap_operations"));
        assertEquals(new StageId("c"), players.find(player).orElseThrow().stageId());
    }

    @Test
    @DisplayName("[A69] A mid-transaction history failure rolls back every player and journal row")
    void midTransactionFailureRollsBackAllRows() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        players.insert(state(first, "b"));
        players.insert(state(second, "b"));
        var captured = migrations.capture(Optional.of(plan())).remap().orElseThrow();
        try (Connection connection = sqlite.open(); PreparedStatement statement = connection.prepareStatement("""
                CREATE TRIGGER reject_stage_remap_history BEFORE INSERT ON mp_stage_history
                WHEN NEW.reason LIKE 'configuration-stage-remap%'
                BEGIN SELECT RAISE(ABORT, 'injected history failure'); END
                """)) {
            statement.execute();
        }

        assertThrows(PersistenceException.class, () -> transition(captured, "injected failure", NOW));

        assertEquals(new StageId("b"), players.find(first).orElseThrow().stageId());
        assertEquals(new StageId("b"), players.find(second).orElseThrow().stageId());
        assertEquals("0", scalar("SELECT COUNT(*) FROM mp_stage_remap_operations"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM mp_stage_remap_entries"));
    }

    @Test
    @DisplayName("[A69] A committed remap fences its removed stage across restart until a terminal status")
    void pendingRemapFenceSurvivesRestartAndClearsOnlyAtTerminalStatus() throws Exception {
        players.insert(state(UUID.randomUUID(), "b"));
        var captured = migrations.capture(Optional.of(plan())).remap().orElseThrow();
        transition(captured, "pause before activation", NOW);
        SqliteStageReferenceMigrationStore restarted = new SqliteStageReferenceMigrationStore(
                new SqliteFoundation(temporaryDirectory.resolve("stage-remap.db")));

        OperationId blockedOperation = operation();
        journal(blockedOperation, "PREPARED");
        assertThrows(StageTransitionBlockedException.class, () -> restarted.acquire(
                blockedOperation, new StageId("a"), new StageId("b"), SOURCE_REVISION, NOW.plusSeconds(1)));
        assertThrows(StageTransitionBlockedException.class, () -> players.insert(state(UUID.randomUUID(), "b")),
                "direct/manual repository writes must share the same canonical fence");

        terminalConfigurationOwner("FAILED");
        restarted.markTransitionFailedSafe(TARGET_REVISION, "Injected activation failure", NOW.plusSeconds(2));
        OperationId resumedOperation = operation();
        journal(resumedOperation, "PREPARED");
        var permit = restarted.acquire(resumedOperation, new StageId("a"), new StageId("b"), SOURCE_REVISION,
                NOW.plusSeconds(3));
        terminal(resumedOperation, "FAILED");
        restarted.release(permit, NOW.plusSeconds(4));
        assertEquals("0", scalar("SELECT COUNT(*) FROM mp_stage_transition_leases"));
    }

    @Test
    @DisplayName("[A69] An already-running transition lease makes a concurrent remap fail closed before migration")
    void activeTransitionLeaseBlocksRemapBeforeAnyPlayerMoves() throws Exception {
        UUID player = UUID.randomUUID();
        players.insert(state(player, "b"));
        var captured = migrations.capture(Optional.of(plan())).remap().orElseThrow();
        OperationId operationId = operation();
        journal(operationId, "PREPARED");
        var permit = migrations.acquire(operationId, new StageId("b"), new StageId("c"), SOURCE_REVISION, NOW);
        var nested = migrations.acquire(operationId, new StageId("b"), new StageId("c"), SOURCE_REVISION,
                NOW.plusMillis(1));
        assertEquals(permit.leaseToken(), nested.leaseToken());

        assertThrows(StageTransitionBlockedException.class, () -> transition(
                captured, "must wait for stage operation", NOW.plusSeconds(1)));
        assertEquals(new StageId("b"), players.find(player).orElseThrow().stageId());
        assertEquals("0", scalar("SELECT COUNT(*) FROM mp_stage_remap_operations"));

        migrations.release(nested, NOW.plusSeconds(2));
        assertEquals("1", scalar("SELECT COUNT(*) FROM mp_stage_transition_leases"),
                "nested/nonterminal release must not clear outer authority");
        terminal(operationId, "FAILED");
        migrations.release(permit, NOW.plusSeconds(2));
        transition(captured, "retry after terminal stage operation", NOW.plusSeconds(3));
        assertEquals(new StageId("c"), players.find(player).orElseThrow().stageId());
    }

    @Test
    @DisplayName("[A69] Durable leases require a journal owner and a restarted owner adopts and releases exactly")
    void journalOwnershipAndRestartAdoptionPreventOrphans() throws Exception {
        OperationId absent = operation();
        assertThrows(StageTransitionBlockedException.class, () -> migrations.acquire(absent,
                new StageId("b"), new StageId("c"), SOURCE_REVISION, NOW));
        assertEquals("0", scalar("SELECT COUNT(*) FROM mp_stage_transition_leases"));

        OperationId owned = operation();
        journal(owned, "PREPARED");
        var first = migrations.acquire(owned, new StageId("b"), new StageId("c"), SOURCE_REVISION, NOW);
        SqliteStageReferenceMigrationStore restarted = new SqliteStageReferenceMigrationStore(
                new SqliteFoundation(temporaryDirectory.resolve("stage-remap.db")));
        var adopted = restarted.acquire(owned, new StageId("b"), new StageId("c"), SOURCE_REVISION,
                NOW.plusSeconds(1));

        assertEquals(first.leaseToken(), adopted.leaseToken());
        restarted.release(adopted, NOW.plusSeconds(2));
        assertEquals("1", scalar("SELECT COUNT(*) FROM mp_stage_transition_leases"));
        terminal(owned, "FAILED");
        restarted.release(adopted, NOW.plusSeconds(3));
        assertEquals("0", scalar("SELECT COUNT(*) FROM mp_stage_transition_leases"));
    }

    @Test
    @DisplayName("[A69] Terminal-owner maintenance removes a crash-left lease without an age heuristic")
    void terminalOwnerCleanupIsEvidenceBased() throws Exception {
        OperationId operationId = operation();
        journal(operationId, "PREPARED");
        migrations.acquire(operationId, new StageId("b"), new StageId("c"), SOURCE_REVISION, NOW);
        terminal(operationId, "FAILED");

        assertEquals(1, migrations.releaseTerminalLeases(NOW.plusSeconds(1)));
        assertEquals("0", scalar("SELECT COUNT(*) FROM mp_stage_transition_leases"));
    }

    @Test
    @DisplayName("[A69] Concurrent terminal releases are idempotent and remove one exact owned lease")
    void concurrentTerminalReleaseIsIdempotent() throws Exception {
        OperationId operationId = operation();
        journal(operationId, "PREPARED");
        var permit = migrations.acquire(operationId, new StageId("b"), new StageId("c"), SOURCE_REVISION, NOW);
        terminal(operationId, "FAILED");

        CompletableFuture<Void> first = CompletableFuture.runAsync(
                () -> migrations.release(permit, NOW.plusSeconds(1)));
        CompletableFuture<Void> second = CompletableFuture.runAsync(
                () -> migrations.release(permit, NOW.plusSeconds(1)));
        CompletableFuture.allOf(first, second).join();

        assertEquals("0", scalar("SELECT COUNT(*) FROM mp_stage_transition_leases"));
    }

    @Test
    @DisplayName("[A69] Zero-reference removal fences insert, import and CAS target until terminal evidence")
    void zeroReferenceRemovalFencesEveryDirectWriteAndReleasesIdempotently() throws Exception {
        prepareConfigurationOwner();
        var transition = migrations.beginTransition(TARGET_REVISION, Optional.of(SOURCE_REVISION),
                RevisionHasher.hashText("target"),
                Map.of(new StageId("d"), ConfigurationStageReservationKind.REMOVED), Optional.empty(),
                actor(), "remove zero-reference d", NOW);

        assertTrue(transition.remapExecution().isEmpty());
        assertEquals(Map.of(new StageId("d"), ConfigurationStageReservationKind.REMOVED),
                migrations.transitions(10).getFirst().reservedStages());
        assertThrows(StageTransitionBlockedException.class,
                () -> players.insert(state(UUID.randomUUID(), "d")));
        PlayerStageState imported = new PlayerStageState(UUID.randomUUID(), new StageId("d"), 0,
                SOURCE_REVISION, NOW, NOW, NOW, Optional.empty(), Optional.empty(), Optional.of(NOW));
        assertThrows(StageTransitionBlockedException.class, () -> players.importOnce(imported));
        UUID existing = UUID.randomUUID();
        PlayerStageState before = state(existing, "a");
        players.insert(before);
        assertThrows(StageTransitionBlockedException.class, () -> players.update(
                before.advanceTo(new StageId("d"), SOURCE_REVISION, 1, NOW.plusSeconds(1)), 0));
        players.insert(state(UUID.randomUUID(), "unrelated"));

        terminalConfigurationOwner("APPLIED");
        migrations.markTransitionApplied(TARGET_REVISION, NOW.plusSeconds(2));
        migrations.markTransitionApplied(TARGET_REVISION, NOW.plusSeconds(3));
        players.insert(state(UUID.randomUUID(), "d"));
        assertTrue(migrations.transitions(10).isEmpty());
    }

    @Test
    @DisplayName("[A69] Direct insert, CAS and import winning first invalidate a zero-reference reservation attempt")
    void directWritesWinningFirstMakeFinalRevalidationFailClosed() throws Exception {
        prepareConfigurationOwner();
        players.insert(state(UUID.randomUUID(), "d"));
        assertThrows(StalePlayerStageStateException.class, () -> migrations.beginTransition(
                TARGET_REVISION, Optional.of(SOURCE_REVISION), RevisionHasher.hashText("target"),
                Map.of(new StageId("d"), ConfigurationStageReservationKind.REMOVED), Optional.empty(),
                actor(), "insert won first", NOW));
        assertEquals("0", scalar("SELECT COUNT(*) FROM mp_configuration_stage_transitions"));

        UUID updatedPlayer = UUID.randomUUID();
        PlayerStageState before = state(updatedPlayer, "a");
        players.insert(before);
        players.update(before.advanceTo(new StageId("d"), SOURCE_REVISION, 1, NOW.plusSeconds(1)), 0);
        PlayerStageState imported = new PlayerStageState(UUID.randomUUID(), new StageId("d"), 0,
                SOURCE_REVISION, NOW, NOW, NOW, Optional.empty(), Optional.empty(), Optional.of(NOW));
        assertTrue(players.importOnce(imported));
        assertThrows(StalePlayerStageStateException.class, () -> migrations.beginTransition(
                TARGET_REVISION, Optional.of(SOURCE_REVISION), RevisionHasher.hashText("target"),
                Map.of(new StageId("d"), ConfigurationStageReservationKind.DISABLED), Optional.empty(),
                actor(), "CAS and import won first", NOW.plusSeconds(2)));
        assertEquals("3", scalar("SELECT COUNT(*) FROM mp_player_stage_state WHERE stage_id = 'd'"));
    }

    @Test
    @DisplayName("[A69] Zero-reference disablement owns the same durable reservation lifecycle")
    void zeroReferenceDisablementUsesIdenticalFailClosedAuthority() throws Exception {
        prepareConfigurationOwner();
        migrations.beginTransition(TARGET_REVISION, Optional.of(SOURCE_REVISION),
                RevisionHasher.hashText("target"),
                Map.of(new StageId("d"), ConfigurationStageReservationKind.DISABLED), Optional.empty(),
                actor(), "disable zero-reference d", NOW);

        assertEquals(ConfigurationStageReservationKind.DISABLED,
                migrations.transitions(10).getFirst().reservedStages().get(new StageId("d")));
        assertThrows(StageTransitionBlockedException.class,
                () -> players.insert(state(UUID.randomUUID(), "d")));
        terminalConfigurationOwner("FAILED");
        assertThrows(StageTransitionBlockedException.class, () -> migrations.beginTransition(
                TARGET_REVISION, Optional.of(SOURCE_REVISION), RevisionHasher.hashText("target"),
                Map.of(new StageId("d"), ConfigurationStageReservationKind.DISABLED), Optional.empty(),
                actor(), "terminal owner cannot retry", NOW.plusMillis(500)));
        migrations.markTransitionFailedSafe(TARGET_REVISION, "prior authority restored", NOW.plusSeconds(1));
        players.insert(state(UUID.randomUUID(), "d"));
    }

    @Test
    @DisplayName("[A69] Operation source or target participation wins before zero-reference configuration authority")
    void operationWinsFirstForEitherStageRole() throws Exception {
        prepareConfigurationOwner();
        OperationId targetOwner = operation();
        journal(targetOwner, "PREPARED");
        var targetPermit = migrations.acquire(targetOwner, new StageId("a"), new StageId("d"),
                SOURCE_REVISION, NOW);

        assertThrows(StageTransitionBlockedException.class, () -> migrations.beginTransition(
                TARGET_REVISION, Optional.of(SOURCE_REVISION), RevisionHasher.hashText("target"),
                Map.of(new StageId("d"), ConfigurationStageReservationKind.REMOVED), Optional.empty(),
                actor(), "target lease wins", NOW.plusSeconds(1)));
        terminal(targetOwner, "FAILED");
        migrations.release(targetPermit, NOW.plusSeconds(2));

        OperationId sourceOwner = operation();
        journal(sourceOwner, "PREPARED");
        var sourcePermit = migrations.acquire(sourceOwner, new StageId("d"), new StageId("a"),
                SOURCE_REVISION, NOW.plusSeconds(3));
        assertThrows(StageTransitionBlockedException.class, () -> migrations.beginTransition(
                TARGET_REVISION, Optional.of(SOURCE_REVISION), RevisionHasher.hashText("target"),
                Map.of(new StageId("d"), ConfigurationStageReservationKind.DISABLED), Optional.empty(),
                actor(), "source lease wins", NOW.plusSeconds(4)));
        terminal(sourceOwner, "FAILED");
        migrations.release(sourcePermit, NOW.plusSeconds(5));
        assertEquals("0", scalar("SELECT COUNT(*) FROM mp_configuration_stage_transitions"));
    }

    @Test
    @DisplayName("[A69] Configuration wins first for either operation role while unrelated stages proceed")
    void configurationWinsFirstWithoutGloballyBlockingOperations() throws Exception {
        prepareConfigurationOwner();
        migrations.beginTransition(TARGET_REVISION, Optional.of(SOURCE_REVISION),
                RevisionHasher.hashText("target"),
                Map.of(new StageId("d"), ConfigurationStageReservationKind.REMOVED), Optional.empty(),
                actor(), "configuration wins", NOW);

        OperationId source = operation();
        journal(source, "PREPARED");
        assertThrows(StageTransitionBlockedException.class, () -> migrations.acquire(
                source, new StageId("d"), new StageId("a"), SOURCE_REVISION, NOW.plusSeconds(1)));
        OperationId target = operation();
        journal(target, "PREPARED");
        assertThrows(StageTransitionBlockedException.class, () -> migrations.acquire(
                target, new StageId("a"), new StageId("d"), SOURCE_REVISION, NOW.plusSeconds(1)));
        OperationId unrelated = operation();
        journal(unrelated, "PREPARED");
        var permit = migrations.acquire(unrelated, new StageId("a"), new StageId("c"),
                SOURCE_REVISION, NOW.plusSeconds(1));
        terminal(unrelated, "FAILED");
        migrations.release(permit, NOW.plusSeconds(2));
    }

    @Test
    @DisplayName("[A69] Mixed B/D/F candidate reserves complete scope while atomically remapping B and F")
    void mixedReferencedAndZeroReferenceCandidateRetainsOneCoherentAuthority() throws Exception {
        UUID bPlayer = UUID.randomUUID();
        UUID fPlayer = UUID.randomUUID();
        players.insert(state(bPlayer, "b"));
        players.insert(state(fPlayer, "f"));
        StageRemapPlan plan = new StageRemapPlan("mixed_b_d_f", Map.of(
                new StageId("b"), new StageId("c"), new StageId("f"), new StageId("e")));
        StageRemapSnapshot snapshot = migrations.capture(Optional.of(plan)).remap().orElseThrow();
        prepareConfigurationOwner();

        migrations.beginTransition(TARGET_REVISION, Optional.of(SOURCE_REVISION),
                RevisionHasher.hashText("target"), Map.of(
                        new StageId("b"), ConfigurationStageReservationKind.REMOVED,
                        new StageId("d"), ConfigurationStageReservationKind.DISABLED,
                        new StageId("f"), ConfigurationStageReservationKind.REMOVED),
                Optional.of(snapshot), actor(), "mixed destructive candidate", NOW);

        assertEquals(Set.of(new StageId("b"), new StageId("d"), new StageId("f")),
                migrations.transitions(10).getFirst().reservedStages().keySet());
        assertEquals(new StageId("c"), players.find(bPlayer).orElseThrow().stageId());
        assertEquals(new StageId("e"), players.find(fPlayer).orElseThrow().stageId());
        assertThrows(StageTransitionBlockedException.class,
                () -> players.insert(state(UUID.randomUUID(), "d")));
        var adopted = migrations.beginTransition(TARGET_REVISION, Optional.of(SOURCE_REVISION),
                RevisionHasher.hashText("target"), Map.of(
                        new StageId("b"), ConfigurationStageReservationKind.REMOVED,
                        new StageId("d"), ConfigurationStageReservationKind.DISABLED,
                        new StageId("f"), ConfigurationStageReservationKind.REMOVED),
                Optional.of(snapshot), actor(), "idempotent retry", NOW.plusSeconds(1));
        assertTrue(adopted.remapExecution().isPresent());

        terminalConfigurationOwner("APPLIED");
        migrations.markTransitionApplied(TARGET_REVISION, NOW.plusSeconds(2));
        assertTrue(migrations.transitions(10).isEmpty());
    }

    @Test
    @DisplayName("[A69] A second destructive configuration cannot overlap an unresolved owner")
    void simultaneousConfigurationTransitionsFailClosedWithoutConflictingReservations() throws Exception {
        prepareConfigurationOwner();
        migrations.beginTransition(TARGET_REVISION, Optional.of(SOURCE_REVISION),
                RevisionHasher.hashText("target"),
                Map.of(new StageId("d"), ConfigurationStageReservationKind.REMOVED), Optional.empty(),
                actor(), "first transition", NOW);
        ConfigRevisionId second = new ConfigRevisionId("second_target_revision");
        var secondHash = RevisionHasher.hashText("second target");
        new SqliteConfigRevisionRepository(sqlite).insert(second, secondHash);
        prepareConfigurationOwner(second, secondHash);

        assertThrows(StageTransitionBlockedException.class, () -> migrations.beginTransition(
                second, Optional.of(SOURCE_REVISION), secondHash,
                Map.of(new StageId("unrelated"), ConfigurationStageReservationKind.DISABLED), Optional.empty(),
                actor(), "second transition", NOW.plusSeconds(1)));
        assertEquals("1", scalar("SELECT COUNT(*) FROM mp_configuration_stage_reservations"));
        assertEquals(TARGET_REVISION.value(), scalar(
                "SELECT config_revision_id FROM mp_configuration_stage_reservations"));
    }

    @Test
    @DisplayName("[A45][A69] Partial scope or terminal owner with a lingering reservation fails globally closed")
    void contradictoryConfigurationAuthorityFailsGloballyClosedAndRemainsDiagnosable() throws Exception {
        prepareConfigurationOwner();
        migrations.beginTransition(TARGET_REVISION, Optional.of(SOURCE_REVISION),
                RevisionHasher.hashText("target"), Map.of(
                        new StageId("d"), ConfigurationStageReservationKind.REMOVED,
                        new StageId("e"), ConfigurationStageReservationKind.DISABLED), Optional.empty(),
                actor(), "corruption audit", NOW);
        try (Connection connection = sqlite.open(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM mp_configuration_stage_reservations WHERE stage_id = 'e'")) {
            assertEquals(1, statement.executeUpdate());
        }

        assertThrows(StageTransitionBlockedException.class,
                () -> players.insert(state(UUID.randomUUID(), "unrelated")));
        assertTrue(migrations.transitions(10).getFirst().abnormal());

        try (Connection connection = sqlite.open(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO mp_configuration_stage_reservations "
                        + "(stage_id, config_revision_id, reserved_at) VALUES ('e', ?, ?)")) {
            statement.setString(1, TARGET_REVISION.value());
            statement.setString(2, NOW.toString());
            assertEquals(1, statement.executeUpdate());
        }
        terminalConfigurationOwner("APPLIED");

        assertThrows(StageTransitionBlockedException.class,
                () -> players.insert(state(UUID.randomUUID(), "unrelated")));
        assertTrue(migrations.transitions(10).getFirst().abnormal());
    }

    private PlayerStageState state(UUID player, String stage) {
        return new PlayerStageState(player, new StageId(stage), 0, SOURCE_REVISION, NOW, NOW, NOW,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static StageRemapPlan plan() {
        return new StageRemapPlan("delete_b", Map.of(new StageId("b"), new StageId("c")));
    }

    private static Actor actor() {
        return new Actor("console", Optional.empty(), "Owner");
    }

    private static OperationId operation() {
        return new OperationId(UUID.randomUUID());
    }

    private StageRemapExecution transition(StageRemapSnapshot snapshot, String reason, Instant occurredAt)
            throws Exception {
        prepareConfigurationOwner();
        return migrations.beginTransition(TARGET_REVISION, Optional.of(SOURCE_REVISION),
                RevisionHasher.hashText("target"),
                Map.of(new StageId("b"), ConfigurationStageReservationKind.REMOVED), Optional.of(snapshot),
                actor(), reason, occurredAt).remapExecution().orElseThrow();
    }

    private void prepareConfigurationOwner() throws Exception {
        prepareConfigurationOwner(TARGET_REVISION, RevisionHasher.hashText("target"));
    }

    private void prepareConfigurationOwner(ConfigRevisionId revision, ContentHash contentHash) throws Exception {
        String sql = "INSERT OR IGNORE INTO mp_configuration_revisions_v2 (revision_id, parent_revision_id, "
                + "canonical_content_hash, actor_type, actor_name, source_surface, reason, validation_summary, "
                + "diff_summary, application_status, created_at) VALUES (?, ?, ?, 'console', 'Owner', "
                + "'test', 'stage transition test', 'valid', 'test', 'ATTEMPTED', ?)";
        try (Connection connection = sqlite.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, revision.value());
            statement.setString(2, SOURCE_REVISION.value());
            statement.setString(3, contentHash.value());
            statement.setString(4, NOW.toString());
            statement.executeUpdate();
        }
    }

    private void terminalConfigurationOwner(String status) throws Exception {
        try (Connection connection = sqlite.open(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE mp_configuration_revisions_v2 SET application_status = ? WHERE revision_id = ?")) {
            statement.setString(1, status);
            statement.setString(2, TARGET_REVISION.value());
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void journal(OperationId operationId, String state) throws Exception {
        String sql = "INSERT INTO mp_operations (operation_id, operation_type, target_uuid, idempotency_key, state, "
                + "expected_state_revision, config_revision_id, provider_generations, redacted_preview, "
                + "created_at, updated_at) VALUES (?, 'test-stage-transition', ?, ?, ?, 0, ?, '', '', ?, ?)";
        try (Connection connection = sqlite.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId.toString());
            statement.setString(2, UUID.randomUUID().toString());
            statement.setString(3, operationId.toString());
            statement.setString(4, state);
            statement.setString(5, SOURCE_REVISION.value());
            statement.setString(6, NOW.toString());
            statement.setString(7, NOW.toString());
            statement.executeUpdate();
        }
    }

    private void terminal(OperationId operationId, String state) throws Exception {
        try (Connection connection = sqlite.open(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE mp_operations SET state = ?, updated_at = ? WHERE operation_id = ?")) {
            statement.setString(1, state);
            statement.setString(2, NOW.plusSeconds(1).toString());
            statement.setString(3, operationId.toString());
            assertEquals(1, statement.executeUpdate());
        }
    }

    private String scalar(String sql) throws Exception {
        try (Connection connection = sqlite.open(); PreparedStatement statement = connection.prepareStatement(sql);
                var row = statement.executeQuery()) {
            assertTrue(row.next());
            return row.getString(1);
        }
    }
}
