package net.maddkraft.maddprestige.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.maddkraft.prestige.model.ProgressionRank;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class V1CharacterizationTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    @DisplayName("[A36][A37] V1 fixed ranks remain migration evidence and are not inferred as V2 stages")
    void recordsFixedLegacyRanks() {
        assertEquals(List.of("CURIOUS", "ODD", "MAD", "UNBOUND"),
                Arrays.stream(ProgressionRank.values()).map(Enum::name).toList());
    }

    @Test
    @DisplayName("[A35][A63] A disposable copy of the V1 SQLite fixture proves schema version 4")
    void readsEmptyLegacyDatabaseWithoutMutation(@TempDir Path temporaryDirectory) throws Exception {
        Path frozenDatabase = ROOT.resolve("baseline/v1/runtime/maddprestige.db");
        Path databaseCopy = temporaryDirectory.resolve("maddprestige.db");
        Files.copy(frozenDatabase, databaseCopy);
        String url = "jdbc:sqlite:file:" + databaseCopy.toString().replace('\\', '/') + "?mode=ro";
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            try (var row = statement.executeQuery("SELECT version FROM schema_info LIMIT 1")) {
                assertTrue(row.next());
                assertEquals(4, row.getInt(1));
            }
            try (var row = statement.executeQuery("SELECT COUNT(*) FROM player_lifetime")) {
                assertTrue(row.next());
                assertEquals(0, row.getInt(1));
            }
        }
    }

    @Test
    @DisplayName("Frozen V1 release JAR checksum remains byte-identical")
    void verifiesReleaseChecksum() throws Exception {
        String expected = "1de0b772ef11de8dd1f70dfda71e65cb307cce5d47eeba4af26205eda36a3a08";
        assertEquals(expected, sha256(ROOT.resolve("dist/MaddPrestige-1.2.0.jar")));
        assertEquals(expected, sha256(ROOT.resolve("baseline/v1/external/MaddPrestige-1.2.0-download.jar")));
    }

    @Test
    @DisplayName("[A38][A54] V1 config shape preserves dotted-key and QuickShop-default defect evidence")
    void characterizesConfigurationDefects() throws Exception {
        String config = Files.readString(ROOT.resolve("src/main/resources/config.yml"));
        assertTrue(config.contains("quickshop-earned-weight: 0.25"));
        assertTrue(config.contains("maddkraft.patron.knave: 3"));
        String fixture = Files.readString(ROOT.resolve("baseline/v1/characterization/v1-behavior.yml"));
        assertTrue(fixture.contains("observed-parser-result: \"{maddkraft=0}\""));
        assertTrue(fixture.contains("snapshot-format: semicolon-delimited-key-value-text"));
    }

    @Test
    @DisplayName("[A05] V1 LuckPerms group creation is retained only as a documented legacy defect")
    void characterizesLegacyGroupCreation() throws Exception {
        String source = Files.readString(ROOT.resolve(
                "src/main/java/gg/maddkraft/prestige/integration/LuckPermsBridge.java"));
        assertTrue(source.contains("createAndLoadGroup"));
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
