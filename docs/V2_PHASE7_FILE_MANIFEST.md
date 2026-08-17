# Phase 7 exact changed/new Git manifest

Comparison baseline: `36f8723f17209e8b18a180cbcf940a4770d5a265`.

Declared inventory: **62 paths — 15 modified tracked paths and 47 new unstaged paths**. Actual `git status --porcelain=v1 --untracked-files=all` inventory: 62. Missing: 0. Extra: 0. Staged: 0.

## Modified tracked paths (15)

| Status | Path |
|---|---|
| M | `DECISIONS.md` |
| M | `STATUS.md` |
| M | `docs/V2_ARCHITECTURE.md` |
| M | `docs/V2_TRACEABILITY.md` |
| M | `maddprestige-distribution/pom.xml` |
| M | `maddprestige-integrations/pom.xml` |
| M | `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/config/PhaseFiveIntegrationCompiler.java` |
| M | `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/config/PhaseFiveIntegrationConfiguration.java` |
| M | `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/config/PhaseFiveIntegrationPlan.java` |
| M | `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/config/PhaseFiveIntegrationSchema.java` |
| M | `maddprestige-integrations/src/main/resources/integrations.yml` |
| M | `maddprestige-integrations/src/test/java/net/maddkraft/maddprestige/integrations/luckperms/LuckPermsRankAdapterTest.java` |
| M | `maddprestige-platform-paper/pom.xml` |
| M | `pom.xml` |
| M | `src/main/resources/plugin.yml` |

## New unstaged paths (47)

| Status | Path |
|---|---|
| A | `PHASE7_OWNER_REVIEW_SUMMARY.txt` |
| A | `docs/V2_PHASE7_ACCEPTANCE_PROOF.md` |
| A | `docs/V2_PHASE7_ARTIFACT_AUDIT.md` |
| A | `docs/V2_PHASE7_FAILURE_REGISTER.md` |
| A | `docs/V2_PHASE7_FILE_MANIFEST.md` |
| A | `docs/V2_PHASE7_IMPLEMENTATION.md` |
| A | `docs/V2_PHASE7_STATIC_AUDIT.md` |
| A | `docs/evidence/phase7/A73_CE_ABSENT_SANITIZED.log` |
| A | `docs/evidence/phase7/A73_FULL_STACK_SANITIZED.log` |
| A | `docs/evidence/phase7/A73_GP_ABSENT_SANITIZED.log` |
| A | `docs/evidence/phase7/A73_PAPER_ONLY_SANITIZED.log` |
| A | `docs/evidence/phase7/A73_PLUGIN_METADATA_AND_DISPOSITION.md` |
| A | `docs/evidence/phase7/A73_WE_ABSENT_SANITIZED.log` |
| A | `docs/evidence/phase7/A73_WG_ABSENT_SANITIZED.log` |
| A | `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/craftengine/CraftEngineItemAccess.java` |
| A | `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/craftengine/CraftEngineItemMetricProvider.java` |
| A | `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/craftengine/CraftEngineItemRewardProvider.java` |
| A | `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/craftengine/CraftEngineProviderDescriptors.java` |
| A | `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/craftengine/OfficialCraftEngineItemAccess.java` |
| A | `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/griefprevention/GriefPreventionAccess.java` |
| A | `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/griefprevention/GriefPreventionClaimBlockRewardProvider.java` |
| A | `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/griefprevention/GriefPreventionMetricProvider.java` |
| A | `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/griefprevention/GriefPreventionProviderDescriptors.java` |
| A | `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/griefprevention/OfficialGriefPreventionAccess.java` |
| A | `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/worldguard/OfficialWorldGuardRegionAccess.java` |
| A | `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/worldguard/WorldGuardRegionAccess.java` |
| A | `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/worldguard/WorldGuardRegionMetricProvider.java` |
| A | `maddprestige-integrations/src/test/java/net/maddkraft/maddprestige/integrations/PhaseSevenNativeProviderTest.java` |
| A | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/BukkitPaperTaskScheduler.java` |
| A | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/PaperWorldContextMetricProvider.java` |
| A | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/bootstrap/MaddPrestigeV2Plugin.java` |
| A | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/bootstrap/PhaseSevenCommandExecutor.java` |
| A | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/integration/CraftEngineIntegrationBootstrap.java` |
| A | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/integration/CraftEngineReadinessProbe.java` |
| A | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/integration/CraftEngineRegistryLifecycle.java` |
| A | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/integration/CraftEngineReloadListener.java` |
| A | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/integration/EconomyShopGuiIntegrationBootstrap.java` |
| A | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/integration/GriefPreventionIntegrationBootstrap.java` |
| A | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/integration/McMmoIntegrationBootstrap.java` |
| A | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/integration/PhaseSevenOptionalIntegrationManager.java` |
| A | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/integration/PlaceholderApiIntegrationBootstrap.java` |
| A | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/integration/QuickShopIntegrationBootstrap.java` |
| A | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/integration/VaultIntegrationBootstrap.java` |
| A | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/integration/WorldGuardIntegrationBootstrap.java` |
| A | `maddprestige-platform-paper/src/test/java/net/maddkraft/maddprestige/platform/paper/PhaseSevenWorldLifecycleOwnershipTest.java` |
| A | `maddprestige-platform-paper/src/test/java/net/maddkraft/maddprestige/platform/paper/integration/PhaseSevenOptionalCompositionTest.java` |
| A | `maddprestige-testkit/src/test/java/net/maddkraft/maddprestige/testkit/PhaseSevenAcceptanceFixtureTest.java` |

Ignored build outputs under `target`, including `target/MaddPrestige_Phase7_Owner_Review.zip`, are not Git paths. The read-only snapshot and disposable A73 server/harness are outside the repository and are intentionally excluded.
