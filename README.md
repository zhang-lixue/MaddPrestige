# MaddPrestige 2.0

MaddPrestige V2 is a generic, provider-driven progression and Prestige platform for a single Paper server. Ordered
stage IDs are independent of display names and external rank groups; requirements, costs, rewards, lifecycle rules,
currencies, entitlements, seasons, and optional integrations compile into one immutable active configuration revision.

This repository is a Phase 8 release candidate, not a production-ready or GA release. Production deployment and real
server migration qualification remain Phase 9.

## Supported Phase 8D candidate

- Java 25.
- Paper 26.1.2 build 74 stable.
- LuckPerms 5.5.10 for a rank-projected profile.
- SQLite is the sole MaddPrestige 2.0 production persistence backend.
- One Paper process and one server directory per database.
- MySQL/MariaDB and shared-database/multi-process operation are deferred post-2.0.

MaddPrestige does not create LuckPerms groups. The administrator owns those groups and their display/prefix policy.
Optional providers are required only when an active configuration references them.

## Start here

1. [Install the candidate](docs/INSTALLATION_V2.md).
2. Follow the exact [Member → Adventurer → Veteran Quick Start](docs/QUICK_START.md).
3. Keep [commands and permissions](docs/COMMANDS_PERMISSIONS.md) and
   [diagnostics](docs/DIAGNOSTICS_TROUBLESHOOTING.md) available to operators.
4. Read [configuration](docs/CONFIGURATION.md) before making later revisions.

The frozen generic profile is under [examples/member-adventurer-veteran](examples/member-adventurer-veteran). A
minimal external API consumer is under [examples/provider-sdk](examples/provider-sdk).

## Public documentation

- [Stages and ranks](docs/STAGES_RANKS.md)
- [Requirements and scopes](docs/REQUIREMENTS_SCOPES.md)
- [Costs and rewards](docs/COSTS_REWARDS.md)
- [Prestige lifecycle](docs/PRESTIGE_LIFECYCLE.md)
- [Currencies and entitlements](docs/CURRENCIES_ENTITLEMENTS.md)
- [Seasons and milestones](docs/SEASONS_MILESTONES.md)
- [Providers and integrations](docs/PROVIDERS_INTEGRATIONS.md)
- [Migrations, backups, and recovery](docs/MIGRATIONS_BACKUPS_RECOVERY.md)
- [Public API and SDK](docs/API_SDK.md)
- [Paper events](docs/EVENTS.md)

Historical V1 operator documents remain in the repository for the accepted transitional package, but they are not V2
installation or configuration instructions.

## Build and verify

```text
.\mvnw.cmd --no-transfer-progress clean verify
```

The shaded candidate is `maddprestige-distribution/target/MaddPrestige-2.0.0-SNAPSHOT.jar`; the aggregate CycloneDX
SBOM is `target/bom.json`. Do not publish a locally built candidate as production-ready before release hardening and
Phase 9 qualification.
