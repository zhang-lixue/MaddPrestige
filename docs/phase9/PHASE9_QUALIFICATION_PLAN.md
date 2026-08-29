# Phase 9 MaddKraft qualification plan

## Status and evidence boundary

This Phase 9A package is ready for owner review before a MaddKraft clone/test deployment. No real clone was created,
no live server or database was touched, and no mock result in this repository is represented as real-provider or
full-stack qualification. MaddPrestige remains a release candidate, not GA or production-ready. Phase 10 has not
started.

Phase 9A begins at branch `v2/phase-9a`, exact merged-main baseline
`57d8487e3153d1edee892832be9187caab854bba`. The worktree was empty before verification, the baseline is also local
`main`, and there are zero commits after it. The accepted clean command
`.\mvnw.cmd --no-transfer-progress clean verify` passed before Phase 9A changes with 553 tests in 103 suites, zero
failures, errors, or skips, and zero Checkstyle violations. `git diff --check` passed. The rebuilt distribution was
16,507,914 bytes with SHA-256 `0D961DB73D6C44CF18986CA42B7950914CEA5A1598BA7D41D0E919BFB76C6513`;
the aggregate SBOM was 190,831 bytes with SHA-256
`3797F16C617B3207229EFD8A846BE2EC31FB9CD56BE681A2069447A859D5568E`. The Phase 8F evidence-integrity tests passed
3/3, the ledger remained 63 Satisfied / 12 Partial / 1 Later / 0 Blocked, no protected V1 path differed from the
baseline, and no Phase 10/web-UI path was present.

## Existing Phase 9 support audit

`READY` means the repository capability can be reused at its proven boundary. `PARTIAL` means useful support exists
but the remaining clone/manual boundary is explicit. `MISSING` means Phase 9 cannot claim the named result yet.
`EXTERNAL/MANUAL` means the repository must not fabricate it.

| Capability | Classification | Reusable evidence and exact remaining boundary |
|---|---|---|
| MaddKraft profile | READY for repository validation; PARTIAL for deployment | `PhaseSevenAcceptanceFixtureTest` and `qualification/phase9a/maddkraft-clone/progression.yml` compile the exact six stages. Real current LP groups must be inventoried on the clone before apply. |
| Legacy config detection | READY after Phase 9A correction | `LegacyStageDetector` now recognizes the actual nested V1 `progression.ranks`, LP mapping, patron, and obsolete competition shapes read-only. It does not guess a mapping. |
| Legacy player-stage mapping | PARTIAL | `LegacyStageMigrationPlanner` produces deterministic explicit, revisioned, backup-gated plans and exposes no executable-looking mapping on error. It remains read-only. |
| General V1-to-V2 mutation executor | MISSING, intentionally blocked | No accepted executor converts V1 schema 1 into V2 schema 11. It must not be implemented until the owner resolves the mapping decisions in `MIGRATION_MAPPING.md`. |
| Migration reports | PARTIAL | V2 schema migration has exact history/manifest evidence. The legacy planner returns findings in memory; Phase 9 still needs a source-to-destination report persisted beside the clone migration. |
| V2 SQLite backup/restore | READY | `SqliteBackupService` uses the native SQLite backup API, strict manifest/hash/integrity/schema/history/config validation, and disposable restore rehearsal. |
| Legacy DB/config backup | PARTIAL / EXTERNAL | A stopped-directory copy plus hashes is the safe pre-V2 boundary. `FileBackupService` is only for quiesced fixtures; production composition does not use it. Host/clone tooling must prove the source was stopped. |
| Startup migration/recovery | READY for V2 schemas 0-11 | `MigrationRunner`, schema 11 validation, pending-operation recovery, configuration compatibility, and unchanged restart are accepted. This is not a V1 semantic conversion. |
| LuckPerms reconciliation/isolation | READY for repository boundary; EXTERNAL for clone | Exact permanent context-free managed membership, group-existence validation, offline UUID load/save, supporter preservation, and warn-only reconciliation are covered. Actual clone groups/accounts remain manual. |
| Doctor/Why/provider health | READY | Database, configuration, provider, rank target, pending/reconciliation, Placeholder, scheduler/cache, and flush status are composed into production diagnostics. |
| mcMMO | READY for supported adapter; EXTERNAL for real scenarios | Public mcMMO 2.2.053 current metrics and authenticated adjusted-XP events are covered. Real earning, restart, disable/recovery, and player data must run on the clone. |
| Vault/economy | READY for supported adapter; EXTERNAL for real scenarios | Balance, cost, reward, loss/rebind, and uncertainty boundaries exist for Vault 2.20.2 with an Economy service. Real EssentialsX balances and failure handling remain clone work. |
| EconomyShopGUI | READY as zero-credit diagnostics; PARTIAL as earnings provider | 7.2.0 public events bind for compatibility only. No reliable currency-homogeneous earnings metric is exposed, so production credit remains disabled. |
| QuickShop-Hikari | READY as zero-credit diagnostics | 6.2.0.11 events never create progress, costs, or rewards. P2P revenue is disabled by contract. |
| AxTrade / AxSellWands | EXTERNAL coexistence | No direct adapter is accepted. AxTrade volume is non-creditable; AxSellWands requires a future source-aware public event before credit can be considered. |
| Optional native integrations | PARTIAL | GriefPrevention 16.18.7, WorldGuard 7.0.18, and CraftEngine 26.7.4 have accepted narrow adapters. AdvancedCrates/UMC/DiscordSRV have reviewed fallbacks; AxPlayerWarps is unavailable. |
| Full-stack coexistence | READY historical evidence; EXTERNAL current clone rerun | Accepted Phase 7 classified 47 JARs and ran full/absence cases. Phase 9 must hash and boot the current clone stack; historical evidence cannot substitute for current deployment evidence. |
| Restart/crash/outage/fault injection | READY harnesses; EXTERNAL real execution | Operation recovery, provider simulators, failure injection, Phase 8E harnesses, and fault matrices exist. Process kill and real provider/service outages still require the clone. |
| Resource-world/PvP boundaries | READY static/provider contract; EXTERNAL real boundary event | No world lifecycle or Court ownership exists. A current resource reset and future Court-plugin coexistence are not repository facts until executed. |
| Acceptance/release evidence machinery | READY | Traceability, manifests, reproducibility, package, SBOM, and docs-as-tests are established. Phase 9 must preserve the accepted Phase 8F evidence. |

## Clone qualification sequence

Every step is performed only in an owner-designated disposable clone after the owner approves this package.

1. Freeze the source. Stop Paper cleanly, record host/time/operator, hash the server directory inventory and every
   plugin JAR, and prove the clone uses a separate absolute path, ports, network endpoints, Discord target, and backup
   destination. Do not reuse production credentials or webhooks.
2. Create and validate the pre-migration backup described in `MIGRATION_MAPPING.md`. Preserve the whole legacy
   `plugins/MaddPrestige/` directory, not only the SQLite main file. Record hashes, row counts, integrity result, WAL
   state, LP export, and a restore rehearsal. Any missing or failed check stops the run.
3. Inventory source data read-only. Record schema version, every table count, distinct `progression_rank` values,
   pending transactions, supporter/staff groups, current configuration hashes, and all unresolved mappings.
4. Resolve and sign the mapping manifest. `OWNER_DECISION_REQUIRED` may not appear in an executable manifest. The
   source hash, mapping hash, candidate JAR hash, operator, and decision record become immutable run inputs.
5. Boot the clone first with the accepted artifact in safely dormant mode. Confirm generated V2 documents, V2 schema
   11, clean Doctor output appropriate to dormancy, and no write to the legacy `maddprestige.db` or `config.yml`.
6. Validate the exact six-stage profile against existing LP groups. `wanderer` represents the implicit `default`
   baseline with `projection: none`; only `curious`, `dreamer`, `tea_guest`, `wonderlander`, and `madcap` are managed
   direct groups. A missing group blocks apply and is never created.
7. Execute a read-only migration dry run. Compare source counts and per-player decisions, review every skipped/archive
   category, and require zero unresolved affected players. Phase 9 cannot proceed to mutation while the executor is
   absent or the dry-run report is incomplete.
8. When a later owner-approved executor exists, restore the clone to the pristine checkpoint, run the migration once,
   and reconcile source/report/destination counts and hashes. Run it a second time only if the executor explicitly
   proves idempotency; otherwise prove repetition is rejected before mutation.
9. Run the real-provider matrix in `PROVIDER_TEST_MATRIX.md`, followed by restart, controlled process interruption,
   provider loss/rebind, stale generation, and explicit outage cases. Preserve console, audit, Doctor, Why, DB,
   provider inventory, and before/after external state.
10. Run the full-stack boot and shutdown procedure in `MADDKRAFT_CLONE_ENVIRONMENT.md`; then run the economy exploit,
    resource-world, PvP/Court-boundary, and role-based tests below.
11. Restore the clone to a known checkpoint or destroy the disposable clone. Never promote its DB/config to production
    until every readiness gate is satisfied and the owner separately authorizes production deployment.

## Full-stack coexistence procedure for A73

1. Hash the current server, Java, Paper, MaddPrestige, and every plugin artifact; compare identities with the accepted
   matrix and explain every delta before boot.
2. Remove the V1 MaddPrestige JAR from the clone. Never co-load V1 and V2. Keep the legacy data directory read-only
   until the backup and mapping gates pass.
3. Start once from the pristine clone and once unchanged. Capture the entire console from JVM start through clean
   shutdown. MaddPrestige is `POSTWORLD` and declares every dependency soft; required configured capability comes from
   health/configuration, not descriptor hard-dependence.
4. Reject unexplained `ClassNotFoundException`, `NoClassDefFoundError`, `LinkageError`, plugin disable, command collision,
   listener exception, async Paper API access, scheduler leak, watchdog stall, repeated provider bind, or shutdown
   error attributable to MaddPrestige.
5. Verify `/maddprestige`, `/mp`, and `/mprestige` ownership/alias behavior; PAPI expansion registration; LP, Vault,
   mcMMO, shop diagnostic, GP, WG, and CraftEngine capability status; and absence of duplicate listeners/services.
6. Disable or withdraw one optional capability at a time using the provider-supported lifecycle method. Referencing
   operations must fail closed while unrelated operations and Doctor remain usable. Rebind must replace authority and
   stale work must not complete.
7. Record startup/shutdown times, plugin enable order, exact error scan, provider generations, Doctor before/after,
   and the log hash. A73 evidence requires the current full stack, not a subset or mocks.

## Resource-world and PvP/Court qualification

- A74: before an external resource reset, record MaddPrestige tasks/listeners and world inventory; run the real reset
  owner; confirm no MaddPrestige scheduling, create/load/unload/delete call, command, filesystem mutation, or recovery
  action owns the lifecycle. After reset, Doctor and unrelated player operations remain healthy. Static ownership tests
  support this run but do not replace it.
- A75: with no Court provider configured, install/enable the future plugin and confirm no provider activation or
  progression effect. Then configure only its owner-attested public metric, prove healthy evaluation, disable/re-enable,
  stale-generation rejection, and cleanup. Search its commands/state/logs for drafting/scoring ownership; any such
  ownership in MaddPrestige fails the gate. Until the real future plugin exists, this remains a boundary plan only.

## Manual role-based matrix

| Actor | Required cases | Expected boundary |
|---|---|---|
| OP/admin | status, Doctor verbose, setup/config preview, simulation, apply/rollback, player view/edit/repair, migration dry run | Every mutation is explicit, acknowledged, audited, and redacted; OP state is not used as LP authority. |
| Authorized non-OP staff | Doctor/Why/simulation and only granted player/config actions | Granular permission allows the action; denied actions have zero state change. |
| Ordinary non-OP player | profile, Why, rank-up preview/apply, Prestige preview/confirm, GUI/command parity | Only own player actions are available; exact provider/cost blockers are visible without secrets. |
| Supporter + progression player | every stage, Prestige reset, reconciliation, restart | `mad_hatter` and all staff/supporter/unrelated/contextual nodes survive; only the exact managed progression node changes. |
| Offline player | view/repair/rank projection and supported costs/rewards | UUID-capable paths work; online-only mcMMO/WG/CraftEngine paths return unavailable rather than zero or mutation. |

For every row capture actor UUID/name, permissions, command or GUI path, request/durable operation IDs, configuration
revision, external groups/balance before and after, audit rows, Doctor/Why output, console span, and cleanup checkpoint.

## Acceptance accounting

A71, A72, A73, A74, and A75 retain their already accepted `Satisfied` classifications from Phase 7; Phase 9A neither
reopens nor inflates them. Their current-clone rerun remains a Phase 9 deployment gate. A63 remains `Partial` until the
actual owner-approved MaddKraft clone data migration passes. A64 remains `Later`. No ledger row changes in Phase 9A.
