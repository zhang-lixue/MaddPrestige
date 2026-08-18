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
                rowCounts.put(table, countAndReadRepresentative(actual, table));
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
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("PRAGMA table_xinfo(" + quote(table) + ")")) {
            while (rows.next()) {
                columns.add(new ColumnContract(
                        rows.getInt("cid"), rows.getString("name"), normalizeType(rows.getString("type")),
                        rows.getInt("notnull"), rows.getString("dflt_value"), rows.getInt("pk"),
                        rows.getInt("hidden")));
            }
        }
        return List.copyOf(columns);
    }

    private static List<ForeignKeyContract> foreignKeys(Connection connection, String table) throws SQLException {
        ArrayList<ForeignKeyContract> keys = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("PRAGMA foreign_key_list(" + quote(table) + ")")) {
            while (rows.next()) {
                keys.add(new ForeignKeyContract(
                        rows.getInt("id"), rows.getInt("seq"), rows.getString("table"), rows.getString("from"),
                        rows.getString("to"), rows.getString("on_update"), rows.getString("on_delete"),
                        rows.getString("match")));
            }
        }
        return List.copyOf(keys);
    }

    private static Map<String, UniqueIndexContract> uniqueIndexes(Connection connection, String table)
            throws SQLException {
        LinkedHashMap<String, UniqueIndexContract> indexes = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("PRAGMA index_list(" + quote(table) + ")")) {
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
        return Map.copyOf(indexes);
    }

    private static List<IndexColumnContract> indexColumns(Connection connection, String index) throws SQLException {
        ArrayList<IndexColumnContract> columns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("PRAGMA index_xinfo(" + quote(index) + ")")) {
            while (rows.next()) {
                columns.add(new IndexColumnContract(
                        rows.getInt("seqno"), rows.getInt("cid"), rows.getString("name"), rows.getInt("desc"),
                        rows.getString("coll"), rows.getInt("key")));
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

    private static long countAndReadRepresentative(Connection connection, String table) throws SQLException {
        long count;
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + quote(table))) {
            rows.next();
            count = rows.getLong(1);
        }
        if (count > 0) {
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT * FROM " + quote(table) + " LIMIT 1")) {
                if (!rows.next()) {
                    throw new PersistenceException("Could not read representative row from " + table);
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

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
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
}
