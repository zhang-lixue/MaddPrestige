package net.maddkraft.maddprestige.core.requirement;

import net.maddkraft.maddprestige.api.id.RequirementId;

public sealed interface RequirementNode permits RequirementLeaf, RequirementGroup {
    RequirementId id();
}
