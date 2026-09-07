package net.maddkraft.maddprestige.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.audit.AuditOutcome;
import net.maddkraft.maddprestige.api.audit.AuditRecord;
import net.maddkraft.maddprestige.api.audit.AuditValue;
import net.maddkraft.maddprestige.api.id.CurrencyId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.operation.OperationActionPlan;
import net.maddkraft.maddprestige.api.operation.OperationPlan;
import net.maddkraft.maddprestige.api.operation.OperationState;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.core.operation.IllegalTransitionException;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteRepositoryTest {
    @TempDir
    Path temporaryDirectory;
    private SqliteFoundation sqlite;
    private ConfigRevisionId revisionId;

    @BeforeEach
    void migrate() {
        Path database = temporaryDirectory.resolve("repository.db");
        sqlite = new SqliteFoundation(database);
        new MigrationRunner(sqlite,
                new FileBackupService(database, temporaryDirectory.resolve("backups"), Clock.systemUTC()),
                Clock.systemUTC()).migrate(SqliteMigrations.throughVersionOne());
        revisionId = new ConfigRevisionId("revision_1");
        new SqliteConfigRevisionRepository(sqlite).insert(revisionId, RevisionHasher.hashText("revision one"));
    }

    @Test
    @DisplayName("Persistence uniqueness prevents duplicate operation idempotency keys")
    void preventsDuplicateOperations() {
        UUID target = UUID.randomUUID();
        SqliteOperationRepository repository = new SqliteOperationRepository(sqlite);
        OperationPlan first = plan(OperationId.random(), target, "rankup-click-1");
        repository.insertPrepared(first);
        assertEquals(OperationState.PREPARED, repository.find(first.id()).orElseThrow().state());
        assertThrows(IllegalTransitionException.class,
                () -> repository.transition(first.id(), OperationState.PREPARED, OperationState.COMPLETED));

        OperationPlan duplicate = plan(OperationId.random(), target, "rankup-click-1");
        assertThrows(PersistenceException.class, () -> repository.insertPrepared(duplicate));
    }

    @Test
    @DisplayName("[A30]SQLite exact decimals round-trip without floating-point conversion")
    void roundTripsExactDecimal() {
        SqliteCurrencyAccountRepository repository = new SqliteCurrencyAccountRepository(sqlite);
        UUID player = UUID.randomUUID();
        CurrencyId currency = new CurrencyId("prestige_points");
        ExactDecimal expected = ExactDecimal.parse("9007199254740993.123456789012345678901");
        repository.set(player, currency, expected);
        assertEquals(expected, repository.find(player, currency).orElseThrow());
        assertEquals(expected.toString(), repository.find(player, currency).orElseThrow().toString());
    }

    @Test
    @DisplayName("[A57]SQLite audit persistence redacts sensitive values before storage")
    void persistsRedactedAuditValues() throws Exception {
        UUID auditId = UUID.randomUUID();
        AuditRecord record = new AuditRecord(auditId, new Actor("console", Optional.empty(), "Console"),
                Optional.empty(), Optional.empty(), Optional.of(revisionId), "config.apply",
                Optional.of(new AuditValue("old-secret", true)), Optional.of(new AuditValue("new-secret", true)),
                "command", "foundation redaction test", AuditOutcome.SUCCEEDED, Optional.empty(), UUID.randomUUID(),
                Instant.now());
        new SqliteAuditRepository(sqlite).append(record);
        try (var connection = sqlite.open();
                var statement = connection.prepareStatement(
                        "SELECT old_value, new_value, values_redacted FROM mp_audit_log WHERE audit_id = ?")) {
            statement.setString(1, auditId.toString());
            try (var row = statement.executeQuery()) {
                row.next();
                assertEquals(AuditValue.REDACTED, row.getString(1));
                assertEquals(AuditValue.REDACTED, row.getString(2));
                assertEquals(1, row.getInt(3));
            }
        }
    }

    private OperationPlan plan(OperationId id, UUID target, String idempotencyKey) {
        Actor actor = new Actor("player", Optional.of(target), "Test Player");
        OperationActionPlan action = new OperationActionPlan("internal-state", new ProviderId("internal"),
                "state-update", "Advance configured state", true, true);
        return new OperationPlan(id, "rankup", actor, target, 0, revisionId, Map.of(new ProviderId("internal"), 1L),
                idempotencyKey, List.of(action), "Advance player to the next configured stage");
    }
}
