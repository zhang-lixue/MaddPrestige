# MaddPrestige V2 architecture

**Architecture baseline:** Phase 5, 2026-08-16
**Runtime status:** Phase 5 lifecycle and flagship adapters are implemented and testable, but remain deliberately disconnected from frozen V1 production bootstrap/player data

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

## Phase 5 flagship integration architecture

Phase 5 keeps concrete plugin types in `maddprestige-integrations` and the PlaceholderAPI output expansion in the Paper boundary. Generic core remains plugin-agnostic. `IntegrationProviderLifecycle` composes with the accepted `ProviderRegistry`: discovery registers an adapter inactive and every validated configuration plan is reconciled as the exact desired active set. Registry `ActivationState` is configuration reachability, while `MutableProviderHealth` is operational dependency truth. Discovery and configuration reconcile do not rewrite health. Newly reachable bindings activate, removed bindings deactivate, and unchanged active bindings keep the same registration/generation, but `UNHEALTHY`, `UNAVAILABLE`, `DEGRADED`, `UNSUPPORTED`, `INACTIVE`, or `NOT_INSTALLED` survives every config off/on transition. Only `AVAILABLE` and `ACTIVE` are usable. A successful exact-binding probe may explicitly recover health to `AVAILABLE`; dependency disable marks the old binding unavailable and unregisters it, and successful reacquisition supplies a new healthy binding/generation. Existing planning/authorization checks therefore reject inactive, unhealthy and stale bindings. A dormant missing or unhealthy plugin remains isolated.

`integrations.yml` schema 5 is dormant by default. `PhaseFiveIntegrationSchema`, strict compiler, immutable configuration and activation plan cover every accepted field and mapping. An absent optional object receives its documented dormant default; explicit null, scalar or sequence where a mapping is required fails at the exact path with expected/actual type. Boolean/integer/string/duration fields are not string-coerced. Placeholder input keys must be real YAML strings accepted by canonical `MetricId`. Unknown keys, malformed values, self-recursive MaddPrestige placeholder tokens, and shop progression credit are errors. No accepted setting is hidden or inert. The resource default disables Vault, mcMMO, PlaceholderAPI output, EconomyShopGUI/QuickShop compatibility and both shop progression paths, and defines no PlaceholderAPI input.

Vault binds the public `Economy` service once per registry generation. Separate cost/reward/balance providers share that immutable binding and a controlled server-thread scheduler. Cost preflight is read-only and aggregate-aware. Before scheduling any Vault call, every action must have the same player UUID, operation ID, configuration revision and sealed provider generation and must validate as a Vault cost. A mixed batch is blocked with zero Vault precision, balance or mutation calls; a coherent batch performs one aggregate balance check. Execution checks the shared usable-health allowlist before even querying `Economy.isEnabled()`, then checks service enablement immediately before the external call. Decimal conversion is exact within the economy's fractional digit policy and rejects a value that cannot round-trip through Vault's `double` API. A success response with a different amount is external uncertainty. Vault does not expose MaddPrestige operation keys or reliable compensation evidence, so the adapters do not claim idempotency, reversibility or reconciliation; the canonical journal/pin/state ordering provides normal duplicate suppression and prevents a failed/stale debit path from advancing internal state.

mcMMO absolute metrics call only the official read-only `ExperienceAPI` for a configured valid skill level or power level. Skill and power are externally owned `CURRENT` values that may decrease, so both descriptors are `NON_MONOTONIC` with reset policy `NOT_APPLICABLE`. XP progression listens at the public final XP-gain event boundary and increments a capability-authenticated exact-decimal manual metric only through a `ProviderRegistrationGate` bound to the exact active, usable mcMMO registration. The old listener rejects after config deactivation, outage, unregister or rebind; only the current recovered generation can resume. The manual provider aggregates dirty values before persistence rather than writing SQL per event. Existing scope baseline records turn that separate monotonic lifetime total into a Prestige-scope delta without editing/resetting mcMMO or trusting current-level XP.

PlaceholderAPI output registers the official persistent expansion but performs only a bounded immutable snapshot lookup during `onRequest`. Canonical runtime owners publish stage, current/lifetime Prestige, requirement status and explicit extras outside rendering. Null player returns an empty value; absent snapshot or unknown parameter returns unresolved. Output resolution has no repository/provider/mutation reference. PlaceholderAPI input is an optional generic metric provider whose controlled refresh rechecks its exact `ProviderRegistrationGate` inside the scheduled task before each official resolver call and again before caching the returned value. A task scheduled before outage, config deactivation, unregister or rebind makes zero resolver calls and leaves prior cache state untouched; an outage during a multi-input refresh stops remaining calls and discards the in-flight returned value. A current healthy generation parses the configured type and atomically replaces a bounded LRU entry. Requirement reads are cache-only and fail unavailable for missing, unresolved, invalid, stale or non-usable health. Input validation scans every closed `%identifier_parameters%` token and rejects identifier `maddprestige` case-insensitively, including mixed case and multi-placeholder strings; a different identifier with `maddprestige` only in its parameters remains unrelated.

EconomyShopGUI uses the official public `PostTransactionEvent` artifact solely for optional compatibility diagnostics. It exposes no metric, manual-progress handle, cost or reward and always has zero progression credit. Progression is deferred because a scalar price cannot truthfully combine Vault money, XP, levels, item currency, points or custom economy totals, especially for multi-economy sale events. The compiler rejects enabling progression. QuickShop likewise uses its public `ShopSuccessPurchaseEvent` solely for compatibility diagnostics. No QuickShop amount or player identity is connected to a progression handle; even self/circular/wash traffic has hard zero progression credit and the compiler rejects enabling it.

PlayTimeManager is intentionally absent. Its tested installed version has no necessary dedicated public service contract, and `VanillaStatisticsProvider` already supplies Paper's authoritative playtime statistic. All third-party API dependencies are Maven `provided` with transitive exclusions where appropriate, so distribution shading does not bundle plugin implementations. The frozen V1 bootstrap and plugin descriptor remain unchanged; production V2 composition belongs to the later authorized runtime phase.

## Phase 6 administration architecture

Phase 6 introduces an administration application layer in generic core. `PhaseSixCommandService`, `GuiSessionService`, `SetupWizardService`, configuration introspection, diagnostics, player inspection, operation preview/confirmation and manual Prestige administration depend on accepted V2 contracts only. Paper code translates a command sender into a permission snapshot, moves blocking work to the configured administration executor, returns rendering to the server thread and maps opaque GUI actions to inventory presentation. It does not compile configuration, evaluate requirements, authorize progression or issue persistence writes itself.

```text
YAML candidate   command draft   GUI mutation   setup wizard
      \              |               |               /
       +-------------+---------------+--------------+
                             |
             ConfigurationAdministrationService
                             |
       complete canonical compile + provider extensions
                             |
           immutable prepared snapshot + history attempt
                             |
             atomic active-revision pointer switch
                             |
                immutable runtime publication
```

The active configuration is a complete immutable multi-document snapshot. A draft carries its source revision and independent lossless documents; editing it cannot publish state. Scalar replacement routes through `LosslessConfigurationEditor`, preserving comments, order, line endings and unknown keys. Structural operations are accepted only when a purpose-built canonical operation can state their semantics, such as setup generation or a stage-remap callback. A candidate is always recompiled in full, including Phase 5 integration schema and provider-specific validation, before preparation or activation.

`AtomicConfigurationFileStore` writes each revision into a new immutable directory with UTF-8 documents and a manifest containing exact content hashes and canonical configuration hash. Preparation verifies every persisted byte before returning. Activation replaces only the small `active-revision` pointer atomically; incomplete revision directories cannot become active. `SqliteConfigurationHistoryStore` separately records `ATTEMPTED`, `APPLIED` or `FAILED` outcome with actor/source/timestamp/detail and the exact document bytes. The attempt precedes file preparation, so a crash leaves understandable evidence. Rollback reads an exact prior valid snapshot, recompiles it against current provider capabilities and creates a new forward revision. It never rewinds or mutates history.

Setup is a session model over the same workflow. Discovery reports current rank/metric provider capabilities and never invokes a creation API. External group names are operator-selected existing targets and are validated through the retained `RankAdapter` at preview/apply. The minimal generated configuration is intentionally conservative: arbitrary stages are supported, but requirements, costs, rewards, Prestige, seasons and optional integrations remain disabled unless the operator subsequently enables and validates them.

Command completion uses an explicitly refreshed immutable `CompletionCatalog`. Provider enumeration and metric descriptor calls happen during refresh on the administration worker, not during keystroke completion. Suggestions are filtered by the same permission model used during execution. Search, get, explain, validate and diff use `SchemaRegistry`, configuration documents and metric descriptors rather than separate help metadata. Unknown paths, ambiguous leaf aliases, bounded query violations and unsupported shapes return actionable errors.

`DoctorService` runs independent bounded read-only probes and orders findings deterministically by severity/component/path. Configuration history identifies failed or abandoned attempts, database health supplies operational state, provider diagnostics report activation/health/capability detail, and rank-target diagnostics identify the exact stage/group/provider without creating anything. `WhyService` accepts only the canonical `RankUpAuthorization`; its blockers, target and recursive explanation therefore cannot disagree with simulation/execution logic.

```text
canonical authorization/simulation
              |
     zero-write OperationPreview
              |
 opaque server-side PreparedConfirmation
 actor + kind + expiry + active revision + sealed plan
              |
       consume once and revalidate
              |
 accepted RankUp/Prestige executor boundary
```

Confirmation IDs are high-entropy opaque values and their server-side records are actor-bound, expiring and single-use. Consumption removes authority before dispatch and rejects a changed active revision. The record retains the accepted sealed authorization/plan; command arguments, chat text, inventory material, lore and item metadata do not reconstruct a consequential operation. Rank-up simulation permission does not imply rank-up execution permission, and Prestige has its own preview/execute permissions.

GUI sessions are likewise server-side and revision-bound. Each rendered slot contains only a label/material associated with an opaque action ID held by `GuiSessionService`. Action execution rechecks audience permission, expected revision and any required stage replacement. `PaperGuiInventoryGuard` blocks top/bottom inventory transfer, shift-click, number keys, offhand swap, double-click and drag paths. The GUI can expose canonical callbacks, but it owns no alternative progression/configuration model.

Manual Prestige administration is a separate explicit authority from normal Prestige execution. The service validates non-negative bounded current/lifetime values, expected state revision, actor, reason and permission. The SQLite store selects the current row, performs an optimistic update and appends a complete audit event in the same transaction. A stale concurrent edit rolls back without an audit row; a successful edit cannot commit without one.

SQLite migration 6 adds exact configuration revision/document history with indexes for revision, status and time. The filesystem remains the immutable payload/activation authority; SQLite is the queryable audit/history authority. Their transition order is deliberate and diagnostics expose an incomplete attempt instead of guessing success. No dynamic caller-provided SQL, destructive production configuration access or live player database was introduced in Phase 6 tests.

### Phase 6 correction-pass authority and recovery

Correction Pass 4 makes destructive configuration ownership a first-class persistence concept. The candidate's complete unsafe set is every stage removed or changed from referenceable to disabled, regardless of the current reference count. `PhaseSixConfigurationWorkflow.beginStageTransition` does not derive this set from a remap. The exact candidate configuration-history revision in `ATTEMPTED` state owns it.

```text
ATTEMPTED history + prepared immutable snapshot
                     │
                     v
SQLite BEGIN IMMEDIATE
  ├─ reject another unresolved configuration transition
  ├─ reject operation source/target leases intersecting any unsafe stage
  ├─ insert declared REMOVED/DISABLED scope + active per-stage reservations
  ├─ revalidate every unsafe-stage count and exact remap snapshot
  └─ migrate every referenced source, if required
                     │
                     v
activate pointer → publish runtime → record APPLIED/FAILED history
                     │
                     v
terminal-gated reservation/remap release, or NEEDS_RECONCILIATION
```

Configuration reservations and operation leases are deliberately distinct. An operation journal owns one source/current plus target/result lease and controls its terminal recovery. A configuration-history revision owns a set of stages that will become invalid for durable references. `SqliteStageReferenceMigrationStore` and `SqliteStageTransitionGuard` make them conflict under the same immediate-write protocol. Healthy non-overlapping operations remain allowed; malformed/incomplete configuration authority blocks globally because its true stage scope is unknowable.

Every remap target is validated against two immutable server-owned authorities before migration: the current/prior compiled configuration and the candidate compiled configuration. It must exist, be enabled and ordered in both, and its exact projection must match the source and remain unchanged between authorities. Normal apply and rollback use the same analyzer and acquisition path. This is intentionally more conservative than reverse migration: no candidate-only stage can become durable fallback state.

Migration 10 stores `mp_configuration_stage_transitions`, immutable `mp_configuration_transition_stages` and active `mp_configuration_stage_reservations`. Acquisition inserts the complete scope and performs any reference migration atomically. Direct player insert/import/CAS/history writes, the Prestige lifecycle reset and operation-lease acquisition check active reservations within their own `BEGIN IMMEDIATE` transaction. Bidirectional declared-scope/reservation integrity and exact `ATTEMPTED` ownership are required; missing/extra rows, terminal-owner residue and ownerless legacy remap authority fail globally closed.

Release requires durable terminal configuration evidence. `CONFIG_APPLIED` requires `APPLIED` history after candidate pointer/runtime publication. `CONFIG_FAILED_SAFE` requires `FAILED` history after coherent prior pointer/runtime authority is retained or restored. Recovery compares history, the filesystem pointer and canonical runtime; it never expires a reservation by age. A disagreement remains `NEEDS_RECONCILIATION`, continues blocking unsafe writes and appears as blocked Doctor evidence. A complete short-lived `RESERVED` owner is only a warning.

`ConfigurationAdministrationService` is the single mutation authority for direct, command and visual configuration work. Draft identity is `(draft ID, server version, actor, base revision, content hash, required apply kind)`. Every edit increments the version and invalidates preview. Preview also seals the exact validation finding set and server-selected persisted-stage remap. Apply repeats full preparation, compares seals, then CAS-claims the same draft before durable work. The claim prevents edit/cancel/apply races from reviving or discarding newer state.

```text
direct admin ─┐
command tokens ├─> ConfigurationAdministrationService ─> PhaseSixConfigurationWorkflow
opaque GUI ───┘          │                                  │
                         ├─ exact draft/version/finding seal ├─ provider pins
                         ├─ server acknowledgement authority └─ persisted remap seal
                         └─ history/snapshot apply
```

`CanonicalGuiMutationExecutor` interprets only server-created `GuiMutationContext`; it never parses inventory lore/NBT into mutations. `CommandResponse` may contain a real `GuiSessionView`, and `PaperPhaseSixCommandAdapter` requests `PaperPhaseSixGuiController.open` on the server thread when bound. The adapter boundary is therefore concrete even though the frozen V1 bootstrap does not register it.

Referenced-stage replacement uses two coordinated durable stores. SQLite migration 7 journals the sealed remap and CAS-migrates every exact player row in one immediate transaction. The immutable configuration directory is then reverified byte-for-byte immediately before atomic pointer selection. These stores cannot share one ACID transaction, so the permitted recovery state is explicit: only projection-equivalent replacements are accepted, meaning a committed player B→C remap remains semantically valid under the prior configuration if pointer activation fails. The journal records `MIGRATED_PENDING_CONFIG`, `CONFIG_APPLIED`, or `CONFIG_FAILED_SAFE`; Doctor surfaces every non-applied reconciliation record. A deleted-stage/current-reference orphan state is never an allowed transition.

SQLite migration 8 introduced `mp_stage_transition_leases`; migration 9 upgrades it to exact operation-owned source-and-target participation. The same `SqliteStageReferenceMigrationStore` implements remap migration and `StageTransitionFence`. Acquisition and remap start both use `BEGIN IMMEDIATE`. A journaled operation that wins first owns `(operation ID, source/current stage, target/result stage, configuration revision, lease token)`, so removal of either participating stage fails before any player row moves. A remap that wins first commits `MIGRATED_PENDING_CONFIG`; the pending remap rejects a later lease whose source or target touches the removed stage before cost or projection execution. The fence is a database-level start barrier, not a late CAS assertion.

The operation journal is created as `PREPARED` before durable participation. Lease acquisition refuses an absent or terminal journal owner, so a crash before journal creation cannot leave a lease. An exact restarted operation adopts the existing token; a different source, target or revision cannot steal it. Release is terminal-state-gated and idempotent: nested/nonterminal release retains the outer authority, while `COMPLETED`, `COMPENSATED` or `FAILED` permits exact deletion. `NEEDS_RECONCILIATION` deliberately retains the row. `PendingOperationRecoveryService` removes terminal evidence at startup, releases on automatic terminal recovery and exposes an evidence-backed `NEEDS_RECONCILIATION` terminal-resolution route that records the recovery event before release. No lease is deleted merely because it is old.

Migration 9 preserves a migration-8 row only when its operation journal is nonterminal. Such a row has an unknown source and is marked incomplete, which blocks destructive remap globally until the same operation supplies and adopts its exact source participation. Rows with absent or terminal owners are deterministically discarded during migration. Doctor reports a normal short-lived active owner as a warning and reports incomplete, absent-owner, terminal-owner or unresolved-reconciliation authority as blocked operational work.

Direct `SqlitePlayerStageRepository` insert/import/update/update-with-history paths and `SqlitePrestigeLifecycleRepository`'s internal commit do not mint durable random-owner leases. Each opens the shared immediate transaction, derives the current source where applicable, checks source plus target against pending remap state, and writes in that same transaction. This serializes import/bootstrap and any direct/manual repository caller against remap without creating crash-orphanable authority. The nested Prestige repository transaction cannot release the outer executor's journal-owned lease.

Rank-up and Prestige ordering is: sealed authorization/config/provider check; duplicate lookup; `PREPARED` journal creation; source-and-target lease acquire/adopt; binding/target recheck; `PREPARED`→`EXECUTING` claim; costs; full binding recheck; external projection; internal CAS/atomic commit; rewards; terminal operation state; terminal-gated lease release. The standalone projection executor follows the same journal→lease→binding→execution pattern and derives its source from the expected authoritative player state. If lease acquisition loses to remap, the still-effect-free prepared journal becomes `FAILED`.

Draft remaps are accumulated per source. Selecting a mapping merges/replaces only that source and removing a mapping leaves other sources intact. The complete map is recompiled and checked immediately: every source must be absent and every target must exist, be enabled and occur in the candidate order. The source-scoped command and opaque GUI mutations increment draft version and invalidate stale preview/candidate/acknowledgement authority. Preview then seals all matching persisted B and D rows together, and apply migrates the complete set in the existing all-or-nothing remap transaction.

Every authoritative current-stage path consumes this boundary. The three executors create or find `PREPARED` journal ownership before acquiring/adopting source+target participation, and acquire before any consequential effect. Direct `SqlitePlayerStageRepository` and nested `SqlitePrestigeLifecycleRepository` paths use the shared transaction guard rather than minting durable authority. The remap writer checks every lease and direct-write transaction before moving a row. Terminal journal evidence releases an exact token even after restart adoption; uncertain/reconciliation outcomes retain it. Phase 6 has no separate manual-current-stage service, so direct repository mutation is the manual boundary and is transaction-guarded.

The durable remap state names are `MIGRATED_PENDING_CONFIG`, `CONFIG_APPLIED` and `CONFIG_FAILED_SAFE`. Pending survives restart and continues to fence every removed source stage. `CONFIG_APPLIED` clears that fence because the new configuration is authoritative. `CONFIG_FAILED_SAFE` clears it only when the old pointer/runtime is safely authoritative and migrated references point to a valid projection-equivalent replacement. If runtime publication fails after pointer activation and restoring the prior pointer also fails, the status deliberately remains pending; Doctor/reconciliation must resolve the pointer/runtime mismatch before old-stage entry can resume. This is coordinated recovery across SQLite, filesystem and memory, not a claim of cross-store atomicity.

Prepared configuration activation checks exact manifest text, flat file names, every document byte and the canonical hash at the last responsible moment. Revision directories, exact configuration documents, history outcomes, player stage history and remap journals are retained. Phase 6 owns no retention deletion because cleanup in the activation path would weaken recovery evidence.

Opaque configuration acknowledgements bind actor, draft version/hash, base revision, immutable apply kind and exact finding seal for five minutes. Draft creation assigns `NORMAL`, `ROLLBACK` or `SETUP`; their permissions are `CONFIG_APPLY`, `CONFIG_ROLLBACK` and `SETUP`. Callers cannot select the acknowledgement kind. Direct, command and GUI paths derive it from live draft state and recheck the exact current permission at final mutation. Operation confirmations use the same actor-before-consume and conditional compare/remove principles. Draft/setup sessions expire at two hours; GUI/operation confirmation lifetime is bounded by construction. Expired authority is pruned opportunistically and is never usable.

`GuiConfigurationAuthority` lets GUI sessions query only the server-owned draft/acknowledgement kind. High-risk rollback therefore produces a rollback-labelled action, issues a rollback acknowledgement and dispatches the rollback apply method; setup stays in the setup-owned route. The older contextless destructive stage editor no longer exists. Stage-remap selection requires actor-owned draft context, verifies that the replacement exists and is enabled in that candidate, increments the draft version and invalidates preview. `preview` no longer accepts a caller-provided remap plan.

Completion remains an immutable cached view populated through explicit refresh methods outside the keystroke path. The service/adapter contract and executable-route parity are tested, but automatic live provider/draft/revision refresh is not claimed while the V2 production bootstrap remains intentionally disconnected.

Doctor declares every Phase 6-owned diagnostic domain. A missing domain produces an explicit deferred not-checked finding, preventing a false complete-health claim. The operational probe covers schema, exact provider references, pending/reconciliation state, orphan/duplicate/integrity checks, Placeholder, scheduler/cache, flush state and unsupported capabilities in addition to database, history and rank-target probes.

The Paper command and GUI adapters remain disconnected from the frozen V1 production bootstrap and `plugin.yml`. This preserves the explicit V1 protection boundary and avoids falsely qualifying a live V2 deployment. The module-level Phase 6 services/adapters are complete and tested; an authorized composition phase must still register them, bind actual runtime repositories/executors, and run live Paper/LuckPerms qualification before A01/A02 or a V2-default release can be marked satisfied.
