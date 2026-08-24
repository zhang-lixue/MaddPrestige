package net.maddkraft.maddprestige.platform.paper.provider;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ProviderId;

/**
 * Owns callback isolation budgets by stable, owner-qualified provider identity.
 *
 * <p>A generation lease keeps its identity state reachable. Closing the last generation does not reclaim that
 * state until every callback admitted by an older generation has exited, so a rebind cannot mint fresh permits
 * while old synchronous work still occupies the shared executor.</p>
 */
final class ProviderCallbackBulkhead {
    static final int MAX_CONCURRENT_CALLBACKS = 4;

    private final Map<ProviderId, State> states = new HashMap<>();

    synchronized Generation acquire(ProviderId providerId) {
        ProviderId identity = Objects.requireNonNull(providerId, "provider ID");
        State state = states.computeIfAbsent(identity, ignored -> new State());
        state.generations++;
        return new Generation(this, identity, state);
    }

    synchronized int stateCount() {
        return states.size();
    }

    synchronized int activeCallbacks(ProviderId providerId) {
        State state = states.get(Objects.requireNonNull(providerId, "provider ID"));
        return state == null ? 0 : state.activeCallbacks;
    }

    private synchronized boolean tryEnter(Generation generation) {
        if (generation.closed || states.get(generation.identity) != generation.state
                || generation.state.activeCallbacks == MAX_CONCURRENT_CALLBACKS) {
            return false;
        }
        generation.state.activeCallbacks++;
        return true;
    }

    private synchronized void exit(Generation generation) {
        if (generation.state.activeCallbacks <= 0) {
            throw new IllegalStateException("Provider callback permit was not active");
        }
        generation.state.activeCallbacks--;
        reclaim(generation.identity, generation.state);
    }

    private synchronized void close(Generation generation) {
        if (generation.closed) {
            return;
        }
        generation.closed = true;
        generation.state.generations--;
        reclaim(generation.identity, generation.state);
    }

    private void reclaim(ProviderId identity, State state) {
        if (state.generations == 0 && state.activeCallbacks == 0) {
            states.remove(identity, state);
        }
    }

    static final class Generation implements AutoCloseable {
        private final ProviderCallbackBulkhead owner;
        private final ProviderId identity;
        private final State state;
        private boolean closed;

        private Generation(ProviderCallbackBulkhead owner, ProviderId identity, State state) {
            this.owner = owner;
            this.identity = identity;
            this.state = state;
        }

        boolean tryEnter() {
            return owner.tryEnter(this);
        }

        void exit() {
            owner.exit(this);
        }

        @Override
        public void close() {
            owner.close(this);
        }
    }

    private static final class State {
        private int generations;
        private int activeCallbacks;
    }
}
