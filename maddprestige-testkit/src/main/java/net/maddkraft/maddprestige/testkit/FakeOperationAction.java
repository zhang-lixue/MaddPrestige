package net.maddkraft.maddprestige.testkit;

import java.util.concurrent.atomic.AtomicInteger;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.result.Result;

public final class FakeOperationAction {
    private final String injectionPoint;
    private final FailureInjector failures;
    private final AtomicInteger executions = new AtomicInteger();

    public FakeOperationAction(String injectionPoint, FailureInjector failures) {
        this.injectionPoint = injectionPoint;
        this.failures = failures;
    }

    public Result<Integer> execute(OperationId operationId) {
        failures.check(injectionPoint);
        return Result.success(executions.incrementAndGet());
    }

    public int executions() {
        return executions.get();
    }
}
