package net.maddkraft.maddprestige.core.schema;

import java.util.Objects;
import java.util.Optional;

public record SchemaConstraint(String code, String description, Optional<String> expression) {
    public SchemaConstraint {
        code = Objects.requireNonNull(code, "code");
        description = Objects.requireNonNull(description, "description");
        expression = Objects.requireNonNull(expression, "expression");
    }
}
