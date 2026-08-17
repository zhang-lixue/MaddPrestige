package net.maddkraft.maddprestige.core.admin.setup;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.StageId;

public record SetupStage(StageId id, String displayName, Optional<String> externalGroup) {
    public SetupStage {
        id = Objects.requireNonNull(id, "stage ID");
        displayName = Objects.requireNonNull(displayName, "display name");
        externalGroup = Objects.requireNonNull(externalGroup, "external group");
        if (displayName.isBlank() || displayName.length() > 128) {
            throw new IllegalArgumentException("Stage display name must contain 1-128 characters");
        }
        externalGroup.ifPresent(group -> {
            if (group.isBlank() || group.length() > 128 || containsControl(group)) {
                throw new IllegalArgumentException("External group must contain 1-128 printable characters");
            }
        });
    }

    private static boolean containsControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
