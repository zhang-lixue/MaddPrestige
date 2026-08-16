package net.maddkraft.maddprestige.core.operation;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.maddkraft.maddprestige.api.operation.ActionState;
import net.maddkraft.maddprestige.api.operation.OperationState;

public final class OperationStateMachine {
    private static final Map<OperationState, Set<OperationState>> OPERATION_TRANSITIONS = operationTransitions();
    private static final Map<ActionState, Set<ActionState>> ACTION_TRANSITIONS = actionTransitions();

    public OperationState transition(OperationState current, OperationState requested) {
        requireAllowed(current, requested, OPERATION_TRANSITIONS, "operation");
        return requested;
    }

    public ActionState transition(ActionState current, ActionState requested) {
        requireAllowed(current, requested, ACTION_TRANSITIONS, "action");
        return requested;
    }

    public boolean canTransition(OperationState current, OperationState requested) {
        return OPERATION_TRANSITIONS.getOrDefault(current, Set.of()).contains(requested);
    }

    private static <S extends Enum<S>> void requireAllowed(
            S current, S requested, Map<S, Set<S>> transitions, String kind) {
        Objects.requireNonNull(current, "current state");
        Objects.requireNonNull(requested, "requested state");
        if (!transitions.getOrDefault(current, Set.of()).contains(requested)) {
            throw new IllegalTransitionException("Illegal " + kind + " state transition: " + current + " -> " + requested);
        }
    }

    private static Map<OperationState, Set<OperationState>> operationTransitions() {
        EnumMap<OperationState, Set<OperationState>> transitions = new EnumMap<>(OperationState.class);
        transitions.put(OperationState.PLANNED, EnumSet.of(OperationState.PREPARED, OperationState.FAILED));
        transitions.put(OperationState.PREPARED, EnumSet.of(OperationState.EXECUTING, OperationState.FAILED));
        transitions.put(OperationState.EXECUTING, EnumSet.of(
                OperationState.STATE_COMMITTED, OperationState.COMPENSATING, OperationState.FAILED,
                OperationState.NEEDS_RECONCILIATION));
        transitions.put(OperationState.STATE_COMMITTED,
                EnumSet.of(OperationState.COMPLETED, OperationState.NEEDS_RECONCILIATION));
        transitions.put(OperationState.COMPENSATING,
                EnumSet.of(OperationState.COMPENSATED, OperationState.FAILED, OperationState.NEEDS_RECONCILIATION));
        return Map.copyOf(transitions);
    }

    private static Map<ActionState, Set<ActionState>> actionTransitions() {
        EnumMap<ActionState, Set<ActionState>> transitions = new EnumMap<>(ActionState.class);
        transitions.put(ActionState.PENDING, EnumSet.of(ActionState.STARTED, ActionState.FAILED));
        transitions.put(ActionState.STARTED,
                EnumSet.of(ActionState.SUCCEEDED, ActionState.FAILED, ActionState.UNCERTAIN));
        transitions.put(ActionState.SUCCEEDED,
                EnumSet.of(ActionState.VERIFIED, ActionState.FAILED, ActionState.UNCERTAIN, ActionState.COMPENSATED));
        transitions.put(ActionState.VERIFIED, EnumSet.of(ActionState.COMPENSATED));
        transitions.put(ActionState.FAILED, EnumSet.of(ActionState.COMPENSATED));
        transitions.put(ActionState.UNCERTAIN, EnumSet.of(ActionState.VERIFIED, ActionState.COMPENSATED));
        return Map.copyOf(transitions);
    }
}
