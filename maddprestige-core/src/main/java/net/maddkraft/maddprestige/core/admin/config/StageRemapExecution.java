package net.maddkraft.maddprestige.core.admin.config;

import java.util.Objects;
import java.util.UUID;

public record StageRemapExecution(UUID operationId, int migratedPlayers) {
    public StageRemapExecution {
        operationId = Objects.requireNonNull(operationId, "operation ID");
        if (migratedPlayers < 1) {
            throw new IllegalArgumentException("A stage remap execution must migrate at least one player");
        }
    }
}
