package net.maddkraft.maddprestige.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.integrations.config.IntegrationConfigurationValidationExtension;
import net.maddkraft.maddprestige.integrations.config.IntegrationConfigurationCompiler;
import net.maddkraft.maddprestige.integrations.config.ActiveIntegrationSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IntegrationAdministrationTest {
    @Test
    @DisplayName("[A42][A43] administration schema combines generic measurement and integration integration metadata")
    void exposesOneCombinedCanonicalSchema() {
        var schema = ActiveIntegrationSchema.create();

        assertTrue(schema.resolve("requirements.requirements.play.scope").isPresent());
        assertTrue(schema.resolve("integrations.vault.enabled").isPresent());
        assertEquals("false", schema.resolve("integrations.vault.enabled").orElseThrow()
                .defaultValue().orElseThrow());
    }

    @Test
    @DisplayName("[A40] Strict integration integration compiler participates in administration apply validation")
    void integrationExtensionBlocksInvalidConfiguration() {
        String yaml = """
                schema-version: 5
                vault: {enabled: false}
                mcmmo: {enabled: false}
                placeholderapi: {output: {enabled: false}, inputs: {}}
                economyshopgui: {compatibility-enabled: false, progression-credit: {enabled: false}}
                quickshop: {compatibility-enabled: false, progression-credit: {enabled: true}}
                """;
        Map<String, String> documents = Map.of("integrations.yml", yaml);
        CompiledConfiguration configuration = new CompiledConfiguration(
                RevisionHasher.hashDocuments(documents), documents);

        var report = new IntegrationConfigurationValidationExtension(new IntegrationConfigurationCompiler())
                .validate(configuration);

        assertTrue(report.hasErrors());
        assertTrue(report.findings().stream().anyMatch(finding -> finding.path()
                .equals("integrations.quickshop.progression-credit.enabled")));
    }
}
