# Installing MaddPrestige V2

## Exact Phase 8D qualification platform

- Java 25
- Paper 26.1.2 build 74 stable
- LuckPerms 5.5.71
- the Phase 8D MaddPrestige `2.0.0-SNAPSHOT` candidate and its published checksum

These are the qualified versions, not a promise of compatibility with every later build. SQLite is embedded in the
MaddPrestige distribution. Do not add an external JDBC plugin or configure MySQL/MariaDB.

## Fresh installation

1. Create an empty Paper server directory and accept Paper's EULA.
2. Put the exact LuckPerms JAR and `MaddPrestige-2.0.0-SNAPSHOT.jar` in `plugins/`.
3. Verify both artifacts against the checksums supplied with the candidate.
4. Start Paper. Wait for `Done`, then confirm both plugins are enabled.
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
recovery workflow in [Migrations, backups, and recovery](MIGRATIONS_BACKUPS_RECOVERY.md).

## Deployment boundary

MaddPrestige 2.0 supports one Paper process using its local SQLite database. Network/proxy synchronization,
multi-process/shared-database use, MySQL, and MariaDB are unsupported and deferred post-2.0. Phase 9 must still qualify
the real deployment and migration before any production-ready claim.
