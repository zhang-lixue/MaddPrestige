package net.maddkraft.maddprestige.core.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhaseEightFEvidenceIntegrityTest {
    private static final Pattern BASELINE = Pattern.compile("(?m)^Baseline: `([0-9a-f]{40})`<br>$");
    private static final Pattern UNRESOLVED = Pattern.compile(
            "(?i)\\$(?:base|head|branch)\\b|\\$\\{[A-Za-z_][A-Za-z0-9_.-]*}"
                    + "|%[A-Za-z_][A-Za-z0-9_]*%|\\b(?:TBD|TO_BE_FILLED|PLACEHOLDER_VALUE)\\b|<PLACEHOLDER>");
    private static final List<String> PROVENANCE_DOCUMENTS = List.of(
            "STATUS.md",
            "PHASE8F_OWNER_REVIEW_SUMMARY.txt",
            "docs/V2_PHASE8F_IMPLEMENTATION.md",
            "docs/V2_PHASE8F_RELEASE_MATRIX.md");
    private static final List<String> V2_OPERATOR_DOCUMENTS = List.of(
            "README.md",
            "docs/INSTALLATION_V2.md",
            "docs/QUICK_START.md",
            "docs/CONFIGURATION.md",
            "docs/UPGRADE_ROLLBACK_V2.md");
    private static final List<String> V2_PRODUCTION_ROOTS = List.of(
            "maddprestige-api/src/main",
            "maddprestige-core/src/main",
            "maddprestige-persistence/src/main",
            "maddprestige-platform-paper/src/main",
            "maddprestige-integrations/src/main",
            "maddprestige-testkit/src/main",
            "maddprestige-distribution/src/main");
    private static final Pattern LEGACY_BUKKIT_CONFIG = Pattern.compile(
            "gg\\.maddkraft\\.prestige|MaddPrestigePlugin|saveDefaultConfig\\(|reloadConfig\\(|saveConfig\\("
                    + "|getConfig\\(|[\"']config\\.yml[\"']");

    @Test
    @DisplayName("[OR8F-01] Release evidence has resolved, exact and consistent Git provenance")
    void releaseEvidenceHasResolvedExactProvenance() throws IOException, InterruptedException {
        Path root = repositoryRoot();
        String manifest = read(root.resolve("docs/V2_PHASE8F_FILE_MANIFEST.md"));
        Matcher matcher = BASELINE.matcher(manifest);
        assertTrue(matcher.find(), "manifest must contain one exact 40-character baseline SHA");
        String baseline = matcher.group(1);
        assertFalse(matcher.find(), "manifest must contain only one baseline declaration");

        for (String document : PROVENANCE_DOCUMENTS) {
            assertTrue(read(root.resolve(document)).contains(baseline), document + " must name the exact baseline");
        }
        Process process = new ProcessBuilder("git", "merge-base", "--is-ancestor", baseline, "HEAD")
                .directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), () -> "recorded baseline is not an ancestor of HEAD: " + output);

        ArrayList<String> findings = new ArrayList<>();
        try (var files = Files.walk(root.resolve("docs"))) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("V2_PHASE8F_"))
                    .toList()) {
                Matcher unresolved = UNRESOLVED.matcher(read(file));
                if (unresolved.find()) {
                    findings.add(root.relativize(file) + " -> " + unresolved.group());
                }
            }
        }
        Matcher summary = UNRESOLVED.matcher(read(root.resolve("PHASE8F_OWNER_REVIEW_SUMMARY.txt")));
        if (summary.find()) {
            findings.add("PHASE8F_OWNER_REVIEW_SUMMARY.txt -> " + summary.group());
        }
        assertEquals(List.of(), findings);
    }

    @Test
    @DisplayName("[OR8F-01] Evidence guard rejects representative unresolved placeholders")
    void unresolvedEvidenceGuardRejectsRepresentativePlaceholders() {
        for (String sample : List.of("$base", "$head", "$branch", "${GIT_SHA}", "%GIT_SHA%", "TBD",
                "TO_BE_FILLED", "PLACEHOLDER_VALUE", "<PLACEHOLDER>")) {
            assertTrue(UNRESOLVED.matcher(sample).find(), sample);
        }
        assertFalse(UNRESOLVED.matcher("993599dc47cacc76b6372302d338d1850bf2896e").find());
    }

    @Test
    @DisplayName("[OR8F-02] Root config is retained for V1 only and isolated from active V2 production")
    void rootConfigIsRetainedOnlyForV1AndV2DoesNotUseBukkitConfig() throws IOException {
        Path root = repositoryRoot();
        String legacyConfig = read(root.resolve("src/main/resources/config.yml"));
        String legacyBootstrap = read(root.resolve("src/main/java/gg/maddkraft/prestige/MaddPrestigePlugin.java"));
        String plugin = read(root.resolve("src/main/resources/plugin.yml"));
        assertTrue(legacyConfig.contains("progression-groups:"));
        assertTrue(legacyConfig.contains("quickshop-earned-weight: 0.25"));
        assertTrue(legacyBootstrap.contains("saveDefaultConfig()"));
        assertTrue(legacyBootstrap.contains("getConfig()"));
        assertTrue(plugin.contains(
                "main: net.maddkraft.maddprestige.platform.paper.bootstrap.MaddPrestigeV2Plugin"));

        String packageAudit = read(root.resolve("docs/V2_PHASE8F_PACKAGE_CONTENT_AUDIT.md"));
        for (String category : List.of("REQUIRED V2 RUNTIME", "REQUIRED TRANSITIONAL / LEGACY",
                "PUBLIC REPOSITORY-ONLY", "BUILD/TEST-ONLY", "OPTIONAL DEPENDENCY", "PROHIBITED")) {
            assertTrue(packageAudit.contains(category), category);
        }
        for (String resource : List.of("plugin.yml", "META-INF/MANIFEST.MF", "config.yml",
                "defaults/progression.yml", "locales/en_US.yml", "META-INF/services/java.sql.Driver",
                "SQLite", "SnakeYAML", "THIRD-PARTY-NOTICES.txt", "examples/member-adventurer-veteran")) {
            assertTrue(packageAudit.contains(resource), resource);
        }

        ArrayList<String> findings = new ArrayList<>();
        for (String document : V2_OPERATOR_DOCUMENTS) {
            String text = read(root.resolve(document));
            for (String forbidden : List.of("plugins/MaddPrestige/config.yml", "edit config.yml",
                    "saveDefaultConfig", "getConfig()")) {
                if (text.contains(forbidden)) {
                    findings.add(document + " -> " + forbidden);
                }
            }
        }
        for (String sourceRoot : V2_PRODUCTION_ROOTS) {
            Path directory = root.resolve(sourceRoot);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (var files = Files.walk(directory)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    Matcher legacy = LEGACY_BUKKIT_CONFIG.matcher(read(file));
                    if (legacy.find()) {
                        findings.add(root.relativize(file) + " -> " + legacy.group());
                    }
                }
            }
        }
        assertEquals(List.of(), findings);
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
