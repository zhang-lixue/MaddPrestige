package net.maddkraft.maddprestige.core.admin.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.core.admin.ui.StaffHistoryPresentation;
import net.maddkraft.maddprestige.core.admin.ui.StaffHistorySource;
import net.maddkraft.maddprestige.core.admin.ui.StaffPlayerDirectory;
import net.maddkraft.maddprestige.core.admin.ui.StaffPlayerIdentity;

/** Read-only command projection over the same canonical player history source used by the Staff GUI. */
public final class StaffHistoryCommandService {
    static final int PAGE_SIZE = 5;
    private final StaffHistorySource history;
    private final StaffPlayerDirectory players;
    private final Map<UUID, List<UUID>> displayedEntries = new ConcurrentHashMap<>();

    public StaffHistoryCommandService(StaffHistorySource history, StaffPlayerDirectory players) {
        this.history = Objects.requireNonNull(history, "history");
        this.players = Objects.requireNonNull(players, "players");
    }

    public CompletionStage<CommandResponse> execute(PermissionSubject subject, List<String> arguments) {
        Objects.requireNonNull(subject, "subject").require(PhaseSixPermissions.PLAYER_VIEW);
        List<String> tokens = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        if (tokens.size() >= 2 && tokens.get(1).equalsIgnoreCase("details")) {
            return detail(tokens);
        }
        return summary(tokens);
    }

    public List<String> playerSuggestions() {
        return players.knownPlayers().stream()
                .map(StaffPlayerIdentity::name)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public List<String> entrySuggestions(String playerSelector) {
        return players.findPlayer(playerSelector)
                .map(player -> displayedEntries.getOrDefault(player.playerId(), List.of()).stream()
                        .map(UUID::toString).toList())
                .orElseGet(List::of);
    }

    private CompletionStage<CommandResponse> summary(List<String> tokens) {
        if (tokens.size() < 2 || tokens.size() > 3) {
            return usage("history <player> [page]");
        }
        Optional<StaffPlayerIdentity> selected = players.findPlayer(tokens.get(1));
        if (selected.isEmpty()) {
            return completed(CommandResponse.failure("history.player.unknown",
                    m("command.history.player_unknown", "player", tokens.get(1))));
        }
        int pageNumber;
        try {
            pageNumber = tokens.size() == 3 ? Integer.parseInt(tokens.get(2)) : 1;
        } catch (NumberFormatException exception) {
            return invalidPage(tokens.getLast());
        }
        if (pageNumber < 1) {
            return invalidPage(Integer.toString(pageNumber));
        }
        StaffPlayerIdentity player = selected.orElseThrow();
        int offset;
        try {
            offset = Math.multiplyExact(pageNumber - 1, PAGE_SIZE);
        } catch (ArithmeticException exception) {
            return invalidPage(Integer.toString(pageNumber));
        }
        return history.forPlayer(player.playerId(), offset, PAGE_SIZE).thenApply(page -> {
            if (pageNumber > 1 && page.entries().isEmpty()) {
                return CommandResponse.failure("history.page.invalid",
                        m("command.history.page_invalid", "current", pageNumber));
            }
            displayedEntries.put(player.playerId(), page.entries().stream()
                    .map(StaffHistorySource.Entry::entryId).toList());
            ArrayList<MessageReference> lines = new ArrayList<>();
            lines.add(m("command.history.header", "player", player.name()));
            if (page.entries().isEmpty()) {
                lines.add(m("command.history.empty"));
                return CommandResponse.success("history.empty", lines);
            }
            page.entries().forEach(entry -> lines.add(m(
                    StaffHistoryPresentation.commandEntryOutcomeKey(entry.outcome()),
                    "before", entry.before(), "after", entry.after(),
                    "status", StaffHistoryPresentation.outcomeText(entry.outcome()),
                    "value", StaffHistoryPresentation.timestamp(entry.occurredAt()),
                    "player", player.name(), "id", entry.entryId())));
            long pages = page.totalEntries() == 0L
                    ? 1L : ((page.totalEntries() - 1L) / PAGE_SIZE) + 1L;
            lines.add(m("command.history.page", "current", pageNumber, "total", pages,
                    "player", player.name(),
                    "previous", page.hasPrevious() ? pageNumber - 1 : "",
                    "next", page.hasNext() ? pageNumber + 1 : ""));
            return CommandResponse.success("history.summary", lines);
        });
    }

    private CompletionStage<CommandResponse> detail(List<String> tokens) {
        if (tokens.size() != 4) {
            return usage("history details <player> <entry>");
        }
        Optional<StaffPlayerIdentity> selected = players.findPlayer(tokens.get(2));
        if (selected.isEmpty()) {
            return completed(CommandResponse.failure("history.player.unknown",
                    m("command.history.player_unknown", "player", tokens.get(2))));
        }
        UUID entryId;
        try {
            entryId = UUID.fromString(tokens.get(3));
        } catch (IllegalArgumentException exception) {
            return completed(CommandResponse.failure("history.entry.invalid",
                    m("command.history.entry_invalid", "id", tokens.get(3))));
        }
        StaffPlayerIdentity player = selected.orElseThrow();
        return history.entry(player.playerId(), entryId).thenApply(found -> found
                .map(StaffHistoryCommandService::detail)
                .orElseGet(() -> CommandResponse.failure("history.entry.unknown",
                        m("command.history.entry_unknown", "id", entryId))));
    }

    private static CommandResponse detail(StaffHistorySource.Entry entry) {
        ArrayList<MessageReference> lines = new ArrayList<>();
        lines.add(m("command.history.detail.header", "before", entry.before(), "after", entry.after()));
        lines.add(m(StaffHistoryPresentation.commandDetailOutcomeKey(entry.outcome()),
                "status", StaffHistoryPresentation.outcomeText(entry.outcome())));
        StaffHistoryPresentation.FinancialOutcome financial =
                StaffHistoryPresentation.financialOutcome(entry);
        financial.balance().ifPresent(balance -> lines.add(m("command.history.detail.balance",
                "before", balance.before(), "after", balance.after())));
        financial.fallbackCost().ifPresent(value ->
                lines.add(m("command.history.detail.cost", "value", value)));
        financial.reward().ifPresent(value ->
                lines.add(m("command.history.detail.reward", "value", value)));
        lines.add(m("command.history.detail.time", "value",
                StaffHistoryPresentation.timestamp(entry.occurredAt())));
        return CommandResponse.success("history.detail", lines);
    }

    private static CompletionStage<CommandResponse> usage(String value) {
        return completed(CommandResponse.failure("command.invalid",
                m("command.error.usage", "usage", "/maddprestige " + value)));
    }

    private static CompletionStage<CommandResponse> invalidPage(String page) {
        return completed(CommandResponse.failure("history.page.invalid",
                m("command.history.page_invalid", "current", page)));
    }

    private static CompletionStage<CommandResponse> completed(CommandResponse response) {
        return CompletableFuture.completedFuture(response);
    }

    private static MessageReference m(String key, Object... arguments) {
        return MessageReference.of(key, arguments);
    }
}
