package net.maddkraft.maddprestige.core.requirement;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.RequirementId;

public record RequirementLeaf(RequirementDefinition definition) implements RequirementNode {
    public RequirementLeaf {
        definition = Objects.requireNonNull(definition, "definition");
    }

    @Override
    public RequirementId id() {
        return definition.id();
    }
}
