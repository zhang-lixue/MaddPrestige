package net.maddkraft.maddprestige.platform.paper.bootstrap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationApplicationStatus;
import net.maddkraft.maddprestige.core.admin.config.StoredConfigurationRevision;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.VerifiedBackup;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteDatabaseValidator;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteFoundation;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteMigrations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupPersistenceCompatibilityTest {
    private static final Instant NOW = Instant.parse("2026-08-17T20:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("[A63] Fresh DB is compatible with dormant configuration authority")
    void acceptsFreshDormantDatabase() {
        SqliteFoundation foundation = current("fresh.sqlite");

        assertDoesNotThrow(() -> StartupPersistenceCompatibility.assess(foundation, Optional.empty()));
    }

    @Test
    @DisplayName("[A63] Persisted operations without an active configuration fail closed")
    void rejectsAuthorityWithoutActiveConfiguration() {
        SqliteFoundation foundation = current("missing-config.sqlite");
        StoredConfigurationRevision stored = revision(foundation, "revision-one", validDocuments(), NOW);
        insertOperation(foundation, stored.id());

        PersistenceException failure = assertThrows(PersistenceException.class,
                () -> StartupPersistenceCompatibility.assess(foundation, Optional.empty()));

        assertTrue(failure.getMessage().contains("no active configuration"));
    }

    @Test
    @DisplayName("[OR8C-02] APPLIED config history alone requires the missing active pointer")
    void rejectsAppliedConfigurationWithoutPointerOrPlayerRows() {
        SqliteFoundation foundation = current("applied-config-no-pointer.sqlite");
        revision(foundation, "revision-applied-only", validDocuments(), NOW);

        assertThrows(PersistenceException.class,
                () -> StartupPersistenceCompatibility.assess(foundation, Optional.empty()));
    }

    @Test
    @DisplayName("[OR8C-02] Currency and manual progress authority require an active pointer")
    void rejectsCurrencyAndProgressWithoutPointer() {
        SqliteFoundation foundation = current("currency-progress-no-pointer.sqlite");
        execute(foundation, "INSERT INTO mp_currency_accounts "
                + "(player_uuid, currency_id, balance_text, updated_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID().toString(), "points", "12.5", NOW.toString());
        execute(foundation, "INSERT INTO mp_manual_progress (provider_id, metric_id, player_uuid, value_type, "
                + "value_text, update_version, provenance, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "manual", "votes", UUID.randomUUID().toString(), "INTEGER", "3", 1, "test", NOW.toString());

        PersistenceException failure = assertThrows(PersistenceException.class,
                () -> StartupPersistenceCompatibility.assess(foundation, Optional.empty()));
        assertTrue(failure.getMessage().contains("currency accounts"));
        assertTrue(failure.getMessage().contains("manual progress"));
    }

    @Test
    @DisplayName("[OR8C-02] Season/remap/lease/reservation authority requires an active pointer")
    void rejectsRecoveryAndReservationAuthorityWithoutPointer() {
        SqliteFoundation foundation = current("recovery-no-pointer.sqlite");
        ConfigRevisionId revision = dormantRevision(foundation, "revision-attempted");
        execute(foundation, "INSERT INTO mp_seasons (season_id, display_name_snapshot, lifecycle_state, scope_id, "
                + "season_progress_policy, config_revision_id, started_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "season", "Season", "ACTIVE", "global", "PRESERVE", revision.value(), NOW.toString());
        UUID operation = insertOperation(foundation, revision);
        execute(foundation, "INSERT INTO mp_stage_transition_leases (operation_id, source_stage_id, "
                + "target_stage_id, config_revision_id, lease_token, owner_type, participation_complete, "
                + "acquired_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", operation.toString(), "veteran", "elite",
                revision.value(), UUID.randomUUID().toString(), "OPERATION", 1, NOW.toString());
        execute(foundation, "INSERT INTO mp_stage_remap_operations (operation_id, config_revision_id, "
                + "plan_revision, plan_hash, actor_type, actor_name, reason, status, migrated_players, created_at, "
                + "updated_at, detail) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(),
                revision.value(), "plan", "a".repeat(64), "console", "Owner", "test", "CONFIG_FAILED_SAFE", 1,
                NOW.toString(), NOW.toString(), "requires reconciliation");
        execute(foundation, "INSERT INTO mp_configuration_stage_transitions (config_revision_id, candidate_hash, "
                + "status, scope_complete, created_at, updated_at, detail) VALUES (?, ?, ?, ?, ?, ?, ?)",
                revision.value(), RevisionHasher.hashDocuments(validDocuments()).value(), "NEEDS_RECONCILIATION", 1,
                NOW.toString(), NOW.toString(), "test");
        execute(foundation, "INSERT INTO mp_configuration_transition_stages "
                + "(config_revision_id, stage_id, reservation_kind) VALUES (?, ?, ?)",
                revision.value(), "removed", "REMOVED");
        execute(foundation, "INSERT INTO mp_configuration_stage_reservations "
                + "(stage_id, config_revision_id, reserved_at) VALUES (?, ?, ?)",
                "removed", revision.value(), NOW.toString());

        PersistenceException failure = assertThrows(PersistenceException.class,
                () -> StartupPersistenceCompatibility.assess(foundation, Optional.empty()));
        assertTrue(failure.getMessage().contains("seasons"));
        assertTrue(failure.getMessage().contains("stage remap operations"));
        assertTrue(failure.getMessage().contains("stage transition leases"));
        assertTrue(failure.getMessage().contains("configuration stage reservations"));
    }

    @Test
    @DisplayName("[OR8C-02] Pointer-independent audit and non-APPLIED config attempts may remain dormant")
    void acceptsExplicitPointerIndependentHistory() {
        SqliteFoundation foundation = current("safe-history-no-pointer.sqlite");
        ConfigRevisionId revision = dormantRevision(foundation, "revision-failed");
        execute(foundation, "UPDATE mp_configuration_revisions_v2 SET application_status='FAILED', "
                + "failure_detail='rejected candidate' WHERE revision_id=?", revision.value());
        execute(foundation, "INSERT INTO mp_audit_log (audit_id, actor_type, actor_name, config_revision_id, "
                + "provider_action, values_redacted, source_surface, reason, outcome, correlation_id, occurred_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(), "console", "Owner",
                revision.value(), "config.validate", 1, "test", "candidate rejected", "FAILED",
                UUID.randomUUID().toString(), NOW.toString());

        assertDoesNotThrow(() -> StartupPersistenceCompatibility.assess(foundation, Optional.empty()));
    }

    @Test
    @DisplayName("[OR8C-01][OR8C-02] Startup preflight failure occurs before service publication")
    void rejectsBeforeProductionServicePublication() {
        SqliteFoundation foundation = current("publication-gate.sqlite");
        revision(foundation, "revision-unpublished", validDocuments(), NOW);
        boolean[] servicePublished = {false};

        assertThrows(PersistenceException.class, () -> {
            StartupPersistenceCompatibility.assess(foundation, Optional.empty());
            servicePublished[0] = true;
        });

        assertFalse(servicePublished[0]);
    }

    @Test
    @DisplayName("[OR8C-01] Production schema preflight rejects before service publication")
    void rejectsMalformedSchemaBeforeProductionServicePublication() {
        Path database = temporaryDirectory.resolve("malformed-publication-gate.sqlite");
        SqliteFoundation foundation = current(database.getFileName().toString());
        execute(foundation, "ALTER TABLE mp_stage_history DROP COLUMN actor_uuid");
        boolean[] servicePublished = {false};

        assertThrows(PersistenceException.class, () -> {
            SqliteDatabaseValidator.validate(database, SqliteMigrations.current());
            servicePublished[0] = true;
        });

        assertFalse(servicePublished[0]);
    }

    @Test
    @DisplayName("[A63][P9B] Populated release-candidate stage state archives and starts under empty numeric config at P0")
    void acceptsReconstructiblePopulatedState() {
        SqliteFoundation foundation = release("compatible.sqlite");
        StoredConfigurationRevision stored = revision(foundation, "revision-current", validDocuments(), NOW);
        UUID player = insertPlayer(foundation, stored.id(), "veteran", true);
        migrate(foundation, SqliteMigrations.current());

        assertDoesNotThrow(() -> StartupPersistenceCompatibility.assess(foundation, Optional.of(stored)));
        assertEquals("0", scalar(foundation,
                "SELECT COUNT(*) FROM mp_player_stage_state WHERE player_uuid='" + player + "'"));
        assertEquals("veteran", scalar(foundation,
                "SELECT stage_id FROM mp_legacy_stage_player_state WHERE player_uuid='" + player + "'"));
        assertEquals("0:0", scalar(foundation,
                "SELECT current_prestige || ':' || lifetime_prestige FROM mp_player_prestige_state "
                        + "WHERE player_uuid='" + player + "'"));
    }

    @Test
    @DisplayName("[A63][P9B] A stale active pointer remains fail closed")
    void rejectsIncompatibleDatabaseAuthority() {
        SqliteFoundation staleFoundation = current("stale.sqlite");
        StoredConfigurationRevision stale = revision(staleFoundation, "revision-stale", validDocuments(), NOW);
        revision(staleFoundation, "revision-newer", validDocuments(), NOW.plusSeconds(1));
        assertThrows(PersistenceException.class,
                () -> StartupPersistenceCompatibility.assess(staleFoundation, Optional.of(stale)));

    }

    @Test
    @DisplayName("[P9B] Numeric Prestige state without a legacy stage survives startup compatibility assessment")
    void acceptsNumericPrestigeStateWithoutLegacyStage() {
        SqliteFoundation foundation = current("numeric-prestige-only.sqlite");
        StoredConfigurationRevision stored = revision(foundation, "revision-numeric", validDocuments(), NOW);
        insertPrestigeOnly(foundation, stored.id());

        assertDoesNotThrow(() -> StartupPersistenceCompatibility.assess(foundation, Optional.of(stored)));
    }

    @Test
    @DisplayName("[P9B] Persistence compatibility does not reinterpret stage schema as numeric authority")
    void leavesConfigurationSemanticValidationToRuntimeComposition() {
        SqliteFoundation foundation = current("future-config.sqlite");
        Map<String, String> future = Map.of("progression.yml", """
                schema-version: 99
                active: false
                stages: {}
                order: []
                """);
        StoredConfigurationRevision stored = revision(foundation, "revision-future", future, NOW);

        assertDoesNotThrow(() -> StartupPersistenceCompatibility.assess(foundation, Optional.of(stored)));
    }

    private SqliteFoundation current(String name) {
        Path database = temporaryDirectory.resolve(name);
        SqliteFoundation foundation = new SqliteFoundation(database);
        migrate(foundation, SqliteMigrations.current());
        return foundation;
    }

    private SqliteFoundation release(String name) {
        Path database = temporaryDirectory.resolve(name);
        SqliteFoundation foundation = new SqliteFoundation(database);
        migrate(foundation, SqliteMigrations.throughVersionEleven());
        return foundation;
    }

    private void migrate(SqliteFoundation foundation,
            java.util.List<net.maddkraft.maddprestige.persistence.migration.Migration> migrations) {
        new MigrationRunner(foundation, ignored -> new VerifiedBackup(
                "fixture", Optional.of(foundation.databaseFile()),
                Optional.of(RevisionHasher.hashText("fixture")), NOW, true,
                "controlled startup fixture"), CLOCK).migrate(migrations);
    }

    private static StoredConfigurationRevision revision(
            SqliteFoundation foundation, String rawId, Map<String, String> documents, Instant appliedAt) {
        ConfigRevisionId id = new ConfigRevisionId(rawId);
        CompiledConfiguration compiled = new CompiledConfiguration(RevisionHasher.hashDocuments(documents), documents);
        String legacyHash = RevisionHasher.hashText(compiled.contentHash().value() + "\0" + rawId).value();
        execute(foundation, "INSERT INTO mp_config_revisions (revision_id, content_hash, created_at, applied_at, "
                + "actor, source_surface, validation_summary, diff_summary) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                rawId, legacyHash, appliedAt.toString(), appliedAt.toString(), "Owner", "migration-recovery-test", "valid", "test");
        execute(foundation, "INSERT INTO mp_configuration_revisions_v2 (revision_id, canonical_content_hash, "
                + "actor_type, actor_name, source_surface, reason, validation_summary, diff_summary, "
                + "application_status, created_at, applied_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                rawId, compiled.contentHash().value(), "console", "Owner", "migration-recovery-test", "test", "valid", "test",
                "APPLIED", appliedAt.toString(), appliedAt.toString());
        documents.forEach((name, content) -> execute(foundation,
                "INSERT INTO mp_configuration_revision_documents (revision_id, document_name, document_hash, "
                        + "document_content) VALUES (?, ?, ?, ?)",
                rawId, name, RevisionHasher.hashText(content).value(), content));
        return new StoredConfigurationRevision(id, Optional.empty(), Optional.empty(), compiled,
                new Actor("console", Optional.empty(), "Owner"), "migration-recovery-test", "test", ValidationReport.VALID,
                "test", ConfigurationApplicationStatus.APPLIED, appliedAt, Optional.of(appliedAt), Optional.empty());
    }

    private static UUID insertPlayer(
            SqliteFoundation foundation, ConfigRevisionId revision, String stage, boolean withPrestige) {
        UUID playerId = UUID.randomUUID();
        String player = playerId.toString();
        execute(foundation, "INSERT INTO mp_player_stage_state (player_uuid, stage_id, state_revision, "
                + "config_revision_id, stage_entered_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                player, stage, 4, revision.value(), NOW.toString(), NOW.toString(), NOW.toString());
        if (withPrestige) {
            execute(foundation, "INSERT INTO mp_player_prestige_state (player_uuid, current_prestige, "
                    + "lifetime_prestige, state_revision, config_revision_id, prestige_scope_id, created_at, "
                    + "updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", player, 2, 3, 5, revision.value(), "global",
                    NOW.toString(), NOW.toString());
        }
        return playerId;
    }

    private static void insertPrestigeOnly(SqliteFoundation foundation, ConfigRevisionId revision) {
        execute(foundation, "INSERT INTO mp_player_prestige_state (player_uuid, current_prestige, "
                + "lifetime_prestige, state_revision, config_revision_id, prestige_scope_id, created_at, "
                + "updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(), 2, 2, 5,
                revision.value(), "global", NOW.toString(), NOW.toString());
    }

    private static UUID insertOperation(SqliteFoundation foundation, ConfigRevisionId revision) {
        UUID operation = UUID.randomUUID();
        execute(foundation, "INSERT INTO mp_operations (operation_id, operation_type, target_uuid, idempotency_key, "
                + "state, expected_state_revision, config_revision_id, provider_generations, redacted_preview, "
                + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", operation.toString(),
                "rankup", UUID.randomUUID().toString(), "request", "PREPARED", 0, revision.value(), "", "sanitized",
                NOW.toString(), NOW.toString());
        return operation;
    }

    private static ConfigRevisionId dormantRevision(SqliteFoundation foundation, String rawId) {
        ConfigRevisionId id = new ConfigRevisionId(rawId);
        Map<String, String> documents = validDocuments();
        String canonicalHash = RevisionHasher.hashDocuments(documents).value();
        execute(foundation, "INSERT INTO mp_config_revisions (revision_id, content_hash, created_at, actor, "
                + "source_surface, validation_summary, diff_summary) VALUES (?, ?, ?, ?, ?, ?, ?)", rawId,
                RevisionHasher.hashText("dormant-" + rawId).value(), NOW.toString(), "Owner", "migration-recovery-test",
                "valid", "test");
        execute(foundation, "INSERT INTO mp_configuration_revisions_v2 (revision_id, canonical_content_hash, "
                + "actor_type, actor_name, source_surface, reason, validation_summary, diff_summary, "
                + "application_status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rawId, canonicalHash,
                "console", "Owner", "migration-recovery-test", "test", "valid", "test", "ATTEMPTED", NOW.toString());
        documents.forEach((name, content) -> execute(foundation,
                "INSERT INTO mp_configuration_revision_documents (revision_id, document_name, document_hash, "
                        + "document_content) VALUES (?, ?, ?, ?)",
                rawId, name, RevisionHasher.hashText(content).value(), content));
        return id;
    }

    private static void execute(SqliteFoundation foundation, String sql, Object... values) {
        try (Connection connection = foundation.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not prepare startup compatibility fixture", exception);
        }
    }

    private static String scalar(SqliteFoundation foundation, String sql) {
        try (Connection connection = foundation.open(); PreparedStatement statement = connection.prepareStatement(sql);
                var row = statement.executeQuery()) {
            return row.next() ? row.getString(1) : null;
        } catch (SQLException exception) {
            throw new PersistenceException("Could not query startup compatibility fixture", exception);
        }
    }

    private static Map<String, String> validDocuments() {
        return Map.of("progression.yml", """
                schema-version: 3
                active: false
                reconciliation-policy: warn-only
                """);
    }
}
