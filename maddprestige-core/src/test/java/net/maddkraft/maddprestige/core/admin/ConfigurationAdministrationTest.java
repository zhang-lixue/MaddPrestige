package net.maddkraft.maddprestige.core.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.rank.ManagedRankState;
import net.maddkraft.maddprestige.api.rank.RankAdapter;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.api.rank.RankProjectionResult;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationAdministrationService;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationApplyKind;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationHistoryStore;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationSnapshotStore;
import net.maddkraft.maddprestige.core.admin.config.CanonicalGuidedConfigurationAdministration;
import net.maddkraft.maddprestige.core.admin.config.GuidedMoneyScalingPair;
import net.maddkraft.maddprestige.core.admin.config.GuidedRequirementConfigurationEntry;
import net.maddkraft.maddprestige.core.admin.config.GuidedScalingParameter;
import net.maddkraft.maddprestige.core.admin.config.AdministrationConfigurationWorkflow;
import net.maddkraft.maddprestige.core.admin.config.PreparedConfigurationSnapshot;
import net.maddkraft.maddprestige.core.admin.config.ScalingOverrideEdit;
import net.maddkraft.maddprestige.core.admin.config.StoredConfigurationRevision;
import net.maddkraft.maddprestige.core.admin.config.StructuredConfigurationValue;
import net.maddkraft.maddprestige.core.admin.setup.SetupStage;
import net.maddkraft.maddprestige.core.admin.setup.SetupCost;
import net.maddkraft.maddprestige.core.admin.setup.SetupPrestige;
import net.maddkraft.maddprestige.core.admin.setup.SetupRequirement;
import net.maddkraft.maddprestige.core.admin.setup.SetupReward;
import net.maddkraft.maddprestige.core.admin.setup.SetupWizardService;
import net.maddkraft.maddprestige.core.config.BackupMetadata;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.ConfigurationService;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.schema.ActiveConfigurationSchema;
import net.maddkraft.maddprestige.core.scaling.SegmentScalingMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfigurationAdministrationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-16T12:00:00Z"), ZoneOffset.UTC);
    private static final PermissionSubject OWNER = new PermissionSubject(
            new Actor("console", Optional.empty(), "Owner"), AdministrationPermissions.all());

    @Test
    @DisplayName("[A02] Setup wizard creates a validated internal ladder only after final preview/apply")
    void setupWizardCreatesSimpleLadderWithoutExternalMutation() {
        Fixture fixture = new Fixture();
        SetupWizardService wizard = new SetupWizardService(fixture.service, new ProviderRegistry());
        var discovery = wizard.discover(OWNER);
        assertFalse(discovery.activeConfigurationPresent());
        assertTrue(discovery.externalGroupPolicy().contains("never creates groups"));
        UUID session = wizard.start(OWNER);
        wizard.selectRankProvider(OWNER, session, Optional.empty());
        wizard.addStage(OWNER, session, new SetupStage(new StageId("member"), "Member", Optional.empty()));
        wizard.addStage(OWNER, session, new SetupStage(new StageId("veteran"), "Veteran", Optional.empty()));

        var preview = wizard.preview(OWNER, session).toCompletableFuture().join();

        assertFalse(preview.configuration().validation().hasErrors(), preview.configuration().validation().toString());
        assertEquals(List.of("command.setup.experience_transition", "command.setup.experience_requirement",
                        "command.setup.experience_cost", "command.setup.experience_reward",
                        "command.setup.experience_prestige_disabled"),
                preview.playerExperience().stream().map(value -> value.key()).toList());
        assertTrue(fixture.canonical.active().isEmpty());
        AdministrationException arbitrary = assertThrows(AdministrationException.class, () -> wizard.apply(OWNER,
                session, acknowledgements(preview.configuration()), "Unsafe caller codes"));
        assertEquals("config.acknowledgement.server_authority_required", arbitrary.code());
        var authority = wizard.prepareAcknowledgement(OWNER, session);
        PermissionSubject intruder = new PermissionSubject(new Actor("staff", Optional.of(UUID.randomUUID()),
                "Intruder"), AdministrationPermissions.all());
        assertEquals("setup.session.owner_mismatch", assertThrows(AdministrationException.class, () ->
                wizard.confirmAcknowledgement(intruder, authority.acknowledgementId(), "stolen token")).code());
        wizard.confirmAcknowledgement(OWNER, authority.acknowledgementId(), "Initial internal ladder")
                .toCompletableFuture().join();
        assertTrue(fixture.canonical.active().orElseThrow().compiled().documents().get("progression.yml")
                .contains("order:\n  - member\n  - veteran\n"));
    }

    @Test
    @DisplayName("[A68][OR8D-09] Real setup and config failures retain exact public facts")
    void realAdministrationFailuresRetainExactSemanticFacts() {
        ProviderRegistry setupProviders = new ProviderRegistry();
        setupProviders.activate(setupProviders.register("test", new SetupProvider()));
        Fixture fixture = new Fixture(setupProviders);
        SetupWizardService wizard = new SetupWizardService(fixture.service, setupProviders);
        UUID session = wizard.start(OWNER);
        SetupStage member = new SetupStage(new StageId("member"), "Member", Optional.empty());
        wizard.addStage(OWNER, session, member);

        AdministrationException duplicate = assertThrows(AdministrationException.class,
                () -> wizard.addStage(OWNER, session, member));
        AdministrationException baseline = assertThrows(AdministrationException.class,
                () -> wizard.selectBaseline(OWNER, session, new StageId("missing")));
        var introspection = new net.maddkraft.maddprestige.core.admin.config.ConfigurationIntrospectionService(
                compatibilitySchema(), fixture.canonical::active, () -> ValidationReport.VALID);
        AdministrationException path = assertThrows(AdministrationException.class,
                () -> introspection.explain(OWNER, "prestige.unknown"));
        AdministrationException prestige = assertThrows(AdministrationException.class,
                () -> wizard.configurePrestige(OWNER, session, new SetupPrestige(true,
                        Optional.of(new StageId("legacy")), Optional.of(new StageId("member")))));

        UUID requirements = wizard.start(OWNER);
        wizard.addStage(OWNER, requirements,
                new SetupStage(new StageId("member"), "Member", Optional.empty()));
        wizard.addStage(OWNER, requirements,
                new SetupStage(new StageId("veteran"), "Veteran", Optional.empty()));
        wizard.addStage(OWNER, requirements,
                new SetupStage(new StageId("elite"), "Elite", Optional.empty()));
        SetupRequirement repeated = new SetupRequirement(new RequirementId("playtime"),
                new ProviderId("setup_metric"), new MetricId("play_time"), "greater-or-equal", "10",
                "absolute", "live");
        wizard.configureRequirementForStage(OWNER, requirements, new StageId("veteran"), repeated);
        AdministrationException duplicateRequirement = assertThrows(AdministrationException.class,
                () -> wizard.configureRequirementForStage(OWNER, requirements, new StageId("elite"), repeated));

        UUID missingMetricSession = wizard.start(OWNER);
        wizard.addStage(OWNER, missingMetricSession,
                new SetupStage(new StageId("member"), "Member", Optional.empty()));
        wizard.addStage(OWNER, missingMetricSession,
                new SetupStage(new StageId("veteran"), "Veteran", Optional.empty()));
        AdministrationException missingMetric = assertThrows(AdministrationException.class,
                () -> wizard.configureRequirement(OWNER, missingMetricSession, new SetupRequirement(
                        new RequirementId("missing"), new ProviderId("statistics"), new MetricId("play_time"),
                        "greater-or-equal", "10", "absolute", "live")));

        UUID missingGroupSession = wizard.start(OWNER);
        wizard.selectRankProvider(OWNER, missingGroupSession, Optional.of(new ProviderId("luckperms")));
        wizard.addStage(OWNER, missingGroupSession,
                new SetupStage(new StageId("member"), "Member", Optional.of("Member")));
        wizard.addStage(OWNER, missingGroupSession,
                new SetupStage(new StageId("veteran"), "Veteran", Optional.empty()));
        AdministrationException missingGroup = assertThrows(AdministrationException.class,
                () -> wizard.preview(OWNER, missingGroupSession));

        UUID incompleteDraft = fixture.service.beginInitialDraft(OWNER,
                Map.of("progression.yml", "schema-version: 4\n"), "semantic-fact-test");
        AdministrationException missingDocument = assertThrows(AdministrationException.class,
                () -> fixture.service.editScalar(OWNER, incompleteDraft, "prestige.enabled", "true"));
        StageId sameStage = new StageId("legacy");
        AdministrationException invalidRemap = assertThrows(AdministrationException.class,
                () -> fixture.service.selectStageRemap(OWNER, incompleteDraft, sameStage, sameStage));

        PermissionSubject unauthorized = new PermissionSubject(
                new Actor("staff", Optional.empty(), "Staff"), Set.of());
        AdministrationException denied = assertThrows(AdministrationException.class,
                () -> unauthorized.require("maddprestige.admin.config.apply"));

        assertEquals("setup.stage.duplicate", duplicate.code());
        assertEquals("member", duplicate.facts().get("stage"));
        assertEquals("setup.baseline.unknown", baseline.code());
        assertEquals("missing", baseline.facts().get("stage"));
        assertEquals("config.path.unknown", path.code());
        assertEquals("prestige.unknown", path.facts().get("path"));
        assertEquals("setup.prestige.stage_unknown", prestige.code());
        assertEquals(Map.of("stage", "legacy", "purpose", "eligibility"), prestige.facts());
        assertEquals("setup.requirement.duplicate", duplicateRequirement.code());
        assertEquals(Map.of("requirement", "playtime", "stage", "elite"), duplicateRequirement.facts());
        assertEquals("setup.requirement.metric_unknown", missingMetric.code());
        assertEquals(Map.of("provider", "statistics", "metric", "play_time"), missingMetric.facts());
        assertEquals("setup.group.missing", missingGroup.code());
        assertEquals(Map.of("stage", "veteran", "provider", "luckperms"), missingGroup.facts());
        assertEquals("config.document.missing", missingDocument.code());
        assertEquals(Map.of("document", "lifecycle.yml"), missingDocument.facts());
        assertEquals("stage.change.remap_invalid", invalidRemap.code());
        assertEquals(Map.of("source", "legacy", "target", "legacy"), invalidRemap.facts());
        assertEquals("permission.denied", denied.code());
        assertEquals(Map.of("permission", "maddprestige.admin.config.apply"), denied.facts());
    }

    @Test
    @DisplayName("Setup wizard generates numeric Prestige without a stage ladder")
    void setupWizardBuildsCompleteSimpleConfiguration() {
        ProviderRegistry providers = new ProviderRegistry();
        providers.activate(providers.register("test", new SetupProvider()));
        providers.activate(providers.register("test", new SetupCostProvider()));
        providers.activate(providers.register("test", new SetupRewardProvider()));
        Fixture fixture = new Fixture(providers);
        SetupWizardService wizard = new SetupWizardService(fixture.service, providers);
        UUID session = wizard.start(OWNER);
        wizard.configureRequirement(OWNER, session, new SetupRequirement(new RequirementId("play"),
                new ProviderId("setup_metric"), new MetricId("play_time"), "greater-or-equal", "10", "absolute",
                "live"));
        wizard.configureCost(OWNER, session, new SetupCost(new CostId("payment"), new ProviderId("setup_cost"),
                "debit", "currency-amount", "25", "Payment"));
        wizard.configureReward(OWNER, session, new SetupReward(new RewardId("grant"),
                new ProviderId("setup_reward"),
                "grant", "exact-decimal", "1", "Grant"));
        wizard.configurePrestige(OWNER, session, SetupPrestige.numericEnabled());

        var preview = wizard.preview(OWNER, session).toCompletableFuture().join();

        assertFalse(preview.configuration().validation().hasErrors(), preview.configuration().validation().toString());
        assertTrue(experienceHas(preview, "command.setup.experience_requirement", "id", "play"));
        assertTrue(experienceHas(preview, "command.setup.experience_cost", "id", "payment"));
        assertTrue(experienceHas(preview, "command.setup.experience_reward", "id", "grant"));
        assertTrue(preview.playerExperience().stream()
                .anyMatch(line -> line.key().equals("command.setup.experience_prestige_enabled")));
        wizard.apply(OWNER, session, Set.of(), "Complete simple setup")
                .toCompletableFuture().join();
        Map<String, String> active = fixture.canonical.active().orElseThrow().compiled().documents();
        assertEquals("schema-version: 3\n", active.get("progression.yml"));
        assertFalse(active.get("progression.yml").contains("stages:"));
        assertTrue(active.get("requirements.yml").contains("metric: play_time"));
        assertTrue(active.get("lifecycle.yml").contains("requirement-tree: setup_eligibility"));
        assertTrue(active.get("lifecycle.yml").contains("costs: [payment]"));
        assertTrue(active.get("lifecycle.yml").contains("rewards: [grant]"));
        assertFalse(active.get("lifecycle.yml").contains("required-stages"));
        assertFalse(active.get("lifecycle.yml").contains("reset-stage"));
        assertFalse(active.get("lifecycle.yml").contains("reset-policy"));
        assertFalse(active.get("requirements.yml").contains("maximum-depth"));
        assertFalse(active.get("rewards.yml").contains("command-actions"));
        assertEquals("schema-version: 7\n", active.get("integrations.yml"));
    }

    @Test
    @DisplayName("[A02][A70] Canonical setup assigns distinct validated requirements to ordered target stages")
    void setupWizardBuildsStageSpecificRequirements() {
        ProviderRegistry providers = new ProviderRegistry();
        providers.activate(providers.register("test", new SetupProvider()));
        Fixture fixture = new Fixture(providers);
        SetupWizardService wizard = new SetupWizardService(fixture.service, providers);
        UUID session = wizard.start(OWNER);
        wizard.addStage(OWNER, session, new SetupStage(new StageId("member"), "Member", Optional.empty()));
        wizard.addStage(OWNER, session,
                new SetupStage(new StageId("adventurer"), "Adventurer", Optional.empty()));
        wizard.addStage(OWNER, session, new SetupStage(new StageId("veteran"), "Veteran", Optional.empty()));
        wizard.configureRequirementForStage(OWNER, session, new StageId("adventurer"), new SetupRequirement(
                new RequirementId("play_60"), new ProviderId("setup_metric"), new MetricId("play_time"),
                "greater-or-equal", "60", "absolute", "live"));
        wizard.configureRequirementForStage(OWNER, session, new StageId("veteran"), new SetupRequirement(
                new RequirementId("play_180"), new ProviderId("setup_metric"), new MetricId("play_time"),
                "greater-or-equal", "180", "absolute", "live"));

        var preview = wizard.preview(OWNER, session).toCompletableFuture().join();

        assertFalse(preview.configuration().validation().hasErrors(), preview.configuration().validation().toString());
        assertEquals("setup.requirement.baseline", assertThrows(AdministrationException.class, () ->
                wizard.configureRequirementForStage(OWNER, session, new StageId("member"), new SetupRequirement(
                        new RequirementId("invalid"), new ProviderId("setup_metric"), new MetricId("play_time"),
                        "greater-or-equal", "1", "absolute", "live"))).code());
        var authority = wizard.prepareAcknowledgement(OWNER, session);
        wizard.confirmAcknowledgement(OWNER, authority.acknowledgementId(), "Stage-specific setup")
                .toCompletableFuture().join();
        Map<String, String> documents = fixture.canonical.active().orElseThrow().compiled().documents();
        assertTrue(documents.get("progression.yml")
                .contains("requirements: setup_eligibility_adventurer"));
        assertTrue(documents.get("progression.yml")
                .contains("requirements: setup_eligibility_veteran"));
        assertTrue(documents.get("requirements.yml")
                .contains("setup_eligibility_adventurer:"));
        assertTrue(documents.get("requirements.yml")
                .contains("setup_eligibility_veteran:"));
        assertTrue(experienceHas(preview, "command.setup.experience_stage_requirement", "id", "play_60"));
        assertTrue(experienceHas(preview, "command.setup.experience_stage_requirement", "id", "play_180"));
    }

    @Test
    @DisplayName("[A02][A70][OR8D-04][OR8D-05] Frozen setup output is deterministic and byte-exact public profile")
    void canonicalSetupOutputMatchesPublicExampleExactly() throws IOException {
        Map<String, String> first = generateCanonicalProfile();
        Map<String, String> second = generateCanonicalProfile();

        assertEquals(first, second, "identical setup inputs must produce identical canonical documents");
        assertEquals(List.of("progression.yml", "requirements.yml", "rewards.yml", "lifecycle.yml",
                "integrations.yml"), List.copyOf(first.keySet()));
        String requirements = first.get("requirements.yml");
        assertTrue(requirements.indexOf("playtime_60_seconds:")
                < requirements.indexOf("playtime_180_seconds:"));
        assertTrue(requirements.indexOf("setup_eligibility_adventurer:")
                < requirements.indexOf("setup_eligibility_veteran:"));

        Path root = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(root.resolve("examples/compatibility/member-adventurer-veteran"))) {
            root = root.getParent();
        }
        for (String document : first.keySet()) {
            assertEquals(Files.readString(root.resolve("examples/compatibility/member-adventurer-veteran").resolve(document),
                    StandardCharsets.UTF_8), first.get(document), document);
        }
    }

    @Test
    @DisplayName("[A02][A70][A76] Guided playtime setup types PT1M/PT3M at entry and retains preview authority")
    void guidedPlaytimeSetupCanonicalizesDurationTargetsBeforePreview() {
        ProviderRegistry providers = new ProviderRegistry();
        providers.activate(providers.register("test", new CanonicalRankAdapter()));
        providers.activate(providers.register("test", new CanonicalMetricProvider()));
        Fixture fixture = new Fixture(providers);
        SetupWizardService wizard = new SetupWizardService(fixture.service, providers);
        UUID session = wizard.start(OWNER);
        assertEquals(session, wizard.currentSession(OWNER));

        wizard.selectRankProvider(OWNER, session, Optional.of(new ProviderId("luckperms")));
        wizard.addStage(OWNER, session, new SetupStage(new StageId("member"), "Member", Optional.of("Member")));
        wizard.addStage(OWNER, session, new SetupStage(new StageId("adventurer"), "Adventurer",
                Optional.of("Adventurer")));
        wizard.addStage(OWNER, session, new SetupStage(new StageId("veteran"), "Veteran", Optional.of("Veteran")));
        wizard.selectBaseline(OWNER, session, new StageId("member"));
        wizard.configurePlaytimeRequirement(OWNER, session, new StageId("adventurer"), "PT1M");
        AdministrationException invalid = assertThrows(AdministrationException.class, () ->
                wizard.configurePlaytimeRequirement(OWNER, session, new StageId("veteran"), "one-minute"));
        assertEquals("setup.requirement.target.invalid", invalid.code());
        assertEquals("DURATION", invalid.facts().get("type"));
        assertEquals("one-minute", invalid.facts().get("target"));
        wizard.configurePlaytimeRequirement(OWNER, session, new StageId("veteran"), "PT3M");
        wizard.configurePrestige(OWNER, session, new SetupPrestige(true, Optional.of(new StageId("veteran")),
                Optional.of(new StageId("member"))));

        Map<String, String> generated = wizard.generatedDocuments(OWNER, session);
        assertTrue(generated.get("requirements.yml").contains("adventurer_playtime:"));
        assertTrue(generated.get("requirements.yml").contains("veteran_playtime:"));
        assertEquals(2, occurrences(generated.get("requirements.yml"), "value-type: DURATION"));
        assertTrue(generated.get("requirements.yml").contains("target: PT1M"));
        assertTrue(generated.get("requirements.yml").contains("target: PT3M"));

        var preview = wizard.preview(OWNER, session).toCompletableFuture().join();
        assertFalse(preview.configuration().validation().hasErrors(), preview.configuration().validation().toString());
        var acknowledgement = wizard.prepareAcknowledgement(OWNER, session);
        wizard.confirmAcknowledgement(OWNER, acknowledgement.acknowledgementId(), "Guided public profile")
                .toCompletableFuture().join();
        assertEquals(generated, fixture.canonical.active().orElseThrow().compiled().documents());
        assertEquals("setup.session.unknown", assertThrows(AdministrationException.class,
                () -> wizard.currentSession(OWNER)).code());
    }

    private static long occurrences(String source, String value) {
        return source.lines().filter(line -> line.contains(value)).count();
    }

    private static Map<String, String> generateCanonicalProfile() {
        ProviderRegistry providers = new ProviderRegistry();
        providers.activate(providers.register("test", new CanonicalRankAdapter()));
        providers.activate(providers.register("test", new CanonicalMetricProvider()));
        Fixture fixture = new Fixture(providers);
        SetupWizardService wizard = new SetupWizardService(fixture.service, providers);
        UUID session = wizard.start(OWNER);
        wizard.selectRankProvider(OWNER, session, Optional.of(new ProviderId("luckperms")));
        wizard.addStage(OWNER, session, new SetupStage(new StageId("member"), "Member", Optional.of("Member")));
        wizard.addStage(OWNER, session, new SetupStage(new StageId("adventurer"), "Adventurer",
                Optional.of("Adventurer")));
        wizard.addStage(OWNER, session, new SetupStage(new StageId("veteran"), "Veteran",
                Optional.of("Veteran")));
        wizard.selectBaseline(OWNER, session, new StageId("member"));
        wizard.configureRequirementForStage(OWNER, session, new StageId("adventurer"), new SetupRequirement(
                new RequirementId("playtime_60_seconds"), new ProviderId("paper_statistics"),
                new MetricId("play_one_minute"), "GREATER_OR_EQUAL", "PT1M", "SINCE_PRESTIGE_START", "LIVE"));
        wizard.configureRequirementForStage(OWNER, session, new StageId("veteran"), new SetupRequirement(
                new RequirementId("playtime_180_seconds"), new ProviderId("paper_statistics"),
                new MetricId("play_one_minute"), "GREATER_OR_EQUAL", "PT3M", "SINCE_PRESTIGE_START", "LIVE"));
        wizard.configurePrestige(OWNER, session, new SetupPrestige(true, Optional.of(new StageId("veteran")),
                Optional.of(new StageId("member"))));
        Map<String, String> generated = wizard.generatedDocuments(OWNER, session);
        var preview = wizard.preview(OWNER, session).toCompletableFuture().join();
        assertFalse(preview.configuration().validation().hasErrors(), preview.configuration().validation().toString());
        var acknowledgement = wizard.prepareAcknowledgement(OWNER, session);
        wizard.confirmAcknowledgement(OWNER, acknowledgement.acknowledgementId(), "Canonical public profile")
                .toCompletableFuture().join();
        assertEquals(generated, fixture.canonical.active().orElseThrow().compiled().documents());
        return generated;
    }

    @Test
    void setupPersistsEveryDirectlyConfigurableBuiltInIntegrationSelectedByRequirementsCostsOrRewards() {
        assertSetupRequirementEnables("vault_balance", "balance", "vault");
        assertSetupCostEnables("vault_economy_cost", "vault");
        assertSetupRewardEnables("vault_economy_reward", "vault");
        assertSetupRequirementEnables("mcmmo", "total_level", "mcmmo");
        assertSetupRequirementEnables("event_progress", "mcmmo_adjusted_xp_total", "mcmmo");
        assertSetupRequirementEnables("griefprevention_claims", "owned_claim_count", "griefprevention");
        assertSetupRewardEnables("griefprevention_claim_blocks_reward", "griefprevention");
    }

    @Test
    void setupRejectsBuiltInSelectionsWhoseRequiredParametersItCannotGenerate() {
        assertSetupRequirementRejected("placeholder_input", "sample");
        assertSetupRequirementRejected("mcmmo", "skill_level");
        assertSetupRequirementRejected("worldguard_region", "inside_region");
        assertSetupRequirementRejected("craftengine_item_count", "item_count");
        assertSetupRewardRejected("craftengine_item_reward");
    }

    @Test
    @SuppressWarnings("unchecked")
    void defensiveIntegrationGenerationUsesTheSameCompleteStructuredDiagnostic() throws Exception {
        ProviderRegistry providers = new ProviderRegistry();
        providers.activate(providers.register("test", new SetupProvider("placeholder_input", "sample")));
        Fixture fixture = new Fixture(providers);
        SetupWizardService wizard = twoStageWizard(fixture, providers);
        UUID session = wizard.start(OWNER);
        addTwoStages(wizard, session);
        SetupRequirement injected = new SetupRequirement(new RequirementId("input"),
                new ProviderId("placeholder_input"), new MetricId("sample"), "GREATER_OR_EQUAL", "1",
                "ABSOLUTE", "LIVE");
        var sessionsField = SetupWizardService.class.getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        Map<UUID, Object> sessions = (Map<UUID, Object>) sessionsField.get(wizard);
        Object current = sessions.get(session);
        var withRequirement = current.getClass().getDeclaredMethod("withRequirement", SetupRequirement.class);
        withRequirement.setAccessible(true);
        sessions.put(session, withRequirement.invoke(current, injected));

        AdministrationException failure = assertThrows(AdministrationException.class,
                () -> wizard.generatedDocuments(OWNER, session));

        assertEquals("setup.integration.unconfigurable", failure.code());
        assertEquals(Map.of("provider", "placeholder_input", "component", "placeholderapi",
                "requirement", "placeholder, value type, and maximum age"), failure.facts());
    }

    private static void assertSetupRequirementRejected(String providerId, String metricId) {
        ProviderRegistry providers = new ProviderRegistry();
        providers.activate(providers.register("test", new SetupProvider(providerId, metricId)));
        Fixture fixture = new Fixture(providers);
        SetupWizardService wizard = twoStageWizard(fixture, providers);
        UUID session = wizard.start(OWNER);
        addTwoStages(wizard, session);

        AdministrationException failure = assertThrows(AdministrationException.class,
                () -> wizard.configureRequirement(OWNER, session, new SetupRequirement(new RequirementId("input"),
                        new ProviderId(providerId), new MetricId(metricId), "GREATER_OR_EQUAL", "1",
                        "ABSOLUTE", "LIVE")));

        assertEquals("setup.integration.unconfigurable", failure.code());
        assertEquals(expectedUnconfigurableFacts(providerId), failure.facts());
        assertTrue(fixture.canonical.active().isEmpty(), "unconfigurable requirement must not publish a revision");
    }

    private static void assertSetupRewardRejected(String providerId) {
        Fixture fixture = new Fixture();
        SetupWizardService wizard = twoStageWizard(fixture, new ProviderRegistry());
        UUID session = wizard.start(OWNER);
        addTwoStages(wizard, session);

        AdministrationException failure = assertThrows(AdministrationException.class,
                () -> wizard.configureReward(OWNER, session, new SetupReward(new RewardId("gift"),
                        new ProviderId(providerId), "custom_item", "COUNT", "1", "Gift")));

        assertEquals("setup.integration.unconfigurable", failure.code());
        assertEquals(Map.of("provider", "craftengine_item_reward", "component", "craftengine",
                "requirement", "item-id metadata"), failure.facts());
        assertTrue(fixture.canonical.active().isEmpty(), "unconfigurable reward must not publish a revision");
    }

    private static Map<String, String> expectedUnconfigurableFacts(String providerId) {
        return switch (providerId) {
            case "placeholder_input" -> Map.of("provider", providerId, "component", "placeholderapi",
                    "requirement", "placeholder, value type, and maximum age");
            case "mcmmo" -> Map.of("provider", providerId, "component", "mcmmo",
                    "requirement", "skill filter");
            case "worldguard_region" -> Map.of("provider", providerId, "component", "worldguard",
                    "requirement", "region-id filter");
            case "craftengine_item_count" -> Map.of("provider", providerId, "component", "craftengine",
                    "requirement", "item-id filter");
            default -> throw new AssertionError("Unexpected unconfigurable provider " + providerId);
        };
    }

    private static void assertSetupRequirementEnables(String providerId, String metricId, String integration) {
        ProviderRegistry providers = new ProviderRegistry();
        providers.activate(providers.register("test", new SetupProvider(providerId, metricId)));
        Fixture fixture = new Fixture(providers);
        SetupWizardService wizard = twoStageWizard(fixture, providers);
        UUID session = wizard.start(OWNER);
        addTwoStages(wizard, session);
        wizard.configureRequirement(OWNER, session, new SetupRequirement(new RequirementId("requirement"),
                new ProviderId(providerId), new MetricId(metricId), "GREATER_OR_EQUAL", "1", "ABSOLUTE", "LIVE"));

        applyAndAssertIntegration(wizard, session, fixture, integration);
        assertEquals(net.maddkraft.maddprestige.api.provider.ActivationState.ACTIVE,
                providers.find(new ProviderId(providerId)).orElseThrow().activation());
    }

    private static void assertSetupCostEnables(String providerId, String integration) {
        ProviderRegistry providers = new ProviderRegistry();
        providers.activate(providers.register("test", new SetupCostProvider(providerId)));
        Fixture fixture = new Fixture(providers);
        SetupWizardService wizard = twoStageWizard(fixture, providers);
        UUID session = wizard.start(OWNER);
        addTwoStages(wizard, session);
        wizard.configureCost(OWNER, session, new SetupCost(new net.maddkraft.maddprestige.api.id.CostId("fee"),
                new ProviderId(providerId), "fixture", "COUNT", "1", "Fee"));

        applyAndAssertIntegration(wizard, session, fixture, integration);
        assertEquals(net.maddkraft.maddprestige.api.provider.ActivationState.ACTIVE,
                providers.find(new ProviderId(providerId)).orElseThrow().activation());
    }

    private static void assertSetupRewardEnables(String providerId, String integration) {
        ProviderRegistry providers = new ProviderRegistry();
        providers.activate(providers.register("test", new SetupRewardProvider(providerId)));
        Fixture fixture = new Fixture(providers);
        SetupWizardService wizard = twoStageWizard(fixture, providers);
        UUID session = wizard.start(OWNER);
        addTwoStages(wizard, session);
        wizard.configureReward(OWNER, session, new SetupReward(new net.maddkraft.maddprestige.api.id.RewardId("gift"),
                new ProviderId(providerId), "fixture", "COUNT", "1", "Gift"));

        applyAndAssertIntegration(wizard, session, fixture, integration);
        assertEquals(net.maddkraft.maddprestige.api.provider.ActivationState.ACTIVE,
                providers.find(new ProviderId(providerId)).orElseThrow().activation());
    }

    private static void applyAndAssertIntegration(
            SetupWizardService wizard, UUID session, Fixture fixture, String integration) {
        assertIntegrationEnabledAlone(wizard.generatedDocuments(OWNER, session).get("integrations.yml"), integration);
        var preview = wizard.preview(OWNER, session).toCompletableFuture().join();
        assertFalse(preview.configuration().validation().hasErrors(), preview.configuration().validation().toString());
        var acknowledgement = wizard.prepareAcknowledgement(OWNER, session);
        wizard.confirmAcknowledgement(OWNER, acknowledgement.acknowledgementId(), "Integration setup audit")
                .toCompletableFuture().join();
        assertIntegrationEnabledAlone(fixture.canonical.active().orElseThrow().compiled().documents()
                .get("integrations.yml"), integration);
    }

    private static SetupWizardService twoStageWizard(Fixture fixture, ProviderRegistry providers) {
        return new SetupWizardService(fixture.service, providers);
    }

    private static void addTwoStages(SetupWizardService wizard, UUID session) {
        wizard.addStage(OWNER, session, new SetupStage(new StageId("base"), "Base", Optional.empty()));
        wizard.addStage(OWNER, session, new SetupStage(new StageId("target"), "Target", Optional.empty()));
    }

    private static void assertIntegrationEnabledAlone(String integrations, String enabled) {
        for (String integration : List.of("vault", "mcmmo", "griefprevention", "worldguard", "craftengine")) {
            assertTrue(integrations.contains(integration + ":\n  enabled: " + integration.equals(enabled) + "\n"),
                    integrations);
        }
    }

    @Test
    @DisplayName("[A02] Setup resumes server state, cancels completely, and rejects provider loss after preview")
    void setupResumeCancelAndProviderLossFailClosed() {
        ProviderRegistry providers = new ProviderRegistry();
        var metricRegistration = providers.register("test", new SetupProvider());
        providers.activate(metricRegistration);
        Fixture fixture = new Fixture(providers);
        SetupWizardService wizard = new SetupWizardService(fixture.service, providers);

        UUID cancelled = wizard.start(OWNER);
        wizard.addStage(OWNER, cancelled, new SetupStage(new StageId("member"), "Member", Optional.empty()));
        wizard.addStage(OWNER, cancelled, new SetupStage(new StageId("veteran"), "Veteran", Optional.empty()));
        assertEquals(List.of("command.setup.experience_transition", "command.setup.experience_requirement",
                        "command.setup.experience_cost", "command.setup.experience_reward",
                        "command.setup.experience_prestige_disabled"),
                wizard.preview(OWNER, cancelled).toCompletableFuture().join().playerExperience().stream()
                        .map(value -> value.key()).toList());
        assertTrue(fixture.canonical.active().isEmpty(), "resumed preview is zero mutation");
        wizard.cancel(OWNER, cancelled);
        assertEquals("setup.session.unknown", assertThrows(AdministrationException.class,
                () -> wizard.preview(OWNER, cancelled)).code());

        UUID disappearing = wizard.start(OWNER);
        wizard.addStage(OWNER, disappearing, new SetupStage(new StageId("member"), "Member", Optional.empty()));
        wizard.addStage(OWNER, disappearing,
                new SetupStage(new StageId("veteran"), "Veteran", Optional.empty()));
        wizard.configureRequirement(OWNER, disappearing, new SetupRequirement(new RequirementId("play"),
                new ProviderId("setup_metric"), new MetricId("play_time"), "greater-or-equal", "10", "absolute",
                "live"));
        var preview = wizard.preview(OWNER, disappearing).toCompletableFuture().join();
        assertFalse(preview.configuration().validation().hasErrors(), preview.configuration().validation().toString());
        var authority = wizard.prepareAcknowledgement(OWNER, disappearing);

        providers.deactivate(metricRegistration);

        assertEquals("config.acknowledgement.findings_changed", failure(wizard.confirmAcknowledgement(OWNER,
                authority.acknowledgementId(), "provider disappeared")).code());
        assertTrue(fixture.canonical.active().isEmpty());
    }

    @Test
    @DisplayName("[A39]Two admin drafts use CAS so the stale preview cannot overwrite the winner")
    void staleConcurrentDraftFailsClosed() {
        Fixture fixture = new Fixture();
        StoredConfigurationRevision first = fixture.applyInitial(defaultDocuments());
        PermissionSubject adminA = new PermissionSubject(new Actor("staff", Optional.of(UUID.randomUUID()), "A"),
                AdministrationPermissions.all());
        PermissionSubject adminB = new PermissionSubject(new Actor("staff", Optional.of(UUID.randomUUID()), "B"),
                AdministrationPermissions.all());
        UUID draftA = fixture.service.beginDraft(adminA, "gui");
        UUID draftB = fixture.service.beginDraft(adminB, "command");
        fixture.service.editScalar(adminA, draftA, "prestige.cooldown", "PT2S");
        fixture.service.editScalar(adminB, draftB, "prestige.cooldown", "PT3S");
        var previewA = fixture.service.preview(adminA, draftA).toCompletableFuture().join();
        var previewB = fixture.service.preview(adminB, draftB).toCompletableFuture().join();
        StoredConfigurationRevision winner = fixture.service.applyDraft(adminA, draftA, Optional.of(first.id()),
                acknowledgements(previewA), "Admin A wins").toCompletableFuture().join();

        CompletionException wrapped = assertThrows(CompletionException.class, () -> fixture.service.applyDraft(
                adminB, draftB, Optional.of(first.id()), acknowledgements(previewB), "Stale admin B")
                .toCompletableFuture().join());

        assertEquals("config.revision.stale", ((AdministrationException) wrapped.getCause()).code());
        assertEquals(winner.id(), fixture.canonical.active().orElseThrow().revisionId());
        assertTrue(fixture.canonical.active().orElseThrow().compiled().documents().get("lifecycle.yml")
                .contains("cooldown: PT2S"));
    }

    @Test
    @DisplayName("[A38][A39] Surgical draft edit preserves comments/order and remains inactive until apply")
    void preservesPresentationAndDraftIsolation() {
        Fixture fixture = new Fixture();
        StoredConfigurationRevision first = fixture.applyInitial(defaultDocuments());
        String before = fixture.canonical.active().orElseThrow().compiled().documents().get("lifecycle.yml");
        UUID draft = fixture.service.beginDraft(OWNER, "command");

        var edited = fixture.service.editScalar(OWNER, draft, "prestige.cooldown", "PT2S");
        String after = edited.documents().get("lifecycle.yml");

        assertEquals(before + "prestige:\n  cooldown: PT2S\n", after);
        assertEquals(before, fixture.canonical.active().orElseThrow().compiled().documents().get("lifecycle.yml"));
        assertEquals(first.id(), fixture.canonical.active().orElseThrow().revisionId());

        var preview = fixture.service.preview(OWNER, draft).toCompletableFuture().join();
        fixture.service.applyDraft(OWNER, draft, Optional.of(first.id()), acknowledgements(preview),
                "Raise disabled default increment for future use").toCompletableFuture().join();
        assertEquals(after, fixture.canonical.active().orElseThrow().compiled().documents().get("lifecycle.yml"));
        assertTrue(after.contains("# Numeric Prestige is disabled until a validated revision explicitly enables it."));
    }

    @Test
    @DisplayName("Schema-owned scope/completion edits change runtime compilation")
    void canonicalRequirementEditsDriveRuntimeCompiler() {
        Fixture fixture = new Fixture();
        LinkedHashMap<String, String> documents = new LinkedHashMap<>(defaultDocuments());
        documents.put("requirements.yml", """
                schema-version: 3
                requirements:
                  play:
                    provider: dormant
                    metric: play
                    value-type: DURATION
                    target: PT1M
                trees: {}
                """);
        fixture.applyInitial(documents);
        UUID draft = fixture.service.beginDraft(OWNER, "command");

        fixture.service.editScalar(OWNER, draft, "requirements.requirements.play.scope",
                "SINCE_PRESTIGE_START");
        var edited = fixture.service.editScalar(OWNER, draft, "requirements.requirements.play.completion",
                "LATCHED");
        var compilation = new net.maddkraft.maddprestige.core.config.progression.ProgressionConfigurationCompiler()
                .compile(new CompiledConfiguration(RevisionHasher.hashDocuments(edited.documents()),
                        edited.documents()), Map.of());
        var requirement = compilation.configuration().requirements().get(new RequirementId("play"));

        assertFalse(compilation.validation().hasErrors(), compilation.validation().toString());
        assertEquals(net.maddkraft.maddprestige.core.requirement.MeasurementScope.SINCE_PRESTIGE_START,
                requirement.scope());
        assertEquals(net.maddkraft.maddprestige.core.requirement.CompletionMode.LATCHED,
                requirement.completionMode());
    }

    @Test
    @DisplayName("Canonical structured drafts create/edit/remove scaling and requirement trees")
    void structuredMutationsAreLosslessSchemaConfinedAndDraftOnly() {
        Fixture fixture = new Fixture();
        StoredConfigurationRevision active = fixture.applyInitial(defaultDocuments());
        UUID draft = fixture.service.beginDraft(OWNER, "gui");
        var firstSegment = new StructuredConfigurationValue(Map.of(
                "start-prestige", 1L,
                "end-prestige", "unlimited",
                "mode", "LINEAR",
                "base", new java.math.BigDecimal("10"),
                "rate", new java.math.BigDecimal("2")));

        var added = fixture.service.addStructuredObject(OWNER, draft,
                "prestige.cost-scaling.payment.segments", Optional.empty(), firstSegment);
        assertTrue(added.documents().get("lifecycle.yml").contains("start-prestige: 1"));
        assertTrue(added.documents().get("lifecycle.yml").contains("rate: 2"));

        var editedSegment = new StructuredConfigurationValue(Map.of(
                "start-prestige", 1L,
                "end-prestige", "unlimited",
                "mode", "FLAT",
                "base", new java.math.BigDecimal("25")));
        var edited = fixture.service.editStructuredObject(OWNER, draft,
                "prestige.cost-scaling.payment.segments", "0", editedSegment);
        assertTrue(edited.documents().get("lifecycle.yml").contains("base: 25"));
        assertFalse(edited.documents().get("lifecycle.yml").contains("rate: 2"));

        var nestedTree = new StructuredConfigurationValue(Map.of(
                "mode", "ALL",
                "children", List.of(Map.of(
                        "id", "provider_gate",
                        "mode", "ANY",
                        "children", List.of(Map.of("requirement", "play"))))));
        var treeAdded = fixture.service.addStructuredObject(OWNER, draft, "requirements.trees",
                Optional.of("prestige_gate"), nestedTree);
        assertTrue(treeAdded.documents().get("requirements.yml").contains("provider_gate"));

        var treeEdited = fixture.service.editStructuredObject(OWNER, draft, "requirements.trees",
                "prestige_gate", new StructuredConfigurationValue(
                        Map.of("mode", "X_OF_N", "threshold", new java.math.BigDecimal("1"),
                                "children", List.of(Map.of("requirement", "play")))));
        assertTrue(treeEdited.documents().get("requirements.yml").contains("mode: X_OF_N"));

        var cost = fixture.service.addStructuredObject(OWNER, draft, "requirements.costs",
                Optional.of("payment"), new StructuredConfigurationValue(Map.of(
                        "provider", "vault",
                        "type", "withdraw",
                        "value-type", "CURRENCY_AMOUNT",
                        "amount", "100",
                        "display-name", "Prestige payment",
                        "metadata", Map.of("currency", "vault"))));
        assertTrue(cost.documents().get("requirements.yml").contains("provider: vault"));

        var reward = fixture.service.addStructuredObject(OWNER, draft, "rewards.rewards",
                Optional.of("permission"), new StructuredConfigurationValue(Map.of(
                        "provider", "luckperms",
                        "type", "permission",
                        "value-type", "INTEGER_COUNT",
                        "value", "1",
                        "failure-policy", "OPTIONAL",
                        "repeatability", "REPEATABLE")));
        assertTrue(reward.documents().get("rewards.yml").contains("provider: luckperms"));

        var currency = fixture.service.addStructuredObject(OWNER, draft, "currencies", Optional.of("credits"),
                new StructuredConfigurationValue(Map.of(
                        "display-name", "Credits",
                        "symbol", "C",
                        "scale", 2,
                        "rounding-mode", "HALF_UP",
                        "maximum-precision", 18,
                        "maximum-balance", new BigDecimal("999999.99"),
                        "prestige-scoped", true)));
        assertTrue(currency.documents().get("lifecycle.yml").contains("maximum-balance: 999999.99"));

        var milestone = fixture.service.addStructuredObject(OWNER, draft, "milestones", Optional.of("first"),
                new StructuredConfigurationValue(Map.of(
                        "display-name", "First Prestige",
                        "enabled", true,
                        "trigger", "CURRENT_PRESTIGE",
                        "value-type", "INTEGER_COUNT",
                        "threshold", "1",
                        "repeatability", "ONCE",
                        "rewards", List.of("permission"))));
        assertTrue(milestone.documents().get("lifecycle.yml").contains("- permission"));

        assertThrows(AdministrationException.class, () -> fixture.service.addStructuredObject(OWNER, draft,
                "prestige.cost-scaling.payment.segments", Optional.empty(),
                new StructuredConfigurationValue(Map.of("not-canonical", "ignored"))));
        assertEquals(active.id(), fixture.canonical.active().orElseThrow().revisionId());

        var treeRemoved = fixture.service.removeStructuredObject(OWNER, draft, "requirements.trees",
                "prestige_gate");
        assertFalse(treeRemoved.documents().get("requirements.yml").contains("prestige_gate"));
        var segmentRemoved = fixture.service.removeStructuredObject(OWNER, draft,
                "prestige.cost-scaling.payment.segments", "0");
        assertTrue(segmentRemoved.documents().get("lifecycle.yml").contains("segments: []"));
        assertEquals(active.id(), fixture.canonical.active().orElseThrow().revisionId());
    }

    @Test
    @DisplayName("Guided Money edits one level override and cost atomically without YAML loss")
    void guidedMoneyDraftSynchronizesSchemaOwnedValuesLosslessly() {
        Fixture fixture = new Fixture();
        fixture.applyInitial(defaultDocuments());
        UUID draft = fixture.service.beginDraft(OWNER, "staff-gui:configuration:money");
        fixture.service.addStructuredObject(OWNER, draft, "requirements.requirements", Optional.of("money"),
                new StructuredConfigurationValue(Map.of(
                        "provider", "vault_balance",
                        "metric", "balance",
                        "value-type", "CURRENCY_AMOUNT",
                        "target", "2",
                        "scaling", Map.of("segments", List.of(Map.of(
                                "start-prestige", 1L,
                                "end-prestige", "unlimited",
                                "mode", "LINEAR",
                                "base", BigDecimal.ONE,
                                "rate", new BigDecimal("0.5"),
                                "overrides", Map.of("3", new BigDecimal("3"))))))));
        fixture.service.addStructuredObject(OWNER, draft, "requirements.requirements", Optional.of("skill"),
                new StructuredConfigurationValue(Map.of(
                        "provider", "mcmmo",
                        "metric", "total_level",
                        "value-type", "INTEGER_COUNT",
                        "target", "1")));
        fixture.service.addStructuredObject(OWNER, draft, "requirements.costs", Optional.of("money_cost"),
                new StructuredConfigurationValue(Map.of(
                        "provider", "vault_economy_cost",
                        "type", "vault_economy",
                        "value-type", "CURRENCY_AMOUNT",
                        "amount", "7")));

        var edited = fixture.service.editGuidedMoney(OWNER, draft,
                new ScalingOverrideEdit("requirements.requirements.money.scaling", 0, 6, "4"),
                guidedMoneyPair("1", "0.5", Map.of("3", new BigDecimal("3"))));
        String requirements = edited.documents().get("requirements.yml");
        String lifecycle = edited.documents().get("lifecycle.yml");

        assertTrue(requirements.contains("# Add requirement trees and independent costs"));
        assertTrue(requirements.contains("\"3\": 3"));
        assertTrue(requirements.contains("\"6\": 4"));
        assertTrue(requirements.contains("amount: \"2\""));
        assertTrue(requirements.contains("metric: total_level"));
        assertTrue(lifecycle.contains("cost-scaling:"));
        assertTrue(lifecycle.contains("money_cost:"));
        assertTrue(lifecycle.contains("\"6\": 4"));
        assertFalse(fixture.canonical.active().orElseThrow().compiled().documents().get("requirements.yml")
                .contains("money_cost"));
    }

    @Test
    @DisplayName("Guided Money review is draft-only and applies one audited revision exactly once")
    void guidedMoneyReviewAppliesOneRevisionWithoutProviderOrPlayerMutation() {
        ProviderRegistry providers = new ProviderRegistry();
        ConfigurationMetricProvider metric = new ConfigurationMetricProvider();
        ConfigurationCostProvider cost = new ConfigurationCostProvider();
        providers.activate(providers.register("test", metric));
        providers.activate(providers.register("test", cost));
        Fixture fixture = new Fixture(providers);
        StoredConfigurationRevision original = fixture.applyInitial(guidedMoneyDocuments());
        var guided = new CanonicalGuidedConfigurationAdministration(
                fixture.service, fixture.workflow::active, CLOCK);

        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L),
                guided.prestigeLevels(OWNER, 0, 7).levels());
        assertEquals("7", guided.prestigeLevel(OWNER, 6).moneyRequirement());
        var input = guided.moneyInput(OWNER, 6);
        assertEquals(original.id(), input.revision());
        assertEquals("7", input.currentValue());
        assertEquals("8.5", guided.validateMoneyInput(OWNER, 6, "8.500", original.id()));
        var review = guided.reviewMoney(OWNER, 6, "8").toCompletableFuture().join();

        assertEquals(original.id(), review.baseRevision());
        assertEquals("7", review.currentAmount());
        assertEquals("8", review.newAmount());
        assertEquals(original.id(), fixture.canonical.active().orElseThrow().revisionId());
        assertEquals(1, fixture.history.recent(10).size());
        assertEquals(0, metric.reads.get());
        assertEquals(0, cost.executions.get());

        var result = guided.confirmMoney(OWNER, review.reviewId()).toCompletableFuture().join();
        var active = fixture.canonical.active().orElseThrow();

        assertEquals(original.id(), result.previousRevision());
        assertEquals(active.revisionId(), result.newRevision());
        assertEquals("2", guided.prestigeLevel(OWNER, 1).moneyRequirement());
        assertEquals("2", guided.prestigeLevel(OWNER, 1).moneyCost());
        assertEquals("8", guided.prestigeLevel(OWNER, 6).moneyRequirement());
        assertEquals("8", guided.prestigeLevel(OWNER, 6).moneyCost());
        assertEquals(2, fixture.history.recent(10).size());
        StoredConfigurationRevision audit = fixture.history.recent(10).getFirst();
        assertEquals(OWNER.actor(), audit.actor());
        assertEquals("staff-gui:configuration:money", audit.sourceSurface());
        assertTrue(audit.reason().contains("Guided Money for Prestige 6: 7 -> 8"));
        assertTrue(active.compiled().documents().get("requirements.yml").contains("# owner Money note"));
        assertTrue(active.compiled().documents().get("requirements.yml").contains("custom-owner-key: keep"));
        assertTrue(active.compiled().documents().get("requirements.yml").contains("\"6\": 4"));
        assertEquals(0, metric.reads.get());
        assertEquals(0, cost.executions.get());
        assertEquals("config.gui.review.expired", assertThrows(AdministrationException.class,
                () -> guided.confirmMoney(OWNER, review.reviewId())).code());
        assertEquals(2, fixture.history.recent(10).size());
    }

    @Test
    @DisplayName("Guided Money authority rejects stale revision and requires edit plus apply")
    void guidedMoneyRejectsStaleRevisionAndNarrowPermissionFailures() {
        ProviderRegistry providers = new ProviderRegistry();
        providers.activate(providers.register("test", new ConfigurationMetricProvider()));
        providers.activate(providers.register("test", new ConfigurationCostProvider()));
        Fixture fixture = new Fixture(providers);
        fixture.applyInitial(guidedMoneyDocuments());
        var guided = new CanonicalGuidedConfigurationAdministration(
                fixture.service, fixture.workflow::active, CLOCK);
        PermissionSubject viewer = new PermissionSubject(OWNER.actor(), Set.of(AdministrationPermissions.CONFIG_VIEW));
        PermissionSubject editor = new PermissionSubject(OWNER.actor(), Set.of(
                AdministrationPermissions.CONFIG_VIEW, AdministrationPermissions.CONFIG_EDIT));

        assertEquals(7, guided.prestigeLevels(viewer, 0, 7).levels().size());
        assertEquals("permission.denied", assertThrows(AdministrationException.class,
                () -> guided.moneyAmounts(viewer, 6, 0, 7)).code());
        assertEquals("permission.denied", assertThrows(AdministrationException.class,
                () -> guided.moneyAmounts(editor, 6, 0, 7)).code());
        ConfigRevisionId currentRevision = fixture.canonical.active().orElseThrow().revisionId();
        for (String invalid : List.of("", "money", "-1", "0", "10001", "NaN", "Infinity", "1e2", "1.2.3")) {
            assertEquals("config.gui.money.invalid", assertThrows(AdministrationException.class,
                    () -> guided.validateMoneyInput(OWNER, 6, invalid, currentRevision)).code());
        }
        assertEquals("config.revision.stale", assertThrows(AdministrationException.class,
                () -> guided.validateMoneyInput(
                        OWNER, 6, "8", new ConfigRevisionId("not-active"))).code());

        var staleReview = guided.reviewMoney(OWNER, 6, "8").toCompletableFuture().join();
        ConfigRevisionId beforeCompetingEdit = fixture.canonical.active().orElseThrow().revisionId();
        UUID competingDraft = fixture.service.beginDraft(OWNER, "competing-editor");
        fixture.service.editScalar(OWNER, competingDraft, "prestige.cooldown", "PT2S");
        var competingPreview = fixture.service.preview(OWNER, competingDraft).toCompletableFuture().join();
        fixture.service.applyDraft(OWNER, competingDraft, Optional.of(beforeCompetingEdit),
                acknowledgements(competingPreview), "Competing safe edit").toCompletableFuture().join();

        assertEquals("config.revision.stale", assertThrows(AdministrationException.class,
                () -> guided.confirmMoney(OWNER, staleReview.reviewId())).code());
        assertEquals("7", guided.prestigeLevel(OWNER, 6).moneyRequirement());
        assertEquals("7", guided.prestigeLevel(OWNER, 6).moneyCost());
        assertEquals(2, fixture.history.recent(10).size());

        Fixture complex = new Fixture();
        complex.applyInitial(defaultDocuments());
        var complexGuided = new CanonicalGuidedConfigurationAdministration(
                complex.service, complex.workflow::active, CLOCK);
        assertFalse(complexGuided.prestigeLevel(OWNER, 1).moneyAvailable(),
                "unsupported structures remain inspectable instead of being simplified or crashing the hub");
    }

    @Test
    @DisplayName("Guided Money preview fails closed if either paired side diverges")
    void guidedMoneyPairRejectsDivergenceBeforeActivation() {
        Fixture fixture = new Fixture(guidedMoneyProviders());
        StoredConfigurationRevision original = fixture.applyInitial(guidedMoneyDocuments());
        UUID draft = fixture.service.beginDraft(OWNER, "staff-gui:configuration:money");
        fixture.service.editGuidedMoney(OWNER, draft,
                new ScalingOverrideEdit("requirements.requirements.money.scaling", 0, 6, "4"),
                guidedMoneyPair("1", "0.5", Map.of("3", new BigDecimal("3"))));
        fixture.service.editScalar(OWNER, draft, "requirements.costs.money_cost.amount", "3");

        CompletionException failure = assertThrows(CompletionException.class,
                () -> fixture.service.preview(OWNER, draft).toCompletableFuture().join());
        assertEquals("config.guided_money.invariant",
                ((AdministrationException) failure.getCause()).code());
        assertEquals(original.id(), fixture.canonical.active().orElseThrow().revisionId());
        assertEquals(1, fixture.history.recent(10).size());
    }

    @Test
    @DisplayName("Advanced configuration retains independent requirement and cost semantics")
    void genericConfigurationMayStillApplyIndependentMoneyValues() {
        Fixture fixture = new Fixture(guidedMoneyProviders());
        StoredConfigurationRevision original = fixture.applyInitial(guidedMoneyDocuments());
        UUID draft = fixture.service.beginDraft(OWNER, "command");
        fixture.service.editScalar(OWNER, draft, "requirements.costs.money_cost.amount", "8");
        var preview = fixture.service.preview(OWNER, draft).toCompletableFuture().join();

        assertFalse(preview.validation().hasErrors(), preview.validation().toString());
        fixture.service.applyDraft(OWNER, draft, Optional.of(original.id()), acknowledgements(preview),
                "Advanced independent cost").toCompletableFuture().join();

        var guided = new CanonicalGuidedConfigurationAdministration(
                fixture.service, fixture.workflow::active, CLOCK);
        assertEquals("7", guided.prestigeLevel(OWNER, 6).moneyRequirement());
        assertEquals("8", guided.prestigeLevel(OWNER, 6).moneyCost());
    }

    @Test
    @DisplayName("Guided Total Skill Level is count-bound, lossless, audited, and exactly once")
    void guidedTotalSkillLevelReviewPreservesRequirementTreeAndOtherConfiguration() {
        ProviderRegistry providers = new ProviderRegistry();
        ConfigurationMetricProvider metric = new ConfigurationMetricProvider();
        ConfigurationCostProvider cost = new ConfigurationCostProvider();
        ConfigurationRewardProvider reward = new ConfigurationRewardProvider();
        SetupProvider skill = new SetupProvider("mcmmo", "total_level",
                net.maddkraft.maddprestige.api.metric.MetricValueType.INTEGER);
        providers.activate(providers.register("test", metric));
        providers.activate(providers.register("test", cost));
        providers.activate(providers.register("test", reward));
        providers.activate(providers.register("test", skill));
        Fixture fixture = new Fixture(providers);
        StoredConfigurationRevision original = fixture.applyInitial(guidedTotalSkillLevelDocuments());
        var guided = new CanonicalGuidedConfigurationAdministration(
                fixture.service, fixture.workflow::active, CLOCK);

        var requirements = guided.requirements(OWNER, 6);
        var totalSkillLevel = requirements.requirements().stream()
                .filter(entry -> entry.kind() == GuidedRequirementConfigurationEntry.Kind.TOTAL_SKILL_LEVEL)
                .findFirst().orElseThrow();
        assertFalse(requirements.complex());
        assertEquals("1", totalSkillLevel.currentTarget());
        assertTrue(totalSkillLevel.editable());
        var money = requirements.requirements().stream()
                .filter(entry -> entry.kind() == GuidedRequirementConfigurationEntry.Kind.MONEY)
                .findFirst().orElseThrow();
        assertEquals("6", money.currentTarget());
        assertTrue(money.editable());
        assertEquals("6", guided.moneyInput(OWNER, 6).currentValue(),
                "the Money editor prefills the configured amount even when scaling changes the effective requirement");
        assertEquals("1", guided.totalSkillLevelInput(OWNER, 6).currentValue());
        assertEquals("0", guided.validateTotalSkillLevelInput(OWNER, 6, "000", original.id()));
        assertEquals("2", guided.validateTotalSkillLevelInput(OWNER, 6, "0002", original.id()));
        for (String invalid : List.of("", "-1", "1.2", "+1", "1e2", "level")) {
            assertEquals("config.gui.total_skill_level.invalid", assertThrows(AdministrationException.class,
                    () -> guided.validateTotalSkillLevelInput(OWNER, 6, invalid, original.id())).code());
        }

        var review = guided.reviewTotalSkillLevel(OWNER, 6, "2", original.id())
                .toCompletableFuture().join();
        assertEquals(original.id(), review.baseRevision());
        assertEquals("1", review.currentTarget());
        assertEquals("2", review.newTarget());
        assertEquals(original.id(), fixture.canonical.active().orElseThrow().revisionId());
        assertEquals(1, fixture.history.recent(10).size());
        assertEquals(0, metric.reads.get());
        assertEquals(0, cost.executions.get());

        var result = guided.confirmTotalSkillLevel(OWNER, review.reviewId()).toCompletableFuture().join();
        var active = fixture.canonical.active().orElseThrow();
        assertEquals(original.id(), result.previousRevision());
        assertEquals(active.revisionId(), result.newRevision());
        assertEquals("2", guided.requirements(OWNER, 6).requirements().stream()
                .filter(entry -> entry.kind() == GuidedRequirementConfigurationEntry.Kind.TOTAL_SKILL_LEVEL)
                .findFirst().orElseThrow().currentTarget());
        assertEquals(2, fixture.history.recent(10).size());
        StoredConfigurationRevision audit = fixture.history.recent(10).getFirst();
        assertEquals(OWNER.actor(), audit.actor());
        assertEquals("staff-gui:configuration:total-skill-level", audit.sourceSurface());
        assertTrue(audit.reason().contains("Guided Total Skill Level for Prestige 6: 1 -> 2"));
        String requirementYaml = active.compiled().documents().get("requirements.yml");
        assertTrue(requirementYaml.contains("# owner Total Skill Level note"));
        assertTrue(requirementYaml.contains("custom-skill-key: keep"));
        assertTrue(requirementYaml.contains("target: \"2\""));
        assertTrue(requirementYaml.contains("mode: ALL"));
        assertTrue(requirementYaml.contains("requirement: money"));
        assertTrue(requirementYaml.contains("requirement: skill"));
        assertTrue(requirementYaml.contains("base: 2"));
        assertTrue(requirementYaml.contains("rate: 1.25"));
        assertTrue(requirementYaml.contains("\"6\": 4"));
        assertTrue(requirementYaml.contains("amount: \"6\""));
        assertTrue(active.compiled().documents().get("rewards.yml").contains("value: \"2\""));
        assertEquals(0, metric.reads.get());
        assertEquals(0, cost.executions.get());
        assertEquals(0, reward.executions.get());
        assertEquals("config.gui.review.expired", assertThrows(AdministrationException.class,
                () -> guided.confirmTotalSkillLevel(OWNER, review.reviewId())).code());
        assertEquals(2, fixture.history.recent(10).size());
    }

    @Test
    @DisplayName("Nested Total Skill Level structures remain read-only and revisions fail closed")
    void guidedTotalSkillLevelRejectsComplexTreesAndStaleRevision() {
        ProviderRegistry providers = new ProviderRegistry();
        providers.activate(providers.register("test", new ConfigurationMetricProvider()));
        providers.activate(providers.register("test", new ConfigurationCostProvider()));
        providers.activate(providers.register("test", new ConfigurationRewardProvider()));
        providers.activate(providers.register("test", new SetupProvider("mcmmo", "total_level",
                net.maddkraft.maddprestige.api.metric.MetricValueType.INTEGER)));
        Fixture fixture = new Fixture(providers);
        StoredConfigurationRevision original = fixture.applyInitial(guidedTotalSkillLevelDocuments());
        var guided = new CanonicalGuidedConfigurationAdministration(
                fixture.service, fixture.workflow::active, CLOCK);

        assertEquals("config.revision.stale", assertThrows(AdministrationException.class,
                () -> guided.validateTotalSkillLevelInput(
                        OWNER, 6, "2", new ConfigRevisionId("not-active"))).code());
        assertEquals(original.id(), fixture.canonical.active().orElseThrow().revisionId());

        Fixture nestedFixture = new Fixture(providers);
        nestedFixture.applyInitial(complexTotalSkillLevelDocuments());
        var nested = new CanonicalGuidedConfigurationAdministration(
                nestedFixture.service, nestedFixture.workflow::active, CLOCK);
        var requirements = nested.requirements(OWNER, 6);
        assertTrue(requirements.complex());
        assertFalse(requirements.requirements().stream()
                .filter(entry -> entry.kind() == GuidedRequirementConfigurationEntry.Kind.TOTAL_SKILL_LEVEL)
                .findFirst().orElseThrow().editable());
        assertEquals("config.gui.total_skill_level.unsupported", assertThrows(AdministrationException.class,
                () -> nested.totalSkillLevelInput(OWNER, 6)).code());
        assertEquals(1, nestedFixture.history.recent(10).size());
    }

    @Test
    @DisplayName("Guided Linear increment uses canonical scaling and applies once without provider action")
    void guidedScalingReviewIsLosslessAuditedAndExactlyOnce() {
        ProviderRegistry providers = new ProviderRegistry();
        ConfigurationMetricProvider metric = new ConfigurationMetricProvider();
        ConfigurationCostProvider cost = new ConfigurationCostProvider();
        providers.activate(providers.register("test", metric));
        providers.activate(providers.register("test", cost));
        Fixture fixture = new Fixture(providers);
        StoredConfigurationRevision original = fixture.applyInitial(guidedMoneyDocuments());
        var guided = new CanonicalGuidedConfigurationAdministration(
                fixture.service, fixture.workflow::active, CLOCK);

        var scaling = guided.scaling(OWNER, 6);
        assertEquals(SegmentScalingMode.LINEAR, scaling.mode());
        assertEquals("1", scaling.base());
        assertEquals("0.5", scaling.rate());
        assertEquals("7", scaling.effectiveValue());
        assertEquals(Optional.empty(), scaling.overrideValue());
        assertEquals(Set.of(GuidedScalingParameter.LINEAR_BASE, GuidedScalingParameter.LINEAR_INCREMENT),
                scaling.editableParameters());
        assertFalse(scaling.complex());
        assertEquals("0.5", guided.scalingInput(
                OWNER, 6, GuidedScalingParameter.LINEAR_INCREMENT).currentValue());
        assertEquals("0", guided.validateScalingInput(OWNER, 6,
                GuidedScalingParameter.LINEAR_INCREMENT, "0.000", original.id()));
        assertEquals("1", guided.validateScalingInput(OWNER, 6,
                GuidedScalingParameter.LINEAR_INCREMENT, "1", original.id()));
        assertEquals("1.2", guided.validateScalingInput(OWNER, 6,
                GuidedScalingParameter.LINEAR_INCREMENT, "1.2", original.id()));
        assertEquals("1.25", guided.validateScalingInput(OWNER, 6,
                GuidedScalingParameter.LINEAR_INCREMENT, "1.25", original.id()));
        assertEquals("0.5", guided.validateScalingInput(OWNER, 6,
                GuidedScalingParameter.LINEAR_INCREMENT, "0.5", original.id()));
        assertEquals("0.55", guided.validateScalingInput(OWNER, 6,
                GuidedScalingParameter.LINEAR_INCREMENT, "0.55", original.id()));
        assertEquals("1.2", guided.validateScalingInput(OWNER, 6,
                GuidedScalingParameter.LINEAR_INCREMENT, "1.20", original.id()));

        var review = guided.reviewScaling(OWNER, 6, GuidedScalingParameter.LINEAR_INCREMENT,
                "1", original.id()).toCompletableFuture().join();
        assertEquals(original.id(), review.baseRevision());
        assertEquals("0.5", review.currentValue());
        assertEquals("1", review.newValue());
        assertEquals(original.id(), fixture.canonical.active().orElseThrow().revisionId());
        assertEquals(1, fixture.history.recent(10).size());
        assertEquals(0, metric.reads.get());
        assertEquals(0, cost.executions.get());

        var result = guided.confirmScaling(OWNER, review.reviewId()).toCompletableFuture().join();
        var active = fixture.canonical.active().orElseThrow();
        assertEquals(original.id(), result.previousRevision());
        assertEquals(active.revisionId(), result.newRevision());
        assertEquals("1", guided.scaling(OWNER, 6).rate());
        assertEquals("12", guided.scaling(OWNER, 6).effectiveValue());
        assertEquals("12", guided.prestigeLevel(OWNER, 6).moneyRequirement());
        assertEquals("12", guided.prestigeLevel(OWNER, 6).moneyCost());
        assertEquals(2, fixture.history.recent(10).size());
        StoredConfigurationRevision audit = fixture.history.recent(10).getFirst();
        assertEquals(OWNER.actor(), audit.actor());
        assertEquals("staff-gui:configuration:scaling", audit.sourceSurface());
        assertTrue(audit.reason().contains("Guided Linear increment for Prestige 6: 0.5 -> 1"));
        String requirements = active.compiled().documents().get("requirements.yml");
        assertTrue(requirements.contains("# owner Money note"));
        assertTrue(requirements.contains("custom-owner-key: keep"));
        assertTrue(requirements.contains("rate: 1"));
        assertTrue(requirements.contains("\"3\": 3"));
        assertTrue(requirements.contains("amount: \"2\""));
        assertEquals(0, metric.reads.get());
        assertEquals(0, cost.executions.get());
        assertEquals("config.gui.review.expired", assertThrows(AdministrationException.class,
                () -> guided.confirmScaling(OWNER, review.reviewId())).code());
        assertEquals(2, fixture.history.recent(10).size());
    }

    @Test
    @DisplayName("Guided Linear base is exact, lossless, override-aware, and exactly once")
    void guidedLinearBaseReviewPreservesIncrementOverrideAndCanonicalEffectiveValue() {
        ProviderRegistry providers = new ProviderRegistry();
        ConfigurationMetricProvider metric = new ConfigurationMetricProvider();
        ConfigurationCostProvider cost = new ConfigurationCostProvider();
        providers.activate(providers.register("test", metric));
        providers.activate(providers.register("test", cost));
        Fixture fixture = new Fixture(providers);
        StoredConfigurationRevision original = fixture.applyInitial(guidedBaseOverrideDocuments());
        var guided = new CanonicalGuidedConfigurationAdministration(
                fixture.service, fixture.workflow::active, CLOCK);

        var before = guided.scaling(OWNER, 6);
        assertEquals(SegmentScalingMode.LINEAR, before.mode());
        assertEquals("1", before.base());
        assertEquals("1.25", before.rate());
        assertEquals(Optional.of("3"), before.overrideValue());
        assertEquals("6", before.effectiveValue());
        assertEquals(Set.of(GuidedScalingParameter.LINEAR_BASE, GuidedScalingParameter.LINEAR_INCREMENT),
                before.editableParameters());
        assertEquals("1", guided.scalingInput(OWNER, 6, GuidedScalingParameter.LINEAR_BASE).currentValue());
        assertEquals("0", guided.validateScalingInput(
                OWNER, 6, GuidedScalingParameter.LINEAR_BASE, "0.000", original.id()));
        assertEquals("2", guided.validateScalingInput(
                OWNER, 6, GuidedScalingParameter.LINEAR_BASE, "2", original.id()));
        assertEquals("1.25", guided.validateScalingInput(
                OWNER, 6, GuidedScalingParameter.LINEAR_BASE, "1.250", original.id()));
        assertEquals("config.revision.stale", assertThrows(AdministrationException.class,
                () -> guided.validateScalingInput(OWNER, 6, GuidedScalingParameter.LINEAR_BASE,
                        "2", new ConfigRevisionId("not-active"))).code());

        var review = guided.reviewScaling(OWNER, 6, GuidedScalingParameter.LINEAR_BASE,
                "2", original.id()).toCompletableFuture().join();
        assertEquals(GuidedScalingParameter.LINEAR_BASE, review.parameter());
        assertEquals("1", review.currentValue());
        assertEquals("2", review.newValue());
        assertEquals(original.id(), fixture.canonical.active().orElseThrow().revisionId());
        assertEquals(1, fixture.history.recent(10).size());
        assertEquals(0, metric.reads.get());
        assertEquals(0, cost.executions.get());

        var result = guided.confirmScaling(OWNER, review.reviewId()).toCompletableFuture().join();
        var active = fixture.canonical.active().orElseThrow();
        var after = guided.scaling(OWNER, 6);
        assertEquals(GuidedScalingParameter.LINEAR_BASE, result.parameter());
        assertEquals(original.id(), result.previousRevision());
        assertEquals(active.revisionId(), result.newRevision());
        assertEquals("2", after.base());
        assertEquals("1.25", after.rate());
        assertEquals(Optional.of("3"), after.overrideValue());
        assertEquals("6", after.effectiveValue(), "the explicit P6 override remains authoritative");
        assertEquals("4", guided.prestigeLevel(OWNER, 1).moneyRequirement());
        assertEquals("4", guided.prestigeLevel(OWNER, 1).moneyCost());
        assertEquals("6", guided.prestigeLevel(OWNER, 6).moneyRequirement());
        assertEquals("6", guided.prestigeLevel(OWNER, 6).moneyCost());
        assertEquals(2, fixture.history.recent(10).size());
        StoredConfigurationRevision audit = fixture.history.recent(10).getFirst();
        assertEquals(OWNER.actor(), audit.actor());
        assertEquals("staff-gui:configuration:scaling", audit.sourceSurface());
        assertTrue(audit.reason().contains("Guided Linear base for Prestige 6: 1 -> 2"));
        String requirements = active.compiled().documents().get("requirements.yml");
        assertTrue(requirements.contains("# owner Money note"));
        assertTrue(requirements.contains("custom-owner-key: keep"));
        assertTrue(requirements.contains("base: 2"));
        assertTrue(requirements.contains("rate: 1.25"));
        assertTrue(requirements.contains("\"6\": 3"));
        assertTrue(requirements.contains("amount: \"2\""));
        assertEquals(0, metric.reads.get());
        assertEquals(0, cost.executions.get());
        assertEquals("config.gui.review.expired", assertThrows(AdministrationException.class,
                () -> guided.confirmScaling(OWNER, review.reviewId())).code());
        assertEquals(2, fixture.history.recent(10).size());
    }
    @Test
    @DisplayName("Guided Prestige Override edit is exact, lossless, audited, and exactly once")
    void guidedScalingOverrideEditUsesCanonicalEffectiveValueAndPreservesConfiguration() {
        ProviderRegistry providers = new ProviderRegistry();
        ConfigurationMetricProvider metric = new ConfigurationMetricProvider();
        ConfigurationCostProvider cost = new ConfigurationCostProvider();
        providers.activate(providers.register("test", metric));
        providers.activate(providers.register("test", cost));
        providers.activate(providers.register("test", new ConfigurationRewardProvider()));
        Fixture fixture = new Fixture(providers);
        StoredConfigurationRevision original = fixture.applyInitial(guidedOverrideDocuments());
        var guided = new CanonicalGuidedConfigurationAdministration(
                fixture.service, fixture.workflow::active, CLOCK);

        var before = guided.scaling(OWNER, 6);
        assertEquals(SegmentScalingMode.LINEAR, before.mode());
        assertEquals("2", before.base());
        assertEquals("1.25", before.rate());
        assertEquals(Optional.of("3"), before.overrideValue());
        assertEquals("6", before.effectiveValue());
        assertTrue(before.overrideManageable());
        assertEquals("3", guided.scalingOverrideInput(OWNER, 6).currentValue());
        assertEquals("0", guided.validateScalingOverrideInput(OWNER, 6, "0.000", original.id()));
        assertEquals("1", guided.validateScalingOverrideInput(OWNER, 6, "1", original.id()));
        assertEquals("1.2", guided.validateScalingOverrideInput(OWNER, 6, "1.2", original.id()));
        assertEquals("1.25", guided.validateScalingOverrideInput(OWNER, 6, "1.25", original.id()));
        assertEquals("0.5", guided.validateScalingOverrideInput(OWNER, 6, "0.5", original.id()));
        assertEquals("0.55", guided.validateScalingOverrideInput(OWNER, 6, "0.55", original.id()));
        assertEquals("1.2", guided.validateScalingOverrideInput(OWNER, 6, "1.20", original.id()));
        for (String invalid : List.of("", "-1", "NaN", "Infinity", "1e2", "1.2.3",
                "1" + "0".repeat(102), "0." + "1".repeat(257))) {
            assertEquals("config.gui.scaling.override.invalid", assertThrows(AdministrationException.class,
                    () -> guided.validateScalingOverrideInput(OWNER, 6, invalid, original.id())).code());
        }

        var review = guided.reviewScalingOverride(OWNER, 6, "4", original.id())
                .toCompletableFuture().join();
        assertEquals(original.id(), review.baseRevision());
        assertEquals(Optional.of("3"), review.currentOverride());
        assertEquals(Optional.of("4"), review.newOverride());
        assertEquals(Optional.of("6"), review.currentEffectiveValue());
        assertEquals(Optional.of("8"), review.newEffectiveValue());
        assertFalse(review.removal());
        assertEquals(original.id(), fixture.canonical.active().orElseThrow().revisionId());
        assertEquals(1, fixture.history.recent(10).size());
        assertEquals(0, metric.reads.get());
        assertEquals(0, cost.executions.get());
        PermissionSubject otherStaff = new PermissionSubject(
                new Actor("player", Optional.of(UUID.fromString("99999999-9999-4999-8999-999999999999")),
                        "Other Staff"),
                AdministrationPermissions.all());
        assertEquals("config.gui.review.actor_mismatch", assertThrows(AdministrationException.class,
                () -> guided.confirmScalingOverride(otherStaff, review.reviewId())).code());
        assertEquals(1, fixture.history.recent(10).size());

        var result = guided.confirmScalingOverride(OWNER, review.reviewId()).toCompletableFuture().join();
        var active = fixture.canonical.active().orElseThrow();
        var after = guided.scaling(OWNER, 6);
        assertEquals(original.id(), result.previousRevision());
        assertEquals(active.revisionId(), result.newRevision());
        assertEquals(Optional.of("3"), result.previousOverride());
        assertEquals(Optional.of("4"), result.newOverride());
        assertEquals("2", after.base());
        assertEquals("1.25", after.rate());
        assertEquals(Optional.of("4"), after.overrideValue());
        assertEquals("8", after.effectiveValue());
        assertEquals("4", guided.prestigeLevel(OWNER, 1).moneyRequirement());
        assertEquals("4", guided.prestigeLevel(OWNER, 1).moneyCost());
        assertEquals("8", guided.prestigeLevel(OWNER, 6).moneyRequirement());
        assertEquals("8", guided.prestigeLevel(OWNER, 6).moneyCost());
        assertEquals(2, fixture.history.recent(10).size());
        StoredConfigurationRevision audit = fixture.history.recent(10).getFirst();
        assertEquals(OWNER.actor(), audit.actor());
        assertEquals("staff-gui:configuration:scaling-override-edit", audit.sourceSurface());
        assertTrue(audit.reason().contains("Guided Scaling Override edit for Prestige 6: 3 -> 4"));
        String requirements = active.compiled().documents().get("requirements.yml");
        String rewards = active.compiled().documents().get("rewards.yml");
        String lifecycle = active.compiled().documents().get("lifecycle.yml");
        assertTrue(requirements.contains("# owner Money note"));
        assertTrue(requirements.contains("custom-owner-key: keep"));
        assertTrue(requirements.contains("base: 2"));
        assertTrue(requirements.contains("rate: 1.25"));
        assertTrue(requirements.contains("\"3\": 3"));
        assertTrue(requirements.contains("\"6\": 4"));
        assertTrue(requirements.contains("amount: \"2\""));
        assertTrue(rewards.contains("# owner Reward note"));
        assertTrue(rewards.contains("value: \"2\""));
        assertTrue(lifecycle.contains("# owner lifecycle note"));
        assertTrue(lifecycle.contains("custom-owner-key: keep"));
        assertTrue(lifecycle.contains("cost-scaling:"));
        assertTrue(lifecycle.contains("money_cost:"));
        assertTrue(lifecycle.contains("\"6\": 4"));
        assertEquals(0, metric.reads.get());
        assertEquals(0, cost.executions.get());
        assertEquals("config.gui.review.expired", assertThrows(AdministrationException.class,
                () -> guided.confirmScalingOverride(OWNER, review.reviewId())).code());
        assertEquals(2, fixture.history.recent(10).size());
    }

    @Test
    @DisplayName("Guided Prestige Override removal reveals inherited value without YAML loss")
    void guidedScalingOverrideRemovalIsStructuralAuditedAndExactlyOnce() {
        ProviderRegistry providers = guidedMoneyProviders();
        providers.activate(providers.register("test", new ConfigurationRewardProvider()));
        Fixture fixture = new Fixture(providers);
        StoredConfigurationRevision original = fixture.applyInitial(guidedOverrideDocuments());
        var guided = new CanonicalGuidedConfigurationAdministration(
                fixture.service, fixture.workflow::active, CLOCK);

        var review = guided.reviewScalingOverrideRemoval(OWNER, 6, original.id())
                .toCompletableFuture().join();
        assertTrue(review.removal());
        assertEquals(Optional.of("3"), review.currentOverride());
        assertEquals(Optional.empty(), review.newOverride());
        assertEquals(Optional.of("6"), review.currentEffectiveValue());
        assertEquals(Optional.of("16.5"), review.newEffectiveValue());
        assertEquals(original.id(), fixture.canonical.active().orElseThrow().revisionId());
        assertEquals(1, fixture.history.recent(10).size());

        var result = guided.confirmScalingOverride(OWNER, review.reviewId()).toCompletableFuture().join();
        var active = fixture.canonical.active().orElseThrow();
        var after = guided.scaling(OWNER, 6);
        assertTrue(result.removal());
        assertEquals(original.id(), result.previousRevision());
        assertEquals(active.revisionId(), result.newRevision());
        assertEquals("2", after.base());
        assertEquals("1.25", after.rate());
        assertEquals(Optional.empty(), after.overrideValue());
        assertEquals("16.5", after.effectiveValue());
        assertEquals("16.5", guided.prestigeLevel(OWNER, 6).moneyRequirement());
        assertEquals("16.5", guided.prestigeLevel(OWNER, 6).moneyCost());
        assertTrue(after.overrideManageable());
        assertEquals(2, fixture.history.recent(10).size());
        StoredConfigurationRevision audit = fixture.history.recent(10).getFirst();
        assertEquals(OWNER.actor(), audit.actor());
        assertEquals("staff-gui:configuration:scaling-override-removal", audit.sourceSurface());
        assertTrue(audit.reason().contains("Guided Scaling Override removal for Prestige 6: 3 -> inherited"));
        String requirements = active.compiled().documents().get("requirements.yml");
        String rewards = active.compiled().documents().get("rewards.yml");
        assertTrue(requirements.contains("# owner Money note"));
        assertTrue(requirements.contains("custom-owner-key: keep"));
        assertTrue(requirements.contains("base: 2"));
        assertTrue(requirements.contains("rate: 1.25"));
        assertTrue(requirements.contains("\"3\": 3"));
        assertFalse(requirements.contains("\"6\":"));
        assertTrue(requirements.contains("amount: \"2\""));
        assertTrue(rewards.contains("# owner Reward note"));
        assertTrue(rewards.contains("value: \"2\""));
        assertEquals("config.gui.review.expired", assertThrows(AdministrationException.class,
                () -> guided.confirmScalingOverride(OWNER, review.reviewId())).code());
        assertEquals(2, fixture.history.recent(10).size());

        var inherited = guided.scaling(OWNER, 6);
        assertTrue(inherited.overrideManageable());
        assertEquals(Optional.empty(), inherited.overrideValue());
        assertEquals("16.5", guided.scalingOverrideInput(OWNER, 6).currentValue());

        var addReview = guided.reviewScalingOverride(OWNER, 6, "4", active.revisionId())
                .toCompletableFuture().join();
        assertTrue(addReview.addition());
        assertFalse(addReview.removal());
        assertEquals(Optional.empty(), addReview.currentOverride());
        assertEquals(Optional.of("4"), addReview.newOverride());
        assertEquals(Optional.of("16.5"), addReview.currentEffectiveValue());
        assertEquals(Optional.of("8"), addReview.newEffectiveValue());
        assertEquals(active.revisionId(), fixture.canonical.active().orElseThrow().revisionId());
        assertEquals(2, fixture.history.recent(10).size());

        var addResult = guided.confirmScalingOverride(OWNER, addReview.reviewId()).toCompletableFuture().join();
        var addedActive = fixture.canonical.active().orElseThrow();
        var readded = guided.scaling(OWNER, 6);
        assertTrue(addResult.addition());
        assertEquals(Optional.empty(), addResult.previousOverride());
        assertEquals(Optional.of("4"), addResult.newOverride());
        assertEquals(Optional.of("4"), readded.overrideValue());
        assertEquals("8", readded.effectiveValue());
        assertEquals("8", guided.prestigeLevel(OWNER, 6).moneyRequirement());
        assertEquals("8", guided.prestigeLevel(OWNER, 6).moneyCost());
        assertTrue(readded.overrideManageable());
        assertEquals(3, fixture.history.recent(10).size());
        StoredConfigurationRevision addAudit = fixture.history.recent(10).getFirst();
        assertEquals(OWNER.actor(), addAudit.actor());
        assertEquals("staff-gui:configuration:scaling-override-add", addAudit.sourceSurface());
        assertTrue(addAudit.reason().contains(
                "Guided Scaling Override addition for Prestige 6: inherited -> 4"));
        String addedRequirements = addedActive.compiled().documents().get("requirements.yml");
        assertTrue(addedRequirements.contains("# owner Money note"));
        assertTrue(addedRequirements.contains("custom-owner-key: keep"));
        assertTrue(addedRequirements.contains("base: 2"));
        assertTrue(addedRequirements.contains("rate: 1.25"));
        assertTrue(addedRequirements.contains("\"3\": 3"));
        assertTrue(addedRequirements.contains("\"6\": 4"));
        assertTrue(addedRequirements.contains("amount: \"2\""));
        assertTrue(addedActive.compiled().documents().get("rewards.yml").contains("value: \"2\""));
        assertEquals("config.gui.review.expired", assertThrows(AdministrationException.class,
                () -> guided.confirmScalingOverride(OWNER, addReview.reviewId())).code());
        assertEquals(3, fixture.history.recent(10).size());
    }

    @Test
    @DisplayName("Scaling validation is field-specific and complex/manual structures stay read-only")
    void guidedScalingRejectsInvalidStaleAndComplexEditsWithoutYamlLoss() {
        ProviderRegistry providers = guidedMoneyProviders();
        Fixture fixture = new Fixture(providers);
        StoredConfigurationRevision original = fixture.applyInitial(guidedMoneyDocuments());
        var guided = new CanonicalGuidedConfigurationAdministration(
                fixture.service, fixture.workflow::active, CLOCK);
        for (String invalid : List.of("", "-1", "NaN", "Infinity", "1e2", "1.2.3",
                "1" + "0".repeat(102), "0." + "1".repeat(257))) {
            assertEquals("config.gui.scaling.invalid", assertThrows(AdministrationException.class,
                    () -> guided.validateScalingInput(OWNER, 6, GuidedScalingParameter.LINEAR_INCREMENT,
                            invalid, original.id())).code());
        }
        assertEquals("config.revision.stale", assertThrows(AdministrationException.class,
                () -> guided.validateScalingInput(OWNER, 6, GuidedScalingParameter.LINEAR_INCREMENT,
                        "1", new ConfigRevisionId("not-active"))).code());

        for (Map<String, String> documents : List.of(segmentedContinueDocuments(), manualScalingDocuments())) {
            Fixture readOnly = new Fixture(guidedMoneyProviders());
            StoredConfigurationRevision active = readOnly.applyInitial(documents);
            String before = active.compiled().documents().get("requirements.yml");
            var complex = new CanonicalGuidedConfigurationAdministration(
                    readOnly.service, readOnly.workflow::active, CLOCK);
            var view = complex.scaling(OWNER, 6);
            assertTrue(view.complex());
            assertTrue(view.editableParameters().isEmpty());
            assertFalse(view.overrideManageable());
            assertEquals("config.gui.scaling.unsupported", assertThrows(AdministrationException.class,
                    () -> complex.scalingInput(OWNER, 6, GuidedScalingParameter.LINEAR_INCREMENT)).code());
            assertEquals("config.gui.scaling.unsupported", assertThrows(AdministrationException.class,
                    () -> complex.scalingOverrideInput(OWNER, 6)).code());
            assertEquals("config.gui.scaling.unsupported", assertThrows(AdministrationException.class,
                    () -> complex.reviewScalingOverrideRemoval(OWNER, 6, active.id())).code());
            assertEquals(active.id(), readOnly.canonical.active().orElseThrow().revisionId());
            assertEquals(before, readOnly.canonical.active().orElseThrow().compiled()
                    .documents().get("requirements.yml"));
        }
    }

    private static ProviderRegistry guidedMoneyProviders() {
        ProviderRegistry providers = new ProviderRegistry();
        providers.activate(providers.register("test", new ConfigurationMetricProvider()));
        providers.activate(providers.register("test", new ConfigurationCostProvider()));
        return providers;
    }

    @Test
    @DisplayName("Guided Reward review applies one lossless audited revision without provider action")
    void guidedRewardReviewAppliesOneRevisionWithoutProviderOrPlayerMutation() {
        ProviderRegistry providers = new ProviderRegistry();
        ConfigurationMetricProvider metric = new ConfigurationMetricProvider();
        ConfigurationCostProvider cost = new ConfigurationCostProvider();
        ConfigurationRewardProvider reward = new ConfigurationRewardProvider();
        providers.activate(providers.register("test", metric));
        providers.activate(providers.register("test", cost));
        providers.activate(providers.register("test", reward));
        Fixture fixture = new Fixture(providers);
        StoredConfigurationRevision original = fixture.applyInitial(guidedRewardDocuments());
        var guided = new CanonicalGuidedConfigurationAdministration(
                fixture.service, fixture.workflow::active, CLOCK);

        var level = guided.prestigeLevel(OWNER, 6);
        assertTrue(level.rewardAvailable());
        assertTrue(level.rewardEditable());
        assertEquals("1", level.rewardAmount());
        var input = guided.rewardInput(OWNER, 6);
        assertEquals(original.id(), input.revision());
        assertEquals("1", input.currentValue());
        assertEquals("2.5", guided.validateRewardInput(OWNER, 6, "2.500", original.id()));
        var review = guided.reviewReward(OWNER, 6, "2").toCompletableFuture().join();

        assertEquals(original.id(), review.baseRevision());
        assertEquals("1", review.currentAmount());
        assertEquals("2", review.newAmount());
        assertEquals(original.id(), fixture.canonical.active().orElseThrow().revisionId());
        assertEquals(1, fixture.history.recent(10).size());
        assertEquals(0, metric.reads.get());
        assertEquals(0, cost.executions.get());
        assertEquals(0, reward.executions.get());

        var result = guided.confirmReward(OWNER, review.reviewId()).toCompletableFuture().join();
        var active = fixture.canonical.active().orElseThrow();

        assertEquals(original.id(), result.previousRevision());
        assertEquals(active.revisionId(), result.newRevision());
        assertEquals("2", guided.prestigeLevel(OWNER, 6).rewardAmount());
        assertEquals(2, fixture.history.recent(10).size());
        StoredConfigurationRevision audit = fixture.history.recent(10).getFirst();
        assertEquals(OWNER.actor(), audit.actor());
        assertEquals("staff-gui:configuration:reward", audit.sourceSurface());
        assertTrue(audit.reason().contains("Guided Reward for Prestige 6: 1 -> 2"));
        String rewards = active.compiled().documents().get("rewards.yml");
        assertTrue(rewards.contains("# owner Reward note"));
        assertTrue(rewards.contains("custom-owner-key: keep"));
        assertTrue(rewards.contains("value: \"2\""));
        assertEquals(0, metric.reads.get());
        assertEquals(0, cost.executions.get());
        assertEquals(0, reward.executions.get());
        assertEquals("config.gui.review.expired", assertThrows(AdministrationException.class,
                () -> guided.confirmReward(OWNER, review.reviewId())).code());
        assertEquals(2, fixture.history.recent(10).size());
    }

    @Test
    @DisplayName("Guided Reward rejects invalid, stale, unauthorized, and complex edits fail closed")
    void guidedRewardRejectsInvalidStaleUnauthorizedAndComplexChanges() {
        ProviderRegistry providers = new ProviderRegistry();
        providers.activate(providers.register("test", new ConfigurationMetricProvider()));
        providers.activate(providers.register("test", new ConfigurationCostProvider()));
        providers.activate(providers.register("test", new ConfigurationRewardProvider()));
        Fixture fixture = new Fixture(providers);
        fixture.applyInitial(guidedRewardDocuments());
        var guided = new CanonicalGuidedConfigurationAdministration(
                fixture.service, fixture.workflow::active, CLOCK);
        PermissionSubject viewer = new PermissionSubject(OWNER.actor(), Set.of(AdministrationPermissions.CONFIG_VIEW));

        assertEquals("permission.denied", assertThrows(AdministrationException.class,
                () -> guided.rewardAmounts(viewer, 6, 0, 7)).code());
        ConfigRevisionId currentRevision = fixture.canonical.active().orElseThrow().revisionId();
        for (String invalid : List.of("", "reward", "-1", "0", "10001", "NaN", "Infinity", "1e2", "1.2.3")) {
            assertEquals("config.gui.reward.invalid", assertThrows(AdministrationException.class,
                    () -> guided.validateRewardInput(OWNER, 6, invalid, currentRevision)).code());
        }
        assertEquals("config.revision.stale", assertThrows(AdministrationException.class,
                () -> guided.validateRewardInput(
                        OWNER, 6, "2", new ConfigRevisionId("not-active"))).code());
        var staleReview = guided.reviewReward(OWNER, 6, "2").toCompletableFuture().join();
        ConfigRevisionId beforeCompetingEdit = fixture.canonical.active().orElseThrow().revisionId();
        UUID competingDraft = fixture.service.beginDraft(OWNER, "competing-editor");
        fixture.service.editScalar(OWNER, competingDraft, "prestige.cooldown", "PT2S");
        var competingPreview = fixture.service.preview(OWNER, competingDraft).toCompletableFuture().join();
        fixture.service.applyDraft(OWNER, competingDraft, Optional.of(beforeCompetingEdit),
                acknowledgements(competingPreview), "Competing safe edit").toCompletableFuture().join();

        assertEquals("config.revision.stale", assertThrows(AdministrationException.class,
                () -> guided.confirmReward(OWNER, staleReview.reviewId())).code());
        assertEquals("1", guided.prestigeLevel(OWNER, 6).rewardAmount());

        Fixture complex = new Fixture(providers);
        complex.applyInitial(complexRewardDocuments());
        var complexGuided = new CanonicalGuidedConfigurationAdministration(
                complex.service, complex.workflow::active, CLOCK);
        var complexLevel = complexGuided.prestigeLevel(OWNER, 6);
        assertTrue(complexLevel.rewardAvailable());
        assertFalse(complexLevel.rewardEditable());
        assertEquals("config.gui.reward.unsupported", assertThrows(AdministrationException.class,
                () -> complexGuided.rewardAmounts(OWNER, 6, 0, 7)).code());
        assertEquals("config.gui.reward.unsupported", assertThrows(AdministrationException.class,
                () -> complexGuided.rewardInput(OWNER, 6)).code());
    }

    @Test
    @DisplayName("Omitted reset defaults remain editable through the canonical draft surface")
    void materializesOmittedResetDispositionOnExplicitEdit() {
        Fixture fixture = new Fixture();
        fixture.applyInitial(defaultDocuments());
        String before = fixture.canonical.active().orElseThrow().compiled().documents().get("lifecycle.yml");
        UUID draft = fixture.service.beginDraft(OWNER, "command");

        var edited = fixture.service.editScalar(OWNER, draft,
                "prestige.reset-policy.purchased-perks", "RESET");

        assertEquals(before + """
                prestige:
                  reset-policy:
                    purchased-perks: RESET
                """, edited.documents().get("lifecycle.yml"));
        assertEquals(before, fixture.canonical.active().orElseThrow().compiled().documents().get("lifecycle.yml"));
    }

    @Test
    @DisplayName("[A40] Active requirement with absent provider/metric blocks apply actionably")
    void invalidProviderMetricCannotApply() {
        Fixture fixture = new Fixture();
        Map<String, String> documents = new LinkedHashMap<>(defaultDocuments());
        documents.put("progression.yml", activeProgression());
        documents.put("requirements.yml", activeRequirements());
        UUID draft = fixture.service.beginInitialDraft(OWNER, documents, "setup-wizard");

        var preview = fixture.service.preview(OWNER, draft).toCompletableFuture().join();

        assertTrue(preview.validation().hasErrors());
        assertTrue(preview.validation().findings().stream().anyMatch(finding ->
                finding.code().equals("progression.configuration.provider.unavailable")
                        && finding.path().equals("providers.absent_provider")));
        AdministrationException acknowledgementFailure = assertThrows(AdministrationException.class,
                () -> fixture.service.prepareAcknowledgement(OWNER, draft));
        assertEquals(Optional.of(AdministrationSemanticVariant.CONFIG_VALIDATION_ACKNOWLEDGEMENT_PREPARATION),
                acknowledgementFailure.semanticVariant());
        assertTrue(Long.parseLong(acknowledgementFailure.facts().get("errors")) > 0);
        CompletionException wrapped = assertThrows(CompletionException.class, () -> fixture.service
                .applySetup(OWNER, draft, Set.of(), "Invalid provider test")
                .toCompletableFuture().join());
        AdministrationException failure = (AdministrationException) wrapped.getCause();
        assertEquals("config.validation.blocked", failure.code());
        assertEquals(Optional.of(AdministrationSemanticVariant.CONFIG_VALIDATION_APPLY),
                failure.semanticVariant());
        assertTrue(Long.parseLong(failure.facts().get("errors")) > 0);
        assertTrue(fixture.canonical.active().isEmpty());

        Fixture highRisk = new Fixture();
        StoredConfigurationRevision first = highRisk.applyInitial(defaultDocuments());
        UUID riskyDraft = highRisk.service.beginDraft(OWNER, "command");
        highRisk.service.addStage(OWNER, riskyDraft, new StageId("member"), "Member",
                Optional.empty(), Optional.empty());
        var riskyPreview = highRisk.service.preview(OWNER, riskyDraft).toCompletableFuture().join();
        assertFalse(riskyPreview.validation().hasErrors());
        assertFalse(riskyPreview.validation().canApply(Set.of()));
        AdministrationException unacknowledged = failure(highRisk.service.applyDraft(OWNER, riskyDraft,
                Optional.of(first.id()), Set.of(), "Unacknowledged high risk"));
        assertEquals(Optional.of(AdministrationSemanticVariant.CONFIG_VALIDATION_APPLY),
                unacknowledged.semanticVariant());
        assertEquals("0", unacknowledged.facts().get("errors"));
        assertTrue(Long.parseLong(unacknowledged.facts().get("findings")) > 0);
    }

    @Test
    @DisplayName("[A68][OR8D-09] List and map paths are supported while scalar listing is rejected accurately")
    void configurationCollectionListingSupportsListAndMap() {
        Fixture fixture = new Fixture();
        fixture.applyInitial(defaultDocuments());
        UUID draft = fixture.service.beginDraft(OWNER, "collection-semantics");

        assertEquals(List.of(), fixture.service.listValues(OWNER, draft, "progression.order"));
        assertEquals(List.of(), fixture.service.listValues(OWNER, draft, "requirements.requirements"));
        AdministrationException scalar = assertThrows(AdministrationException.class,
                () -> fixture.service.listValues(OWNER, draft, "prestige.enabled"));

        assertEquals("config.path.not_listable", scalar.code());
        assertEquals(Map.of("path", "prestige.enabled"), scalar.facts());
    }

    @Test
    @DisplayName("[A68][OR8D-11] Draft hash and version drift invalidate only the retained preview")
    void draftHashAndVersionDriftAreRejectedAsPreviewStaleness() {
        Fixture hashFixture = new Fixture();
        StoredConfigurationRevision hashBase = hashFixture.applyInitial(defaultDocuments());
        UUID hashDraft = hashFixture.service.beginDraft(OWNER, "preview-hash-stale");
        hashFixture.service.preview(OWNER, hashDraft).toCompletableFuture().join();
        net.maddkraft.maddprestige.core.config.ConfigDraft retained =
                (net.maddkraft.maddprestige.core.config.ConfigDraft) draftStateComponent(
                        hashFixture.service, hashDraft, "draft");
        LinkedHashMap<String, String> changedDocuments = new LinkedHashMap<>(retained.documents());
        changedDocuments.compute("lifecycle.yml", (ignored, content) -> content + "\n# changed after preview\n");
        var changedDraft = new net.maddkraft.maddprestige.core.config.ConfigDraft(
                retained.draftId(), retained.baseRevision(), changedDocuments, retained.actor(), retained.createdAt());
        replaceDraftStateComponents(hashFixture.service, hashDraft, Map.<String, Object>of("draft", changedDraft));

        AdministrationException hashStale = assertThrows(AdministrationException.class, () ->
                hashFixture.service.applyDraft(OWNER, hashDraft, Optional.of(hashBase.id()), Set.of(),
                        "hash drift after preview"));
        assertEquals("config.preview.stale", hashStale.code());
        assertEquals(hashBase.id(), hashFixture.canonical.active().orElseThrow().revisionId());

        Fixture versionFixture = new Fixture();
        StoredConfigurationRevision versionBase = versionFixture.applyInitial(defaultDocuments());
        UUID versionDraft = versionFixture.service.beginDraft(OWNER, "preview-version-stale");
        versionFixture.service.preview(OWNER, versionDraft).toCompletableFuture().join();
        long retainedVersion = (Long) draftStateComponent(versionFixture.service, versionDraft, "version");
        replaceDraftStateComponents(versionFixture.service, versionDraft,
                Map.<String, Object>of("version", Math.addExact(retainedVersion, 1L)));

        AdministrationException versionStale = assertThrows(AdministrationException.class, () ->
                versionFixture.service.applyDraft(OWNER, versionDraft, Optional.of(versionBase.id()), Set.of(),
                        "version drift after preview"));
        assertEquals("config.preview.stale", versionStale.code());
        assertEquals(versionBase.id(), versionFixture.canonical.active().orElseThrow().revisionId());
    }

    @Test
    @DisplayName("[A68][OR8D-11] Rollback apply identifies a draft lacking rollback-workflow provenance")
    void rollbackApplyRejectsDraftWithoutRollbackWorkflowProvenance() {
        Fixture fixture = new Fixture();
        StoredConfigurationRevision active = fixture.applyInitial(defaultDocuments());
        UUID rollbackDraft = fixture.service.beginRollback(OWNER, active.id(), "rollback-provenance");
        fixture.service.preview(OWNER, rollbackDraft).toCompletableFuture().join();
        replaceDraftStateComponents(fixture.service, rollbackDraft,
                Map.<String, Object>of("rollbackSource", Optional.empty()));

        AdministrationException rejected = assertThrows(AdministrationException.class, () ->
                fixture.service.applyRollback(OWNER, rollbackDraft, Optional.of(active.id()), Set.of(),
                        "missing rollback provenance"));

        assertEquals("config.rollback.not_prepared", rejected.code());
        assertEquals(active.id(), fixture.canonical.active().orElseThrow().revisionId());
    }

    @Test
    @DisplayName("[A68][OR8D-11] Snapshot preparation failure occurs before activation and preserves active state")
    void snapshotPreparationFailureLeavesActiveConfigurationUnchanged() {
        InMemorySnapshots snapshots = new InMemorySnapshots();
        Fixture fixture = new Fixture(new ProviderRegistry(), CLOCK, new InMemoryHistory(), snapshots,
                new InMemoryStageReferenceMigrationStore());
        StoredConfigurationRevision active = fixture.applyInitial(defaultDocuments());
        UUID draft = fixture.service.beginDraft(OWNER, "snapshot-preparation-failure");
        fixture.service.editScalar(OWNER, draft, "prestige.cooldown", "PT2S");
        var preview = fixture.service.preview(OWNER, draft).toCompletableFuture().join();
        snapshots.failNextPrepare();

        AdministrationException rejected = failure(fixture.service.applyDraft(OWNER, draft, Optional.of(active.id()),
                acknowledgements(preview), "snapshot preparation failure"));

        assertEquals("config.snapshot.prepare_failed", rejected.code());
        assertEquals(active.id(), fixture.canonical.active().orElseThrow().revisionId());
        assertEquals(Optional.of(active.id()), snapshots.currentRevision());
        assertTrue(rejected.remediation().contains("active configuration is unchanged"));
    }

    @Test
    @DisplayName("[A68][OR8D-09] Apply failure distinguishes unchanged, restored, and reconciliation outcomes")
    void configurationApplyFailureRetainsExactRecoveryOutcome() {
        AdministrationException unchanged = failedInitialApply(new FailingAppendHistory(), new InMemorySnapshots());
        assertEquals(Optional.of(AdministrationSemanticVariant.CONFIG_APPLY_PRIOR_STATE_UNCHANGED),
                unchanged.semanticVariant());

        FailingRuntimeSnapshots restorable = new FailingRuntimeSnapshots(false);
        AdministrationException restored = failedInitialApply(new InMemoryHistory(), restorable);
        assertEquals(Optional.of(AdministrationSemanticVariant.CONFIG_APPLY_PRIOR_STATE_RESTORED),
                restored.semanticVariant());
        assertTrue(restorable.currentRevision().isEmpty());

        FailingRuntimeSnapshots uncertain = new FailingRuntimeSnapshots(true);
        AdministrationException reconciliation = failedInitialApply(new InMemoryHistory(), uncertain);
        assertEquals(Optional.of(AdministrationSemanticVariant.CONFIG_APPLY_RECONCILIATION_REQUIRED),
                reconciliation.semanticVariant());
        assertTrue(uncertain.currentRevision().isPresent());
        assertTrue(reconciliation.remediation().contains("explicit reconciliation"));
        assertFalse(reconciliation.remediation().contains("retry"));
        assertTrue(unchanged.facts().containsKey("revision"));
        assertTrue(restored.facts().containsKey("revision"));
        assertTrue(reconciliation.facts().containsKey("revision"));
    }

    @Test
    @DisplayName("[A68][OR8D-09] Persisted reference changes invalidate the exact remap preview")
    void persistedReferenceChangeInvalidatesRemapPreview() {
        InMemoryStageReferenceMigrationStore references = new InMemoryStageReferenceMigrationStore(true);
        Fixture fixture = new Fixture(new ProviderRegistry(), CLOCK, new InMemoryHistory(), new InMemorySnapshots(),
                references);
        StoredConfigurationRevision first = fixture.applyInitial(activeInternalDocuments());
        UUID draft = fixture.service.beginDraft(OWNER, "remap-stale");
        fixture.service.removeStage(OWNER, draft, new StageId("first"), Optional.of(new StageId("second")));
        fixture.service.preview(OWNER, draft).toCompletableFuture().join();

        AdministrationException stale = failure(fixture.service.applyDraft(OWNER, draft, Optional.of(first.id()),
                Set.of(), "Persisted references changed"));

        assertEquals("stage.change.remap_snapshot_stale", stale.code());
        assertEquals(Map.of("source", "first", "target", "second", "before", "1", "after", "2"),
                stale.facts());
    }

    @Test
    @DisplayName("[A41] Rollback revalidates exact history and creates a new applied revision")
    void rollbackCreatesNewRevisionWithPriorBehavior() {
        Fixture fixture = new Fixture();
        StoredConfigurationRevision first = fixture.applyInitial(defaultDocuments());
        UUID draft = fixture.service.beginDraft(OWNER, "command");
        fixture.service.editScalar(OWNER, draft, "prestige.cooldown", "PT2S");
        var secondPreview = fixture.service.preview(OWNER, draft).toCompletableFuture().join();
        StoredConfigurationRevision second = fixture.service.applyDraft(OWNER, draft, Optional.of(first.id()),
                acknowledgements(secondPreview), "Second behavior").toCompletableFuture().join();

        UUID rollbackDraft = fixture.service.beginRollback(OWNER, first.id(), "gui");
        var rollbackPreview = fixture.service.preview(OWNER, rollbackDraft)
                .toCompletableFuture().join();
        StoredConfigurationRevision rollback = fixture.service.applyRollback(OWNER, rollbackDraft,
                Optional.of(second.id()), acknowledgements(rollbackPreview), "Restore first behavior")
                .toCompletableFuture().join();

        assertNotEquals(first.id(), rollback.id());
        assertEquals(Optional.of(first.id()), rollback.rollbackSource());
        assertEquals(first.compiled(), rollback.compiled());
        assertEquals(first.compiled(), fixture.canonical.active().orElseThrow().compiled());
        assertEquals(List.of(rollback.id(), second.id(), first.id()), fixture.history.recent(10).stream()
                .map(StoredConfigurationRevision::id).toList());
    }

    @Test
    @DisplayName("[A41][A56] Draft provenance seals setup, normal, and rollback authority through acknowledgement")
    void immutableDraftKindRejectsCrossKindApplyAndRechecksExactPermissionAtConfirmation() {
        Fixture fixture = new Fixture();
        UUID setupDraft = fixture.service.beginInitialDraft(OWNER, defaultDocuments(), "setup-wizard");
        fixture.service.preview(OWNER, setupDraft).toCompletableFuture().join();
        AdministrationException setupAsNormal = assertThrows(AdministrationException.class, () ->
                fixture.service.applyDraft(OWNER, setupDraft, Optional.empty(), Set.of(), "cross-kind setup"));
        assertEquals("config.apply.kind_mismatch", setupAsNormal.code());
        PermissionSubject setupCrossKind = new PermissionSubject(OWNER.actor(), Set.of(
                AdministrationPermissions.CONFIG_APPLY, AdministrationPermissions.CONFIG_ROLLBACK));
        assertEquals("config.apply.kind_mismatch", assertThrows(AdministrationException.class, () ->
                fixture.service.applyRollback(setupCrossKind, setupDraft, Optional.empty(), Set.of(),
                        "setup through rollback")).code());
        assertEquals("permission.denied", assertThrows(AdministrationException.class, () ->
                fixture.service.applySetup(setupCrossKind, setupDraft, Set.of(), "setup permission missing")).code());
        assertEquals(ConfigurationApplyKind.SETUP, fixture.service.requiredApplyKind(OWNER, setupDraft));
        StoredConfigurationRevision first = fixture.service.applySetup(OWNER, setupDraft, Set.of(),
                "initial setup").toCompletableFuture().join();

        UUID normalDraft = fixture.service.beginDraft(OWNER, "command");
        fixture.service.addStage(OWNER, normalDraft, new StageId("member"), "Member",
                Optional.empty(), Optional.empty());
        fixture.service.preview(OWNER, normalDraft).toCompletableFuture().join();
        PermissionSubject rollbackOnly = new PermissionSubject(OWNER.actor(),
                Set.of(AdministrationPermissions.CONFIG_ROLLBACK));
        assertEquals("permission.denied", assertThrows(AdministrationException.class, () ->
                fixture.service.prepareAcknowledgement(rollbackOnly, normalDraft)).code());
        assertEquals("permission.denied", assertThrows(AdministrationException.class, () ->
                fixture.service.applyDraft(rollbackOnly, normalDraft, Optional.of(first.id()), Set.of(),
                        "normal permission missing")).code());
        AdministrationException normalAsRollback = assertThrows(AdministrationException.class, () ->
                fixture.service.applyRollback(OWNER, normalDraft, Optional.of(first.id()), Set.of(),
                        "cross-kind normal"));
        assertEquals("config.apply.kind_mismatch", normalAsRollback.code());
        var normalAuthority = fixture.service.prepareAcknowledgement(OWNER, normalDraft);
        StoredConfigurationRevision second = fixture.service.confirmAcknowledgement(OWNER,
                normalAuthority.acknowledgementId(), "add member").toCompletableFuture().join();

        UUID rollbackDraft = fixture.service.beginRollback(OWNER, first.id(), "gui");
        var rollbackPreview = fixture.service.preview(OWNER, rollbackDraft)
                .toCompletableFuture().join();
        assertFalse(rollbackPreview.validation().canApply(Set.of()),
                "removing the newly active stage must retain its high-risk acknowledgement requirement");
        AdministrationException rollbackAsNormal = assertThrows(AdministrationException.class, () ->
                fixture.service.applyDraft(OWNER, rollbackDraft, Optional.of(second.id()), Set.of(),
                        "cross-kind rollback"));
        assertEquals("config.apply.kind_mismatch", rollbackAsNormal.code());
        PermissionSubject normalOnly = new PermissionSubject(OWNER.actor(),
                Set.of(AdministrationPermissions.CONFIG_APPLY));
        assertEquals("permission.denied", assertThrows(AdministrationException.class, () ->
                fixture.service.prepareAcknowledgement(normalOnly, rollbackDraft)).code());
        assertEquals("permission.denied", assertThrows(AdministrationException.class, () ->
                fixture.service.applyRollback(normalOnly, rollbackDraft, Optional.of(second.id()), Set.of(),
                        "rollback permission missing")).code());
        var rollbackAuthority = fixture.service.prepareAcknowledgement(OWNER, rollbackDraft);
        assertEquals(ConfigurationApplyKind.ROLLBACK, rollbackAuthority.kind());

        PermissionSubject revokedAfterAcknowledgement = new PermissionSubject(OWNER.actor(),
                Set.of(AdministrationPermissions.CONFIG_APPLY));
        AdministrationException revoked = assertThrows(AdministrationException.class, () -> fixture.service
                .confirmAcknowledgement(revokedAfterAcknowledgement, rollbackAuthority.acknowledgementId(),
                        "permission revoked"));
        assertEquals("permission.denied", revoked.code());
        StoredConfigurationRevision rollback = fixture.service.confirmAcknowledgement(OWNER,
                rollbackAuthority.acknowledgementId(), "owner retains rollback authority")
                .toCompletableFuture().join();
        assertEquals(Optional.of(first.id()), rollback.rollbackSource());
    }

    @Test
    @DisplayName("[A42][A56] Command/GUI scalar paths are equivalent and view permission cannot mutate")
    void commandAndGuiUseEquivalentCanonicalEdit() {
        Fixture command = new Fixture();
        Fixture gui = new Fixture();
        command.applyInitial(defaultDocuments());
        gui.applyInitial(defaultDocuments());
        UUID commandDraft = command.service.beginDraft(OWNER, "command");
        UUID guiDraft = gui.service.beginDraft(OWNER, "gui");

        var commandModel = command.service.editScalar(OWNER, commandDraft,
                "prestige.cooldown", "PT2S");
        var guiModel = gui.service.editScalar(OWNER, guiDraft,
                "prestige.cooldown", "PT2S");

        assertEquals(commandModel.documents(), guiModel.documents());
        PermissionSubject viewer = new PermissionSubject(new Actor("player", Optional.of(UUID.randomUUID()),
                "Viewer"), Set.of(AdministrationPermissions.CONFIG_VIEW, AdministrationPermissions.ADMIN_GUI));
        assertTrue(command.service.active(viewer).isPresent());
        assertThrows(AdministrationException.class, () -> command.service.beginDraft(viewer, "command"));
        assertThrows(AdministrationException.class, () -> command.service.editScalar(
                viewer, commandDraft, "prestige.cooldown", "PT3S"));
    }

    @Test
    @DisplayName("Fresh drafts remain actor-owned and usable through their two-hour lifetime")
    void freshDraftRemainsOwnedUsableAndExpiresAtDeadline() {
        MutableClock clock = new MutableClock(CLOCK.instant());
        Fixture fixture = new Fixture(new ProviderRegistry(), clock);
        fixture.applyInitial(defaultDocuments());
        UUID staffId = UUID.fromString("12121212-1212-4212-8212-121212121212");
        PermissionSubject staff = new PermissionSubject(new Actor("player", Optional.of(staffId), "Staff"),
                AdministrationPermissions.all());
        PermissionSubject reconnected = new PermissionSubject(
                new Actor("player", Optional.of(staffId), "Staff"), AdministrationPermissions.all());
        PermissionSubject other = new PermissionSubject(
                new Actor("player", Optional.of(UUID.randomUUID()), "Other"), AdministrationPermissions.all());

        UUID draft = fixture.service.beginDraft(staff, "command");

        assertEquals(List.of(draft), fixture.service.ownedDraftIds(reconnected));
        assertTrue(fixture.service.preview(reconnected, draft).toCompletableFuture().join()
                .validation().findings().isEmpty());
        assertEquals(List.of(), fixture.service.ownedDraftIds(other));
        assertEquals(List.of(), fixture.service.ownedDraftIds(
                new PermissionSubject(staff.actor(), Set.of())));

        clock.advance(Duration.ofHours(2));

        AdministrationException expired = assertThrows(AdministrationException.class,
                () -> fixture.service.preview(reconnected, draft));
        assertEquals("config.draft.expired", expired.code());
        assertEquals(List.of(), fixture.service.ownedDraftIds(reconnected));
    }

    @Test
    @DisplayName("[A39]Edit or cancel during async same-draft prepare cannot activate stale content")
    void sameDraftEditAndCancelDuringPrepareFailClosed() {
        ProviderRegistry providers = new ProviderRegistry();
        BlockingRankAdapter adapter = new BlockingRankAdapter();
        providers.activate(providers.register("test", adapter));
        Fixture fixture = new Fixture(providers);
        StoredConfigurationRevision initial = fixture.applyInitial(externalDocuments());

        UUID editedDraft = fixture.service.beginDraft(OWNER, "command");
        fixture.service.editScalar(OWNER, editedDraft, "progression.stages.first.display-name", "First preview");
        fixture.service.preview(OWNER, editedDraft).toCompletableFuture().join();
        CompletableFuture<Result<Set<String>>> editGate = adapter.block();
        CompletionStage<StoredConfigurationRevision> staleEdit = fixture.service.applyDraft(OWNER, editedDraft,
                Optional.of(initial.id()), Set.of(), "stale edit");
        fixture.service.editScalar(OWNER, editedDraft, "progression.stages.first.display-name", "Newer edit");
        editGate.complete(Result.success(Set.of("member", "veteran")));
        assertEquals("config.draft.changed_during_apply", failure(staleEdit).code());
        assertEquals(initial.id(), fixture.canonical.active().orElseThrow().revisionId());

        UUID cancelledDraft = fixture.service.beginDraft(OWNER, "command");
        fixture.service.editScalar(OWNER, cancelledDraft, "progression.stages.first.display-name", "Cancelled");
        fixture.service.preview(OWNER, cancelledDraft).toCompletableFuture().join();
        CompletableFuture<Result<Set<String>>> cancelGate = adapter.block();
        CompletionStage<StoredConfigurationRevision> staleCancel = fixture.service.applyDraft(OWNER, cancelledDraft,
                Optional.of(initial.id()), Set.of(), "stale cancel");
        fixture.service.discardDraft(OWNER, cancelledDraft);
        cancelGate.complete(Result.success(Set.of("member", "veteran")));
        assertEquals("config.draft.cancelled", failure(staleCancel).code());
        assertEquals(initial.id(), fixture.canonical.active().orElseThrow().revisionId());
    }

    @Test
    @DisplayName("[A39]Two async applies of one exact draft have one deterministic winner")
    void sameDraftConcurrentApplyHasOneWinner() {
        ProviderRegistry providers = new ProviderRegistry();
        BlockingRankAdapter adapter = new BlockingRankAdapter();
        providers.activate(providers.register("test", adapter));
        Fixture fixture = new Fixture(providers);
        StoredConfigurationRevision initial = fixture.applyInitial(externalDocuments());
        UUID draft = fixture.service.beginDraft(OWNER, "gui");
        fixture.service.editScalar(OWNER, draft, "progression.stages.first.display-name", "Renamed");
        fixture.service.preview(OWNER, draft).toCompletableFuture().join();
        CompletableFuture<Result<Set<String>>> gate = adapter.block();

        CompletionStage<StoredConfigurationRevision> first = fixture.service.applyDraft(OWNER, draft,
                Optional.of(initial.id()), Set.of(), "same draft");
        CompletionStage<StoredConfigurationRevision> second = fixture.service.applyDraft(OWNER, draft,
                Optional.of(initial.id()), Set.of(), "same draft");
        gate.complete(Result.success(Set.of("member", "veteran")));

        long successes = java.util.stream.Stream.of(first, second).filter(stage -> {
            try {
                stage.toCompletableFuture().join();
                return true;
            } catch (CompletionException exception) {
                return false;
            }
        }).count();
        assertEquals(1, successes);
        assertEquals(2, fixture.history.recent(10).size());
        assertTrue(fixture.canonical.active().orElseThrow().compiled().documents().get("progression.yml")
                .contains("display-name: Renamed"));
    }

    @Test
    @DisplayName("Configuration acknowledgement is exact-draft bound and stale authority is revoked")
    void staleConfigurationAcknowledgementCannotBeReplayed() {
        Fixture fixture = new Fixture();
        fixture.applyInitial(defaultDocuments());
        UUID draft = fixture.service.beginDraft(OWNER, "command");
        fixture.service.addStage(OWNER, draft, new StageId("member"), "Member", Optional.empty(), Optional.empty());
        fixture.service.preview(OWNER, draft).toCompletableFuture().join();
        var authority = fixture.service.prepareAcknowledgement(OWNER, draft);

        fixture.service.addStage(OWNER, draft, new StageId("veteran"), "Veteran", Optional.empty(),
                Optional.empty());

        AdministrationException stale = assertThrows(AdministrationException.class, () -> fixture.service
                .confirmAcknowledgement(OWNER, authority.acknowledgementId(), "stale authority"));
        assertEquals("config.acknowledgement.stale", stale.code());
        AdministrationException replay = assertThrows(AdministrationException.class, () -> fixture.service
                .confirmAcknowledgement(OWNER, authority.acknowledgementId(), "replay"));
        assertEquals("config.acknowledgement.unknown", replay.code());
    }

    @Test
    @DisplayName("Concurrent configuration confirmation consumes one server authority exactly once")
    void concurrentConfigurationAcknowledgementHasOneWinner() {
        Fixture fixture = new Fixture();
        fixture.applyInitial(defaultDocuments());
        UUID draft = fixture.service.beginDraft(OWNER, "gui");
        fixture.service.addStage(OWNER, draft, new StageId("member"), "Member", Optional.empty(), Optional.empty());
        fixture.service.preview(OWNER, draft).toCompletableFuture().join();
        var authority = fixture.service.prepareAcknowledgement(OWNER, draft);
        CountDownLatch start = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.supplyAsync(
                    () -> confirm(fixture, authority.acknowledgementId(), start), workers);
            var second = CompletableFuture.supplyAsync(
                    () -> confirm(fixture, authority.acknowledgementId(), start), workers);
            start.countDown();
            List<String> results = List.of(first.join(), second.join());

            assertEquals(1, results.stream().filter("success"::equals).count(), results::toString);
            assertEquals(1, results.stream().filter("config.acknowledgement.already_used"::equals).count(),
                    results::toString);
        }
        assertEquals(2, fixture.history.recent(10).size());
    }

    @Test
    @DisplayName("Configuration acknowledgement expires at its exact server deadline")
    void configurationAcknowledgementExpiresAtDeadline() {
        MutableClock clock = new MutableClock(CLOCK.instant());
        Fixture fixture = new Fixture(new ProviderRegistry(), clock);
        fixture.applyInitial(defaultDocuments());
        UUID draft = fixture.service.beginDraft(OWNER, "command");
        fixture.service.addStage(OWNER, draft, new StageId("member"), "Member", Optional.empty(), Optional.empty());
        fixture.service.preview(OWNER, draft).toCompletableFuture().join();
        var authority = fixture.service.prepareAcknowledgement(OWNER, draft);

        clock.advance(Duration.ofMinutes(5));

        AdministrationException expired = assertThrows(AdministrationException.class, () -> fixture.service
                .confirmAcknowledgement(OWNER, authority.acknowledgementId(), "expired authority"));
        assertEquals("config.acknowledgement.expired", expired.code());
    }

    private static String confirm(Fixture fixture, UUID authority, CountDownLatch start) {
        try {
            start.await();
            fixture.service.confirmAcknowledgement(OWNER, authority, "concurrent confirmation")
                    .toCompletableFuture().join();
            return "success";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        } catch (AdministrationException exception) {
            return exception.code();
        }
    }

    private static AdministrationException failure(CompletionStage<?> stage) {
        CompletionException wrapped = assertThrows(CompletionException.class,
                () -> stage.toCompletableFuture().join());
        return (AdministrationException) wrapped.getCause();
    }

    private static Object draftStateComponent(
            ConfigurationAdministrationService service,
            UUID draftId,
            String componentName) {
        Object state = draftStates(service).get(draftId);
        if (state == null) {
            throw new AssertionError("Missing retained draft state " + draftId);
        }
        try {
            for (java.lang.reflect.RecordComponent component : state.getClass().getRecordComponents()) {
                if (component.getName().equals(componentName)) {
                    var accessor = component.getAccessor();
                    accessor.setAccessible(true);
                    return accessor.invoke(state);
                }
            }
            throw new AssertionError("Unknown draft-state component " + componentName);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect retained draft state", exception);
        }
    }

    private static void replaceDraftStateComponents(
            ConfigurationAdministrationService service,
            UUID draftId,
            Map<String, Object> replacements) {
        Map<UUID, Object> states = draftStates(service);
        Object state = states.get(draftId);
        if (state == null) {
            throw new AssertionError("Missing retained draft state " + draftId);
        }
        try {
            java.lang.reflect.RecordComponent[] components = state.getClass().getRecordComponents();
            Object[] arguments = new Object[components.length];
            Class<?>[] parameterTypes = new Class<?>[components.length];
            for (int index = 0; index < components.length; index++) {
                java.lang.reflect.RecordComponent component = components[index];
                var accessor = component.getAccessor();
                accessor.setAccessible(true);
                Object current = accessor.invoke(state);
                arguments[index] = replacements.getOrDefault(component.getName(), current);
                parameterTypes[index] = component.getType();
            }
            var constructor = state.getClass().getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            states.put(draftId, constructor.newInstance(arguments));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not replace retained draft state", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> draftStates(ConfigurationAdministrationService service) {
        try {
            var field = ConfigurationAdministrationService.class.getDeclaredField("drafts");
            field.setAccessible(true);
            return (Map<UUID, Object>) field.get(service);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect configuration draft registry", exception);
        }
    }

    private static AdministrationException failedInitialApply(
            ConfigurationHistoryStore history,
            ConfigurationSnapshotStore snapshots) {
        Fixture fixture = new Fixture(new ProviderRegistry(), CLOCK, history, snapshots,
                new InMemoryStageReferenceMigrationStore());
        UUID draft = fixture.service.beginInitialDraft(OWNER, defaultDocuments(), "apply-recovery-test");
        var preview = fixture.service.preview(OWNER, draft).toCompletableFuture().join();
        assertTrue(preview.validation().canApply(Set.of()));
        return failure(fixture.service.applySetup(OWNER, draft, Set.of(), "Apply recovery outcome"));
    }

    private static boolean experienceHas(
            net.maddkraft.maddprestige.core.admin.setup.SetupPreview preview,
            String key,
            String argument,
            String value) {
        return preview.playerExperience().stream().anyMatch(message -> message.key().equals(key)
                && message.argument(argument).filter(value::equals).isPresent());
    }

    private static Set<String> acknowledgements(
            net.maddkraft.maddprestige.core.admin.config.ConfigurationPreview preview) {
        return preview.validation().findings().stream().map(finding -> finding.code())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static Map<String, String> defaultDocuments() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        result.put("progression.yml", resource("/defaults/progression.yml") + "stages: {}\norder: []\n");
        result.put("requirements.yml", resource("/defaults/requirements.yml"));
        result.put("rewards.yml", resource("/defaults/rewards.yml"));
        result.put("lifecycle.yml", resource("/defaults/lifecycle.yml")
                .replace("  maximum: unlimited", "  current-count-increment: 1\n"
                        + "  lifetime-count-increment: 1\n  maximum: unlimited")
                .replace("  reset-policy:\n", "  reset-policy:\n    progression-stage: PRESERVE\n"));
        result.put("integrations.yml", """
                schema-version: 5
                vault: {enabled: false}
                mcmmo: {enabled: false}
                placeholderapi: {output: {enabled: false}, inputs: {}}
                economyshopgui: {compatibility-enabled: false, progression-credit: {enabled: false}}
                quickshop: {compatibility-enabled: false, progression-credit: {enabled: false}}
                """);
        return Map.copyOf(result);
    }

    private static GuidedMoneyScalingPair guidedMoneyPair(
            String base,
            String rate,
            Map<String, BigDecimal> overrides) {
        LinkedHashMap<String, Object> segment = new LinkedHashMap<>();
        segment.put("start-prestige", 1L);
        segment.put("end-prestige", "unlimited");
        segment.put("mode", "LINEAR");
        segment.put("transition", "EXPLICIT_BASE");
        segment.put("base", new BigDecimal(base));
        segment.put("rate", new BigDecimal(rate));
        segment.put("rounding", "EXACT");
        segment.put("quantum", BigDecimal.ONE);
        segment.put("overrides", overrides);
        return new GuidedMoneyScalingPair(
                "money",
                "money_cost",
                "requirements.requirements.money.scaling",
                "requirements.costs.money_cost.amount",
                "2",
                new StructuredConfigurationValue(Map.of("segments", List.of(segment))));
    }

    private static Map<String, String> guidedMoneyDocuments() {
        LinkedHashMap<String, String> documents = new LinkedHashMap<>(defaultDocuments());
        documents.put("requirements.yml", """
                # owner Money note
                schema-version: 3
                maximum-depth: 8
                requirements:
                  money:
                    provider: vault_balance
                    metric: balance
                    value-type: CURRENCY_AMOUNT
                    operator: GREATER_OR_EQUAL
                    target: "2"
                    scope: ABSOLUTE
                    completion: LIVE
                    scaling:
                      segments:
                        - start-prestige: 1
                          end-prestige: unlimited
                          mode: LINEAR
                          base: 1
                          rate: 0.5
                          overrides:
                            "3": 3
                    custom-owner-key: keep
                trees:
                  prestige_gate:
                    id: prestige_gate
                    mode: ALL
                    children:
                      - requirement: money
                costs:
                  money_cost:
                    provider: vault_economy_cost
                    type: vault_economy
                    value-type: CURRENCY_AMOUNT
                    amount: "7"
                """);
        documents.put("lifecycle.yml", """
                # owner lifecycle note
                schema-version: 4
                prestige:
                  enabled: true
                  current-count-increment: 1
                  lifetime-count-increment: 1
                  maximum: 10
                  cooldown: PT0S
                  requirement-tree: prestige_gate
                  costs: [money_cost]
                  rewards: []
                  reset-policy:
                    progression-stage: PRESERVE
                custom-owner-key: keep
                """);
        return Map.copyOf(documents);
    }

    private static Map<String, String> guidedBaseOverrideDocuments() {
        LinkedHashMap<String, String> documents = new LinkedHashMap<>(guidedMoneyDocuments());
        documents.put("requirements.yml", documents.get("requirements.yml")
                .replace("rate: 0.5", "rate: 1.25")
                .replace("\"3\": 3", "\"3\": 3\n            \"6\": 3"));
        return Map.copyOf(documents);
    }

    private static Map<String, String> guidedOverrideDocuments() {
        LinkedHashMap<String, String> documents = new LinkedHashMap<>(guidedRewardDocuments());
        documents.put("requirements.yml", documents.get("requirements.yml")
                .replace("base: 1", "base: 2")
                .replace("rate: 0.5", "rate: 1.25")
                .replace("\"3\": 3", "\"3\": 3\n            \"6\": 3")
                .replace("amount: \"7\"", "amount: \"6\""));
        documents.put("rewards.yml", documents.get("rewards.yml")
                .replace("value: \"1\"", "value: \"2\""));
        return Map.copyOf(documents);
    }

    private static Map<String, String> guidedRewardDocuments() {
        LinkedHashMap<String, String> documents = new LinkedHashMap<>(guidedMoneyDocuments());
        documents.put("rewards.yml", """
                # owner Reward note
                schema-version: 3
                rewards:
                  vault_bonus:
                    display-name: full-stack qualification Vault bonus
                    failure-policy: REQUIRED
                    provider: vault_economy_reward
                    repeatability: ONCE_PER_OPERATION
                    type: vault_economy
                    value: "1"
                    value-type: CURRENCY_AMOUNT
                    custom-owner-key: keep
                """);
        documents.put("lifecycle.yml", documents.get("lifecycle.yml")
                .replace("rewards: []", "rewards: [vault_bonus]"));
        return Map.copyOf(documents);
    }

    private static Map<String, String> guidedTotalSkillLevelDocuments() {
        LinkedHashMap<String, String> documents = new LinkedHashMap<>(guidedOverrideDocuments());
        documents.put("requirements.yml", documents.get("requirements.yml")
                .replace("\"6\": 3", "\"6\": 4")
                .replace("    custom-owner-key: keep\ntrees:", """
                            custom-owner-key: keep
                        # owner Total Skill Level note
                        skill:
                          provider: mcmmo
                          metric: total_level
                          value-type: INTEGER_COUNT
                          operator: GREATER_OR_EQUAL
                          target: "1"
                          scope: ABSOLUTE
                          completion: LIVE
                          custom-skill-key: keep
                      trees:""")
                .replace("      - requirement: money\ncosts:", """
                            - requirement: money
                            - requirement: skill
                      costs:"""));
        return Map.copyOf(documents);
    }

    private static Map<String, String> complexTotalSkillLevelDocuments() {
        LinkedHashMap<String, String> documents = new LinkedHashMap<>(guidedTotalSkillLevelDocuments());
        documents.put("requirements.yml", documents.get("requirements.yml").replace("""
                          - requirement: money
                          - requirement: skill
                    costs:""", """
                          - requirement: money
                          - node:
                              id: skill_branch
                              mode: ALL
                              children:
                                - requirement: skill
                    costs:"""));
        return Map.copyOf(documents);
    }

    private static Map<String, String> segmentedContinueDocuments() {
        LinkedHashMap<String, String> documents = new LinkedHashMap<>(guidedMoneyDocuments());
        documents.put("requirements.yml", documents.get("requirements.yml").replace("""
                      segments:
                        - start-prestige: 1
                          end-prestige: unlimited
                          mode: LINEAR
                          base: 1
                          rate: 0.5
                          overrides:
                            "3": 3
                """, """
                      segments:
                        - start-prestige: 1
                          end-prestige: 3
                          mode: FLAT
                          transition: EXPLICIT_BASE
                          base: 1
                          rate: 0
                          overrides:
                            "3": 3
                        - start-prestige: 4
                          end-prestige: unlimited
                          mode: LINEAR
                          transition: CONTINUE
                          base: 1
                          rate: 0.5
                          overrides:
                            "6": 4
                """));
        return Map.copyOf(documents);
    }

    private static Map<String, String> manualScalingDocuments() {
        LinkedHashMap<String, String> documents = new LinkedHashMap<>(guidedMoneyDocuments());
        documents.put("requirements.yml", documents.get("requirements.yml").replace("""
                      segments:
                        - start-prestige: 1
                          end-prestige: unlimited
                          mode: LINEAR
                          base: 1
                          rate: 0.5
                          overrides:
                            "3": 3
                """, """
                      segments:
                        - start-prestige: 1
                          end-prestige: 10
                          mode: MANUAL
                          transition: EXPLICIT_BASE
                          base: 1
                          rate: 0
                          overrides:
                            "1": 1
                            "2": 2
                            "3": 3
                            "4": 4
                            "5": 5
                            "6": 6
                            "7": 7
                            "8": 8
                            "9": 9
                            "10": 10
                """));
        return Map.copyOf(documents);
    }

    private static Map<String, String> complexRewardDocuments() {
        LinkedHashMap<String, String> documents = new LinkedHashMap<>(guidedRewardDocuments());
        documents.put("lifecycle.yml", """
                schema-version: 4
                prestige:
                  enabled: true
                  current-count-increment: 1
                  lifetime-count-increment: 1
                  maximum: 10
                  cooldown: PT0S
                  requirement-tree: prestige_gate
                  costs: [money_cost]
                  rewards: [vault_bonus]
                  reward-scaling:
                    vault_bonus:
                      mode: FLAT
                      base: 1
                  reset-policy:
                    progression-stage: PRESERVE
                """);
        return Map.copyOf(documents);
    }

    private static Map<String, String> externalDocuments() {
        LinkedHashMap<String, String> documents = new LinkedHashMap<>(defaultDocuments());
        documents.put("progression.yml", """
                schema-version: 3
                active: true
                reconciliation-policy: warn-only
                baseline: first
                stages:
                  first:
                    enabled: true
                    display-name: First
                    projection: {type: group, provider: rank, group: member}
                  second:
                    enabled: true
                    display-name: Second
                    projection: {type: group, provider: rank, group: veteran}
                order:
                  - first
                  - second
                """);
        return Map.copyOf(documents);
    }

    private static Map<String, String> activeInternalDocuments() {
        LinkedHashMap<String, String> documents = new LinkedHashMap<>(defaultDocuments());
        documents.put("progression.yml", """
                schema-version: 3
                active: true
                reconciliation-policy: warn-only
                baseline: first
                stages:
                  first:
                    enabled: true
                    display-name: First
                    projection: none
                  second:
                    enabled: true
                    display-name: Second
                    projection: none
                order:
                  - first
                  - second
                """);
        return Map.copyOf(documents);
    }

    private static String activeProgression() {
        return """
                schema-version: 3
                active: true
                reconciliation-policy: warn-only
                baseline: first
                stages:
                  first: {enabled: true, display-name: First, projection: none}
                  second:
                    enabled: true
                    display-name: Second
                    projection: none
                    requirements: eligibility
                order: [first, second]
                """;
    }

    private static String activeRequirements() {
        return """
                schema-version: 3
                maximum-depth: 8
                requirements:
                  play:
                    provider: absent_provider
                    metric: absent_metric
                    value-type: count
                    operator: greater-or-equal
                    target: 10
                    scope: absolute
                    completion: live
                trees:
                  eligibility:
                    id: eligibility
                    mode: all
                    children: [{requirement: play}]
                costs: {}
                """;
    }

    private static String resource(String name) {
        try (var stream = ConfigurationAdministrationTest.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IllegalArgumentException("Missing test resource " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static final class Fixture {
        private final ConfigurationService canonical = new ConfigurationService();
        private final ConfigurationHistoryStore history;
        private final ConfigurationSnapshotStore snapshots;
        private final AdministrationConfigurationWorkflow workflow;
        private final ConfigurationAdministrationService service;

        private Fixture() {
            this(new ProviderRegistry());
        }

        private Fixture(ProviderRegistry providers) {
            this(providers, CLOCK);
        }

        private Fixture(ProviderRegistry providers, Clock clock) {
            this(providers, clock, new InMemoryHistory(), new InMemorySnapshots(),
                    new InMemoryStageReferenceMigrationStore());
        }

        private Fixture(
                ProviderRegistry providers,
                Clock clock,
                ConfigurationHistoryStore history,
                ConfigurationSnapshotStore snapshots,
                InMemoryStageReferenceMigrationStore stageReferences) {
            this.history = history;
            this.snapshots = snapshots;
            workflow = new AdministrationConfigurationWorkflow(canonical, providers, stageReferences, List.of());
            service = new ConfigurationAdministrationService(canonical, workflow,
                    compatibilitySchema(), history, snapshots, clock);
        }

        private StoredConfigurationRevision applyInitial(Map<String, String> documents) {
            UUID draft = service.beginInitialDraft(OWNER, documents, "setup-wizard");
            var preview = service.preview(OWNER, draft).toCompletableFuture().join();
            assertFalse(preview.validation().hasErrors(), preview.validation().toString());
            if (preview.validation().canApply(Set.of())) {
                return service.applySetup(OWNER, draft, Set.of(), "Initial test revision")
                        .toCompletableFuture().join();
            }
            var acknowledgement = service.prepareAcknowledgement(OWNER, draft);
            return service.confirmAcknowledgement(OWNER, acknowledgement.acknowledgementId(),
                    "Initial test revision").toCompletableFuture().join();
        }
    }

    private static final class ConfigurationMetricProvider
            implements net.maddkraft.maddprestige.api.metric.MetricProvider {
        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public ProviderDescriptor descriptor() {
            return setupDescriptor("vault_balance", "metric");
        }

        @Override
        public ProviderHealth health() {
            return setupHealth();
        }

        @Override
        public Set<net.maddkraft.maddprestige.api.metric.MetricDescriptor> metrics() {
            return Set.of(new net.maddkraft.maddprestige.api.metric.MetricDescriptor(
                    new ProviderId("vault_balance"), new MetricId("balance"),
                    net.maddkraft.maddprestige.api.metric.MetricValueType.CURRENCY_AMOUNT,
                    Set.of(net.maddkraft.maddprestige.api.metric.MetricOperator.GREATER_OR_EQUAL),
                    Set.of(net.maddkraft.maddprestige.api.metric.MetricReadMode.CURRENT), true,
                    net.maddkraft.maddprestige.api.metric.MetricMonotonicity.NON_MONOTONIC,
                    net.maddkraft.maddprestige.api.metric.MetricResetPolicy.NOT_APPLICABLE,
                    Map.of(), "Money", "Money", "currency", "authoritative"));
        }

        @Override
        public CompletionStage<Map<net.maddkraft.maddprestige.api.metric.MetricQuery,
                net.maddkraft.maddprestige.api.metric.MetricSample>> read(
                UUID playerId,
                List<net.maddkraft.maddprestige.api.metric.MetricQuery> queries,
                long providerGeneration) {
            reads.incrementAndGet();
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    private static final class ConfigurationCostProvider
            implements net.maddkraft.maddprestige.api.cost.CostProvider {
        private final AtomicInteger executions = new AtomicInteger();

        @Override
        public ProviderDescriptor descriptor() {
            return setupDescriptor("vault_economy_cost", "cost");
        }

        @Override
        public ProviderHealth health() {
            return setupHealth();
        }

        @Override
        public net.maddkraft.maddprestige.api.action.ActionCharacteristics characteristics(
                net.maddkraft.maddprestige.api.cost.CostDefinition definition) {
            return new net.maddkraft.maddprestige.api.action.ActionCharacteristics(true, true, true, true);
        }

        @Override
        public ValidationReport validate(net.maddkraft.maddprestige.api.cost.CostDefinition definition) {
            return ValidationReport.VALID;
        }

        @Override
        public CompletionStage<net.maddkraft.maddprestige.api.cost.CostPreflight> preflight(
                net.maddkraft.maddprestige.api.cost.PlannedCost proposed) {
            return CompletableFuture.completedFuture(
                    net.maddkraft.maddprestige.api.cost.CostPreflight.ready(proposed));
        }

        @Override
        public CompletionStage<net.maddkraft.maddprestige.api.action.ActionExecutionResult> execute(
                net.maddkraft.maddprestige.api.cost.PlannedCost plannedCost) {
            executions.incrementAndGet();
            return CompletableFuture.completedFuture(
                    net.maddkraft.maddprestige.api.action.ActionExecutionResult.applied());
        }
    }

    private static final class ConfigurationRewardProvider
            implements net.maddkraft.maddprestige.api.reward.RewardProvider {
        private final AtomicInteger executions = new AtomicInteger();

        @Override
        public ProviderDescriptor descriptor() {
            return setupDescriptor("vault_economy_reward", "reward");
        }

        @Override
        public ProviderHealth health() {
            return setupHealth();
        }

        @Override
        public net.maddkraft.maddprestige.api.action.ActionCharacteristics characteristics(
                net.maddkraft.maddprestige.api.reward.RewardDefinition definition) {
            return new net.maddkraft.maddprestige.api.action.ActionCharacteristics(true, true, true, true);
        }

        @Override
        public ValidationReport validate(net.maddkraft.maddprestige.api.reward.RewardDefinition definition) {
            return ValidationReport.VALID;
        }

        @Override
        public CompletionStage<net.maddkraft.maddprestige.api.reward.RewardPreflight> preflight(
                net.maddkraft.maddprestige.api.reward.PlannedReward proposed) {
            return CompletableFuture.completedFuture(
                    net.maddkraft.maddprestige.api.reward.RewardPreflight.ready(proposed));
        }

        @Override
        public CompletionStage<net.maddkraft.maddprestige.api.action.ActionExecutionResult> execute(
                net.maddkraft.maddprestige.api.reward.PlannedReward plannedReward) {
            executions.incrementAndGet();
            return CompletableFuture.completedFuture(
                    net.maddkraft.maddprestige.api.action.ActionExecutionResult.applied());
        }
    }

    private static net.maddkraft.maddprestige.core.schema.SchemaRegistry compatibilitySchema() {
        var schema = net.maddkraft.maddprestige.core.schema.PrestigeLifecycleSchema.create();
        ActiveConfigurationSchema.extend(schema);
        return schema;
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            current = new AtomicReference<>(initial);
        }

        private void advance(Duration duration) {
            current.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Test clock is fixed to UTC");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }

    private static final class SetupProvider implements net.maddkraft.maddprestige.api.metric.MetricProvider {
        private final ProviderId providerId;
        private final MetricId metricId;
        private final net.maddkraft.maddprestige.api.metric.MetricValueType valueType;

        private SetupProvider() {
            this("setup_metric", "play_time");
        }

        private SetupProvider(String providerId, String metricId) {
            this(providerId, metricId, net.maddkraft.maddprestige.api.metric.MetricValueType.COUNT);
        }

        private SetupProvider(
                String providerId,
                String metricId,
                net.maddkraft.maddprestige.api.metric.MetricValueType valueType) {
            this.providerId = new ProviderId(providerId);
            this.metricId = new MetricId(metricId);
            this.valueType = valueType;
        }

        @Override
        public ProviderDescriptor descriptor() {
            return new ProviderDescriptor(providerId, "test", "1", "1", List.of(), List.of(
                    new CapabilityDescriptor("metric", "metric", "test", Map.of())));
        }

        @Override
        public ProviderHealth health() {
            return new ProviderHealth(ProviderHealthState.AVAILABLE, "available", "ready", CLOCK.instant());
        }

        @Override
        public Set<net.maddkraft.maddprestige.api.metric.MetricDescriptor> metrics() {
            return Set.of(new net.maddkraft.maddprestige.api.metric.MetricDescriptor(providerId,
                    metricId, valueType,
                    net.maddkraft.maddprestige.api.metric.MetricOperator.compatibleWith(valueType),
                    Set.of(net.maddkraft.maddprestige.api.metric.MetricReadMode.CURRENT), true,
                    net.maddkraft.maddprestige.api.metric.MetricMonotonicity.MONOTONIC,
                    net.maddkraft.maddprestige.api.metric.MetricResetPolicy.NOT_APPLICABLE, Map.of(), "Play time",
                    "Play time", "count", "authoritative"));
        }

        @Override
        public CompletionStage<Map<net.maddkraft.maddprestige.api.metric.MetricQuery,
                net.maddkraft.maddprestige.api.metric.MetricSample>> read(
                UUID playerId,
                List<net.maddkraft.maddprestige.api.metric.MetricQuery> queries,
                long providerGeneration) {
            return CompletableFuture.completedFuture(Map.of());
        }

    }

    private static final class CanonicalMetricProvider implements net.maddkraft.maddprestige.api.metric.MetricProvider {
        @Override
        public ProviderDescriptor descriptor() {
            return new ProviderDescriptor(new ProviderId("paper_statistics"), "test", "1", "1", List.of(),
                    List.of(new CapabilityDescriptor("metric", "metric", "test", Map.of())));
        }

        @Override
        public ProviderHealth health() {
            return setupHealth();
        }

        @Override
        public Set<net.maddkraft.maddprestige.api.metric.MetricDescriptor> metrics() {
            return Set.of(new net.maddkraft.maddprestige.api.metric.MetricDescriptor(
                    new ProviderId("paper_statistics"), new MetricId("play_one_minute"),
                    net.maddkraft.maddprestige.api.metric.MetricValueType.DURATION,
                    Set.of(net.maddkraft.maddprestige.api.metric.MetricOperator.GREATER_OR_EQUAL),
                    Set.of(net.maddkraft.maddprestige.api.metric.MetricReadMode.CURRENT), true,
                    net.maddkraft.maddprestige.api.metric.MetricMonotonicity.MONOTONIC,
                    net.maddkraft.maddprestige.api.metric.MetricResetPolicy.NOT_APPLICABLE, Map.of(), "Play time",
                    "Play time", "duration", "authoritative"));
        }

        @Override
        public CompletionStage<Map<net.maddkraft.maddprestige.api.metric.MetricQuery,
                net.maddkraft.maddprestige.api.metric.MetricSample>> read(
                UUID playerId,
                List<net.maddkraft.maddprestige.api.metric.MetricQuery> queries,
                long providerGeneration) {
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    private static final class CanonicalRankAdapter implements RankAdapter {
        @Override
        public ProviderDescriptor descriptor() {
            return new ProviderDescriptor(new ProviderId("luckperms"), "test", "1", "1", List.of(),
                    List.of(new CapabilityDescriptor("rank", "rank", "test", Map.of())));
        }

        @Override
        public ProviderHealth health() {
            return setupHealth();
        }

        @Override
        public CompletionStage<Result<Set<String>>> validateTargets(Set<String> groupNames) {
            return CompletableFuture.completedFuture(Result.success(Set.copyOf(groupNames)));
        }

        @Override
        public CompletionStage<Result<ManagedRankState>> readManagedState(UUID playerId, Set<String> managedGroups) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Result<RankProjectionResult>> project(RankProjectionRequest request) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class SetupCostProvider implements net.maddkraft.maddprestige.api.cost.CostProvider {
        private final String providerId;

        private SetupCostProvider() {
            this("setup_cost");
        }

        private SetupCostProvider(String providerId) {
            this.providerId = providerId;
        }

        @Override
        public ProviderDescriptor descriptor() {
            return setupDescriptor(providerId, "cost");
        }

        @Override
        public ProviderHealth health() {
            return setupHealth();
        }

        @Override
        public net.maddkraft.maddprestige.api.action.ActionCharacteristics characteristics(
                net.maddkraft.maddprestige.api.cost.CostDefinition definition) {
            return new net.maddkraft.maddprestige.api.action.ActionCharacteristics(true, true, true, true);
        }

        @Override
        public ValidationReport validate(net.maddkraft.maddprestige.api.cost.CostDefinition definition) {
            return ValidationReport.VALID;
        }

        @Override
        public CompletionStage<net.maddkraft.maddprestige.api.cost.CostPreflight> preflight(
                net.maddkraft.maddprestige.api.cost.PlannedCost proposed) {
            return CompletableFuture.completedFuture(
                    net.maddkraft.maddprestige.api.cost.CostPreflight.ready(proposed));
        }

        @Override
        public CompletionStage<net.maddkraft.maddprestige.api.action.ActionExecutionResult> execute(
                net.maddkraft.maddprestige.api.cost.PlannedCost plannedCost) {
            return CompletableFuture.completedFuture(
                    net.maddkraft.maddprestige.api.action.ActionExecutionResult.applied());
        }
    }

    private static final class SetupRewardProvider implements net.maddkraft.maddprestige.api.reward.RewardProvider {
        private final String providerId;

        private SetupRewardProvider() {
            this("setup_reward");
        }

        private SetupRewardProvider(String providerId) {
            this.providerId = providerId;
        }

        @Override
        public ProviderDescriptor descriptor() {
            return setupDescriptor(providerId, "reward");
        }

        @Override
        public ProviderHealth health() {
            return setupHealth();
        }

        @Override
        public net.maddkraft.maddprestige.api.action.ActionCharacteristics characteristics(
                net.maddkraft.maddprestige.api.reward.RewardDefinition definition) {
            return new net.maddkraft.maddprestige.api.action.ActionCharacteristics(true, false, true, true);
        }

        @Override
        public ValidationReport validate(net.maddkraft.maddprestige.api.reward.RewardDefinition definition) {
            return ValidationReport.VALID;
        }

        @Override
        public CompletionStage<net.maddkraft.maddprestige.api.reward.RewardPreflight> preflight(
                net.maddkraft.maddprestige.api.reward.PlannedReward proposed) {
            return CompletableFuture.completedFuture(
                    net.maddkraft.maddprestige.api.reward.RewardPreflight.ready(proposed));
        }

        @Override
        public CompletionStage<net.maddkraft.maddprestige.api.action.ActionExecutionResult> execute(
                net.maddkraft.maddprestige.api.reward.PlannedReward plannedReward) {
            return CompletableFuture.completedFuture(
                    net.maddkraft.maddprestige.api.action.ActionExecutionResult.applied());
        }
    }

    private static ProviderDescriptor setupDescriptor(String id, String capability) {
        return new ProviderDescriptor(new ProviderId(id), "test", "1", "1", List.of(),
                List.of(new CapabilityDescriptor(capability, capability, "test", Map.of())));
    }

    private static ProviderHealth setupHealth() {
        return new ProviderHealth(ProviderHealthState.AVAILABLE, "available", "ready", CLOCK.instant());
    }

    private static final class BlockingRankAdapter implements RankAdapter {
        private final AtomicReference<CompletableFuture<Result<Set<String>>>> blocked = new AtomicReference<>();

        private CompletableFuture<Result<Set<String>>> block() {
            CompletableFuture<Result<Set<String>>> future = new CompletableFuture<>();
            blocked.set(future);
            future.whenComplete((ignored, failure) -> blocked.compareAndSet(future, null));
            return future;
        }

        @Override
        public ProviderDescriptor descriptor() {
            return new ProviderDescriptor(new ProviderId("rank"), "test", "1", "1", List.of(),
                    List.of(new CapabilityDescriptor("rank", "rank", "test", Map.of())));
        }

        @Override
        public ProviderHealth health() {
            return new ProviderHealth(ProviderHealthState.AVAILABLE, "available", "ready", CLOCK.instant());
        }

        @Override
        public CompletionStage<Result<Set<String>>> validateTargets(Set<String> groupNames) {
            CompletableFuture<Result<Set<String>>> future = blocked.get();
            return future == null ? CompletableFuture.completedFuture(Result.success(Set.copyOf(groupNames))) : future;
        }

        @Override
        public CompletionStage<Result<ManagedRankState>> readManagedState(UUID playerId, Set<String> managedGroups) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<Result<RankProjectionResult>> project(RankProjectionRequest request) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryHistory implements ConfigurationHistoryStore {
        private final List<StoredConfigurationRevision> values = new ArrayList<>();

        @Override
        public synchronized void append(StoredConfigurationRevision revision) {
            values.add(revision);
        }

        @Override
        public synchronized void replaceOutcome(StoredConfigurationRevision revision) {
            for (int index = 0; index < values.size(); index++) {
                if (values.get(index).id().equals(revision.id())) {
                    values.set(index, revision);
                    return;
                }
            }
            throw new IllegalStateException("Missing attempted revision");
        }

        @Override
        public synchronized Optional<StoredConfigurationRevision> find(ConfigRevisionId revisionId) {
            return values.stream().filter(value -> value.id().equals(revisionId)).findFirst();
        }

        @Override
        public synchronized List<StoredConfigurationRevision> recent(int limit) {
            return values.reversed().stream().limit(limit).toList();
        }
    }

    private static final class InMemoryStageReferenceMigrationStore implements
            net.maddkraft.maddprestige.core.admin.config.StageReferenceMigrationStore {
        private final boolean changePlannedReferences;
        private int plannedCaptures;

        private InMemoryStageReferenceMigrationStore() {
            this(false);
        }

        private InMemoryStageReferenceMigrationStore(boolean changePlannedReferences) {
            this.changePlannedReferences = changePlannedReferences;
        }

        @Override
        public net.maddkraft.maddprestige.core.admin.config.StageReferenceSnapshot capture(
                Optional<net.maddkraft.maddprestige.core.stage.StageRemapPlan> plan) {
            if (changePlannedReferences && plan.isPresent()) {
                int count = ++plannedCaptures;
                var mapping = plan.orElseThrow().mappings().entrySet().iterator().next();
                List<net.maddkraft.maddprestige.core.admin.config.StageRemapEntry> entries =
                        java.util.stream.IntStream.range(0, count).mapToObj(index ->
                                new net.maddkraft.maddprestige.core.admin.config.StageRemapEntry(
                                        UUID.nameUUIDFromBytes(("remap-player-" + index).getBytes(StandardCharsets.UTF_8)),
                                        mapping.getKey(), mapping.getValue(), index,
                                        new ConfigRevisionId("r_source"))).toList();
                return new net.maddkraft.maddprestige.core.admin.config.StageReferenceSnapshot(
                        Map.of(mapping.getKey(), (long) count),
                        Optional.of(net.maddkraft.maddprestige.core.admin.config.StageRemapSnapshot.create(
                                plan.orElseThrow(), entries)));
            }
            return new net.maddkraft.maddprestige.core.admin.config.StageReferenceSnapshot(Map.of(),
                    plan.map(value -> net.maddkraft.maddprestige.core.admin.config.StageRemapSnapshot.create(
                            value, List.of())));
        }

        @Override
        public net.maddkraft.maddprestige.core.admin.config.ConfigurationStageTransitionExecution beginTransition(
                ConfigRevisionId revision,
                Optional<ConfigRevisionId> priorRevision,
                net.maddkraft.maddprestige.core.config.ContentHash candidateHash,
                Map<StageId, net.maddkraft.maddprestige.core.admin.config.ConfigurationStageReservationKind> stages,
                Optional<net.maddkraft.maddprestige.core.admin.config.StageRemapSnapshot> remap,
                Actor actor,
                String reason,
                Instant occurredAt) {
            return new net.maddkraft.maddprestige.core.admin.config.ConfigurationStageTransitionExecution(
                    revision, priorRevision, candidateHash, stages, Optional.empty());
        }

        @Override
        public void markTransitionApplied(ConfigRevisionId revision, Instant occurredAt) {
        }

        @Override
        public void markTransitionFailedSafe(ConfigRevisionId revision, String detail, Instant occurredAt) {
        }

        @Override
        public void markTransitionNeedsReconciliation(ConfigRevisionId revision, String detail, Instant occurredAt) {
        }

        @Override
        public List<net.maddkraft.maddprestige.core.admin.config.ConfigurationStageTransitionState> transitions(
                int limit) {
            return List.of();
        }

        @Override
        public List<net.maddkraft.maddprestige.core.admin.config.StageRemapReconciliation> unresolved(int limit) {
            return List.of();
        }
    }

    private static final class InMemorySnapshots implements ConfigurationSnapshotStore {
        private Optional<ConfigRevisionId> active = Optional.empty();
        private boolean failNextPrepare;

        private void failNextPrepare() {
            failNextPrepare = true;
        }

        @Override
        public PreparedConfigurationSnapshot prepare(
                ConfigRevisionId revisionId,
                CompiledConfiguration configuration) {
            if (failNextPrepare) {
                failNextPrepare = false;
                throw new IllegalStateException("SNAPSHOT_PREPARATION_FAILURE_INTERNAL");
            }
            Optional<ConfigRevisionId> prior = active;
            BackupMetadata backup = new BackupMetadata(prior.map(ConfigRevisionId::value).orElse("empty"),
                    RevisionHasher.hashText(prior.map(ConfigRevisionId::value).orElse("")), CLOCK.instant(), true);
            return new PreparedConfigurationSnapshot() {
                @Override
                public BackupMetadata backup() {
                    return backup;
                }

                @Override
                public void activate() {
                    active = Optional.of(revisionId);
                }

                @Override
                public void restorePrevious() {
                    active = prior;
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public Optional<ConfigRevisionId> currentRevision() {
            return active;
        }
    }

    private static final class FailingRuntimeSnapshots implements ConfigurationSnapshotStore {
        private final boolean restoreFails;
        private Optional<ConfigRevisionId> active = Optional.empty();

        private FailingRuntimeSnapshots(boolean restoreFails) {
            this.restoreFails = restoreFails;
        }

        @Override
        public PreparedConfigurationSnapshot prepare(
                ConfigRevisionId revisionId,
                CompiledConfiguration configuration) {
            Optional<ConfigRevisionId> prior = active;
            return new PreparedConfigurationSnapshot() {
                @Override
                public BackupMetadata backup() {
                    return new BackupMetadata("runtime-failure", RevisionHasher.hashText("runtime-failure"),
                            CLOCK.instant(), false);
                }

                @Override
                public void activate() {
                    active = Optional.of(revisionId);
                }

                @Override
                public void restorePrevious() {
                    if (restoreFails) {
                        throw new IllegalStateException("RESTORE_FAILURE_INTERNAL");
                    }
                    active = prior;
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public Optional<ConfigRevisionId> currentRevision() {
            return active;
        }
    }

    private static final class FailingAppendHistory implements ConfigurationHistoryStore {
        @Override
        public void append(StoredConfigurationRevision revision) {
            throw new IllegalStateException("HISTORY_APPEND_FAILURE_INTERNAL");
        }

        @Override
        public void replaceOutcome(StoredConfigurationRevision revision) {
            throw new AssertionError("No history outcome exists after append failure");
        }

        @Override
        public Optional<StoredConfigurationRevision> find(ConfigRevisionId revisionId) {
            return Optional.empty();
        }

        @Override
        public List<StoredConfigurationRevision> recent(int limit) {
            return List.of();
        }
    }
}
