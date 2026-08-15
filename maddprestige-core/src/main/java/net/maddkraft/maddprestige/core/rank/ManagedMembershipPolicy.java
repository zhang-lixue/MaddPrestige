package net.maddkraft.maddprestige.core.rank;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ManagedMembershipPolicy {
    public ManagedMembershipDelta plan(
            Set<String> currentDirectMemberships,
            Set<String> configuredManagedGroups,
            Optional<String> requestedGroup) {
        Objects.requireNonNull(currentDirectMemberships, "current memberships");
        Objects.requireNonNull(configuredManagedGroups, "managed groups");
        Objects.requireNonNull(requestedGroup, "requested group");
        requestedGroup.ifPresent(group -> {
            if (!configuredManagedGroups.contains(group)) {
                throw new IllegalArgumentException("Requested group is not explicitly managed: " + group);
            }
        });
        HashSet<String> remove = new HashSet<>(currentDirectMemberships);
        remove.retainAll(configuredManagedGroups);
        requestedGroup.ifPresent(remove::remove);
        Set<String> add = requestedGroup.filter(group -> !currentDirectMemberships.contains(group))
                .map(Set::of).orElseGet(Set::of);
        HashSet<String> preserve = new HashSet<>(currentDirectMemberships);
        preserve.removeAll(remove);
        return new ManagedMembershipDelta(remove, add, preserve);
    }
}
