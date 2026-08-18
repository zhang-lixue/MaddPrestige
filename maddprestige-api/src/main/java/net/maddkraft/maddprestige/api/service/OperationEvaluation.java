package net.maddkraft.maddprestige.api.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.StageId;

/**
 * Immutable, side-effect-free evaluation of the current canonical operation path.
 *
 * @param kind operation evaluated
 * @param status coarse machine-readable result
 * @param sourceStage source stage, or empty when the player has no materialized stage
 * @param targetStage resolved target stage, or empty when no canonical target exists
 * @param configRevision exact authoritative revision, or empty when the runtime is unavailable
 * @param blockers immutable bounded machine-readable blockers, at most 128
 * @param requirements immutable bounded requirement-leaf snapshots, at most 512
 * @param simulation immutable plan projection, present for every eligible evaluation
 * @param observedAt non-null observation time; the snapshot is not live after construction
 */
public record OperationEvaluation(
        OperationKind kind,
        OperationEvaluationStatus status,
        Optional<StageId> sourceStage,
        Optional<StageId> targetStage,
        Optional<ConfigRevisionId> configRevision,
        List<ServiceError> blockers,
        List<RequirementProgressView> requirements,
        Optional<OperationSimulationView> simulation,
        Instant observedAt) {
    public OperationEvaluation {
        kind = Objects.requireNonNull(kind, "operation kind");
        status = Objects.requireNonNull(status, "evaluation status");
        sourceStage = Objects.requireNonNull(sourceStage, "source stage");
        targetStage = Objects.requireNonNull(targetStage, "target stage");
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        simulation = Objects.requireNonNull(simulation, "simulation");
        observedAt = Objects.requireNonNull(observedAt, "observation time");
        if (blockers.size() > 128 || requirements.size() > 512
                || (status == OperationEvaluationStatus.ELIGIBLE && (!blockers.isEmpty() || simulation.isEmpty()))) {
            throw new IllegalArgumentException("Operation evaluation is outside stable bounds");
        }
    }
}
