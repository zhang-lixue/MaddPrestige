package net.maddkraft.maddprestige.core.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
import net.maddkraft.maddprestige.core.admin.config.PhaseSixConfigurationWorkflow;
import net.maddkraft.maddprestige.core.admin.config.PreparedConfigurationSnapshot;
import net.maddkraft.maddprestige.core.admin.config.StoredConfigurationRevision;
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
import net.maddkraft.maddprestige.core.schema.PhaseSixSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhaseSixConfigurationAdministrationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-16T12:00:00Z"), ZoneOffset.UTC);
    private static final PermissionSubject OWNER = new PermissionSubject(
            new Actor("console", Optional.empty(), "Owner"), PhaseSixPermissions.all());

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
        assertEquals(List.of("Member → Veteran", "Requirement: none", "Cost: none", "Reward: none",
                        "Prestige: disabled"),
                preview.playerExperience());
        assertTrue(fixture.canonical.active().isEmpty());
        AdministrationException arbitrary = assertThrows(AdministrationException.class, () -> wizard.apply(OWNER,
                session, acknowledgements(preview.configuration()), "Unsafe caller codes"));
        assertEquals("config.acknowledgement.server_authority_required", arbitrary.code());
        var authority = wizard.prepareAcknowledgement(OWNER, session);
        PermissionSubject intruder = new PermissionSubject(new Actor("staff", Optional.of(UUID.randomUUID()),
                "Intruder"), PhaseSixPermissions.all());
        assertEquals("setup.session.owner_mismatch", assertThrows(AdministrationException.class, () ->
                wizard.confirmAcknowledgement(intruder, authority.acknowledgementId(), "stolen token")).code());
        wizard.confirmAcknowledgement(OWNER, authority.acknowledgementId(), "Initial internal ladder")
                .toCompletableFuture().join();
        assertTrue(fixture.canonical.active().orElseThrow().compiled().documents().get("progression.yml")
                .contains("order:\n  - member\n  - veteran\n"));
    }

    @Test
    @DisplayName("[A02] Setup wizard generates validated requirement, cost, reward, and Prestige reset semantics")
    void setupWizardBuildsCompleteSimpleConfiguration() {
        ProviderRegistry providers = new ProviderRegistry();
        providers.activate(providers.register("test", new SetupProvider()));
        providers.activate(providers.register("test", new SetupCostProvider()));
        providers.activate(providers.register("test", new SetupRewardProvider()));
        Fixture fixture = new Fixture(providers);
        SetupWizardService wizard = new SetupWizardService(fixture.service, providers);
        UUID session = wizard.start(OWNER);
        wizard.addStage(OWNER, session, new SetupStage(new StageId("member"), "Member", Optional.empty()));
        wizard.addStage(OWNER, session, new SetupStage(new StageId("veteran"), "Veteran", Optional.empty()));
        wizard.configureRequirement(OWNER, session, new SetupRequirement(new RequirementId("play"),
                new ProviderId("setup_metric"), new MetricId("play_time"), "greater-or-equal", "10", "absolute",
                "live"));
        wizard.configureCost(OWNER, session, new SetupCost(new CostId("payment"), new ProviderId("setup_cost"),
                "debit", "currency-amount", "25", "Payment"));
        wizard.configureReward(OWNER, session, new SetupReward(new RewardId("grant"),
                new ProviderId("setup_reward"),
                "grant", "exact-decimal", "1", "Grant"));
        wizard.configurePrestige(OWNER, session, new SetupPrestige(true, Optional.of(new StageId("veteran")),
                Optional.of(new StageId("member"))));

        var preview = wizard.preview(OWNER, session).toCompletableFuture().join();

        assertFalse(preview.configuration().validation().hasErrors(), preview.configuration().validation().toString());
        assertTrue(preview.playerExperience().contains("Requirement: play"));
        assertTrue(preview.playerExperience().contains("Cost: payment"));
        assertTrue(preview.playerExperience().contains("Reward: grant"));
        assertTrue(preview.playerExperience().contains("Prestige: eligible at veteran, resets to member"));
        var authority = wizard.prepareAcknowledgement(OWNER, session);
        wizard.confirmAcknowledgement(OWNER, authority.acknowledgementId(), "Complete simple setup")
                .toCompletableFuture().join();
        Map<String, String> active = fixture.canonical.active().orElseThrow().compiled().documents();
        assertTrue(active.get("progression.yml").contains("requirements: setup_eligibility"));
        assertTrue(active.get("progression.yml").contains("costs: [payment]"));
        assertTrue(active.get("progression.yml").contains("rewards: [grant]"));
        assertTrue(active.get("requirements.yml").contains("metric: play_time"));
        assertTrue(active.get("lifecycle.yml").contains("required-stages: [veteran]"));
        assertTrue(active.get("lifecycle.yml").contains("reset-stage: member"));
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
        assertEquals(List.of("Member → Veteran", "Requirement: none", "Cost: none", "Reward: none",
                        "Prestige: disabled"),
                wizard.preview(OWNER, cancelled).toCompletableFuture().join().playerExperience());
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
    @DisplayName("[A39][Phase6-race] Two admin drafts use CAS so the stale preview cannot overwrite the winner")
    void staleConcurrentDraftFailsClosed() {
        Fixture fixture = new Fixture();
        StoredConfigurationRevision first = fixture.applyInitial(defaultDocuments());
        PermissionSubject adminA = new PermissionSubject(new Actor("staff", Optional.of(UUID.randomUUID()), "A"),
                PhaseSixPermissions.all());
        PermissionSubject adminB = new PermissionSubject(new Actor("staff", Optional.of(UUID.randomUUID()), "B"),
                PhaseSixPermissions.all());
        UUID draftA = fixture.service.beginDraft(adminA, "gui");
        UUID draftB = fixture.service.beginDraft(adminB, "command");
        fixture.service.editScalar(adminA, draftA, "prestige.current-count-increment", "2");
        fixture.service.editScalar(adminB, draftB, "prestige.current-count-increment", "3");
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
                .contains("current-count-increment: 2"));
    }

    @Test
    @DisplayName("[A38][A39] Surgical draft edit preserves comments/order and remains inactive until apply")
    void preservesPresentationAndDraftIsolation() {
        Fixture fixture = new Fixture();
        StoredConfigurationRevision first = fixture.applyInitial(defaultDocuments());
        String before = fixture.canonical.active().orElseThrow().compiled().documents().get("lifecycle.yml");
        UUID draft = fixture.service.beginDraft(OWNER, "command");

        var edited = fixture.service.editScalar(OWNER, draft, "prestige.current-count-increment", "2");
        String after = edited.documents().get("lifecycle.yml");

        assertEquals(before.replace("current-count-increment: 1", "current-count-increment: 2"), after);
        assertEquals(before, fixture.canonical.active().orElseThrow().compiled().documents().get("lifecycle.yml"));
        assertEquals(first.id(), fixture.canonical.active().orElseThrow().revisionId());

        var preview = fixture.service.preview(OWNER, draft).toCompletableFuture().join();
        fixture.service.applyDraft(OWNER, draft, Optional.of(first.id()), acknowledgements(preview),
                "Raise disabled default increment for future use").toCompletableFuture().join();
        assertEquals(after, fixture.canonical.active().orElseThrow().compiled().documents().get("lifecycle.yml"));
        assertTrue(after.contains("# MaddPrestige V2 Phase 4 generic lifecycle defaults."));
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
                finding.code().equals("phase3.provider.unavailable")
                        && finding.path().equals("providers.absent_provider")));
        CompletionException wrapped = assertThrows(CompletionException.class, () -> fixture.service
                .applySetup(OWNER, draft, Set.of(), "Invalid provider test")
                .toCompletableFuture().join());
        AdministrationException failure = (AdministrationException) wrapped.getCause();
        assertEquals("config.validation.blocked", failure.code());
        assertTrue(fixture.canonical.active().isEmpty());
    }

    @Test
    @DisplayName("[A41] Rollback revalidates exact history and creates a new applied revision")
    void rollbackCreatesNewRevisionWithPriorBehavior() {
        Fixture fixture = new Fixture();
        StoredConfigurationRevision first = fixture.applyInitial(defaultDocuments());
        UUID draft = fixture.service.beginDraft(OWNER, "command");
        fixture.service.editScalar(OWNER, draft, "prestige.current-count-increment", "2");
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
                PhaseSixPermissions.CONFIG_APPLY, PhaseSixPermissions.CONFIG_ROLLBACK));
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
                Set.of(PhaseSixPermissions.CONFIG_ROLLBACK));
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
                Set.of(PhaseSixPermissions.CONFIG_APPLY));
        assertEquals("permission.denied", assertThrows(AdministrationException.class, () ->
                fixture.service.prepareAcknowledgement(normalOnly, rollbackDraft)).code());
        assertEquals("permission.denied", assertThrows(AdministrationException.class, () ->
                fixture.service.applyRollback(normalOnly, rollbackDraft, Optional.of(second.id()), Set.of(),
                        "rollback permission missing")).code());
        var rollbackAuthority = fixture.service.prepareAcknowledgement(OWNER, rollbackDraft);
        assertEquals(ConfigurationApplyKind.ROLLBACK, rollbackAuthority.kind());

        PermissionSubject revokedAfterAcknowledgement = new PermissionSubject(OWNER.actor(),
                Set.of(PhaseSixPermissions.CONFIG_APPLY));
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
                "prestige.current-count-increment", "2");
        var guiModel = gui.service.editScalar(OWNER, guiDraft,
                "prestige.current-count-increment", "2");

        assertEquals(commandModel.documents(), guiModel.documents());
        PermissionSubject viewer = new PermissionSubject(new Actor("player", Optional.of(UUID.randomUUID()),
                "Viewer"), Set.of(PhaseSixPermissions.CONFIG_VIEW, PhaseSixPermissions.ADMIN_GUI));
        assertTrue(command.service.active(viewer).isPresent());
        assertThrows(AdministrationException.class, () -> command.service.beginDraft(viewer, "command"));
        assertThrows(AdministrationException.class, () -> command.service.editScalar(
                viewer, commandDraft, "prestige.current-count-increment", "3"));
    }

    @Test
    @DisplayName("[A39][Phase6-race] Edit or cancel during async same-draft prepare cannot activate stale content")
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
    @DisplayName("[A39][Phase6-race] Two async applies of one exact draft have one deterministic winner")
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
    @DisplayName("[Phase6-security] Configuration acknowledgement is exact-draft bound and stale authority is revoked")
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
    @DisplayName("[Phase6-security] Concurrent configuration confirmation consumes one server authority exactly once")
    void concurrentConfigurationAcknowledgementHasOneWinner() {
        Fixture fixture = new Fixture();
        fixture.applyInitial(defaultDocuments());
        UUID draft = fixture.service.beginDraft(OWNER, "gui");
        fixture.service.addStage(OWNER, draft, new StageId("member"), "Member", Optional.empty(), Optional.empty());
        fixture.service.preview(OWNER, draft).toCompletableFuture().join();
        var authority = fixture.service.prepareAcknowledgement(OWNER, draft);
        CountDownLatch start = new CountDownLatch(1);

        var first = CompletableFuture.supplyAsync(() -> confirm(fixture, authority.acknowledgementId(), start));
        var second = CompletableFuture.supplyAsync(() -> confirm(fixture, authority.acknowledgementId(), start));
        start.countDown();
        List<String> results = List.of(first.join(), second.join());

        assertEquals(1, results.stream().filter("success"::equals).count());
        assertEquals(1, results.stream().filter("config.acknowledgement.already_used"::equals).count());
        assertEquals(2, fixture.history.recent(10).size());
    }

    @Test
    @DisplayName("[Phase6-security] Configuration acknowledgement expires at its exact server deadline")
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

    private static Set<String> acknowledgements(
            net.maddkraft.maddprestige.core.admin.config.ConfigurationPreview preview) {
        return preview.validation().findings().stream().map(finding -> finding.code())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static Map<String, String> defaultDocuments() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        result.put("progression.yml", resource("/defaults/progression.yml"));
        result.put("requirements.yml", resource("/defaults/requirements.yml"));
        result.put("rewards.yml", resource("/defaults/rewards.yml"));
        result.put("lifecycle.yml", resource("/defaults/lifecycle.yml"));
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
                order: [first, second]
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
        try (var stream = PhaseSixConfigurationAdministrationTest.class.getResourceAsStream(name)) {
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
        private final InMemoryHistory history = new InMemoryHistory();
        private final InMemorySnapshots snapshots = new InMemorySnapshots();
        private final ConfigurationAdministrationService service;

        private Fixture() {
            this(new ProviderRegistry());
        }

        private Fixture(ProviderRegistry providers) {
            this(providers, CLOCK);
        }

        private Fixture(ProviderRegistry providers, Clock clock) {
            service = new ConfigurationAdministrationService(canonical,
                    new PhaseSixConfigurationWorkflow(canonical, providers,
                            new InMemoryStageReferenceMigrationStore(), List.of()),
                    PhaseSixSchema.create(), history, snapshots, clock);
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
        @Override
        public ProviderDescriptor descriptor() {
            return new ProviderDescriptor(new ProviderId("setup_metric"), "test", "1", "1", List.of(), List.of(
                    new CapabilityDescriptor("metric", "metric", "test", Map.of())));
        }

        @Override
        public ProviderHealth health() {
            return new ProviderHealth(ProviderHealthState.AVAILABLE, "available", "ready", CLOCK.instant());
        }

        @Override
        public Set<net.maddkraft.maddprestige.api.metric.MetricDescriptor> metrics() {
            return Set.of(new net.maddkraft.maddprestige.api.metric.MetricDescriptor(new ProviderId("setup_metric"),
                    new MetricId("play_time"), net.maddkraft.maddprestige.api.metric.MetricValueType.COUNT,
                    Set.of(net.maddkraft.maddprestige.api.metric.MetricOperator.GREATER_OR_EQUAL),
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

    private static final class SetupCostProvider implements net.maddkraft.maddprestige.api.cost.CostProvider {
        @Override
        public ProviderDescriptor descriptor() {
            return setupDescriptor("setup_cost", "cost");
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
        @Override
        public ProviderDescriptor descriptor() {
            return setupDescriptor("setup_reward", "reward");
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
        @Override
        public net.maddkraft.maddprestige.core.admin.config.StageReferenceSnapshot capture(
                Optional<net.maddkraft.maddprestige.core.stage.StageRemapPlan> plan) {
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

        @Override
        public PreparedConfigurationSnapshot prepare(
                ConfigRevisionId revisionId,
                CompiledConfiguration configuration) {
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
    }
}
