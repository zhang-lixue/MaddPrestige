package net.maddkraft.maddprestige.core.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.config.progression.ProgressionConfigurationCompiler;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LegacyIdentifierCompatibilityTest {
    private static final Set<String> SUPPLEMENTARY_CODES = Set.of(
            "phase4.scaling.ambiguous_boundary",
            "phase4.scaling.gap",
            "phase4.scaling.invalid_bounds",
            "phase4.scaling.invalid_override",
            "phase4.scaling.invalid_range",
            "phase4.scaling.overlap",
            "stage.feature.unsupported_phase2");

    @Test
    @DisplayName("The 80 audited diagnostics and six materialized scaling variants use exact aliases")
    void diagnosticAliasesAreExactAndNeverGeneric() {
        assertEquals(80, LegacyDiagnosticCodes.aliases().stream()
                .filter(alias -> !SUPPLEMENTARY_CODES.contains(alias.legacyCode())).count());
        assertEquals(87, LegacyDiagnosticCodes.aliases().size());
        LegacyDiagnosticCodes.aliases().forEach(alias -> {
            assertEquals(alias.canonicalCode(), LegacyDiagnosticCodes.canonicalize(alias.legacyCode()));
            assertEquals(alias.legacyCode(), LegacyDiagnosticCodes.legacyAlias(alias.canonicalCode()).orElseThrow());
            assertFalse(alias.canonicalCode().startsWith("phase"));
        });
        assertEquals("phase4.scaling.future_condition",
                LegacyDiagnosticCodes.canonicalize("phase4.scaling.future_condition"));
        assertEquals("phase999.arbitrary", LegacyDiagnosticCodes.canonicalize("phase999.arbitrary"));
    }

    @Test
    @DisplayName("The historical event provider alias resolves to one canonical registry entry")
    void providerAliasResolvesWithoutDuplicateRegistration() {
        ProviderRegistry registry = new ProviderRegistry();
        Provider provider = provider(LegacyProviderIdentifiers.EVENT_PROGRESS);
        registry.register("maddprestige", provider);

        assertEquals(1, registry.snapshots().size());
        assertEquals(LegacyProviderIdentifiers.EVENT_PROGRESS,
                registry.snapshots().iterator().next().descriptor().id());
        assertEquals(LegacyProviderIdentifiers.EVENT_PROGRESS,
                registry.find(new ProviderId("phase5_events")).orElseThrow().descriptor().id());
        assertEquals(provider, registry.provider(new ProviderId("phase5_events")).orElseThrow());
        assertEquals(ProviderHealthState.AVAILABLE,
                registry.refreshHealth(new ProviderId("phase5_events")).orElseThrow().state());
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("maddprestige", provider(new ProviderId("phase5_events"))));
    }

    @Test
    @DisplayName("New and historical provider IDs compile to the same canonical in-memory binding")
    void configurationAliasesPreserveSourceDocumentsAndHashes() {
        var canonical = compile("event_progress");
        var historical = compile("phase5_events");

        assertFalse(canonical.validation().hasErrors(), canonical.validation().toString());
        assertFalse(historical.validation().hasErrors(), historical.validation().toString());
        assertEquals(LegacyProviderIdentifiers.EVENT_PROGRESS,
                canonical.configuration().requirements().values().iterator().next().providerId());
        assertEquals(LegacyProviderIdentifiers.EVENT_PROGRESS,
                historical.configuration().requirements().values().iterator().next().providerId());
        assertTrue(historical.configuration().requirements().values().stream()
                .noneMatch(requirement -> requirement.providerId().value().startsWith("phase")));
    }

    @Test
    @DisplayName("Provider metadata uses a durable contract version and artifact-derived implementation version")
    void providerMetadataVersionContractIsPurposeBased() {
        assertEquals("2-stable", ProviderMetadataVersions.STABLE_API);
        assertEquals("development", ProviderMetadataVersions.implementationVersion(getClass()));
        assertFalse(ProviderMetadataVersions.STABLE_API.contains("phase"));
    }

    private static net.maddkraft.maddprestige.core.config.progression.ProgressionConfigurationCompilation compile(
            String providerId) {
        String requirements = """
                schema-version: 3
                requirements:
                  event_count:
                    provider: %s
                    metric: events
                    value-type: count
                    operator: greater-or-equal
                    target: 1
                    scope: absolute
                    completion: live
                trees:
                  eligibility:
                    mode: all
                    children:
                      - requirement: event_count
                costs: {}
                """.formatted(providerId);
        String rewards = """
                schema-version: 3
                rewards: {}
                """;
        Map<String, String> documents = Map.of(
                "requirements.yml", requirements,
                "rewards.yml", rewards);
        CompiledConfiguration compiled = new CompiledConfiguration(
                RevisionHasher.hashDocuments(documents), documents);
        var result = new ProgressionConfigurationCompiler().compile(compiled, Map.of());
        assertEquals(documents, compiled.documents());
        assertEquals(RevisionHasher.hashDocuments(documents), compiled.contentHash());
        return result;
    }

    private static Provider provider(ProviderId id) {
        return new Provider() {
            @Override
            public ProviderDescriptor descriptor() {
                return new ProviderDescriptor(id, "maddprestige", ProviderMetadataVersions.STABLE_API,
                        "test", List.of(), List.of());
            }

            @Override
            public ProviderHealth health() {
                return new ProviderHealth(ProviderHealthState.AVAILABLE,
                        "provider.available", "Available", Instant.EPOCH);
            }
        };
    }
}
