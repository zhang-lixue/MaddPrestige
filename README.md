# MaddPrestige 2.0

MaddPrestige V2 is a generic, provider-driven numeric Prestige platform for a single Paper server. Its authoritative
player progression state is a durable non-negative integer: a successful operation advances `P` to `P + 1`, subject
to administrator-configured requirements, independent costs, rewards, cooldown, and optional maximum. Stages, ranks,
LuckPerms groups, seasons, and world resets are not intrinsic to Prestige.

Phase 9F is owner-accepted and checkpointed: the Player GUI, read-only Staff GUI, safe player administration,
revision-bound configuration editors, and final usability pass are complete. Phase 9G is the final release
qualification. It authorizes no live deployment, V1 player import, production balance, or first-party Prestige Shop;
the Shop is intentionally deferred until live/beta player feedback defines a useful reward catalog. The candidate is not GA or production-ready until release qualification and owner publication approval are complete.

## Final Phase 9 release candidate

- Java 25.
- Paper 26.1.2 build 74 stable.
- LuckPerms 5.5.71 when an active configuration explicitly uses optional permission/group rewards.
- SQLite is the sole MaddPrestige 2.0 production persistence backend.
- One Paper process and one server directory per database.
- MySQL/MariaDB and shared-database/multi-process operation are deferred post-2.0.

MaddPrestige does not create LuckPerms groups. The administrator owns those groups and their display/prefix policy.
Optional providers are required only when an active configuration references them.

## Start here

1. [Install the candidate](docs/INSTALLATION_V2.md).
2. Follow the [numeric Prestige Quick Start](docs/QUICK_START.md).
3. Keep [commands and permissions](docs/COMMANDS_PERMISSIONS.md) and
   [diagnostics](docs/DIAGNOSTICS_TROUBLESHOOTING.md) available to operators.
4. Read [configuration](docs/CONFIGURATION.md) before making later revisions.
5. Use the [deployment/rollback runbook](docs/DEPLOYMENT_RUNBOOK.md) for a separately authorized release rehearsal.

The active stage-free example is under [examples/numeric-prestige](examples/numeric-prestige). The former
[stage-ladder example](examples/member-adventurer-veteran) is retained as compatibility evidence only. A minimal
external API consumer is under [examples/provider-sdk](examples/provider-sdk).

## Public documentation

- [Stage/rank compatibility](docs/STAGES_RANKS.md)
- [Requirements and scopes](docs/REQUIREMENTS_SCOPES.md)
- [Costs and rewards](docs/COSTS_REWARDS.md)
- [Prestige lifecycle](docs/PRESTIGE_LIFECYCLE.md)
- [Currencies and entitlements](docs/CURRENCIES_ENTITLEMENTS.md)
- [Seasons and milestones](docs/SEASONS_MILESTONES.md)
- [Providers and integrations](docs/PROVIDERS_INTEGRATIONS.md)
- [Provider capability matrix](docs/PROVIDER_CAPABILITY_MATRIX.md)
- [Migrations, backups, and recovery](docs/MIGRATIONS_BACKUPS_RECOVERY.md)
- [Upgrade and rollback](docs/UPGRADE_ROLLBACK_V2.md)
- [Public API and SDK](docs/API_SDK.md)
- [Paper events](docs/EVENTS.md)

Historical V1 operator documents remain in the repository for the accepted transitional package, but they are not V2
installation or configuration instructions.

## Build and verify

```text
.\mvnw.cmd --no-transfer-progress clean verify
```

The shaded release candidate is `maddprestige-distribution/target/MaddPrestige-2.0.0-rc.1.jar`; the public API
coordinate is `net.maddkraft:maddprestige-api:2.0.0-rc.1`, and the aggregate CycloneDX SBOM is
`target/bom.json`. Phase 9 preserves the frozen numeric-Prestige backend while adding the completed Player and Staff
GUI surfaces. Owner deployment approval and production balance selection remain outside this checkpoint.
