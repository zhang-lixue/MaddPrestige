# MaddPrestige V2 Phase 2 implementation

**Implementation date:** 2026-08-15
**Baseline:** accepted Phase 1 merge `5aab340554afcf7abe43fd36f6b835175550acc1`
**Scope:** generic stage/rank architecture, persistence, reconciliation, and read-only legacy-mapping scaffolding

## Outcome

Phase 2 adds a generic, configuration-driven stage ladder and a narrow rank-projection boundary without activating V2 in the production plugin. The frozen V1 bootstrap, data, configuration, database evidence, and release artifacts remain unchanged. The V2 default is an inactive, empty ladder using `warn-only` reconciliation.

This phase deliberately does not implement requirements, costs, rewards, prestige, actions, setup UI, commands, or full Paper lifecycle integration. If deferred Phase 3+ stage fields appear in a Phase 2 draft, compilation produces a structured error instead of pretending they work.

## Canonical stage configuration

`defaults/progression.yml` is one document in the existing Phase 1 draft/revision workflow. There is no parallel configuration store. The typed compiler accepts:

- `schema-version`;
- `active`;
- arbitrary immutable stage IDs in `stages`;
- an explicit enabled-stage `order`;
- an explicit `baseline` for active ladders;
- display names and scalar display metadata;
- `projection: none` or an explicit provider/group mapping;
- `warn-only`, `maddprestige-authoritative`, or `import-once` reconciliation.

Accepted projection forms are exactly scalar `projection: none`, mapping `{type: none}` without provider/group, mapping `{type: group, provider: ..., group: ...}`, and the documented shorthand mapping `{provider: ..., group: ...}`. Any other explicit, blank, or non-scalar `type` is a structured error.

The compiler rejects malformed IDs, duplicate YAML keys, duplicate/undefined/disabled/omitted ordered stages, invalid baselines, invalid projection types, duplicate external group ownership, and more than one rank adapter in a Phase 2 ladder. Unknown compatible fields are reported as warnings and remain preserved by the lossless source document. Active deferred gameplay fields are errors.

`StageConfigurationWorkflow` compiles the canonical draft, combines structural and provider validation, calculates the semantic/stored-player impact, and delegates activation to the existing `ConfigurationService`. External validation pins the active provider generation; a binding/generation/health change before apply blocks activation and requires preparing the candidate again. The typed active snapshot changes only after the canonical service has accepted acknowledgements, backup metadata, and the immutable revision.

## Stage identity and impact

`StageId` is the stored identity. Display name, display metadata, position, and external group are configuration attributes and can change without silently changing the stored stage.

`StageChangeImpactAnalyzer` reports:

- old and candidate order;
- added, removed, enabled, and disabled IDs;
- every projection mapping change;
- stored-player counts for removed/disabled IDs;
- a high-risk semantic diff for reorder or projection changes;
- acknowledgement requirements;
- whether an explicit revisioned remap plan covers every affected player reference;
- whether persisted references still require actual remap execution before activation.

Phase 2 validates remap intent/plans but does not execute remaps; a plan alone does not authorize activation while referenced player rows remain. A complete plan is useful impact evidence, but the candidate retains a structured `stage.change.remap_execution_required` error until an authorized atomic executor has run and verified that reference counts are zero. Phase 2 does not implement that executor.

## Player stage state

`PlayerStageState` and `PlayerStageRepository` establish UUID-first authoritative state with:

- immutable `StageId`;
- optimistic `stateRevision`;
- configuration revision;
- stage-entry, create, and update timestamps;
- optional last-reconciliation time and provider generation;
- optional import timestamp.

SQLite migration 2 appends `mp_player_stage_state` and indexes stage references and configuration revisions. Normal updates are compare-and-set and reject stale revisions. `importOnce` inserts only when the UUID has no internal record. Stage-reference counts feed configuration impact analysis.

## Generic rank-adapter contract

`RankAdapter` is an asynchronous provider contract with three operations:

1. validate configured external targets without creating them;
2. read direct membership within an exact caller-pinned managed set;
3. project that set to an optional desired direct membership.

Requests carry the UUID, exact managed set, optional desired group, configuration revision, provider generation, and idempotency key. Results distinguish applied and unchanged outcomes and retain before/after managed-state evidence. Temporary or contextual managed membership is represented explicitly as ambiguity.

No group-creation method exists in the V2 API or adapter. The existing Phase 1 provider registry remains the only registry and now exposes the registered provider instance for generation/binding verification.

## LuckPerms boundary

`LuckPermsRankAdapter` compiles against the real LuckPerms 5.5 public API while keeping it a provided, optional integration dependency. It:

- validates every pinned managed group using `GroupManager.loadGroup` before loading or mutating a user;
- never calls a group-creation API;
- loads users asynchronously by UUID, including offline users;
- preserves cached users and cleans up users it loaded solely for the operation;
- classifies only direct inheritance nodes from the exact managed set;
- mutates only permanent, context-free managed inheritance nodes;
- preserves permissions, metadata, prefixes, suffixes, unrelated groups, temporary nodes, and contextual nodes;
- adds the desired group before removing obsolete managed groups;
- treats contextual/temporary managed nodes as ambiguity and fails closed;
- reports save failure or disablement after mutation begins as uncertain.

The production constructor uses LuckPerms' `InheritanceNode.builder`. Tests link against the real LuckPerms API types but use a proxy-backed in-memory API implementation and a test-only node factory because there is no live LuckPerms service in a unit-test JVM.

## Reconciliation

`RankReconciler` is a pure policy decision service:

- `warn-only` records drift and never mutates internal or external state;
- `maddprestige-authoritative` projects existing internal state through a persisted rank operation;
- `import-once` seeds an absent internal record only when exactly one permanent/context-free managed group maps uniquely to one enabled stage.

Missing internal state is never inferred as the baseline. Multiple managed groups, contextual/temporary managed nodes, missing mappings, or an existing internal record block import. `ReconciliationRateGate` bounds repeated attempts.

`RankReconciliationCoordinator` loads repository state on a worker executor, validates provider generation and health, reads the adapter asynchronously, applies the pure decision, persists/audits the result, and uses insert-once or the operation executor for mutations. It does not introduce a second provider registry or scheduler abstraction.

For `import-once`, the worker rechecks the active configuration revision and exact provider identity/generation/activation/health immediately before `PlayerStageRepository.importOnce`. A stale binding produces an audited `STALE_GENERATION` result and no row; it is never retried or reinterpreted against the replacement configuration.

## Persisted operation and recovery semantics

`RankProjectionOperationPlanner` pins the full managed set, desired group, target stage, configuration revision, expected player state revision, provider generation, and operation idempotency key.

`RankProjectionOperationExecutor` performs repository work on its worker executor and follows this order:

1. detect an existing idempotency tuple;
2. insert the prepared operation and pending action;
3. verify active configuration, exact provider instance, activation/health, and generation;
4. move the journal to executing/started;
5. invoke the asynchronous adapter;
6. persist success/failure/uncertainty;
7. recheck configuration/provider binding after the external effect;
8. optionally compare-and-set internal stage state;
9. verify the action, append audit, and complete.

An uncertain adapter outcome, generation/configuration race after an external effect, internal compare-and-set failure after projection, or audit failure after commit never produces false success. A LuckPerms save followed by failed after-state verification is explicitly post-effect `UNCERTAIN`, including when user cleanup also fails. The operation becomes `NEEDS_RECONCILIATION`; its action becomes `UNCERTAIN` when the external outcome cannot be safely asserted. Duplicate idempotency keys return the existing-operation outcome rather than replaying the adapter call.

Phase 2 records enough state for later recovery tooling but does not add a background replay loop. Blind replay of uncertain external work remains prohibited.

## Legacy scaffolding

`LegacyStageDetector` reports only structural evidence supplied by a caller. Production V2 code contains no legacy gameplay/rank-name list. `LegacyStageMigrationPlanner` treats legacy values as opaque strings and requires an explicit revisioned mapping manifest. Missing mappings, conflicting mappings, or invalid target stages are errors. Invalid/conflicting entries are excluded from compiled mappings, and any error yields an empty planned-player mapping so `canProceed` and plan contents cannot disagree. Non-dry plans require verified backup metadata.

The planner is intentionally read-only: it produces a deterministic UUID-to-`StageId` plan and no mutation executor. It never creates groups, infers mappings, touches frozen V1 files, or opens a production database.

## Runtime safety and threading

`StageRuntimeBootstrap` returns an inert status when no typed snapshot exists, the snapshot is inactive, or it contains no ordered stages. This is a core-level startup seam, not a claim that a complete V2 Paper plugin bootstrap or setup wizard exists.

Database and reconciliation coordination accept an explicit worker executor. LuckPerms operations remain asynchronous through its API. No blocking join is introduced on a Paper server thread. Phase 2 does not activate any event ingestion or gameplay mutation path.

## Verification coverage

Phase 2 adds focused tests for:

- arbitrary stages, explicit ordering/baseline, accepted/invalid projection forms, inactive defaults, deferred-field rejection, and genericity scanning;
- canonical draft compilation/apply, invalid-draft isolation, and provider-generation changes between validation/apply;
- reorder/projection impact, acknowledgements, stored references, complete-but-unexecuted remap blocking, disablement blocking, zero-reference removal, and persisted-row/config preservation;
- all three reconciliation policies, ambiguity, baseline non-inference, and repeated/idempotent behavior;
- SQLite migration 2, UUID round-trip, compare-and-set, import-once, and reference counts;
- persisted projection success, duplicate suppression, uncertain save/post-save verification, config/provider races, repair, import-once coordination, and final pre-insert binding rejection;
- LuckPerms missing-target/no-create behavior, managed-set isolation, cached and offline players, unrelated/temp/contextual nodes, repeated projection, provider outage, disablement during mutation, save uncertainty, post-save verification, and cleanup outcome preservation;
- explicit-only legacy mapping, ambiguity, invalid-target exclusion, plan-content invariants, backup gate, and dry-run behavior.

The complete reactor, quality gates, reproducibility comparison, protected-path scan, and exact final test totals are recorded in `STATUS.md` after final verification.

## Qualification boundary and deferred work

The tests use actual LuckPerms 5.5 API interfaces but not a running Paper server or LuckPerms plugin/database. Therefore Phase 2 establishes API compatibility and deterministic adapter behavior, not live-server qualification. MySQL/MariaDB service containers also remain CI harness targets and are not local support evidence.

Deferred beyond Phase 2:

- requirements/evaluation, costs, rewards, actions, and rank-up gameplay;
- prestige, currency, entitlement, season, and competition engines;
- setup wizard, command/GUI editing, doctor/why surfaces, and Paper bootstrap activation;
- live-server Paper/LuckPerms qualification and lifecycle wiring;
- bulk stage-remap execution and legacy production migration;
- automated uncertain-operation recovery/replay policy;
- MaddKraft presets, branded rank mappings, and tuning.

No Phase 3+ system is activated or claimed by this implementation.
