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

class QualificationPackageIntegrityTest {
    private static final List<String> DOCUMENTS = List.of(
            "README.md",
            "docs/architecture.md",
            "docs/acceptance.md",
            "docs/compatibility-baseline.md",
            "docs/operations/upgrading.md");

    @Test
    @DisplayName("[Public docs] Durable release documents retain deployment and compatibility boundaries")
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
        assertTrue(combined.contains("stable release"));
        assertTrue(combined.contains("Production data, permissions, balance, and services were not changed"));
        assertTrue(combined.contains("Populated pre-numeric V2 schema upgrade and activation: passed"));
        assertTrue(combined.contains("V1 player/configuration data is not automatically imported"));
        assertTrue(combined.contains("MySQL"));
        assertTrue(combined.contains("MariaDB"));
        assertTrue(combined.contains("resource-world"));
        assertTrue(combined.contains("Court"));
    }

    @Test
    @DisplayName("[A71][A72] Archived compatibility profile compiles without a production balance")
    void archivedCompatibilityProfileCompilesWithoutProductionBalance() throws IOException {
        Path profile = repositoryRoot().resolve(
                "examples/compatibility/member-adventurer-veteran/progression.yml");
        String source = read(profile);
        ConfigDraft draft = new ConfigDraft(UUID.randomUUID(), Optional.empty(), Map.of("progression.yml", source),
                new Actor("SYSTEM", Optional.empty(), "numeric migration qualification fixture"), Instant.now());
        var compilation = new StageConfigurationCompiler().compile(new ConfigCompiler().compile(draft));

        assertFalse(compilation.validation().hasErrors(), compilation.validation().findings().toString());
        var stages = compilation.configuration().orElseThrow();
        assertEquals(List.of("member", "adventurer", "veteran"),
                stages.order().stream().map(id -> id.value()).toList());
        assertEquals(Set.of("Member", "Adventurer", "Veteran"),
                stages.managedGroups(new ProviderId("luckperms")));
        assertEquals(ProjectionPolicy.GROUP,
                stages.stages().get(stages.baselineStage().orElseThrow()).projection().policy());
        for (String forbidden : List.of("25000000", "40000000", "500000", "0.18", "rabbit", "decree",
                "boss", "tea_leaves", "milestone")) {
            assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains(forbidden), forbidden);
        }
    }

    @Test
    @DisplayName("[A35][A36] V1 remains frozen evidence and is never inferred into numeric Prestige")
    void exactLegacyFixtureIsHistoricalSupersededAndNonExecutable() throws IOException {
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

        assertFalse(Files.exists(root.resolve(
                "qualification/migration-qualification/maddkraft-clone/legacy-migration-mapping.yml")));
        assertFalse(Files.exists(root.resolve(
                "qualification/migration-qualification/maddkraft-clone/progression.yml")));
        String compatibility = read(root.resolve("docs/compatibility-baseline.md"));
        String upgrading = read(root.resolve("docs/operations/upgrading.md"));
        assertTrue(compatibility.contains("not automatically imported"));
        assertTrue(compatibility.contains("starts at Prestige 0"));
        assertTrue(upgrading.contains("fresh V2 player state starts at Prestige 0"));
        assertTrue(compatibility.contains("MaddPrestige never creates LuckPerms groups"));
    }

    @Test
    @DisplayName("[Release acceptance] Migration, progression and ownership guarantees remain explicit")
    void numericAcceptanceRowsRemainIndependentAndStrong() throws IOException {
        Path root = repositoryRoot();
        String acceptance = read(root.resolve("docs/acceptance.md"));
        String architecture = read(root.resolve("docs/architecture.md"));
        String compatibility = read(root.resolve("docs/compatibility-baseline.md"));

        assertTrue(compatibility.contains("not automatically imported")
                && compatibility.contains("starts at Prestige 0"));
        assertTrue(acceptance.contains("Populated pre-numeric V2 schema upgrade and activation: passed"));
        assertTrue(acceptance.contains("numeric P0 -> P1 and P1 -> P2 progression")
                && acceptance.contains("independent cost/reward behavior"));
        assertTrue(architecture.contains("advances exactly from `P` to `P + 1`"));
        assertTrue(compatibility.contains("MaddPrestige never creates LuckPerms groups"));
        assertTrue(compatibility.contains("staff, supporter, event, and unrelated memberships are preserved"));
    }

    @Test
    @DisplayName("Production discovery and Doctor composition expose no rank/stage path")
    void productionCompositionIsRewardOnlyAndNumericDoctorOnly() throws IOException {
        Path root = repositoryRoot();
        String plugin = read(root.resolve("maddprestige-platform-paper/src/main/java/net/maddkraft/"
                + "maddprestige/platform/paper/bootstrap/MaddPrestigeV2Plugin.java"));
        assertTrue(plugin.contains("LuckPermsRewardProvider"));
        assertFalse(plugin.contains("LuckPermsRankAdapter"));

        String runtime = read(root.resolve("maddprestige-platform-paper/src/main/java/net/maddkraft/"
                + "maddprestige/platform/paper/bootstrap/ProductionRuntime.java"));
        assertFalse(runtime.contains("transitions.leases("));
        assertFalse(runtime.contains("configuration.stageTransitions("));
        assertFalse(runtime.contains("configuration.unresolvedStageRemaps("));

        String probe = read(root.resolve("maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/"
                + "diagnostic/OperationalDiagnosticProbe.java"));
        assertFalse(probe.contains("snapshot.stageTransitionLeases()"));
        assertFalse(probe.contains("snapshot.configurationStageTransitions()"));
        assertFalse(probe.contains("snapshot.playerStages()"));

        String locale = read(root.resolve("maddprestige-platform-paper/src/main/resources/locales/en_US.yml"));
        assertFalse(locale.contains("rank=<rank>"));
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
