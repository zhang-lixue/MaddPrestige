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

    public static List<Migration> phaseSix() {
        ArrayList<Migration> migrations = new ArrayList<>(phaseFour());
        migrations.add(Migration.of(6, "Phase 6 durable configuration history and exact revision documents", List.of(
                """
                CREATE TABLE mp_configuration_revisions_v2 (
                    revision_id TEXT PRIMARY KEY,
                    parent_revision_id TEXT NULL,
                    rollback_source_revision_id TEXT NULL,
                    canonical_content_hash TEXT NOT NULL,
                    actor_type TEXT NOT NULL,
                    actor_uuid TEXT NULL,
                    actor_name TEXT NOT NULL,
                    source_surface TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    validation_summary TEXT NOT NULL,
                    diff_summary TEXT NOT NULL,
                    application_status TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    applied_at TEXT NULL,
                    failure_detail TEXT NULL,
                    CHECK (length(canonical_content_hash) = 64),
                    CHECK (application_status IN ('ATTEMPTED','APPLIED','FAILED'))
                )
                """,
                """
                CREATE TABLE mp_configuration_revision_documents (
                    revision_id TEXT NOT NULL REFERENCES mp_configuration_revisions_v2(revision_id)
                        ON DELETE CASCADE,
                    document_name TEXT NOT NULL,
                    document_hash TEXT NOT NULL,
                    document_content TEXT NOT NULL,
                    PRIMARY KEY (revision_id, document_name),
                    CHECK (length(document_hash) = 64)
                )
                """,
                "CREATE INDEX mp_configuration_revision_status_time_idx ON "
                        + "mp_configuration_revisions_v2(application_status, created_at DESC)",
                "CREATE INDEX mp_configuration_revision_parent_idx ON "
                        + "mp_configuration_revisions_v2(parent_revision_id, created_at DESC)")));
        migrations.add(Migration.of(7, "Phase 6 recoverable referenced-stage replacement migration", List.of(
                """
                CREATE TABLE mp_stage_remap_operations (
                    operation_id TEXT PRIMARY KEY,
                    config_revision_id TEXT NOT NULL REFERENCES mp_config_revisions(revision_id),
                    plan_revision TEXT NOT NULL,
                    plan_hash TEXT NOT NULL,
                    actor_type TEXT NOT NULL,
                    actor_uuid TEXT NULL,
                    actor_name TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    status TEXT NOT NULL,
                    migrated_players INTEGER NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    detail TEXT NOT NULL,
                    CHECK (length(plan_hash) = 64),
                    CHECK (migrated_players >= 1),
                    CHECK (status IN ('MIGRATED_PENDING_CONFIG','CONFIG_APPLIED','CONFIG_FAILED_SAFE'))
                )
                """,
                """
                CREATE TABLE mp_stage_remap_entries (
                    operation_id TEXT NOT NULL REFERENCES mp_stage_remap_operations(operation_id)
                        ON DELETE CASCADE,
                    player_uuid TEXT NOT NULL,
                    source_stage_id TEXT NOT NULL,
                    target_stage_id TEXT NOT NULL,
                    expected_state_revision INTEGER NOT NULL,
                    resulting_state_revision INTEGER NOT NULL,
                    source_config_revision_id TEXT NOT NULL REFERENCES mp_config_revisions(revision_id),
                    PRIMARY KEY (operation_id, player_uuid),
                    CHECK (expected_state_revision >= 0),
                    CHECK (resulting_state_revision = expected_state_revision + 1),
                    CHECK (source_stage_id <> target_stage_id)
                )
                """,
                "CREATE INDEX mp_stage_remap_status_time_idx ON mp_stage_remap_operations(status, updated_at)",
                "CREATE INDEX mp_stage_remap_entry_source_idx ON "
                        + "mp_stage_remap_entries(source_stage_id, player_uuid)")));
        migrations.add(Migration.of(8, "Phase 6 durable stage-transition fence", List.of(
                """
                CREATE TABLE mp_stage_transition_leases (
                    operation_id TEXT PRIMARY KEY,
                    target_stage_id TEXT NOT NULL,
                    config_revision_id TEXT NOT NULL REFERENCES mp_config_revisions(revision_id),
                    acquired_at TEXT NOT NULL
                )
                """,
                "CREATE INDEX mp_stage_transition_target_idx ON "
                        + "mp_stage_transition_leases(target_stage_id, acquired_at)")));
        migrations.add(Migration.of(9, "Phase 6 owned source-and-target stage participation", List.of(
                """
                CREATE TABLE mp_stage_transition_leases_v9 (
                    operation_id TEXT PRIMARY KEY REFERENCES mp_operations(operation_id) ON DELETE CASCADE,
                    source_stage_id TEXT NULL,
                    target_stage_id TEXT NOT NULL,
                    config_revision_id TEXT NOT NULL REFERENCES mp_config_revisions(revision_id),
                    lease_token TEXT NOT NULL UNIQUE,
                    owner_type TEXT NOT NULL,
                    participation_complete INTEGER NOT NULL,
                    acquired_at TEXT NOT NULL,
                    CHECK (owner_type = 'OPERATION'),
                    CHECK (participation_complete IN (0, 1)),
                    CHECK (participation_complete = 0 OR source_stage_id IS NOT NULL)
                )
                """,
                """
                INSERT INTO mp_stage_transition_leases_v9 (
                    operation_id, source_stage_id, target_stage_id, config_revision_id,
                    lease_token, owner_type, participation_complete, acquired_at
                )
                SELECT lease.operation_id, NULL, lease.target_stage_id, lease.config_revision_id,
                    lease.operation_id, 'OPERATION', 0, lease.acquired_at
                FROM mp_stage_transition_leases lease
                JOIN mp_operations operation ON operation.operation_id = lease.operation_id
                WHERE operation.state IN (
                    'PREPARED','EXECUTING','STATE_COMMITTED','COMPENSATING','NEEDS_RECONCILIATION'
                )
                """,
                "DROP TABLE mp_stage_transition_leases",
                "ALTER TABLE mp_stage_transition_leases_v9 RENAME TO mp_stage_transition_leases",
                "CREATE INDEX mp_stage_transition_source_idx ON "
                        + "mp_stage_transition_leases(source_stage_id, acquired_at)",
                "CREATE INDEX mp_stage_transition_target_idx ON "
                        + "mp_stage_transition_leases(target_stage_id, acquired_at)",
                "CREATE INDEX mp_stage_transition_owner_idx ON "
                        + "mp_stage_transition_leases(owner_type, participation_complete, acquired_at)")));
        migrations.add(Migration.of(10, "Phase 6 configuration-owned unsafe-stage reservations", List.of(
                """
                CREATE TABLE mp_configuration_stage_transitions (
                    config_revision_id TEXT PRIMARY KEY
                        REFERENCES mp_configuration_revisions_v2(revision_id),
                    prior_revision_id TEXT NULL,
                    candidate_hash TEXT NOT NULL,
                    remap_operation_id TEXT NULL
                        REFERENCES mp_stage_remap_operations(operation_id),
                    status TEXT NOT NULL,
                    scope_complete INTEGER NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    detail TEXT NOT NULL,
                    CHECK (length(candidate_hash) = 64),
                    CHECK (status IN (
                        'RESERVED','NEEDS_RECONCILIATION','CONFIG_APPLIED','CONFIG_FAILED_SAFE'
                    )),
                    CHECK (scope_complete IN (0, 1))
                )
                """,
                """
                CREATE TABLE mp_configuration_transition_stages (
                    config_revision_id TEXT NOT NULL
                        REFERENCES mp_configuration_stage_transitions(config_revision_id) ON DELETE CASCADE,
                    stage_id TEXT NOT NULL,
                    reservation_kind TEXT NOT NULL,
                    PRIMARY KEY (config_revision_id, stage_id),
                    CHECK (reservation_kind IN ('REMOVED','DISABLED','UNKNOWN_LEGACY'))
                )
                """,
                """
                CREATE TABLE mp_configuration_stage_reservations (
                    stage_id TEXT PRIMARY KEY,
                    config_revision_id TEXT NOT NULL
                        REFERENCES mp_configuration_stage_transitions(config_revision_id) ON DELETE CASCADE,
                    reserved_at TEXT NOT NULL,
                    UNIQUE (config_revision_id, stage_id)
                )
                """,
                """
                INSERT OR IGNORE INTO mp_configuration_stage_transitions (
                    config_revision_id, prior_revision_id, candidate_hash, remap_operation_id,
                    status, scope_complete, created_at, updated_at, detail
                )
                SELECT operation.config_revision_id, history.parent_revision_id,
                    history.canonical_content_hash, operation.operation_id,
                    'NEEDS_RECONCILIATION', 0, operation.created_at, operation.updated_at,
                    'Migrated pending remap has an unknown complete unsafe-stage scope; reconcile fail-closed.'
                FROM mp_stage_remap_operations operation
                JOIN mp_configuration_revisions_v2 history
                    ON history.revision_id = operation.config_revision_id
                WHERE operation.status IN ('MIGRATED_PENDING_CONFIG','CONFIG_FAILED_SAFE')
                """,
                """
                INSERT OR IGNORE INTO mp_configuration_transition_stages (
                    config_revision_id, stage_id, reservation_kind
                )
                SELECT operation.config_revision_id, entry.source_stage_id, 'UNKNOWN_LEGACY'
                FROM mp_stage_remap_operations operation
                JOIN mp_stage_remap_entries entry ON entry.operation_id = operation.operation_id
                JOIN mp_configuration_stage_transitions transition
                    ON transition.config_revision_id = operation.config_revision_id
                WHERE operation.status IN ('MIGRATED_PENDING_CONFIG','CONFIG_FAILED_SAFE')
                """,
                """
                INSERT OR IGNORE INTO mp_configuration_stage_reservations (
                    stage_id, config_revision_id, reserved_at
                )
                SELECT stages.stage_id, stages.config_revision_id, transition.updated_at
                FROM mp_configuration_transition_stages stages
                JOIN mp_configuration_stage_transitions transition
                    ON transition.config_revision_id = stages.config_revision_id
                WHERE transition.status IN ('RESERVED','NEEDS_RECONCILIATION')
                """,
                "CREATE INDEX mp_configuration_transition_status_time_idx ON "
                        + "mp_configuration_stage_transitions(status, updated_at)",
                "CREATE INDEX mp_configuration_transition_stage_owner_idx ON "
                        + "mp_configuration_transition_stages(stage_id, config_revision_id)",
                "CREATE INDEX mp_configuration_reservation_owner_idx ON "
                        + "mp_configuration_stage_reservations(config_revision_id, stage_id)")));
        return List.copyOf(migrations);
    }
}
