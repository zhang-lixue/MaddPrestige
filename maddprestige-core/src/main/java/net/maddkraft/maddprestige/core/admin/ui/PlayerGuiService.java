package net.maddkraft.maddprestige.core.admin.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.explanation.ExplanationNode;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.OperationConfirmationService;
import net.maddkraft.maddprestige.core.admin.OperationPreview;
import net.maddkraft.maddprestige.core.admin.OperationPreviewService;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.AdministrationPermissions;
import net.maddkraft.maddprestige.core.admin.player.PlayerProgressViewService;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker;

/**
 * Player inventory presentation over the canonical preview, progress, and confirmation services.
 * No value is calculated or persisted by this layer.
 */
public final class PlayerGuiService {
    private static final int SIZE = 27;
    private static final List<Integer> BORDER_SLOTS = List.of(
            0, 1, 2, 3, 5, 6, 7, 8,
            9, 17,
            18, 19, 20, 21, 23, 24, 25);
    private final GuiSessionService sessions;
    private final PlayerProgressViewService progress;
    private final OperationPreviewService previews;
    private final OperationConfirmationService confirmations;

    public PlayerGuiService(
            GuiSessionService sessions,
            PlayerProgressViewService progress,
            OperationPreviewService previews,
            OperationConfirmationService confirmations) {
        this.sessions = Objects.requireNonNull(sessions, "GUI sessions");
        this.progress = Objects.requireNonNull(progress, "player progress");
        this.previews = Objects.requireNonNull(previews, "operation previews");
        this.confirmations = Objects.requireNonNull(confirmations, "operation confirmations");
    }

    public CompletionStage<GuiSessionView> open(PermissionSubject subject, UUID playerId) {
        requireSelf(subject, playerId);
        subject.require(AdministrationPermissions.USE);
        return progress.view(subject, playerId).thenApply(view -> playerView(subject, playerId, view.prestige()));
    }

    public CompletionStage<PlayerGuiInteractionResult> click(
            PermissionSubject subject,
            UUID sessionId,
            UUID actionId) {
        GuiAction action;
        try {
            action = sessions.authorizePlayerClick(subject, sessionId, actionId);
        } catch (AdministrationException exception) {
            if (exception.code().equals("gui.action.stale") && subject.actor().uuid().isPresent()) {
                return refresh(subject, subject.actor().uuid().orElseThrow());
            }
            throw exception;
        }
        UUID playerId = target(action);
        return switch (action.kind()) {
            case SIMULATE_PRESTIGE -> previews.simulatePrestige(subject, playerId)
                    .thenApply(preview -> PlayerGuiInteractionResult.navigate(
                            previewView(subject, playerId, preview)));
            case PREPARE_PRESTIGE -> executePrestige(subject, playerId);
            case BACK_PLAYER -> open(subject, playerId).thenApply(PlayerGuiInteractionResult::navigate);
            case CLOSE_PLAYER -> CompletableFuture.completedFuture(PlayerGuiInteractionResult.closed());
            default -> throw new AdministrationException("gui.action.player_invalid",
                    "The selected action does not belong to the Player GUI.",
                    "Reopen the Player GUI to obtain current player-only controls.");
        };
    }

    public void closeView(UUID sessionId) {
        sessions.invalidate(sessionId);
    }

    public void invalidatePlayer(UUID playerId) {
        sessions.invalidatePlayer(playerId);
    }

    private CompletionStage<PlayerGuiInteractionResult> executePrestige(
            PermissionSubject subject,
            UUID playerId) {
        CompletionStage<PlayerGuiInteractionResult> execution;
        try {
            execution = confirmations.preparePrestige(subject, playerId)
                    .thenCompose(prepared -> confirmations.confirm(subject, prepared.confirmationId()))
                    .thenCompose(result -> open(subject, playerId)
                            .thenApply(PlayerGuiInteractionResult::navigate));
        } catch (AdministrationException exception) {
            return refreshAfterSafetyFailure(subject, playerId, exception);
        }
        return execution.exceptionallyCompose(failure ->
                refreshAfterSafetyFailure(subject, playerId, failure));
    }

    private CompletionStage<PlayerGuiInteractionResult> refresh(
            PermissionSubject subject,
            UUID playerId) {
        return open(subject, playerId).thenApply(PlayerGuiInteractionResult::navigate);
    }

    private CompletionStage<PlayerGuiInteractionResult> refreshAfterSafetyFailure(
            PermissionSubject subject,
            UUID playerId,
            Throwable failure) {
        Throwable cause = rootCause(failure);
        if (cause instanceof AdministrationException exception
                && refreshableSafetyFailure(exception.code())) {
            return refresh(subject, playerId);
        }
        return CompletableFuture.failedFuture(cause);
    }

    private static boolean refreshableSafetyFailure(String code) {
        return code.equals("operation.preview.blocked")
                || code.equals("confirmation.revalidation_failed")
                || code.equals("confirmation.config_stale")
                || code.equals("confirmation.expired")
                || code.equals("confirmation.session_ended");
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private GuiSessionView playerView(
            PermissionSubject subject,
            UUID playerId,
            OperationPreview preview) {
        ConfigRevisionId revision = preview.configRevision();
        PrestigeTransition transition = transition(preview);
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = commonItems(preview, false);
        if (subject.has(AdministrationPermissions.PRESTIGE)) {
            GuiAction prestige = action(GuiActionKind.SIMULATE_PRESTIGE, "gui.action.prestige",
                    AdministrationPermissions.PRESTIGE, false, revision, playerId, null);
            actions.add(prestige);
            items.add(GuiDisplayItem.action(22, GuiItemIcon.PRESTIGE, prestige.label(),
                    List.of(m("gui.item.prestige.open_preview", "current", transition.current(),
                            "target", transition.target())), prestige.actionId(), preview.executable()));
        }
        addClose(actions, items, revision, playerId);
        addBorder(items);
        return sessions.storePlayerScreen(subject, playerId,
                m("gui.title.player", "current", transition.current()), actions,
                GuiScreenKind.PLAYER, SIZE, items);
    }

    private GuiSessionView previewView(
            PermissionSubject subject,
            UUID playerId,
            OperationPreview preview) {
        ConfigRevisionId revision = preview.configRevision();
        PrestigeTransition transition = transition(preview);
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = commonItems(preview, true);
        if (preview.executable()) {
            GuiAction proceed = action(GuiActionKind.PREPARE_PRESTIGE, "gui.action.confirm",
                    AdministrationPermissions.PRESTIGE, true, revision, playerId, null);
            actions.add(proceed);
            items.add(GuiDisplayItem.action(22, GuiItemIcon.CONFIRM, proceed.label(),
                    List.of(), proceed.actionId(), true));
        } else {
            items.removeIf(item -> item.icon() == GuiItemIcon.BALANCE);
            addBlockedBalance(items, preview, 10);
        }
        addBack(actions, items, revision, playerId);
        addClose(actions, items, revision, playerId);
        addBorder(items);
        return sessions.storePlayerScreen(subject, playerId, m("gui.title.prestige_preview",
                "current", transition.current(), "target", transition.target()), actions,
                GuiScreenKind.PRESTIGE_PREVIEW, SIZE, items);
    }

    static ArrayList<GuiDisplayItem> commonItems(
            OperationPreview preview,
            boolean projectedBalance) {
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        items.add(GuiDisplayItem.display(4, GuiItemIcon.PROGRESS, m("gui.item.progress.title"),
                progress(preview)));
        items.add(GuiDisplayItem.display(13, GuiItemIcon.REQUIREMENTS,
                m("gui.item.requirements.title"), requirements(preview)));
        addBalance(items, preview, 10, projectedBalance);
        addRewards(items, preview, 16);
        if (!preview.milestones().isEmpty()) {
            items.add(GuiDisplayItem.display(20, GuiItemIcon.MILESTONE, m("gui.item.milestones.title"),
                    preview.milestones().stream().map(value -> m("gui.item.milestone", "value", value)).toList()));
        }
        return items;
    }

    static ArrayList<GuiDisplayItem> staffPreviewItems(OperationPreview preview) {
        ArrayList<GuiDisplayItem> items = commonItems(preview, true);
        if (preview.executable()) {
            return items;
        }
        items.removeIf(item -> item.icon() == GuiItemIcon.BALANCE);
        addBlockedBalance(items, preview, 10);
        return items;
    }

    private static void addBlockedBalance(
            List<GuiDisplayItem> items,
            OperationPreview preview,
            int slot) {
        Optional<MessageReference> snapshot = balanceSnapshot(preview);
        ArrayList<MessageReference> lore = new ArrayList<>();
        lore.add(m("gui.item.balance.current", "current", snapshot
                .flatMap(reference -> reference.argument("current"))
                .map(PlayerGuiService::money).orElse("Unavailable")));
        snapshot.flatMap(reference -> reference.argument("missing"))
                .map(PlayerGuiService::money)
                .map(missing -> m("gui.item.balance.missing", "missing", missing))
                .ifPresent(lore::add);
        items.add(GuiDisplayItem.display(slot, GuiItemIcon.BALANCE,
                m("gui.item.balance.title"), lore));
    }

    private static void addBalance(
            List<GuiDisplayItem> items,
            OperationPreview preview,
            int slot,
            boolean projected) {
        Optional<MessageReference> snapshot = balanceSnapshot(preview);
        MessageReference value = snapshot.map(reference -> projected
                ? m("gui.item.balance.projected",
                        "current", money(reference.argument("current").orElse("Unavailable")),
                        "projected", money(reference.argument("projected").orElse("Unavailable")))
                : m("gui.item.balance.current",
                        "current", money(reference.argument("current").orElse("Unavailable"))))
                .orElseGet(() -> m("gui.item.balance.current", "current", "Unavailable"));
        items.add(GuiDisplayItem.display(slot, GuiItemIcon.BALANCE,
                m("gui.item.balance.title"), List.of(value)));
    }

    private static Optional<MessageReference> balanceSnapshot(OperationPreview preview) {
        return preview.semanticDetails().stream()
                .filter(reference -> reference.key().equals("command.preview.balance_projection"))
                .findFirst();
    }

    private static void addRewards(
            List<GuiDisplayItem> items,
            OperationPreview preview,
            int rewardSlot) {
        List<MessageReference> concise = SemanticPresentation.preview("command.preview.summary", preview, false);
        List<MessageReference> rewards = changeValues(preview, concise, false);
        items.add(GuiDisplayItem.display(rewardSlot, GuiItemIcon.REWARD, m("gui.item.rewards.title"), rewards));
    }

    private static String money(String value) {
        return value.equals("Unavailable") || value.startsWith("$") ? value : "$" + value;
    }

    private static List<MessageReference> changeValues(
            OperationPreview preview,
            List<MessageReference> concise,
            boolean costValues) {
        ArrayList<MessageReference> values = concise.stream()
                .filter(costValues ? PlayerGuiService::cost : PlayerGuiService::reward)
                .map(PlayerGuiService::changeValue)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        preview.authorizationBlockers().stream()
                .filter(blocker -> actionBlocker(blocker, costValues))
                .map(PlayerGuiService::blockerGameplayValue)
                .distinct()
                .map(value -> m("gui.item.change.value", "value", value))
                .forEach(values::add);
        if (values.isEmpty()) {
            values.add(m("gui.item.change.value", "value", costValues ? "$0" : "None"));
        }
        return List.copyOf(values);
    }

    private static boolean actionBlocker(AuthorizationBlocker blocker, boolean costValues) {
        String prefix = costValues ? "COST_" : "REWARD_";
        return blocker.kind().name().startsWith(prefix);
    }

    private static String blockerGameplayValue(AuthorizationBlocker blocker) {
        String amount = blocker.facts().get("amount");
        if (amount == null) {
            return "Unavailable";
        }
        String provider = blocker.facts().getOrDefault("provider", "").toLowerCase(java.util.Locale.ROOT);
        String type = blocker.facts().getOrDefault("type", "").toLowerCase(java.util.Locale.ROOT);
        if (provider.contains("vault") || type.contains("money") || type.contains("withdraw")
                || type.contains("deposit")) {
            return "$" + amount;
        }
        return amount;
    }

    private static List<MessageReference> requirements(OperationPreview preview) {
        return preview.requirements().map(root -> requirements(preview, root))
                .orElseGet(() -> List.of(m("gui.item.change.value", "value", "None")));
    }

    private static List<MessageReference> requirements(OperationPreview preview, ExplanationNode root) {
        ArrayList<MessageReference> lore = new ArrayList<>();
        String progress = root.facts().getOrDefault("progress", root.status().name());
        int total = leafCount(root);
        String progressKey = progress.equals(Integer.toString(total))
                ? "gui.item.requirements.progress.complete"
                : "gui.item.requirements.progress.incomplete";
        lore.add(m(progressKey, "progress", progress, "total", total));
        List<MessageReference> entries = SemanticPresentation.preview("command.preview.summary", preview, false).stream()
                .filter(reference -> reference.key().equals("command.concise.requirement"))
                .map(PlayerGuiService::requirementLine)
                .toList();
        if (!entries.isEmpty()) {
            lore.add(m("gui.item.separator"));
            lore.addAll(entries);
        }
        return List.copyOf(lore);
    }

    private static List<MessageReference> progress(OperationPreview preview) {
        PrestigeTransition transition = transition(preview);
        return List.of(
                m("gui.item.progress.prestige", "current", transition.current()),
                m("gui.item.progress.next", "target", transition.target()),
                m(preview.executable() ? "gui.item.progress.ready" : "gui.item.progress.not_ready"));
    }

    static MessageReference requirementLine(MessageReference reference) {
        String canonicalLabel = reference.argument("label").orElse("Progress");
        String current = gameplayRequirementValue(canonicalLabel,
                reference.argument("current").orElse("Unavailable"));
        String target = gameplayRequirementValue(canonicalLabel,
                reference.argument("target").orElse("Unavailable"));
        boolean met = reference.argument("indicator").filter("✓"::equals).isPresent();
        String label = playerRequirementLabel(canonicalLabel);
        return m(requirementKey(label, met), "label", label,
                "current", current, "target", target);
    }

    private static String requirementKey(String label, boolean met) {
        if (label.equalsIgnoreCase("Money")) {
            return met ? "gui.item.requirement.money.met" : "gui.item.requirement.money.not_met";
        }
        if (label.equalsIgnoreCase("Total Skill Level")) {
            return met ? "gui.item.requirement.total_skill_level.met"
                    : "gui.item.requirement.total_skill_level.not_met";
        }
        return met ? "gui.item.requirement.met" : "gui.item.requirement.not_met";
    }

    private static String playerRequirementLabel(String label) {
        return label.equalsIgnoreCase("mcMMO") ? "Total Skill Level" : label;
    }

    private static String gameplayRequirementValue(String label, String value) {
        if (label.equalsIgnoreCase("Money") && !value.startsWith("$")
                && !value.equalsIgnoreCase("Unavailable")) {
            return "$" + value;
        }
        return value;
    }

    private static MessageReference changeValue(MessageReference reference) {
        return m("gui.item.change.value", "value",
                reference.argument("amount").or(() -> reference.argument("value")).orElse("Unavailable"));
    }

    private static PrestigeTransition transition(OperationPreview preview) {
        List<MessageReference> player = SemanticPresentation.player(preview);
        String current = player.stream()
                .filter(reference -> reference.key().equals("command.player.concise.prestige"))
                .findFirst().flatMap(reference -> reference.argument("current")).orElse("0");
        String target = player.stream()
                .filter(reference -> reference.key().equals("command.player.concise.next"))
                .findFirst().flatMap(reference -> reference.argument("target")).orElse(current);
        return new PrestigeTransition(current, target);
    }

    private static void addBorder(List<GuiDisplayItem> items) {
        java.util.Set<Integer> occupied = items.stream().map(GuiDisplayItem::slot)
                .collect(java.util.stream.Collectors.toSet());
        for (int index = 0; index < BORDER_SLOTS.size(); index++) {
            int slot = BORDER_SLOTS.get(index);
            if (!occupied.contains(slot)) {
                GuiItemIcon icon = index % 2 == 0 ? GuiItemIcon.BORDER_PURPLE : GuiItemIcon.BORDER_AQUA;
                items.add(GuiDisplayItem.display(slot, icon, m("gui.item.border"), List.of()));
            }
        }
    }

    private static int leafCount(ExplanationNode node) {
        return node.children().isEmpty() ? 1 : node.children().stream().mapToInt(PlayerGuiService::leafCount).sum();
    }

    private static boolean cost(MessageReference reference) {
        return reference.key().equals("command.concise.cost")
                || reference.key().equals("command.concise.cost_text");
    }

    private static boolean reward(MessageReference reference) {
        return reference.key().equals("command.concise.reward")
                || reference.key().equals("command.concise.reward_text");
    }

    private static void addBack(
            List<GuiAction> actions,
            List<GuiDisplayItem> items,
            ConfigRevisionId revision,
            UUID playerId) {
        GuiAction back = action(GuiActionKind.BACK_PLAYER, "gui.action.back", AdministrationPermissions.USE,
                false, revision, playerId, null);
        actions.add(back);
        items.add(GuiDisplayItem.action(18, GuiItemIcon.BACK, back.label(), List.of(), back.actionId(), false));
    }

    private static void addClose(
            List<GuiAction> actions,
            List<GuiDisplayItem> items,
            ConfigRevisionId revision,
            UUID playerId) {
        GuiAction close = action(GuiActionKind.CLOSE_PLAYER, "gui.action.close", AdministrationPermissions.USE,
                false, revision, playerId, null);
        actions.add(close);
        items.add(GuiDisplayItem.action(26, GuiItemIcon.CLOSE, close.label(), List.of(), close.actionId(), false));
    }

    private static GuiAction action(
            GuiActionKind kind,
            String label,
            String permission,
            boolean mutating,
            ConfigRevisionId revision,
            UUID playerId,
            UUID confirmationId) {
        return new GuiAction(UUID.randomUUID(), kind, m(label), permission, mutating, Optional.of(revision),
                Optional.of(playerId), Optional.ofNullable(confirmationId), Optional.empty(), Optional.empty(),
                Optional.empty());
    }

    private static UUID target(GuiAction action) {
        return action.targetPlayer().orElseThrow(() -> new AdministrationException(
                "gui.player.target_missing", "The server-owned GUI action has no player target.",
                "Reopen the Player GUI to obtain a complete action."));
    }

    private static void requireSelf(PermissionSubject subject, UUID playerId) {
        if (subject.actor().uuid().filter(playerId::equals).isEmpty()) {
            throw new AdministrationException("gui.player.self_required",
                    "The Player GUI can only be opened for the current player.",
                    "Use the explicit staff player commands for another player.");
        }
    }

    private static MessageReference m(String key, Object... arguments) {
        return MessageReference.of(key, arguments);
    }

    private record PrestigeTransition(String current, String target) {
    }
}
