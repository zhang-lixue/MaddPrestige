package net.maddkraft.maddprestige.core.admin;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import net.maddkraft.maddprestige.core.plan.RankUpAuthorizationResult;
import net.maddkraft.maddprestige.core.plan.RankUpIntent;
import net.maddkraft.maddprestige.core.plan.RankUpPlan;
import net.maddkraft.maddprestige.core.prestige.PrestigeAuthorizationResult;
import net.maddkraft.maddprestige.core.prestige.PrestigeIntent;
import net.maddkraft.maddprestige.core.prestige.PrestigePlan;

public final class OperationPreviewService {
    private final Function<RankUpIntent, CompletionStage<RankUpAuthorizationResult>> rankUp;
    private final Function<PrestigeIntent, CompletionStage<PrestigeAuthorizationResult>> prestige;

    public OperationPreviewService(
            Function<RankUpIntent, CompletionStage<RankUpAuthorizationResult>> rankUp,
            Function<PrestigeIntent, CompletionStage<PrestigeAuthorizationResult>> prestige) {
        this.rankUp = Objects.requireNonNull(rankUp, "rank-up authorization");
        this.prestige = Objects.requireNonNull(prestige, "Prestige authorization");
    }

    public CompletionStage<OperationPreview> simulateRankUp(PermissionSubject subject, UUID playerId) {
        requireSimulation(subject, playerId, PhaseSixPermissions.RANK_UP);
        return rankUp.apply(new RankUpIntent(subject.actor(), playerId, Optional.empty(),
                "simulate-rankup-" + UUID.randomUUID())).thenApply(result -> result.plan()
                        .map(OperationPreviewService::rankUpPreview)
                        .orElseThrow(() -> rejected("rank-up", result.blockers())));
    }

    public CompletionStage<OperationPreview> simulatePrestige(PermissionSubject subject, UUID playerId) {
        requireSimulation(subject, playerId, PhaseSixPermissions.PRESTIGE);
        return prestige.apply(new PrestigeIntent(subject.actor(), playerId,
                "simulate-prestige-" + UUID.randomUUID())).thenApply(result -> result.plan()
                        .map(OperationPreviewService::prestigePreview)
                        .orElseThrow(() -> rejected("Prestige", List.of(result.rejection().orElse("unavailable")))));
    }

    public CompletionStage<RankUpPlan> authorizeRankUp(PermissionSubject subject, UUID playerId) {
        requireExecution(subject, playerId, PhaseSixPermissions.RANK_UP);
        return rankUp.apply(new RankUpIntent(subject.actor(), playerId, Optional.empty(),
                "confirm-rankup-" + UUID.randomUUID())).thenApply(result -> {
                    Optional<RankUpPlan> plan = result.plan();
                    if (plan.isPresent() && plan.orElseThrow().executionAllowed()
                            && plan.orElseThrow().blockers().isEmpty()) {
                        return plan.orElseThrow();
                    }
                    List<String> blockers = plan.map(RankUpPlan::blockers).filter(values -> !values.isEmpty())
                            .orElse(result.blockers());
                    throw rejected("rank-up", blockers);
                });
    }

    public CompletionStage<PrestigePlan> authorizePrestige(PermissionSubject subject, UUID playerId) {
        requireExecution(subject, playerId, PhaseSixPermissions.PRESTIGE);
        return prestige.apply(new PrestigeIntent(subject.actor(), playerId,
                "confirm-prestige-" + UUID.randomUUID())).thenApply(result -> {
                    Optional<PrestigePlan> plan = result.plan();
                    if (plan.isPresent() && plan.orElseThrow().executionAllowed()
                            && plan.orElseThrow().blockers().isEmpty()) {
                        return plan.orElseThrow();
                    }
                    List<String> blockers = plan.map(PrestigePlan::blockers).filter(values -> !values.isEmpty())
                            .orElseGet(() -> List.of(result.rejection().orElse("blocked")));
                    throw rejected("Prestige", blockers);
                });
    }

    public static OperationPreview rankUpPreview(RankUpPlan plan) {
        return new OperationPreview(OperationKind.RANK_UP, plan.playerId(), plan.executionAllowed(),
                plan.sourceStage().value() + " → " + plan.targetStage().value(),
                Optional.of(plan.requirements().explanation()),
                plan.costs().stream().map(cost -> cost.redactedPreview()).toList(),
                plan.rewards().stream().map(reward -> reward.redactedPreview()).toList(),
                plan.rankProjectionRequest().map(value -> List.of("External managed rank projection to "
                        + value.desiredGroup().orElse("none"))).orElse(List.of()),
                plan.blockers(), plan.configRevision(), plan.providerGenerations(), List.of());
    }

    public static OperationPreview prestigePreview(PrestigePlan plan) {
        var simulation = plan.simulation();
        List<String> consequences = java.util.stream.Stream.concat(
                simulation.componentConsequences().stream().map(Object::toString),
                simulation.currencyChanges().stream().map(Object::toString)).toList();
        return new OperationPreview(OperationKind.PRESTIGE, plan.playerId(), plan.executionAllowed(),
                simulation.sourceStage().value() + " → " + simulation.resetStage().value()
                        + "; Prestige " + simulation.currentPrestigeBefore() + " → "
                        + simulation.currentPrestigeAfter(),
                Optional.of(simulation.requirements().result().explanation()),
                plan.costs().stream().map(cost -> cost.redactedPreview()).toList(),
                plan.rewards().stream().map(reward -> reward.redactedPreview()).toList(), consequences,
                plan.blockers(), plan.configRevision(), plan.providerGenerations(),
                simulation.uncertainExternalEffects());
    }

    private static void requireSimulation(PermissionSubject subject, UUID playerId, String selfPermission) {
        if (subject.actor().uuid().filter(playerId::equals).isPresent()) {
            subject.require(selfPermission);
        } else {
            subject.require(PhaseSixPermissions.SIMULATE);
        }
    }

    private static void requireExecution(PermissionSubject subject, UUID playerId, String selfPermission) {
        if (subject.actor().uuid().filter(playerId::equals).isPresent()) {
            subject.require(selfPermission);
        } else {
            subject.require(PhaseSixPermissions.EXECUTE);
        }
    }

    private static AdministrationException rejected(String operation, List<String> blockers) {
        return new AdministrationException("operation.preview.blocked", operation + " is blocked: "
                + String.join("; ", blockers), "Use why to inspect canonical blockers and correct them first.");
    }
}
