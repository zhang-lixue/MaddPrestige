# Phase 8C SQLite migration qualification matrix

**Current schema:** 11

**Supported historical prefixes:** 0 (fresh), 1, 2, 3, 4, 5, 6, 7, 8, 9 and 10

**Execution authority:** `SqliteMigrations.phaseEightC()` and `MigrationRunner`

## Prefix matrix

| Input | Populated authority represented before upgrade | Expected result | Deterministic evidence |
|---|---|---|---|
| Fresh / no history | None | Create 1..11; valid empty schema; unchanged restart no-op | `upgradesEverySupportedPrefixAndRestartsIdempotently` |
| 1 | Config reference, operation/action journal, currency account, audit | Preserve UUID/idempotency/config/exact decimals; apply 2..11 | same parameterized prefix loop |
| 2 | Plus player stage row and state/config revision | Preserve stage identity and revision; migration 11 supplies Prestige pair | same loop; migrated restart regression |
| 3 | Plus baselines, latches and manual progress | Preserve scope/fingerprint/value/version evidence exactly | same loop |
| 4 | Plus Prestige, ledger, stage/Prestige history, season and recovery structures | Preserve exact current/lifetime counters, decimal ledger and recovery | same loop |
| 5 | Schema-only historical prefix | Apply 6..11 without reinterpretation | same loop |
| 6 | Plus exact configuration revision/documents and hashes | Preserve revision linkage, documents, canonical hash and latest APPLIED identity | same loop |
| 7 | Plus remap operation and entries | Preserve remap UUID, state/config revision and entry identity | same loop |
| 8 | Legacy target-only lease states | Deterministically retain live nonterminal authority, reject orphan semantics, upgrade columns | prefix loop plus `upgradesLegacyTransitionLeasesWithoutRetainingOrphans` |
| 9 | Source/target lease ownership and adoption evidence | Preserve lease token/owner/participation state | prefix loop |
| 10 | Complete configuration transition scope and reservations | Preserve transition owner/scope/reservations; backfill only missing Prestige row | prefix loop plus real Paper schema-10 boot |
| 11 | Already current | No backup or migration churn; exact repeated startup | prefix loop, Paper unchanged restart |

The reusable `SqlitePhase8cFixture` inserts only structures available at the requested prefix. It uses fixed UUIDs and
timestamps and verifies equality after migration and reopen. Exact decimal values remain canonical text; operation
request/idempotency evidence is not rewritten into durable operation identity.

## Failure matrix

| Failure class | Required result | Evidence |
|---|---|---|
| Requested chain missing a version | Reject before DB mutation | `rejectsMissingMigrationInRequestedChain` |
| Applied history gap/non-prefix | Reject exact expected/found version | `rejectsGapInAppliedHistory`, `rejectsNewerAppliedVersionWhenOlderVersionIsMissing` |
| Unknown/future APPLIED or FAILED version | Reject; never reinterpret future schema | `rejectsUnknownAppliedVersion`, `rejectsUnknownFutureFailedHistory` |
| Known FAILED version beyond immediate next pending | Reject impossible/ambiguous sequential history | `rejectsFailedAttemptBeyondNextPendingMigration`, independent validator case |
| FAILED within APPLIED prefix or immediate next | Accept diagnostic retry history; do not require definition checksum/description | `acceptsFailedAttemptsInAppliedPrefixAndAtNextPendingMigration` |
| FAILED after same-version APPLIED or before prior-version APPLIED | Reject causally impossible sequential evidence; equality is allowed | `rejectsFailedAttemptAfterSameVersionApplied`, `rejectsNextFailureBeforePriorVersionApplied`, `acceptsEqualFailedAttemptBoundaries` and independent-validator case |
| Complete FAILED/APPLIED ledger changes while backup seals | Reject stale plan even when the APPLIED prefix is unchanged | `rejectsCompleteAttemptLedgerChangeDuringBackup` |
| APPLIED checksum mismatch | Reject before backup/apply | `rejectsAppliedChecksumMismatch` |
| APPLIED description or malformed UUID/time/result/hash | Reject malformed/inconsistent history | `rejectsMalformedAndInconsistentHistoryMetadata` |
| Duplicate APPLIED version | Reject even if a damaged DB bypassed the unique index | `rejectsDuplicateAppliedHistory` |
| MaddPrestige schema without history | Reject ambiguous replay | `refusesPartialSchema` |
| Backup failure on fresh or prefix DB | Leave schema/history untouched | `backupFailureLeavesPreexistingSchemaIntact`, `backupFailureAfterAppliedPrefixLeavesHistoryUntouched` |
| Migration statement failure | Roll back DDL/data/APPLIED; record truthful FAILED; permit deterministic retry | `failedMigrationRollsBackAndRestartsDeterministically`, `recordsRepeatedFailuresThenSuccessfulRetry` |
| Rollback/commit acknowledgement/restoration ambiguity | Distinguish committed, rolled back and uncertain outcomes; do not invent result | four focused `SqliteMigrationTest` failure tests |
| Structurally incomplete/malformed current DB | Migration-derived table/column/property/index/FK/constraint authority rejects | five OR8C-01 adversarial `SqliteBackupServiceTest` cases |
| Quoted partial-index/CHECK literal changes case or whitespace | Reject; quoted SQL content is character-exact correctness authority | three OR8C-06 canonicalizer/schema tests, including two uppercase `ACTIVE` rows admitted by the corrupt lowercase predicate |
| Missing-pointer/stale/future/incompatible configuration authority | Reject before runtime publication | OR8C-01/02 `StartupPersistenceCompatibilityTest` and future-schema compiler tests |

## Regression assertions

- migrated player can rank up and Prestige after reopen;
- unknown player initialization still atomically creates stage plus Prestige state;
- `NEEDS_RECONCILIATION`, UNCERTAIN action and recovery rows survive without replay;
- request and durable operation identities remain distinct in the Paper flow;
- stage remap, declared transition scope, active reservations and leases remain readable and exact;
- canonical active configuration identity remains the latest reconstructible APPLIED revision;
- a second completed migration run is a no-op and creates no new migration backup.

The process qualification used the shaded distribution on Paper 26.1.2 build 74. A first-party utility transformed a
cleanly stopped populated schema-11 fixture into an explicit schema-10 historical input, then the production startup
path generated/validated/rehearsed a backup and applied version 11. The second boot was unchanged.
