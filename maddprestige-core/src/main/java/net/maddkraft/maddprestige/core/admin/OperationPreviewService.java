package net.maddkraft.maddprestige.core.admin;

import java.util.List;
import java.util.ArrayList;
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
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker;

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
                        .orElseThrow(() -> rejected("rank-up", result.authorizationBlockers())));
    }

    public CompletionStage<OperationPreview> simulatePrestige(PermissionSubject subject, UUID playerId) {
        requireSimulation(subject, playerId, PhaseSixPermissions.PRESTIGE);
        return prestige.apply(new PrestigeIntent(subject.actor(), playerId,
                "simulate-prestige-" + UUID.randomUUID())).thenApply(result -> result.plan()
                        .map(OperationPreviewService::prestigePreview)
                        .orElseThrow(() -> rejected("Prestige", result.authorizationBlockers())));
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
                    List<AuthorizationBlocker> blockers = plan.map(RankUpPlan::authorizationBlockers)
                            .filter(values -> !values.isEmpty()).orElse(result.authorizationBlockers());
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
                    List<AuthorizationBlocker> blockers = plan.map(PrestigePlan::authorizationBlockers)
                            .filter(values -> !values.isEmpty()).orElse(result.authorizationBlockers());
                    throw rejected("Prestige", blockers);
                });
    }

    public static OperationPreview rankUpPreview(RankUpPlan plan) {
        ArrayList<MessageReference> details = new ArrayList<>();
        details.add(m("command.preview.state_change", "current_stage", plan.sourceStage().value(),
                "target_stage", plan.targetStage().value()));
        plan.costs().forEach(cost -> details.add(m("command.preview.cost", "id", cost.definition().id().value(),
                "provider", cost.definition().providerId().value(), "type", cost.definition().type(),
                "amount", cost.definition().amount(), "value", cost.definition().displayName())));
        plan.rewards().forEach(reward -> details.add(m("command.preview.reward", "id",
                reward.definition().id().value(), "provider", reward.definition().providerId().value(),
                "type", reward.definition().type(), "amount", reward.definition().value(),
                "value", reward.definition().displayName())));
        plan.rankProjectionRequest().ifPresent(projection -> details.add(m("command.preview.rank_projection",
                "rank", projection.desiredGroup().orElse("NONE"))));
        return new OperationPreview(OperationKind.RANK_UP, plan.playerId(), plan.executionAllowed(),
                plan.sourceStage().value() + " → " + plan.targetStage().value(),
                Optional.of(plan.requirements().explanation()),
                plan.costs().stream().map(cost -> cost.redactedPreview()).toList(),
                plan.rewards().stream().map(reward -> reward.redactedPreview()).toList(),
                plan.rankProjectionRequest().map(value -> List.of("External managed rank projection to "
                        + value.desiredGroup().orElse("none"))).orElse(List.of()),
                plan.blockers(), plan.configRevision(), plan.providerGenerations(), List.of(), details,
                plan.authorizationBlockers());
    }

    public static OperationPreview prestigePreview(PrestigePlan plan) {
        var simulation = plan.simulation();
        ArrayList<MessageReference> details = new ArrayList<>();
        details.add(m("command.preview.prestige_state_change", "current_stage", simulation.sourceStage().value(),
                "target_stage", simulation.resetStage().value(), "current_prestige",
                simulation.currentPrestigeBefore(), "target_prestige", simulation.currentPrestigeAfter(),
                "current_lifetime", simulation.lifetimePrestigeBefore(), "target_lifetime",
                simulation.lifetimePrestigeAfter()));
        plan.costs().forEach(cost -> details.add(m("command.preview.cost", "id", cost.definition().id().value(),
                "provider", cost.definition().providerId().value(), "type", cost.definition().type(),
                "amount", cost.definition().amount(), "value", cost.definition().displayName())));
        plan.rewards().forEach(reward -> details.add(m("command.preview.reward", "id",
                reward.definition().id().value(), "provider", reward.definition().providerId().value(),
                "type", reward.definition().type(), "amount", reward.definition().value(),
                "value", reward.definition().displayName())));
        simulation.componentConsequences().forEach(consequence -> details.add(m(
                "command.preview.component_consequence", "component", consequence.component(),
                "disposition", consequence.disposition())));
        simulation.currencyChanges().forEach(change -> details.add(m("command.preview.currency_change",
                "currency", change.currencyId().value(), "before", change.before(), "delta", change.delta(),
                "after", change.after())));
        simulation.providerActions().stream().filter(value -> value.uncertaintyPossible()).forEach(action ->
                details.add(m("command.preview.external_uncertainty", "operation", action.actionId(),
                        "provider", action.providerId().value())));
        plan.rankProjectionRequest().ifPresent(projection -> details.add(m("command.preview.rank_projection",
                "rank", projection.desiredGroup().orElse("NONE"))));
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
                simulation.uncertainExternalEffects(), details, plan.authorizationBlockers());
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

    private static AdministrationException rejected(String operation, List<AuthorizationBlocker> blockers) {
        return AdministrationException.authorizationRejected(operation, blockers);
    }

    private static MessageReference m(String key, Object... arguments) {
        return MessageReference.of(key, arguments);
    }
}
