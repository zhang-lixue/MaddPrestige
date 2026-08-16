# MaddPrestige V2 Phase 3 file manifest

**Baseline:** accepted Phase 2 merge `ca13fa4`
**Worktree state:** intentionally uncommitted
**Protected V1 roots:** `src`, `dist`, `baseline/v1/runtime`, `baseline/v1/external` (unchanged)
**Exact change surface:** 151 files — API 31, core 99, persistence 6, Paper boundary 4, testkit 5, documentation/status/decisions 6

## Documentation, decisions, and status (6)

- `DECISIONS.md`
- `STATUS.md`
- `docs/V2_ARCHITECTURE.md`
- `docs/V2_PHASE3_IMPLEMENTATION.md`
- `docs/V2_PHASE3_FILE_MANIFEST.md`
- `docs/V2_TRACEABILITY.md`

## API module (31)

- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/explanation/ExplanationStatus.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/action/ActionCharacteristics.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/action/ActionExecutionResult.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/action/ActionExecutionStatus.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/action/PreflightStatus.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/cost/CostDefinition.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/cost/CostPreflight.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/cost/CostProvider.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/cost/PlannedCost.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/id/CostId.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/id/RequirementId.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/id/RewardId.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricDescriptor.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricDimension.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricMonotonicity.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricOperator.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricProvider.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricQuery.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricReadMode.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricResetPolicy.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricSample.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricSampleStatus.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricValue.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricValueType.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/reward/PlannedReward.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/reward/RewardDefinition.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/reward/RewardFailurePolicy.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/reward/RewardPreflight.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/reward/RewardProvider.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/reward/RewardRepeatability.java`
- `maddprestige-api/src/test/java/net/maddkraft/maddprestige/api/metric/MetricValueTest.java`

## Core module (99)

Existing stage/operation/default files:

- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/operation/OperationStateMachine.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/ActiveStageConfiguration.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/PlayerStageState.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageConfigurationCandidate.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageConfigurationCompiler.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageConfigurationValidator.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageConfigurationWorkflow.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageDefinition.java`
- `maddprestige-core/src/main/resources/defaults/progression.yml`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/stage/StageConfigurationCompilerTest.java`

Command package:

- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/command/CommandActionExecutor.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/command/CommandActionPlan.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/command/CommandActionPolicy.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/command/CommandActionRequest.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/command/CommandActionValidation.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/command/CommandActionValidator.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/command/CommandDispatchResult.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/command/CommandDispatcher.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/command/CommandExecutionContext.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/command/CommandRewardProvider.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/command/CommandTemplate.java`

Phase 3 configuration package:

- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase3/PhaseThreeConfiguration.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase3/PhaseThreeConfigurationCompilation.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase3/PhaseThreeConfigurationCompiler.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase3/PhaseThreeConfigurationSnapshot.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase3/PhaseThreeConfigurationValidator.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase3/PhaseThreeProviderValidation.java`

Manual progress package:

- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/manual/ManualCounterDefinition.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/manual/ManualMetricHandle.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/manual/ManualMetricRegistration.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/manual/ManualProgressBootstrap.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/manual/ManualProgressOwner.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/manual/ManualProgressProvider.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/manual/ManualProgressRecord.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/manual/ManualProgressRepository.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/manual/ProgressProvenance.java`

Rank-up plan package:

- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/PlayerStageStateSource.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpAuthorization.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpAuthorizationResult.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpAuthorizationService.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpExecutionResult.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpExecutionStatus.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpIntent.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpPlan.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpPlanner.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpPlanningRequest.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpProgressContext.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpProgressContextSource.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpSimulationService.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/StageTransitionCommitter.java`

Requirement package:

- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/BaselineInitializationService.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/BaselineKey.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/BoundRequirementEvaluation.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/CatchUpProfile.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/CompletionMode.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/EffectiveTarget.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/LatchKey.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/MeasurementScope.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/MetricBinding.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementBaseline.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementChild.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementCompletionTransitionService.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementDefinition.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementEvaluationAuthorizer.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementEvaluationBinding.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementEvaluationContext.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementEvaluationResult.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementEvaluationStatus.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementEvaluator.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementGroup.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementGroupMode.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementLatch.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementLeaf.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementMetricCollector.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementNode.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementSemantics.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementStateReader.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementStateWriter.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementTarget.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementTreeSemantics.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementTreeValidator.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/ScalingProfile.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/ScalingStrategy.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/ScopeContext.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/TargetRounding.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/TargetTransformer.java`

Schema/defaults/tests:

- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/schema/PhaseThreeSchema.java`
- `maddprestige-core/src/main/resources/defaults/requirements.yml`
- `maddprestige-core/src/main/resources/defaults/rewards.yml`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/command/CommandActionValidatorTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/config/phase3/PhaseThreeConfigurationCompilerTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/config/phase3/PhaseThreeConfigurationValidatorTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/manual/ManualProgressProviderTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/requirement/RequirementEngineTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/requirement/RequirementMetricCollectorTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/requirement/RequirementMetricMatrixTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/requirement/RequirementSemanticsTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/requirement/ScalingCatchUpTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/stage/StageConfigurationWorkflowTest.java`

## Persistence module (6)

- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/plan/RankUpOperationExecutor.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/plan/RepositoryStageTransitionCommitter.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteManualProgressRepository.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteMigrations.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteRequirementStateRepository.java`
- `maddprestige-persistence/src/test/java/net/maddkraft/maddprestige/persistence/sqlite/SqlitePhaseThreeStateTest.java`

## Paper boundary (4)

- `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/PaperStatisticDimensionCatalog.java`
- `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/StatisticDimensionCatalog.java`
- `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/VanillaStatisticsProvider.java`
- `maddprestige-platform-paper/src/test/java/net/maddkraft/maddprestige/platform/paper/VanillaStatisticsProviderTest.java`

## Testkit (5)

- `maddprestige-testkit/src/main/java/net/maddkraft/maddprestige/testkit/DisposableSqliteFixture.java`
- `maddprestige-testkit/src/main/java/net/maddkraft/maddprestige/testkit/FakeCostProvider.java`
- `maddprestige-testkit/src/main/java/net/maddkraft/maddprestige/testkit/FakeProgressionProvider.java`
- `maddprestige-testkit/src/main/java/net/maddkraft/maddprestige/testkit/FakeRewardProvider.java`
- `maddprestige-testkit/src/test/java/net/maddkraft/maddprestige/testkit/RankUpEngineTest.java`

No build-module declaration or dependency POM changed in Phase 3. No protected V1 file changed. No file outside the seven accepted modules and governing documentation was added or modified.
