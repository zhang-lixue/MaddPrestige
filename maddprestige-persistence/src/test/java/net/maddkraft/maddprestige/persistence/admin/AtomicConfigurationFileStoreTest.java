package net.maddkraft.maddprestige.persistence.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicConfigurationFileStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("[A38][A41] Immutable snapshots preserve exact Unicode/CRLF bytes and restore prior pointer")
    void preservesExactDocumentsAndRestoresPointer() throws Exception {
        Path root = temporaryDirectory.resolve("configuration");
        AtomicConfigurationFileStore store = new AtomicConfigurationFileStore(root, Clock.systemUTC());
        ConfigRevisionId firstId = new ConfigRevisionId("revision_one");
        String firstYaml = "# Owner note 😀\r\nschema-version: 3\r\nactive: false\r\n";
        var first = store.prepare(firstId, compiled(Map.of("progression.yml", firstYaml)));
        first.activate();

        assertEquals(Optional.of(firstId), store.currentRevision());
        assertEquals(Optional.of(Map.of("progression.yml", firstYaml)), store.activeDocuments());
        assertEquals(firstYaml, Files.readString(root.resolve("revisions").resolve(firstId.value())
                .resolve("progression.yml"), StandardCharsets.UTF_8));

        ConfigRevisionId secondId = new ConfigRevisionId("revision_two");
        var second = store.prepare(secondId, compiled(Map.of("progression.yml",
                firstYaml.replace("false", "true"))));
        assertTrue(second.backup().verified());
        second.activate();
        assertEquals(Optional.of(secondId), store.currentRevision());
        second.restorePrevious();
        assertEquals(Optional.of(firstId), store.currentRevision());
        assertEquals(Optional.of(Map.of("progression.yml", firstYaml)), store.activeDocuments());
    }

    @Test
    @DisplayName("Unsafe names and tampered prior snapshots fail before active replacement")
    void rejectsPathEscapeAndCorruptBackup() throws Exception {
        Path root = temporaryDirectory.resolve("configuration");
        AtomicConfigurationFileStore store = new AtomicConfigurationFileStore(root, Clock.systemUTC());
        ConfigRevisionId firstId = new ConfigRevisionId("revision_one");
        var first = store.prepare(firstId, compiled(Map.of("progression.yml", "active: false\n")));
        first.activate();

        assertThrows(PersistenceException.class, () -> store.prepare(new ConfigRevisionId("unsafe_revision"),
                compiled(Map.of("../outside.yml", "unsafe: true\n"))));
        Files.writeString(root.resolve("revisions").resolve(firstId.value()).resolve("progression.yml"),
                "tampered: true\n", StandardCharsets.UTF_8);
        assertThrows(PersistenceException.class, store::activeDocuments);
        assertThrows(PersistenceException.class, () -> store.prepare(new ConfigRevisionId("revision_three"),
                compiled(Map.of("progression.yml", "active: true\n"))));
        assertEquals(Optional.of(firstId), store.currentRevision());
        assertTrue(Files.notExists(temporaryDirectory.resolve("outside.yml")));
    }

    @Test
    @DisplayName("Prepared document, manifest, missing file, and extra file tamper cannot activate")
    void verifiesExactPreparedSnapshotImmediatelyBeforePointerSwitch() throws Exception {
        Path root = temporaryDirectory.resolve("tamper-configuration");
        AtomicConfigurationFileStore store = new AtomicConfigurationFileStore(root, Clock.systemUTC());
        ConfigRevisionId activeId = new ConfigRevisionId("active_revision");
        store.prepare(activeId, compiled(Map.of("progression.yml", "active: false\n"))).activate();

        ConfigRevisionId documentId = new ConfigRevisionId("document_tamper");
        var document = store.prepare(documentId, compiled(Map.of("progression.yml", "active: true\n")));
        Files.writeString(revision(root, documentId).resolve("progression.yml"), "active: false\n",
                StandardCharsets.UTF_8);
        assertThrows(PersistenceException.class, document::activate);
        assertEquals(Optional.of(activeId), store.currentRevision());

        ConfigRevisionId manifestId = new ConfigRevisionId("manifest_tamper");
        var manifest = store.prepare(manifestId, compiled(Map.of("progression.yml", "active: true\n")));
        Files.writeString(revision(root, manifestId).resolve(".maddprestige-revision"), "tampered\n",
                StandardCharsets.UTF_8);
        assertThrows(PersistenceException.class, manifest::activate);
        assertEquals(Optional.of(activeId), store.currentRevision());

        ConfigRevisionId missingId = new ConfigRevisionId("missing_document");
        var missing = store.prepare(missingId, compiled(Map.of("progression.yml", "active: true\n")));
        Files.delete(revision(root, missingId).resolve("progression.yml"));
        assertThrows(PersistenceException.class, missing::activate);
        assertEquals(Optional.of(activeId), store.currentRevision());

        ConfigRevisionId extraId = new ConfigRevisionId("extra_document");
        var extra = store.prepare(extraId, compiled(Map.of("progression.yml", "active: true\n")));
        Files.writeString(revision(root, extraId).resolve("unexpected.yml"), "unsafe: true\n",
                StandardCharsets.UTF_8);
        assertThrows(PersistenceException.class, extra::activate);
        assertEquals(Optional.of(activeId), store.currentRevision());
    }

    @Test
    @DisplayName("[OR8B-09] Snapshot temporary paths remain bounded under a deep disposable server root")
    void supportsDeepDisposableServerRoot() {
        Path root = temporaryDirectory.resolve("deep-server-root-1234567890123456789012345678901234567890")
                .resolve("plugins").resolve("MaddPrestige").resolve("configuration");
        AtomicConfigurationFileStore store = new AtomicConfigurationFileStore(root, Clock.systemUTC());
        ConfigRevisionId revision = new ConfigRevisionId("r_12345678901234567890123456789012");

        store.prepare(revision, compiled(Map.of("progression.yml", "active: true\n"))).activate();

        assertEquals(Optional.of(revision), store.currentRevision());
    }

    private static Path revision(Path root, ConfigRevisionId id) {
        return root.resolve("revisions").resolve(id.value());
    }

    private static CompiledConfiguration compiled(Map<String, String> source) {
        LinkedHashMap<String, String> documents = new LinkedHashMap<>(source);
        return new CompiledConfiguration(RevisionHasher.hashDocuments(documents), documents);
    }
}
