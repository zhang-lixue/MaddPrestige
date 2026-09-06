# Architecture

MaddPrestige 2.0 has one active progression model: a durable non-negative integer Prestige. A successful player
operation advances exactly from `P` to `P + 1`; administrative Set and Reset are separate audited operations.
Stages, ranks, LuckPerms groups, seasons, and world resets are not intrinsic to Prestige.

## Module boundaries

- `maddprestige-api`: stable public types and service/provider contracts with no Bukkit dependency
- `maddprestige-core`: configuration, evaluation, planning, operation, administration, and GUI projections
- `maddprestige-persistence`: SQLite repositories, migrations, backup gates, journals, and recovery
- `maddprestige-integrations`: optional provider contracts and adapters
- `maddprestige-platform-paper`: Paper lifecycle, commands, inventories, events, localization, and plugin discovery
- `maddprestige-distribution`: the shaded server plugin
- `maddprestige-testkit`: deterministic fixtures and failure-injection support

The legacy V1 source remains frozen as a characterization boundary. The V2 entry point is
`net.maddkraft.maddprestige.platform.paper.bootstrap.MaddPrestigeV2Plugin`.

## Configuration authority

Versioned YAML documents are parsed through one schema registry, preserved through a lossless source-document model,
validated, compiled, and published as one immutable `ActiveConfiguration`. Runtime work pins the active revision and
revalidates it before consequential effects. Commands and GUIs call the same draft/revision administration services;
they do not write YAML or database state directly.

Supported guided edits preserve comments, ordering, unknown keys, requirement-tree shape, and unsupported structures.
An edit that cannot be represented losslessly fails closed. Complex structures remain available through the YAML
draft/validate/diff/apply workflow.

## Requirements, costs, and rewards

Requirement evaluation is pure and provider-backed. Trees support `ALL`, `ANY`, and `X_OF_N`; typed metric
contracts prevent presentation code from inventing provider semantics. Costs, internal commitment, and rewards are
planned separately. Advanced configuration may use independent requirements and costs, while the guided Money editor
intentionally keeps its effective requirement and consumed amount synchronized.

Scaling uses exact decimal arithmetic with validated bounds. Flat, linear, exponential, manual, segmented,
transition, and per-level override behavior is resolved by the canonical scaling service before preview or execution.

## Operations and recovery

Consequential work is represented by immutable plans with idempotency keys and persisted per-action state. Player
Prestige executes in the order costs, durable internal commit, then rewards. Startup recovery resumes or reconciles
unfinished work from the journal; it never guesses an external outcome. Provider unavailability and uncertain state
fail closed.

Interactive confirmations are player- and login-session-bound. They expire on successful consumption, replacement,
logout, shutdown/restart, relevant configuration change, or failed final revalidation. A secondary bounded lifetime
does not replace session ownership.

## Persistence and ownership

SQLite is the sole supported 2.0 production backend. One serialized, bounded write coordinator and database
transactions protect state, ledger, audit, migration, and operation invariants for one Paper process. Schema
migrations are ordered, checksummed, backup-gated, and forward-only.

Provider-owned values remain external. MaddPrestige reads or changes them only through declared capabilities and
planned actions. LuckPerms groups are administrator-owned: MaddPrestige never creates groups and touches only exact
configured direct memberships within its managed set.

## User and administration surfaces

The Player GUI and concise commands present gameplay state. Detailed provenance and diagnostics are opt-in. Staff
views use canonical projections for player state, providers, history, configuration, and system health. Set/Reset
Prestige and configuration changes have narrow permissions, selected-target/session binding, revalidation,
exactly-once behavior, and durable actor-aware audit records.

See [Configuration](configuration.md), [Commands and permissions](commands-permissions.md),
[API and provider SDK](api.md), and [Compatibility baseline](compatibility-baseline.md).
