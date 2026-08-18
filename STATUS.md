# MaddPrestige V2 status

**Current phase:** Phase 8C Owner Review Correction Pass 2 candidate complete, awaiting owner review; Phase 8D not started

**Last updated:** 2026-08-18

**Branch:** `v2/phase-8`

**Frozen pre-Phase-8 HEAD:** `7fc5c3532f0614634933ff66ee0e03bf55b8e5cf`

**Candidate scope:** SQLite persistence, migration, backup and recovery hardening; accepted Phase 1-8B behavior retained

## Outcome

Phase 8C adds the production SQLite-native backup authority selected from the existing Xerial driver. A fair
application-connection fence drains/fences MaddPrestige work while the backup API seals a unique snapshot; a partial
artifact cannot be promoted until independent header/integrity/foreign-key/schema/history/configuration validation,
streaming SHA-256 and an isolated same-path restore rehearsal pass. Format-1 durable manifests identify reason/source,
source schema, active configuration revision, artifact, hash, validation/rehearsal outcomes and journal mode. Accepted
revalidation binds the UUID to its canonical artifact and re-observes every derivable field, including journal mode. Failed
attempts preserve both live source and prior accepted backups.

The exact migration chain is now schema 1 through 11. Populated fixtures exercise fresh and every historical prefix,
then unchanged restart. Migration 11 backfills zero Prestige state only for historical stage-only players, preserving
the accepted atomic stage/Prestige invariant. Validation reconstructs the claimed migration prefix and compares every
MaddPrestige table, column/property, correctness UNIQUE/partial-UNIQUE authority, foreign key and database-enforced
table constraint. Its quote-aware SQL canonicalizer normalizes only unquoted case and external whitespace; quoted
literal content, escaped quotes and internal whitespace remain exact correctness authority. Future, missing, gapped,
duplicate or malformed history, impossible FAILED-attempt version/timestamp ordering, and APPLIED
checksum/description mismatch fail closed. FAILED checksum/description text remains diagnostic rather than immutable
migration-definition authority. The complete ordered APPLIED/FAILED attempt ledger is snapshotted before backup and
must be unchanged afterward. Startup validates SQLite before service publication and rejects every
pointer-dependent authority row when the filesystem pointer is absent, rather than repairing or reinterpreting it.

The shaded distribution passed a two-boot Paper 26.1.2 process qualification from a populated schema-10 fixture: 8/8
first-boot assertions and 3/3 unchanged-restart assertions. It independently validated the native backup and rehearsal,
preserved existing player plus uncertainty/recovery evidence, backfilled a stage-only player, completed real rank-up and
Prestige with distinct request/durable identities, and retained exact revisions/counts on restart.

Phase 8B composes the accepted V2 engines into the active Paper runtime and presents the owner-accepted Phase 8 API
candidate. `MaddPrestigeService` is published only through Paper `ServicesManager`, after migrations, provider
composition, configuration validation and pending-operation recovery. It now exposes side-effect-free rank-up/Prestige
evaluation, stage catalog, requirement progress, currencies and active-season reads in addition to player progress and
mutations. Operational failures are bounded structured values; request correlation is distinct from optional durable
journal identity. Shutdown unregisters first and rejects new work without cancelling accepted durable operations.

The full Phase 6 command, completion, GUI, configuration administration, setup, doctor/why/player and manual-Prestige
surfaces are live-bound. Admin-applied immutable configuration revisions are checksum/inventory/history verified and
hydrated with the exact same identity on restart; seed templates never silently become active. Canonical apply and
provider lifecycle recompose optional integrations, Placeholder, provider activation and runtime caches in deterministic
order while operations remain fail-closed. LuckPerms is resolved from the live Paper service and registered as the
production rank adapter; no group or hierarchy creation path was added.

An external provider registers a stable `ProviderDeclaration` through Paper `ServicesManager`. MaddPrestige cross-checks
the registered service owner with the implementation class's actual providing plugin, rejects normalized namespace
collisions, caches validated metadata outside registry locks, assigns the internal generation, and applies deadline plus
live cancellation on a bounded executor. Duplicate/ambiguous metadata and malformed result maps fail closed. Installed
plugins remain inside Paper's trusted-server boundary. Public manual-progress submission remains omitted pending an
owner-approved authority model.

Stable Paper events now exist for pre/post rank-up, pre/post Prestige, configuration applied and provider-health changed.
One ingress request UUID is retained through authorization, PRE, POST and result while the durable operation UUID remains
separate. PRE delivery occurs synchronously on the Paper thread after canonical authorization but before initialization
or journal insertion; cancellation/listener failure is zero-effect, and exact virtual-or-durable player/config/provider
authority is revalidated before atomic initialization and again before journaling. POST follows durable terminal state;
listener failure is isolated. Same-player recursive mutation returns a structured conflict.

Live progress contexts now use durable stage, current/lifetime Prestige and active-season state for scaling, catch-up and
all accepted scope identities. Unknown-player reads/PRE use virtual initial state; atomic stage/Prestige initialization
occurs only after PRE succeeds, so cancellation/listener failure creates no player state or operation effects.

Manual progress uses one shared single-flight drain, bounded 1,024-row repository batches, request coalescing,
version-aware dirty clearing, failure retention/retry and bounded shutdown. Persistence failure reports `DEGRADED`,
successful retry restores `AVAILABLE`, and repeated failure logs are rate-limited.

A separate first-party harness qualified the actual shaded plugin in two disposable Paper 26.1.2 boots: 22/22 fresh
setup/service/provider/event/recomposition assertions and 3/3 exact-revision restart assertions passed. The run includes
request/PRE/POST/result identity, distinct durable IDs, stale-PRE zero materialization and exact two-key provider output.
Third-party
JARs remain outside the review bundle; sanitized evidence is `PHASE8B_PAPER_QUALIFICATION.log`.

## Accepted API candidate

- Phase 8A: 76 public top-level API types.
- Phase 8B: 109 public top-level API types.
- The owner accepts the present 46-type Bukkit-free SDK Stable candidate and the six-type Paper Stable event candidate.
- The SDK inventory also classifies 5 Experimental, 17 Internal/Should Not Be Public and 41 Legacy/Pending Removal
  types.
- The exact SDK and Paper event `javap -public` inventories are `docs/V2_PHASE8B_API_INVENTORY.md` and
  `docs/V2_PHASE8B_PAPER_API_INVENTORY.md`.
- SDK and dynamically discovered Paper Stable leakage tests reject every non-allowlisted signature type; only the
  minimum Bukkit event contract is allowed on the Paper surface.
- These are approved Phase 8 candidates, not a frozen compatibility baseline. Final baseline freeze remains a Phase 8
  completion/release-hardening responsibility.
- No external SDK nullness-annotation dependency is added merely for Phase 8B. Existing explicit `Optional` contracts,
  runtime validation and Javadocs remain authoritative; Paper-specific JetBrains annotations required by Paper
  conventions may remain.

## Canonical remaining Phase 8 decomposition

- Phase 8C: SQLite Persistence, Migration, Backup & Recovery Hardening — Owner Review Correction Pass 2 candidate
  complete, awaiting owner review.
- Phase 8D: i18n, a generic example, public documentation and admin UX.
- Phase 8E: performance plus fault/dependency qualification.
- Phase 8F: packaging and release hardening, including final compatibility-baseline responsibility.

The owner decision dated 2026-08-17 makes SQLite the only officially supported MaddPrestige 2.0 production persistence
backend. Phase 8C retains the full SQLite correctness burden: coordinated backup, verified metadata/checksums/integrity,
restore rehearsal, populated migrations, interruption and schema-history failure handling, corruption diagnostics,
database/config compatibility, and exact populated-state restart/recovery qualification without weakening any accepted
transaction, journal, lease, uncertainty, reconciliation or configuration-publication invariant. Existing
backend-neutral boundaries remain for a proper future implementation.

MySQL/MariaDB production repositories, HikariCP integration solely for them, three-backend parity, external row-lock and
deadlock semantics, external-DB outage/failover qualification, and shared-database/multi-process deployment support are
deferred post-2.0 unless explicitly re-authorized. This is a scope decision, not support evidence and not an A64 pass.

## Acceptance classification

| Acceptance | Current classification | Phase 8B evidence / remaining gate |
|---|---|---|
| A02 | Partial | Complete Phase 6 setup/admin/GUI composition is reachable live; independent Quick Start usability remains Phase 8D |
| A61 | Partial | Durable internal/offline paths and live async LuckPerms composition exist; complete provider-by-provider live offline qualification remains |
| A62 | Partial | Manual backpressure/health and cache-only rendering are corrected; per-player virtual-thread/SQLite fan-out still needs real Paper load/TPS qualification in Phase 8E |
| A63 | Partial | Populated fresh/prefix migration, native backup, restore rehearsal, corruption/history failures and real Paper restart are qualified; Phase 9 retains actual MaddKraft clone/deployment migration qualification |
| A64 | Later | MySQL/MariaDB production support and semantic parity are explicitly deferred post-2.0; no support or acceptance claim is made |
| A65 | Partial | A separate live harness proves owner-attested SDK registration, admin-configured metric evaluation, unregister and rebind; the broader independent provider qualification matrix remains |
| A66 | Partial | Live Paper proves correlation, distinct request/durable IDs, zero-effect cancellation and stale successful PRE, plus durable rank/Prestige POST; the complete uncertainty/listener-fault/reentrancy matrix remains |
| A67 | Partial | Callback/linkage failure is isolated and unrelated registry state survives; broader dependency service/reload/operation matrix remains Phase 8E |
| A70 | Partial | Live generic LuckPerms/rank-up/Prestige composition now exists; the complete unbranded three-stage server exercise is not an 8B claim |
| A76 | Partial | Live setup is reachable, but Quick Start documentation and independent fresh-admin qualification remain Phase 8D |

The mechanically audited ledger remains **54 Satisfied, 21 Partial and 1 Later**. A63 remains `Partial` because Phase 9
retains the live MaddKraft clone/deployment gate; A64 remains `Later`. No Phase 8D-8F criterion is claimed.

## Verification

The Phase 8C candidate is sealed by two consecutive eight-module `clean verify` runs with no source or documentation
edits between them. Their exact totals, module counts and reproducible hashes were then recorded in
`PHASE8C_OWNER_REVIEW_SUMMARY.txt` and the owner-review bundle.

## Owner handoff

- `docs/V2_PHASE8C_IMPLEMENTATION.md`
- `docs/V2_PHASE8C_MIGRATION_MATRIX.md`
- `docs/V2_PHASE8C_BACKUP_RESTORE_EVIDENCE.md`
- `docs/V2_PHASE8C_FIXTURE_METADATA.md`
- `docs/V2_PHASE8C_FILE_MANIFEST.md`
- `docs/V2_PHASE8_FAILURE_REGISTER.md`
- `docs/V2_PHASE8B_STATIC_AUDIT.md`
- `docs/V2_ARCHITECTURE.md`
- `docs/V2_TRACEABILITY.md`
- `DECISIONS.md`
- `PHASE8C_OWNER_REVIEW_SUMMARY.txt`
- `PHASE8C_PAPER_QUALIFICATION.log`
- `PHASE8C_VERIFY_1.log`
- `PHASE8C_VERIFY_2.log`
- `target/MaddPrestige_Phase8C_Owner_Review.zip`

Phase 8B Correction Pass 2 remains owner-accepted and unchanged. Phase 8C Correction Pass 2 is a review candidate,
not owner-accepted.
Phase 8D has not started.
