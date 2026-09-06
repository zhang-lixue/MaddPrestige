package net.maddkraft.maddprestige.core.admin.config;

import java.util.List;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

public record MoneyAmountPage(
        ConfigRevisionId revision,
        long prestigeLevel,
        String currentAmount,
        List<String> amounts,
        int pageIndex,
        boolean hasPrevious,
        boolean hasNext) {
    public MoneyAmountPage {
        revision = Objects.requireNonNull(revision, "revision");
        currentAmount = Objects.requireNonNull(currentAmount, "current amount");
        amounts = List.copyOf(Objects.requireNonNull(amounts, "amounts"));
        if (prestigeLevel < 1 || pageIndex < 0 || amounts.isEmpty()) {
            throw new IllegalArgumentException("Money amount page requires a positive level and selectable amounts");
        }
    }
}
