package net.maddkraft.maddprestige.core.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.maddkraft.maddprestige.api.operation.ActionState;
import net.maddkraft.maddprestige.api.operation.OperationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OperationStateMachineTest {
    private final OperationStateMachine stateMachine = new OperationStateMachine();

    @Test
    @DisplayName("[A59][Phase1-hard-7] Operation and action state machines reject illegal transitions")
    void rejectsIllegalTransitions() {
        assertEquals(OperationState.PREPARED,
                stateMachine.transition(OperationState.PLANNED, OperationState.PREPARED));
        assertEquals(ActionState.UNCERTAIN,
                stateMachine.transition(ActionState.STARTED, ActionState.UNCERTAIN));
        assertThrows(IllegalTransitionException.class,
                () -> stateMachine.transition(OperationState.PLANNED, OperationState.COMPLETED));
        assertThrows(IllegalTransitionException.class,
                () -> stateMachine.transition(ActionState.PENDING, ActionState.VERIFIED));
    }
}
