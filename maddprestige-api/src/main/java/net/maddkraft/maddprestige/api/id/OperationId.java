package net.maddkraft.maddprestige.api.id;

import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.annotation.Stable;

/**
 * Durable journal identity allocated only after a progression request crosses its PRE boundary.
 *
 * @param value non-null immutable UUID
 */
@Stable
public record OperationId(UUID value) {
    public OperationId {
        Objects.requireNonNull(value, "operation UUID");
    }

    /**
     * Creates a locally random durable identity without I/O.
     *
     * @return new non-null immutable identity
     */
    public static OperationId random() {
        return new OperationId(UUID.randomUUID());
    }

    /**
     * Returns the canonical UUID wire representation.
     *
     * @return non-null lower-case UUID text
     */
    @Override
    public String toString() {
        return value.toString();
    }
}
