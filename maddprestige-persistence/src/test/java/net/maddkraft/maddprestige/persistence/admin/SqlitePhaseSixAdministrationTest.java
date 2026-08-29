package net.maddkraft.maddprestige.persistence.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.core.admin.ManualPrestigeAdjustment;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationApplicationStatus;
import net.maddkraft.maddprestige.core.admin.config.StoredConfigurationRevision;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.StalePlayerStageStateException;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteFoundation;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteMigrations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlitePhaseSixAdministrationTest {
    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("[A41] Durable history stores exact documents and rollback as a new append-only revision")
    void historyPersistsExactRollbackRevision() throws Exception {
        SqliteFoundation sqlite = migrated("history.db");
        SqliteConfigurationHistoryStore history = new SqliteConfigurationHistoryStore(sqlite);
        CompiledConfiguration content = compiled(Map.of("progression.yml",
                "# retained 😀\r\nschema-version: 3\r\nactive: false\r\n"));
        StoredConfigurationRevision firstAttempt = attempted(new ConfigRevisionId("revision_one"),
                Optional.empty(), Optional.empty(), content, "Initial");
        history.append(firstAttempt);
        history.replaceOutcome(firstAttempt.withOutcome(ConfigurationApplicationStatus.APPLIED,
                Optional.of(NOW), Optional.empty()));

        StoredConfigurationRevision rollbackAttempt = attempted(new ConfigRevisionId("revision_rollback"),
                Optional.of(firstAttempt.id()), Optional.of(firstAttempt.id()), content, "Rollback");
        history.append(rollbackAttempt);
        history.replaceOutcome(rollbackAttempt.withOutcome(ConfigurationApplicationStatus.APPLIED,
                Optional.of(NOW.plusSeconds(1)), Optional.empty()));

        StoredConfigurationRevision loaded = history.find(rollbackAttempt.id()).orElseThrow();
        assertEquals(ConfigurationApplicationStatus.APPLIED, loaded.status());
        assertEquals(Optional.of(firstAttempt.id()), loaded.rollbackSource());
        assertEquals(content, loaded.compiled());
        assertEquals(List.of(rollbackAttempt.id(), firstAttempt.id()), history.recent(10).stream()
                .map(StoredConfigurationRevision::id).toList());
        assertThrows(PersistenceException.class, () -> history.replaceOutcome(loaded.withOutcome(
                ConfigurationApplicationStatus.FAILED, Optional.empty(), Optional.of("late overwrite"))));
        assertEquals("10", scalar(sqlite, "SELECT MAX(version) FROM mp_schema_migrations WHERE result='APPLIED'"));
        String firstStorageHash = scalar(sqlite,
                "SELECT content_hash FROM mp_config_revisions WHERE revision_id='revision_one'");
        String rollbackStorageHash = scalar(sqlite,
                "SELECT content_hash FROM mp_config_revisions WHERE revision_id='revision_rollback'");
        assertNotEquals(firstStorageHash, rollbackStorageHash,
                "legacy unique storage hashes must not corrupt equal canonical rollback content");
        assertEquals(content.contentHash().value(), loaded.compiled().contentHash().value());
    }

    @Test
    @DisplayName("[S1][S2] History query variants bind unusual runtime text without changing SQL structure")
    void historyQueriesKeepAdversarialTextAsBoundData() {
        SqliteConfigurationHistoryStore history = new SqliteConfigurationHistoryStore(migrated("history-data.db"));
        String unusual = "O'Brien'; DROP TABLE mp_configuration_revisions_v2; -- %_ /* */ 雪 😀\nsecond line";
        CompiledConfiguration content = compiled(Map.of("progression.yml", "reason: \"" + unusual + "\"\n"));
        StoredConfigurationRevision first = new StoredConfigurationRevision(
                new ConfigRevisionId("bound_data_one"), Optional.empty(), Optional.empty(), content,
                new Actor("console", Optional.empty(), "Owner ' -- 😀"), "command;--", unusual,
                ValidationReport.VALID, unusual, ConfigurationApplicationStatus.ATTEMPTED, NOW,
                Optional.empty(), Optional.empty());
        StoredConfigurationRevision second = new StoredConfigurationRevision(
                new ConfigRevisionId("bound_data_two"), Optional.of(first.id()), Optional.empty(), content,
                new Actor("console", Optional.empty(), "Owner %_ 雪"), "gui/*data*/", unusual + " later",
                ValidationReport.VALID, unusual + " later", ConfigurationApplicationStatus.ATTEMPTED,
                NOW.plusSeconds(1), Optional.empty(), Optional.empty());
        history.append(first);
        history.replaceOutcome(first.withOutcome(ConfigurationApplicationStatus.APPLIED,
                Optional.of(NOW), Optional.empty()));
        history.append(second);
        history.replaceOutcome(second.withOutcome(ConfigurationApplicationStatus.FAILED,
                Optional.empty(), Optional.of(unusual)));

        StoredConfigurationRevision loadedFirst = history.find(first.id()).orElseThrow();
        StoredConfigurationRevision loadedSecond = history.find(second.id()).orElseThrow();
        assertEquals(unusual, loadedFirst.reason());
        assertEquals(unusual, loadedFirst.diffSummary());
        assertEquals(content, loadedFirst.compiled());
        assertEquals(Optional.of(unusual), loadedSecond.failure());
        assertEquals(List.of(second.id(), first.id()), history.recent(2).stream()
                .map(StoredConfigurationRevision::id).toList());
    }

    @Test
    @DisplayName("[A57] Manual Prestige CAS and complete actor/target/old/new/time audit commit atomically")
    void manualPrestigeAdjustmentIsFullyAudited() throws Exception {
        SqliteFoundation sqlite = migrated("prestige.db");
        UUID playerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        ConfigRevisionId revision = new ConfigRevisionId("active_revision");
        seedPrestige(sqlite, playerId, revision);
        SqlitePrestigeAdministrationStore store = new SqlitePrestigeAdministrationStore(sqlite, CLOCK);
        assertThrows(IllegalArgumentException.class, () -> new ManualPrestigeAdjustment(
                playerId, 7, 3, 9, revision,
                new Actor("staff", Optional.of(actorId), "Moderator"), "staff-gui", "Invalid divergent counters"));
        assertEquals("1:5:7", scalar(sqlite, "SELECT current_prestige || ':' || lifetime_prestige || ':' "
                + "|| state_revision FROM mp_player_prestige_state"));
        assertEquals("0", scalar(sqlite, "SELECT COUNT(*) FROM mp_audit_log"));

        ManualPrestigeAdjustment adjustment = new ManualPrestigeAdjustment(playerId, 7, 3, 3, revision,
                new Actor("staff", Optional.of(actorId), "Moderator"), "staff-gui", "Correct imported counters");

        var updated = store.adjust(adjustment);

        assertEquals(3, updated.currentPrestige());
        assertEquals(3, updated.lifetimePrestige());
        assertEquals(8, updated.stateRevision());
        assertEquals(actorId.toString(), scalar(sqlite, "SELECT actor_uuid FROM mp_audit_log"));
        assertEquals(playerId.toString(), scalar(sqlite, "SELECT target_uuid FROM mp_audit_log"));
        assertEquals("current=1,lifetime=5,state-revision=7",
                scalar(sqlite, "SELECT old_value FROM mp_audit_log"));
        assertEquals("current=3,lifetime=3,state-revision=8",
                scalar(sqlite, "SELECT new_value FROM mp_audit_log"));
        assertEquals(NOW.toString(), scalar(sqlite, "SELECT occurred_at FROM mp_audit_log"));
        assertEquals("staff-gui", scalar(sqlite, "SELECT source_surface FROM mp_audit_log"));

        assertThrows(StalePlayerStageStateException.class, () -> store.adjust(adjustment));
        assertEquals("1", scalar(sqlite, "SELECT COUNT(*) FROM mp_audit_log"));
        assertEquals("8", scalar(sqlite, "SELECT state_revision FROM mp_player_prestige_state"));
    }

    private SqliteFoundation migrated(String fileName) {
        Path database = temporaryDirectory.resolve(fileName);
        SqliteFoundation sqlite = new SqliteFoundation(database);
        new MigrationRunner(sqlite, new FileBackupService(database,
                temporaryDirectory.resolve(fileName + "-backups"), CLOCK), CLOCK)
                .migrate(SqliteMigrations.phaseSix());
        return sqlite;
    }

    private static StoredConfigurationRevision attempted(
            ConfigRevisionId id,
            Optional<ConfigRevisionId> parent,
            Optional<ConfigRevisionId> rollbackSource,
            CompiledConfiguration configuration,
            String reason) {
        return new StoredConfigurationRevision(id, parent, rollbackSource, configuration,
                new Actor("console", Optional.empty(), "Owner"), "command", reason, ValidationReport.VALID,
                "progression.yml", ConfigurationApplicationStatus.ATTEMPTED, NOW, Optional.empty(),
                Optional.empty());
    }

    private static CompiledConfiguration compiled(Map<String, String> documents) {
        return new CompiledConfiguration(RevisionHasher.hashDocuments(documents), documents);
    }

    private static void seedPrestige(
            SqliteFoundation sqlite,
            UUID playerId,
            ConfigRevisionId revision) throws Exception {
        try (Connection connection = sqlite.open()) {
            try (PreparedStatement config = connection.prepareStatement(
                    "INSERT INTO mp_config_revisions (revision_id, content_hash, created_at, actor, source_surface, "
                            + "validation_summary, diff_summary) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                config.setString(1, revision.value());
                config.setString(2, "a".repeat(64));
                config.setString(3, NOW.toString());
                config.setString(4, "Owner");
                config.setString(5, "test");
                config.setString(6, "valid");
                config.setString(7, "initial");
                config.executeUpdate();
            }
            try (PreparedStatement player = connection.prepareStatement(
                    "INSERT INTO mp_player_prestige_state (player_uuid, current_prestige, lifetime_prestige, "
                            + "state_revision, config_revision_id, prestige_scope_id, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                player.setString(1, playerId.toString());
                player.setLong(2, 1);
                player.setLong(3, 5);
                player.setLong(4, 7);
                player.setString(5, revision.value());
                player.setString(6, "prestige-scope");
                player.setString(7, NOW.minusSeconds(600).toString());
                player.setString(8, NOW.minusSeconds(60).toString());
                player.executeUpdate();
            }
        }
    }

    private static String scalar(SqliteFoundation sqlite, String sql) throws Exception {
        try (Connection connection = sqlite.open(); PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet row = statement.executeQuery()) {
            assertTrue(row.next());
            return row.getString(1);
        }
    }
}
