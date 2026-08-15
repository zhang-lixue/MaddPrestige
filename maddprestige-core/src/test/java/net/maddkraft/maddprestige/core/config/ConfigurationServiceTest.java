package net.maddkraft.maddprestige.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfigurationServiceTest {
    @Test
    @DisplayName("[A39][A40][Phase1-hard-5/6] Draft revisions are immutable, hashed, and invalid candidates stay inactive")
    void guardsActiveReference() {
        Actor actor = new Actor("console", Optional.empty(), "Console");
        Map<String, String> documents = Map.of("config.yml", "enabled: false\n", "progression.yml", "stages: []\n");
        ConfigDraft draft = new ConfigDraft(UUID.randomUUID(), Optional.empty(), documents, actor, Instant.now());
        CompiledConfiguration compiled = new ConfigCompiler().compile(draft);
        assertEquals(RevisionHasher.hashDocuments(Map.of("progression.yml", "stages: []\n", "config.yml", "enabled: false\n")),
                compiled.contentHash());
        assertThrows(UnsupportedOperationException.class, () -> compiled.documents().put("x.yml", "bad"));

        ConfigurationService service = new ConfigurationService();
        ValidationFinding error = new ValidationFinding("config.invalid", ValidationSeverity.ERROR, "config.yml",
                "Invalid configuration", "Activation would be unsafe", "Correct the draft");
        BackupMetadata backup = verifiedBackup();
        assertThrows(IllegalStateException.class, () -> service.apply(new ConfigRevisionId("rev_invalid"), compiled,
                new ValidationReport(List.of(error)), Set.of(), backup));
        assertFalse(service.active().isPresent());

        service.apply(new ConfigRevisionId("rev_valid"), compiled, ValidationReport.VALID, Set.of(), backup);
        assertEquals(compiled.contentHash(), service.active().orElseThrow().compiled().contentHash());
    }

    private static BackupMetadata verifiedBackup() {
        return new BackupMetadata("backup-1", RevisionHasher.hashText("backup"), Instant.now(), true);
    }
}
