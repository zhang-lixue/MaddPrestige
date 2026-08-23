package net.maddkraft.maddprestige.core.admin.diagnostic;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.plan.RankUpAuthorizationResult;
import net.maddkraft.maddprestige.core.plan.RankUpIntent;
import net.maddkraft.maddprestige.core.prestige.PrestigeAuthorizationResult;
import net.maddkraft.maddprestige.core.prestige.PrestigeIntent;

public final class WhyService {
    private final Function<RankUpIntent, CompletionStage<RankUpAuthorizationResult>> rankUp;
    private final Function<PrestigeIntent, CompletionStage<PrestigeAuthorizationResult>> prestige;

    public WhyService(
            Function<RankUpIntent, CompletionStage<RankUpAuthorizationResult>> rankUp,
            Function<PrestigeIntent, CompletionStage<PrestigeAuthorizationResult>> prestige) {
        this.rankUp = Objects.requireNonNull(rankUp, "rank-up authorization");
        this.prestige = Objects.requireNonNull(prestige, "Prestige authorization");
    }

    public CompletionStage<WhyReport> rankUp(PermissionSubject subject, UUID playerId) {
        requireView(subject, playerId);
        RankUpIntent intent = new RankUpIntent(subject.actor(), playerId, Optional.empty(),
                "why-rankup-" + UUID.randomUUID());
        return rankUp.apply(intent).thenApply(result -> result.plan().map(plan -> new WhyReport(
                plan.executionAllowed(), plan.blockers(), Optional.of(plan.requirements().explanation()),
                Optional.of(plan.configRevision()), plan.authorizationBlockers()))
                .orElseGet(() -> new WhyReport(false, result.blockers(), Optional.empty(), Optional.empty(),
                        result.authorizationBlockers())));
    }

    public CompletionStage<WhyReport> prestige(PermissionSubject subject, UUID playerId) {
        requireView(subject, playerId);
        PrestigeIntent intent = new PrestigeIntent(subject.actor(), playerId,
                "why-prestige-" + UUID.randomUUID());
        return prestige.apply(intent).thenApply(result -> result.plan().map(plan -> new WhyReport(
                plan.executionAllowed(), plan.blockers(),
                Optional.of(plan.simulation().requirements().result().explanation()),
                Optional.of(plan.configRevision()), plan.authorizationBlockers())).orElseGet(() -> new WhyReport(false,
                        net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker.diagnostics(
                                result.authorizationBlockers()),
                        Optional.empty(), Optional.empty(), result.authorizationBlockers())));
    }

    private static void requireView(PermissionSubject subject, UUID playerId) {
        if (subject.actor().uuid().filter(playerId::equals).isPresent()) {
            subject.require(PhaseSixPermissions.USE);
        } else {
            subject.require(PhaseSixPermissions.PLAYER_VIEW);
        }
    }
}
