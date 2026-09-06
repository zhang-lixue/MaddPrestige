# MaddPrestige

MaddPrestige is a provider-driven numeric Prestige plugin for a single Paper server. A successful player operation
advances exactly from Prestige `P` to `P + 1` after the configured requirements and costs are revalidated. Rewards,
scaling, administration, history, and recovery all use the same revision-bound configuration and durable operation
model.

The current release is **2.0.0-rc.1**. It is a release candidate: rehearse installation and upgrades on a disposable
copy before using it on a live server.

## Features

- Numeric Prestige with exact `P -> P + 1` progression
- Composable `ALL`, `ANY`, and `X_OF_N` requirements
- Independent, provider-backed costs and rewards
- Flat, linear, exponential, manual, segmented, and per-level scaling
- Player GUI at `/prestige`
- Permission-scoped Staff GUI, player inspection, history, and audit
- Audited Set/Reset Prestige administration
- Revision-bound, lossless guided configuration editing
- Session-bound confirmations and durable crash recovery
- Optional Vault, mcMMO, LuckPerms, PlaceholderAPI, and other integrations
- Stable Java API, provider SDK, and Paper events

## Requirements

- Java 25
- Paper 26.1.2 build 74
- SQLite, embedded in the distribution
- Optional plugins only when referenced by the active configuration

These are the qualified versions, not a promise of compatibility with untested later builds. SQLite is the only
supported production persistence backend for 2.0, with one Paper process and one server directory per database.

## Quick start

1. Download `MaddPrestige-2.0.0-rc.1.jar` from the
   [2.0.0-rc.1 release](https://github.com/zhang-lixue/MaddPrestige/releases/tag/v2.0.0-rc.1).
2. Verify its published SHA-256, then place it in the Paper server's `plugins/` directory.
3. Start Paper once and wait for MaddPrestige to report ready.
4. Run `/maddprestige status` and `/maddprestige setup discover`.
5. Validate and apply a deliberate configuration; the shipped configuration is safely dormant.

See [Getting started](docs/getting-started.md) and the [Quick Start](docs/QUICK_START.md) for the complete flow.

## Player usage

`/prestige` opens the Player GUI. Players can inspect their current and next Prestige, requirements, costs, rewards,
and confirmation state without seeing implementation diagnostics. Advanced read-only explanations remain available
through `/maddprestige why prestige` and `/maddprestige simulate prestige`.

See the [player guide](docs/player-guide.md).

## Administration

`/maddprestige admin` opens the permission-scoped Staff Dashboard. It provides player lookup and inspection,
history/audit views, safe Set/Reset Prestige workflows, system status, and bounded configuration editors. Complex
configuration that cannot be represented losslessly remains read-only and must be edited through the documented
draft workflow.

See the [staff guide](docs/staff-guide.md), [commands and permissions](docs/commands-permissions.md), and
[configuration guide](docs/configuration.md).

## Configuration

Configuration is split into versioned YAML documents and compiled into one immutable active revision. Draft,
validation, diff, apply, rollback, and guided GUI edits all use the same canonical pipeline. MaddPrestige preserves
comments, ordering, and unsupported keys for supported narrow edits, and fails closed when it cannot do so safely.

The active stage-free example is in [examples/numeric-prestige](examples/numeric-prestige). The former stage-ladder
sample is retained only as [compatibility evidence](examples/compatibility/member-adventurer-veteran).

## Integrations and compatibility

MaddPrestige does not create LuckPerms groups. Administrators own groups, hierarchy, prefixes, and unrelated
memberships. Optional providers are activated only when the current configuration references their capabilities.

Read the [compatibility baseline](docs/compatibility-baseline.md), [integrations guide](docs/integrations.md), and
[provider capability matrix](docs/PROVIDER_CAPABILITY_MATRIX.md) before enabling integrations.

## API and extension points

The public API coordinate is:

```text
net.maddkraft:maddprestige-api:2.0.0-rc.1
```

The accepted `2.x-stable-1` compatibility baseline covers the stable Bukkit-free SDK and Paper event surfaces.
Start with the [API and provider SDK guide](docs/api.md), [Paper events](docs/events.md), and the
[provider example](examples/provider-sdk).

## Build and test

```powershell
.\mvnw.cmd --no-transfer-progress clean verify
```

The shaded plugin is written to
`maddprestige-distribution/target/MaddPrestige-2.0.0-rc.1.jar`; the aggregate CycloneDX SBOM is
`target/bom.json`. See [Contributing](CONTRIBUTING.md) for repository expectations.

## Operations and troubleshooting

- [Deployment](docs/operations/deployment.md)
- [Upgrading and rollback](docs/operations/upgrading.md)
- [Migrations, backups, and recovery](docs/operations/recovery.md)
- [Diagnostics and troubleshooting](docs/operations/troubleshooting.md)
- [Release acceptance](docs/acceptance.md)

Never copy only a live SQLite main file, edit migration history, or force an active-configuration pointer. Preserve
the complete stopped plugin-data directory and rehearse restoration before selecting a restored copy for service.

## Known limitations and deferred work

- MySQL, MariaDB, shared-database, and multi-process operation are not supported in 2.0.
- V1 player/configuration data is not automatically imported into numeric Prestige.
- External resource-world reset behavior remains outside MaddPrestige ownership and requires environment-specific
  qualification.
- A first-party Prestige Shop is intentionally deferred until player feedback defines a useful reward catalog; it is
  not a launch requirement and no placeholder UI, command, permission, configuration, or persistence surface ships.

Historical development records remain available from the release tag and Git history; see the
[development archive index](docs/archive/v2-development/README.md).
