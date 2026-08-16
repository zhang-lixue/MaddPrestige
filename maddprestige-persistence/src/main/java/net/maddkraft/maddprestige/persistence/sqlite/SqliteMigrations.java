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

    public static List<Migration> phaseFour() {
        ArrayList<Migration> migrations = new ArrayList<>(phaseThree());
        migrations.add(Migration.of(4, "Phase 4 Prestige, currency ledger, milestones, seasons, history, recovery",
                List.of(
                """
                CREATE TABLE mp_player_prestige_state (
                    player_uuid TEXT PRIMARY KEY,
                    current_prestige INTEGER NOT NULL,
                    lifetime_prestige INTEGER NOT NULL,
                    state_revision INTEGER NOT NULL,
                    config_revision_id TEXT NOT NULL REFERENCES mp_config_revisions(revision_id),
                    prestige_scope_id TEXT NOT NULL,
                    last_prestiged_at TEXT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    CHECK (current_prestige >= 0),
                    CHECK (lifetime_prestige >= current_prestige),
                    CHECK (state_revision >= 0),
                    CHECK (length(prestige_scope_id) BETWEEN 1 AND 64)
                )
                """,
                """
                CREATE TABLE mp_prestige_operation_details (
                    operation_id TEXT PRIMARY KEY REFERENCES mp_operations(operation_id) ON DELETE CASCADE,
                    source_stage_id TEXT NOT NULL,
                    reset_stage_id TEXT NOT NULL,
                    expected_prestige_revision INTEGER NOT NULL,
                    current_before INTEGER NOT NULL,
                    current_after INTEGER NOT NULL,
                    lifetime_before INTEGER NOT NULL,
                    lifetime_after INTEGER NOT NULL,
                    scope_before TEXT NOT NULL,
                    scope_after TEXT NOT NULL,
                    stage_config_provenance TEXT NOT NULL,
                    prestige_config_provenance TEXT NOT NULL,
                    confirmation_snapshot TEXT NOT NULL,
                    planned_at TEXT NOT NULL,
                    CHECK (expected_prestige_revision >= 0),
                    CHECK (current_before >= 0 AND current_after >= current_before),
                    CHECK (lifetime_before >= 0 AND lifetime_after >= lifetime_before)
                )
                """,
                """
                CREATE TABLE mp_currency_ledger (
                    sequence_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    operation_id TEXT NOT NULL,
                    action_id TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    currency_id TEXT NOT NULL,
                    delta_text TEXT NOT NULL,
                    balance_after_text TEXT NOT NULL,
                    mutation_kind TEXT NOT NULL,
                    actor_type TEXT NOT NULL,
                    actor_uuid TEXT NULL,
                    actor_name TEXT NOT NULL,
                    source TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    config_revision_id TEXT NOT NULL REFERENCES mp_config_revisions(revision_id),
                    occurred_at TEXT NOT NULL,
                    UNIQUE (operation_id, action_id),
                    CHECK (length(delta_text) BETWEEN 1 AND 512),
                    CHECK (length(balance_after_text) BETWEEN 1 AND 512),
                    CHECK (mutation_kind IN ('EARN','SPEND','ADJUSTMENT','PRESTIGE_RESET','COMPENSATION'))
                )
                """,
                """
                CREATE TABLE mp_stage_history (
                    history_id TEXT PRIMARY KEY,
                    player_uuid TEXT NOT NULL,
                    stage_id TEXT NOT NULL,
                    entered_at TEXT NOT NULL,
                    operation_id TEXT NULL REFERENCES mp_operations(operation_id),
                    actor_type TEXT NOT NULL,
                    actor_name TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    config_revision_id TEXT NOT NULL REFERENCES mp_config_revisions(revision_id)
                )
                """,
                """
                CREATE TABLE mp_prestige_history (
                    history_id TEXT PRIMARY KEY,
                    player_uuid TEXT NOT NULL,
                    operation_id TEXT NOT NULL REFERENCES mp_operations(operation_id),
                    event_type TEXT NOT NULL,
                    source_stage_id TEXT NOT NULL,
                    reset_stage_id TEXT NOT NULL,
                    current_before INTEGER NOT NULL,
                    current_after INTEGER NOT NULL,
                    lifetime_before INTEGER NOT NULL,
                    lifetime_after INTEGER NOT NULL,
                    result TEXT NOT NULL,
                    costs_snapshot TEXT NOT NULL,
                    rewards_snapshot TEXT NOT NULL,
                    config_revision_id TEXT NOT NULL REFERENCES mp_config_revisions(revision_id),
                    occurred_at TEXT NOT NULL,
                    UNIQUE (operation_id, event_type)
                )
                """,
                """
                CREATE TABLE mp_milestone_awards (
                    player_uuid TEXT NOT NULL,
                    milestone_id TEXT NOT NULL,
                    repeatability_key TEXT NOT NULL,
                    operation_id TEXT NOT NULL REFERENCES mp_operations(operation_id),
                    config_revision_id TEXT NOT NULL REFERENCES mp_config_revisions(revision_id),
                    reward_snapshot TEXT NOT NULL,
                    awarded_at TEXT NOT NULL,
                    PRIMARY KEY (player_uuid, milestone_id, repeatability_key),
                    UNIQUE (operation_id, milestone_id)
                )
                """,
                """
                CREATE TABLE mp_seasons (
                    season_id TEXT PRIMARY KEY,
                    display_name_snapshot TEXT NOT NULL,
                    lifecycle_state TEXT NOT NULL,
                    scope_id TEXT NOT NULL,
                    season_progress_policy TEXT NOT NULL,
                    config_revision_id TEXT NOT NULL REFERENCES mp_config_revisions(revision_id),
                    started_at TEXT NOT NULL,
                    ended_at TEXT NULL,
                    archived_at TEXT NULL,
                    CHECK (lifecycle_state IN ('ACTIVE','ENDED','ARCHIVED')),
                    CHECK (season_progress_policy IN ('RESET','PRESERVE')),
                    CHECK (length(scope_id) BETWEEN 1 AND 64)
                )
                """,
                """
                CREATE UNIQUE INDEX mp_seasons_one_active_idx ON mp_seasons(lifecycle_state)
                    WHERE lifecycle_state = 'ACTIVE'
                """,
                """
                CREATE TABLE mp_player_season_state (
                    player_uuid TEXT NOT NULL,
                    season_id TEXT NOT NULL REFERENCES mp_seasons(season_id),
                    progress_text TEXT NOT NULL,
                    entered_at TEXT NOT NULL,
                    completed_at TEXT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (player_uuid, season_id)
                )
                """,
                """
                CREATE TABLE mp_season_history (
                    history_id TEXT PRIMARY KEY,
                    season_id TEXT NOT NULL REFERENCES mp_seasons(season_id),
                    event_type TEXT NOT NULL,
                    display_name_snapshot TEXT NOT NULL,
                    scope_id TEXT NOT NULL,
                    config_revision_id TEXT NOT NULL REFERENCES mp_config_revisions(revision_id),
                    occurred_at TEXT NOT NULL,
                    UNIQUE (season_id, event_type)
                )
                """,
                """
                CREATE TABLE mp_recovery_events (
                    recovery_id TEXT PRIMARY KEY,
                    operation_id TEXT NOT NULL REFERENCES mp_operations(operation_id),
                    previous_state TEXT NOT NULL,
                    resulting_state TEXT NOT NULL,
                    decision TEXT NOT NULL,
                    detail TEXT NOT NULL,
                    occurred_at TEXT NOT NULL
                )
                """,
                """
                CREATE TABLE mp_prestige_recovery_rewards (
                    operation_id TEXT NOT NULL REFERENCES mp_prestige_operation_details(operation_id)
                        ON DELETE CASCADE,
                    action_id TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    reward_id TEXT NOT NULL,
                    provider_id TEXT NOT NULL,
                    reward_type TEXT NOT NULL,
                    value_type TEXT NOT NULL,
                    value_text TEXT NOT NULL,
                    metadata_text TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    failure_policy TEXT NOT NULL,
                    repeatability TEXT NOT NULL,
                    config_revision_id TEXT NOT NULL REFERENCES mp_config_revisions(revision_id),
                    provider_generation INTEGER NOT NULL,
                    reconcilable INTEGER NOT NULL,
                    reversible INTEGER NOT NULL,
                    idempotent INTEGER NOT NULL,
                    external_uncertainty INTEGER NOT NULL,
                    redacted_preview TEXT NOT NULL,
                    PRIMARY KEY (operation_id, action_id),
                    CHECK (provider_generation >= 1),
                    CHECK (reconcilable IN (0, 1)),
                    CHECK (reversible IN (0, 1)),
                    CHECK (idempotent IN (0, 1)),
                    CHECK (external_uncertainty IN (0, 1))
                )
                """,
                """
                CREATE TABLE mp_prestige_recovery_costs (
                    operation_id TEXT NOT NULL REFERENCES mp_prestige_operation_details(operation_id)
                        ON DELETE CASCADE,
                    action_id TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    cost_id TEXT NOT NULL,
                    provider_id TEXT NOT NULL,
                    cost_type TEXT NOT NULL,
                    value_type TEXT NOT NULL,
                    value_text TEXT NOT NULL,
                    metadata_text TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    config_revision_id TEXT NOT NULL REFERENCES mp_config_revisions(revision_id),
                    provider_generation INTEGER NOT NULL,
                    idempotent INTEGER NOT NULL,
                    reversible INTEGER NOT NULL,
                    reconcilable INTEGER NOT NULL,
                    external_uncertainty INTEGER NOT NULL,
                    redacted_preview TEXT NOT NULL,
                    PRIMARY KEY (operation_id, action_id),
                    CHECK (provider_generation >= 1),
                    CHECK (idempotent IN (0, 1)),
                    CHECK (reversible IN (0, 1)),
                    CHECK (reconcilable IN (0, 1)),
                    CHECK (external_uncertainty IN (0, 1))
                )
                """,
                "CREATE INDEX mp_player_prestige_count_idx ON mp_player_prestige_state(lifetime_prestige DESC, player_uuid)",
                "CREATE INDEX mp_currency_ledger_player_idx ON mp_currency_ledger(player_uuid, currency_id, sequence_id DESC)",
                "CREATE INDEX mp_currency_ledger_operation_idx ON mp_currency_ledger(operation_id, action_id)",
                "CREATE INDEX mp_stage_history_player_idx ON mp_stage_history(player_uuid, entered_at DESC)",
                "CREATE INDEX mp_prestige_history_player_idx ON mp_prestige_history(player_uuid, occurred_at DESC)",
                "CREATE INDEX mp_milestone_awards_player_idx ON mp_milestone_awards(player_uuid, awarded_at DESC)",
                 "CREATE INDEX mp_player_season_progress_idx ON mp_player_season_state(season_id, progress_text, player_uuid)",
                 "CREATE INDEX mp_season_history_time_idx ON mp_season_history(season_id, occurred_at)",
                 "CREATE INDEX mp_recovery_operation_idx ON mp_recovery_events(operation_id, occurred_at)")));
        migrations.add(Migration.of(5, "Phase 4 stage-history actor UUID provenance", List.of(
                "ALTER TABLE mp_stage_history ADD COLUMN actor_uuid TEXT NULL")));
        return List.copyOf(migrations);
    }
}
