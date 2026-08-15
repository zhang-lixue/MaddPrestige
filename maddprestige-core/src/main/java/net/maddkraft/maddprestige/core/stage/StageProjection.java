package net.maddkraft.maddprestige.core.stage;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.core.rank.ProjectionPolicy;

public record StageProjection(
        ProjectionPolicy policy,
        Optional<ProviderId> providerId,
        Optional<String> groupName) {
    public StageProjection {
        policy = Objects.requireNonNull(policy, "projection policy");
        providerId = Objects.requireNonNull(providerId, "provider ID");
        groupName = Objects.requireNonNull(groupName, "group name");
        if (policy == ProjectionPolicy.NONE && (providerId.isPresent() || groupName.isPresent())) {
            throw new IllegalArgumentException("Projection none cannot name a provider or group");
        }
        if (policy == ProjectionPolicy.GROUP && (providerId.isEmpty() || groupName.isEmpty())) {
            throw new IllegalArgumentException("Group projection requires a provider and group");
        }
        groupName.ifPresent(group -> {
            if (group.isBlank()) {
                throw new IllegalArgumentException("Projection group cannot be blank");
            }
        });
    }

    public static StageProjection none() {
        return new StageProjection(ProjectionPolicy.NONE, Optional.empty(), Optional.empty());
    }

    public static StageProjection group(ProviderId providerId, String groupName) {
        return new StageProjection(ProjectionPolicy.GROUP, Optional.of(providerId), Optional.of(groupName));
    }
}
