# Getting started with MaddPrestige

## Qualified stable platform

- Java 25
- Paper 26.1.2 build 74 stable
- LuckPerms 5.5.71 only when the active configuration references its optional rewards/conditions
- MaddPrestige `2.0.0` and its published checksum

These are the qualified versions, not a promise of compatibility with every later build. SQLite is embedded in the
MaddPrestige distribution. Do not add an external JDBC plugin or configure MySQL/MariaDB.

## Fresh installation

1. Create an empty Paper server directory and accept Paper's EULA.
2. Put the plugin in `plugins/MaddPrestige.jar`; add the exact LuckPerms JAR only if the release configuration
   explicitly references it.
3. Verify every installed artifact against the checksums supplied with the release.
4. Start Paper. Wait for `Done`, then confirm MaddPrestige and every configured optional provider are enabled.
5. Run `/maddprestige status` and `/maddprestige setup discover` as an operator.
6. Continue with [Quick Start](QUICK_START.md). The first boot is intentionally dormant until a validated revision is
   applied.

MaddPrestige owns these paths beneath `plugins/MaddPrestige/`:

- `maddprestige-v2.sqlite` — the live SQLite database;
- `backups/` — migration-gated native SQLite backup/manifest pairs;
- `configuration/` — immutable configuration artifacts and active pointer;
- `locale.yml` — server-global locale selection;
- `locales/` — administrator-supplied UTF-8 locale catalogs.

Do not edit the database or active pointer. Do not copy a live WAL database file as a backup. Use the shutdown and
recovery workflow in [Migrations, backups, and recovery](operations/recovery.md). For an update or
rollback, follow [Upgrading and rolling back MaddPrestige](operations/upgrading.md) in a disposable rehearsal first.

## Deployment boundary

MaddPrestige 2.0 supports one Paper process using its local SQLite database. Network/proxy synchronization,
multi-process/shared-database use, MySQL, and MariaDB are unsupported and deferred post-2.0. Release qualification
covered an isolated 42-plugin server copy and a retained populated schema-11 upgrade rehearsal. Neither result is
permission to deploy live. Follow the [deployment runbook](operations/deployment.md) only after explicit approval.
