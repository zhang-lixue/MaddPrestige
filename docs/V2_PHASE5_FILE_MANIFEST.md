# MaddPrestige V2 Phase 5 file manifest

**Baseline:** `38cde9a084712a3f8c06edb0e77dc31333204374`

**Manifest scope:** 47 changed/new third-owner-review paths plus 22 unchanged provider/core contracts required to review the integration safety boundary.

## Build and dependency declarations (4)

- `pom.xml`
- `maddprestige-integrations/pom.xml`
- `maddprestige-platform-paper/pom.xml`
- `maddprestige-distribution/pom.xml`

## Integration production and configuration (24)

- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/IntegrationProviderLifecycle.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/IntegrationTaskScheduler.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/MutableProviderHealth.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/ProviderRegistrationGate.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/config/PhaseFiveIntegrationCompilation.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/config/PhaseFiveIntegrationCompiler.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/config/PhaseFiveIntegrationConfiguration.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/config/PhaseFiveIntegrationPlan.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/config/PhaseFiveIntegrationSchema.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/economyshopgui/EconomyShopGuiCompatibilityListener.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/mcmmo/McMmoAdjustedXpListener.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/mcmmo/McMmoExperienceAccess.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/mcmmo/McMmoMetricProvider.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/mcmmo/OfficialMcMmoExperienceAccess.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/placeholder/OfficialPlaceholderResolver.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/placeholder/PlaceholderInputMetricProvider.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/placeholder/PlaceholderResolver.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/quickshop/QuickShopCompatibilityListener.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/vault/VaultBalanceMetricProvider.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/vault/VaultEconomyBinding.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/vault/VaultEconomyCostProvider.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/vault/VaultEconomyRewardProvider.java`
- `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/vault/VaultProviderDescriptors.java`
- `maddprestige-integrations/src/main/resources/integrations.yml`

## Paper PlaceholderAPI output boundary (3)

- `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/placeholder/MaddPrestigePlaceholderCache.java`
- `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/placeholder/MaddPrestigePlaceholderExpansion.java`
- `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/placeholder/MaddPrestigePlaceholderSnapshot.java`

## New Phase 5 tests (7)

- `maddprestige-integrations/src/test/java/net/maddkraft/maddprestige/integrations/McMmoAndPlaceholderProviderTest.java`
- `maddprestige-integrations/src/test/java/net/maddkraft/maddprestige/integrations/PhaseFiveConfigurationAndLifecycleTest.java`
- `maddprestige-integrations/src/test/java/net/maddkraft/maddprestige/integrations/PhaseFiveCorrectionPassTest.java`
- `maddprestige-integrations/src/test/java/net/maddkraft/maddprestige/integrations/PhaseFiveOutageHardeningTest.java`
- `maddprestige-integrations/src/test/java/net/maddkraft/maddprestige/integrations/ShopAndMcMmoEventContractTest.java`
- `maddprestige-integrations/src/test/java/net/maddkraft/maddprestige/integrations/VaultEconomyProviderTest.java`
- `maddprestige-platform-paper/src/test/java/net/maddkraft/maddprestige/platform/paper/placeholder/MaddPrestigePlaceholderExpansionTest.java`

## Documentation and owner-review records (9)

- `DECISIONS.md`
- `STATUS.md`
- `docs/V2_ARCHITECTURE.md`
- `docs/V2_TRACEABILITY.md`
- `docs/V2_PHASE5_IMPLEMENTATION.md`
- `docs/V2_PHASE5_FILE_MANIFEST.md`
- `PHASE5_OWNER_REVIEW_SUMMARY.txt`
- `PHASE5_SECOND_OWNER_REVIEW_SUMMARY.txt`
- `PHASE5_THIRD_OWNER_REVIEW_SUMMARY.txt`

## Relevant unchanged contracts included in the review bundle (22)

- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/action/ActionCharacteristics.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/action/ActionExecutionResult.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/cost/CostProvider.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/cost/PlannedCost.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricDescriptor.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricMonotonicity.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricProvider.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricResetPolicy.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/metric/MetricSample.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/ActivationState.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/Provider.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/ProviderHealth.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/ProviderHealthState.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/provider/ProviderSnapshot.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/reward/PlannedReward.java`
- `maddprestige-api/src/main/java/net/maddkraft/maddprestige/api/reward/RewardProvider.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/manual/ManualMetricHandle.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/manual/ManualProgressProvider.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/provider/ProviderRegistration.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/provider/ProviderRegistry.java`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementMetricCollector.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/plan/PrestigeOperationExecutor.java`

## Generated verification evidence included in the review bundle

- `target/bom.json` (aggregate CycloneDX 1.6 SBOM)
- `maddprestige-distribution/target/MaddPrestige-2.0.0-SNAPSHOT.jar` (verified shaded distribution)
- module `target/surefire-reports/TEST-*.xml` files
- module `target/site/jacoco/jacoco.xml` files
- root/module `target/checkstyle-result.xml` files

The manifest deliberately excludes generated compiler classes, transient Maven state and unrelated baseline files. The bundle path inventory is verified against this manifest before handoff. Protected V1 paths are absent from the changed-path sections.
