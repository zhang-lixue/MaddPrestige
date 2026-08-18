# MaddPrestige V2 status

**Current phase:** Phase 8B owner-accepted checkpoint; Phase 8C scope prepared but implementation not started

**Last updated:** 2026-08-17

**Branch:** `v2/phase-8`

**Frozen pre-Phase-8 HEAD:** `7fc5c3532f0614634933ff66ee0e03bf55b8e5cf`

**Checkpoint scope:** documentation-only owner product-scope decision; accepted Phase 8B production behavior unchanged

## Outcome

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

- Phase 8C: SQLite Persistence, Migration, Backup & Recovery Hardening.
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
| A64 | Later | MySQL/MariaDB production support and semantic parity are explicitly deferred post-2.0; no support or acceptance claim is made |
| A65 | Partial | A separate live harness proves owner-attested SDK registration, admin-configured metric evaluation, unregister and rebind; the broader independent provider qualification matrix remains |
| A66 | Partial | Live Paper proves correlation, distinct request/durable IDs, zero-effect cancellation and stale successful PRE, plus durable rank/Prestige POST; the complete uncertainty/listener-fault/reentrancy matrix remains |
| A67 | Partial | Callback/linkage failure is isolated and unrelated registry state survives; broader dependency service/reload/operation matrix remains Phase 8E |
| A70 | Partial | Live generic LuckPerms/rank-up/Prestige composition now exists; the complete unbranded three-stage server exercise is not an 8B claim |
| A76 | Partial | Live setup is reachable, but Quick Start documentation and independent fresh-admin qualification remain Phase 8D |

The mechanically updated ledger is **54 Satisfied, 21 Partial and 1 Later**. A64 moved from `Partial` to `Later`; no
criterion became `Satisfied`. Phase 8B does not claim any Phase 8C–8F gate.

## Verification

The corrected implementation is sealed by two consecutive eight-module `clean verify` runs after source and
documentation completion. Exact totals and reproducible hashes are recorded in `PHASE8B_OWNER_REVIEW_SUMMARY.txt` and
the owner-review bundle.

## Owner handoff

- `docs/V2_PHASE8B_IMPLEMENTATION.md`
- `docs/V2_PHASE8B_API_INVENTORY.md`
- `docs/V2_PHASE8B_PAPER_API_INVENTORY.md`
- `docs/V2_PHASE8_FAILURE_REGISTER.md`
- `docs/V2_PHASE8B_STATIC_AUDIT.md`
- `docs/V2_ARCHITECTURE.md`
- `docs/V2_TRACEABILITY.md`
- `DECISIONS.md`
- `PHASE8B_OWNER_REVIEW_SUMMARY.txt`
- `PHASE8B_PAPER_QUALIFICATION.log`
- `target/MaddPrestige_Phase8B_Owner_Review.zip`

Phase 8B Correction Pass 2 remains owner-accepted. This scope-preparation checkpoint contains no Phase 8C implementation.
