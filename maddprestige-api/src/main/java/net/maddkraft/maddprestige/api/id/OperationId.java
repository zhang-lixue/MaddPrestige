package net.maddkraft.maddprestige.api.id;

import java.util.Objects;
import java.util.UUID;

public record OperationId(UUID value) {
    public OperationId {
        Objects.requireNonNull(value, "operation UUID");
    }

    public static OperationId random() {
        return new OperationId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
