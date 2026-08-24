package net.maddkraft.maddprestige.platform.paper.integration;

import java.util.HashSet;
import java.util.Set;

/** Prevents provider-lifecycle callbacks from recursively starting the same optional binding. */
final class BindingAttemptGuard {
    private final Set<String> active = new HashSet<>();

    boolean begin(String dependency) {
        return active.add(java.util.Objects.requireNonNull(dependency, "dependency"));
    }

    void end(String dependency) {
        if (!active.remove(java.util.Objects.requireNonNull(dependency, "dependency"))) {
            throw new IllegalStateException("Optional binding attempt is not active: " + dependency);
        }
    }
}
