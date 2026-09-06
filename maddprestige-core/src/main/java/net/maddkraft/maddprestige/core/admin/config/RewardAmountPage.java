package net.maddkraft.maddprestige.core.admin.config;

import java.util.List;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

public record RewardAmountPage(
        ConfigRevisionId revision,
        long prestigeLevel,
        String currentAmount,
        List<String> amounts,
        int pageIndex,
        boolean hasPrevious,
        boolean hasNext) {
    public RewardAmountPage {
        revision = Objects.requireNonNull(revision, "revision");
        currentAmount = Objects.requireNonNull(currentAmount, "current amount");
        amounts = List.copyOf(Objects.requireNonNull(amounts, "amounts"));
        if (prestigeLevel < 1 || pageIndex < 0) {
            throw new IllegalArgumentException("Prestige level must be positive and page cannot be negative");
        }
    }
}
