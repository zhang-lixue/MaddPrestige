package net.maddkraft.maddprestige.persistence.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.OperationState;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.admin.SqliteStageReferenceMigrationStore;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteConfigRevisionRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteFoundation;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteMigrations;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteOperationRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePrestigeLifecycleRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteRecoveryEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StageTransitionLeaseRecoveryTest {
    private static final Instant NOW = Instant.parse("2026-08-16T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ConfigRevisionId REVISION = new ConfigRevisionId("lease_recovery_revision");

    @TempDir
    Path temporaryDirectory;
    private SqliteFoundation sqlite;
    private SqliteOperationRepository operations;
    private SqliteStageReferenceMigrationStore fence;
    private PendingOperationRecoveryService recovery;

    @BeforeEach
    void setUp() {
        Path database = temporaryDirectory.resolve("lease-recovery.db");
        sqlite = new SqliteFoundation(database);
        new MigrationRunner(sqlite, new FileBackupService(database, temporaryDirectory.resolve("backups"), CLOCK),
                CLOCK).migrate(SqliteMigrations.phaseSix());
        new SqliteConfigRevisionRepository(sqlite).insert(REVISION, RevisionHasher.hashText("lease recovery"));
        operations = new SqliteOperationRepository(sqlite);
        fence = new SqliteStageReferenceMigrationStore(sqlite);
        recovery = new PendingOperationRecoveryService(operations,
                new SqlitePrestigeLifecycleRepository(sqlite), new SqliteRecoveryEventRepository(sqlite), CLOCK,
                new ProviderRegistry(), fence);
    }

    @Test
    @DisplayName("[A69] Crash after journal but before lease is failed safely without an orphan owner")
    void preparedJournalWithoutLeaseRecoversWithoutPermanentBlocker() throws Exception {
        OperationId operationId = journal(OperationState.PREPARED);

        var outcome = recovery.recover(100).getFirst();

        assertEquals(operationId, outcome.operationId());
        assertEquals(OperationState.FAILED, outcome.state());
        assertTrue(fence.leases(10).isEmpty());
        assertEquals(OperationState.FAILED, operations.find(operationId).orElseThrow().state());
    }

    @Test
    @DisplayName("[A69] NEEDS_RECONCILIATION retains participation until evidence-backed terminal resolution")
    void reconciliationResolutionReleasesLeaseExactlyOnce() throws Exception {
        OperationId operationId = journal(OperationState.NEEDS_RECONCILIATION);
        fence.acquire(operationId, new StageId("source"), new StageId("target"), REVISION, NOW);

        assertEquals("retained", recovery.recover(100).getFirst().decision());
        assertEquals(1, fence.leases(10).size());
        var resolved = recovery.resolve(operationId, OperationState.FAILED,
                "Owner verified that no external effect remains applied.");

        assertEquals(OperationState.FAILED, resolved.state());
        assertTrue(fence.leases(10).isEmpty());
        assertThrows(IllegalStateException.class, () -> recovery.resolve(operationId, OperationState.FAILED,
                "A terminal reconciliation cannot be consumed twice."));
    }

    private OperationId journal(OperationState state) throws Exception {
        OperationId operationId = OperationId.random();
        String sql = "INSERT INTO mp_operations (operation_id, operation_type, target_uuid, idempotency_key, state, "
                + "expected_state_revision, config_revision_id, provider_generations, redacted_preview, "
                + "created_at, updated_at) VALUES (?, 'generic-recovery-test', ?, ?, ?, 0, ?, '', '', ?, ?)";
        try (Connection connection = sqlite.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId.toString());
            statement.setString(2, java.util.UUID.randomUUID().toString());
            statement.setString(3, operationId.toString());
            statement.setString(4, state.name());
            statement.setString(5, REVISION.value());
            statement.setString(6, NOW.toString());
            statement.setString(7, NOW.toString());
            statement.executeUpdate();
        }
        return operationId;
    }
}
