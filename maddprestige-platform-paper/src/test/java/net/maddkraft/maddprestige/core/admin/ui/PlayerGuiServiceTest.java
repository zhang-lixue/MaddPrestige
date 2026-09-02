package net.maddkraft.maddprestige.core.admin.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.explanation.ExplanationNode;
import net.maddkraft.maddprestige.api.explanation.ExplanationStatus;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.OperationConfirmationService;
import net.maddkraft.maddprestige.core.admin.OperationExecutionResult;
import net.maddkraft.maddprestige.core.admin.OperationKind;
import net.maddkraft.maddprestige.core.admin.OperationPreview;
import net.maddkraft.maddprestige.core.admin.OperationPreviewService;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.admin.PreparedConfirmation;
import net.maddkraft.maddprestige.core.admin.ManualPrestigeAdministrationService;
import net.maddkraft.maddprestige.core.admin.command.CommandInvocation;
import net.maddkraft.maddprestige.core.admin.command.ContextualHelpService;
import net.maddkraft.maddprestige.core.admin.command.PhaseSixCommandService;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationAdministrationService;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationIntrospectionService;
import net.maddkraft.maddprestige.core.admin.diagnostic.DoctorService;
import net.maddkraft.maddprestige.core.admin.diagnostic.WhyService;
import net.maddkraft.maddprestige.core.admin.player.PlayerProgressView;
import net.maddkraft.maddprestige.core.admin.player.PlayerProgressViewService;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.core.admin.setup.SetupWizardService;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlayerGuiServiceTest {
    private static final UUID PLAYER = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID CONFIRMATION = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final ConfigRevisionId REVISION = new ConfigRevisionId("phase9f-player-gui");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);
    private final AtomicReference<Optional<ConfigRevisionId>> revision =
            new AtomicReference<>(Optional.of(REVISION));
    private PlayerProgressViewService progress;
    private OperationPreviewService previews;
    private OperationConfirmationService confirmations;
    private GuiSessionService sessions;
    private PlayerGuiService service;

    @BeforeEach
    void setUp() {
        progress = mock(PlayerProgressViewService.class);
        previews = mock(OperationPreviewService.class);
        confirmations = mock(OperationConfirmationService.class);
        GuiConfigurationAuthority configuration = mock(GuiConfigurationAuthority.class);
        sessions = new GuiSessionService(revision::get, (subject, action) ->
                CompletableFuture.completedFuture(MessageReference.of("unused")), configuration,
                Duration.ofMinutes(5), CLOCK);
        service = new PlayerGuiService(sessions, progress, previews, confirmations);
    }

    @Test
    @DisplayName("[Phase 9F-A] Player GUI opening is self-only, permission-checked, compact, and canonical")
    void opensCompactAuthorizedPlayerView() {
        OperationPreview preview = ready("ALL", "2", "2", true);
        when(progress.view(any(), eq(PLAYER)))
                .thenReturn(CompletableFuture.completedFuture(new PlayerProgressView(PLAYER, preview)));

        AdministrationException denied = assertThrows(AdministrationException.class,
                () -> service.open(subject(Set.of()), PLAYER));
        assertEquals("permission.denied", denied.code());

        GuiSessionView view = service.open(player(), PLAYER).toCompletableFuture().join();

        assertEquals(GuiScreenKind.PLAYER, view.screen());
        assertEquals(27, view.inventorySize());
        assertEquals("gui.title.player", view.title().key());
        assertEquals(Optional.of("2"), view.title().argument("current"));
        assertEquals(GuiItemIcon.PROGRESS, itemAt(view, 4).icon());
        assertEquals(GuiItemIcon.BALANCE, itemAt(view, 10).icon());
        assertEquals(GuiItemIcon.REQUIREMENTS, itemAt(view, 13).icon());
        assertEquals(GuiItemIcon.REWARD, itemAt(view, 16).icon());
        assertEquals(GuiItemIcon.PRESTIGE, itemAt(view, 22).icon());
        assertEquals(GuiItemIcon.CLOSE, itemAt(view, 26).icon());
        assertEquals("gui.item.requirements.progress.complete",
                item(view, GuiItemIcon.REQUIREMENTS).lore().getFirst().key());
        assertTrue(itemAt(view, 22).lore().stream()
                .anyMatch(line -> line.argument("current").filter("2"::equals).isPresent()
                        && line.argument("target").filter("3"::equals).isPresent()));
        assertTrue(view.items().stream().noneMatch(item -> item.icon() == GuiItemIcon.READY
                || item.icon() == GuiItemIcon.BLOCKED));
        assertEquals("gui.item.balance.title", itemAt(view, 10).title().key());
        assertEquals(Optional.of("$12"), itemAt(view, 10).lore().getFirst().argument("current"));
        assertTrue(view.items().stream().noneMatch(item -> item.icon() == GuiItemIcon.COST));
        assertTrue(view.items().stream().anyMatch(item -> item.icon() == GuiItemIcon.REWARD
                && item.lore().stream().anyMatch(line -> line.argument("value").filter("$1"::equals).isPresent())));
        assertTrue(view.items().stream().filter(item -> item.icon() == GuiItemIcon.BORDER_PURPLE
                        || item.icon() == GuiItemIcon.BORDER_AQUA)
                .allMatch(item -> item.actionId().isEmpty() && item.lore().isEmpty()));
        assertTrue(view.items().stream().flatMap(item -> item.lore().stream())
                .noneMatch(line -> line.diagnosticForm().contains("MetricValue[")
                        || line.diagnosticForm().contains("zero-write")
                        || line.diagnosticForm().contains("canonical")
                        || line.arguments().containsKey("provider")
                        || line.arguments().containsKey("revision")));
    }

    @Test
    @DisplayName("[Phase 9F-A] No arguments and explicit gui both open Player GUI for an authorized non-OP player")
    void routesBothPlayerGuiEntryPoints() {
        PlayerGuiService route = mock(PlayerGuiService.class);
        GuiSessionView view = new GuiSessionView(UUID.randomUUID(), GuiAudience.PLAYER,
                MessageReference.of("gui.title.player"), List.of(), CLOCK.instant().plus(Duration.ofMinutes(5)),
                GuiScreenKind.PLAYER, 27, List.of());
        when(route.open(any(), eq(PLAYER))).thenReturn(CompletableFuture.completedFuture(view));
        PhaseSixCommandService commands = new PhaseSixCommandService(
                mock(ContextualHelpService.class), mock(ConfigurationIntrospectionService.class),
                mock(ConfigurationAdministrationService.class), mock(DoctorService.class), mock(WhyService.class),
                previews, confirmations, progress, mock(SetupWizardService.class),
                mock(ManualPrestigeAdministrationService.class), sessions, route, revision::get, Runnable::run);
        PermissionSubject authorizedPlayer = player();

        var primary = commands.execute(new CommandInvocation(authorizedPlayer, List.of()))
                .toCompletableFuture().join();
        var explicit = commands.execute(new CommandInvocation(authorizedPlayer, List.of("gui")))
                .toCompletableFuture().join();

        assertEquals(GuiAudience.PLAYER, primary.guiView().orElseThrow().audience());
        assertEquals(GuiAudience.PLAYER, explicit.guiView().orElseThrow().audience());
        assertTrue(primary.messages().isEmpty());
        assertTrue(explicit.messages().isEmpty());
        verify(route, org.mockito.Mockito.times(2)).open(any(), eq(PLAYER));
    }

    @Test
    @DisplayName("[Phase 9F-A] Player requirements hide modes and use concise gameplay labels")
    void rendersConcisePlayerRequirements() {
        for (String mode : List.of("ALL", "ANY", "X_OF_N")) {
            OperationPreview preview = ready(mode, "1", mode.equals("ALL") ? "2" : "1", false);
            when(progress.view(any(), eq(PLAYER)))
                    .thenReturn(CompletableFuture.completedFuture(new PlayerProgressView(PLAYER, preview)));

            GuiSessionView view = service.open(player(), PLAYER).toCompletableFuture().join();
            GuiDisplayItem requirements = item(view, GuiItemIcon.REQUIREMENTS);
            MessageReference progressLine = requirements.lore().getFirst();
            assertEquals("gui.item.requirements.progress.incomplete", progressLine.key());
            assertEquals(Optional.of("1"), progressLine.argument("progress"));
            assertEquals(Optional.of("2"), progressLine.argument("total"));
            assertEquals("gui.item.separator", requirements.lore().get(1).key());
            assertEquals(4, requirements.lore().size());
            assertTrue(requirements.lore().stream().noneMatch(line ->
                    line.key().equals("gui.item.requirements.mode")
                            || line.key().equals("gui.item.requirements.required")
                            || line.arguments().containsKey("mode")
                            || line.arguments().containsKey("threshold")));
            assertEquals(2, requirements.lore().stream()
                    .filter(line -> line.key().startsWith("gui.item.requirement.")).count());
            MessageReference money = requirements.lore().stream()
                    .filter(line -> line.argument("label").filter("Money"::equals).isPresent())
                    .findFirst().orElseThrow();
            assertEquals("gui.item.requirement.money.met", money.key());
            assertEquals(Optional.of("$12"), money.argument("current"));
            assertEquals(Optional.of("$10"), money.argument("target"));
            MessageReference skillLevel = requirements.lore().stream()
                    .filter(line -> line.argument("label").filter("Total Skill Level"::equals).isPresent())
                    .findFirst().orElseThrow();
            assertEquals("gui.item.requirement.total_skill_level.met", skillLevel.key());
            assertEquals(Optional.of("1"), skillLevel.argument("current"));
            assertEquals(Optional.of("1"), skillLevel.argument("target"));
            assertTrue(requirements.lore().stream().noneMatch(line ->
                    line.argument("label").filter("mcMMO"::equalsIgnoreCase).isPresent()));
        }
    }

    @Test
    @DisplayName("[Phase 9F-A] Stable information slots render current balance and no rewards truthfully")
    void rendersStableEmptyInformationSections() {
        OperationPreview preview = preview("ALL", "2", "2", true, List.of(), List.of(), List.of());
        when(progress.view(any(), eq(PLAYER)))
                .thenReturn(CompletableFuture.completedFuture(new PlayerProgressView(PLAYER, preview)));

        GuiSessionView view = service.open(player(), PLAYER).toCompletableFuture().join();

        assertEquals(GuiItemIcon.BALANCE, itemAt(view, 10).icon());
        assertEquals(GuiItemIcon.REQUIREMENTS, itemAt(view, 13).icon());
        assertEquals(GuiItemIcon.REWARD, itemAt(view, 16).icon());
        assertEquals(Optional.of("$12"), itemAt(view, 10).lore().getFirst().argument("current"));
        assertEquals(Optional.of("None"), itemAt(view, 16).lore().getFirst().argument("value"));
        assertFalse(view.items().stream().anyMatch(item -> item.icon() == GuiItemIcon.MILESTONE));
    }

    @Test
    @DisplayName("[Phase 9F-A] A canonical zero balance remains visible rather than disappearing")
    void rendersZeroBalanceTruthfully() {
        OperationPreview preview = preview("ALL", "2", "2", true, List.of("$0"), List.of("$0"), List.of());
        preview = withBalanceProjection(preview, "0", "0");
        when(progress.view(any(), eq(PLAYER)))
                .thenReturn(CompletableFuture.completedFuture(new PlayerProgressView(PLAYER, preview)));
        when(previews.simulatePrestige(any(), eq(PLAYER))).thenReturn(CompletableFuture.completedFuture(preview));

        GuiSessionView main = service.open(player(), PLAYER).toCompletableFuture().join();
        GuiSessionView previewView = click(main, GuiActionKind.SIMULATE_PRESTIGE).nextView().orElseThrow();

        assertEquals(Optional.of("$0"), itemAt(main, 10).lore().getFirst().argument("current"));
        assertEquals(Optional.of("$0"), itemAt(previewView, 10).lore().getFirst().argument("current"));
        assertEquals(Optional.of("$0"), itemAt(previewView, 10).lore().getFirst().argument("projected"));
        assertEquals(Optional.of("$0"), itemAt(main, 16).lore().getFirst().argument("value"));
    }

    @Test
    @DisplayName("[Phase 9F-A] Ready Preview Confirm uses canonical prepare and consume without another GUI")
    void readyPreviewConfirmExecutesCanonicallyWithoutAnotherGui() {
        OperationPreview preview = ready("ALL", "2", "2", true);
        when(progress.view(any(), eq(PLAYER)))
                .thenReturn(CompletableFuture.completedFuture(new PlayerProgressView(PLAYER, preview)));
        when(previews.simulatePrestige(any(), eq(PLAYER))).thenReturn(CompletableFuture.completedFuture(preview));
        when(confirmations.preparePrestige(any(), eq(PLAYER))).thenReturn(CompletableFuture.completedFuture(
                new PreparedConfirmation(CONFIRMATION, preview, CLOCK.instant().plus(Duration.ofHours(1)))));
        when(confirmations.confirm(any(), eq(CONFIRMATION))).thenReturn(CompletableFuture.completedFuture(
                new OperationExecutionResult(OperationId.random(), "COMPLETED", "done")));

        GuiSessionView main = service.open(player(), PLAYER).toCompletableFuture().join();
        GuiSessionView previewView = click(main, GuiActionKind.SIMULATE_PRESTIGE).nextView().orElseThrow();
        PlayerGuiInteractionResult result = click(previewView, GuiActionKind.PREPARE_PRESTIGE);

        assertEquals(GuiScreenKind.PRESTIGE_PREVIEW, previewView.screen());
        assertEquals(GuiScreenKind.PLAYER, result.nextView().orElseThrow().screen());
        assertEquals("gui.title.prestige_preview", previewView.title().key());
        assertEquals(Optional.of("2"), previewView.title().argument("current"));
        assertEquals(Optional.of("3"), previewView.title().argument("target"));
        GuiDisplayItem confirmItem = item(previewView, GuiItemIcon.CONFIRM);
        assertEquals(22, confirmItem.slot());
        assertEquals("gui.action.confirm", confirmItem.title().key());
        assertTrue(confirmItem.lore().isEmpty());
        assertTrue(confirmItem.actionId().isPresent());
        assertTrue(previewView.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.PREPARE_PRESTIGE && action.mutating()));
        assertTrue(main.actions().stream().noneMatch(action -> action.kind() == GuiActionKind.CONFIRM_PRESTIGE));
        assertTrue(previewView.actions().stream().noneMatch(action -> action.kind() == GuiActionKind.CONFIRM_PRESTIGE));
        assertTrue(List.of(main, previewView, result.nextView().orElseThrow()).stream()
                .noneMatch(view -> view.screen() == GuiScreenKind.PRESTIGE_CONFIRMATION));
        assertTrue(result.messages().isEmpty());
        verify(previews).simulatePrestige(any(), eq(PLAYER));
        verify(confirmations).preparePrestige(any(), eq(PLAYER));
        verify(confirmations).confirm(any(), eq(CONFIRMATION));
    }

    @Test
    @DisplayName("[Phase 9F-A owner UX] Blocked Preview keeps an inert red Not Ready control")
    void blockedPreviewCannotCreateConfirmation() {
        OperationPreview blocked = blockedCostPreview();
        when(progress.view(any(), eq(PLAYER)))
                .thenReturn(CompletableFuture.completedFuture(new PlayerProgressView(PLAYER, blocked)));
        when(previews.simulatePrestige(any(), eq(PLAYER))).thenReturn(CompletableFuture.completedFuture(blocked));

        GuiSessionView main = service.open(player(), PLAYER).toCompletableFuture().join();
        GuiSessionView previewView = click(main, GuiActionKind.SIMULATE_PRESTIGE).nextView().orElseThrow();

        assertTrue(main.items().stream().noneMatch(item -> item.icon() == GuiItemIcon.READY
                || item.icon() == GuiItemIcon.BLOCKED));
        GuiDisplayItem blockedItem = itemAt(previewView, 22);
        assertEquals(GuiItemIcon.BLOCKED, blockedItem.icon());
        assertEquals("gui.item.blocked.title", blockedItem.title().key());
        assertTrue(blockedItem.lore().isEmpty());
        assertTrue(blockedItem.actionId().isEmpty());
        assertFalse(blockedItem.highlighted());
        assertTrue(previewView.actions().stream().anyMatch(action -> action.kind() == GuiActionKind.BACK_PLAYER));
        assertTrue(previewView.actions().stream().anyMatch(action -> action.kind() == GuiActionKind.CLOSE_PLAYER
                && action.label().key().equals("gui.action.close")));
        assertTrue(previewView.actions().stream().noneMatch(action ->
                action.kind() == GuiActionKind.PREPARE_PRESTIGE
                        || action.kind() == GuiActionKind.CONFIRM_PRESTIGE));
        for (GuiSessionView view : List.of(main, previewView)) {
            assertEquals(GuiItemIcon.BALANCE, itemAt(view, 10).icon());
            assertEquals(GuiItemIcon.REQUIREMENTS, itemAt(view, 13).icon());
            assertEquals(GuiItemIcon.REWARD, itemAt(view, 16).icon());
            assertEquals(Optional.of("$1"), itemAt(view, 16).lore().getFirst().argument("value"));
            assertTrue(view.items().stream().noneMatch(item -> item.icon() == GuiItemIcon.COST
                    || item.title().key().equals("gui.item.cost.title")));
        }
        assertEquals(Optional.of("$12"), itemAt(main, 10).lore().getFirst().argument("current"));
        assertEquals(Optional.of("$12"), itemAt(previewView, 10).lore().getFirst().argument("current"));
        assertEquals(Optional.of("$8"), itemAt(previewView, 10).lore().getFirst().argument("projected"));
        verify(confirmations, never()).preparePrestige(any(), any());
    }

    @Test
    @DisplayName("[Phase 9F-A] Main and Preview information topology is stable across consecutive Prestige levels")
    void keepsInformationTopologyAcrossPrestigeLevels() {
        for (int current : List.of(3, 4)) {
            OperationPreview preview = transition(current, current + 1);
            when(progress.view(any(), eq(PLAYER)))
                    .thenReturn(CompletableFuture.completedFuture(new PlayerProgressView(PLAYER, preview)));
            when(previews.simulatePrestige(any(), eq(PLAYER)))
                    .thenReturn(CompletableFuture.completedFuture(preview));

            GuiSessionView main = service.open(player(), PLAYER).toCompletableFuture().join();
            GuiSessionView previewView = click(main, GuiActionKind.SIMULATE_PRESTIGE).nextView().orElseThrow();

            for (GuiSessionView view : List.of(main, previewView)) {
                assertEquals(GuiItemIcon.PROGRESS, itemAt(view, 4).icon());
                assertEquals(GuiItemIcon.BALANCE, itemAt(view, 10).icon());
                assertEquals(GuiItemIcon.REQUIREMENTS, itemAt(view, 13).icon());
                assertEquals(GuiItemIcon.REWARD, itemAt(view, 16).icon());
            }
        }
    }

    @Test
    @DisplayName("[Phase 9F-A] Preview execution action is single-use and cannot replay")
    void previewExecutionClickCannotReplay() {
        OperationPreview preview = ready("ALL", "2", "2", true);
        when(progress.view(any(), eq(PLAYER)))
                .thenReturn(CompletableFuture.completedFuture(new PlayerProgressView(PLAYER, preview)));
        when(previews.simulatePrestige(any(), eq(PLAYER))).thenReturn(CompletableFuture.completedFuture(preview));
        when(confirmations.preparePrestige(any(), eq(PLAYER))).thenReturn(CompletableFuture.completedFuture(
                new PreparedConfirmation(CONFIRMATION, preview, CLOCK.instant().plus(Duration.ofHours(1)))));
        when(confirmations.confirm(any(), eq(CONFIRMATION))).thenReturn(CompletableFuture.completedFuture(
                new OperationExecutionResult(OperationId.random(), "COMPLETED", "done")));
        GuiSessionView main = service.open(player(), PLAYER).toCompletableFuture().join();
        GuiSessionView previewView = click(main, GuiActionKind.SIMULATE_PRESTIGE).nextView().orElseThrow();
        GuiAction confirm = action(previewView, GuiActionKind.PREPARE_PRESTIGE);

        service.click(player(), previewView.sessionId(), confirm.actionId()).toCompletableFuture().join();
        AdministrationException replayed = assertThrows(AdministrationException.class,
                () -> service.click(player(), previewView.sessionId(), confirm.actionId()));
        assertEquals("gui.session.expired", replayed.code());
        verify(confirmations).preparePrestige(any(), eq(PLAYER));
        verify(confirmations).confirm(any(), eq(CONFIRMATION));
    }

    @Test
    @DisplayName("[Phase 9F-A] Confirm delegates exact canonical ID and refreshes backend state")
    void confirmsAndRefreshesCanonicalState() {
        OperationPreview before = ready("ALL", "2", "2", true);
        OperationPreview after = withBalanceProjection(transition(3, 4), "8", "4");
        AtomicInteger reads = new AtomicInteger();
        when(progress.view(any(), eq(PLAYER))).thenAnswer(ignored -> CompletableFuture.completedFuture(
                new PlayerProgressView(PLAYER, reads.getAndIncrement() == 0 ? before : after)));
        when(previews.simulatePrestige(any(), eq(PLAYER))).thenReturn(CompletableFuture.completedFuture(before));
        when(confirmations.preparePrestige(any(), eq(PLAYER))).thenReturn(CompletableFuture.completedFuture(
                new PreparedConfirmation(CONFIRMATION, before, CLOCK.instant().plus(Duration.ofHours(1)))));
        when(confirmations.confirm(any(), eq(CONFIRMATION))).thenReturn(CompletableFuture.completedFuture(
                new OperationExecutionResult(OperationId.random(), "COMPLETED", "done")));

        GuiSessionView main = service.open(player(), PLAYER).toCompletableFuture().join();
        GuiSessionView previewView = click(main, GuiActionKind.SIMULATE_PRESTIGE).nextView().orElseThrow();
        PlayerGuiInteractionResult result = click(previewView, GuiActionKind.PREPARE_PRESTIGE);

        GuiSessionView refreshed = result.nextView().orElseThrow();
        assertEquals(GuiScreenKind.PLAYER, refreshed.screen());
        assertTrue(result.messages().isEmpty());
        assertTrue(item(refreshed, GuiItemIcon.PRESTIGE).lore().stream()
                .anyMatch(line -> line.argument("current").filter("3"::equals).isPresent()));
        assertEquals(Optional.of("$8"), item(refreshed, GuiItemIcon.BALANCE).lore().getFirst()
                .argument("current"));
        verify(confirmations).confirm(any(), eq(CONFIRMATION));
        verify(progress, org.mockito.Mockito.times(2)).view(any(), eq(PLAYER));
    }

    @Test
    @DisplayName("[Phase 9F-A] Final-state revalidation failure refreshes truthfully without mutation success")
    void staleReadyPreviewRefreshesFailClosed() {
        OperationPreview ready = ready("ALL", "2", "2", true);
        OperationPreview blocked = blockedCostPreview();
        AtomicInteger reads = new AtomicInteger();
        when(progress.view(any(), eq(PLAYER))).thenAnswer(ignored -> CompletableFuture.completedFuture(
                new PlayerProgressView(PLAYER, reads.getAndIncrement() == 0 ? ready : blocked)));
        when(previews.simulatePrestige(any(), eq(PLAYER))).thenReturn(CompletableFuture.completedFuture(ready));
        when(confirmations.preparePrestige(any(), eq(PLAYER))).thenReturn(CompletableFuture.completedFuture(
                new PreparedConfirmation(CONFIRMATION, ready, CLOCK.instant().plus(Duration.ofHours(1)))));
        when(confirmations.confirm(any(), eq(CONFIRMATION))).thenReturn(CompletableFuture.failedFuture(
                new AdministrationException("confirmation.revalidation_failed",
                        "Current state is no longer executable.", "Request a fresh preview.")));

        GuiSessionView main = service.open(player(), PLAYER).toCompletableFuture().join();
        GuiSessionView previewView = click(main, GuiActionKind.SIMULATE_PRESTIGE).nextView().orElseThrow();
        PlayerGuiInteractionResult result = click(previewView, GuiActionKind.PREPARE_PRESTIGE);

        GuiSessionView refreshed = result.nextView().orElseThrow();
        assertEquals(GuiScreenKind.PLAYER, refreshed.screen());
        assertTrue(result.messages().isEmpty());
        assertTrue(item(refreshed, GuiItemIcon.PROGRESS).lore().stream()
                .anyMatch(line -> line.key().equals("gui.item.progress.not_ready")));
        verify(confirmations).preparePrestige(any(), eq(PLAYER));
        verify(confirmations).confirm(any(), eq(CONFIRMATION));
    }

    @Test
    @DisplayName("[Phase 9F-A] Logout, replacement, revision change, and permission loss fail closed")
    void invalidatesUnsafeSessions() {
        OperationPreview preview = ready("ALL", "2", "2", true);
        when(progress.view(any(), eq(PLAYER)))
                .thenReturn(CompletableFuture.completedFuture(new PlayerProgressView(PLAYER, preview)));

        GuiSessionView logout = service.open(player(), PLAYER).toCompletableFuture().join();
        service.invalidatePlayer(PLAYER);
        assertEquals("gui.session.expired", failure(logout, player()).code());

        GuiSessionView replaced = service.open(player(), PLAYER).toCompletableFuture().join();
        service.open(player(), PLAYER).toCompletableFuture().join();
        assertEquals("gui.session.expired", failure(replaced, player()).code());

        GuiSessionView stale = service.open(player(), PLAYER).toCompletableFuture().join();
        revision.set(Optional.of(new ConfigRevisionId("changed")));
        GuiAction staleAction = action(stale, GuiActionKind.SIMULATE_PRESTIGE);
        PlayerGuiInteractionResult refreshed = service.click(
                player(), stale.sessionId(), staleAction.actionId()).toCompletableFuture().join();
        assertEquals(GuiScreenKind.PLAYER, refreshed.nextView().orElseThrow().screen());
        assertTrue(refreshed.messages().isEmpty());

        revision.set(Optional.of(REVISION));
        GuiSessionView permission = service.open(player(), PLAYER).toCompletableFuture().join();
        assertEquals("permission.denied", failure(permission, subject(Set.of(PhaseSixPermissions.USE))).code());
        verify(previews, never()).simulatePrestige(any(), any());
    }

    @Test
    @DisplayName("[Phase 9F-A correction] Fresh Main opens Preview on the first click and retires only the old view")
    void freshMainHandsOffToPreviewOnFirstClick() {
        OperationPreview preview = ready("ALL", "2", "2", true);
        when(progress.view(any(), eq(PLAYER)))
                .thenReturn(CompletableFuture.completedFuture(new PlayerProgressView(PLAYER, preview)));
        when(previews.simulatePrestige(any(), eq(PLAYER)))
                .thenReturn(CompletableFuture.completedFuture(preview));

        GuiSessionView main = service.open(player(), PLAYER).toCompletableFuture().join();
        PlayerGuiInteractionResult firstClick = click(main, GuiActionKind.SIMULATE_PRESTIGE);
        GuiSessionView destination = firstClick.nextView().orElseThrow();

        assertEquals(GuiScreenKind.PRESTIGE_PREVIEW, destination.screen());
        assertTrue(firstClick.messages().isEmpty());
        assertEquals("gui.session.expired", failure(main, player()).code());

        PlayerGuiInteractionResult back = click(destination, GuiActionKind.BACK_PLAYER);
        GuiSessionView returned = back.nextView().orElseThrow();
        assertEquals(GuiScreenKind.PLAYER, returned.screen());
        assertTrue(back.messages().isEmpty());

        PlayerGuiInteractionResult close = click(returned, GuiActionKind.CLOSE_PLAYER);
        assertTrue(close.close());
        assertTrue(close.nextView().isEmpty());
        assertTrue(close.messages().isEmpty());
        verify(previews).simulatePrestige(any(), eq(PLAYER));
    }

    private AdministrationException failure(GuiSessionView view, PermissionSubject subject) {
        GuiAction action = action(view, view.screen() == GuiScreenKind.PLAYER
                ? GuiActionKind.SIMULATE_PRESTIGE : GuiActionKind.CLOSE_PLAYER);
        return assertThrows(AdministrationException.class,
                () -> service.click(subject, view.sessionId(), action.actionId()));
    }

    private PlayerGuiInteractionResult click(GuiSessionView view, GuiActionKind kind) {
        GuiAction action = action(view, kind);
        return service.click(player(), view.sessionId(), action.actionId()).toCompletableFuture().join();
    }

    private static GuiAction action(GuiSessionView view, GuiActionKind kind) {
        return view.actions().stream().filter(value -> value.kind() == kind).findFirst().orElseThrow();
    }

    private static GuiDisplayItem item(GuiSessionView view, GuiItemIcon icon) {
        return view.items().stream().filter(value -> value.icon() == icon).findFirst().orElseThrow();
    }

    private static GuiDisplayItem itemAt(GuiSessionView view, int slot) {
        return view.items().stream().filter(value -> value.slot() == slot).findFirst().orElseThrow();
    }

    private static PermissionSubject player() {
        return subject(Set.of(PhaseSixPermissions.USE, PhaseSixPermissions.PRESTIGE));
    }

    private static PermissionSubject subject(Set<String> permissions) {
        return new PermissionSubject(new Actor("PLAYER", Optional.of(PLAYER), "Player"), permissions);
    }

    private static OperationPreview ready(
            String mode,
            String progress,
            String threshold,
            boolean executable) {
        return preview(mode, progress, threshold, executable, List.of("$5"), List.of("$1"),
                List.of("first-prestige → 1 reward"));
    }

    private static OperationPreview transition(int current, int target) {
        return preview("ALL", "2", "2", true, List.of("$5"), List.of("$1"), List.of(),
                current, target);
    }

    private static OperationPreview blockedCostPreview() {
        OperationPreview base = preview("ALL", "1", "2", false, List.of(), List.of("$1"), List.of(), 4, 5);
        base = withBalanceProjection(base, "12", "8");
        List<AuthorizationBlocker> blockers = List.of(
                AuthorizationBlocker.of(AuthorizationBlockerKind.REQUIREMENT_UNSATISFIED,
                        "Prestige requirements are not satisfied", "requirement", "prestige", "status", "PARTIAL"),
                AuthorizationBlocker.of(AuthorizationBlockerKind.COST_PREFLIGHT_BLOCKED,
                        "Cost preflight blocked: insufficient balance", "id", "vault-cost", "provider",
                        "vault_economy_cost", "amount", "5", "type", "vault_economy", "status", "BLOCKED",
                        "detail", "insufficient balance"));
        return new OperationPreview(base.kind(), base.playerId(), false, base.stateChange(), base.requirements(),
                base.costs(), base.rewards(), base.milestones(), base.consequences(),
                AuthorizationBlocker.diagnostics(blockers), base.configRevision(), base.providerGenerations(),
                base.externalUncertainty(), base.semanticDetails(), blockers);
    }

    private static OperationPreview preview(
            String mode,
            String progress,
            String threshold,
            boolean executable,
            List<String> costs,
            List<String> rewards,
            List<String> milestones) {
        return preview(mode, progress, threshold, executable, costs, rewards, milestones, 2, 3);
    }

    private static OperationPreview preview(
            String mode,
            String progress,
            String threshold,
            boolean executable,
            List<String> costs,
            List<String> rewards,
            List<String> milestones,
            int current,
            int target) {
        ExplanationNode money = leaf("balance", "12", "10", ExplanationStatus.SATISFIED);
        ExplanationNode mcmmo = leaf("total_level", "1", "1", ExplanationStatus.SATISFIED);
        ExplanationNode root = new ExplanationNode("requirement.group",
                executable ? ExplanationStatus.SATISFIED : ExplanationStatus.UNSATISFIED,
                executable ? "Ready" : "Not ready",
                Map.of("mode", mode, "progress", progress, "threshold", threshold),
                List.of(money, mcmmo));
        List<MessageReference> details = new java.util.ArrayList<>();
        details.add(MessageReference.of("command.preview.prestige_state_change",
                "current_prestige", current, "target_prestige", target,
                "current_lifetime", current, "target_lifetime", target));
        if (!costs.isEmpty()) {
            String amount = currencyAmount(costs.getFirst());
            details.add(MessageReference.of("command.preview.cost", "id", "vault-cost", "provider", "vault",
                    "type", "WITHDRAW", "amount", amount, "canonical", amount, "value", "Money"));
        }
        if (!rewards.isEmpty()) {
            String amount = currencyAmount(rewards.getFirst());
            details.add(MessageReference.of("command.preview.reward", "id", "vault-reward", "provider", "vault",
                    "type", "DEPOSIT", "amount", amount, "canonical", amount, "value", "Money"));
        }
        java.math.BigDecimal currentBalance = new java.math.BigDecimal("12");
        java.math.BigDecimal cost = costs.isEmpty() ? java.math.BigDecimal.ZERO
                : new java.math.BigDecimal(currencyAmount(costs.getFirst()));
        java.math.BigDecimal reward = rewards.isEmpty() ? java.math.BigDecimal.ZERO
                : new java.math.BigDecimal(currencyAmount(rewards.getFirst()));
        details.add(MessageReference.of("command.preview.balance_projection",
                "current", currentBalance.toPlainString(),
                "projected", currentBalance.subtract(cost).add(reward).toPlainString()));
        List<String> blockers = executable ? List.of() : List.of("Requirements are not met");
        return new OperationPreview(OperationKind.PRESTIGE, PLAYER, executable,
                "Prestige " + current + " → " + target, Optional.of(root), costs, rewards, milestones,
                List.of(), blockers, REVISION, Map.of(), List.of(), details,
                net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker.unknownAll(blockers));
    }

    private static String currencyAmount(String value) {
        return value.startsWith("$") ? value.substring(1) : value;
    }

    private static OperationPreview withBalanceProjection(
            OperationPreview preview,
            String current,
            String projected) {
        List<MessageReference> details = preview.semanticDetails().stream()
                .filter(reference -> !reference.key().equals("command.preview.balance_projection"))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        details.add(MessageReference.of("command.preview.balance_projection",
                "current", current, "projected", projected));
        return new OperationPreview(preview.kind(), preview.playerId(), preview.executable(), preview.stateChange(),
                preview.requirements(), preview.costs(), preview.rewards(), preview.milestones(),
                preview.consequences(), preview.blockers(), preview.configRevision(), preview.providerGenerations(),
                preview.externalUncertainty(), details, preview.authorizationBlockers());
    }

    private static ExplanationNode leaf(
            String metric,
            String current,
            String target,
            ExplanationStatus status) {
        return new ExplanationNode("requirement.leaf", status, status.name(),
                Map.of("metric", metric, "current", current, "target", target), List.of());
    }
}
