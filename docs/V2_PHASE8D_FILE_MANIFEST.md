# Phase 8D exact file manifest

**Baseline:** `d481a9db7cd67108ff77f97e2d64d737e9096276`  
**Branch:** `v2/phase-8`  
**Changed/new review paths:** 119 (59 modified, 60 new)  
**Accepted pre-publication state:** every path was unstaged  
**Publication path set:** the same 119 paths; acceptance-only evidence edits add no path

This manifest is mechanically reconciled to `git diff --name-only` plus
`git ls-files --others --exclude-standard`. Generated targets, disposable Paper/world/database/runtime data, caches and
third-party JARs are not review paths.

| State | Path |
|---|---|
| Modified | `DECISIONS.md` |
| Modified | `README.md` |
| Modified | `STATUS.md` |
| Modified | `docs/V2_ARCHITECTURE.md` |
| Modified | `docs/V2_PHASE8_FAILURE_REGISTER.md` |
| Modified | `docs/V2_TRACEABILITY.md` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/AdministrationException.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/ManualPrestigeAdministrationService.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/OperationPreview.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/OperationPreviewService.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/PermissionSubject.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/command/CommandResponse.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/command/ContextualHelpService.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/command/PhaseSixCommandService.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/config/ConfigurationAdministrationService.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/config/ConfigurationExplanation.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/config/ConfigurationIntrospectionService.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/diagnostic/DoctorService.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/diagnostic/WhyReport.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/diagnostic/WhyService.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/setup/SetupPreview.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/setup/SetupWizardService.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/ui/CanonicalGuiActionExecutor.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/ui/CanonicalGuiMutationExecutor.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/ui/GuiAction.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/ui/GuiActionExecutor.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/ui/GuiMutationExecutor.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/ui/GuiSessionService.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/ui/GuiSessionView.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpAuthorizationResult.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpAuthorizationService.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpPlan.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/plan/RankUpPlanner.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeActionPlanner.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeActionPreflight.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeAuthorizationResult.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeAuthorizationService.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigeBoundaryCollection.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/prestige/PrestigePlan.java` |
| Modified | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/requirement/RequirementEvaluator.java` |
| Modified | `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/admin/PhaseSixAdministrationUxTest.java` |
| Modified | `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/admin/PhaseSixCommandServiceTest.java` |
| Modified | `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/admin/PhaseSixConfigurationAdministrationTest.java` |
| Modified | `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/admin/PhaseSixSimulationAndConfirmationTest.java` |
| Modified | `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/prestige/PrestigeAuthorizationServiceTest.java` |
| Modified | `maddprestige-integrations/src/main/java/net/maddkraft/maddprestige/integrations/luckperms/LuckPermsRankAdapter.java` |
| Modified | `maddprestige-integrations/src/test/java/net/maddkraft/maddprestige/integrations/luckperms/LuckPermsRankAdapterTest.java` |
| Modified | `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/admin/AtomicConfigurationFileStore.java` |
| Modified | `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqlitePlayerInitializationStore.java` |
| Modified | `maddprestige-persistence/src/test/java/net/maddkraft/maddprestige/persistence/sqlite/SqlitePlayerInitializationStoreTest.java` |
| Modified | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/VanillaStatisticsProvider.java` |
| Modified | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/admin/PaperGuiInventory.java` |
| Modified | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/admin/PaperPhaseSixCommandAdapter.java` |
| Modified | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/admin/PaperPhaseSixGuiController.java` |
| Modified | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/bootstrap/MaddPrestigeV2Plugin.java` |
| Modified | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/bootstrap/PhaseSevenCommandExecutor.java` |
| Modified | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/bootstrap/ProductionRuntime.java` |
| Modified | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/placeholder/PlaceholderSnapshotPublisher.java` |
| Modified | `src/main/resources/plugin.yml` |
| New | `PHASE8D_A76_PROCESS_EVIDENCE.txt` |
| New | `PHASE8D_BASELINE_VERIFY.log` |
| New | `PHASE8D_DOCS_AS_TESTS.log` |
| New | `PHASE8D_FOCUSED_TESTS.log` |
| New | `PHASE8D_I18N_TESTS.log` |
| New | `PHASE8D_OWNER_REVIEW_SUMMARY.txt` |
| New | `PHASE8D_PAPER_BOOT_1.log` |
| New | `PHASE8D_PAPER_BOOT_2.log` |
| New | `PHASE8D_PROVIDER_SDK_VERIFY.log` |
| New | `PHASE8D_VERIFY_1.log` |
| New | `PHASE8D_VERIFY_2.log` |
| New | `docs/API_SDK.md` |
| New | `docs/COMMANDS_PERMISSIONS.md` |
| New | `docs/CONFIGURATION.md` |
| New | `docs/COSTS_REWARDS.md` |
| New | `docs/CURRENCIES_ENTITLEMENTS.md` |
| New | `docs/DIAGNOSTICS_TROUBLESHOOTING.md` |
| New | `docs/EVENTS.md` |
| New | `docs/INSTALLATION_V2.md` |
| New | `docs/MIGRATIONS_BACKUPS_RECOVERY.md` |
| New | `docs/PRESTIGE_LIFECYCLE.md` |
| New | `docs/PROVIDERS_INTEGRATIONS.md` |
| New | `docs/QUICK_START.md` |
| New | `docs/REQUIREMENTS_SCOPES.md` |
| New | `docs/SEASONS_MILESTONES.md` |
| New | `docs/STAGES_RANKS.md` |
| New | `docs/V2_PHASE8D_A70_QUALIFICATION.md` |
| New | `docs/V2_PHASE8D_A76_PROTOCOL.md` |
| New | `docs/V2_PHASE8D_A47_AUTHORIZATION_EVIDENCE.md` |
| New | `docs/V2_PHASE8D_ACCEPTANCE_EVIDENCE.md` |
| New | `docs/V2_PHASE8D_ADMINISTRATION_MULTI_THROW_AUDIT.md` |
| New | `docs/V2_PHASE8D_ADMINISTRATION_SEMANTIC_AUDIT.md` |
| New | `docs/V2_PHASE8D_ADMINISTRATION_SINGLE_SOURCE_AUDIT.md` |
| New | `docs/V2_PHASE8D_FILE_MANIFEST.md` |
| New | `docs/V2_PHASE8D_I18N_EVIDENCE.md` |
| New | `docs/V2_PHASE8D_IMPLEMENTATION.md` |
| New | `examples/member-adventurer-veteran/README.md` |
| New | `examples/member-adventurer-veteran/integrations.yml` |
| New | `examples/member-adventurer-veteran/lifecycle.yml` |
| New | `examples/member-adventurer-veteran/progression.yml` |
| New | `examples/member-adventurer-veteran/requirements.yml` |
| New | `examples/member-adventurer-veteran/rewards.yml` |
| New | `examples/provider-sdk/README.md` |
| New | `examples/provider-sdk/pom.xml` |
| New | `examples/provider-sdk/src/main/java/org/example/maddprestige/ExampleProgressProviderPlugin.java` |
| New | `examples/provider-sdk/src/main/resources/plugin.yml` |
| New | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/AdministrationSemanticVariant.java` |
| New | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/presentation/MessageReference.java` |
| New | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/presentation/SemanticPresentation.java` |
| New | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/authorization/AuthorizationBlocker.java` |
| New | `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/authorization/AuthorizationBlockerKind.java` |
| New | `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/documentation/PhaseEightDPublicDocumentationTest.java` |
| New | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/i18n/LocaleReloadResult.java` |
| New | `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/i18n/PaperMessageService.java` |
| New | `maddprestige-platform-paper/src/main/resources/locales/en_US.yml` |
| New | `maddprestige-platform-paper/src/test/java/net/maddkraft/maddprestige/platform/paper/bootstrap/PhaseSevenCommandExecutorPresentationTest.java` |
| New | `maddprestige-platform-paper/src/test/java/net/maddkraft/maddprestige/platform/paper/i18n/PaperMessageServiceTest.java` |
| New | `qualification/phase8d-paper-harness/pom.xml` |
| New | `qualification/phase8d-paper-harness/src/main/java/net/maddkraft/qualification/phase8d/Phase8DQualificationHarness.java` |
| New | `qualification/phase8d-paper-harness/src/main/resources/plugin.yml` |

## Generated review artifacts outside the worktree delta

- `maddprestige-distribution/target/MaddPrestige-2.0.0-SNAPSHOT.jar`
- `target/bom.json`
- `target/MaddPrestige_Phase8D_Owner_Review.zip`

The review ZIP contains the distribution and aggregate SBOM in addition to the 119 paths above. It excludes Paper,
LuckPerms and every other third-party plugin binary, as well as disposable worlds, databases, server configuration,
Maven caches and generated example/harness target directories.
