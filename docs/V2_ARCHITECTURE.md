# MaddPrestige V2 architecture

**Architecture baseline:** Phase 2, 2026-08-15
**Runtime status:** Phase 2 services are implemented and testable, but remain deliberately disconnected from frozen V1 production bootstrap/player data

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

- `maddprestige-api` contains immutable public value objects, identifiers, structured results, provider metadata, operation plans, validation, explanations, audit contracts, and the asynchronous generic rank-adapter contract. It imports no Paper, SQL, Vault, LuckPerms, or V1 types.
- `maddprestige-core` contains schema/configuration, lossless YAML documents, provider registry, operation state validation, arbitrary ordered stage definitions, stage draft compilation/validation/impact analysis, reconciliation decisions, legacy stage mapping plans, and generic safe defaults. It imports no Paper, SQL, or external plugin APIs.
- `maddprestige-persistence` owns repository interfaces, JDBC boundaries, backup verification, deterministic migration history, UUID-first player-stage state, persisted rank projection/reconciliation coordination, the disposable SQLite V2 schema, and external-backend contract harness.
- `maddprestige-platform-paper` owns explicit Paper server-thread/worker boundaries. It contains no V2 bootstrap or progression activation through Phase 2.
- `maddprestige-integrations` owns dependency-health classification and the first-party LuckPerms 5.5 rank adapter. LuckPerms remains provided/optional and is never imported by generic core.
- `maddprestige-testkit` provides fake rank/currency/progression providers, health simulation, failure injection, action fakes, golden configuration helpers, and disposable SQLite fixtures.
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

Phase 2 adds a typed `progression.yml` compiler and workflow on top of that same flow. It supports an inert document (`active: false`, empty stages/order), immutable stage map, explicit enabled-stage order, configurable baseline, `projection: none`, one rank adapter per ordered ladder, and the three approved reconciliation policies. Structural validation, external target validation, stored-player impact, semantic diff, acknowledgement, verified backup, and atomic canonical apply all run before the typed active snapshot changes. External validation pins the provider generation, and apply fails if that binding is no longer active and healthy. Active Phase 3+ fields are rejected rather than treated as functional.

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

## Persistence foundation

The SQLite implementation uses a V2-only disposable schema with foreign keys, integrity checks, explicit indexes, UTC ISO-8601 timestamps, exact decimal text, operation/action journals, configuration revisions, UUID-first `mp_player_stage_state`, and append-only audit rows. Player stage updates use optimistic state-revision compare-and-set; import uses insert-once semantics. Migration attempts record version, checksum, description, time, result, and detail. An applied version is written only in the same transaction as its successful DDL.

`MySQL` and `MariaDB` are backend contract targets, not supported deployments. CI can provision each separately to exercise exact-decimal and uniqueness primitives. No network/proxy-safe behavior is claimed.

## V1 isolation

V1 source, tests, resources, release artifacts, runtime configuration, and empty SQLite evidence remain at their baseline paths. Phase 0/1 characterization remains historical evidence. Phase 2 tests use only synthetic YAML, proxy-backed API fixtures, and disposable `mp_`-prefixed SQLite files. No production configuration, player record, database, or LuckPerms state is opened or mutated.
