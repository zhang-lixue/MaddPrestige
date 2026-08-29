package net.maddkraft.maddprestige.api.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Primary V2 service published through the Paper service manager after recovery is complete.
 * All potentially-I/O methods return immediately with a caller-owned completion stage. Operational failures are
 * represented by {@link ServiceResult}; only null/invalid programmer arguments fail fast. Returned collections and
 * values are immutable snapshots. Mutation calls complete after their terminal outcome, which may precede durable
 * journal creation when authorization or PRE delivery blocks the request.
 */
public interface MaddPrestigeService {
    /**
     * Returns a materialized player snapshot without blocking or mutating player state.
     *
     * @param playerId non-null player identity
     * @return non-null asynchronous structured result containing an immutable snapshot
     */
    CompletionStage<ServiceResult<PlayerProgressSnapshot>> playerProgress(UUID playerId);

    /**
     * Returns the retired stage catalog compatibility view. Numeric Prestige has no active stage catalog, so the
     * production implementation returns an empty list. This method performs no I/O or mutation.
     *
     * @return non-null structured compatibility result; empty for the active numeric model
     * @deprecated stage catalogs are not part of active V2 numeric Prestige progression
     */
    ServiceResult<List<StageView>> stages();

    /**
     * Evaluates the retained rank-up compatibility operation. Production always returns a blocked evaluation without
     * PRE events, journaling, costs, rewards, stage changes, projection, or any other state write.
     *
     * @param playerId non-null player identity
     * @return non-null asynchronous blocked compatibility result
     * @deprecated use {@link #evaluatePrestige(UUID)} for active numeric Prestige progression
     */
    CompletionStage<ServiceResult<OperationEvaluation>> evaluateRankUp(UUID playerId);

    /**
     * Evaluates canonical Prestige authorization without PRE events, journaling, costs, rewards, or state writes.
     *
     * @param playerId non-null player identity
     * @return non-null asynchronous structured evaluation result
     */
    CompletionStage<ServiceResult<OperationEvaluation>> evaluatePrestige(UUID playerId);

    /**
     * Returns requirement-leaf progress reachable from both current side-effect-free operation evaluations.
     *
     * @param playerId non-null player identity
     * @return non-null asynchronous structured result in deterministic operation and requirement order
     */
    CompletionStage<ServiceResult<List<RequirementProgressView>>> requirementProgress(UUID playerId);

    /**
     * Returns exact configured internal-currency balances without mutation.
     *
     * @param playerId non-null player identity
     * @return non-null asynchronous structured result in canonical currency order
     */
    CompletionStage<ServiceResult<List<CurrencyBalanceView>>> currencies(UUID playerId);

    /**
     * Returns the active season and queried player's progress, or empty when no season is active.
     *
     * @param playerId non-null player identity
     * @return non-null asynchronous structured result whose value may contain no active season
     */
    CompletionStage<ServiceResult<Optional<SeasonView>>> activeSeason(UUID playerId);

    /**
     * Requests the retained rank-up compatibility operation. Production always returns a blocked terminal result and
     * never invokes confirmation, stage mutation, provider projection, or the numeric Prestige operation engine.
     *
     * @param playerId non-null player identity
     * @return non-null asynchronous blocked compatibility result with a request ID
     * @deprecated use {@link #prestige(UUID)} for active numeric Prestige progression
     */
    CompletionStage<ServiceResult<OperationResult>> rankUp(UUID playerId);

    /**
     * Requests the active numeric transition from Prestige {@code N} to {@code N + 1} through the canonical operation
     * engine.
     *
     * @param playerId non-null player identity
     * @return non-null asynchronous structured terminal result; its request ID exists even when PRE blocks durability
     */
    CompletionStage<ServiceResult<OperationResult>> prestige(UUID playerId);

    /**
     * Returns a synchronous immutable snapshot of already materialized provider state.
     *
     * @return non-null structured result in canonical provider-ID order
     */
    ServiceResult<List<ProviderView>> providers();
}
