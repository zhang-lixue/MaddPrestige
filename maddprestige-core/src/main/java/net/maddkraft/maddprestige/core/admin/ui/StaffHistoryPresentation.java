package net.maddkraft.maddprestige.core.admin.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Shared player-safe language for GUI and command views of canonical Prestige history. */
public final class StaffHistoryPresentation {
    private static final DateTimeFormatter TIME = DateTimeFormatter
            .ofPattern("MMM d, uuuu • h:mm a", Locale.US);

    private StaffHistoryPresentation() {
    }

    public static String guiOutcomeKey(StaffHistorySource.Outcome outcome) {
        return outcomeKey("gui.item.staff.history.outcome.", outcome);
    }

    public static String commandEntryOutcomeKey(StaffHistorySource.Outcome outcome) {
        return outcomeKey("command.history.entry.", outcome);
    }

    public static String commandDetailOutcomeKey(StaffHistorySource.Outcome outcome) {
        return outcomeKey("command.history.outcome.", outcome);
    }

    public static String outcomeText(StaffHistorySource.Outcome outcome) {
        return switch (outcome) {
            case COMPLETED -> "Completed";
            case RECOVERED -> "Recovered";
            case REJECTED -> "Rejected";
            case ATTENTION -> "Attention";
            case FAILED -> "Failed";
            case IN_PROGRESS -> "In progress";
        };
    }

    public static String transactionKey(StaffHistorySource.Entry entry) {
        if (entry.costRecorded() && entry.rewardRecorded()) {
            return "gui.item.staff.history.transaction.cost_reward";
        }
        if (entry.costRecorded()) {
            return "gui.item.staff.history.transaction.cost";
        }
        if (entry.rewardRecorded()) {
            return "gui.item.staff.history.transaction.reward";
        }
        return "gui.item.staff.history.transaction.none";
    }

    /**
     * Selects the most useful truthful financial projection shared by Staff GUI and command history.
     * A complete canonical balance pair supersedes the less useful cost amount; incomplete balance
     * evidence falls back to the canonical cost/reward summaries without reconstruction.
     */
    public static FinancialOutcome financialOutcome(StaffHistorySource.Entry entry) {
        StaffHistorySource.Entry value = Objects.requireNonNull(entry, "history entry");
        Optional<BalanceOutcome> balance = value.balanceBefore().flatMap(before ->
                value.balanceAfter().map(after -> new BalanceOutcome(before, after)));
        return new FinancialOutcome(balance,
                balance.isPresent() ? Optional.empty() : value.costSummary(), value.rewardSummary());
    }

    public static String timestamp(Instant occurredAt) {
        return TIME.withZone(ZoneId.systemDefault()).format(occurredAt);
    }

    /** Presents one canonical snapshot amount without exposing its internal definition ID. */
    public static Optional<String> snapshotAmount(String snapshot) {
        String value = snapshot == null ? "" : snapshot.trim();
        if (value.isEmpty() || value.equals("[]") || value.equals("{}") || value.contains(",")) {
            return Optional.empty();
        }
        int separator = value.indexOf('=');
        if (separator <= 0 || separator == value.length() - 1) {
            return Optional.empty();
        }
        String id = value.substring(0, separator).toLowerCase(Locale.ROOT);
        String amount = value.substring(separator + 1).trim();
        if (amount.isEmpty()) {
            return Optional.empty();
        }
        boolean currency = id.contains("vault") || id.contains("money")
                || id.contains("economy") || id.contains("currency");
        return Optional.of((currency ? "$" : "") + amount);
    }

    private static String outcomeKey(String prefix, StaffHistorySource.Outcome outcome) {
        return prefix + outcome.name().toLowerCase(Locale.ROOT);
    }

    public record FinancialOutcome(
            Optional<BalanceOutcome> balance,
            Optional<String> fallbackCost,
            Optional<String> reward) {
        public FinancialOutcome {
            balance = Objects.requireNonNull(balance, "balance outcome");
            fallbackCost = Objects.requireNonNull(fallbackCost, "fallback cost");
            reward = Objects.requireNonNull(reward, "reward");
        }
    }

    public record BalanceOutcome(String before, String after) {
        public BalanceOutcome {
            before = Objects.requireNonNull(before, "balance before");
            after = Objects.requireNonNull(after, "balance after");
        }
    }
}
