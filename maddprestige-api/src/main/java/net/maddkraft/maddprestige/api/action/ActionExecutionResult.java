package net.maddkraft.maddprestige.api.action;

import java.util.Objects;
import java.util.Optional;

public record ActionExecutionResult(ActionExecutionStatus status, Optional<String> detail) {
    public ActionExecutionResult {
        status = Objects.requireNonNull(status, "execution status");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public static ActionExecutionResult applied() {
        return new ActionExecutionResult(ActionExecutionStatus.APPLIED, Optional.empty());
    }

    public static ActionExecutionResult unchanged() {
        return new ActionExecutionResult(ActionExecutionStatus.UNCHANGED, Optional.empty());
    }

    public static ActionExecutionResult failed(String detail) {
        return new ActionExecutionResult(ActionExecutionStatus.FAILED, Optional.of(detail));
    }

    public static ActionExecutionResult uncertain(String detail) {
        return new ActionExecutionResult(ActionExecutionStatus.UNCERTAIN, Optional.of(detail));
    }
}
