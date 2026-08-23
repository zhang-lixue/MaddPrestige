package net.maddkraft.maddprestige.persistence.sqlite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.maddkraft.maddprestige.core.config.ContentHash;
import net.maddkraft.maddprestige.persistence.PersistenceException;

public record SqliteBackupManifest(
        int formatVersion,
        String backupId,
        Instant createdAt,
        String reason,
        String source,
        long sourceSchemaVersion,
        Optional<String> activeConfigurationRevision,
        String databaseArtifact,
        ContentHash sha256,
        String validationResult,
        String restoreRehearsalResult,
        String journalMode) {
    private static final int CURRENT_FORMAT = 1;
    private static final Set<String> KEYS = Set.of(
            "formatVersion", "backupId", "createdAt", "reasonBase64", "sourceBase64", "sourceSchemaVersion",
            "activeConfigurationRevisionBase64", "databaseArtifact", "sha256", "validationResult",
            "restoreRehearsalResult", "journalMode");

    public SqliteBackupManifest {
        if (formatVersion != CURRENT_FORMAT) {
            throw new IllegalArgumentException("Unsupported SQLite backup manifest format " + formatVersion);
        }
        backupId = requireUuid(backupId);
        createdAt = Objects.requireNonNull(createdAt, "created at");
        reason = requireText(reason, "reason");
        source = requireText(source, "source");
        if (sourceSchemaVersion < 0) {
            throw new IllegalArgumentException("Source schema version cannot be negative");
        }
        activeConfigurationRevision = Objects.requireNonNull(
                activeConfigurationRevision, "active configuration revision");
        databaseArtifact = requireSimple(databaseArtifact, "database artifact");
        sha256 = Objects.requireNonNull(sha256, "SHA-256");
        validationResult = requireSimple(validationResult, "validation result");
        restoreRehearsalResult = requireSimple(restoreRehearsalResult, "restore rehearsal result");
        journalMode = requireSimple(journalMode, "journal mode");
    }

    public static SqliteBackupManifest accepted(
            String backupId,
            Instant createdAt,
            String reason,
            String source,
            SqliteValidationResult validation,
            String databaseArtifact,
            ContentHash sha256) {
        return new SqliteBackupManifest(
                CURRENT_FORMAT, backupId, createdAt, reason, source, validation.schemaVersion(),
                validation.activeConfigurationRevision(), databaseArtifact, sha256, "PASS", "PASS",
                validation.journalMode());
    }

    public void writeNew(Path manifest) throws IOException {
        Files.writeString(manifest, serialize(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        try (var channel = java.nio.channels.FileChannel.open(manifest, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    public static SqliteBackupManifest read(Path manifest) {
        try {
            List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            for (String line : lines) {
                int separator = line.indexOf('=');
                if (separator < 1 || values.put(line.substring(0, separator), line.substring(separator + 1)) != null) {
                    throw new PersistenceException("Malformed or duplicate backup manifest entry");
                }
            }
            if (!values.keySet().equals(KEYS)) {
                throw new PersistenceException("Backup manifest has missing or unknown fields");
            }
            return new SqliteBackupManifest(
                    Integer.parseInt(values.get("formatVersion")), values.get("backupId"),
                    Instant.parse(values.get("createdAt")), decode(values.get("reasonBase64")),
                    decode(values.get("sourceBase64")), Long.parseLong(values.get("sourceSchemaVersion")),
                    optionalDecode(values.get("activeConfigurationRevisionBase64")),
                    values.get("databaseArtifact"), new ContentHash(values.get("sha256")),
                    values.get("validationResult"), values.get("restoreRehearsalResult"),
                    values.get("journalMode"));
        } catch (IOException | IllegalArgumentException exception) {
            throw new PersistenceException("Could not parse SQLite backup manifest " + manifest, exception);
        }
    }

    private String serialize() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("formatVersion", Integer.toString(formatVersion));
        values.put("backupId", backupId);
        values.put("createdAt", createdAt.toString());
        values.put("reasonBase64", encode(reason));
        values.put("sourceBase64", encode(source));
        values.put("sourceSchemaVersion", Long.toString(sourceSchemaVersion));
        values.put("activeConfigurationRevisionBase64", activeConfigurationRevision.map(
                SqliteBackupManifest::encode).orElse(""));
        values.put("databaseArtifact", databaseArtifact);
        values.put("sha256", sha256.value());
        values.put("validationResult", validationResult);
        values.put("restoreRehearsalResult", restoreRehearsalResult);
        values.put("journalMode", journalMode);
        StringBuilder text = new StringBuilder();
        values.forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));
        return text.toString();
    }

    private static String requireSimple(String value, String name) {
        value = Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > 255 || value.chars().anyMatch(character -> character < 32)) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return value;
    }

    private static String requireUuid(String value) {
        value = requireSimple(value, "backup ID");
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("Backup ID must be a canonical UUID");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        value = Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > 1024 || value.chars().anyMatch(character -> character < 32)) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return value;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static Optional<String> optionalDecode(String value) {
        return value.isEmpty() ? Optional.empty() : Optional.of(decode(value));
    }
}
