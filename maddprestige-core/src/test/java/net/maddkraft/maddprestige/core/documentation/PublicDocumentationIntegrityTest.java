package net.maddkraft.maddprestige.core.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.rank.ManagedRankState;
import net.maddkraft.maddprestige.api.rank.RankAdapter;
import net.maddkraft.maddprestige.api.rank.RankProjectionOutcome;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.api.rank.RankProjectionResult;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.core.admin.config.AdministrationConfigurationWorkflow;
import net.maddkraft.maddprestige.core.config.BackupMetadata;
import net.maddkraft.maddprestige.core.config.ConfigDraft;
import net.maddkraft.maddprestige.core.config.ConfigurationService;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublicDocumentationIntegrityTest {
    private static final List<String> PUBLIC_DOCUMENTS = List.of(
            "README.md", "docs/getting-started.md", "docs/QUICK_START.md", "docs/configuration.md",
            "docs/commands-permissions.md", "docs/STAGES_RANKS.md", "docs/REQUIREMENTS_SCOPES.md",
            "docs/COSTS_REWARDS.md", "docs/PRESTIGE_LIFECYCLE.md", "docs/CURRENCIES_ENTITLEMENTS.md",
            "docs/SEASONS_MILESTONES.md", "docs/integrations.md",
            "docs/operations/recovery.md", "docs/operations/upgrading.md",
            "docs/operations/deployment.md", "docs/operations/troubleshooting.md",
            "docs/api.md", "docs/events.md", "docs/architecture.md", "docs/acceptance.md",
            "docs/compatibility-baseline.md", "docs/player-guide.md", "docs/staff-guide.md",
            "examples/compatibility/member-adventurer-veteran/README.md",
            "examples/provider-sdk/README.md", "examples/numeric-prestige/README.md");
    private static final Set<String> COMMAND_ROOTS = Set.of("help", "status", "rankup", "prestige", "confirm",
            "simulate", "why", "player", "gui", "admin", "history", "config", "setup", "doctor", "locale", "staff");
    private static final Pattern LINK = Pattern.compile("\\[[^]]+]\\((?!https?://|#)([^)]+)\\)");
    private static final Pattern COMMAND = Pattern.compile("/maddprestige(?:\\s+([a-z][a-z-]*))?");
    private static final Pattern PERMISSION = Pattern.compile("`(maddprestige(?:\\.[a-z]+)+)`");
    private static final Pattern V1_BRANDING = Pattern.compile(
            "(?i)\\b(Curious|Unbound|MaddHatter|Tea Leaves|Rabbit Holes|Knave|Duchess|White Queen|Queen of Hearts)\\b");

    @Test
    @DisplayName("[A76][D-F13] Public local links, command roots, permissions, and V2-only instructions exist")
    void publicDocumentationReferencesRealSurfaces() throws IOException {
        Path root = repositoryRoot();
        String plugin = Files.readString(root.resolve("src/main/resources/plugin.yml"), StandardCharsets.UTF_8);
        assertTrue(plugin.contains("  prestige:"));
        assertTrue(plugin.contains("usage: /prestige"));
        Set<String> registeredPermissions = plugin.lines().map(String::strip)
                .filter(line -> line.matches("maddprestige(?:\\.[a-z]+)+:"))
                .map(line -> line.substring(0, line.length() - 1)).collect(java.util.stream.Collectors.toSet());
        ArrayList<String> failures = new ArrayList<>();

        for (String document : PUBLIC_DOCUMENTS) {
            Path path = root.resolve(document);
            assertTrue(Files.isRegularFile(path), document);
            String text = Files.readString(path, StandardCharsets.UTF_8);
            Matcher links = LINK.matcher(text);
            while (links.find()) {
                String target = links.group(1).split("#", 2)[0];
                if (!target.isBlank() && !Files.exists(path.getParent().resolve(target).normalize())) {
                    failures.add(document + " -> " + target);
                }
            }
            Matcher commands = COMMAND.matcher(text);
            while (commands.find()) {
                if (commands.group(1) != null && !COMMAND_ROOTS.contains(commands.group(1))) {
                    failures.add(document + " command root " + commands.group(1));
                }
            }
            Matcher permissions = PERMISSION.matcher(text);
            while (permissions.find()) {
                if (!registeredPermissions.contains(permissions.group(1))) {
                    failures.add(document + " permission " + permissions.group(1));
                }
            }
            if (text.matches("(?s).*(?m)^\\s*/(?:rankup|season|maddhatter)\\b.*")) {
                failures.add(document + " contains a stale V1 command");
            }
        }
        assertEquals(List.of(), failures);
    }

    @Test
    @DisplayName("[A70][D-F15] Generic example and public operator text contain no legacy gameplay/rank branding")
    void genericPublicSurfaceIsUnbranded() throws IOException {
        Path root = repositoryRoot();
        ArrayList<String> findings = new ArrayList<>();
        for (String document : PUBLIC_DOCUMENTS) {
            String text = Files.readString(root.resolve(document), StandardCharsets.UTF_8);
            if (V1_BRANDING.matcher(text).find()) {
                findings.add(document);
            }
        }
        try (var files = Files.walk(root.resolve("examples/compatibility/member-adventurer-veteran"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (V1_BRANDING.matcher(Files.readString(file, StandardCharsets.UTF_8)).find()) {
                    findings.add(root.relativize(file).toString());
                }
            }
        }
        assertEquals(List.of(), findings);
    }

    @Test
    @DisplayName("[A02][A70][D-F01..05] Historical stage example remains valid compatibility evidence")
    void exactExampleCompilesAndApplies() throws IOException {
        Path example = repositoryRoot().resolve("examples/compatibility/member-adventurer-veteran");
        LinkedHashMap<String, String> documents = new LinkedHashMap<>();
        for (String name : List.of("progression.yml", "requirements.yml", "rewards.yml", "lifecycle.yml",
                "integrations.yml")) {
            documents.put(name, Files.readString(example.resolve(name), StandardCharsets.UTF_8));
        }

        ProviderRegistry providers = new ProviderRegistry();
        providers.activate(providers.register("docs-test", new ExistingGroupsRankAdapter()));
        providers.activate(providers.register("docs-test", new PaperPlayTimeProvider()));
        ConfigurationService canonical = new ConfigurationService();
        AdministrationConfigurationWorkflow workflow = new AdministrationConfigurationWorkflow(canonical, providers,
                () -> Map.of(), List.of());
        ConfigDraft draft = new ConfigDraft(UUID.randomUUID(), Optional.empty(), documents,
                new Actor("test", Optional.empty(), "Documentation test"), Instant.now());

        var candidate = workflow.prepare(draft, Optional.empty()).toCompletableFuture().join();
        assertFalse(candidate.validation().hasErrors(), candidate.validation().toString());
        Set<String> acknowledgements = candidate.validation().findings().stream()
                .map(finding -> finding.code()).collect(java.util.stream.Collectors.toSet());
        var backup = new BackupMetadata("docs-test", RevisionHasher.hashText("docs-test"), Instant.now(), true);
        var active = workflow.apply(new ConfigRevisionId("docs_example"), candidate, acknowledgements, backup);

        assertEquals(List.of("member", "adventurer", "veteran"), active.prerequisites().stages().configuration()
                .order().stream().map(value -> value.value()).toList());
        assertEquals("member", active.prerequisites().stages().configuration().baselineStage().orElseThrow().value());
        assertTrue(active.lifecycle().configuration().prestige().enabled());
        assertFalse(active.lifecycle().configuration().prestige().hasLegacyStagePolicy());
    }

    @Test
    @DisplayName("Numeric example compiles and activates without stages or LuckPerms")
    void numericExampleCompilesWithoutStageOrRankProvider() throws IOException {
        Path example = repositoryRoot().resolve("examples/numeric-prestige");
        LinkedHashMap<String, String> documents = new LinkedHashMap<>();
        for (String name : List.of("progression.yml", "requirements.yml", "rewards.yml", "lifecycle.yml",
                "integrations.yml")) {
            documents.put(name, Files.readString(example.resolve(name), StandardCharsets.UTF_8));
        }
        ProviderRegistry providers = new ProviderRegistry();
        providers.activate(providers.register("docs-test", new PaperPlayTimeProvider()));
        AdministrationConfigurationWorkflow workflow = new AdministrationConfigurationWorkflow(
                new ConfigurationService(), providers, Map::of, List.of());
        ConfigDraft draft = new ConfigDraft(UUID.randomUUID(), Optional.empty(), documents,
                new Actor("test", Optional.empty(), "Numeric documentation test"), Instant.now());

        var candidate = workflow.prepare(draft, Optional.empty()).toCompletableFuture().join();

        assertFalse(candidate.validation().hasErrors(), candidate.validation().toString());
        assertFalse(candidate.stages().active());
        assertTrue(candidate.stages().order().isEmpty());
        assertTrue(candidate.lifecycle().prestige().enabled());
        assertFalse(candidate.lifecycle().prestige().hasLegacyStagePolicy());
        assertEquals("prestige_eligibility", candidate.lifecycle().prestige()
                .requirementTreeId().orElseThrow().value());
    }

    @Test
    @DisplayName("Release identity, support boundaries, and config/example schemas agree")
    void finalReleaseDocumentationAndSchemaParityAreExact() throws IOException {
        Path root = repositoryRoot();
        for (String document : PUBLIC_DOCUMENTS) {
            String text = Files.readString(root.resolve(document), StandardCharsets.UTF_8);
            assertFalse(text.contains("2.0.0-SNAPSHOT"), document);
            assertFalse(text.contains("public documentation candidate"), document);
        }

        List<String> currentPoms = List.of(
                "pom.xml", "maddprestige-api/pom.xml", "maddprestige-core/pom.xml",
                "maddprestige-persistence/pom.xml", "maddprestige-platform-paper/pom.xml",
                "maddprestige-integrations/pom.xml", "maddprestige-testkit/pom.xml",
                "maddprestige-distribution/pom.xml", "examples/provider-sdk/pom.xml",
                "qualification/migration-recovery/pom.xml",
                "qualification/runtime-provider-fault/provider-alpha/pom.xml",
                "qualification/runtime-provider-fault/provider-beta/pom.xml",
                "qualification/runtime-provider-fault/paper-harness/pom.xml");
        for (String pom : currentPoms) {
            String text = Files.readString(root.resolve(pom), StandardCharsets.UTF_8);
            assertFalse(text.contains("2.0.0-SNAPSHOT"), pom);
            assertTrue(text.contains("2.0.0-rc.1"), pom);
        }

        String readme = Files.readString(root.resolve("README.md"), StandardCharsets.UTF_8);
        String installation = Files.readString(root.resolve("docs/getting-started.md"), StandardCharsets.UTF_8);
        String upgrade = Files.readString(root.resolve("docs/operations/upgrading.md"), StandardCharsets.UTF_8);
        String api = Files.readString(root.resolve("docs/api.md"), StandardCharsets.UTF_8);
        assertTrue(readme.contains("MaddPrestige-2.0.0-rc.1.jar"));
        assertTrue(readme.contains("release candidate"));
        assertTrue(readme.contains("2.0.0-rc.1 release"));
        assertTrue(installation.contains("Paper 26.1.2 build 74"));
        assertTrue(installation.contains("LuckPerms 5.5.71"));
        assertTrue(upgrade.contains("forward-only"));
        assertTrue(upgrade.contains("fresh V2 player state starts at Prestige 0"));
        assertTrue(api.contains("2.x-stable-1"));
        assertTrue(api.contains("net.maddkraft:maddprestige-api:2.0.0-rc.1"));

        String quickStart = Files.readString(root.resolve("docs/QUICK_START.md"), StandardCharsets.UTF_8);
        assertTrue(quickStart.contains("/maddprestige setup prestige enabled"));
        assertTrue(quickStart.contains("/maddprestige setup preview"));
        assertTrue(quickStart.contains("requirements, costs, and rewards are independent")
                || quickStart.contains("requirement threshold and cost amount are independent"));
        assertFalse(quickStart.contains("required-stage"));
        assertFalse(quickStart.contains("reset-stage"));
        String scopes = Files.readString(root.resolve("docs/REQUIREMENTS_SCOPES.md"), StandardCharsets.UTF_8);
        assertTrue(scopes.contains("target is canonicalized as `DURATION`"));

        List<Path> schemaDocuments = List.of(
                root.resolve("examples/numeric-prestige/progression.yml"),
                root.resolve("examples/numeric-prestige/requirements.yml"),
                root.resolve("examples/numeric-prestige/rewards.yml"),
                root.resolve("examples/numeric-prestige/lifecycle.yml"),
                root.resolve("examples/numeric-prestige/integrations.yml"));
        assertEquals(List.of("3", "3", "3", "4", "7"), schemaDocuments.stream().map(document -> {
            try {
                return Files.readAllLines(document, StandardCharsets.UTF_8).stream()
                        .filter(line -> line.startsWith("schema-version:"))
                        .findFirst().orElseThrow().substring("schema-version:".length()).strip();
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }).toList());
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

    private static ProviderDescriptor descriptor(String id, String capability) {
        return new ProviderDescriptor(new ProviderId(id), "docs-test", "1", "1", List.of(),
                List.of(new CapabilityDescriptor(capability, capability, "documentation", Map.of())));
    }

    private static ProviderHealth health() {
        return new ProviderHealth(ProviderHealthState.AVAILABLE, "available", "ready", Instant.now());
    }

    private static final class ExistingGroupsRankAdapter implements RankAdapter {
        @Override
        public CompletionStage<Result<Set<String>>> validateTargets(Set<String> groups) {
            return CompletableFuture.completedFuture(Result.success(Set.copyOf(groups)));
        }

        @Override
        public CompletionStage<Result<ManagedRankState>> readManagedState(UUID playerId, Set<String> groups) {
            return CompletableFuture.completedFuture(Result.success(new ManagedRankState(playerId, Set.of(), List.of())));
        }

        @Override
        public CompletionStage<Result<RankProjectionResult>> project(RankProjectionRequest request) {
            ManagedRankState before = new ManagedRankState(request.playerId(), Set.of(), List.of());
            ManagedRankState after = new ManagedRankState(request.playerId(), request.desiredGroup().stream()
                    .collect(java.util.stream.Collectors.toSet()), List.of());
            return CompletableFuture.completedFuture(Result.success(
                    new RankProjectionResult(before, after, RankProjectionOutcome.APPLIED)));
        }

        @Override
        public ProviderDescriptor descriptor() {
            return PublicDocumentationIntegrityTest.descriptor("luckperms", "rank");
        }

        @Override
        public ProviderHealth health() {
            return PublicDocumentationIntegrityTest.health();
        }
    }

    private static final class PaperPlayTimeProvider implements MetricProvider {
        @Override
        public Set<MetricDescriptor> metrics() {
            return Set.of(new MetricDescriptor(new ProviderId("paper_statistics"), new MetricId("play_one_minute"),
                    MetricValueType.DURATION, MetricOperator.compatibleWith(MetricValueType.DURATION),
                    EnumSet.of(MetricReadMode.CURRENT, MetricReadMode.LIFETIME), true,
                    MetricMonotonicity.MONOTONIC, MetricResetPolicy.FAIL_RECONCILIATION, Map.of(), "Play time",
                    "Paper persisted play time", "duration", "Paper/Bukkit persisted player statistic"));
        }

        @Override
        public CompletionStage<Map<MetricQuery, MetricSample>> read(
                UUID playerId, List<MetricQuery> queries, long providerGeneration) {
            return CompletableFuture.completedFuture(Map.of());
        }

        @Override
        public ProviderDescriptor descriptor() {
            return PublicDocumentationIntegrityTest.descriptor("paper_statistics", "metric");
        }

        @Override
        public ProviderHealth health() {
            return PublicDocumentationIntegrityTest.health();
        }
    }
}
