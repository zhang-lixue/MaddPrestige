package net.maddkraft.maddprestige.platform.paper.integration;

import java.util.Objects;

/** Pure lifecycle authority for the asynchronous CraftEngine item registry. */
public final class CraftEngineRegistryLifecycle {
    private State state = State.ABSENT;

    public State state() {
        return state;
    }

    public Transition dependencyPresent() {
        state = State.WAITING_FOR_REGISTRY;
        return Transition.NONE;
    }

    public Transition startupProbe(boolean nonEmptyPublicRegistry) {
        if (state != State.WAITING_FOR_REGISTRY || !nonEmptyPublicRegistry) {
            return Transition.NONE;
        }
        state = State.AVAILABLE;
        return Transition.BIND;
    }

    public Transition reloadCompleted() {
        if (state == State.WAITING_FOR_REGISTRY) {
            state = State.AVAILABLE;
            return Transition.BIND;
        }
        return state == State.AVAILABLE ? Transition.REBIND : Transition.NONE;
    }

    public Transition dependencyDisabled() {
        Transition transition = state == State.AVAILABLE ? Transition.UNBIND : Transition.NONE;
        state = State.DISABLED_OR_UNAVAILABLE;
        return transition;
    }

    public Transition dependencyAbsent() {
        Transition transition = state == State.AVAILABLE ? Transition.UNBIND : Transition.NONE;
        state = State.ABSENT;
        return transition;
    }

    public void bindingFailed() {
        state = State.DISABLED_OR_UNAVAILABLE;
    }

    public enum State {
        ABSENT,
        WAITING_FOR_REGISTRY,
        AVAILABLE,
        DISABLED_OR_UNAVAILABLE
    }

    public enum Transition {
        NONE,
        BIND,
        REBIND,
        UNBIND;

        public boolean is(Transition expected) {
            return this == Objects.requireNonNull(expected, "expected transition");
        }
    }
}
