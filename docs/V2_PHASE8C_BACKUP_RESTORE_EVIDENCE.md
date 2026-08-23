# Phase 8C SQLite backup and restore evidence

## Selected mechanism

Production uses the backup API exposed by the existing Xerial SQLite JDBC driver. The call is made on a native
`SQLiteConnection` inside `SqliteFoundation.withExclusiveSnapshotAccess`. This was selected over main-file copy
because WAL state is part of SQLite authority, and over an external `sqlite3` command because MaddPrestige must not
depend on a process or administrator-installed binary. `VACUUM INTO` was not needed because the bundled driver exposes
the native backup facility directly.

The fair foundation boundary drains all connections already admitted through the production foundation and fences new
ones until snapshot completion. Unit tests hold a real write transaction open and separately queue a writer; the backup
waits and its accepted image contains the committed states in deterministic order. The supported product topology is
one Paper process with one production foundation. No cross-process fencing claim is made.

## Acceptance sequence

1. Validate a printable bounded reason and controlled non-symlink source/backup root.
2. Allocate UUID-named `.sqlite.partial` and manifest paths with no overwrite.
3. Seal a SQLite-native snapshot inside the exclusive application boundary.
4. Force the partial database to stable storage.
5. Open it independently read-only; validate header, integrity, foreign keys, ordered history, the complete
   migration-derived schema contract, critical reads and
   configuration linkage.
   SQL contract comparison normalizes only unquoted case and external whitespace; quoted literal case, internal
   whitespace and doubled-delimiter escapes remain exact.
6. Copy it into a unique isolated rehearsal directory; open through `MigrationRunner`; reach schema 11; validate again;
   close and remove only the rehearsal directory.
7. Remove validation sidecars, stream SHA-256, write and force the format-1 manifest.
8. Promote the database then manifest atomically where supported; if manifest promotion fails, remove only the new
   unaccepted database.
9. Re-read manifest, hash and database independently before returning a verified result.

## Manifest fields

| Field | Meaning |
|---|---|
| `formatVersion` | Strict manifest format, currently 1 |
| `backupId` | Canonical UUID identity bound to `<backupId>.sqlite` |
| `createdAt` | UTC instant supplied by the production clock |
| `reasonBase64` | Bounded non-secret migration reason |
| `sourceBase64` | Diagnostic source path; no credentials |
| `sourceSchemaVersion` | Exact independently observed APPLIED prefix |
| `activeConfigurationRevisionBase64` | Latest APPLIED config revision if present |
| `databaseArtifact` | Exact final `.sqlite` filename |
| `sha256` | Completed artifact SHA-256 |
| `validationResult` | Must be `PASS` |
| `restoreRehearsalResult` | Must be `PASS` |
| `journalMode` | Journal mode observed on the independent snapshot |

Unknown, missing, duplicate or malformed fields reject the manifest. Backup UUID/artifact identity, SHA-256, schema
version, configuration revision, both PASS outcomes and observed journal mode are rechecked rather than trusted from
text. `createdAt`, reason and source are historical provenance that cannot be independently re-derived; they are
retained with strict parse/syntax/bounds checks rather than falsely presented as rebound facts.

## Deterministic tests

| Case | Result |
|---|---|
| Valid populated native snapshot | Accepted with exact manifest/hash and all representative rows readable |
| Active writer | Backup blocks until transaction/connection closes; committed value is present |
| Already queued writer | Fair coordination drains it before snapshot and fences later connection acquisition |
| Missing manifest / altered hash | Accepted-backup validation rejects |
| Invalid header / truncation / deterministic bit flip | Header, SQLite open or integrity validation rejects |
| Missing migration-5 actor UUID / another required column | Migration-derived column contract rejects |
| Readable table with wrong type/property | `table_xinfo`/table constraint contract rejects |
| Removed correctness partial-UNIQUE or foreign key | Index/FK/table contract rejects |
| Altered quoted partial-index or CHECK literal | Quote-aware contract rejects even when the database remains readable |
| Causally impossible FAILED time | Ordered-history validation rejects; equality at a legal boundary remains valid |
| Any APPLIED/FAILED attempt added while backup seals | Full immutable ledger comparison rejects the now-stale plan |
| Manifest UUID/artifact or journal-mode tamper | Accepted-manifest rebinding rejects |
| Failed restore rehearsal | No artifact/manifest promotion |
| Unsafe blank/control reason | Reject before artifact creation |
| Failed later backup | Live source hash and previous known-good database/manifest hashes remain unchanged |

## Real Paper evidence

The first qualified Phase 8C boot started from a populated schema-10 database. Production created a unique native backup
whose manifest reported source schema 10, validation PASS and rehearsal PASS. The first-party harness invoked
`validateAcceptedBackup` through the production class loader, verified the migrated live database with
`PRAGMA integrity_check`, and completed player operations. On unchanged restart the accepted backup count was exact and
the same source-schema-10 artifact validated again; no new migration backup was created.

The disposable runtime, third-party Paper/LuckPerms/PlaceholderAPI JARs, world data and live database are excluded from
the owner-review bundle. Only sanitized assertions and first-party harness source are retained.
