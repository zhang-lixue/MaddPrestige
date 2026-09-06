import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** Phase 0 helper: prints schema and aggregate facts without mutating the SQLite database. */
public final class ReadOnlySqliteInventory {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected path to maddprestige.db");
        Class.forName("org.sqlite.JDBC");
        String normalized = args[0].replace('\\', '/');
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + normalized)) {
            printSingle(connection.createStatement().executeQuery("PRAGMA integrity_check"), "integrity_check");

            int foreignKeyIssues = 0;
            try (ResultSet result = connection.createStatement().executeQuery("PRAGMA foreign_key_check")) {
                while (result.next()) foreignKeyIssues++;
            }
            System.out.println("foreign_key_issues=" + foreignKeyIssues);

            List<String> tables = new ArrayList<>();
            try (ResultSet result = connection.createStatement().executeQuery(
                    "SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
                while (result.next()) {
                    tables.add(result.getString(1));
                    System.out.println("TABLE=" + result.getString(1));
                    System.out.println(result.getString(2));
                }
            }
            for (String table : tables) {
                try (ResultSet result = connection.createStatement().executeQuery("SELECT COUNT(*) FROM " + table)) {
                    result.next();
                    System.out.println("COUNT " + table + "=" + result.getLong(1));
                }
            }
            printRows(connection, "schema_info", "SELECT id, version FROM schema_info");
            printRows(connection, "rank", "SELECT progression_rank, COUNT(*) FROM player_season GROUP BY progression_rank");
            printRows(connection, "transaction_state", "SELECT state, COUNT(*) FROM prestige_transactions GROUP BY state");
            printRows(connection, "pending_tier", "SELECT tier, COUNT(*) FROM pending_patron_grants GROUP BY tier");
            printRows(connection, "season", "SELECT id, number, display_name, status, starts_at, ends_at FROM seasons ORDER BY number");
        }
    }

    private static void printSingle(ResultSet result, String label) throws Exception {
        try (result) {
            while (result.next()) System.out.println(label + "=" + result.getString(1));
        }
    }

    private static void printRows(Connection connection, String label, String sql) throws Exception {
        try (ResultSet result = connection.createStatement().executeQuery(sql)) {
            int columns = result.getMetaData().getColumnCount();
            while (result.next()) {
                StringBuilder row = new StringBuilder(label).append('=');
                for (int column = 1; column <= columns; column++) {
                    if (column > 1) row.append(',');
                    row.append(result.getString(column));
                }
                System.out.println(row);
            }
        }
    }
}
