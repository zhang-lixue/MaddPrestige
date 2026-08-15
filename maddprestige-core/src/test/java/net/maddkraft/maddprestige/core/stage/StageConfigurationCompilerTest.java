package net.maddkraft.maddprestige.core.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.rank.ProjectionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StageConfigurationCompilerTest {
    @Test
    @DisplayName("[A03][A04] Arbitrary ordered stages keep immutable identity separate from display and ordinal")
    void compilesArbitraryOrderedStages() {
        String source = ladder("First Label", "second", "third", "fourth", "fifth");
        var compilation = compile(source);
        assertFalse(compilation.validation().hasErrors(), compilation.validation().toString());
        StageConfiguration configuration = compilation.configuration().orElseThrow();
        assertEquals(5, configuration.stages().size());
        assertEquals(List.of("first", "second", "third", "fourth", "fifth"),
                configuration.order().stream().map(StageId::value).toList());
        assertEquals(ProjectionPolicy.NONE,
                configuration.stages().get(new StageId("first")).projection().policy());

        StageConfiguration renamed = compile(source.replace("First Label", "Renamed Presentation"))
                .configuration().orElseThrow();
        assertEquals(new StageId("first"), renamed.order().getFirst());
        assertEquals("Renamed Presentation", renamed.stages().get(new StageId("first")).displayName());
    }

    @Test
    @DisplayName("[A08] Duplicate, undefined, omitted, and disabled order entries fail structured validation")
    void rejectsInvalidOrderShapes() {
        String source = """
                schema-version: 2
                active: true
                baseline: first
                stages:
                  first: {enabled: true, display-name: First, projection: none}
                  omitted: {enabled: true, display-name: Omitted, projection: none}
                  disabled: {enabled: false, display-name: Disabled, projection: none}
                order: [first, first, missing, disabled]
                """;
        var report = compile(source).validation();
        assertTrue(report.hasErrors());
        assertTrue(codes(report).containsAll(List.of("stage.order.duplicate", "stage.order.undefined",
                "stage.order.omits_enabled", "stage.order.disabled")));
    }

    @Test
    @DisplayName("[A03] Duplicate YAML stage IDs and active later-phase fields never pretend to work")
    void rejectsDuplicatesAndDeferredFeatures() {
        String duplicate = """
                active: true
                stages:
                  first: {enabled: true, display-name: First, projection: none}
                  first: {enabled: true, display-name: Again, projection: none}
                order: [first]
                baseline: first
                """;
        assertTrue(compile(duplicate).validation().hasErrors());

        String unsupported = """
                active: true
                stages:
                  first:
                    enabled: true
                    display-name: First
                    projection: none
                    requirements: [future]
                order: [first]
                baseline: first
                """;
        assertTrue(codes(compile(unsupported).validation()).contains("stage.feature.unsupported_phase2"));
    }

    @Test
    @DisplayName("[A01] Fresh canonical stage configuration is safely inactive without a rank provider")
    void startsInactive() {
        var compilation = compile("""
                schema-version: 2
                active: false
                reconciliation-policy: warn-only
                stages: {}
                order: []
                """);
        assertFalse(compilation.validation().hasErrors());
        assertFalse(compilation.configuration().orElseThrow().active());
        assertEquals(StageRuntimeState.INACTIVE,
                new StageRuntimeBootstrap().inspect(
                        java.util.Optional.empty(), new net.maddkraft.maddprestige.core.provider.ProviderRegistry()).state());
    }

    @Test
    @DisplayName("[A05] Explicit group and documented shorthand projection mappings are valid")
    void acceptsSupportedGroupProjectionForms() {
        assertFalse(compile(projection("{type: group, provider: rank_provider, group: first_group}"))
                .validation().hasErrors());
        assertFalse(compile(projection("{provider: rank_provider, group: first_group}"))
                .validation().hasErrors());
        assertFalse(compile(projection("{type: none}"))
                .validation().hasErrors());
    }

    @Test
    @DisplayName("[A05] Invalid, blank, non-scalar, and contradictory projection types fail validation")
    void rejectsUnsupportedProjectionTypes() {
        for (String invalid : List.of(
                "{type: nonsense, provider: rank_provider, group: first_group}",
                "{type: '', provider: rank_provider, group: first_group}",
                "{type: [group], provider: rank_provider, group: first_group}",
                "{type: none, provider: rank_provider, group: first_group}")) {
            var validation = compile(projection(invalid)).validation();
            assertTrue(validation.hasErrors(), invalid);
        }
        assertTrue(codes(compile(projection(
                "{type: nonsense, provider: rank_provider, group: first_group}")).validation())
                .contains("stage.projection.type.invalid"));
    }

    @Test
    @DisplayName("[A03] Generic Phase 2 production source/resources contain no deployment rank terminology")
    void genericityScanIsClean() throws IOException {
        List<String> prohibited = List.of(
                "CURIOUS", "ODD", "UNBOUND", "Wanderer", "Dreamer", "Tea Guest", "Wonderlander",
                "Madcap", "Mad Hatter");
        List<Path> roots = List.of(Path.of("src/main/java"), Path.of("src/main/resources"));
        for (Path root : roots) {
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String text = Files.readString(file);
                    for (String term : prohibited) {
                        assertFalse(text.contains(term), () -> file + " contains prohibited generic term " + term);
                    }
                }
            }
        }
    }

    private static StageConfigurationCompilation compile(String source) {
        Map<String, String> documents = Map.of("progression.yml", source);
        return new StageConfigurationCompiler().compile(
                new CompiledConfiguration(RevisionHasher.hashDocuments(documents), documents));
    }

    private static List<String> codes(net.maddkraft.maddprestige.api.validation.ValidationReport report) {
        return report.findings().stream().map(finding -> finding.code()).toList();
    }

    private static String ladder(String firstDisplay, String... remaining) {
        StringBuilder source = new StringBuilder("""
                schema-version: 2
                active: true
                reconciliation-policy: warn-only
                baseline: first
                stages:
                  first:
                    enabled: true
                    display-name: %s
                    projection: none
                """.formatted(firstDisplay));
        for (String id : remaining) {
            source.append("  ").append(id).append(":\n")
                    .append("    enabled: true\n")
                    .append("    display-name: ").append(id).append("\n")
                    .append("    projection: none\n");
        }
        source.append("order: [first, ").append(String.join(", ", remaining)).append("]\n");
        return source.toString();
    }

    private static String projection(String projection) {
        return """
                schema-version: 2
                active: true
                baseline: first
                stages:
                  first:
                    enabled: true
                    display-name: First
                    projection: %s
                order: [first]
                """.formatted(projection);
    }
}
