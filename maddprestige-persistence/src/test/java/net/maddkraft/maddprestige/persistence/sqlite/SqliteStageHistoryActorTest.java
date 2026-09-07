package net.maddkraft.maddprestige.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.StageHistoryRecord;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteStageHistoryActorTest {
    private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");
    private static final ConfigRevisionId REVISION = new ConfigRevisionId("prestige-lifecycle-stage-history");
    @TempDir
    Path temporaryDirectory;
    private SqliteFoundation sqlite;
    private SqlitePlayerStageRepository repository;

    @BeforeEach
    void migrate() {
        Path database = temporaryDirectory.resolve("stage-history.db");
        sqlite = new SqliteFoundation(database);
        new MigrationRunner(sqlite,
                new FileBackupService(database, temporaryDirectory.resolve("backups"), Clock.systemUTC()),
                Clock.systemUTC()).migrate(SqliteMigrations.throughVersionTen());
        new SqliteConfigRevisionRepository(sqlite).insert(REVISION, RevisionHasher.hashText("stage history actor"));
        repository = new SqlitePlayerStageRepository(sqlite);
    }

    @Test
    @DisplayName("[A58] Stage-history actor UUID survives readback/restart and nullable actors remain valid")
    void stageHistoryPreservesOptionalActorUuidAcrossRestart() throws Exception {
        UUID playerId = UUID.randomUUID();
        PlayerStageState initial = state(playerId, "first", 0, NOW);
        repository.insert(initial);
        PlayerStageState second = initial.advanceTo(new StageId("second"), REVISION, Optional.empty(),
                NOW.plusSeconds(1));
        UUID actorId = UUID.randomUUID();
        OperationId firstOperation = OperationId.random();
        insertOperation(firstOperation, playerId);
        repository.updateAndAppendHistory(second, 0, new StageHistoryRecord(playerId, second.stageId(),
                second.stageEnteredAt(), firstOperation, new Actor("staff", Optional.of(actorId), "Auditor"),
                "Audited advancement", REVISION));

        SqlitePlayerStageRepository restarted = new SqlitePlayerStageRepository(sqlite);
        assertEquals(Optional.of(actorId), restarted.history(playerId, 10).getFirst().actor().uuid());

        PlayerStageState third = second.advanceTo(new StageId("third"), REVISION, Optional.empty(),
                NOW.plusSeconds(2));
        OperationId secondOperation = OperationId.random();
        insertOperation(secondOperation, playerId);
        restarted.updateAndAppendHistory(third, 1, new StageHistoryRecord(playerId, third.stageId(),
                third.stageEnteredAt(), secondOperation, new Actor("console", Optional.empty(), "Console"),
                "Console advancement", REVISION));
        var reopenedHistory = new SqlitePlayerStageRepository(sqlite).history(playerId, 10);
        assertTrue(reopenedHistory.getFirst().actor().uuid().isEmpty());
        assertEquals(Optional.of(actorId), reopenedHistory.get(1).actor().uuid());
    }

    private void insertOperation(OperationId operationId, UUID playerId) throws Exception {
        String sql = "INSERT INTO mp_operations (operation_id, operation_type, target_uuid, idempotency_key, state, "
                + "expected_state_revision, config_revision_id, provider_generations, redacted_preview, created_at, "
                + "updated_at) VALUES (?, 'rank-up', ?, ?, 'COMPLETED', 0, ?, '', 'test', ?, ?)";
        try (var connection = sqlite.open(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId.toString());
            statement.setString(2, playerId.toString());
            statement.setString(3, operationId.toString());
            statement.setString(4, REVISION.value());
            statement.setString(5, NOW.toString());
            statement.setString(6, NOW.toString());
            statement.executeUpdate();
        }
    }

    private static PlayerStageState state(UUID playerId, String stage, long revision, Instant now) {
        return new PlayerStageState(playerId, new StageId(stage), revision, REVISION, now, now, now,
                Optional.empty(), Optional.empty(), Optional.empty());
    }
}
