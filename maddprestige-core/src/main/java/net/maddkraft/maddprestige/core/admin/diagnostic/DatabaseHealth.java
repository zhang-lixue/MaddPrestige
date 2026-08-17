package net.maddkraft.maddprestige.core.admin.diagnostic;

import java.util.Objects;

public record DatabaseHealth(boolean reachable, boolean migrationsCurrent, String backend, String detail) {
    public DatabaseHealth {
        backend = Objects.requireNonNull(backend, "backend");
        detail = Objects.requireNonNull(detail, "detail");
    }
}
