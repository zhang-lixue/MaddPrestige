# SQLite migrations, backups, and recovery

SQLite is the sole supported MaddPrestige 2.0 production backend. It is for one Paper process and one local server
directory. MySQL/MariaDB, shared databases, and multi-process/network operation are deferred post-2.0.

## Automatic migration gate

At startup MaddPrestige validates the exact ordered/checksummed schema history. Before a required migration it fences
application connections, drains admitted work, creates a unique SQLite-native snapshot, writes a SHA-256-bound UTF-8
manifest, independently checks integrity/foreign keys/schema/history/configuration, and rehearses an isolated restore.
Only a complete database/manifest pair with PASS validation and rehearsal is promoted in
`plugins/MaddPrestige/backups/`. Migration then rechecks the complete history snapshot before proceeding.

Future/gapped/duplicate/mismatched history, corrupt/truncated databases or backups, missing pointer authority, and
failed rehearsal block service publication. MaddPrestige does not opportunistically mark a partial schema current.

Migration 12 is the numeric-Prestige activation boundary. It labels legacy operation/history rows, removes stage-era
rows from active authority, preserves audit/provider-owned data, and initializes active numeric Prestige at P0 without
mapping stage position. The path was qualified backup-first against a retained populated schema-11 copy, including an
idempotent restart and schema-11 rollback restore. See [Release acceptance](../acceptance.md).

## Operator workflow

1. Stop Paper cleanly before host-level backup or restore work.
2. Preserve the entire `plugins/MaddPrestige/` directory, not only `maddprestige-v2.sqlite`.
3. Never copy only a live WAL database and never edit SQLite/manifest/pointer files.
4. Rehearse restoration into a disposable server directory with the exact candidate JARs first.
5. Confirm startup, `/maddprestige status`, `/maddprestige player`, and `/maddprestige doctor` before selecting a
   restored directory for service.

The current public runtime exposes migration-gated backups, not an on-demand online-backup command. Use server/host
backup automation only around a clean shutdown until an explicit public operator command is released.

Uncertain external operations and unresolved configuration transitions stay visible for reconciliation. Do not delete
journal/history rows or force pointer files to hide them. Consult [Troubleshooting](troubleshooting.md) and
retain the exact log/diagnostic code for review.
