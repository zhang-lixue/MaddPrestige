package net.maddkraft.maddprestige.core.stage;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.StageId;

public record StageDefinition(
        StageId id,
        boolean enabled,
        String displayName,
        Map<String, String> displayMetadata,
        StageProjection projection) {
    public StageDefinition {
        id = Objects.requireNonNull(id, "stage ID");
        displayName = Objects.requireNonNull(displayName, "display name");
        displayMetadata = Map.copyOf(Objects.requireNonNull(displayMetadata, "display metadata"));
        projection = Objects.requireNonNull(projection, "projection");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("Stage display name cannot be blank");
        }
    }
}
