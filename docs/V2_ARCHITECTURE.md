# MaddPrestige V2 architecture

**Architecture baseline:** Phase 4, 2026-08-15
**Runtime status:** Phase 4 lifecycle/authorization/recovery services are implemented and testable, but remain deliberately disconnected from frozen V1 production bootstrap/player data

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
- `maddprestige-core` contains schema/configuration, lossless YAML documents, provider registry, typed requirement trees/scopes/scaling/catch-up, manual progress, safe command rewards, immutable rank-up and Prestige authorization/simulation, exact internal currency contracts, entitlement merging, milestone/season models, operation state validation, arbitrary ordered stages, reconciliation decisions, legacy plans, and generic safe defaults. It imports no Paper, SQL, or external plugin APIs.
- `maddprestige-persistence` owns repository interfaces, JDBC boundaries, backup verification, deterministic migration history, UUID-first player-stage/Prestige/baseline/latch/manual/season/currency state, persisted rank-up/Prestige/projection/reconciliation coordination, bounded startup recovery, the disposable SQLite V2 schema, and external-backend contract harness.
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
26. Prestige has its own intent-only authorization and plan because its multi-domain reset transaction is not a rank-up, while sharing the accepted operation/action journal.
27. A successful Prestige changes stage, current/lifetime counts, Prestige scope/baselines, configured internal currency, milestone awards and stage/Prestige history in one SQLite transaction guarded by both stage and Prestige CAS revisions.
28. Internal currency identity is independent of display metadata; all mutations are exact, bounded, audited, transactionally ledgered and idempotent by operation/action ID.
29. External metric read capability never implies reset authority. Phase 4 external resets are disabled and unsupported configuration fails closed.
30. Seasons are one-active progression-data containers with immutable scope/archive history; they never own world or unrelated gameplay lifecycle.
31. Recovery is bounded and evidence-driven. Known native effects may be verified; uncertain external actions are retained for reconciliation and never blindly replayed.
32. Phase 4 provider pins must retain every prior-phase pin exactly and may add validated Phase 4-only cost/reward/metric pins. The active Prestige requirement tree is traversed transitively; reachable leaves are validated/health-checked/pinned while truly dormant definitions remain lazy.
33. Prestige scoped requirement progress, baselines, and latches are one coherent disposition. `RESET` creates a new scope and reachable boundaries; `PRESERVE` keeps the existing scope and writes no replacement boundary. Purchased perks, milestone history, season progress, and historical statistics are preserve-only in the Prestige policy. A season owns a separate, narrow `season-progress` `RESET`/`PRESERVE` policy; unrelated generic reset components are rejected.
34. Full active-revision/provider-generation/activation/health bindings are rechecked after costs and before either rank projection or internal commit. Native currency actions carry their plan-bound configuration revision in the sealed planned action.
35. SQLite, not a process-local monitor, is the internal-currency concurrency authority. Independent connections use transactions, uniqueness, and bounded contention retry. Currency replay equivalence binds financial identity plus actor type/UUID/name, source, reason, and configuration revision; retry timestamps are not identity.
36. Season entry, its authoritative `ACTIVE` qualification, and all required season baselines share one transaction. Active progress updates use a conditional `ACTIVE` write; archive makes normal season progress immutable.
37. Recovery may replay only exact persisted native idempotent payloads through the same healthy generation, or compensate exact known-applied native reversible/idempotent costs. Terminal `COMPENSATED` requires every consequential cost effect to be reversed or proven absent; any mixed uncertain external cost keeps `NEEDS_RECONCILIATION` even after safe native compensation.
38. Normal rank-up and Prestige-reset stage history persist complete actor type/optional UUID/name provenance. Migration 5 preserves legacy null-UUID rows while making actor UUID durable across restart.

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

## Phase 4 lifecycle architecture

```text
PrestigeIntent
    ↓ active Stage + PhaseThree + PhaseFour snapshots
authoritative PlayerStageState + PlayerPrestigeState
    ↓ trusted progress/scope/state sources + pinned metric collection
requirements + cost/reward/milestone preflight
    ↓ exact zero-write PrestigeSimulation
opaque sealed PrestigePlan
    ↓ persist PREPARED operation/actions/details
costs → managed rank projection → atomic internal commit → rewards
    ↓
COMPLETED / COMPENSATED / FAILED / NEEDS_RECONCILIATION
```

Prestige eligibility derives only from configured immutable required/reset stage IDs. Current and lifetime counters are distinct; the finite cap applies to current count, while lifetime is preserved historical state. Cooldown and count arithmetic fail closed. The trusted progress source owns scaling index, catch-up position and current scope IDs. Requirement trees retain Phase 3 exact scaling/catch-up behavior.

The simulation is the future UI's canonical confirmation payload. It seals source/reset stages, counter transitions, requirement explanation, costs/rewards, currency deltas, every reset/preserve component, old/new scopes and baseline snapshots, milestones, season context, provider actions/uncertainty, config provenance, state revisions and generation pins. Simulation has no mutation dependency. Blocked plans have denied authority, and the executor rejects reconstruction/tampering before journal insertion.

The internal commit is a single SQLite transaction guarded by stage and Prestige compare-and-set predicates. It updates both authoritative rows, applies the exact sealed scoped-state disposition, conditionally stores fresh provider-sampled reachable `SINCE_PRESTIGE_START` baselines, conditionally resets Prestige-scoped balances with ledger provenance, records repeatability-keyed milestones, and appends immutable stage/Prestige history. `PRESERVE` retains the current scope/baselines/latches; `RESET` creates a new scope without deleting old evidence. The three scoped requirement components must agree. Purchased perks, milestone history, season progress, and historical statistics reject `RESET` because Phase 4 does not own a truthful mutation for them. External provider resets are a separate capability and remain disabled.

Internal currencies use stable IDs plus display metadata and exact bounded values. Each ledger action uniquely binds operation/action/player/currency/delta/kind/revision. Exact replay returns the original result; conflicting reuse is rejected. The revision is carried by `PlannedCost`/`PlannedReward`, including compensation, so a later active configuration cannot rewrite provenance. Two independent stores are serialized by SQLite transactions and bounded `BUSY`/`LOCKED`/busy-snapshot retry; correctness does not depend on a JVM monitor. Administrative adjustment is a non-player, reason-bound service call.

Entitlement merging is pure and deterministic. Contributions sort by priority then source ID; duplicate sources and mismatched types fail. `MAX`, `MIN`, exact overflow-bounded `SUM`, priority `OVERRIDE`, and `BOOLEAN_OR` return both effective value and provenance.

Milestone definitions model current/lifetime Prestige, stage, season and provider-metric triggers. The Prestige lifecycle evaluates only current/lifetime triggers it owns; enabled unsupported trigger kinds fail configuration validation. Triggered reward definitions enter the same preflight/action plan. Award identity and reward snapshot are committed atomically so restart cannot redeliver a one-time key.

Seasons use a one-active partial unique index. Start allocates a new immutable scope; player entry prepares boundary values without mutation and then transactionally persists the entry plus every required `SINCE_SEASON_START` baseline. Boundary failure rolls back the entry, and exact retry is idempotent. End archives and records history before replacement. Requirement overrides/catch-up references fail closed until a runtime owns them; timestamps are manual-lifecycle metadata only. Stored progress and archive queries are bounded/indexed. A season is data only and has no world, PvP, Court or resource-world authority.

```text
bounded incomplete-operation scan
    ├─ PREPARED → FAILED (no execution began)
    ├─ committed internal evidence → verify transaction actions
    │       ├─ exact native idempotent reward → replay by sealed operation/action ID
    │       ├─ all rewards verified → COMPLETED
    │       └─ incomplete/uncertain external reward → NEEDS_RECONCILIATION
    ├─ no internal commit + exact known native costs → compensate in reverse order
    ├─ interrupted STARTED external action → UNCERTAIN
    └─ generic/compensation ambiguity → NEEDS_RECONCILIATION
```

Recovery reads persisted provider/action characteristics, exact sealed native recovery payloads, journal state, and internal commit evidence. It reissues only native actions explicitly marked recoverable, idempotent, non-external, bound to the same active healthy generation; it never fabricates a plan or reissues an unknown external provider call. Automatic decisions append recovery audit events. Prestige history is updated from `STATE_COMMITTED` to `COMPLETED` or `NEEDS_RECONCILIATION`, preserving both authoritative internal success and later external uncertainty.

## Persistence foundation

The SQLite implementation uses a V2-only disposable schema with foreign keys, integrity checks, explicit indexes, UTC ISO-8601 timestamps, exact decimal text, operation/action journals, configuration revisions, UUID-first `mp_player_stage_state`, semantic baseline/latch state, versioned manual progress, and append-only audit rows. `mp_player_stage_state.config_revision` records the active configuration that last wrote the player's stage; it is historical row provenance, not a demand to bulk-rewrite every row on config apply. Player stage updates use optimistic state-revision compare-and-set; import and baseline/latch initialization use insert-once semantics. Manual progress uses static prepared batch UPSERT guarded by update version. Migration attempts record version, checksum, description, time, result, and detail. An applied version is written only in the same transaction as its successful DDL.

Migration 4 adds player Prestige state/details, exact currency ledger, stage/Prestige history, milestone awards, one-active seasons/player progress/archive, native recovery payloads, and recovery events with player/time/state indexes. Both normal rank-up and Prestige reset append stage history transactionally with their stage CAS; stale CAS writes no orphan row. History and recovery APIs enforce 1–1000 row bounds. Query values are prepared parameters; static column fragments do not contain caller data. No Phase 4 high-volume event path writes SQL per event.

`MySQL` and `MariaDB` are backend contract targets, not supported deployments. CI can provision each separately to exercise exact-decimal and uniqueness primitives. No network/proxy-safe behavior is claimed.

## V1 isolation

V1 source, tests, resources, release artifacts, runtime configuration, and empty SQLite evidence remain at their baseline paths. Phase 0/1 characterization remains historical evidence. Phase 4 tests use only synthetic YAML, real public API types with proxy/test boundaries, in-memory providers, and disposable `mp_`-prefixed SQLite files. No production configuration, player record, database, economy, Paper server, or LuckPerms state is opened or mutated.
