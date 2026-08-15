# MaddPrestige V2 Status

**Current phase:** Phase 2 generic stage/rank architecture owner-review correction pass implemented and locally verified
**Last updated:** 2026-08-15
**Implementation status:** correction pass complete in the `v2/phase-2` worktree, intentionally uncommitted and disconnected from production bootstrap/data

## Outcome

Phase 2 adds arbitrary immutable stages, ordered-ladder compilation, change-impact/remap gating, UUID-first player stage persistence, an asynchronous generic rank adapter, a LuckPerms 5.5 implementation, reconciliation policies, persisted rank operations, and explicit read-only legacy mapping plans.

The V2 runtime remains safe and inactive by default. The canonical default has `active: false`, no stages, no order, and `warn-only` reconciliation. Frozen V1 still provides the distribution bootstrap; no production configuration, player data, database, or LuckPerms state was accessed or changed.

Principal handoff documents:

- [docs/V2_ARCHITECTURE.md](docs/V2_ARCHITECTURE.md);
- [docs/V2_PHASE2_IMPLEMENTATION.md](docs/V2_PHASE2_IMPLEMENTATION.md);
- [docs/V2_PHASE2_FILE_MANIFEST.md](docs/V2_PHASE2_FILE_MANIFEST.md);
- [docs/V2_TRACEABILITY.md](docs/V2_TRACEABILITY.md);
- [DECISIONS.md](DECISIONS.md).

## Implemented in Phase 2

- immutable arbitrary stage definitions, display metadata, enabled order, explicit baseline, and `projection: none` or one provider/group projection;
- `progression.yml` compilation through the existing lossless draft/revision/apply workflow rather than a second configuration system;
- structured validation for order/baseline/projection/provider targets, plus explicit rejection of active Phase 3+ fields;
- semantic impact reporting for order, add/remove, enable/disable, projection changes, stored-player references, acknowledgements, and revisioned remap plans;
- inert runtime status when configuration is absent, inactive, or empty;
- UUID-first player stage schema/repository with optimistic revisions, import-once, reconciliation metadata, and reference counts;
- async rank-adapter API with a caller-pinned managed set, target validation, ambiguity reporting, and no group-creation capability;
- LuckPerms 5.5 adapter using public API types, asynchronous UUID load/save, cached/offline lifecycle handling, exact permanent/context-free node ownership, unrelated-node preservation, fail-closed ambiguity, and uncertainty reporting;
- pure `warn-only`, `maddprestige-authoritative`, and `import-once` reconciliation decisions with a rate gate;
- persisted projection/reconciliation coordination with idempotency, configuration/provider-generation checks, optimistic internal commit, action journaling, audit, and `NEEDS_RECONCILIATION` recovery state;
- structural legacy detection and explicit mapping manifests/plans with no inference, no mutation executor, and a verified-backup gate for non-dry planning.

## Owner-review correction pass

- a complete remap plan now records operator intent only: activation remains blocked while persisted references to removed or disabled stages are nonzero, because Phase 2 has no bulk remap executor;
- import-once rechecks the active configuration revision and provider identity, generation, activation, and health immediately before its insert; either deterministic race returns `STALE_GENERATION`, writes a failed audit record, and inserts nothing;
- explicit `projection.type` accepts only `none` or `group`, rejects blank, unknown, and non-scalar values, and retains the documented omitted-type shorthand;
- a LuckPerms snapshot failure after a successful save is classified as `UNCERTAIN`; operation state remains `NEEDS_RECONCILIATION`, the internal stage does not advance, and cleanup cannot downgrade that result;
- invalid or conflicting legacy mappings produce an error report with an empty planned-mapping set, so they cannot be mistaken for executable-looking work.

## Verification completed

- `./mvnw --no-transfer-progress clean verify`: **passed** on the Windows wrapper equivalent;
- reactor tests: **94 run, 0 failures, 0 errors, 0 skipped** across 30 suites;
- Maven Enforcer, dependency convergence, duplicate dependency-version checks: **passed**;
- Checkstyle: **0 violations**;
- JaCoCo reports and aggregate CycloneDX SBOM: **generated**;
- focused stage workflow, LuckPerms, persistence, and provider/configuration race regression reruns: **passed**;
- two clean correction-pass builds produced the same distribution SHA-256: `ee6689defc1b5410d711e9912402958ddc0398f8daadb2d34bb04e571956b9ce`;
- protected-path comparison against accepted Phase 1 merge `5aab340554afcf7abe43fd36f6b835175550acc1`: **0 changes** in `src`, `dist`, `baseline/v1/runtime`, and `baseline/v1/external`;
- prohibited fixed legacy gameplay/rank-name scan over generic V2 production modules/resources: **0 hits**;
- executable V2 group-creation call scan: **0 hits** (four broad textual matches are capability/remediation strings saying groups are not created);
- generic API/core external-plugin import scan: **0 hits**;
- Phase 3+ implementation/package leak scan: **0 hits**; deferred YAML fields exist only as fail-closed rejection keys;
- `git diff --check`: **passed**.

## What the LuckPerms tests establish

The integration module compiles and tests against the actual LuckPerms 5.5 API artifact and public interfaces. Its proxy-backed in-memory API fixture proves call ordering and adapter invariants, including missing-target blocking, no create call, exact managed-node isolation, cached/offline user load-save-cleanup, ambiguity, repeat behavior, outage, disablement during projection, save uncertainty, and post-save verification uncertainty that cleanup cannot downgrade.

A running Paper server, LuckPerms plugin, LuckPerms storage backend, and production player corpus were not used. This is API-level implementation evidence, not live-server qualification. A later lifecycle/qualification phase must run the adapter in a disposable real server stack before support is claimed.

## Remaining risks and gates

1. V2 is not wired into the Paper plugin lifecycle, commands, setup wizard, or event ingestion. A full fresh Paper-only startup criterion is therefore only partially satisfied.
2. Live Paper/LuckPerms lifecycle, classloader, disable/reload, storage, and offline-player behavior remain unqualified outside the proxy-backed API tests.
3. `NEEDS_RECONCILIATION` is persisted honestly, but automated recovery/replay and operator tooling are later work; uncertain external effects must not be blindly replayed.
4. Phase 2 validates revisioned remap plans but does not execute bulk stored-player remapping. Consequently, any removal or disablement with nonzero stored-player references remains intentionally blocked even when the operator supplies a complete plan; activation can proceed only after a separately authorized future executor has atomically remapped the rows and the reference counts reach zero.
5. Legacy planning is synthetic/read-only. Actual production legacy migration, mapping approval, backup, and mutation remain Phase 9 work.
6. MySQL and MariaDB containers were not run locally. Existing service-container jobs remain contract harnesses, not support claims.
7. Live WAL-mode SQLite backup remains outside the byte-copy fixture service and requires the previously documented coordinated online-backup/checkpoint implementation.
8. Requirements, costs, rewards, actions, prestige, currencies, seasons, competitions, commands, GUI, and branded presets remain later phases and are neither activated nor claimed.
9. Existing frozen V1 compiler/deprecation warnings remain characterization evidence and were not changed for Phase 2.

## Decisions and deviations

Phase 2 decisions D-036 through D-040 record the canonical stage workflow, single-adapter ladder boundary, exact LuckPerms node ownership, persisted reconciliation/uncertainty design, and the rule that remap intent cannot stand in for remap execution.

No approved safety or architecture requirement was weakened. No Phase 3+ work was implemented. The only explicit qualification limitation is environmental: real Paper/LuckPerms and MySQL/MariaDB service instances were not available or used locally, so the traceability matrix retains partial classifications for those runtime criteria.

## Stop point

The Phase 2 owner-review correction pass and local verification are complete. Stop here for a second owner review. Do not commit, push, open a PR, merge, activate V2, touch production data, or begin Phase 3 without separate authorization.
