# Phase 8C implementation: SQLite persistence, migration, backup and recovery hardening

**Candidate date:** 2026-08-17

**Starting checkpoint:** `c71709ccd3e6c375bc6a9490856024979a66c17e`

**Backend policy:** SQLite is the sole MaddPrestige 2.0 production persistence backend. External SQL remains deferred post-2.0.

## Outcome

Phase 8C replaces the quiesced-file-only migration backup in production composition with a coordinated SQLite-native
backup authority. It validates the completed database independently, writes a checksum-bound manifest, rehearses a
restore in an isolated disposable directory through the same migration/validation path, and promotes only the complete
database-plus-manifest pair. A failed backup, validation, or rehearsal blocks migration and preserves the source and any
earlier accepted backup.

Migration qualification now covers fresh schema and every contiguous historical prefix from 1 through 10, using a
populated deterministic fixture wherever that schema can represent the data. Version 11 backfills missing zeroed
Prestige state for historical stage-only players so the accepted atomic stage-plus-Prestige invariant holds after
upgrade. UUIDs, exact decimal strings, state/config revisions, request/idempotency evidence, uncertainty,
`NEEDS_RECONCILIATION`, recovery events, stage-remap/reservation/lease authority, configuration documents and history
are compared after upgrade and unchanged restart.

Startup independently validates SQLite integrity, exact APPLIED migration history/checksums/descriptions, FAILED
attempt version/causal-time ordering, the complete migration-derived schema contract and readable representative rows before it publishes
the service. It then cross-checks the
filesystem active pointer against the latest APPLIED database configuration revision, recompiles the supported
progression schema, verifies every persisted stage remains reconstructible, and requires stage/Prestige row pairs.
Failure disables MaddPrestige before service publication without stopping Paper.

## Coordination and snapshot protocol

All connections opened by one production `SqliteFoundation` hold a fair shared coordination lock until close. The
backup authority takes the exclusive side of that lock. This drains already admitted connections, lets already queued
application work ahead of the exclusive request finish in fair order, and prevents later application connections from
starting until the SQLite snapshot has sealed. MaddPrestige 2.0 supports one Paper process; this JVM coordination is not
claimed as a multi-process/shared-database protocol.

Inside the exclusive boundary the Xerial JDBC driver's SQLite native backup API
(`SQLiteConnection.getDatabase().backup`) copies the complete authoritative database image to a unique
`.sqlite.partial` file. The implementation does not copy only the main live file, does not checkpoint by assumption,
does not execute a subprocess, and does not require `sqlite3`.

The partial artifact is forced, opened independently read-only, and subjected to:

- exact SQLite header validation;
- `PRAGMA integrity_check` requiring exactly one `ok` row;
- `PRAGMA foreign_key_check` requiring no row;
- exact contiguous APPLIED migration history and its known checksums/descriptions;
- rejection of unknown/malformed/duplicate/gapped history, FAILED attempts beyond the immediate next version and
  causally impossible FAILED timestamps;
- exact migration-prefix tables, columns and semantically relevant properties;
- correctness-enforcing UNIQUE/partial-UNIQUE indexes, foreign keys and table constraints;
- quote-aware SQL comparison that preserves exact quoted literals, doubled-quote escapes and quoted whitespace;
- a count plus representative read from every critical table that has rows;
- per-document and canonical configuration hashes plus parent/rollback-source links.

The accepted manifest is deterministic UTF-8 format version 1 and contains a canonical UUID backup ID, creation time, bounded reason,
source, source schema, optional active configuration revision, final artifact filename, streaming SHA-256, validation
PASS, rehearsal PASS and observed journal mode. Accepted revalidation binds the ID to `<backupId>.sqlite` and
re-observes artifact name, SHA, schema, active configuration, both PASS outcomes and journal mode. Creation time,
reason and source are historical metadata that cannot be re-derived and therefore receive syntax/bounds validation
only. Variable text is URL-safe Base64 encoded. No credentials or connection secrets are present.

The schema authority is not a second manually maintained table list. It creates a disposable in-memory SQLite schema
from the same canonical migration-history DDL and the exact claimed migration prefix, then compares the closed backup
against that reference. Table sets, `table_xinfo` column type/nullability/default/PK/hidden properties,
`foreign_key_list`, every UNIQUE/partial-UNIQUE index definition and normalized `CREATE TABLE` constraints must match.
Only unquoted case and external whitespace are normalized. Single-quoted literal content, whitespace inside quoted
tokens and doubled-quote escapes remain character-exact, so `'ACTIVE'` cannot compare equal to `'active'`.
Performance-only non-UNIQUE indexes remain outside the correctness gate. Representative reads remain useful data checks
but are never treated as structural proof.

Promotion uses new UUID destinations and `CREATE_NEW` manifest semantics. Database and manifest are first written as
partial files. After validation/rehearsal and checksum calculation, they are atomically moved where supported. A
manifest-promotion failure removes only the just-created unaccepted database. Cleanup is scoped to owned partial,
sidecar and rehearsal paths; existing accepted backups are never overwritten or retention-pruned in Phase 8C.

## Disposable restore rehearsal

Each candidate backup is copied into a newly created directory under the controlled backup root. A new
`SqliteFoundation` opens that copy, `MigrationRunner` applies the same complete Phase 8C chain if needed, and
`SqliteDatabaseValidator` repeats history, integrity, configuration and representative-row reads. The rehearsal must
reach schema 11 and close cleanly. The isolated directory is then deleted with a bounded owned-tree walk. The live
database is never selected as the rehearsal target.

## Migration failure semantics

The requested chain must be exactly 1..N. Preflight refuses any MaddPrestige schema without history, every unknown
history version, malformed fields, duplicate APPLIED record, APPLIED checksum/description mismatch, gap or non-prefix
state. FAILED attempts are valid for an already APPLIED version or the immediate next pending version only; later
known versions contradict the sequential protocol. A failure for applied version `v` may not follow its APPLIED
completion and, for `v > 1`, may not precede APPLIED `v-1`; an immediate-next failure may not precede the current
prefix completion. Equal timestamps remain valid. FAILED checksum/description values remain bounded, parseable
diagnostic evidence and intentionally are not compared to immutable migration definitions. Inspection closes before
backup after constructing an immutable snapshot of every ordered APPLIED/FAILED attempt plus the derived APPLIED
prefix. After the backup is accepted, migration reopens the database and requires the complete snapshot—not merely
the APPLIED projection—to be identical before initializing or applying anything.

Each migration's DDL/data statements and APPLIED record share one SQLite transaction. If execution fails and rollback
is confirmed, a separate FAILED audit record may be committed and retry is deterministic. If outcome cannot be
confirmed, no misleading FAILED record is invented. Commit acknowledgement and connection-restoration failures are
reported distinctly from a rolled-back migration. Existing schema checksums remain unchanged; version 11 is strictly
forward-only and idempotent because it inserts only missing Prestige rows.

## Compatibility and corruption policy

Stage and all Phase 3 document compilers reject unsupported future schema versions explicitly. With no filesystem
pointer, startup permits only migration metadata, append-only `mp_audit_log` rows and non-APPLIED configuration
attempts plus their immutable documents: none is consulted as live progression authority. It rejects APPLIED legacy
or V2 configuration, current/history currency or progression, requirement/manual, season, operation/recovery,
remap/lease and configuration-transition/reservation rows. With a pointer, startup also rejects a pointer not matching
latest APPLIED database history,
invalid/future active progression, persisted stages absent from active configuration, or one-sided stage/Prestige
state. It does not recreate, delete, reinterpret or repair authoritative player data.

Invalid header, truncation, deterministic bit flip, integrity/structure failure, missing manifest, checksum mismatch,
bad rehearsal and unsafe reason/path states all fail closed. Diagnostics identify the failing layer. Production startup
does not publish `MaddPrestigeService` until migration, database validation, configuration loading, compatibility
assessment, provider composition and recovery have completed.

## Preserved accepted behavior

No accepted operation plan, journal schema, request/durable identity, provider-generation fence, CAS rule,
stage-transition reservation/lease, uncertainty/reconciliation state, configuration publication ordering, optional
plugin boundary or LuckPerms ownership behavior was weakened. The real Paper qualification migrated populated schema
10 state, retained a `NEEDS_RECONCILIATION` operation with UNCERTAIN action and recovery evidence, backfilled a
stage-only player, then completed a real rank-up and Prestige. Its unchanged restart retained exact revisions, counts
and backup identity.

## Deliberate non-claims

- A63 remains Partial until Phase 9 qualifies the actual MaddKraft clone/deployment migration; Phase 8C supplies the
  complete synthetic/populated SQLite hardening candidate.
- A64 remains Later/deferred post-2.0.
- There is no MySQL/MariaDB repository, HikariCP external-SQL integration, three-backend parity, shared-database or
  multi-process claim.
- Phase 8D i18n/public docs/admin UX, Phase 8E performance/fault qualification, Phase 8F release hardening and Phase 9
  deployment work have not started here.

## Evidence index

- `docs/V2_PHASE8C_MIGRATION_MATRIX.md`
- `docs/V2_PHASE8C_BACKUP_RESTORE_EVIDENCE.md`
- `docs/V2_PHASE8C_FIXTURE_METADATA.md`
- `PHASE8C_PAPER_QUALIFICATION.log`
- `PHASE8C_VERIFY_1.log`
- `PHASE8C_VERIFY_2.log`
