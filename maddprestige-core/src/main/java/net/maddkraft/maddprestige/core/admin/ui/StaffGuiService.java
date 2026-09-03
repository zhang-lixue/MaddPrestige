package net.maddkraft.maddprestige.core.admin.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.OperationPreview;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.admin.player.PlayerProgressViewService;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind;
import net.maddkraft.maddprestige.core.admin.ui.StaffHistorySource.Entry;
import net.maddkraft.maddprestige.core.admin.ui.StaffSystemStatusSource.Component;
import net.maddkraft.maddprestige.core.admin.ui.StaffSystemStatusSource.ConfigurationSummary;
import net.maddkraft.maddprestige.core.admin.ui.StaffSystemStatusSource.Health;
import net.maddkraft.maddprestige.core.admin.ui.StaffSystemStatusSource.Summary;

/**
 * Read-only staff inventory projection over canonical player progress. This service owns no
 * Prestige, provider, configuration, or persistence mutation capability.
 */
public final class StaffGuiService {
    private static final int SIZE = 27;
    private static final int PAGE_SIZE = 7;
    private static final List<Integer> REQUIREMENT_CARD_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16);
    private static final List<Integer> BALANCED_TWO_REQUIREMENT_SLOTS = List.of(11, 15);
    private static final List<Integer> BORDER_SLOTS = List.of(
            0, 1, 2, 3, 5, 6, 7, 8,
            9, 17,
            19, 20, 21, 23, 24, 25);
    private final GuiSessionService sessions;
    private final PlayerProgressViewService progress;
    private final StaffPlayerDirectory players;
    private final Supplier<Optional<ConfigRevisionId>> activeRevision;
    private final StaffHistorySource history;
    private final StaffSystemStatusSource systemStatus;

    public StaffGuiService(
            GuiSessionService sessions,
            PlayerProgressViewService progress,
            StaffPlayerDirectory players,
            Supplier<Optional<ConfigRevisionId>> activeRevision,
            StaffHistorySource history,
            StaffSystemStatusSource systemStatus) {
        this.sessions = Objects.requireNonNull(sessions, "GUI sessions");
        this.progress = Objects.requireNonNull(progress, "player progress");
        this.players = Objects.requireNonNull(players, "player directory");
        this.activeRevision = Objects.requireNonNull(activeRevision, "active revision");
        this.history = Objects.requireNonNull(history, "history source");
        this.systemStatus = Objects.requireNonNull(systemStatus, "system status source");
    }

    public GuiSessionView open(PermissionSubject subject) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        requirePlayerActor(subject);
        Optional<ConfigRevisionId> revision = activeRevision.get();
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        Summary summary = safeSummary();
        items.add(GuiDisplayItem.display(4, GuiItemIcon.STAFF,
                m("gui.item.staff.overview.title"), List.of(
                        m(summary.configurationActive()
                                ? "gui.item.staff.overview.configuration.active"
                                : "gui.item.staff.overview.configuration.inactive"),
                        m("gui.item.staff.overview.providers", "current", summary.availableProviders(),
                                "total", summary.totalProviders()))));
        if (subject.has(PhaseSixPermissions.PLAYER_VIEW)) {
            GuiAction playersAction = action(GuiActionKind.STAFF_OPEN_PLAYERS,
                    "gui.action.staff.players", PhaseSixPermissions.PLAYER_VIEW, revision, null);
            actions.add(playersAction);
            items.add(GuiDisplayItem.action(10, GuiItemIcon.PLAYERS, playersAction.label(),
                    List.of(m("gui.item.staff.players.lore")), playersAction.actionId(), false));
        } else {
            items.add(GuiDisplayItem.display(10, GuiItemIcon.PLAYERS,
                    m("gui.action.staff.players"), List.of(m("gui.item.staff.players.unavailable"))));
        }
        GuiAction configurationAction = action(GuiActionKind.STAFF_VIEW_CONFIGURATION,
                "gui.action.staff.configuration", PhaseSixPermissions.ADMIN_GUI, revision, null);
        actions.add(configurationAction);
        items.add(GuiDisplayItem.action(12, GuiItemIcon.CONFIGURATION, configurationAction.label(),
                List.of(m("gui.item.staff.configuration.lore")), configurationAction.actionId(), false));
        GuiAction historyAction = action(GuiActionKind.STAFF_VIEW_HISTORY,
                "gui.action.staff.history", PhaseSixPermissions.ADMIN_GUI, revision, null);
        actions.add(historyAction);
        items.add(GuiDisplayItem.action(14, GuiItemIcon.HISTORY, historyAction.label(),
                List.of(m("gui.item.staff.history.lore")), historyAction.actionId(), false));
        GuiAction statusAction = action(GuiActionKind.STAFF_VIEW_SYSTEM_STATUS,
                statusActionKey(summary.health()), PhaseSixPermissions.ADMIN_GUI, revision, null);
        actions.add(statusAction);
        items.add(GuiDisplayItem.action(16, GuiItemIcon.SYSTEM_STATUS, statusAction.label(),
                List.of(m("gui.item.staff.system_status.lore", "status", statusText(summary.health()))),
                statusAction.actionId(), false));
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.dashboard"), actions,
                GuiScreenKind.STAFF_DASHBOARD, SIZE, items);
    }

    public CompletionStage<PlayerGuiInteractionResult> click(
            PermissionSubject subject,
            UUID sessionId,
            UUID actionId) {
        GuiAction action = sessions.authorizeStaffClick(subject, sessionId, actionId);
        return switch (action.kind()) {
            case STAFF_OPEN_PLAYERS -> completed(playerManagement(subject));
            case STAFF_OPEN_ONLINE_PLAYERS -> completed(onlinePlayerSelection(subject));
            case STAFF_FIND_PLAYER -> CompletableFuture.completedFuture(PlayerGuiInteractionResult.closed(
                    m("gui.staff.find.prompt")));
            case STAFF_VIEW_CONFIGURATION -> completed(configuration(subject));
            case STAFF_VIEW_HISTORY -> history(subject, 0);
            case STAFF_HISTORY_PREVIOUS, STAFF_HISTORY_NEXT -> history(subject, page(action));
            case STAFF_VIEW_SYSTEM_STATUS -> systemStatus(subject, 0);
            case STAFF_REFRESH_SYSTEM_STATUS -> systemStatus(subject, page(action));
            case STAFF_SYSTEM_STATUS_PREVIOUS, STAFF_SYSTEM_STATUS_NEXT ->
                    systemStatus(subject, page(action));
            case STAFF_SELECT_PLAYER -> overview(subject, target(action), false);
            case STAFF_SELECT_SEARCH_RESULT -> overview(subject, target(action), true);
            case STAFF_REFRESH_PLAYER_OVERVIEW -> overview(subject, target(action), false);
            case STAFF_REFRESH_SEARCH_PLAYER_OVERVIEW -> overview(subject, target(action), true);
            case STAFF_COPY_PLAYER_UUID -> copyPlayerUuid(subject, target(action), false);
            case STAFF_COPY_SEARCH_PLAYER_UUID -> copyPlayerUuid(subject, target(action), true);
            case STAFF_VIEW_PRESTIGE_PREVIEW, STAFF_REFRESH_PRESTIGE_PREVIEW ->
                    prestigePreview(subject, target(action));
            case STAFF_VIEW_PLAYER_HISTORY -> playerHistory(subject, target(action), 0);
            case STAFF_PLAYER_HISTORY_PREVIOUS, STAFF_PLAYER_HISTORY_NEXT ->
                    playerHistory(subject, target(action), page(action));
            case STAFF_VIEW_REQUIREMENTS -> requirements(subject, target(action), 0);
            case STAFF_REQUIREMENTS_PREVIOUS, STAFF_REQUIREMENTS_NEXT ->
                    requirements(subject, target(action), page(action));
            case STAFF_BACK_DASHBOARD -> completed(open(subject));
            case STAFF_BACK_PLAYER_MANAGEMENT -> completed(playerManagement(subject));
            case STAFF_BACK_PLAYER_LIST -> completed(onlinePlayerSelection(subject));
            case STAFF_BACK_PLAYER_SEARCH_RESULTS -> completed(searchResults(subject,
                    known(target(action)).name()));
            case STAFF_BACK_PLAYER_OVERVIEW -> overview(subject, target(action));
            case STAFF_CLOSE -> CompletableFuture.completedFuture(PlayerGuiInteractionResult.closed());
            default -> throw new AdministrationException("gui.action.staff_invalid",
                    "The selected control does not belong to the Staff GUI.",
                    "Reopen the Staff GUI to obtain current server-owned controls.");
        };
    }

    public void closeView(UUID sessionId) {
        sessions.invalidate(sessionId);
    }

    public void invalidateStaff(UUID actorId) {
        sessions.invalidateStaff(actorId);
    }

    /** Opens actor-bound, server-owned results for a bounded known-player selector. */
    public GuiSessionView findPlayers(PermissionSubject subject, String selector) {
        subject.require(PhaseSixPermissions.PLAYER_VIEW);
        requirePlayerActor(subject);
        return searchResults(subject, selector);
    }

    private GuiSessionView configuration(PermissionSubject subject) {
        Optional<ConfigRevisionId> revision = activeRevision.get();
        ConfigurationSummary summary = safeConfigurationSummary();
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        ArrayList<MessageReference> lore = new ArrayList<>();
        if (!summary.active()) {
            lore.add(m("gui.item.staff.configuration.inactive"));
        } else {
            lore.add(m(summary.usable()
                    ? "gui.item.staff.configuration.usable"
                    : "gui.item.staff.configuration.unavailable"));
            lore.add(m("gui.item.staff.configuration.range", "value", summary.prestigeRange()));
            lore.add(m("gui.item.staff.configuration.requirements", "count", summary.requirements()));
            lore.add(m("gui.item.staff.configuration.costs", "count", summary.costs()));
            lore.add(m("gui.item.staff.configuration.rewards", "count", summary.rewards()));
            lore.add(m("gui.item.staff.configuration.scaling", "count", summary.scalingProfiles()));
            lore.add(m("gui.item.staff.configuration.providers", "provider",
                    providerSummary(summary.providers())));
            revision.ifPresent(value -> lore.add(
                    m("gui.item.staff.configuration.revision", "revision", value.value())));
        }
        items.add(GuiDisplayItem.display(13, GuiItemIcon.CONFIGURATION,
                m("gui.item.staff.configuration.detail.title"), lore));
        addBack(actions, items, GuiActionKind.STAFF_BACK_DASHBOARD, revision, null);
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.configuration"), actions,
                GuiScreenKind.STAFF_CONFIGURATION, SIZE, items);
    }

    private CompletionStage<PlayerGuiInteractionResult> history(
            PermissionSubject subject,
            int pageIndex) {
        int offset = Math.multiplyExact(pageIndex, PAGE_SIZE);
        return history.recent(offset, PAGE_SIZE).thenApply(page -> {
            if (pageIndex > 0 && page.entries().isEmpty()) {
                throw new IllegalStateException("Server-owned History page is outside the current projection");
            }
            Optional<ConfigRevisionId> revision = activeRevision.get();
            ArrayList<GuiAction> actions = new ArrayList<>();
            ArrayList<GuiDisplayItem> items = new ArrayList<>();
            ArrayList<MessageReference> summary = new ArrayList<>();
            summary.add(m("gui.item.staff.history.summary", "count", page.entries().size()));
            long exceptions = page.entries().stream()
                    .filter(entry -> entry.outcome() != StaffHistorySource.Outcome.COMPLETED)
                    .count();
            summary.add(m("gui.item.staff.history.exceptions", "count", exceptions));
            if (page.hasPrevious() || page.hasNext()) {
                summary.add(m("gui.item.staff.history.page", "current", pageIndex + 1));
            }
            items.add(GuiDisplayItem.display(4, GuiItemIcon.HISTORY,
                    m("gui.item.staff.history.summary.title"), summary));
            if (page.entries().isEmpty()) {
                items.add(GuiDisplayItem.display(13, GuiItemIcon.HISTORY,
                        m("gui.item.staff.history.empty.title"),
                        List.of(m("gui.item.staff.history.empty.lore"))));
            } else {
                int firstSlot = 10 + Math.max(0, (PAGE_SIZE - page.entries().size()) / 2);
                for (int index = 0; index < page.entries().size(); index++) {
                    Entry entry = page.entries().get(index);
                    ArrayList<MessageReference> lore = new ArrayList<>();
                    lore.add(m("gui.item.staff.history.transition", "before", entry.before(),
                            "after", entry.after()));
                    lore.add(m(StaffHistoryPresentation.guiOutcomeKey(entry.outcome()),
                            "status", StaffHistoryPresentation.outcomeText(entry.outcome())));
                    lore.add(m("gui.item.staff.history.time", "value",
                            StaffHistoryPresentation.timestamp(entry.occurredAt())));
                    items.add(GuiDisplayItem.display(firstSlot + index, GuiItemIcon.HISTORY,
                            m("gui.item.staff.history.entry.title", "player", entry.player()), lore));
                }
            }
            if (page.hasPrevious()) {
                addPage(actions, items, GuiActionKind.STAFF_HISTORY_PREVIOUS,
                        "gui.action.staff.previous_page", 19, PhaseSixPermissions.ADMIN_GUI,
                        revision, null, pageIndex - 1);
            }
            if (page.hasNext()) {
                addPage(actions, items, GuiActionKind.STAFF_HISTORY_NEXT,
                        "gui.action.staff.next_page", 25, PhaseSixPermissions.ADMIN_GUI,
                        revision, null, pageIndex + 1);
            }
            addBack(actions, items, GuiActionKind.STAFF_BACK_DASHBOARD, revision, null);
            addClose(actions, items, revision);
            addBorder(items);
            return PlayerGuiInteractionResult.navigate(sessions.storeStaffScreen(subject,
                    m("gui.title.staff.history"), actions, GuiScreenKind.STAFF_HISTORY, SIZE, items));
        });
    }

    private CompletionStage<PlayerGuiInteractionResult> systemStatus(
            PermissionSubject subject,
            int pageIndex) {
        return systemStatus.inspect().thenApply(snapshot -> {
            int pageCount = Math.max(1,
                    (snapshot.components().size() + PAGE_SIZE - 1) / PAGE_SIZE);
            if (pageIndex < 0 || pageIndex >= pageCount) {
                throw new IllegalStateException("Server-owned System Status page is outside the current projection");
            }
            Optional<ConfigRevisionId> revision = activeRevision.get();
            ArrayList<GuiAction> actions = new ArrayList<>();
            ArrayList<GuiDisplayItem> items = new ArrayList<>();
            ArrayList<MessageReference> summary = new ArrayList<>();
            summary.add(m("gui.item.staff.system_status.summary", "status",
                    statusText(snapshot.summary().health())));
            summary.add(m("gui.item.staff.overview.providers",
                    "current", snapshot.summary().availableProviders(),
                    "total", snapshot.summary().totalProviders()));
            if (pageCount > 1) {
                summary.add(m("gui.item.staff.system_status.page", "current", pageIndex + 1,
                        "total", pageCount));
            }
            items.add(GuiDisplayItem.display(4, GuiItemIcon.SYSTEM_STATUS,
                    m(statusSummaryKey(snapshot.summary().health())), summary));
            if (snapshot.components().isEmpty()) {
                items.add(GuiDisplayItem.display(13, GuiItemIcon.SYSTEM_STATUS,
                        m("gui.item.staff.system_status.no_integrations.title"),
                        List.of(m("gui.item.staff.system_status.no_integrations.lore"))));
            } else {
                int first = pageIndex * PAGE_SIZE;
                int last = Math.min(first + PAGE_SIZE, snapshot.components().size());
                int firstSlot = 10 + (PAGE_SIZE - (last - first)) / 2;
                for (int index = first; index < last; index++) {
                    Component component = snapshot.components().get(index);
                    ArrayList<MessageReference> lore = new ArrayList<>();
                    lore.add(m(statusLineKey(component.health()), "status", component.status()));
                    if (!component.detail().isBlank()) {
                        lore.add(m("gui.item.staff.system_status.component.detail",
                                "detail", component.detail()));
                    }
                    items.add(GuiDisplayItem.display(firstSlot + index - first,
                            component.name().equalsIgnoreCase("Configuration")
                                    ? GuiItemIcon.CONFIGURATION : GuiItemIcon.SYSTEM_STATUS,
                            m(statusComponentKey(component.health()), "component", component.name()), lore));
                }
            }
            if (pageIndex > 0) {
                addPage(actions, items, GuiActionKind.STAFF_SYSTEM_STATUS_PREVIOUS,
                        "gui.action.staff.previous_page", 19, PhaseSixPermissions.ADMIN_GUI,
                        revision, null, pageIndex - 1);
            }
            if (pageIndex + 1 < pageCount) {
                addPage(actions, items, GuiActionKind.STAFF_SYSTEM_STATUS_NEXT,
                        "gui.action.staff.next_page", 25, PhaseSixPermissions.ADMIN_GUI,
                        revision, null, pageIndex + 1);
            }
            addRefresh(actions, items, GuiActionKind.STAFF_REFRESH_SYSTEM_STATUS,
                    revision, null, pageIndex);
            addBack(actions, items, GuiActionKind.STAFF_BACK_DASHBOARD, revision, null);
            addClose(actions, items, revision);
            addBorder(items);
            return PlayerGuiInteractionResult.navigate(sessions.storeStaffScreen(subject,
                    m("gui.title.staff.system_status"), actions,
                    GuiScreenKind.STAFF_SYSTEM_STATUS, SIZE, items));
        });
    }

    private GuiSessionView playerManagement(PermissionSubject subject) {
        subject.require(PhaseSixPermissions.PLAYER_VIEW);
        Optional<ConfigRevisionId> revision = activeRevision.get();
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        GuiAction online = action(GuiActionKind.STAFF_OPEN_ONLINE_PLAYERS,
                "gui.action.staff.online_players", PhaseSixPermissions.PLAYER_VIEW, revision, null);
        actions.add(online);
        items.add(GuiDisplayItem.action(11, GuiItemIcon.PLAYERS, online.label(),
                List.of(m("gui.item.staff.online_players.lore")), online.actionId(), false));
        GuiAction find = action(GuiActionKind.STAFF_FIND_PLAYER,
                "gui.action.staff.find_player", PhaseSixPermissions.PLAYER_VIEW, revision, null);
        actions.add(find);
        items.add(GuiDisplayItem.action(15, GuiItemIcon.PLAYER_INFORMATION, find.label(),
                List.of(m("gui.item.staff.find_player.lore")), find.actionId(), false));
        addBack(actions, items, GuiActionKind.STAFF_BACK_DASHBOARD, revision, null);
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.player_management"), actions,
                GuiScreenKind.STAFF_PLAYER_MANAGEMENT, SIZE, items);
    }

    private GuiSessionView onlinePlayerSelection(PermissionSubject subject) {
        subject.require(PhaseSixPermissions.PLAYER_VIEW);
        Optional<ConfigRevisionId> revision = activeRevision.get();
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        List<StaffPlayerIdentity> online = players.onlinePlayers().stream()
                .sorted(Comparator.comparing(StaffPlayerIdentity::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(StaffPlayerIdentity::playerId))
                .limit(7)
                .toList();
        if (online.isEmpty()) {
            items.add(GuiDisplayItem.display(13, GuiItemIcon.PLAYERS,
                    m("gui.item.staff.no_players.title"), List.of(m("gui.item.staff.no_players.lore"))));
        } else {
            int firstSlot = 10 + Math.max(0, (7 - online.size()) / 2);
            for (int index = 0; index < online.size(); index++) {
                StaffPlayerIdentity player = online.get(index);
                GuiAction select = action(GuiActionKind.STAFF_SELECT_PLAYER,
                        "gui.action.staff.select_player", PhaseSixPermissions.PLAYER_VIEW,
                        revision, player.playerId());
                actions.add(select);
                items.add(GuiDisplayItem.profiledAction(firstSlot + index, GuiItemIcon.PLAYERS,
                        m("gui.item.staff.player.title", "player", player.name()),
                        List.of(m("gui.item.staff.player.selection.online"),
                                m("gui.item.staff.player.selection.inspect")),
                        select.actionId(), false, player.playerId()));
            }
        }
        addBack(actions, items, GuiActionKind.STAFF_BACK_PLAYER_MANAGEMENT, revision, null);
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.online_players"), actions,
                GuiScreenKind.STAFF_PLAYER_SELECTION, SIZE, items);
    }

    private GuiSessionView searchResults(PermissionSubject subject, String selector) {
        subject.require(PhaseSixPermissions.PLAYER_VIEW);
        Optional<ConfigRevisionId> revision = activeRevision.get();
        List<StaffPlayerIdentity> matches = players.searchPlayers(selector).stream().limit(7).toList();
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        if (matches.isEmpty()) {
            items.add(GuiDisplayItem.display(13, GuiItemIcon.PLAYER_INFORMATION,
                    m("gui.item.staff.find_player.empty.title"),
                    List.of(m("gui.item.staff.find_player.empty.lore", "player", selector))));
        } else {
            int firstSlot = 10 + Math.max(0, (7 - matches.size()) / 2);
            for (int index = 0; index < matches.size(); index++) {
                StaffPlayerIdentity player = matches.get(index);
                GuiAction select = action(GuiActionKind.STAFF_SELECT_SEARCH_RESULT,
                        "gui.action.staff.select_player", PhaseSixPermissions.PLAYER_VIEW,
                        revision, player.playerId());
                actions.add(select);
                ArrayList<MessageReference> lore = new ArrayList<>();
                lore.add(m(player.online()
                        ? "gui.item.staff.player.selection.online"
                        : "gui.item.staff.player.selection.offline"));
                lore.add(m("gui.item.staff.player.selection.inspect"));
                long sameName = matches.stream().filter(candidate ->
                        candidate.name().equalsIgnoreCase(player.name())).count();
                if (sameName > 1) {
                    lore.add(m("gui.item.staff.player.uuid", "uuid", player.playerId()));
                }
                items.add(GuiDisplayItem.profiledAction(firstSlot + index, GuiItemIcon.PLAYERS,
                        m("gui.item.staff.player.title", "player", player.name()), lore,
                        select.actionId(), false, player.playerId()));
            }
        }
        addBack(actions, items, GuiActionKind.STAFF_BACK_PLAYER_MANAGEMENT, revision, null);
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.find_player"), actions,
                GuiScreenKind.STAFF_PLAYER_SEARCH_RESULTS, SIZE, items);
    }

    private CompletionStage<PlayerGuiInteractionResult> overview(
            PermissionSubject subject,
            UUID playerId) {
        StaffPlayerIdentity identity = known(playerId);
        return overview(subject, identity, !identity.online());
    }

    private CompletionStage<PlayerGuiInteractionResult> overview(
            PermissionSubject subject,
            UUID playerId,
            boolean fromSearch) {
        return overview(subject, known(playerId), fromSearch);
    }

    private CompletionStage<PlayerGuiInteractionResult> overview(
            PermissionSubject subject,
            StaffPlayerIdentity identity,
            boolean fromSearch) {
        UUID playerId = identity.playerId();
        return progress.inspect(subject, playerId).handle((view, failure) -> {
            if (failure == null) {
                return PlayerGuiInteractionResult.navigate(
                        overviewView(subject, identity, view.prestige(), fromSearch));
            }
            if (!identity.online() && playerPrestigeStateUnavailable(failure)) {
                return PlayerGuiInteractionResult.navigate(
                        unavailableOverviewView(subject, identity, fromSearch));
            }
            throw propagate(failure);
        });
    }

    private GuiSessionView unavailableOverviewView(
            PermissionSubject subject,
            StaffPlayerIdentity identity,
            boolean fromSearch) {
        Optional<ConfigRevisionId> revision = activeRevision.get();
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        items.add(GuiDisplayItem.display(4, GuiItemIcon.PROGRESS,
                m("gui.item.progress.title"), List.of(m("gui.item.staff.player.state_unavailable"))));
        items.add(GuiDisplayItem.display(10, GuiItemIcon.BALANCE,
                m("gui.item.balance.title"), List.of(
                        m("gui.item.balance.current", "current", "Unavailable"))));
        items.add(GuiDisplayItem.display(13, GuiItemIcon.REQUIREMENTS,
                m("gui.item.requirements.title"), List.of(
                        m("gui.item.change.value", "value", "Unavailable"))));
        items.add(GuiDisplayItem.display(16, GuiItemIcon.REWARD,
                m("gui.item.rewards.title"), List.of(
                        m("gui.item.change.value", "value", "Unavailable"))));
        items.add(GuiDisplayItem.display(20, GuiItemIcon.PRESTIGE,
                m("gui.action.staff.prestige_preview"), List.of(
                        m("gui.item.staff.prestige_preview.unavailable"))));
        items.add(GuiDisplayItem.profiledDisplay(22, GuiItemIcon.PLAYERS,
                m("gui.item.staff.player.info.title"),
                List.of(m("gui.item.staff.player.offline"),
                        m("gui.item.staff.player.uuid", "uuid", identity.playerId())),
                identity.playerId()));
        addOverviewUtilities(actions, items, revision, identity, fromSearch);
        GuiAction playerHistory = action(GuiActionKind.STAFF_VIEW_PLAYER_HISTORY,
                "gui.action.staff.player_history", PhaseSixPermissions.PLAYER_VIEW,
                revision, identity.playerId());
        actions.add(playerHistory);
        items.add(GuiDisplayItem.action(24, GuiItemIcon.HISTORY, playerHistory.label(),
                List.of(m("gui.item.staff.player_history.lore")), playerHistory.actionId(), false));
        addBack(actions, items, fromSearch
                        ? GuiActionKind.STAFF_BACK_PLAYER_SEARCH_RESULTS
                        : GuiActionKind.STAFF_BACK_PLAYER_LIST,
                revision, identity.playerId());
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.player", "player", identity.name()),
                actions, GuiScreenKind.STAFF_PLAYER_OVERVIEW, SIZE, items);
    }

    private GuiSessionView overviewView(
            PermissionSubject subject,
            StaffPlayerIdentity identity,
            OperationPreview preview,
            boolean fromSearch) {
        ConfigRevisionId revision = preview.configRevision();
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = PlayerGuiService.commonItems(preview, false);
        items.stream().filter(item -> item.slot() == 20 && item.icon() == GuiItemIcon.MILESTONE)
                .findFirst().ifPresent(milestone -> {
                    items.remove(milestone);
                    items.add(GuiDisplayItem.display(21, milestone.icon(), milestone.title(), milestone.lore()));
                });
        GuiDisplayItem requirements = items.stream()
                .filter(item -> item.icon() == GuiItemIcon.REQUIREMENTS)
                .findFirst().orElseThrow();
        items.remove(requirements);
        GuiAction inspect = action(GuiActionKind.STAFF_VIEW_REQUIREMENTS,
                "gui.action.staff.requirements", PhaseSixPermissions.PLAYER_VIEW,
                Optional.of(revision), identity.playerId());
        actions.add(inspect);
        items.add(GuiDisplayItem.action(requirements.slot(), requirements.icon(), requirements.title(),
                requirements.lore(), inspect.actionId(), requirements.highlighted()));
        items.add(GuiDisplayItem.profiledDisplay(22, GuiItemIcon.PLAYERS,
                m("gui.item.staff.player.info.title"),
                List.of(m(identity.online()
                                ? "gui.item.staff.player.online"
                                : "gui.item.staff.player.offline"),
                        m("gui.item.staff.player.uuid", "uuid", identity.playerId())),
                identity.playerId()));
        addOverviewUtilities(actions, items, Optional.of(revision), identity, fromSearch);
        GuiAction playerPreview = action(GuiActionKind.STAFF_VIEW_PRESTIGE_PREVIEW,
                "gui.action.staff.prestige_preview", PhaseSixPermissions.PLAYER_VIEW,
                Optional.of(revision), identity.playerId());
        actions.add(playerPreview);
        items.add(GuiDisplayItem.action(20, GuiItemIcon.PRESTIGE, playerPreview.label(),
                List.of(m("gui.item.staff.prestige_preview.lore")), playerPreview.actionId(), false));
        GuiAction playerHistory = action(GuiActionKind.STAFF_VIEW_PLAYER_HISTORY,
                "gui.action.staff.player_history", PhaseSixPermissions.PLAYER_VIEW,
                Optional.of(revision), identity.playerId());
        actions.add(playerHistory);
        items.add(GuiDisplayItem.action(24, GuiItemIcon.HISTORY, playerHistory.label(),
                List.of(m("gui.item.staff.player_history.lore")), playerHistory.actionId(), false));
        addBack(actions, items, fromSearch
                        ? GuiActionKind.STAFF_BACK_PLAYER_SEARCH_RESULTS
                        : GuiActionKind.STAFF_BACK_PLAYER_LIST,
                Optional.of(revision), identity.playerId());
        addClose(actions, items, Optional.of(revision));
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.player", "player", identity.name()),
                actions, GuiScreenKind.STAFF_PLAYER_OVERVIEW, SIZE, items);
    }

    private CompletionStage<PlayerGuiInteractionResult> copyPlayerUuid(
            PermissionSubject subject,
            UUID playerId,
            boolean fromSearch) {
        return overview(subject, playerId, fromSearch).thenApply(result ->
                PlayerGuiInteractionResult.navigate(result.nextView().orElseThrow(),
                        m("gui.staff.player.uuid.copy", "uuid", playerId)));
    }

    private static void addOverviewUtilities(
            List<GuiAction> actions,
            List<GuiDisplayItem> items,
            Optional<ConfigRevisionId> revision,
            StaffPlayerIdentity identity,
            boolean fromSearch) {
        GuiAction refresh = action(fromSearch
                        ? GuiActionKind.STAFF_REFRESH_SEARCH_PLAYER_OVERVIEW
                        : GuiActionKind.STAFF_REFRESH_PLAYER_OVERVIEW,
                "gui.action.staff.refresh", PhaseSixPermissions.PLAYER_VIEW,
                revision, identity.playerId());
        actions.add(refresh);
        items.add(GuiDisplayItem.action(0, GuiItemIcon.REFRESH, refresh.label(),
                List.of(m("gui.item.staff.refresh.lore")), refresh.actionId(), false));
        GuiAction copy = action(fromSearch
                        ? GuiActionKind.STAFF_COPY_SEARCH_PLAYER_UUID
                        : GuiActionKind.STAFF_COPY_PLAYER_UUID,
                "gui.action.staff.copy_uuid", PhaseSixPermissions.PLAYER_VIEW,
                revision, identity.playerId());
        actions.add(copy);
        items.add(GuiDisplayItem.action(8, GuiItemIcon.PLAYER_INFORMATION, copy.label(),
                List.of(m("gui.item.staff.copy_uuid.lore")), copy.actionId(), false));
    }

    private CompletionStage<PlayerGuiInteractionResult> prestigePreview(
            PermissionSubject subject,
            UUID playerId) {
        StaffPlayerIdentity identity = known(playerId);
        return progress.inspect(subject, playerId).thenApply(view -> PlayerGuiInteractionResult.navigate(
                prestigePreviewView(subject, identity, view.prestige())));
    }

    private GuiSessionView prestigePreviewView(
            PermissionSubject subject,
            StaffPlayerIdentity identity,
            OperationPreview preview) {
        Optional<ConfigRevisionId> revision = Optional.of(preview.configRevision());
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = PlayerGuiService.staffPreviewItems(preview);
        items.add(GuiDisplayItem.display(22,
                preview.executable() ? GuiItemIcon.READY : GuiItemIcon.BLOCKED,
                m(preview.executable()
                        ? "gui.item.staff.prestige_preview.ready"
                        : "gui.item.staff.prestige_preview.not_ready"), List.of()));
        GuiAction refresh = action(GuiActionKind.STAFF_REFRESH_PRESTIGE_PREVIEW,
                "gui.action.staff.refresh", PhaseSixPermissions.PLAYER_VIEW,
                revision, identity.playerId());
        actions.add(refresh);
        items.add(GuiDisplayItem.action(20, GuiItemIcon.PROGRESS, refresh.label(),
                List.of(m("gui.item.staff.refresh.lore")), refresh.actionId(), false));
        items.add(GuiDisplayItem.profiledDisplay(21, GuiItemIcon.PLAYERS,
                m("gui.item.staff.player.info.title"),
                List.of(m(identity.online()
                                ? "gui.item.staff.player.online"
                                : "gui.item.staff.player.offline"),
                        m("gui.item.staff.player.uuid", "uuid", identity.playerId())),
                identity.playerId()));
        GuiAction playerHistory = action(GuiActionKind.STAFF_VIEW_PLAYER_HISTORY,
                "gui.action.staff.player_history", PhaseSixPermissions.PLAYER_VIEW,
                revision, identity.playerId());
        actions.add(playerHistory);
        items.add(GuiDisplayItem.action(24, GuiItemIcon.HISTORY, playerHistory.label(),
                List.of(m("gui.item.staff.player_history.lore")), playerHistory.actionId(), false));
        addBack(actions, items, GuiActionKind.STAFF_BACK_PLAYER_OVERVIEW,
                revision, identity.playerId());
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject,
                m("gui.title.staff.prestige_preview", "player", identity.name()), actions,
                GuiScreenKind.STAFF_PRESTIGE_PREVIEW, SIZE, items);
    }

    private CompletionStage<PlayerGuiInteractionResult> playerHistory(
            PermissionSubject subject,
            UUID playerId,
            int pageIndex) {
        StaffPlayerIdentity identity = known(playerId);
        int offset = Math.multiplyExact(pageIndex, PAGE_SIZE);
        return history.forPlayer(playerId, offset, PAGE_SIZE).thenApply(page -> {
            if (pageIndex > 0 && page.entries().isEmpty()) {
                throw new IllegalStateException("Server-owned Player History page is outside the current projection");
            }
            Optional<ConfigRevisionId> revision = activeRevision.get();
            ArrayList<GuiAction> actions = new ArrayList<>();
            ArrayList<GuiDisplayItem> items = new ArrayList<>();
            ArrayList<MessageReference> summary = new ArrayList<>();
            summary.add(m("gui.item.staff.player_history.summary", "count", page.entries().size()));
            if (page.hasPrevious() || page.hasNext()) {
                summary.add(m("gui.item.staff.player_history.page", "current", pageIndex + 1));
            }
            items.add(GuiDisplayItem.display(4, GuiItemIcon.HISTORY,
                    m("gui.item.staff.player_history.summary.title"), summary));
            if (page.entries().isEmpty()) {
                items.add(GuiDisplayItem.display(13, GuiItemIcon.HISTORY,
                        m("gui.item.staff.player_history.empty.title"),
                        List.of(m("gui.item.staff.player_history.empty.lore"))));
            } else {
                int firstSlot = 10 + Math.max(0, (PAGE_SIZE - page.entries().size()) / 2);
                for (int index = 0; index < page.entries().size(); index++) {
                    Entry entry = page.entries().get(index);
                    ArrayList<MessageReference> lore = new ArrayList<>();
                    lore.add(m(StaffHistoryPresentation.guiOutcomeKey(entry.outcome()),
                            "status", StaffHistoryPresentation.outcomeText(entry.outcome())));
                    StaffHistoryPresentation.FinancialOutcome financial =
                            StaffHistoryPresentation.financialOutcome(entry);
                    financial.balance().ifPresent(balance -> lore.add(m("gui.item.staff.history.balance",
                            "before", balance.before(), "after", balance.after())));
                    financial.fallbackCost().ifPresent(value ->
                            lore.add(m("gui.item.staff.history.cost", "value", value)));
                    financial.reward().ifPresent(value ->
                            lore.add(m("gui.item.staff.history.reward", "value", value)));
                    if (financial.balance().isEmpty() && financial.fallbackCost().isEmpty()
                            && financial.reward().isEmpty()) {
                        lore.add(m(StaffHistoryPresentation.transactionKey(entry)));
                    }
                    lore.add(m("gui.item.staff.history.time", "value",
                            StaffHistoryPresentation.timestamp(entry.occurredAt())));
                    items.add(GuiDisplayItem.display(firstSlot + index, GuiItemIcon.HISTORY,
                            m("gui.item.staff.player_history.entry.title", "before", entry.before(),
                                    "after", entry.after()), lore));
                }
            }
            if (page.hasPrevious()) {
                addPage(actions, items, GuiActionKind.STAFF_PLAYER_HISTORY_PREVIOUS,
                        "gui.action.staff.previous_page", 19, PhaseSixPermissions.PLAYER_VIEW,
                        revision, identity.playerId(), pageIndex - 1);
            }
            if (page.hasNext()) {
                addPage(actions, items, GuiActionKind.STAFF_PLAYER_HISTORY_NEXT,
                        "gui.action.staff.next_page", 25, PhaseSixPermissions.PLAYER_VIEW,
                        revision, identity.playerId(), pageIndex + 1);
            }
            addBack(actions, items, GuiActionKind.STAFF_BACK_PLAYER_OVERVIEW,
                    revision, identity.playerId());
            addClose(actions, items, revision);
            addBorder(items);
            return PlayerGuiInteractionResult.navigate(sessions.storeStaffScreen(subject,
                    m("gui.title.staff.player_history", "player", identity.name()), actions,
                    GuiScreenKind.STAFF_PLAYER_HISTORY, SIZE, items));
        });
    }

    private CompletionStage<PlayerGuiInteractionResult> requirements(
            PermissionSubject subject,
            UUID playerId,
            int pageIndex) {
        StaffPlayerIdentity identity = known(playerId);
        return progress.inspect(subject, playerId).thenApply(view -> {
            OperationPreview preview = view.prestige();
            List<MessageReference> entries = preview.requirements()
                    .map(SemanticPresentation::requirementLeaves)
                    .orElseGet(List::of).stream()
                    .map(PlayerGuiService::requirementLine)
                    .toList();
            int pageCount = Math.max(1,
                    (entries.size() + REQUIREMENT_CARD_SLOTS.size() - 1) / REQUIREMENT_CARD_SLOTS.size());
            if (pageIndex < 0 || pageIndex >= pageCount) {
                throw new IllegalStateException("Server-owned Requirements page is outside the current projection");
            }
            ArrayList<GuiAction> actions = new ArrayList<>();
            ArrayList<GuiDisplayItem> items = new ArrayList<>();
            Optional<ConfigRevisionId> revision = Optional.of(preview.configRevision());
            long satisfied = entries.stream().filter(StaffGuiService::requirementMet).count();
            String summaryKey = satisfied == entries.size()
                    ? "gui.item.requirements.progress.complete"
                    : "gui.item.requirements.progress.incomplete";
            ArrayList<MessageReference> summary = new ArrayList<>();
            summary.add(m(summaryKey, "progress", satisfied, "total", entries.size()));
            if (pageCount > 1) {
                summary.add(m("gui.item.staff.requirements.page", "current", pageIndex + 1,
                        "total", pageCount));
            }
            items.add(GuiDisplayItem.display(4, GuiItemIcon.REQUIREMENTS,
                    m("gui.item.staff.requirements.summary.title"), summary));
            int first = pageIndex * REQUIREMENT_CARD_SLOTS.size();
            int last = Math.min(first + REQUIREMENT_CARD_SLOTS.size(), entries.size());
            List<Integer> cardSlots = entries.size() == 2
                    ? BALANCED_TWO_REQUIREMENT_SLOTS
                    : REQUIREMENT_CARD_SLOTS;
            int slotOffset = (cardSlots.size() - (last - first)) / 2;
            for (int index = first; index < last; index++) {
                MessageReference entry = entries.get(index);
                String label = entry.argument("label").orElse("Requirement");
                items.add(GuiDisplayItem.display(cardSlots.get(slotOffset + index - first),
                        requirementIcon(label), requirementTitle(label), List.of(
                                m("gui.item.staff.requirement.value",
                                        "current", entry.argument("current").orElse("Unavailable"),
                                        "target", entry.argument("target").orElse("Unavailable")),
                                m(requirementMet(entry)
                                        ? "gui.item.staff.requirement.met"
                                        : "gui.item.staff.requirement.not_met"))));
            }
            if (pageIndex > 0) {
                addPage(actions, items, GuiActionKind.STAFF_REQUIREMENTS_PREVIOUS,
                        "gui.action.staff.previous_page", 19, PhaseSixPermissions.PLAYER_VIEW,
                        revision, identity.playerId(), pageIndex - 1);
            }
            if (pageIndex + 1 < pageCount) {
                addPage(actions, items, GuiActionKind.STAFF_REQUIREMENTS_NEXT,
                        "gui.action.staff.next_page", 25, PhaseSixPermissions.PLAYER_VIEW,
                        revision, identity.playerId(), pageIndex + 1);
            }
            addBack(actions, items, GuiActionKind.STAFF_BACK_PLAYER_OVERVIEW,
                    revision, identity.playerId());
            addClose(actions, items, revision);
            addBorder(items);
            return PlayerGuiInteractionResult.navigate(sessions.storeStaffScreen(subject,
                    m("gui.title.staff.requirements", "player", identity.name()), actions,
                    GuiScreenKind.STAFF_REQUIREMENTS, SIZE, items));
        });
    }

    private static GuiItemIcon requirementIcon(String label) {
        if (label.equalsIgnoreCase("Money")) {
            return GuiItemIcon.BALANCE;
        }
        if (label.equalsIgnoreCase("Total Skill Level")) {
            return GuiItemIcon.PROGRESS;
        }
        return GuiItemIcon.REQUIREMENTS;
    }

    private static MessageReference requirementTitle(String label) {
        if (label.equalsIgnoreCase("Money")) {
            return m("gui.item.staff.requirement.money.title");
        }
        if (label.equalsIgnoreCase("Total Skill Level")) {
            return m("gui.item.staff.requirement.total_skill_level.title");
        }
        return m("gui.item.staff.requirement.title", "label", label);
    }

    private static boolean requirementMet(MessageReference entry) {
        return entry.key().endsWith(".met");
    }

    private StaffPlayerIdentity known(UUID playerId) {
        return players.player(playerId).orElseThrow(() -> new AdministrationException(
                "gui.staff.player_unknown", "The selected player is no longer known to this server.",
                "Return to Player Management and select a known player."));
    }

    private static boolean playerPrestigeStateUnavailable(Throwable failure) {
        Throwable cause = unwrap(failure);
        return cause instanceof AdministrationException administration
                && administration.authorizationBlockers().stream().anyMatch(blocker ->
                        blocker.kind() == AuthorizationBlockerKind.PLAYER_PRESTIGE_STATE_UNAVAILABLE);
    }

    private static RuntimeException propagate(Throwable failure) {
        Throwable cause = unwrap(failure);
        return cause instanceof RuntimeException runtime
                ? runtime : new CompletionException(cause);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static CompletionStage<PlayerGuiInteractionResult> completed(GuiSessionView view) {
        return CompletableFuture.completedFuture(PlayerGuiInteractionResult.navigate(view));
    }

    private static UUID target(GuiAction action) {
        return action.targetPlayer().orElseThrow(() -> new AdministrationException(
                "gui.staff.target_missing", "The selected Staff GUI action has no player target.",
                "Return to player selection and choose a player again."));
    }

    private static int page(GuiAction action) {
        return action.page().orElseThrow(() ->
                new IllegalStateException("Server-owned paging action has no page target"));
    }

    private static void addPage(
            List<GuiAction> actions,
            List<GuiDisplayItem> items,
            GuiActionKind kind,
            String label,
            int slot,
            String permission,
            Optional<ConfigRevisionId> revision,
            UUID playerId,
            int pageIndex) {
        GuiAction action = new GuiAction(UUID.randomUUID(), kind, m(label),
                permission, false, revision, Optional.ofNullable(playerId),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(pageIndex));
        actions.add(action);
        items.add(GuiDisplayItem.action(slot, GuiItemIcon.BACK, action.label(), List.of(),
                action.actionId(), false));
    }

    private static void addRefresh(
            List<GuiAction> actions,
            List<GuiDisplayItem> items,
            GuiActionKind kind,
            Optional<ConfigRevisionId> revision,
            UUID playerId,
            int pageIndex) {
        GuiAction refresh = new GuiAction(UUID.randomUUID(), kind, m("gui.action.staff.refresh"),
                PhaseSixPermissions.ADMIN_GUI, false, revision, Optional.ofNullable(playerId),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(pageIndex));
        actions.add(refresh);
        items.add(GuiDisplayItem.action(23, GuiItemIcon.PROGRESS, refresh.label(), List.of(),
                refresh.actionId(), false));
    }

    private static void addBack(
            List<GuiAction> actions,
            List<GuiDisplayItem> items,
            GuiActionKind kind,
            Optional<ConfigRevisionId> revision,
            UUID playerId) {
        GuiAction back = action(kind, "gui.action.back", PhaseSixPermissions.ADMIN_GUI, revision, playerId);
        actions.add(back);
        items.add(GuiDisplayItem.action(18, GuiItemIcon.BACK, back.label(), List.of(), back.actionId(), false));
    }

    private static void addClose(
            List<GuiAction> actions,
            List<GuiDisplayItem> items,
            Optional<ConfigRevisionId> revision) {
        GuiAction close = action(GuiActionKind.STAFF_CLOSE, "gui.action.close",
                PhaseSixPermissions.ADMIN_GUI, revision, null);
        actions.add(close);
        items.add(GuiDisplayItem.action(26, GuiItemIcon.CLOSE, close.label(), List.of(), close.actionId(), false));
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

    private static GuiAction action(
            GuiActionKind kind,
            String label,
            String permission,
            Optional<ConfigRevisionId> revision,
            UUID playerId) {
        return new GuiAction(UUID.randomUUID(), kind, m(label), permission, false, revision,
                Optional.ofNullable(playerId), Optional.empty(), Optional.empty());
    }

    private static void requirePlayerActor(PermissionSubject subject) {
        if (subject.actor().uuid().isEmpty()) {
            throw new AdministrationException("gui.staff.player_required",
                    "The Staff GUI requires an in-game player actor.",
                    "Run /maddprestige admin from an authorized player account.");
        }
    }

    private static MessageReference m(String key, Object... arguments) {
        return MessageReference.of(key, arguments);
    }

    private Summary safeSummary() {
        try {
            return systemStatus.summary();
        } catch (RuntimeException | LinkageError failure) {
            return new Summary(Health.BLOCKED, false, 0, 0);
        }
    }

    private ConfigurationSummary safeConfigurationSummary() {
        try {
            return systemStatus.configuration();
        } catch (RuntimeException | LinkageError failure) {
            return ConfigurationSummary.inactive();
        }
    }

    private static String providerSummary(List<String> providers) {
        if (providers.isEmpty()) {
            return "None configured";
        }
        int visible = Math.min(5, providers.size());
        String joined = providers.subList(0, visible).stream()
                .map(StaffGuiService::boundedProviderLabel)
                .collect(java.util.stream.Collectors.joining(", "));
        return visible == providers.size() ? joined : joined + " +" + (providers.size() - visible) + " more";
    }

    private static String boundedProviderLabel(String provider) {
        String value = Objects.requireNonNull(provider, "provider label").strip();
        if (value.isEmpty()) {
            return "Unnamed provider";
        }
        return value.length() <= 48 ? value : value.substring(0, 47) + "…";
    }

    private static String statusActionKey(Health health) {
        return "gui.action.staff.system_status." + health.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String statusSummaryKey(Health health) {
        return "gui.item.staff.system_status.summary.title."
                + health.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String statusComponentKey(Health health) {
        return "gui.item.staff.system_status.component.title."
                + health.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String statusLineKey(Health health) {
        return "gui.item.staff.system_status.component.status."
                + health.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String statusText(Health health) {
        return switch (health) {
            case HEALTHY -> "Available";
            case WARNING -> "Attention";
            case BLOCKED -> "Unavailable";
        };
    }

}
