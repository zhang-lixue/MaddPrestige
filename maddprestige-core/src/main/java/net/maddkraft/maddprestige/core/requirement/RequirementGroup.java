package net.maddkraft.maddprestige.core.requirement;

import java.util.List;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public record RequirementGroup(
        RequirementId id,
        RequirementGroupMode mode,
        List<RequirementChild> children,
        ExactDecimal threshold,
        CatchUpProfile catchUp,
        String displayName) implements RequirementNode {
    public RequirementGroup {
        id = Objects.requireNonNull(id, "group ID");
        mode = Objects.requireNonNull(mode, "group mode");
        children = List.copyOf(Objects.requireNonNull(children, "children"));
        threshold = Objects.requireNonNull(threshold, "threshold");
        catchUp = Objects.requireNonNull(catchUp, "catch-up");
        displayName = Objects.requireNonNull(displayName, "display name");
    }

    public static RequirementGroup all(RequirementId id, List<RequirementChild> children) {
        return new RequirementGroup(id, RequirementGroupMode.ALL, children, ExactDecimal.parse("1"),
                CatchUpProfile.disabled(), id.value());
    }
}
