package net.maddkraft.maddprestige.persistence.sqlite;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.migration.Migration;
import net.maddkraft.maddprestige.persistence.migration.MigrationHistorySchema;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/** Reconstructs the claimed migration prefix and compares its complete structural contract. */
final class SqliteSchemaContractValidator {
    private SqliteSchemaContractValidator() {
    }

    static LinkedHashMap<String, Long> validate(
            Connection actual, List<Migration> migrations, long appliedPrefix) throws SQLException {
        if (appliedPrefix == 0 && !tableExists(actual, MigrationHistorySchema.TABLE)) {
            return new LinkedHashMap<>();
        }
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite::memory:");
        try (Connection expected = dataSource.getConnection()) {
            MigrationHistorySchema.initialize(expected);
            for (int index = 0; index < appliedPrefix; index++) {
                for (String sql : migrations.get(index).statements()) {
                    try (Statement statement = expected.createStatement()) {
                        statement.execute(sql);
                    }
                }
            }
            compareSchemas(actual, expected, appliedPrefix);
        }

        LinkedHashMap<String, Long> rowCounts = new LinkedHashMap<>();
        for (String table : tableNames(actual)) {
            if (!MigrationHistorySchema.TABLE.equals(table)) {
                ApplicationTable applicationTable = ApplicationTable.fromDatabaseName(table);
                rowCounts.put(table, countAndReadRepresentative(actual, applicationTable));
            }
        }
        return rowCounts;
    }

    private static void compareSchemas(Connection actual, Connection expected, long appliedPrefix) throws SQLException {
        Set<String> actualTables = tableNames(actual);
        Set<String> expectedTables = tableNames(expected);
        if (!actualTables.equals(expectedTables)) {
            LinkedHashSet<String> missing = new LinkedHashSet<>(expectedTables);
            missing.removeAll(actualTables);
            LinkedHashSet<String> unexpected = new LinkedHashSet<>(actualTables);
            unexpected.removeAll(expectedTables);
            throw new PersistenceException("Schema version " + appliedPrefix
                    + " is missing required tables " + missing + " or has unexpected tables " + unexpected);
        }
        for (String table : expectedTables) {
            TableContract actualContract = tableContract(actual, table);
            TableContract expectedContract = tableContract(expected, table);
            if (!actualContract.columns().equals(expectedContract.columns())) {
                throw new PersistenceException("Table " + table
                        + " column contract differs from migration prefix " + appliedPrefix);
            }
            if (!actualContract.foreignKeys().equals(expectedContract.foreignKeys())) {
                throw new PersistenceException("Table " + table
                        + " foreign-key contract differs from migration prefix " + appliedPrefix);
            }
            if (!actualContract.uniqueIndexes().equals(expectedContract.uniqueIndexes())) {
                throw new PersistenceException("Table " + table
                        + " UNIQUE/partial-UNIQUE contract differs from migration prefix " + appliedPrefix);
            }
            if (!actualContract.createSql().equals(expectedContract.createSql())) {
                throw new PersistenceException("Table " + table
                        + " database-enforced constraint contract differs from migration prefix " + appliedPrefix);
            }
        }
    }

    private static TableContract tableContract(Connection connection, String table) throws SQLException {
        return new TableContract(
                tableCreateSql(connection, table), columns(connection, table), foreignKeys(connection, table),
                uniqueIndexes(connection, table));
    }

    private static String tableCreateSql(Connection connection, String table) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE type='table' AND name=?")) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new PersistenceException("Missing table definition for " + table);
                }
                return canonicalizeSql(rows.getString(1));
            }
        }
    }

    private static List<ColumnContract> columns(Connection connection, String table) throws SQLException {
        ArrayList<ColumnContract> columns = new ArrayList<>();
        try (var statement = connection.prepareStatement("SELECT * FROM pragma_table_xinfo(?)")) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    columns.add(new ColumnContract(
                            rows.getInt("cid"), rows.getString("name"), normalizeType(rows.getString("type")),
                            rows.getInt("notnull"), rows.getString("dflt_value"), rows.getInt("pk"),
                            rows.getInt("hidden")));
                }
            }
        }
        return List.copyOf(columns);
    }

    private static List<ForeignKeyContract> foreignKeys(Connection connection, String table) throws SQLException {
        ArrayList<ForeignKeyContract> keys = new ArrayList<>();
        try (var statement = connection.prepareStatement("SELECT * FROM pragma_foreign_key_list(?)")) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    keys.add(new ForeignKeyContract(
                            rows.getInt("id"), rows.getInt("seq"), rows.getString("table"), rows.getString("from"),
                            rows.getString("to"), rows.getString("on_update"), rows.getString("on_delete"),
                            rows.getString("match")));
                }
            }
        }
        return List.copyOf(keys);
    }

    private static Map<String, UniqueIndexContract> uniqueIndexes(Connection connection, String table)
            throws SQLException {
        LinkedHashMap<String, UniqueIndexContract> indexes = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement("SELECT * FROM pragma_index_list(?)")) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (rows.getInt("unique") == 0) {
                        continue;
                    }
                    String name = rows.getString("name");
                    indexes.put(name, new UniqueIndexContract(
                            rows.getString("origin"), rows.getInt("partial"), indexColumns(connection, name),
                            indexSql(connection, name)));
                }
            }
        }
        return Map.copyOf(indexes);
    }

    private static List<IndexColumnContract> indexColumns(Connection connection, String index) throws SQLException {
        ArrayList<IndexColumnContract> columns = new ArrayList<>();
        try (var statement = connection.prepareStatement("SELECT * FROM pragma_index_xinfo(?)")) {
            statement.setString(1, index);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    columns.add(new IndexColumnContract(
                            rows.getInt("seqno"), rows.getInt("cid"), rows.getString("name"), rows.getInt("desc"),
                            rows.getString("coll"), rows.getInt("key")));
                }
            }
        }
        return List.copyOf(columns);
    }

    private static String indexSql(Connection connection, String index) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE type='index' AND name=?")) {
            statement.setString(1, index);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getString(1) != null ? canonicalizeSql(rows.getString(1)) : "<auto>";
            }
        }
    }

    private static Set<String> tableNames(Connection connection) throws SQLException {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        String sql = "SELECT name FROM sqlite_master WHERE type='table' "
                + "AND name LIKE 'mp\\_%' ESCAPE '\\' ORDER BY name";
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                names.add(rows.getString(1));
            }
        }
        return Collections.unmodifiableSet(names);
    }

    private static long countAndReadRepresentative(Connection connection, ApplicationTable table) throws SQLException {
        long count;
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(table.countSql())) {
            rows.next();
            count = rows.getLong(1);
        }
        if (count > 0) {
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery(table.representativeSql())) {
                if (!rows.next()) {
                    throw new PersistenceException(
                            "Could not read representative row from " + table.databaseName());
                }
                for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
                    rows.getObject(column);
                }
            }
        }
        return count;
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (var tables = connection.getMetaData().getTables(null, null, table, new String[] {"TABLE"})) {
            return tables.next();
        }
    }

    static String canonicalizeSql(String sql) {
        StringBuilder canonical = new StringBuilder(sql.length());
        boolean pendingWhitespace = false;
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (Character.isWhitespace(current)) {
                pendingWhitespace = canonical.length() > 0;
                continue;
            }
            if (pendingWhitespace) {
                canonical.append(' ');
                pendingWhitespace = false;
            }
            if (current == '\'' || current == '"' || current == '`' || current == '[') {
                char closing = current == '[' ? ']' : current;
                canonical.append(current);
                while (++index < sql.length()) {
                    char quoted = sql.charAt(index);
                    canonical.append(quoted);
                    if (quoted == closing) {
                        if (index + 1 < sql.length() && sql.charAt(index + 1) == closing) {
                            canonical.append(closing);
                            index++;
                        } else {
                            break;
                        }
                    }
                }
            } else {
                canonical.append(Character.toLowerCase(current));
            }
        }
        return canonical.toString();
    }

    private static String normalizeType(String type) {
        return type == null ? "" : type.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private record TableContract(
            String createSql,
            List<ColumnContract> columns,
            List<ForeignKeyContract> foreignKeys,
            Map<String, UniqueIndexContract> uniqueIndexes) {
    }

    private record ColumnContract(
            int position, String name, String type, int notNull, String defaultValue, int primaryKey, int hidden) {
    }

    private record ForeignKeyContract(
            int id, int sequence, String targetTable, String sourceColumn, String targetColumn,
            String onUpdate, String onDelete, String match) {
    }

    private record UniqueIndexContract(
            String origin, int partial, List<IndexColumnContract> columns, String sql) {
    }

    private record IndexColumnContract(
            int sequence, int columnId, String name, int descending, String collation, int keyColumn) {
    }

    private enum ApplicationTable {
        CONFIG_REVISIONS("mp_config_revisions", "SELECT COUNT(*) FROM mp_config_revisions",
                "SELECT * FROM mp_config_revisions LIMIT 1"),
        OPERATIONS("mp_operations", "SELECT COUNT(*) FROM mp_operations",
                "SELECT * FROM mp_operations LIMIT 1"),
        OPERATION_ACTIONS("mp_operation_actions", "SELECT COUNT(*) FROM mp_operation_actions",
                "SELECT * FROM mp_operation_actions LIMIT 1"),
        CURRENCY_ACCOUNTS("mp_currency_accounts", "SELECT COUNT(*) FROM mp_currency_accounts",
                "SELECT * FROM mp_currency_accounts LIMIT 1"),
        AUDIT_LOG("mp_audit_log", "SELECT COUNT(*) FROM mp_audit_log",
                "SELECT * FROM mp_audit_log LIMIT 1"),
        PLAYER_STAGE_STATE("mp_player_stage_state", "SELECT COUNT(*) FROM mp_player_stage_state",
                "SELECT * FROM mp_player_stage_state LIMIT 1"),
        REQUIREMENT_BASELINES("mp_requirement_baselines", "SELECT COUNT(*) FROM mp_requirement_baselines",
                "SELECT * FROM mp_requirement_baselines LIMIT 1"),
        REQUIREMENT_LATCHES("mp_requirement_latches", "SELECT COUNT(*) FROM mp_requirement_latches",
                "SELECT * FROM mp_requirement_latches LIMIT 1"),
        MANUAL_PROGRESS("mp_manual_progress", "SELECT COUNT(*) FROM mp_manual_progress",
                "SELECT * FROM mp_manual_progress LIMIT 1"),
        PLAYER_PRESTIGE_STATE("mp_player_prestige_state", "SELECT COUNT(*) FROM mp_player_prestige_state",
                "SELECT * FROM mp_player_prestige_state LIMIT 1"),
        PRESTIGE_OPERATION_DETAILS("mp_prestige_operation_details",
                "SELECT COUNT(*) FROM mp_prestige_operation_details",
                "SELECT * FROM mp_prestige_operation_details LIMIT 1"),
        CURRENCY_LEDGER("mp_currency_ledger", "SELECT COUNT(*) FROM mp_currency_ledger",
                "SELECT * FROM mp_currency_ledger LIMIT 1"),
        STAGE_HISTORY("mp_stage_history", "SELECT COUNT(*) FROM mp_stage_history",
                "SELECT * FROM mp_stage_history LIMIT 1"),
        PRESTIGE_HISTORY("mp_prestige_history", "SELECT COUNT(*) FROM mp_prestige_history",
                "SELECT * FROM mp_prestige_history LIMIT 1"),
        MILESTONE_AWARDS("mp_milestone_awards", "SELECT COUNT(*) FROM mp_milestone_awards",
                "SELECT * FROM mp_milestone_awards LIMIT 1"),
        SEASONS("mp_seasons", "SELECT COUNT(*) FROM mp_seasons", "SELECT * FROM mp_seasons LIMIT 1"),
        PLAYER_SEASON_STATE("mp_player_season_state", "SELECT COUNT(*) FROM mp_player_season_state",
                "SELECT * FROM mp_player_season_state LIMIT 1"),
        SEASON_HISTORY("mp_season_history", "SELECT COUNT(*) FROM mp_season_history",
                "SELECT * FROM mp_season_history LIMIT 1"),
        RECOVERY_EVENTS("mp_recovery_events", "SELECT COUNT(*) FROM mp_recovery_events",
                "SELECT * FROM mp_recovery_events LIMIT 1"),
        PRESTIGE_RECOVERY_REWARDS("mp_prestige_recovery_rewards",
                "SELECT COUNT(*) FROM mp_prestige_recovery_rewards",
                "SELECT * FROM mp_prestige_recovery_rewards LIMIT 1"),
        PRESTIGE_RECOVERY_COSTS("mp_prestige_recovery_costs",
                "SELECT COUNT(*) FROM mp_prestige_recovery_costs",
                "SELECT * FROM mp_prestige_recovery_costs LIMIT 1"),
        CONFIGURATION_REVISIONS("mp_configuration_revisions_v2",
                "SELECT COUNT(*) FROM mp_configuration_revisions_v2",
                "SELECT * FROM mp_configuration_revisions_v2 LIMIT 1"),
        CONFIGURATION_DOCUMENTS("mp_configuration_revision_documents",
                "SELECT COUNT(*) FROM mp_configuration_revision_documents",
                "SELECT * FROM mp_configuration_revision_documents LIMIT 1"),
        STAGE_REMAP_OPERATIONS("mp_stage_remap_operations", "SELECT COUNT(*) FROM mp_stage_remap_operations",
                "SELECT * FROM mp_stage_remap_operations LIMIT 1"),
        STAGE_REMAP_ENTRIES("mp_stage_remap_entries", "SELECT COUNT(*) FROM mp_stage_remap_entries",
                "SELECT * FROM mp_stage_remap_entries LIMIT 1"),
        STAGE_TRANSITION_LEASES("mp_stage_transition_leases", "SELECT COUNT(*) FROM mp_stage_transition_leases",
                "SELECT * FROM mp_stage_transition_leases LIMIT 1"),
        CONFIGURATION_STAGE_TRANSITIONS("mp_configuration_stage_transitions",
                "SELECT COUNT(*) FROM mp_configuration_stage_transitions",
                "SELECT * FROM mp_configuration_stage_transitions LIMIT 1"),
        CONFIGURATION_TRANSITION_STAGES("mp_configuration_transition_stages",
                "SELECT COUNT(*) FROM mp_configuration_transition_stages",
                "SELECT * FROM mp_configuration_transition_stages LIMIT 1"),
        CONFIGURATION_STAGE_RESERVATIONS("mp_configuration_stage_reservations",
                "SELECT COUNT(*) FROM mp_configuration_stage_reservations",
                "SELECT * FROM mp_configuration_stage_reservations LIMIT 1");

        private final String databaseName;
        private final String countSql;
        private final String representativeSql;

        ApplicationTable(String databaseName, String countSql, String representativeSql) {
            this.databaseName = databaseName;
            this.countSql = countSql;
            this.representativeSql = representativeSql;
        }

        private static ApplicationTable fromDatabaseName(String databaseName) {
            for (ApplicationTable table : values()) {
                if (table.databaseName.equals(databaseName)) {
                    return table;
                }
            }
            throw new PersistenceException("Table is not in the trusted MaddPrestige schema set: " + databaseName);
        }

        private String databaseName() {
            return databaseName;
        }

        private String countSql() {
            return countSql;
        }

        private String representativeSql() {
            return representativeSql;
        }
    }
}
