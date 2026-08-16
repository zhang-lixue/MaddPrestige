# MaddPrestige V2 Phase 4 file manifest

**Baseline:** `64b568b7c8da6e9c750003bbcb4b6950aa58b1fe`
**Worktree state:** intentionally uncommitted
**Protected V1 roots:** `src`, `dist`, `baseline/v1/runtime`, `baseline/v1/external`
**Exact Phase 4 change surface:** 121 files — API 7, core production/config 69, core tests 9, persistence production 22, persistence tests 3, testkit support 3, testkit tests 2, documentation 6

This manifest lists every path that differs from the accepted baseline, including this manifest. Generated `target` artifacts and the owner-review archive are intentionally excluded from the Git change surface.

## Documentation (6)

- `DECISIONS.md`
- `STATUS.md`
- `docs/V2_ARCHITECTURE.md`
- `docs/V2_PHASE4_IMPLEMENTATION.md`
- `docs/V2_PHASE4_FILE_MANIFEST.md`
- `docs/V2_TRACEABILITY.md`

## API (7)

- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/cost/NativeRecoverableCostProvider.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/cost/PlannedCost.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/id/MilestoneId.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/id/SeasonId.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/reward/NativeRecoverableRewardProvider.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/reward/PlannedReward.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/value/ExactDecimal.java`

## Core production and configuration (69)

- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/competition/CompetitionConfiguration.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/competition/CompetitionFeatureBoundary.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase4/ActivePhaseFourConfiguration.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase4/PhaseFourConfiguration.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase4/PhaseFourConfigurationCompilation.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase4/PhaseFourConfigurationCompiler.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase4/PhaseFourConfigurationSnapshot.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase4/PhaseFourConfigurationValidator.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase4/PhaseFourProviderValidation.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase4/PhaseFourRequirementReachability.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase4/PrestigeConfiguration.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase4/PrestigeLimit.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase4/ResetComponent.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase4/ResetDisposition.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase4/ResetPreservePolicy.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/currency/CurrencyBalanceSource.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/currency/CurrencyDefinition.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/currency/CurrencyHistoryEntry.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/currency/CurrencyLedgerStore.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/currency/CurrencyMutation.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/currency/CurrencyMutationKind.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/currency/CurrencyMutationResult.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/currency/InternalCurrencyCostProvider.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/currency/InternalCurrencyRewardProvider.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/currency/InternalCurrencyService.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/entitlement/EffectiveEntitlement.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/entitlement/EntitlementContribution.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/entitlement/EntitlementDefinition.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/entitlement/EntitlementMergeEngine.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/entitlement/EntitlementMergeStrategy.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/entitlement/EntitlementValue.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/entitlement/EntitlementValueType.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/milestone/MilestoneDefinition.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/milestone/MilestoneRepeatability.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/milestone/MilestoneStateReader.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/milestone/MilestoneTriggerType.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpPlanner.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/ComponentConsequence.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/CurrencyConsequence.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/MilestoneConsequence.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PlayerPrestigeState.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PlayerPrestigeStateSource.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeActionPlanner.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeActionPreflight.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeAuthorization.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeAuthorizationResult.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeAuthorizationService.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeBaselineConsequence.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeBoundaryCollection.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeExecutionResult.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeExecutionStatus.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeIntent.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigePlan.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeProgressContext.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeProgressContextSource.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeSimulation.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/ProviderActionConsequence.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/ScopedRequirementStateConsequence.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/SeasonConsequence.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/BaselineInitializationService.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/schema/PhaseFourSchema.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/season/ActiveSeasonContext.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/season/ActiveSeasonSource.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/season/SeasonDefinition.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/season/SeasonLifecycleService.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/season/SeasonLifecycleState.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/season/SeasonRecord.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/season/SeasonStore.java`
- `maddprestige-core/src/main/resources/defaults/lifecycle.yml`

## Core tests (9)

- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/command/CommandActionValidatorTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/competition/CompetitionFeatureBoundaryTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/config/phase4/PhaseFourConfigurationCompilerTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/config/phase4/PhaseFourConfigurationValidatorTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/config/phase4/PhaseFourProviderReachabilityTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/entitlement/EntitlementMergeEngineTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/prestige/PrestigeAuthorizationServiceTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/prestige/PrestigeProviderHealthTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/schema/SchemaRegistryTest.java`

## Persistence production (22)

- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/OperationRepository.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/PlayerPrestigeRepository.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/PlayerStageRepository.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/PrestigeHistoryRecord.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/PrestigeLifecycleRepository.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/RecoveryEvent.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/RecoveryEventRepository.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/StoredPrestigeOperation.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/StoredOperationAction.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/StageHistoryRecord.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/plan/PrestigeOperationExecutor.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/plan/RepositoryStageTransitionCommitter.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/recovery/PendingOperationRecoveryService.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/recovery/RecoveryOutcome.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteCurrencyLedgerStore.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteMigrations.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteOperationRepository.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqlitePlayerPrestigeRepository.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqlitePlayerStageRepository.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqlitePrestigeLifecycleRepository.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteRecoveryEventRepository.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteSeasonStore.java`

## Persistence tests (3)

- `maddprestige-persistence/src/test/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteCurrencyLedgerStoreTest.java`
- `maddprestige-persistence/src/test/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteStageHistoryActorTest.java`
- `maddprestige-persistence/src/test/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteSeasonLifecycleTest.java`

## Testkit support and tests (5)

- `maddprestige-testkit/src/main/java/net/maddkraft/maddprestige/testkit/DisposableSqliteFixture.java`
- `maddprestige-testkit/src/main/java/net/maddkraft/maddprestige/testkit/FakeCostProvider.java`
- `maddprestige-testkit/src/main/java/net/maddkraft/maddprestige/testkit/FakeRewardProvider.java`
- `maddprestige-testkit/src/test/java/net/maddkraft/maddprestige/testkit/PhaseFourLifecycleTest.java`
- `maddprestige-testkit/src/test/java/net/maddkraft/maddprestige/testkit/RankUpEngineTest.java`

No POM/module declaration, integration module, Paper boundary, protected V1 file, distribution source or Phase 5 provider was added or modified.
