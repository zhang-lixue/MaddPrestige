package net.maddkraft.maddprestige.core.rank;

import java.util.Set;

public record ManagedMembershipDelta(Set<String> remove, Set<String> add, Set<String> preserve) {
    public ManagedMembershipDelta {
        remove = Set.copyOf(remove);
        add = Set.copyOf(add);
        preserve = Set.copyOf(preserve);
    }
}
