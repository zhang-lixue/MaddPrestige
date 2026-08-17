package net.maddkraft.maddprestige.core.admin.config;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.core.config.ContentHash;
import net.maddkraft.maddprestige.core.stage.StageRemapPlan;

public interface StageReferenceMigrationStore {
    StageReferenceSnapshot capture(Optional<StageRemapPlan> plan);

    ConfigurationStageTransitionExecution beginTransition(
            ConfigRevisionId configurationRevision,
            Optional<ConfigRevisionId> priorRevision,
            ContentHash candidateHash,
            Map<StageId, ConfigurationStageReservationKind> reservedStages,
            Optional<StageRemapSnapshot> remap,
            Actor actor,
            String reason,
            Instant occurredAt);

    void markTransitionApplied(ConfigRevisionId configurationRevision, Instant occurredAt);

    void markTransitionFailedSafe(ConfigRevisionId configurationRevision, String detail, Instant occurredAt);

    void markTransitionNeedsReconciliation(
            ConfigRevisionId configurationRevision,
            String detail,
            Instant occurredAt);

    List<ConfigurationStageTransitionState> transitions(int limit);

    List<StageRemapReconciliation> unresolved(int limit);

    static StageReferenceMigrationStore readOnly(java.util.function.Supplier<java.util.Map<
            net.maddkraft.maddprestige.api.id.StageId, Long>> counts) {
        java.util.Objects.requireNonNull(counts, "counts");
        return new StageReferenceMigrationStore() {
            @Override
            public StageReferenceSnapshot capture(Optional<StageRemapPlan> plan) {
                if (plan.isPresent()) {
                    throw new IllegalStateException("The configured stage-reference source cannot execute remaps");
                }
                return new StageReferenceSnapshot(counts.get(), Optional.empty());
            }

            @Override
            public ConfigurationStageTransitionExecution beginTransition(
                    ConfigRevisionId revision,
                    Optional<ConfigRevisionId> priorRevision,
                    ContentHash candidateHash,
                    Map<StageId, ConfigurationStageReservationKind> reservedStages,
                    Optional<StageRemapSnapshot> remap,
                    Actor actor,
                    String reason,
                    Instant occurredAt) {
                throw new IllegalStateException(
                        "The configured stage-reference source cannot reserve destructive transitions");
            }

            @Override
            public void markTransitionApplied(ConfigRevisionId revision, Instant occurredAt) {
                throw new IllegalStateException(
                        "The configured stage-reference source cannot reserve destructive transitions");
            }

            @Override
            public void markTransitionFailedSafe(ConfigRevisionId revision, String detail, Instant occurredAt) {
                throw new IllegalStateException(
                        "The configured stage-reference source cannot reserve destructive transitions");
            }

            @Override
            public void markTransitionNeedsReconciliation(
                    ConfigRevisionId revision,
                    String detail,
                    Instant occurredAt) {
                throw new IllegalStateException(
                        "The configured stage-reference source cannot reserve destructive transitions");
            }

            @Override
            public List<ConfigurationStageTransitionState> transitions(int limit) {
                return List.of();
            }

            @Override
            public List<StageRemapReconciliation> unresolved(int limit) {
                return List.of();
            }
        };
    }
}
