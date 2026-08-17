package net.maddkraft.maddprestige.core.admin.setup;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.ProviderId;

public record SetupCost(
        CostId id,
        ProviderId providerId,
        String type,
        String valueType,
        String amount,
        String displayName) {
    public SetupCost {
        id = Objects.requireNonNull(id, "cost ID");
        providerId = Objects.requireNonNull(providerId, "provider ID");
        type = bounded(type, "type");
        valueType = bounded(valueType, "value type");
        amount = bounded(amount, "amount");
        displayName = bounded(displayName, "display name");
    }

    private static String bounded(String value, String label) {
        value = Objects.requireNonNull(value, label);
        if (value.isBlank() || value.length() > 128 || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Setup cost " + label + " must contain 1-128 printable characters");
        }
        return value;
    }
}
