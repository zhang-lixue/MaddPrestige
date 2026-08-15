# MaddPrestige V2 architecture

**Architecture baseline:** Phase 1, 2026-08-15
**Runtime status:** foundations are not connected to live V1 progression or player data

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

- `maddprestige-api` contains immutable public value objects, identifiers, structured results, provider metadata, operation plans, validation, explanations, and audit contracts. It imports no Paper, SQL, Vault, LuckPerms, or V1 types.
- `maddprestige-core` contains schema/configuration, lossless YAML documents, provider registry, operation state validation, managed rank-membership policy, and generic safe defaults. It imports no Paper, SQL, or external plugin APIs.
- `maddprestige-persistence` owns repository interfaces, JDBC boundaries, backup verification, deterministic migration history, the disposable SQLite V2 schema, and external-backend contract harness.
- `maddprestige-platform-paper` owns explicit Paper server-thread/worker boundaries. It contains no V2 bootstrap or progression activation in Phase 1.
- `maddprestige-integrations` owns dependency-health classification only. It contains no real LuckPerms, Vault, or player mutation adapter in Phase 1.
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

## Persistence foundation

The SQLite implementation uses a V2-only disposable schema with foreign keys, integrity checks, explicit indexes, UTC ISO-8601 timestamps, exact decimal text, operation/action journals, configuration revisions, and append-only audit rows. Migration attempts record version, checksum, description, time, result, and detail. An applied version is written only in the same transaction as its successful DDL.

`MySQL` and `MariaDB` are backend contract targets, not supported deployments. CI can provision each separately to exercise exact-decimal and uniqueness primitives. No network/proxy-safe behavior is claimed.

## V1 isolation

V1 source, tests, resources, release artifacts, runtime configuration, and empty SQLite evidence remain at their baseline paths. Phase 1 adds characterization fixtures and read-only tests. The V2 schema always uses `mp_`-prefixed tables in disposable files and does not open the V1 fixture except in read-only mode.
