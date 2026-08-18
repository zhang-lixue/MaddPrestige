# Phase 8B exact changed/new file manifest

- Branch: `v2/phase-8`
- Baseline/current HEAD: `7fc5c3532f0614634933ff66ee0e03bf55b8e5cf`
- Tracked changed paths: **48**
- Untracked paths: **86**
- Total changed/new paths: **134**
- Staged paths: **0**

## Tracked changes

```text
M	DECISIONS.md
M	docs/V2_ARCHITECTURE.md
M	docs/V2_TRACEABILITY.md
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/id/ConfigRevisionId.java
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/id/MetricId.java
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/id/OperationId.java
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/id/ProviderId.java
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/id/StageId.java
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/id/StringIdentifier.java
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricMonotonicity.java
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricOperator.java
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricProvider.java
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricReadMode.java
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricResetPolicy.java
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricValue.java
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricValueType.java
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/Provider.java
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/ProviderDescriptor.java
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/ProviderHealthState.java
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/ProviderSnapshot.java
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/rank/RankAdapter.java
M	maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/result/StructuredError.java
M	maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/config/ConfigurationAdministrationService.java
M	maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/manual/ManualProgressProvider.java
M	maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpAuthorizationService.java
M	maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpIntent.java
M	maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpPlan.java
M	maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpPlanner.java
M	maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpPlanningRequest.java
M	maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeAuthorizationService.java
M	maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeIntent.java
M	maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigePlan.java
M	maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/provider/ProviderRegistry.java
M	maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementMetricCollector.java
M	maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/manual/ManualProgressProviderTest.java
M	maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/provider/ProviderRegistryTest.java
M	maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/admin/AtomicConfigurationFileStore.java
M	maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/plan/PrestigeOperationExecutor.java
M	maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/plan/RankUpOperationExecutor.java
M	maddprestige-persistence/src/test/java/net/maddkraft/maddprestige/persistence/admin/AtomicConfigurationFileStoreTest.java
M	maddprestige-platform-paper/pom.xml
M	maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/bootstrap/MaddPrestigeV2Plugin.java
M	maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/integration/PhaseSevenOptionalIntegrationManager.java
M	maddprestige-platform-paper/src/test/java/net/maddkraft/maddprestige/platform/paper/integration/PhaseSevenOptionalCompositionTest.java
M	maddprestige-testkit/src/test/java/net/maddkraft/maddprestige/testkit/PhaseFourLifecycleTest.java
M	maddprestige-testkit/src/test/java/net/maddkraft/maddprestige/testkit/RankUpEngineTest.java
M	src/main/resources/plugin.yml
M	STATUS.md
```

## Untracked additions

```text
docs/V2_PHASE8_FAILURE_REGISTER.md
docs/V2_PHASE8B_API_INVENTORY.md
docs/V2_PHASE8B_FILE_MANIFEST.md
docs/V2_PHASE8B_IMPLEMENTATION.md
docs/V2_PHASE8B_PAPER_API_INVENTORY.md
docs/V2_PHASE8B_STATIC_AUDIT.md
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/annotation/Experimental.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/annotation/Stable.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/event/ConfigAppliedSnapshot.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/event/OperationEventSnapshot.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/event/package-info.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/event/ProviderHealthChangedSnapshot.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/id/package-info.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/package-info.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/package-info.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/ProviderCallContext.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/ProviderCancellationSignal.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/ProviderDeclaration.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/ProviderExecutionExpectation.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/ProviderMetadata.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/ProviderMetricDefinition.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/ProviderMetricDimension.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/ProviderMetricRequest.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/ProviderMetricResult.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/ProviderMetricStatus.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/ProviderRegistrationHandle.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/RequirementProvider.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/service/CurrencyBalanceView.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/service/MaddPrestigeService.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/service/OperationEvaluation.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/service/OperationEvaluationStatus.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/service/OperationKind.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/service/OperationResult.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/service/OperationSimulationView.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/service/OperationStatus.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/service/package-info.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/service/PlayerProgressSnapshot.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/service/ProviderView.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/service/RequirementProgressStatus.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/service/RequirementProgressView.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/service/SeasonView.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/service/ServiceError.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/service/ServiceResult.java
maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/service/StageView.java
maddprestige-api/src/test/java/net/maddkraft/maddprestige/api/service/StableApiLeakageTest.java
maddprestige-api/src/test/java/net/maddkraft/maddprestige/api/service/StableServiceContractTest.java
maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/event/OperationLifecycleListener.java
maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/provider/ProviderHealthListener.java
maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/provider/ProviderLifecycleListener.java
maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqlitePlayerInitializationStore.java
maddprestige-persistence/src/test/java/net/maddkraft/maddprestige/persistence/sqlite/SqlitePlayerInitializationStoreTest.java
maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/bootstrap/AuthoritativeProgressContextFactory.java
maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/bootstrap/FailureLogThrottle.java
maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/bootstrap/ProductionRuntime.java
maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/bootstrap/RuntimeConfigurationReconciler.java
maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/bootstrap/StartupConfigurationLoader.java
maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/event/ConfigAppliedEvent.java
maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/event/PaperOperationLifecycle.java
maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/event/PostPrestigeEvent.java
maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/event/PostRankUpEvent.java
maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/event/PrePrestigeEvent.java
maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/event/PreRankUpEvent.java
maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/event/ProviderHealthChangedEvent.java
maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/placeholder/PlaceholderSnapshotPublisher.java
maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/provider/PaperProviderBridge.java
maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/service/ProductionMaddPrestigeService.java
maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/service/ServiceResults.java
maddprestige-platform-paper/src/test/java/net/maddkraft/maddprestige/platform/paper/bootstrap/AuthoritativeProgressContextFactoryTest.java
maddprestige-platform-paper/src/test/java/net/maddkraft/maddprestige/platform/paper/bootstrap/FailureLogThrottleTest.java
maddprestige-platform-paper/src/test/java/net/maddkraft/maddprestige/platform/paper/bootstrap/StartupConfigurationLoaderTest.java
maddprestige-platform-paper/src/test/java/net/maddkraft/maddprestige/platform/paper/event/PaperOperationLifecycleTest.java
maddprestige-platform-paper/src/test/java/net/maddkraft/maddprestige/platform/paper/event/StablePaperEventSurfaceTest.java
maddprestige-platform-paper/src/test/java/net/maddkraft/maddprestige/platform/paper/provider/PaperProviderBridgeTest.java
maddprestige-platform-paper/src/test/java/net/maddkraft/maddprestige/platform/paper/service/ProductionMaddPrestigeServiceTest.java
PHASE8B_OWNER_REVIEW_SUMMARY.txt
PHASE8B_PAPER_QUALIFICATION.log
PHASE8B_VERIFY_1.log
PHASE8B_VERIFY_2.log
qualification/eula.txt
qualification/paper-server.properties
qualification/phase8b-paper-harness/pom.xml
qualification/phase8b-paper-harness/src/main/java/net/maddkraft/qualification/phase8b/Phase8BQualificationHarness.java
qualification/phase8b-paper-harness/src/main/resources/plugin.yml
qualification/README.md
tools/Generate-Phase8BApiInventory.ps1
tools/Generate-Phase8BManifest.ps1
```

Generated mechanically by `tools/Generate-Phase8BManifest.ps1`; run after all review evidence is created.
