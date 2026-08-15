package net.maddkraft.maddprestige.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileBackupServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("[A35] Backup service verifies copied content before reporting success")
    void verifiesCopy() throws Exception {
        Path source = temporaryDirectory.resolve("source.db");
        byte[] bytes = "disposable sqlite fixture".getBytes(StandardCharsets.UTF_8);
        Files.write(source, bytes);
        VerifiedBackup backup = new FileBackupService(source, temporaryDirectory.resolve("backups"), Clock.systemUTC())
                .createVerifiedBackup("test");
        assertTrue(backup.verified());
        assertArrayEquals(bytes, Files.readAllBytes(backup.location().orElseThrow()));
    }
}
