# MaddPrestige V2 architecture

**Architecture baseline:** Phase 3, 2026-08-15
**Runtime status:** Phase 3 authorization/planning services are implemented and testable, but remain deliberately disconnected from frozen V1 production bootstrap/player data

## Module graph

```text
maddprestige-api
        ↓
maddprestige-core
        ↓
maddprestige-persistence
        ↓
maddprestige-testkit

maddprestige-platform-paper ─┐
maddprestige-integrations ───┼─→ maddprestige-distribution
api/core/persistence ────────┘
```

- `maddprestige-api` contains immutable public value objects, typed metric/cost/reward/provider contracts, identifiers, structured results, provider metadata, operation plans, validation, explanations, audit contracts, and the asynchronous generic rank-adapter contract. It imports no Paper, SQL, Vault, LuckPerms, or V1 types.
- `maddprestige-core` contains schema/configuration, lossless YAML documents, provider registry, typed requirement trees/scopes/scaling/catch-up, manual progress, safe command rewards, immutable rank-up planning/simulation, operation state validation, arbitrary ordered stages, reconciliation decisions, legacy plans, and generic safe defaults. It imports no Paper, SQL, or external plugin APIs.
- `maddprestige-persistence` owns repository interfaces, JDBC boundaries, backup verification, deterministic migration history, UUID-first player-stage/baseline/latch/manual state, persisted rank-up/projection/reconciliation coordination, the disposable SQLite V2 schema, and external-backend contract harness.
- `maddprestige-platform-paper` owns explicit Paper server-thread/worker boundaries and actual Bukkit statistic capability/read adaptation. It contains no V2 bootstrap or progression activation through Phase 3.
- `maddprestige-integrations` owns dependency-health classification and the first-party LuckPerms 5.5 rank adapter. LuckPerms remains provided/optional and is never imported by generic core.
- `maddprestige-testkit` provides fake rank/currency/progression/cost/reward providers, health simulation, failure injection, action fakes, golden configuration helpers, and disposable SQLite fixtures.
- `maddprestige-distribution` compiles the frozen V1 source from its unchanged root location and bundles inactive V2 foundation artifacts. This avoids a big-bang V1 move while keeping the reactor reviewable.

## Dependency rules

1. Generic core never imports Paper or a third-party plugin API.
2. SQL never appears in core services.
3. Providers return structured unavailable failures; absence is not represented as numeric zero.
4. No rank-provider contract exposes group creation. Group validation is read-only and missing targets are errors.
5. Membership planning can change only the intersection of direct memberships and the configured managed set.
6. Consequential work is represented by an immutable operation plan pinned to configuration/provider generations.
7. Operation and per-action state changes must pass an explicit transition state machine.
8. Active configuration is one atomic immutable reference. Invalid/unacknowledged candidates and unverified backups cannot activate.
9. Exact decimals enter through decimal text or `BigDecimal`, never through `double`.
10. Paper-thread work and asynchronous work are explicit scheduler targets.
11. Stage state stores UUID plus immutable `StageId`; display, ordinal, and external group are configuration/provider data.
12. Contextual or temporary managed-group nodes are ambiguous, preserved, and never cleaned by name alone.
13. A rank operation carries the exact managed set, desired projection, configuration revision, and provider generation that planned it.
14. Requirement evaluation is read-only; baseline and latch writes occur only in explicit lifecycle transition services.
15. Unavailable, invalid, and error metric samples are distinct from unsatisfied and never become numeric zero.
16. Costs sharing a provider are preflighted as one batch; required rewards preflight before cost consumption.
17. Canonical rank-up authorization, not a caller-composed evaluation/action list, resolves the legal transition and seals the exact plan.
18. For projected stages, execution orders verified costs → journaled external projection → optimistic internal commit → rewards; uncertainty/divergence requires reconciliation.
19. Command actions remain disabled unless exact templates, normalized roots, structured tokens, and bounded operation limits validate; recursion uses trusted runtime context, not metadata.
20. Active stage and Phase 3 configuration publish as one revision-consistent immutable snapshot pair retaining the exact validated active/reference provider-generation pins.
21. Reversible cost compensation has a distinct preplanned action record and never erases original effect evidence.
22. `RankUpIntent` is request intent only. Trusted engine composition supplies scaling index, catch-up position, lifecycle scope identities, and persistent requirement state; a UI/API caller cannot assert them.
23. `PlayerStageState.configRevision` is historical provenance for the configuration that last wrote that row. A rank-up separately binds that observed source revision and the current active revision governing the new plan/write.
24. Rank projection is a required provider role during planning/simulation: its exact pin must be active, healthy, and a `RankAdapter`. Optional reward health remains role-local and may omit only that reward.
25. A blocked canonical plan carries denied authority. An executable authorization seals every execution-consequential plan field and is checked before journal insertion; `unavailableProviders` remains diagnostic-only because no execution decision consumes it.

## Configuration flow foundation

```text
lossless YAML documents
        ↓
named immutable draft
        ↓
schema + structured validation
        ↓
immutable compiled candidate + content hash
        ↓
semantic diff + verified backup metadata
        ↓
atomic active configuration reference
        ↓
new revision (apply or rollback)
```

Commands, GUI, YAML import, API, help, and a possible future web surface are expected to call these services. Phase 1 does not implement those user interfaces.

Phase 2 adds a typed `progression.yml` compiler and workflow on top of that same flow. It supports an inert document (`active: false`, empty stages/order), immutable stage map, explicit enabled-stage order, configurable baseline, `projection: none`, one rank adapter per ordered ladder, and the three approved reconciliation policies. Structural validation, external target validation, stored-player impact, semantic diff, acknowledgement, verified backup, and atomic canonical apply all run before the typed active snapshot changes. External validation pins the provider generation, and apply fails if that binding is no longer active and healthy.

Phase 3 extends the same candidate with schema-3 stable requirement-tree/cost/reward references plus canonical `requirements.yml` and `rewards.yml`. Metric capabilities drive type/operator/scope/filter validation. Validation walks enabled active-stage references: metrics, costs, and required rewards must be usable; available optional rewards validate/pin; absent optional rewards and dormant definitions do not block activation. One atomic reference publishes stage and Phase 3 snapshots with the same revision and exact validated provider-generation map after canonical apply. Invalid Phase 4+ fields remain explicitly deferred and fail closed where parsed.

Referenced stage removal/disablement remains non-applicable while stored player counts are nonzero. A complete `StageRemapPlan` records validated intent only; because Phase 2 has no atomic remap executor, the plan cannot clear the activation error. Accepted projection syntax is explicit `none`, explicit `group`, or the documented provider/group shorthand; unknown explicit types fail validation.

## Rank projection and reconciliation

```text
PlayerStageState (UUID + StageId + revisions)
        ↓ pinned StageConfigurationSnapshot
immutable persisted OperationPlan / action journal
        ↓ asynchronous RankAdapter
LuckPerms: load groups → load user → classify exact nodes → mutate → save
        ↓ verification / audit
COMPLETED or NEEDS_RECONCILIATION
```

The LuckPerms adapter calls only `loadGroup`, `loadUser`, direct persistent `NodeMap` mutation, `saveUser`, and offline cleanup. It has no group-creation capability. Before mutation it validates every group in the revision-pinned managed set. It removes only exact permanent, context-free direct inheritance nodes whose group is in that set, adds the desired exact group before removals, and leaves permissions, metadata, unrelated groups, temporary nodes, and contextual nodes untouched. Any managed contextual/temporary node blocks mutation as ambiguous.

`warn-only` emits an audited decision without mutation. `maddprestige-authoritative` repairs through the persisted operation executor. `import-once` can insert a missing internal record only when exactly one permanent/context-free managed group maps uniquely to an enabled stage; it never overwrites an existing record. The import worker rechecks active configuration and provider binding immediately before insertion. A bounded rate gate prevents uncontrolled repeated reconciliation. A successful external save whose after-state cannot be verified is post-effect uncertainty and enters persisted reconciliation-required handling.

## Requirement authorization and rank-up planning

```text
atomic Stage + PhaseThree snapshots + retained provider pins
        ↓ authoritative PlayerStageState + legal-next-stage resolution
trusted progress-context source + requirement-state reader
        ↓ scaling/catch-up/scope/baseline/latch inputs
one batched metric read/provider → bound pure evaluation
        ↓ exact configured cost/reward batch preflight
executable authorization-sealed RankUpPlan or denied-authority blocked simulation preview
        ↓ persist PREPARED operation and pending actions
costs → external rank projection (if configured) → internal commit → rewards
        ↓
COMPLETED, COMPENSATED, STALE, BLOCKED, or NEEDS_RECONCILIATION
```

Metric descriptors are authoritative for type, operators, reads, delta support, monotonic/reset behavior, and dimensions. `RankUpIntent` contains only actor, player UUID, optional intended target, and idempotency key. An injected `RankUpProgressContextSource` supplies canonical scaling, catch-up, and scope instances, while an injected `RequirementStateReader` supplies persistent baseline/latch state. Tree evaluation accepts revision/generation-pinned samples, and its opaque binding includes player, tree semantic identity, revision, provider pins, and scope instances. Baseline and latch state is keyed by UUID, requirement, scope, explicit scope instance, and the versioned length-prefixed `rsf2:` semantic fingerprint; a returned record with any different key fails closed. Synthetic prestige/season scope instances are Phase 4 lifecycle contracts, not a claim those engines exist.

Scaling derives an exact target before opt-in local catch-up. Evaluation never mutates; explicit `BaselineInitializationService` and `RequirementCompletionTransitionService` calls initialize baselines and persist eligible latches. Those service/persistence semantics are implemented and tested, but Phase 3 has no production lifecycle invocation outside tests. Future production stage-entry/completion composition must own A15/A14 calls; Phase 4 owns the Prestige/season variants. The pure evaluation path is therefore safe for previews, simulation, later `/why` surfaces, and consequential planning without hidden writes.

Cost and reward preflight is provider-batched. Required reward impossibility blocks before costs. A configured projection is also required at planning/simulation time: the exact retained generation must still be active and healthy and the provider must implement `RankAdapter`; `projection: none` has no such dependency, and an optional reward remains independently omittable. A canonical blocked preview carries `RankUpAuthorization.denied()` and cannot authorize itself or an executable reconstruction. An executable authorization seals operation/player/stages, source-state and configuration revisions, provider pins, the complete requirement result plus provenance, costs/rewards, both configured and requested projection, blockers/execution allowance, and the operation plan. The executor checks that seal before journal insertion. `unavailableProviders` is diagnostic-only and unsealed because production execution never reads it. External targets validate before any debit. Projection is a real journal action over the pinned managed set and occurs before `RepositoryStageTransitionCommitter` performs the authoritative optimistic internal commit. The plan binds `expectedPlayerConfigRevision` to the observed source row and `configRevision` to the current active snapshot. Commit checks UUID/state revision/source stage/source provenance, then writes the active plan revision. Safe R1→R2 config apply therefore does not freeze a player whose stage remains valid, while removal/disablement/remap guards and CAS remain binding. This external-first choice prevents new internal-success/external-failure divergence; external-success/internal-failure and preexisting divergence in either direction require reconciliation. Separate compensation actions preserve `APPLIED`/`UNCHANGED`/`FAILED`/`UNCERTAIN` evidence while original debit actions remain verified. An exception or uncertain result after an external call starts is reconciliation work, never a clean rollback assertion.

Command rewards are a narrow adapter over reviewed templates and structured tokens, not arbitrary console/process access. `CommandExecutionContext` retains operation correlation and monotonically increments actual nested depth; production command-triggered rank-up ingress is still Phase 6. Vanilla statistics live in the Paper boundary, classify block/item/entity dimensions separately, and use one scheduler task per provider batch. Manual progress lives in core and uses bootstrap-issued opaque capability authority, bounded aggregation, versioned batch persistence, and explicit flush/shutdown barriers.

## Persistence foundation

The SQLite implementation uses a V2-only disposable schema with foreign keys, integrity checks, explicit indexes, UTC ISO-8601 timestamps, exact decimal text, operation/action journals, configuration revisions, UUID-first `mp_player_stage_state`, semantic baseline/latch state, versioned manual progress, and append-only audit rows. `mp_player_stage_state.config_revision` records the active configuration that last wrote the player's stage; it is historical row provenance, not a demand to bulk-rewrite every row on config apply. Player stage updates use optimistic state-revision compare-and-set; import and baseline/latch initialization use insert-once semantics. Manual progress uses static prepared batch UPSERT guarded by update version. Migration attempts record version, checksum, description, time, result, and detail. An applied version is written only in the same transaction as its successful DDL.

`MySQL` and `MariaDB` are backend contract targets, not supported deployments. CI can provision each separately to exercise exact-decimal and uniqueness primitives. No network/proxy-safe behavior is claimed.

## V1 isolation

V1 source, tests, resources, release artifacts, runtime configuration, and empty SQLite evidence remain at their baseline paths. Phase 0/1 characterization remains historical evidence. Phase 3 tests use only synthetic YAML, real public API types with proxy/test boundaries, in-memory providers, and disposable `mp_`-prefixed SQLite files. No production configuration, player record, database, economy, Paper server, or LuckPerms state is opened or mutated.
