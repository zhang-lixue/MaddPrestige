package net.maddkraft.maddprestige.core.admin.setup;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RewardId;

public record SetupReward(
        RewardId id,
        ProviderId providerId,
        String type,
        String valueType,
        String value,
        String displayName) {
    public SetupReward {
        id = Objects.requireNonNull(id, "reward ID");
        providerId = Objects.requireNonNull(providerId, "provider ID");
        type = bounded(type, "type");
        valueType = bounded(valueType, "value type");
        value = bounded(value, "value");
        displayName = bounded(displayName, "display name");
    }

    private static String bounded(String value, String label) {
        value = Objects.requireNonNull(value, label);
        if (value.isBlank() || value.length() > 128 || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Setup reward " + label + " must contain 1-128 printable characters");
        }
        return value;
    }
}
