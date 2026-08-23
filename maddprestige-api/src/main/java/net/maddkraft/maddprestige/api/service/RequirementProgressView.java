package net.maddkraft.maddprestige.api.service;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.metric.MetricValue;

/**
 * Immutable machine-readable progress for one requirement leaf in the currently evaluated operation.
 *
 * @param operation operation whose requirement tree contains the leaf
 * @param requirementId canonical configured requirement identity, 1-64 characters
 * @param status coarse machine-readable evaluation status
 * @param current exact observed value when the provider supplied one
 * @param target exact configured target when the requirement exposes one
 */
public record RequirementProgressView(
        OperationKind operation,
        String requirementId,
        RequirementProgressStatus status,
        Optional<MetricValue> current,
        Optional<MetricValue> target) {
    public RequirementProgressView {
        operation = Objects.requireNonNull(operation, "operation kind");
        requirementId = Objects.requireNonNull(requirementId, "requirement ID");
        status = Objects.requireNonNull(status, "status");
        current = Objects.requireNonNull(current, "current value");
        target = Objects.requireNonNull(target, "target value");
        if (!requirementId.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Requirement ID is outside stable bounds");
        }
    }
}
