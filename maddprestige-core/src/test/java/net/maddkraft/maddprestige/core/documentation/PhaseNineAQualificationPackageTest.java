package net.maddkraft.maddprestige.core.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.core.config.ConfigCompiler;
import net.maddkraft.maddprestige.core.config.ConfigDraft;
import net.maddkraft.maddprestige.core.legacy.LegacyStageDetector;
import net.maddkraft.maddprestige.core.rank.ProjectionPolicy;
import net.maddkraft.maddprestige.core.stage.StageConfigurationCompiler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhaseNineAQualificationPackageTest {
    private static final List<String> DOCUMENTS = List.of(
            "docs/phase9/PHASE9_QUALIFICATION_PLAN.md",
            "docs/phase9/MADDKRAFT_CLONE_ENVIRONMENT.md",
            "docs/phase9/MIGRATION_MAPPING.md",
            "docs/phase9/PROVIDER_TEST_MATRIX.md",
            "docs/phase9/PRODUCTION_READINESS_CHECKLIST.md");

    @Test
    @DisplayName("[Phase 9A] Qualification documents retain the non-live and non-production boundary")
    void qualificationDocumentsAreCompleteAndTruthful() throws IOException {
        Path root = repositoryRoot();
        String combined = "";
        for (String document : DOCUMENTS) {
            Path path = root.resolve(document);
            assertTrue(Files.isRegularFile(path), document);
            String text = read(path);
            assertFalse(text.isBlank(), document);
            combined += text;
        }
        assertTrue(combined.contains("No real clone was created"));
        assertTrue(combined.contains("NOT READY FOR PRODUCTION"));
        assertTrue(combined.contains("A63 remains `Partial`"));
        assertTrue(combined.contains("No ledger row changes in Phase 9A"));
        assertTrue(combined.contains("QuickShop-Hikari"));
        assertTrue(combined.contains("AxTrade"));
        assertTrue(combined.contains("resource reset"));
        assertTrue(combined.contains("Court"));
    }

    @Test
    @DisplayName("[A71][A72][Phase 9A] Clone profile compiles with an unmanaged default baseline and exact allowlist")
    void maddKraftCloneProfileCompilesWithoutProductionBalance() throws IOException {
        Path profile = repositoryRoot().resolve("qualification/phase9a/maddkraft-clone/progression.yml");
        String source = read(profile);
        ConfigDraft draft = new ConfigDraft(UUID.randomUUID(), Optional.empty(), Map.of("progression.yml", source),
                new Actor("SYSTEM", Optional.empty(), "Phase 9A fixture"), Instant.now());
        var compilation = new StageConfigurationCompiler().compile(new ConfigCompiler().compile(draft));

        assertFalse(compilation.validation().hasErrors(), compilation.validation().findings().toString());
        var stages = compilation.configuration().orElseThrow();
        assertEquals(List.of("wanderer", "curious", "dreamer", "tea_guest", "wonderlander", "madcap"),
                stages.order().stream().map(id -> id.value()).toList());
        assertEquals(Set.of("curious", "dreamer", "tea_guest", "wonderlander", "madcap"),
                stages.managedGroups(new ProviderId("luckperms")));
        assertEquals(ProjectionPolicy.NONE,
                stages.stages().get(stages.baselineStage().orElseThrow()).projection().policy());
        for (String forbidden : List.of("25000000", "40000000", "500000", "0.18", "rabbit", "decree",
                "boss", "tea_leaves", "milestone")) {
            assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains(forbidden), forbidden);
        }
    }

    @Test
    @DisplayName("[A35][A36][Phase 9A] Frozen V1 config is detected and unresolved manifest remains blocked")
    void exactLegacyFixtureRequiresExplicitOwnerMappings() throws IOException {
        Path root = repositoryRoot();
        var detection = new LegacyStageDetector().detect(
                read(root.resolve("baseline/v1/runtime/config.yml")), List.of());
        assertEquals(Set.of("CURIOUS", "ODD", "MAD", "UNBOUND"), detection.legacyStageValues());
        Set<String> codes = detection.findings().findings().stream()
                .map(finding -> finding.code()).collect(java.util.stream.Collectors.toSet());
        assertTrue(codes.contains("legacy.stage.fixed_rank_config"));
        assertTrue(codes.contains("legacy.stage.external_group_config"));
        assertTrue(codes.contains("legacy.stage.parallel_identity"));
        assertTrue(codes.contains("legacy.config.obsolete_competition"));

        String mapping = read(root.resolve(
                "qualification/phase9a/maddkraft-clone/legacy-migration-mapping.yml"));
        assertTrue(mapping.contains("execution: blocked"));
        assertEquals(8, mapping.lines().filter(line -> line.contains("OWNER_DECISION_REQUIRED")).count());
        assertTrue(mapping.contains("create-luckperms-group"));
        assertTrue(mapping.contains("activate-production-balance"));
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("docs"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("docs"))) {
            return parent;
        }
        throw new IllegalStateException("Repository root is unavailable from " + current);
    }
}
