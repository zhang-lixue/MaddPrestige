package net.maddkraft.maddprestige.core.rank;

import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.rank.ManagedRankState;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageDefinition;

public final class RankReconciler {
    public RankReconciliationDecision assess(
            StageConfiguration configuration,
            Optional<PlayerStageState> internalState,
            ManagedRankState externalState) {
        if (externalState.hasAmbiguity()) {
            return decision(ReconciliationStatus.AMBIGUOUS, ReconciliationAction.NONE, Optional.empty(),
                    "Contextual or temporary managed-group membership is ambiguous and is preserved.");
        }
        Optional<ProviderId> providerId = configuration.rankProvider();
        if (providerId.isEmpty()) {
            return decision(ReconciliationStatus.MATCHED, ReconciliationAction.NONE, Optional.empty(),
                    "The ladder has no external rank projection.");
        }
        if (internalState.isEmpty()) {
            return assessMissingInternal(configuration, providerId.orElseThrow(), externalState);
        }
        StageId internalStage = internalState.orElseThrow().stageId();
        StageDefinition definition = configuration.stages().get(internalStage);
        if (definition == null || !definition.enabled()) {
            return decision(ReconciliationStatus.FAILED, ReconciliationAction.NONE, Optional.of(internalStage),
                    "Internal stage ID is not valid in the pinned configuration; no baseline fallback is inferred.");
        }
        Optional<String> desired = definition.projection().groupName();
        boolean matches = desired.map(group -> externalState.permanentContextFreeGroups().equals(java.util.Set.of(group)))
                .orElseGet(() -> externalState.permanentContextFreeGroups().isEmpty());
        if (matches) {
            return decision(ReconciliationStatus.MATCHED, ReconciliationAction.NONE, Optional.of(internalStage),
                    "Internal stage and managed external membership match.");
        }
        return switch (configuration.reconciliationPolicy()) {
            case WARN_ONLY -> decision(ReconciliationStatus.WARNED, ReconciliationAction.NONE,
                    Optional.of(internalStage), "Mismatch reported without external mutation under warn-only policy.");
            case MADD_PRESTIGE_AUTHORITATIVE -> decision(ReconciliationStatus.REPAIR_REQUIRED,
                    ReconciliationAction.PROJECT_INTERNAL_STATE, Optional.of(internalStage),
                    "Pinned internal stage must be projected through a persisted repair operation.");
            case IMPORT_ONCE -> decision(ReconciliationStatus.WARNED, ReconciliationAction.NONE,
                    Optional.of(internalStage),
                    "Import-once never overwrites an existing internal stage; mismatch remains reported.");
        };
    }

    private static RankReconciliationDecision assessMissingInternal(
            StageConfiguration configuration,
            ProviderId providerId,
            ManagedRankState externalState) {
        if (configuration.reconciliationPolicy() != ReconciliationPolicy.IMPORT_ONCE) {
            return decision(ReconciliationStatus.MISSING_INTERNAL_STATE, ReconciliationAction.NONE, Optional.empty(),
                    "No internal stage exists; external state is never interpreted as baseline implicitly.");
        }
        if (externalState.permanentContextFreeGroups().size() != 1) {
            return decision(ReconciliationStatus.AMBIGUOUS, ReconciliationAction.NONE, Optional.empty(),
                    "Import requires exactly one unambiguous managed group; found "
                            + externalState.permanentContextFreeGroups().size() + ".");
        }
        String group = externalState.permanentContextFreeGroups().iterator().next();
        Optional<StageId> stage = configuration.stageForGroup(providerId, group);
        if (stage.isEmpty()) {
            return decision(ReconciliationStatus.AMBIGUOUS, ReconciliationAction.NONE, Optional.empty(),
                    "Managed group does not map uniquely to an enabled stage in the pinned configuration.");
        }
        return decision(ReconciliationStatus.IMPORT_READY, ReconciliationAction.IMPORT_EXTERNAL_STATE, stage,
                "External state can seed one new internal stage record through the explicit import-once flow.");
    }

    private static RankReconciliationDecision decision(
            ReconciliationStatus status,
            ReconciliationAction action,
            Optional<StageId> target,
            String reason) {
        return new RankReconciliationDecision(status, action, target, reason);
    }
}
