package net.maddkraft.maddprestige.platform.paper.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.maddkraft.maddprestige.api.explanation.ExplanationNode;
import net.maddkraft.maddprestige.api.explanation.ExplanationStatus;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.ManualPrestigeAdministrationService;
import net.maddkraft.maddprestige.core.admin.OperationConfirmationService;
import net.maddkraft.maddprestige.core.admin.OperationKind;
import net.maddkraft.maddprestige.core.admin.OperationPreview;
import net.maddkraft.maddprestige.core.admin.OperationPreviewService;
import net.maddkraft.maddprestige.core.admin.OperationExecutionResult;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.AdministrationPermissions;
import net.maddkraft.maddprestige.core.admin.PrestigePlanExecutor;
import net.maddkraft.maddprestige.core.admin.PreparedConfirmation;
import net.maddkraft.maddprestige.core.admin.RankUpPlanExecutor;
import net.maddkraft.maddprestige.core.admin.command.CommandCompletionService;
import net.maddkraft.maddprestige.core.admin.command.CommandResponse;
import net.maddkraft.maddprestige.core.admin.command.ContextualHelpService;
import net.maddkraft.maddprestige.core.admin.command.AdministrationCommandService;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationPreview;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationAdministrationService;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationIntrospectionService;
import net.maddkraft.maddprestige.core.admin.diagnostic.DoctorService;
import net.maddkraft.maddprestige.core.admin.diagnostic.WhyReport;
import net.maddkraft.maddprestige.core.admin.diagnostic.WhyService;
import net.maddkraft.maddprestige.core.admin.player.PlayerProgressViewService;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.core.admin.setup.SetupWizardService;
import net.maddkraft.maddprestige.core.admin.ui.GuiSessionService;
import net.maddkraft.maddprestige.core.config.ContentHash;
import net.maddkraft.maddprestige.core.config.SemanticDiff;
import net.maddkraft.maddprestige.core.plan.RankUpIntent;
import net.maddkraft.maddprestige.core.prestige.PrestigeIntent;
import net.maddkraft.maddprestige.core.stage.StageChangeImpact;
import net.maddkraft.maddprestige.platform.paper.ExecutionThread;
import net.maddkraft.maddprestige.platform.paper.PaperTaskScheduler;
import net.maddkraft.maddprestige.platform.paper.i18n.PaperMessageService;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PaperNumericCommandRoutingTest {
    private static final UUID PLAYER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final ConfigRevisionId REVISION = new ConfigRevisionId("full-stack-command-routing");

    @TempDir
    private Path temporaryDirectory;

    @Test
    @DisplayName("Player routes to visible numeric Prestige only")
    void playerRoutesToVisibleNumericPrestigeOnly() throws IOException {
        Fixture fixture = fixture(AdministrationPermissions.all(), numericPreview());

        fixture.dispatch("player");

        assertEquals(3, fixture.messages.size());
        assertTrue(fixture.text().contains("Prestige: 0"));
        assertTrue(fixture.text().contains("Next: 1"));
        assertTrue(fixture.text().contains("Status: Ready"));
        assertEquals(1, fixture.text().split("Ready", -1).length - 1);
        assertFalse(fixture.text().contains(PLAYER_ID.toString()));
        assertFalse(fixture.text().contains(REVISION.value()));
        assertFalse(fixture.text().contains("rankup.compatibility_only"));
        verify(fixture.previews).simulatePrestige(any(PermissionSubject.class), eq(PLAYER_ID));
        verify(fixture.previews, never()).simulateRankUp(any(PermissionSubject.class), any(UUID.class));
    }

    @Test
    @DisplayName("Player rendering suppresses a redundant standalone readiness line")
    void playerRenderingSuppressesRedundantStandaloneReadiness() throws IOException {
        Fixture fixture = fixture(AdministrationPermissions.all(), numericPreview());
        CommandResponse response = CommandResponse.success("player.progress", List.of(
                MessageReference.of("command.player.concise.prestige", "current", 1),
                MessageReference.of("command.player.concise.next", "target", 2),
                MessageReference.of("command.player.concise.ready"),
                MessageReference.of("command.concise.ready")));

        List<Component> rendered = PaperAdministrationCommandAdapter.renderResponse(response, fixture.paperMessages);
        String text = rendered.stream().map(PlainTextComponentSerializer.plainText()::serialize)
                .reduce((left, right) -> left + "\n" + right).orElse("");

        assertEquals(3, rendered.size());
        assertTrue(text.contains("Status: Ready"));
        assertEquals(1, text.split("Ready", -1).length - 1);
    }

    @Test
    @DisplayName("Normal Why and simulation are bounded gameplay summaries")
    void normalWhyAndSimulationAreConcise() throws IOException {
        Fixture whyFixture = fixture(AdministrationPermissions.all(), numericPreview());
        whyFixture.dispatch("why", "prestige");

        assertTrue(whyFixture.messages.size() <= 8);
        assertTrue(whyFixture.text().contains("Ready"));
        assertTrue(whyFixture.text().contains("Money: 20 / 10 ✓"));
        assertFalse(whyFixture.text().contains("qualification-balance"));
        assertFalse(whyFixture.text().contains("vault_balance"));
        assertFalse(whyFixture.text().contains("COMPILED_EFFECTIVE_CONFIGURATION"));
        assertFalse(whyFixture.text().contains("FLAT(P1)"));
        assertFalse(whyFixture.text().contains(REVISION.value()));

        Fixture simulationFixture = fixture(AdministrationPermissions.all(), numericPreview());
        simulationFixture.dispatch("simulate", "prestige");

        assertTrue(simulationFixture.messages.size() <= 8);
        assertTrue(simulationFixture.text().contains("Prestige 0 → 1"));
        assertTrue(simulationFixture.text().contains("Requirements: Met"));
        assertFalse(simulationFixture.text().contains("qualification-balance"));
        assertFalse(simulationFixture.text().contains("source="));
        assertFalse(simulationFixture.text().contains("revision="));
    }

    @Test
    @DisplayName("Prestige preview is concise and exposes real Confirm/Copy controls")
    void prestigePreviewHasActionableSessionControls() throws IOException {
        Fixture fixture = fixture(AdministrationPermissions.all(), numericPreview());

        fixture.dispatch("prestige");

        assertTrue(fixture.messages.size() <= 8);
        assertTrue(fixture.text().contains("Prestige 0 → 1"));
        assertTrue(fixture.text().contains("Cost: $5"));
        assertTrue(fixture.text().contains("Reward: $1"));
        assertFalse(fixture.text().contains("MetricValue["));
        assertTrue(fixture.text().contains("[Confirm] [Copy ID] Valid this login session"));
        assertFalse(fixture.text().contains(Fixture.CONFIRMATION.toString()));
        assertFalse(fixture.text().contains("provider="));
        assertFalse(fixture.text().contains("formula="));
        assertFalse(fixture.text().contains("revision="));
        assertTrue(fixture.clicks().stream().anyMatch(click -> click.action() == ClickEvent.Action.RUN_COMMAND
                && click.value().equals("/maddprestige confirm " + Fixture.CONFIRMATION)));
        assertTrue(fixture.clicks().stream().anyMatch(click -> click.action() == ClickEvent.Action.COPY_TO_CLIPBOARD
                && click.value().equals(Fixture.CONFIRMATION.toString())));

        assertEquals(List.of(Fixture.CONFIRMATION.toString()), fixture.completions("confirm", ""));
        fixture.dispatch("confirm");
        verify(fixture.confirmations).confirmOnly(any(PermissionSubject.class));
    }

    @Test
    @DisplayName("Why Prestige details is visible through the production dispatcher")
    void whyPrestigeDetailsIsVisible() throws IOException {
        Fixture fixture = fixture(AdministrationPermissions.all(), numericPreview());

        fixture.dispatch("why", "prestige", "details");

        assertTrue(fixture.text().contains("Canonical authorization currently allows"));
        assertTrue(fixture.text().contains("formula=FLAT(P1)"));
        assertTrue(fixture.text().contains("source=COMPILED_EFFECTIVE_CONFIGURATION"));
        assertTrue(fixture.text().contains("— Requirements —"));
        assertTrue(fixture.text().contains(REVISION.value()));
        verify(fixture.why).prestige(any(PermissionSubject.class), eq(PLAYER_ID));
        verify(fixture.why, never()).rankUp(any(PermissionSubject.class), any(UUID.class));
    }

    @Test
    @DisplayName("Simulate Prestige details is fully rendered and visible")
    void simulatePrestigeDetailsIsVisible() throws IOException {
        Fixture fixture = fixture(AdministrationPermissions.all(), numericPreview());

        fixture.dispatch("simulate", "prestige", "details");

        assertTrue(fixture.text().contains("Simulation for " + PLAYER_ID));
        assertTrue(fixture.text().contains("Prestige 0 → 1"));
        assertTrue(fixture.text().contains("scaling=FLAT(P1)"));
        assertTrue(fixture.text().contains("repeatability=ONCE"));
        verify(fixture.previews).simulatePrestige(any(PermissionSubject.class), eq(PLAYER_ID));
        verify(fixture.previews, never()).simulateRankUp(any(PermissionSubject.class), any(UUID.class));
    }

    @Test
    @DisplayName("Active numeric commands cannot reach the retained rank-up executor")
    void activeNumericCommandsCannotReachRankUpExecutor() throws IOException {
        Fixture fixture = fixture(AdministrationPermissions.all(), numericPreview());

        fixture.dispatch("player");
        fixture.dispatch("why", "prestige", "details");
        fixture.dispatch("simulate", "prestige", "details");

        verify(fixture.previews, never()).simulateRankUp(any(PermissionSubject.class), any(UUID.class));
        verify(fixture.why, never()).rankUp(any(PermissionSubject.class), any(UUID.class));
        verifyNoInteractions(fixture.rankUpExecutor);
    }

    @Test
    @DisplayName("Compatibility-only rank permission cannot discover Prestige confirmations")
    void compatibilityRankPermissionCannotDiscoverPrestigeConfirmations() throws IOException {
        Fixture fixture = fixture(Set.of(AdministrationPermissions.RANK_UP), numericPreview());

        assertFalse(fixture.completions("").contains("confirm"));
        assertEquals(List.of(), fixture.completions("confirm", ""));
        verify(fixture.confirmations, never()).validConfirmationIds(any(PermissionSubject.class));
    }

    @Test
    @DisplayName("Retained rank-up command and API boundaries remain visibly blocked")
    void retainedRankUpBoundariesRemainBlocked() throws IOException {
        Fixture fixture = fixture(AdministrationPermissions.all(), numericPreview());

        fixture.dispatch("rankup");

        assertTrue(fixture.text().contains("rankup.compatibility_only"));
        assertTrue(fixture.text().contains("retained compatibility operation"));
        verifyNoInteractions(fixture.rankUpExecutor);

        OperationPreviewService compatibilityBoundary = new OperationPreviewService(
                (RankUpIntent ignored) -> CompletableFuture.failedFuture(new AssertionError("must not authorize")),
                (PrestigeIntent ignored) -> CompletableFuture.failedFuture(new AssertionError("not used")));
        PermissionSubject subject = new PermissionSubject(
                new Actor("player", Optional.of(PLAYER_ID), "full-stack-owner"), Set.of(AdministrationPermissions.RANK_UP));

        CompletionException failure = assertThrows(CompletionException.class, () -> compatibilityBoundary
                .simulateRankUp(subject, PLAYER_ID).toCompletableFuture().join());
        AdministrationException administration = assertInstanceOf(AdministrationException.class, failure.getCause());
        assertTrue(administration.code().equals("rankup.compatibility_only"));
    }

    @Test
    @DisplayName("Permission denial is concise and visible")
    void permissionDenialIsVisible() throws IOException {
        Fixture fixture = fixture(Set.of(), numericPreview());

        fixture.dispatch("simulate", "prestige", "details");

        assertFalse(fixture.messages.isEmpty());
        assertTrue(fixture.text().contains("authority was denied"));
        assertTrue(fixture.text().contains(AdministrationPermissions.PRESTIGE));
    }

    @Test
    @DisplayName("Unexpected response-render failures cannot become silent")
    void renderFailureFallsBackToVisibleFailure() throws IOException {
        OperationPreview invalidRendererInput = numericPreview(List.of(
                MessageReference.of("command.preview.revision", "unsupported", REVISION.value())));
        Fixture fixture = fixture(AdministrationPermissions.all(), invalidRendererInput);

        fixture.dispatch("simulate", "prestige", "details");

        assertTrue(fixture.text().contains("[command.failed]"));
    }

    @Test
    @DisplayName("Successful config validate and diff render through the production dispatcher")
    void successfulConfigValidateAndDiffRenderThroughProductionDispatcher() throws IOException {
        Fixture fixture = fixture(AdministrationPermissions.all(), numericPreview());
        UUID draftId = UUID.fromString("44444444-4444-4444-8444-444444444444");
        when(fixture.configuration.preview(any(PermissionSubject.class), eq(draftId)))
                .thenReturn(CompletableFuture.completedFuture(configurationPreview(draftId, ValidationReport.VALID)));

        fixture.dispatch("config", "validate", draftId.toString());

        assertTrue(fixture.text().contains("Draft " + draftId + ": VALID; changed documents=2; findings=0"));
        assertFalse(fixture.text().contains("[command.failed]"));

        fixture.messages.clear();
        fixture.dispatch("config", "diff", draftId.toString());

        assertTrue(fixture.text().contains("Draft " + draftId + ": VALID; changed documents=2; findings=0"));
        assertTrue(fixture.text().contains("Changed documents: lifecycle.yml, requirements.yml"));
        assertFalse(fixture.text().contains("[command.failed]"));
        verify(fixture.configuration, times(2)).preview(any(PermissionSubject.class), eq(draftId));
        verifyNoMoreInteractions(fixture.configuration);
    }

    @Test
    @DisplayName("Genuine config validation errors remain visible and fail closed")
    void genuineConfigValidationErrorsRemainVisibleAndFailClosed() throws IOException {
        Fixture fixture = fixture(AdministrationPermissions.all(), numericPreview());
        UUID draftId = UUID.fromString("55555555-5555-4555-8555-555555555555");
        ValidationReport blocked = ValidationReport.of(List.of(new ValidationFinding(
                "prestige.test.invalid", ValidationSeverity.ERROR, "prestige.levels.6",
                "The test value is invalid.", "The draft cannot be applied.", "Choose a valid test value.")));
        when(fixture.configuration.preview(any(PermissionSubject.class), eq(draftId)))
                .thenReturn(CompletableFuture.completedFuture(configurationPreview(draftId, blocked)));

        fixture.dispatch("config", "validate", draftId.toString());

        assertTrue(fixture.text().contains("Draft " + draftId + ": BLOCKED; changed documents=2; findings=1"));
        assertTrue(fixture.text().contains("prestige.test.invalid"));
        assertTrue(fixture.text().contains("Next:"));
        assertFalse(fixture.text().contains("[command.failed]"));
        verify(fixture.configuration).preview(any(PermissionSubject.class), eq(draftId));
        verifyNoMoreInteractions(fixture.configuration);
    }

    @Test
    @DisplayName("Draft creation renders exact actionable controls and owned completion")
    void configDraftCreationEliminatesManualIdentifierTranscription() throws IOException {
        Fixture fixture = fixture(AdministrationPermissions.all(), numericPreview());
        UUID draftId = UUID.fromString("66666666-6666-4666-8666-666666666666");
        when(fixture.configuration.beginDraft(any(PermissionSubject.class), eq("command"))).thenReturn(draftId);
        when(fixture.configuration.ownedDraftIds(any(PermissionSubject.class))).thenReturn(List.of(draftId));

        fixture.dispatch("config", "draft");

        assertTrue(fixture.text().contains("Draft created: " + draftId + " [Copy Draft ID]"));
        assertTrue(fixture.text().contains("[Validate] [Diff] [Cancel]"));
        assertTrue(fixture.text().contains("Production remains unchanged"));
        assertTrue(fixture.clicks().stream().anyMatch(click ->
                click.action() == ClickEvent.Action.COPY_TO_CLIPBOARD
                        && click.value().equals(draftId.toString())));
        assertTrue(fixture.clicks().stream().anyMatch(click ->
                click.action() == ClickEvent.Action.RUN_COMMAND
                        && click.value().equals("/maddprestige config validate " + draftId)));
        assertTrue(fixture.clicks().stream().anyMatch(click ->
                click.action() == ClickEvent.Action.RUN_COMMAND
                        && click.value().equals("/maddprestige config diff " + draftId)));
        assertTrue(fixture.clicks().stream().anyMatch(click ->
                click.action() == ClickEvent.Action.RUN_COMMAND
                        && click.value().equals("/maddprestige config cancel " + draftId)));
        assertEquals("Copy draft ID to clipboard", fixture.hover(
                ClickEvent.Action.COPY_TO_CLIPBOARD, draftId.toString()));
        assertEquals("Validate this draft", fixture.hover(
                ClickEvent.Action.RUN_COMMAND, "/maddprestige config validate " + draftId));
        assertEquals("Preview changes in this draft", fixture.hover(
                ClickEvent.Action.RUN_COMMAND, "/maddprestige config diff " + draftId));
        assertEquals("Discard this draft", fixture.hover(
                ClickEvent.Action.RUN_COMMAND, "/maddprestige config cancel " + draftId));
        assertEquals(List.of(draftId.toString()), fixture.completions("config", "validate", ""));
        assertEquals(List.of(draftId.toString()), fixture.completions("config", "diff", ""));
        assertEquals(List.of(draftId.toString()), fixture.completions("config", "cancel", ""));
    }

    private Fixture fixture(Set<String> permissions, OperationPreview preview) throws IOException {
        return new Fixture(permissions, preview, temporaryDirectory.resolve(UUID.randomUUID().toString()));
    }

    private static OperationPreview numericPreview() {
        return numericPreview(List.of(
                MessageReference.of("command.preview.prestige_state_change",
                        "current_prestige", 0, "target_prestige", 1,
                        "current_lifetime", 0, "target_lifetime", 1),
                MessageReference.of("command.preview.cost", "id", "qualification-cost",
                        "provider", "vault_balance", "type", "withdraw", "amount",
                        MetricValue.parse(MetricValueType.CURRENCY_AMOUNT, "5"), "canonical", "5",
                        "value", "Qualification cost"),
                MessageReference.of("command.preview.reward", "id", "qualification-reward",
                        "provider", "vault_balance", "type", "deposit", "amount",
                        MetricValue.parse(MetricValueType.CURRENCY_AMOUNT, "1"), "canonical", "1",
                        "value", "Qualification reward"),
                MessageReference.of("command.preview.milestone",
                        "id", "first-prestige", "repeatability", "ONCE", "rewards", List.of("owner-test"))));
    }

    private static OperationPreview numericPreview(List<MessageReference> semanticDetails) {
        return new OperationPreview(OperationKind.PRESTIGE, PLAYER_ID, true, "Prestige 0 → 1",
                Optional.of(requirements()), List.of("qualification cost"), List.of("qualification reward"),
                List.of("first-prestige → 1 reward"), List.of(), List.of(), REVISION, Map.of(), List.of(),
                semanticDetails, List.of());
    }

    private static WhyReport numericWhy() {
        return new WhyReport(true, List.of(), Optional.of(requirements()), Optional.of(REVISION), List.of());
    }

    private static ConfigurationPreview configurationPreview(UUID draftId, ValidationReport validation) {
        StageChangeImpact impact = new StageChangeImpact(
                List.of(), List.of(), Set.of(), Set.of(), Set.of(), Set.of(), List.of(), Map.of(), false,
                new SemanticDiff(List.of()), validation);
        return new ConfigurationPreview(
                draftId, 1, Optional.of(REVISION), Optional.empty(), new ContentHash("a".repeat(64)),
                Optional.empty(), List.of("lifecycle.yml", "requirements.yml"), validation, impact, false);
    }

    private static ExplanationNode requirements() {
        ExplanationNode leaf = new ExplanationNode("requirement.balance", ExplanationStatus.SATISFIED, "Ready",
                Map.of("requirement", "qualification-balance", "provider", "vault_balance", "metric", "balance",
                        "current", "20", "target", "10", "scope", "ABSOLUTE", "completion", "LIVE",
                        "operator", "GREATER_OR_EQUAL", "effective-formula", "FLAT(P1)"), List.of());
        return new ExplanationNode("requirement.root", ExplanationStatus.SATISFIED, "Ready",
                Map.of("requirement", "prestige", "mode", "ALL"), List.of(leaf));
    }

    private final class Fixture {
        private static final UUID CONFIRMATION = UUID.fromString("33333333-3333-3333-3333-333333333333");
        private final OperationPreviewService previews = mock(OperationPreviewService.class);
        private final WhyService why = mock(WhyService.class);
        private final ConfigurationAdministrationService configuration = mock(ConfigurationAdministrationService.class);
        private final RankUpPlanExecutor rankUpExecutor = mock(RankUpPlanExecutor.class);
        private final OperationConfirmationService confirmations = mock(OperationConfirmationService.class);
        private final List<Component> messages = new ArrayList<>();
        private final Player player = mock(Player.class);
        private final Command command = mock(Command.class);
        private final PaperAdministrationCommandAdapter adapter;
        private final PaperMessageService paperMessages;

        private Fixture(Set<String> permissions, OperationPreview preview, Path localeDirectory) throws IOException {
            when(previews.simulatePrestige(any(PermissionSubject.class), eq(PLAYER_ID))).thenAnswer(invocation -> {
                PermissionSubject subject = invocation.getArgument(0, PermissionSubject.class);
                subject.require(AdministrationPermissions.PRESTIGE);
                return CompletableFuture.completedFuture(preview);
            });
            when(why.prestige(any(PermissionSubject.class), eq(PLAYER_ID))).thenAnswer(invocation -> {
                PermissionSubject subject = invocation.getArgument(0, PermissionSubject.class);
                subject.require(AdministrationPermissions.USE);
                return CompletableFuture.completedFuture(numericWhy());
            });
            when(confirmations.preparePrestige(any(PermissionSubject.class), eq(PLAYER_ID)))
                    .thenReturn(CompletableFuture.completedFuture(new PreparedConfirmation(
                            CONFIRMATION, preview, Instant.now().plus(Duration.ofHours(12)))));
            when(confirmations.validConfirmationIds(any(PermissionSubject.class)))
                    .thenReturn(List.of(CONFIRMATION));
            when(confirmations.confirmOnly(any(PermissionSubject.class))).thenReturn(CompletableFuture.completedFuture(
                    new OperationExecutionResult(OperationId.random(), "COMPLETED", "done")));
            AdministrationCommandService dispatcher = new AdministrationCommandService(
                    mock(ContextualHelpService.class), mock(ConfigurationIntrospectionService.class),
                    configuration, mock(DoctorService.class), why, previews,
                    confirmations, new PlayerProgressViewService(previews), mock(SetupWizardService.class),
                    mock(ManualPrestigeAdministrationService.class), mock(GuiSessionService.class),
                    () -> Optional.of(REVISION), Runnable::run);
            paperMessages = PaperMessageService.open(localeDirectory,
                    PaperMessageService.class.getClassLoader(), ignored -> { });
            adapter = new PaperAdministrationCommandAdapter(
                    dispatcher, new CommandCompletionService(confirmations, configuration),
                    immediateScheduler(), paperMessages);

            when(player.getUniqueId()).thenReturn(PLAYER_ID);
            when(player.getName()).thenReturn("full-stack-owner");
            when(player.hasPermission(anyString())).thenAnswer(invocation ->
                    permissions.contains(invocation.getArgument(0, String.class)));
            doAnswer(invocation -> {
                messages.add(invocation.getArgument(0, Component.class));
                return null;
            }).when(player).sendMessage(any(Component.class));
        }

        private void dispatch(String... arguments) {
            assertTrue(adapter.onCommand(player, command, "maddprestige", arguments));
        }

        private String text() {
            PlainTextComponentSerializer serializer = PlainTextComponentSerializer.plainText();
            return messages.stream().map(serializer::serialize).reduce((left, right) -> left + "\n" + right)
                    .orElse("");
        }

        private List<String> completions(String... arguments) {
            return adapter.onTabComplete(player, command, "maddprestige", arguments);
        }

        private List<ClickEvent> clicks() {
            ArrayList<ClickEvent> result = new ArrayList<>();
            messages.forEach(component -> collectClicks(component, result));
            return List.copyOf(result);
        }

        private String hover(ClickEvent.Action action, String value) {
            Component actionComponent = messages.stream()
                    .map(component -> findAction(component, action, value))
                    .flatMap(Optional::stream)
                    .findFirst()
                    .orElseThrow();
            Component hoverText = assertInstanceOf(Component.class, actionComponent.hoverEvent().value());
            return PlainTextComponentSerializer.plainText().serialize(hoverText);
        }

        private Optional<Component> findAction(Component component, ClickEvent.Action action, String value) {
            ClickEvent click = component.clickEvent();
            if (click != null && click.action() == action && click.value().equals(value)) {
                return Optional.of(component);
            }
            return component.children().stream()
                    .map(child -> findAction(child, action, value))
                    .flatMap(Optional::stream)
                    .findFirst();
        }

        private void collectClicks(Component component, List<ClickEvent> result) {
            if (component.clickEvent() != null) {
                result.add(component.clickEvent());
            }
            component.children().forEach(child -> collectClicks(child, result));
        }
    }

    private static PaperTaskScheduler immediateScheduler() {
        return new PaperTaskScheduler() {
            @Override
            public <T> CompletionStage<T> submit(ExecutionThread thread, Supplier<T> task) {
                try {
                    return CompletableFuture.completedFuture(task.get());
                } catch (RuntimeException exception) {
                    return CompletableFuture.failedFuture(exception);
                }
            }
        };
    }
}
