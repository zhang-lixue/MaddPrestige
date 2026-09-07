package net.maddkraft.maddprestige.core.prestige;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.cost.PlannedCost;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.core.requirement.BoundRequirementEvaluation;

/** Exact immutable confirmation payload consumed unchanged by future administration UI. */
public record PrestigeSimulation(
        UUID playerId,
        StageId sourceStage,
        StageId resetStage,
        long currentPrestigeBefore,
        long currentPrestigeAfter,
        long lifetimePrestigeBefore,
        long lifetimePrestigeAfter,
        BoundRequirementEvaluation requirements,
        List<PlannedCost> costs,
        List<PlannedReward> rewards,
        List<CurrencyConsequence> currencyChanges,
        List<ComponentConsequence> componentConsequences,
        ScopeId prestigeScopeBefore,
        ScopeId prestigeScopeAfter,
        List<PrestigeBaselineConsequence> baselineChanges,
        ScopedRequirementStateConsequence scopedRequirementState,
        List<MilestoneConsequence> milestoneConsequences,
        SeasonConsequence seasonConsequence,
        List<ProviderActionConsequence> providerActions,
        List<String> uncertainExternalEffects,
        ConfigRevisionId playerStageProvenance,
        ConfigRevisionId playerPrestigeProvenance,
        ConfigRevisionId activeConfigRevision,
        long expectedStageRevision,
        long expectedPrestigeRevision,
        Map<ProviderId, Long> providerGenerations,
        Instant plannedAt) {
    public PrestigeSimulation {
        playerId = Objects.requireNonNull(playerId, "player ID");
        sourceStage = Objects.requireNonNull(sourceStage, "source stage");
        resetStage = Objects.requireNonNull(resetStage, "reset stage");
        if (currentPrestigeBefore < 0 || currentPrestigeAfter < currentPrestigeBefore
                || lifetimePrestigeBefore < 0 || lifetimePrestigeAfter < lifetimePrestigeBefore
                || expectedStageRevision < 0 || expectedPrestigeRevision < 0) {
            throw new IllegalArgumentException("Prestige simulation counters/revisions are invalid");
        }
        requirements = Objects.requireNonNull(requirements, "requirements");
        costs = List.copyOf(Objects.requireNonNull(costs, "costs"));
        rewards = List.copyOf(Objects.requireNonNull(rewards, "rewards"));
        currencyChanges = List.copyOf(Objects.requireNonNull(currencyChanges, "currency changes"));
        componentConsequences = List.copyOf(Objects.requireNonNull(componentConsequences, "components"));
        prestigeScopeBefore = Objects.requireNonNull(prestigeScopeBefore, "scope before");
        prestigeScopeAfter = Objects.requireNonNull(prestigeScopeAfter, "scope after");
        baselineChanges = List.copyOf(Objects.requireNonNull(baselineChanges, "baseline changes"));
        scopedRequirementState = Objects.requireNonNull(scopedRequirementState, "scoped requirement state");
        milestoneConsequences = List.copyOf(Objects.requireNonNull(milestoneConsequences, "milestones"));
        seasonConsequence = Objects.requireNonNull(seasonConsequence, "season consequence");
        providerActions = List.copyOf(Objects.requireNonNull(providerActions, "provider actions"));
        uncertainExternalEffects = List.copyOf(Objects.requireNonNull(
                uncertainExternalEffects, "uncertain external effects"));
        playerStageProvenance = Objects.requireNonNull(playerStageProvenance, "stage provenance");
        playerPrestigeProvenance = Objects.requireNonNull(playerPrestigeProvenance, "Prestige provenance");
        activeConfigRevision = Objects.requireNonNull(activeConfigRevision, "active revision");
        providerGenerations = Map.copyOf(Objects.requireNonNull(providerGenerations, "provider generations"));
        plannedAt = Objects.requireNonNull(plannedAt, "planned time");
        if (scopedRequirementState.newScopeEstablished() == prestigeScopeBefore.equals(prestigeScopeAfter)) {
            throw new IllegalArgumentException("Prestige scope identity contradicts scoped-state consequence");
        }
        if (baselineChanges.size() != scopedRequirementState.initializedBaselineCount()) {
            throw new IllegalArgumentException("Structured baseline count differs from exact baseline changes");
        }
    }
}
