# Phase 9 production-readiness checklist

## Current verdict

**NOT READY FOR PRODUCTION.** Phase 9A prepared repository fixtures, audit evidence, and the clone runbook only. No
MaddKraft clone no-import or current real-provider/full-stack qualification was performed. A boot alone cannot change
this verdict. A63 remains `Partial`; no acceptance-ledger row changed.

Every box below requires an immutable current-clone evidence reference and owner sign-off. Historical Phase 7/8
evidence is prerequisite context, not a substitute for a current Phase 9 run.

## Environment and backup gate

- [ ] Owner approves the exact disposable clone path, isolation controls, run ID, operator, and evidence location.
- [ ] Java, Paper, candidate distribution, current plugin stack, configs, and data hashes are recorded.
- [ ] Candidate JAR is the accepted 16,507,914-byte SHA-256
  `0D961DB73D6C44CF18986CA42B7950914CEA5A1598BA7D41D0E919BFB76C6513`, or a later owner-reviewed artifact with a
  complete new release evidence chain.
- [ ] V1 and V2 JARs are never co-loaded.
- [ ] Complete stopped legacy MaddPrestige directory backup passes manifest/hash/integrity validation and disposable
  restore rehearsal.
- [ ] LP export, plugin-local provider state, historical mapping evidence, and the pristine server checkpoint are retained.
- [ ] Rollback to the pristine clone has been rehearsed and verified before any V2 qualification mutation.
- [ ] Network endpoints, Discord, stores, RCON, backup schedules, and ports cannot affect production.

## Fresh-V2 no-import gate

- [ ] V1 data is preserved in a separate immutable path and its DB/config hashes are recorded before V2 boot.
- [ ] V2 has no V1 player-data input, mapper, executor, or read path.
- [ ] Representative UUIDs with conspicuous V1 rank, Prestige, Tea Leaves, perks, progress, pending operations,
  seasons/history/competition data, and preferences initialize in V2 at Prestige 0.
- [ ] Restart preserves exact V2 P0 state while V1 bytes/rows and external plugin state remain unchanged.
- [ ] The historical mapping artifact remains HISTORICAL / SUPERSEDED / NON-EXECUTABLE; its eight markers are evidence,
  not deployment requirements.
- [ ] A36 changes only after the actual no-import evidence passes.
- [ ] A63 changes only after the independent populated pre-Phase9B V2 SQLite upgrade/preservation/report/restart gate.

## MaddKraft profile and identity gate

- [ ] Exact profile is Wanderer → Curious → Dreamer → Tea Guest → Wonderlander → Madcap.
- [ ] `wanderer` uses implicit LP `default` with `projection: none` as accepted by D-005.
- [ ] Existing `curious`, `dreamer`, `tea_guest`, `wonderlander`, and `madcap` groups validate before apply.
- [ ] MaddPrestige creates no group and changes no LP hierarchy, weight, prefix, inheritance, or display metadata.
- [ ] Only permanent context-free direct membership in the exact five projected progression groups is managed.
- [ ] `mad_hatter`, staff, supporter, unrelated, inherited, temporary, and contextual nodes survive rank, Prestige,
  repair, reconciliation, offline handling, and unchanged restart.
- [ ] A71 and A72 current-clone reruns pass without changing their already accepted Phase 7 ledger classifications.

## Real-provider and recovery gate

- [ ] LuckPerms normal, missing-group, offline, service-loss, stale-generation, rebind, and reconciliation cases pass.
- [ ] mcMMO current values, scoped XP, normal events, stale/unavailable state, loss/rebind, restart, and interruption pass.
- [ ] Vault/Essentials balance, aggregate cost, insufficient funds, service loss/replacement, failure, uncertainty,
  offline behavior, and duplicate/retry cases pass.
- [ ] Required optional provider cases pass for every capability used by the approved profile.
- [ ] Provider outages block only referencing features; unrelated operations/Doctor remain available.
- [ ] Clean restart and explicit process interruption preserve exact state and do not duplicate costs/rewards/progress.
- [ ] Every incomplete/uncertain operation remains visible and is reconciled without blind external replay.

## Economy exploit gate

- [ ] QuickShop-Hikari gross/self/circular/cooperating-account volume produces exactly zero progression credit.
- [ ] AxTrade and `/pay` P2P transfers produce exactly zero earned-money credit.
- [ ] EconomyShopGUI controlled sales remain diagnostics-only/zero-credit under the current adapter.
- [ ] AxSellWands remains zero-credit until a stable source-aware provider is separately reviewed.
- [ ] MobCoins remain separate from Vault money and do not cross-credit.
- [ ] Buy/sell cycling, admin grants, refunds/failures, duplicate events, restart retries, and concurrent submits do not
  create false/duplicate progress.
- [ ] Any future economic earnings metric documents reliable source filters, units, event identity, reconciliation, and
  exploit limits before activation.
- [ ] No production fee, target, threshold, scaling, reward cadence, or milestone is approved merely by framework tests.

## Full-stack and boundary gate

- [ ] Current full MaddKraft plugin inventory is hashed/classified and every delta from the accepted 47-JAR snapshot is
  reviewed.
- [ ] Two full-stack boots and clean shutdowns have no unexplained MaddPrestige classloading, linkage, command,
  listener, scheduler/threading, startup, or shutdown failure.
- [ ] Optional integration absence/loss/rebind is isolated; no duplicate listener/service remains.
- [ ] Console, Doctor, provider generations, audit, TPS/watchdog, and shutdown evidence is retained and hashed.
- [ ] A73 current-clone full-stack rerun passes; historical Satisfied status is not treated as the current run.
- [ ] A real external resource reset proves MaddPrestige does not schedule/create/load/unload/delete/reset worlds.
- [ ] A74 current-clone boundary rerun passes, or lack of a current external reset remains an explicit blocker.
- [ ] A future Court/PvP plugin is unconfigured/no-effect by default and metric-only when explicitly configured.
- [ ] No MaddPrestige drafting/scoring state, command, API, or lifecycle ownership exists.
- [ ] A75 real future-plugin rerun passes when that plugin exists; repository fake-provider evidence is not mislabeled.

## Manual roles and operations gate

- [ ] OP/admin completes status, Doctor, setup/config, preview, simulation, apply/rollback, edit/repair, and audit review.
- [ ] Authorized non-OP staff can perform only explicitly granted operations.
- [ ] Ordinary non-OP can inspect and execute only allowed own-player rank/Prestige paths.
- [ ] Supporter+progression account passes every identity-isolation case.
- [ ] Offline account passes UUID-capable paths and receives explicit unavailable results for online-only providers.
- [ ] Command and GUI paths are semantically equivalent where the live adapter exposes both.
- [ ] Permissions are tested by actual denied attempts with zero mutation, not by reading declarations only.
- [ ] Audit output identifies actor, target, old/new state, revision, provider/action, time, and outcome while redacting
  secrets and not exposing arbitrary diagnostic text.

## Regression and release-evidence gate

- [ ] Authoritative clean Maven verification passes with exact tests/suites/failures/errors/skips and Checkstyle zero.
- [ ] `git diff --check` passes and worktree/evidence path counts are recorded.
- [ ] Protected V1 and accepted Phase 8 evidence remain unchanged unless an explicit owner-reviewed necessity exists.
- [ ] Distribution and aggregate SBOM rebuild reproducibly and exact sizes/hashes are recorded.
- [ ] Package, manifest, provenance, dependency, license, Stable API/event, and unresolved-placeholder gates pass.
- [ ] No fake/manual/live result is recorded without its raw/sanitized current evidence.
- [ ] All Phase 9 failures/blockers and remediation are retained; tests are not weakened or bypassed.
- [ ] Final acceptance-ledger changes, if any, cite evidence at the full contract strength.

## Owner policy gate

- [ ] Owner no-import decision is recorded; no V1 player mapping or migration executor is authorized.
- [ ] Owner approves current MaddKraft plugin/version deltas and the completed clone report.
- [ ] Owner approves actual production requirements, rewards, costs, Prestige currency policy, scaling, milestones, and
  reset/preserve semantics using real economy/progression data.
- [ ] Deferred weekly objectives/Rabbit Holes remain optional and are not mandatory unless separately implemented and
  approved.
- [ ] Player choice is preserved; no unapproved requirement forces every provider/category simultaneously.
- [ ] Production deployment, maintenance window, backup retention, rollback authority, and monitoring are separately
  approved after clone qualification.

## Current unresolved blockers

1. No real clone evidence yet proves fresh V2 ignores a separate populated V1 player-data source.
2. No real populated pre-Phase9B V2 SQLite upgrade/preservation/report/restart evidence yet closes A63.
3. No owner-designated clone/full current plugin artifact inventory was supplied or booted.
4. Real LP, mcMMO, Vault/Essentials, shop/trade, restart/crash/outage, and manual-account scenarios are not run.
5. Current resource-reset and future Court/PvP-plugin boundary evidence is not run.
6. Actual MaddKraft production requirements/rewards/balance remain intentionally unapproved and inactive.

Until every applicable blocker and checkbox is resolved, the required report wording is: **Phase 9A package ready for
owner-reviewed clone execution; MaddPrestige V2 is not production-ready.**
