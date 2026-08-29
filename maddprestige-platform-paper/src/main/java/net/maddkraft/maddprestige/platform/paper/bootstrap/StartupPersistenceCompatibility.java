package net.maddkraft.maddprestige.platform.paper.bootstrap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.maddkraft.maddprestige.core.admin.config.StoredConfigurationRevision;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteFoundation;

/** Explicitly rejects DB/config combinations that cannot safely reconstruct persisted progression authority. */
final class StartupPersistenceCompatibility {
    /*
     * With no filesystem pointer, only migration metadata, the append-only audit log, and
     * non-APPLIED configuration attempts/documents are safe: none is consulted as live
     * progression authority. Every probe below identifies authority that needs an active
     * configuration to interpret or recover correctly.
     */
    private static final List<AuthorityProbe> POINTER_DEPENDENT_AUTHORITY = List.of(
            new AuthorityProbe("legacy applied configuration revisions",
                    "mp_config_revisions WHERE applied_at IS NOT NULL"),
            new AuthorityProbe("APPLIED configuration revisions",
                    "mp_configuration_revisions_v2 WHERE application_status='APPLIED'"),
            new AuthorityProbe("player stage state", "mp_player_stage_state"),
            new AuthorityProbe("player Prestige state", "mp_player_prestige_state"),
            new AuthorityProbe("requirement baselines", "mp_requirement_baselines"),
            new AuthorityProbe("requirement latches", "mp_requirement_latches"),
            new AuthorityProbe("manual progress", "mp_manual_progress"),
            new AuthorityProbe("currency accounts", "mp_currency_accounts"),
            new AuthorityProbe("currency ledger", "mp_currency_ledger"),
            new AuthorityProbe("stage history", "mp_stage_history"),
            new AuthorityProbe("Prestige history", "mp_prestige_history"),
            new AuthorityProbe("Prestige operation details", "mp_prestige_operation_details"),
            new AuthorityProbe("milestone awards", "mp_milestone_awards"),
            new AuthorityProbe("seasons", "mp_seasons"),
            new AuthorityProbe("player season state", "mp_player_season_state"),
            new AuthorityProbe("season history", "mp_season_history"),
            new AuthorityProbe("operations", "mp_operations"),
            new AuthorityProbe("operation actions", "mp_operation_actions"),
            new AuthorityProbe("recovery events", "mp_recovery_events"),
            new AuthorityProbe("Prestige recovery rewards", "mp_prestige_recovery_rewards"),
            new AuthorityProbe("Prestige recovery costs", "mp_prestige_recovery_costs"),
            new AuthorityProbe("stage remap operations", "mp_stage_remap_operations"),
            new AuthorityProbe("stage remap entries", "mp_stage_remap_entries"),
            new AuthorityProbe("stage transition leases", "mp_stage_transition_leases"),
            new AuthorityProbe("configuration stage transitions", "mp_configuration_stage_transitions"),
            new AuthorityProbe("configuration transition stages", "mp_configuration_transition_stages"),
            new AuthorityProbe("configuration stage reservations", "mp_configuration_stage_reservations"));

    private StartupPersistenceCompatibility() {
    }

    static void assess(SqliteFoundation foundation, Optional<StoredConfigurationRevision> startup) {
        try (Connection connection = foundation.open()) {
            if (startup.isEmpty()) {
                requireNoPointerDependentAuthority(connection);
                return;
            }

            StoredConfigurationRevision active = startup.orElseThrow();
            String latest = latestAppliedRevision(connection).orElseThrow(() ->
                    new PersistenceException("Active configuration pointer exists without APPLIED database history"));
            if (!active.id().value().equals(latest)) {
                throw new PersistenceException("Active configuration pointer is stale relative to the latest "
                        + "APPLIED database configuration revision");
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not assess startup DB/config compatibility", exception);
        }
    }

    private static void requireNoPointerDependentAuthority(Connection connection) throws SQLException {
        ArrayList<String> populated = new ArrayList<>();
        for (AuthorityProbe probe : POINTER_DEPENDENT_AUTHORITY) {
            if (scalarLong(connection, "SELECT COUNT(*) FROM " + probe.fromClause()) != 0) {
                populated.add(probe.description());
            }
        }
        if (!populated.isEmpty()) {
            throw new PersistenceException("Pointer-dependent persistence authority exists but no active "
                    + "configuration pointer can reconstruct it: " + populated);
        }
    }

    private static Optional<String> latestAppliedRevision(Connection connection) throws SQLException {
        String sql = "SELECT revision_id FROM mp_configuration_revisions_v2 "
                + "WHERE application_status='APPLIED' ORDER BY applied_at DESC, created_at DESC, revision_id DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet row = statement.executeQuery()) {
            return row.next() ? Optional.of(row.getString(1)) : Optional.empty();
        }
    }

    private static long scalarLong(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet row = statement.executeQuery()) {
            if (!row.next()) {
                throw new PersistenceException("Startup compatibility query returned no result");
            }
            return row.getLong(1);
        }
    }

    private record AuthorityProbe(String description, String fromClause) {
    }
}
