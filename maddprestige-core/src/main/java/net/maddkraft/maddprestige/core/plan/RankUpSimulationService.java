package net.maddkraft.maddprestige.core.plan;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Simulation reuses canonical authorization and stops before journal or mutation. */
public final class RankUpSimulationService {
    private final RankUpAuthorizationService authorization;

    public RankUpSimulationService(RankUpAuthorizationService authorization) {
        this.authorization = Objects.requireNonNull(authorization, "authorization service");
    }

    public CompletionStage<RankUpAuthorizationResult> simulate(RankUpIntent intent) {
        return authorization.authorize(intent);
    }
}
