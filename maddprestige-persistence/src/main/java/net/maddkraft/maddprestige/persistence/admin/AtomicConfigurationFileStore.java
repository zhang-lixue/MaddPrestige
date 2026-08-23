package net.maddkraft.maddprestige.persistence.admin;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationSnapshotStore;
import net.maddkraft.maddprestige.core.admin.config.PreparedConfigurationSnapshot;
import net.maddkraft.maddprestige.core.config.BackupMetadata;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.ContentHash;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.persistence.PersistenceException;

public final class AtomicConfigurationFileStore implements ConfigurationSnapshotStore {
    private static final String POINTER = "active-revision";
    private static final String MANIFEST = ".maddprestige-revision";
    private final Path root;
    private final Path revisions;
    private final Clock clock;

    public AtomicConfigurationFileStore(Path configurationRoot, Clock clock) {
        this.root = Objects.requireNonNull(configurationRoot, "configuration root").toAbsolutePath().normalize();
        this.revisions = inside(root.resolve("revisions"));
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<ConfigRevisionId> currentRevision() {
        try {
            return readPointer().map(ConfigRevisionId::new);
        } catch (IOException exception) {
            throw new PersistenceException("Could not read the active configuration pointer", exception);
        }
    }

    /** Loads the exact checksum-verified active revision for production restart composition. */
    public Optional<Map<String, String>> activeDocuments() {
        try {
            Optional<String> active = readPointer();
            if (active.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(readRevision(active.orElseThrow()).documents());
        } catch (IOException exception) {
            throw new PersistenceException("Could not load the active configuration revision", exception);
        }
    }

    @Override
    public PreparedConfigurationSnapshot prepare(
            ConfigRevisionId revisionId,
            CompiledConfiguration configuration) {
        Objects.requireNonNull(revisionId, "revision ID");
        Objects.requireNonNull(configuration, "configuration");
        try {
            Files.createDirectories(revisions);
            Optional<String> previous = readPointer();
            BackupMetadata backup = backup(previous);
            Path target = inside(revisions.resolve(revisionId.value()));
            if (Files.exists(target)) {
                throw new PersistenceException("Configuration revision directory already exists: "
                        + revisionId.value());
            }
            Path temporary = inside(revisions.resolve(".tmp-" + UUID.randomUUID()));
            Files.createDirectory(temporary);
            try {
                writeDocuments(temporary, configuration);
                writeForced(temporary.resolve(MANIFEST), manifest(revisionId, configuration));
                atomicMove(temporary, target, false);
            } catch (RuntimeException | IOException exception) {
                deleteTree(temporary);
                throw exception;
            }
            return new Prepared(target, previous, backup, revisionId, configuration);
        } catch (IOException exception) {
            throw new PersistenceException("Could not prepare atomic configuration revision: "
                    + exception.getClass().getSimpleName() + ": " + safeMessage(exception), exception);
        }
    }

    private void writeDocuments(Path temporary, CompiledConfiguration configuration) throws IOException {
        for (Map.Entry<String, String> document : configuration.documents().entrySet()) {
            validateDocumentName(document.getKey());
            Path path = inside(temporary.resolve(document.getKey()));
            if (!path.getParent().equals(temporary)) {
                throw new PersistenceException("Nested configuration document paths are not enabled in Phase 6");
            }
            writeForced(path, document.getValue());
        }
    }

    private BackupMetadata backup(Optional<String> previous) throws IOException {
        if (previous.isEmpty()) {
            return new BackupMetadata("initial-empty", RevisionHasher.hashText(""), Instant.now(clock), true);
        }
        RevisionContents contents = readRevision(previous.orElseThrow());
        return new BackupMetadata("revision-" + previous.orElseThrow(), contents.hash(), Instant.now(clock), true);
    }

    private RevisionContents readRevision(String revision) throws IOException {
        Path prior = inside(revisions.resolve(revision));
        Path manifest = inside(prior.resolve(MANIFEST));
        if (!Files.isRegularFile(manifest)) {
            throw new PersistenceException("Active configuration manifest is missing");
        }
        String manifestText = Files.readString(manifest, StandardCharsets.UTF_8);
        String hash = manifestText.lines().filter(line -> line.startsWith("content-hash="))
                .map(line -> line.substring("content-hash=".length())).findFirst()
                .orElseThrow(() -> new PersistenceException("Active configuration manifest has no content hash"));
        LinkedHashMap<String, String> documents = new LinkedHashMap<>();
        for (String line : manifestText.lines().filter(value -> value.startsWith("document=")).toList()) {
            int separator = line.lastIndexOf(':');
            if (separator <= "document=".length()) {
                throw new PersistenceException("Active configuration manifest has a malformed document entry");
            }
            String name = line.substring("document=".length(), separator);
            String expected = line.substring(separator + 1);
            validateDocumentName(name);
            Path document = inside(prior.resolve(name));
            if (!document.getParent().equals(prior) || !Files.isRegularFile(document)) {
                throw new PersistenceException("Active configuration document is missing: " + name);
            }
            String content = Files.readString(document, StandardCharsets.UTF_8);
            if (!RevisionHasher.hashText(content).value().equals(expected)) {
                throw new PersistenceException("Active configuration document checksum failed: " + name);
            }
            documents.put(name, content);
        }
        if (documents.isEmpty() || !RevisionHasher.hashDocuments(documents).value().equals(hash)) {
            throw new PersistenceException("Active configuration revision checksum failed");
        }
        java.util.Set<String> expectedNames = new java.util.LinkedHashSet<>(documents.keySet());
        expectedNames.add(MANIFEST);
        try (var entries = Files.list(prior)) {
            java.util.Set<String> actualNames = entries.map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
            if (!actualNames.equals(expectedNames)) {
                throw new PersistenceException("Active configuration revision inventory changed");
            }
        }
        return new RevisionContents(new ContentHash(hash), Map.copyOf(documents));
    }

    private Optional<String> readPointer() throws IOException {
        Path pointer = inside(root.resolve(POINTER));
        if (!Files.exists(pointer)) {
            return Optional.empty();
        }
        String value = Files.readString(pointer, StandardCharsets.UTF_8).strip();
        new ConfigRevisionId(value);
        return Optional.of(value);
    }

    private void replacePointer(String revision) throws IOException {
        Path pointer = inside(root.resolve(POINTER));
        Path temporary = inside(root.resolve(".active-revision-" + UUID.randomUUID() + ".tmp"));
        writeForced(temporary, revision + System.lineSeparator());
        try {
            atomicMove(temporary, pointer, true);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String manifest(
            ConfigRevisionId revisionId,
            CompiledConfiguration configuration) {
        StringBuilder result = new StringBuilder("revision=").append(revisionId.value()).append('\n')
                .append("content-hash=").append(configuration.contentHash().value()).append('\n');
        configuration.documents().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(document ->
                result.append("document=").append(document.getKey()).append(':')
                        .append(RevisionHasher.hashText(document.getValue()).value()).append('\n'));
        return result.toString();
    }

    private static void writeForced(Path path, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void atomicMove(Path source, Path target, boolean replace) throws IOException {
        for (int attempt = 1; attempt <= 8; attempt++) {
            try {
                if (replace) {
                    Files.move(source, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(source, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                }
                return;
            } catch (AtomicMoveNotSupportedException exception) {
                throw new PersistenceException(
                        "Filesystem does not support required atomic configuration replacement", exception);
            } catch (AccessDeniedException exception) {
                if (attempt == 8) {
                    throw exception;
                }
                try {
                    Thread.sleep(25L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    InterruptedIOException failure = new InterruptedIOException(
                            "Interrupted while retrying atomic configuration replacement");
                    failure.initCause(exception);
                    throw failure;
                }
            }
        }
    }

    private static void validateDocumentName(String name) {
        if (!name.matches("[a-z0-9][a-z0-9_-]*\\.yml") || name.contains("..")) {
            throw new PersistenceException("Unsafe configuration document name: " + name);
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private Path inside(Path candidate) {
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new PersistenceException("Configuration path escaped the MaddPrestige-owned root");
        }
        return normalized;
    }

    private static String safeMessage(IOException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return "no operating-system detail";
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private record RevisionContents(ContentHash hash, Map<String, String> documents) {
    }

    private final class Prepared implements PreparedConfigurationSnapshot {
        private final Path target;
        private final Optional<String> previous;
        private final BackupMetadata backup;
        private final ConfigRevisionId revision;
        private final CompiledConfiguration configuration;
        private boolean activated;

        private Prepared(
                Path target,
                Optional<String> previous,
                BackupMetadata backup,
                ConfigRevisionId revision,
                CompiledConfiguration configuration) {
            this.target = target;
            this.previous = previous;
            this.backup = backup;
            this.revision = revision;
            this.configuration = configuration;
        }

        @Override
        public BackupMetadata backup() {
            return backup;
        }

        @Override
        public void activate() {
            if (activated) {
                throw new IllegalStateException("Prepared configuration snapshot is already active");
            }
            if (!Files.isDirectory(target)) {
                throw new PersistenceException("Prepared configuration snapshot is missing");
            }
            try {
                verifyPreparedSnapshot();
                replacePointer(revision.value());
                activated = true;
            } catch (IOException exception) {
                throw new PersistenceException("Could not atomically activate configuration snapshot", exception);
            }
        }

        private void verifyPreparedSnapshot() throws IOException {
            Path manifestPath = inside(target.resolve(MANIFEST));
            if (!Files.isRegularFile(manifestPath)) {
                throw new PersistenceException("Prepared configuration manifest is missing");
            }
            String expectedManifest = manifest(revision, configuration);
            String actualManifest = Files.readString(manifestPath, StandardCharsets.UTF_8);
            if (!actualManifest.equals(expectedManifest)) {
                throw new PersistenceException("Prepared configuration manifest changed after preparation");
            }
            java.util.Set<String> expectedNames = new java.util.LinkedHashSet<>(configuration.documents().keySet());
            expectedNames.add(MANIFEST);
            java.util.Set<String> actualNames = new java.util.LinkedHashSet<>();
            try (var entries = Files.list(target)) {
                for (Path entry : entries.toList()) {
                    if (!Files.isRegularFile(entry) || !entry.getParent().equals(target)) {
                        throw new PersistenceException("Prepared configuration contains a non-file entry");
                    }
                    actualNames.add(entry.getFileName().toString());
                }
            }
            if (!actualNames.equals(expectedNames)) {
                throw new PersistenceException("Prepared configuration file inventory changed after preparation");
            }
            for (Map.Entry<String, String> document : configuration.documents().entrySet()) {
                Path path = inside(target.resolve(document.getKey()));
                if (!Files.isRegularFile(path)
                        || !Files.readString(path, StandardCharsets.UTF_8).equals(document.getValue())) {
                    throw new PersistenceException("Prepared configuration document changed: " + document.getKey());
                }
            }
            if (!RevisionHasher.hashDocuments(configuration.documents()).equals(configuration.contentHash())) {
                throw new PersistenceException("Prepared configuration canonical hash is inconsistent");
            }
        }

        @Override
        public void restorePrevious() {
            if (!activated) {
                return;
            }
            try {
                if (previous.isPresent()) {
                    replacePointer(previous.orElseThrow());
                } else {
                    Files.deleteIfExists(inside(root.resolve(POINTER)));
                }
                activated = false;
            } catch (IOException exception) {
                throw new PersistenceException("Could not restore previous configuration pointer", exception);
            }
        }

        @Override
        public void close() {
            // Revision snapshots are immutable audit/recovery evidence and are never pruned here.
        }
    }
}
