# Phase 9E file manifest

Baseline: `f25821c173bc7f2c320c7f0281378d8f41cb71c8`<br>
Branch: `v2/phase-9e`<br>
Candidate path count: 25<br>

## Production/runtime/resources (4)

1. `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/command/CommandCompletionService.java`
2. `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/schema/PhaseSixSchema.java`
3. `maddprestige-platform-paper/src/main/resources/locales/en_US.yml`
4. `src/main/resources/plugin.yml`
These freeze numeric-only discovery, prune unreachable configuration fields, keep normal help numeric, and record the
accepted policy. No Stable API signature changes.

## Regression tests (4)

1. `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/admin/PhaseSixAdministrationUxTest.java`
2. `maddprestige-integrations/src/test/java/net/maddkraft/maddprestige/integrations/mcmmo/OfficialMcMmoExperienceAccessTest.java`
3. `maddprestige-platform-paper/src/test/java/net/maddkraft/maddprestige/platform/paper/admin/PaperPhaseSixNumericCommandRoutingTest.java`
4. `maddprestige-platform-paper/src/test/java/net/maddkraft/maddprestige/platform/paper/bootstrap/StartupConfigurationLoaderTest.java`

These hold schema/discovery behavior, confirmation isolation, and synthetic fixture identity hygiene.

## Current public/operator/policy documentation (13)

1. `DECISIONS.md`
2. `README.md`
3. `STATUS.md`
4. `docs/COMMANDS_PERMISSIONS.md`
5. `docs/CONFIGURATION.md`
6. `docs/CURRENCIES_ENTITLEMENTS.md`
7. `docs/DEPLOYMENT_RUNBOOK.md`
8. `docs/INSTALLATION_V2.md`
9. `docs/MIGRATIONS_BACKUPS_RECOVERY.md`
10. `docs/PROVIDER_CAPABILITY_MATRIX.md`
11. `docs/PROVIDERS_INTEGRATIONS.md`
12. `docs/QUICK_START.md`
13. `docs/UPGRADE_ROLLBACK_V2.md`

## Consolidation/evidence documentation (4)

1. `docs/V2_PHASE9E_BACKEND_FREEZE.md`
2. `docs/V2_PHASE9E_FILE_MANIFEST.md`
3. `docs/V2_PHASE9E_PHASE9D_PATH_AUDIT.md`
4. `docs/evidence/phase9e/PHASE9E_A63_POPULATED_PRE_PHASE9B_UPGRADE.md`

## Exact totals and scope

- production/runtime/resources: 4
- regression tests: 4
- current public/operator/policy documentation: 13
- consolidation/evidence documentation: 4
- total: 25
- protected V1 Java source changes: 0
- Stable API breaking changes: 0
- GUI/shop/Court/Phase 10 implementation: 0
- generated output, clone config, credentials, test-player identity, debugger/fault hooks, production values: 0

## Verification

- clean Maven verification run 1: 630 tests / 111 suites / 0 failures / 0 errors / 0 skips
- clean Maven verification run 2: 630 tests / 111 suites / 0 failures / 0 errors / 0 skips
- Phase 9D focused union: 245/245 PASS
- Phase 9C regression set: 123/123 PASS
- Phase 9B invariant matrix: 64/64 PASS
- Phase 9A integrity: 5/5 PASS
- segmented scaling: 6/6 PASS
- LuckPerms: 10/10 PASS
- mcMMO: 17/17 PASS
- PlaceholderAPI: 12/12 PASS
- confirmation/session: 18/18 PASS
- lifecycle/recovery: 22/22 PASS
- configuration/admin: 122/122 PASS
- Checkstyle: 0 violations
- `git diff --check`: PASS
- reproducibility: PASS (JAR and aggregate SBOM byte-identical across both clean builds)
- distribution JAR: 16,578,515 bytes; SHA-256 `93F6C330007AF3E41EB2C734334FCA566E7C33286E4615CFAC043078EFD62830`
- aggregate SBOM: 190,831 bytes; SHA-256 `3797F16C617B3207229EFD8A846BE2EC31FB9CD56BE681A2069447A859D5568E`
