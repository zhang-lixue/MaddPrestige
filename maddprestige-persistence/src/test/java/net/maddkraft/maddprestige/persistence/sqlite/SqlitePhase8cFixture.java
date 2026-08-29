package net.maddkraft.maddprestige.persistence.sqlite;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.VerifiedBackup;
import net.maddkraft.maddprestige.persistence.migration.Migration;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;

final class SqlitePhase8cFixture {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-17T20:00:00Z"), java.time.ZoneOffset.UTC);
    static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID OPERATION = UUID.fromString("20000000-0000-0000-0000-000000000002");
    static final UUID REMAP = UUID.fromString("30000000-0000-0000-0000-000000000003");
    static final UUID REMAPPED_PLAYER = UUID.fromString("40000000-0000-0000-0000-000000000004");
    static final String REVISION = "phase8c-fixture-revision";
    static final String EXACT_DECIMAL = "9007199254740993.123456789012345678901";

    private SqlitePhase8cFixture() {
    }

    static SqliteFoundation historical(Path database, int prefix) {
        SqliteFoundation foundation = new SqliteFoundation(database);
        List<Migration> migrations = SqliteMigrations.phaseEightC().subList(0, prefix);
        new MigrationRunner(foundation, ignored -> verifiedFixture(database), CLOCK).migrate(migrations);
        populate(foundation, prefix);
        return foundation;
    }

    static void populate(SqliteFoundation foundation, int prefix) {
        execute(foundation, "INSERT INTO mp_config_revisions (revision_id, content_hash, created_at, actor, "
                + "source_surface, validation_summary, diff_summary) VALUES (?, ?, ?, ?, ?, ?, ?)",
                REVISION, RevisionHasher.hashText("phase8c fixture").value(), CLOCK.instant().toString(), "Owner",
                "phase8c-test", "valid", "fixture");
        execute(foundation, "INSERT INTO mp_operations (operation_id, operation_type, target_uuid, idempotency_key, "
                + "state, expected_state_revision, config_revision_id, provider_generations, redacted_preview, "
                + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                OPERATION.toString(), "prestige", PLAYER.toString(), "request-uuid-is-distinct", "NEEDS_RECONCILIATION",
                7L, REVISION, "internal=7", "sanitized", CLOCK.instant().toString(), CLOCK.instant().toString());
        execute(foundation, "INSERT INTO mp_operation_actions (operation_id, action_index, action_id, provider_id, "
                + "action_type, state, redacted_description, reversible, idempotent, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                OPERATION.toString(), 0, "external-uncertain", "fixture", "reward", "UNCERTAIN", "sanitized",
                0, 0, CLOCK.instant().toString());
        execute(foundation, "INSERT INTO mp_currency_accounts (player_uuid, currency_id, balance_text, updated_at) "
                + "VALUES (?, ?, ?, ?)", PLAYER.toString(), "points", EXACT_DECIMAL, CLOCK.instant().toString());

        if (prefix >= 2) {
            execute(foundation, "INSERT INTO mp_player_stage_state (player_uuid, stage_id, state_revision, "
                    + "config_revision_id, stage_entered_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    PLAYER.toString(), "veteran", 7L, REVISION, CLOCK.instant().toString(), CLOCK.instant().toString(),
                    CLOCK.instant().toString());
        }
        if (prefix >= 3) {
            execute(foundation, "INSERT INTO mp_requirement_baselines (player_uuid, requirement_id, "
                    + "measurement_scope, scope_instance, semantic_fingerprint, value_type, value_text, "
                    + "provider_generation, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    PLAYER.toString(), "money", "LIFETIME", "global", "a".repeat(64), "DECIMAL", EXACT_DECIMAL,
                    7L, CLOCK.instant().toString());
            execute(foundation, "INSERT INTO mp_requirement_latches (player_uuid, requirement_id, "
                    + "measurement_scope, scope_instance, semantic_fingerprint, completed_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?)", PLAYER.toString(), "money", "LIFETIME", "global",
                    "a".repeat(64), CLOCK.instant().toString());
            execute(foundation, "INSERT INTO mp_manual_progress (provider_id, metric_id, player_uuid, value_type, "
                    + "value_text, update_version, provenance, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    "manual", "votes", PLAYER.toString(), "INTEGER", "42", 3L, "fixture",
                    CLOCK.instant().toString());
        }
        if (prefix >= 4) {
            execute(foundation, "INSERT INTO mp_player_prestige_state (player_uuid, current_prestige, "
                    + "lifetime_prestige, state_revision, config_revision_id, prestige_scope_id, last_prestiged_at, "
                    + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", PLAYER.toString(), 3, 5, 9L,
                    REVISION, "global", CLOCK.instant().toString(), CLOCK.instant().toString(), CLOCK.instant().toString());
            execute(foundation, "INSERT INTO mp_prestige_operation_details (operation_id, source_stage_id, "
                    + "reset_stage_id, expected_prestige_revision, current_before, current_after, lifetime_before, "
                    + "lifetime_after, scope_before, scope_after, stage_config_provenance, "
                    + "prestige_config_provenance, confirmation_snapshot, planned_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", OPERATION.toString(), "veteran",
                    "member", 9L, 3, 4, 5, 6, "global", "global", REVISION, REVISION, "fixture snapshot",
                    CLOCK.instant().toString());
            execute(foundation, "INSERT INTO mp_prestige_history (history_id, player_uuid, operation_id, "
                    + "event_type, source_stage_id, reset_stage_id, current_before, current_after, lifetime_before, "
                    + "lifetime_after, result, costs_snapshot, rewards_snapshot, config_revision_id, occurred_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(),
                    PLAYER.toString(), OPERATION.toString(), "PRESTIGE_COMPLETED", "veteran", "member", 3, 4, 5, 6,
                    "SUCCESS", "fixture costs", "fixture rewards", REVISION, CLOCK.instant().toString());
            execute(foundation, "INSERT INTO mp_currency_ledger (operation_id, action_id, player_uuid, currency_id, "
                    + "delta_text, balance_after_text, mutation_kind, actor_type, actor_name, source, reason, "
                    + "config_revision_id, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    OPERATION.toString(), "ledger-action", PLAYER.toString(), "points", "0.000000000000000001",
                    EXACT_DECIMAL, "ADJUSTMENT", "console", "Owner", "fixture", "migration qualification", REVISION,
                    CLOCK.instant().toString());
            execute(foundation, "INSERT INTO mp_stage_history (history_id, player_uuid, stage_id, entered_at, "
                    + "operation_id, actor_type, actor_name, reason, config_revision_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), PLAYER.toString(), "veteran", CLOCK.instant().toString(),
                    OPERATION.toString(), "console", "Owner", "fixture", REVISION);
            execute(foundation, "INSERT INTO mp_recovery_events (recovery_id, operation_id, previous_state, "
                    + "resulting_state, decision, detail, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), OPERATION.toString(), "EXECUTING", "NEEDS_RECONCILIATION",
                    "PRESERVE_UNCERTAINTY", "sanitized fixture", CLOCK.instant().toString());
        }
        if (prefix >= 6) {
            Map<String, String> documents = Map.of("progression.yml", """
                    schema-version: 3
                    active: true
                    reconciliation-policy: warn-only
                    baseline: veteran
                    stages:
                      veteran:
                        enabled: true
                        display-name: Veteran
                        projection: none
                    order: [veteran]
                    """);
            String canonical = RevisionHasher.hashDocuments(documents).value();
            execute(foundation, "INSERT INTO mp_configuration_revisions_v2 (revision_id, canonical_content_hash, "
                    + "actor_type, actor_name, source_surface, reason, validation_summary, diff_summary, "
                    + "application_status, created_at, applied_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    REVISION, canonical, "console", "Owner", "phase8c-test", "fixture", "valid", "fixture", "APPLIED",
                    CLOCK.instant().toString(), CLOCK.instant().toString());
            execute(foundation, "INSERT INTO mp_configuration_revision_documents (revision_id, document_name, "
                    + "document_hash, document_content) VALUES (?, ?, ?, ?)", REVISION, "progression.yml",
                    RevisionHasher.hashText(documents.get("progression.yml")).value(), documents.get("progression.yml"));
        }
        if (prefix >= 7) {
            execute(foundation, "INSERT INTO mp_stage_remap_operations (operation_id, config_revision_id, "
                    + "plan_revision, plan_hash, actor_type, actor_name, reason, status, migrated_players, "
                    + "created_at, updated_at, detail) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    REMAP.toString(), REVISION, "fixture-plan", RevisionHasher.hashText("fixture remap").value(),
                    "console", "Owner", "migration fixture", "MIGRATED_PENDING_CONFIG", 1,
                    CLOCK.instant().toString(), CLOCK.instant().toString(), "awaiting exact configuration outcome");
            execute(foundation, "INSERT INTO mp_stage_remap_entries (operation_id, player_uuid, source_stage_id, "
                    + "target_stage_id, expected_state_revision, resulting_state_revision, "
                    + "source_config_revision_id) VALUES (?, ?, ?, ?, ?, ?, ?)", REMAP.toString(),
                    REMAPPED_PLAYER.toString(), "legacy", "veteran", 6L, 7L, REVISION);
        }
        if (prefix == 8) {
            execute(foundation, "INSERT INTO mp_stage_transition_leases (operation_id, target_stage_id, "
                    + "config_revision_id, acquired_at) VALUES (?, ?, ?, ?)", OPERATION.toString(), "veteran",
                    REVISION, CLOCK.instant().toString());
        } else if (prefix >= 9) {
            execute(foundation, "INSERT INTO mp_stage_transition_leases (operation_id, source_stage_id, "
                    + "target_stage_id, config_revision_id, lease_token, owner_type, participation_complete, "
                    + "acquired_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", OPERATION.toString(), "veteran", "elite",
                    REVISION, OPERATION.toString(), "OPERATION", 1, CLOCK.instant().toString());
        }
        if (prefix >= 10) {
            String candidateHash = scalar(foundation,
                    "SELECT canonical_content_hash FROM mp_configuration_revisions_v2 WHERE revision_id='"
                            + REVISION + "'");
            execute(foundation, "INSERT INTO mp_configuration_stage_transitions (config_revision_id, "
                    + "candidate_hash, remap_operation_id, status, scope_complete, created_at, updated_at, detail) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", REVISION, candidateHash, REMAP.toString(),
                    "NEEDS_RECONCILIATION", 1, CLOCK.instant().toString(), CLOCK.instant().toString(),
                    "fixture preserves explicit transition uncertainty");
            execute(foundation, "INSERT INTO mp_configuration_transition_stages (config_revision_id, stage_id, "
                    + "reservation_kind) VALUES (?, ?, ?)", REVISION, "legacy", "REMOVED");
            execute(foundation, "INSERT INTO mp_configuration_stage_reservations (stage_id, config_revision_id, "
                    + "reserved_at) VALUES (?, ?, ?)", "legacy", REVISION, CLOCK.instant().toString());
        }
    }

    static String scalar(SqliteFoundation foundation, String sql) {
        try (Connection connection = foundation.open();
                var statement = connection.createStatement();
                var row = statement.executeQuery(sql)) {
            return row.next() ? row.getString(1) : "";
        } catch (SQLException exception) {
            throw new PersistenceException("Fixture scalar failed", exception);
        }
    }

    static void execute(SqliteFoundation foundation, String sql, Object... values) {
        try (Connection connection = foundation.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not populate Phase 8C fixture", exception);
        }
    }

    private static VerifiedBackup verifiedFixture(Path database) {
        try {
            if (!java.nio.file.Files.exists(database)) {
                java.nio.file.Files.createFile(database);
            }
            return new VerifiedBackup("fixture", Optional.of(database),
                    Optional.of(SqliteBackupService.sha256(database)), CLOCK.instant(), true,
                    "Controlled fixture initialization");
        } catch (IOException exception) {
            throw new PersistenceException("Could not initialize fixture backup", exception);
        }
    }
}
