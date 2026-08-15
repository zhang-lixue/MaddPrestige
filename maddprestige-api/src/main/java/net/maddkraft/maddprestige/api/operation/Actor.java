package net.maddkraft.maddprestige.api.operation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Actor(String type, Optional<UUID> uuid, String displayName) {
    public Actor {
        type = Objects.requireNonNull(type, "type");
        uuid = Objects.requireNonNull(uuid, "UUID");
        displayName = Objects.requireNonNull(displayName, "display name");
    }
}
