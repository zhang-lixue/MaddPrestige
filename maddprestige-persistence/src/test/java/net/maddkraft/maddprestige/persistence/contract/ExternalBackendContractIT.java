package net.maddkraft.maddprestige.persistence.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "mp.backend.contract", matches = "true")
class ExternalBackendContractIT {
    private String url;
    private String user;
    private String password;

    @BeforeEach
    void createContractTable() throws Exception {
        url = required("mp.backend.url");
        user = required("mp.backend.user");
        password = System.getProperty("mp.backend.password", "");
        String driver = required("mp.backend.driver");
        Class.forName(driver);
        try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS mp_external_contract_probe");
            statement.execute("""
                    CREATE TABLE mp_external_contract_probe (
                        probe_id VARCHAR(64) PRIMARY KEY,
                        amount_text VARCHAR(512) NOT NULL,
                        operation_type VARCHAR(64) NOT NULL,
                        target_uuid VARCHAR(36) NOT NULL,
                        idempotency_key VARCHAR(128) NOT NULL,
                        UNIQUE (operation_type, target_uuid, idempotency_key)
                    )
                    """);
        }
    }

    @AfterEach
    void removeContractTable() throws Exception {
        try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS mp_external_contract_probe");
        }
    }

    @Test
    @DisplayName("[A64-foundation] MySQL/MariaDB harness proves exact-text decimal and idempotency uniqueness primitives")
    void exercisesSharedFoundationSemantics() throws Exception {
        String target = UUID.randomUUID().toString();
        String exact = "9007199254740993.123456789012345678901";
        insert("one", exact, target, "same-key");
        try (var connection = DriverManager.getConnection(url, user, password);
                var statement = connection.prepareStatement(
                        "SELECT amount_text FROM mp_external_contract_probe WHERE probe_id = ?")) {
            statement.setString(1, "one");
            try (var row = statement.executeQuery()) {
                row.next();
                assertEquals(exact, row.getString(1));
            }
        }
        assertThrows(SQLException.class, () -> insert("two", exact, target, "same-key"));
    }

    private void insert(String id, String amount, String target, String key) throws SQLException {
        String sql = "INSERT INTO mp_external_contract_probe "
                + "(probe_id, amount_text, operation_type, target_uuid, idempotency_key) VALUES (?, ?, ?, ?, ?)";
        try (var connection = DriverManager.getConnection(url, user, password);
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, amount);
            statement.setString(3, "rankup");
            statement.setString(4, target);
            statement.setString(5, key);
            statement.executeUpdate();
        }
    }

    private static String required(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required backend contract property: " + property);
        }
        return value;
    }
}
