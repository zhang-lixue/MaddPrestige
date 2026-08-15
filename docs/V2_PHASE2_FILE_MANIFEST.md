# MaddPrestige V2 Phase 2 file manifest

**Baseline:** `5aab340554afcf7abe43fd36f6b835175550acc1`
**Worktree state:** intentionally uncommitted
**Protected V1 roots:** `src`, `dist`, `baseline/v1/runtime`, `baseline/v1/external` (unchanged)

## Build and dependency metadata

- `pom.xml` — manages the LuckPerms API version.
- `maddprestige-integrations/pom.xml` — adds the provided LuckPerms API contract dependency.
- `maddprestige-distribution/pom.xml` — consumes the managed LuckPerms version for the frozen distribution dependency declaration.

## API module

- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/rank/AmbiguousRankMembership.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/rank/ManagedRankState.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/rank/RankAdapter.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/rank/RankProjectionOutcome.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/rank/RankProjectionRequest.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/rank/RankProjectionResult.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/validation/ValidationReport.java` — combines structural, provider, and impact reports.

## Core stage/configuration module

- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/schema/PhaseTwoSchema.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/PlayerStageState.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageChangeImpact.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageChangeImpactAnalyzer.java` — complete remap intent remains blocked until stored references are actually remapped.
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageConfiguration.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageConfigurationCandidate.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageConfigurationCompilation.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageConfigurationCompiler.java` — accepts only documented projection forms and rejects invalid explicit types.
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageConfigurationSnapshot.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageConfigurationValidator.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageConfigurationWorkflow.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageDefinition.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageProjection.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageProjectionChange.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageRemapPlan.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageRuntimeBootstrap.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageRuntimeState.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageRuntimeStatus.java`
- `maddprestige-core/src/main/resources/defaults/progression.yml` — inactive, empty, warn-only safe default.

## Core rank/reconciliation module

- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/provider/ProviderRegistry.java` — exposes the exact registered instance for binding verification.
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/rank/RankOperationExecution.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/rank/RankOperationExecutionStatus.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/rank/RankProjectionOperation.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/rank/RankProjectionOperationPlanner.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/rank/RankReconciler.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/rank/RankReconciliationDecision.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/rank/RankReconciliationResult.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/rank/ReconciliationAction.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/rank/ReconciliationRateGate.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/rank/ReconciliationStatus.java`

## Core legacy-planning module

- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/legacy/LegacyPlayerStageRecord.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/legacy/LegacyStageDetection.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/legacy/LegacyStageDetector.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/legacy/LegacyStageMappingEntry.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/legacy/LegacyStageMappingManifest.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/legacy/LegacyStageMigrationPlan.java` — enforces that an erroneous plan cannot expose player mappings.
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/legacy/LegacyStageMigrationPlanner.java` — excludes conflicting/invalid entries and empties non-proceedable plans.

## Persistence module

- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/OperationRepository.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/PersistenceException.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/PlayerStageRepository.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/StalePlayerStageStateException.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/StoredOperationAction.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/rank/RankProjectionOperationExecutor.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/rank/RankReconciliationCoordinator.java` — rechecks configuration/provider binding immediately before import-once insertion.
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteMigrations.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteOperationRepository.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqlitePlayerStageRepository.java`

## LuckPerms integration module

- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/luckperms/LuckPermsRankAdapter.java` — preserves uncertainty after a successful save if verification/finalization fails.

## Test support and tests

- `maddprestige-testkit/src/main/java/net/maddkraft/maddprestige/testkit/DisposableSqliteFixture.java` — migrates disposable fixtures through Phase 2.
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/legacy/LegacyStageMigrationPlannerTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/rank/RankReconcilerTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/stage/StageChangeImpactAnalyzerTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/stage/StageConfigurationCompilerTest.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/stage/StageConfigurationWorkflowTest.java`
- `maddprestige-integrations/src/test/java/net/maddkraft/maddprestige/integrations/luckperms/LuckPermsRankAdapterTest.java`
- `maddprestige-persistence/src/test/java/net/maddkraft/maddprestige/persistence/rank/RankProjectionOperationExecutorTest.java`
- `maddprestige-persistence/src/test/java/net/maddkraft/maddprestige/persistence/sqlite/SqlitePlayerStageRepositoryTest.java`

## Architecture, decisions, status, and traceability

- `docs/V2_ARCHITECTURE.md`
- `docs/V2_PHASE2_IMPLEMENTATION.md`
- `docs/V2_PHASE2_FILE_MANIFEST.md`
- `docs/V2_TRACEABILITY.md`
- `DECISIONS.md`
- `STATUS.md`

This manifest is exhaustive relative to the final Phase 2 worktree diff. Generated Maven `target` output is intentionally excluded.

The owner-review correction pass modified only files already listed in this Phase 2 manifest; it added no new source path or Phase 3 component.
