package net.maddkraft.maddprestige.persistence.sqlite;

import java.util.List;
import java.util.ArrayList;
import net.maddkraft.maddprestige.persistence.migration.Migration;

public final class SqliteMigrations {
    private SqliteMigrations() {
    }

    public static List<Migration> phaseOne() {
        return List.of(Migration.of(1, "Phase 1 configuration, operation, currency, and audit foundations", List.of(
                """
                CREATE TABLE mp_config_revisions (
                    revision_id TEXT PRIMARY KEY,
                    content_hash TEXT NOT NULL UNIQUE,
                    parent_revision_id TEXT NULL REFERENCES mp_config_revisions(revision_id),
                    created_at TEXT NOT NULL,
                    applied_at TEXT NULL,
                    actor TEXT NOT NULL,
                    source_surface TEXT NOT NULL,
                    validation_summary TEXT NOT NULL,
                    diff_summary TEXT NOT NULL,
                    backup_checksum TEXT NULL,
                    CHECK (length(content_hash) = 64),
                    CHECK (backup_checksum IS NULL OR length(backup_checksum) = 64)
                )
                """,
                """
                CREATE TABLE mp_operations (
                    operation_id TEXT PRIMARY KEY,
                    operation_type TEXT NOT NULL,
                    target_uuid TEXT NOT NULL,
                    idempotency_key TEXT NOT NULL,
                    state TEXT NOT NULL,
                    expected_state_revision INTEGER NOT NULL,
                    config_revision_id TEXT NOT NULL REFERENCES mp_config_revisions(revision_id),
                    provider_generations TEXT NOT NULL,
                    redacted_preview TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    CHECK (expected_state_revision >= 0),
                    CHECK (state IN ('PLANNED','PREPARED','EXECUTING','STATE_COMMITTED','COMPLETED','COMPENSATING','COMPENSATED','FAILED','NEEDS_RECONCILIATION')),
                    UNIQUE (operation_type, target_uuid, idempotency_key)
                )
                """,
                """
                CREATE TABLE mp_operation_actions (
                    operation_id TEXT NOT NULL REFERENCES mp_operations(operation_id) ON DELETE CASCADE,
                    action_index INTEGER NOT NULL,
                    action_id TEXT NOT NULL,
                    provider_id TEXT NOT NULL,
                    action_type TEXT NOT NULL,
                    state TEXT NOT NULL,
                    redacted_description TEXT NOT NULL,
                    reversible INTEGER NOT NULL,
                    idempotent INTEGER NOT NULL,
                    failure_reason TEXT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (operation_id, action_index),
                    UNIQUE (operation_id, action_id),
                    CHECK (action_index >= 0),
                    CHECK (state IN ('PENDING','STARTED','SUCCEEDED','VERIFIED','FAILED','COMPENSATED','UNCERTAIN')),
                    CHECK (reversible IN (0, 1)),
                    CHECK (idempotent IN (0, 1))
                )
                """,
                """
                CREATE TABLE mp_currency_accounts (
                    player_uuid TEXT NOT NULL,
                    currency_id TEXT NOT NULL,
                    balance_text TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (player_uuid, currency_id),
                    CHECK (length(balance_text) BETWEEN 1 AND 512)
                )
                """,
                """
                CREATE TABLE mp_audit_log (
                    audit_id TEXT PRIMARY KEY,
                    actor_type TEXT NOT NULL,
                    actor_uuid TEXT NULL,
                    actor_name TEXT NOT NULL,
                    target_uuid TEXT NULL,
                    operation_id TEXT NULL REFERENCES mp_operations(operation_id),
                    config_revision_id TEXT NULL REFERENCES mp_config_revisions(revision_id),
                    provider_action TEXT NOT NULL,
                    old_value TEXT NULL,
                    new_value TEXT NULL,
                    values_redacted INTEGER NOT NULL,
                    source_surface TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    outcome TEXT NOT NULL,
                    failure_uncertainty TEXT NULL,
                    correlation_id TEXT NOT NULL,
                    occurred_at TEXT NOT NULL,
                    CHECK (values_redacted IN (0, 1))
                )
                """,
                "CREATE INDEX mp_operations_state_idx ON mp_operations(state, updated_at)",
                "CREATE INDEX mp_operations_target_idx ON mp_operations(target_uuid, created_at)",
                "CREATE INDEX mp_operation_actions_state_idx ON mp_operation_actions(state, updated_at)",
                "CREATE INDEX mp_audit_target_time_idx ON mp_audit_log(target_uuid, occurred_at)",
                "CREATE INDEX mp_audit_correlation_idx ON mp_audit_log(correlation_id)")));
    }

    public static List<Migration> phaseTwo() {
        ArrayList<Migration> migrations = new ArrayList<>(phaseOne());
        migrations.add(Migration.of(2, "Phase 2 authoritative player stage state", List.of(
                """
                CREATE TABLE mp_player_stage_state (
                    player_uuid TEXT PRIMARY KEY,
                    stage_id TEXT NOT NULL,
                    state_revision INTEGER NOT NULL,
                    config_revision_id TEXT NOT NULL REFERENCES mp_config_revisions(revision_id),
                    stage_entered_at TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    last_reconciled_at TEXT NULL,
                    last_provider_generation INTEGER NULL,
                    imported_at TEXT NULL,
                    CHECK (state_revision >= 0),
                    CHECK (length(stage_id) BETWEEN 1 AND 64),
                    CHECK (last_provider_generation IS NULL OR last_provider_generation >= 1)
                )
                """,
                "CREATE INDEX mp_player_stage_id_idx ON mp_player_stage_state(stage_id, player_uuid)",
                "CREATE INDEX mp_player_stage_revision_idx ON mp_player_stage_state(config_revision_id, updated_at)")));
        return List.copyOf(migrations);
    }

    public static List<Migration> phaseThree() {
        ArrayList<Migration> migrations = new ArrayList<>(phaseTwo());
        migrations.add(Migration.of(3, "Phase 3 requirement state and batched manual progress", List.of(
                """
                CREATE TABLE mp_requirement_baselines (
                    player_uuid TEXT NOT NULL,
                    requirement_id TEXT NOT NULL,
                    measurement_scope TEXT NOT NULL,
                    scope_instance TEXT NOT NULL,
                    semantic_fingerprint TEXT NOT NULL,
                    value_type TEXT NOT NULL,
                    value_text TEXT NOT NULL,
                    provider_generation INTEGER NOT NULL,
                    created_at TEXT NOT NULL,
                    PRIMARY KEY (player_uuid, requirement_id, measurement_scope, scope_instance, semantic_fingerprint),
                    CHECK (length(requirement_id) BETWEEN 1 AND 64),
                    CHECK (length(scope_instance) BETWEEN 1 AND 64),
                    CHECK (length(semantic_fingerprint) = 64 OR
                           (length(semantic_fingerprint) = 69 AND semantic_fingerprint GLOB 'rsf[0-9]:[0-9a-f]*')),
                    CHECK (provider_generation >= 1)
                )
                """,
                """
                CREATE TABLE mp_requirement_latches (
                    player_uuid TEXT NOT NULL,
                    requirement_id TEXT NOT NULL,
                    measurement_scope TEXT NOT NULL,
                    scope_instance TEXT NOT NULL,
                    semantic_fingerprint TEXT NOT NULL,
                    completed_at TEXT NOT NULL,
                    PRIMARY KEY (player_uuid, requirement_id, measurement_scope, scope_instance, semantic_fingerprint),
                    CHECK (length(requirement_id) BETWEEN 1 AND 64),
                    CHECK (length(scope_instance) BETWEEN 1 AND 64),
                    CHECK (length(semantic_fingerprint) = 64 OR
                           (length(semantic_fingerprint) = 69 AND semantic_fingerprint GLOB 'rsf[0-9]:[0-9a-f]*'))
                )
                """,
                """
                CREATE TABLE mp_manual_progress (
                    provider_id TEXT NOT NULL,
                    metric_id TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    value_type TEXT NOT NULL,
                    value_text TEXT NOT NULL,
                    update_version INTEGER NOT NULL,
                    provenance TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (provider_id, metric_id, player_uuid),
                    CHECK (length(provider_id) BETWEEN 1 AND 64),
                    CHECK (length(metric_id) BETWEEN 1 AND 64),
                    CHECK (length(value_text) BETWEEN 1 AND 512),
                    CHECK (update_version >= 0)
                )
                """,
                "CREATE INDEX mp_requirement_baseline_scope_idx ON mp_requirement_baselines(player_uuid, measurement_scope, scope_instance)",
                "CREATE INDEX mp_requirement_latch_scope_idx ON mp_requirement_latches(player_uuid, measurement_scope, scope_instance)",
                "CREATE INDEX mp_manual_progress_player_idx ON mp_manual_progress(player_uuid, provider_id)")));
        return List.copyOf(migrations);
    }
}
