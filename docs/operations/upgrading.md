# Upgrading and rolling back MaddPrestige

These instructions apply to the `2.0.0-rc.1` candidate on the qualified Java 25 / Paper 26.1.2 build 74 platform.
They prepare a safe release path and are not authorization to migrate the live MaddKraft server.

## Before any candidate upgrade

1. Stop Paper cleanly. Confirm no Java/Paper process still owns the server directory.
2. Preserve a read-only copy of the complete `plugins/MaddPrestige/` directory and the installed MaddPrestige JAR.
   Include the SQLite database, any `-wal`/`-shm` files, configuration revisions/pointer, locale files, and backups.
3. Record the current JAR byte size/SHA-256 and the last healthy `/maddprestige doctor` output.
4. Rehearse the upgrade in a disposable copy with the exact target JAR and dependency versions.
5. Never edit the database, schema-history rows, backup manifests, or active-configuration pointer to force acceptance.

Do not copy only a live SQLite main file. MaddPrestige's migration gate creates coordinated SQLite-native backup and
manifest pairs when a schema migration is required, but a host-level safety copy still requires a cleanly stopped full
plugin-data directory.

## Upgrading a supported V2 candidate

1. Remove the old MaddPrestige plugin JAR from the disposable server's `plugins/` directory. Do not load two versions.
2. Install `MaddPrestige.jar` and verify its published SHA-256 before startup.
3. Keep the copied plugin-data directory unchanged and start the exact qualified Paper/Java platform.
4. Wait for `Done`. A supported schema prefix migrates forward only after history/checksum validation, coordinated
   backup, independent integrity/configuration checks, and disposable restore rehearsal.
5. Run `/maddprestige status`, `/maddprestige doctor`, and `/maddprestige player`. Verify the exact active revision,
   numeric Prestige state, provider health, and absence of unresolved operations/configuration transitions.
6. Stop cleanly, start the unchanged directory again, and repeat the health/state checks before accepting the rehearsal.

MaddPrestige fails closed on future/gapped/duplicate/mismatched schema history, corrupt/truncated data, incompatible
configuration authority, or unresolved recovery evidence. Preserve the complete error and directory for diagnosis;
do not delete evidence to make startup continue.

## V1 and real-deployment boundary

The historical 1.x runtime and V2 use materially different configuration, persistence, and operation contracts. V1
player data is not imported: fresh V2 player state starts at Prestige 0 unless an administrator later uses an audited
V2 function. Keep old artifacts/data read-only only for archive or rollback. Release qualification proved
isolation/durability without executing a player mapping, and the populated schema-11 rehearsal archived stage-era
state before initializing numeric authority. Production remains a separate explicitly authorized deployment.

## Rollback and recovery

MaddPrestige database migrations are forward-only. An older binary must not be pointed at a database/schema/config
revision produced by a newer candidate unless that exact combination is documented as supported. If the upgrade must
be abandoned:

1. Stop Paper and preserve the failed rehearsal directory unchanged for diagnosis.
2. Create a new disposable directory from the complete pre-upgrade plugin-data copy and the exact prior JAR/dependency
   set. Do not overwrite either copy in place.
3. Start the restored copy, wait for `Done`, run status/player/Doctor checks, stop, and perform an unchanged restart.
4. Select a restored directory for service only after the full rehearsal is coherent.

For a migration-gated database backup, keep its `.sqlite` artifact and matching manifest together. A manifest/hash,
integrity, schema/history, active-configuration, and restore-rehearsal PASS is required; filename existence alone is not
proof of a usable backup. There is no public on-demand online-backup command in this candidate.

See [Installation](../getting-started.md), [Migrations, backups, and recovery](recovery.md), and
[Diagnostics and troubleshooting](troubleshooting.md).
