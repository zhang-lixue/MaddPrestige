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
     * Returns the immutable in-memory stage catalog synchronously without I/O or mutation.
     *
     * @return non-null structured result in canonical stage order
     */
    ServiceResult<List<StageView>> stages();

    /**
     * Evaluates canonical rank-up authorization without PRE events, journaling, costs, rewards, or state writes.
     *
     * @param playerId non-null player identity
     * @return non-null asynchronous structured evaluation result
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
     * Requests one authorized rank-up through the canonical operation engine.
     *
     * @param playerId non-null player identity
     * @return non-null asynchronous structured terminal result; its request ID exists even when PRE blocks durability
     */
    CompletionStage<ServiceResult<OperationResult>> rankUp(UUID playerId);

    /**
     * Requests one authorized Prestige through the canonical operation engine.
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
