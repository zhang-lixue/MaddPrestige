package net.maddkraft.maddprestige.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.migration.Migration;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationIdentityCompatibilityTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-06T00:00:00Z"), ZoneOffset.UTC);
    private static final List<Identity> IMMUTABLE_IDENTITIES = List.of(
            identity(1, "7ca666ecaa7c2de6ef9d14f353673242cf7ddbc65f84b4e6a2c6601632c2ac15",
                    "Phase 1 configuration, operation, currency, and audit foundations"),
            identity(2, "bdab7cb0958a9672e87ee074823de0cc4b49a553a1a731d8d69f9a598499b1b1",
                    "Phase 2 authoritative player stage state"),
            identity(3, "e6eebfa9b087dc598a513341e3e5a996c3dc80073dee4b531de5c4ebf2747910",
                    "Phase 3 requirement state and batched manual progress"),
            identity(4, "9180242f9f2910b3701ee058ef14b03db67b06dcbbabe83434e9ef2be63a8b9e",
                    "Phase 4 Prestige, currency ledger, milestones, seasons, history, recovery"),
            identity(5, "0187404cbb1b8e63a9b260a5d0c61c6fa45f2c3a72aa0e9c324999a31937e5a9",
                    "Phase 4 stage-history actor UUID provenance"),
            identity(6, "e59e864a17f380d4f0343c53cce4c29c7647ab73b649134dc225c9025cb3604b",
                    "Phase 6 durable configuration history and exact revision documents"),
            identity(7, "c66357bcfcb2ea78931df09e72b27755abfb06d9f8ce013ca86efe01e41caa7b",
                    "Phase 6 recoverable referenced-stage replacement migration"),
            identity(8, "7897cfba8b063740d369dfdef561f59a927cff7882008e95d5fea20a8321c843",
                    "Phase 6 durable stage-transition fence"),
            identity(9, "5d37bbfb7f4f00064998a86bec1cdef5d6e7ee16934fd3002158d8757fb20b29",
                    "Phase 6 owned source-and-target stage participation"),
            identity(10, "d21a4487d92886409fbdb24d69eae2b8399f0016a7692b93308ecbcbaa5e82bf",
                    "Phase 6 configuration-owned unsafe-stage reservations"),
            identity(11, "b585ecb43b733fe6b5257c7b0d567ca6b6dca4d93685cca89e3151945856acf1",
                    "Phase 8C historical player Prestige-state compatibility"),
            identity(12, "e5dd53496c06525e852b6953a038f1f7d6b01d167abfa6968e0195c35c91181a",
                    "Phase 9B numeric Prestige progression authority"));
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Migration versions 1 through 12 retain exact persisted descriptors and checksums")
    void historicalMigrationIdentitiesRemainByteCompatible() {
        List<Migration> migrations = SqliteMigrations.current();

        assertEquals(IMMUTABLE_IDENTITIES.size(), migrations.size());
        for (int index = 0; index < migrations.size(); index++) {
            Migration migration = migrations.get(index);
            Identity expected = IMMUTABLE_IDENTITIES.get(index);
            assertEquals(expected.version(), migration.version());
            assertEquals(expected.checksum(), migration.checksum().value());
            assertEquals(expected.description(), migration.description());
            assertNotEquals(migration.description(), SqliteMigrations.displayDescription(migration.version()));
        }
    }

    @Test
    @DisplayName("Fresh and previously migrated databases validate against the same immutable history")
    void freshAndExistingHistoriesValidateWithoutAlternateDescriptions() {
        Path fresh = temporaryDirectory.resolve("fresh.db");
        migrate(fresh, SqliteMigrations.current());
        SqliteDatabaseValidator.validate(fresh, SqliteMigrations.current());

        Path existing = temporaryDirectory.resolve("existing.db");
        migrate(existing, SqliteMigrations.throughVersionEleven());
        migrate(existing, SqliteMigrations.current());
        SqliteDatabaseValidator.validate(existing, SqliteMigrations.current());

        assertEquals(IMMUTABLE_IDENTITIES, identities(fresh));
        assertEquals(IMMUTABLE_IDENTITIES, identities(existing));
    }

    private void migrate(Path database, List<Migration> migrations) {
        SqliteFoundation foundation = new SqliteFoundation(database);
        new MigrationRunner(foundation,
                new FileBackupService(database, temporaryDirectory.resolve("backups"), CLOCK), CLOCK)
                .migrate(migrations);
    }

    private static List<Identity> identities(Path database) {
        try (var connection = new SqliteFoundation(database).open();
                var statement = connection.prepareStatement(
                        "SELECT version, checksum, description FROM mp_schema_migrations ORDER BY version");
                var rows = statement.executeQuery()) {
            java.util.ArrayList<Identity> identities = new java.util.ArrayList<>();
            while (rows.next()) {
                identities.add(identity(rows.getLong(1), rows.getString(2), rows.getString(3)));
            }
            return List.copyOf(identities);
        } catch (java.sql.SQLException exception) {
            throw new AssertionError("Could not read migration identities", exception);
        }
    }

    private static Identity identity(long version, String checksum, String description) {
        return new Identity(version, checksum, description);
    }

    private record Identity(long version, String checksum, String description) {
    }
}
