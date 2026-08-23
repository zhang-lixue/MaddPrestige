# MaddPrestige V2 status

**Current phase:** Phase 8D owner-accepted after Independent Owner Review 7 and in publication; Phase 8E not started

**Last updated:** 2026-08-23

**Branch:** `v2/phase-8`

**Frozen pre-Phase-8 HEAD:** `7fc5c3532f0614634933ff66ee0e03bf55b8e5cf`

**Candidate scope:** i18n, exact generic example, public documentation and command-driven admin UX; accepted Phase 1-8C behavior retained

## Outcome

Phase 8D supplies one server-global UTF-8 locale selected by `locale.yml`, with built-in `en_US` as the complete
fallback catalog. Player/admin-facing command, setup, Why, GUI and Phase 7 presentation carries stable semantic message
references to the Paper boundary; it does not wrap precomposed English lines in generic catalog entries. Dynamic values
are named bounded data, not markup authority. Reload parses and validates a complete
snapshot before one atomic publication; an invalid catalog retains the previous known-good snapshot. A missing selected
key falls back to built-in English, while a key absent from both catalogs produces `[message:<key>]`.

Correction Pass 3 removed the lossy authorization and code-fragment classification present after the rejected Pass 2
candidate. Every real
RankUp/Prestige rejection now carries a stable `AuthorizationBlockerKind`, structured facts and secondary diagnostic
text. Why, executable-plan previews and true no-plan preview/simulation rejection use that same structured data; public
meaning is never inferred from diagnostic English. Owner Review 4 accepted that architecture and mapping completeness,
then found the smaller generic administration families were not semantically correct for every exact code.

Correction Pass 4 audits all 84 known public `AdministrationException` codes against their production throw sites and
gives each one a dedicated explicit semantic identity, catalog-owned explanation/remediation and applicable immutable
facts. Server-issued acknowledgement, no-acknowledgement-required, projected group, duplicate requirement, unknown
Prestige stage/metric, missing document, rollback/history and remap/transition failures now describe their actual
condition and action. Owner Review 5 found that exact code mapping alone still collapsed materially different
occurrences of `config.apply.failed` and `config.validation.blocked`.

Correction Pass 5 mechanically inventories 111 literal production exception sites: 84 codes, including 19 codes with
multiple sites. Seventeen multi-site codes share compatible condition/consequence/remediation contracts. The two
overloaded codes now carry five immutable `AdministrationSemanticVariant` values for unchanged/restored/reconciliation
apply outcomes and acknowledgement/apply validation contexts. The public catalog also recognizes list and map
collections and reports persisted-reference change as the cause of stale remap previews. Real branch, occurrence
inventory and alternate-catalog tests prove the exact recovery/action context without reading English diagnostics. The
safe unknown fallback remains, while known codes cannot select it. Catalog rendering also distinguishes
maximum, cooldown, inactive ladder, illegal target, missing cost, provider, preflight, projection, state, requirement
and boundary causes. Command usage is also a catalog template with canonical syntax as data. Exhaustive mapping,
real-service, distinctness, preflight-detail, no-plan, exact-administration, alternate-catalog and static-architecture
tests close OR8D-07 through OR8D-10 without changing the accepted lifecycle, locale-containment or setup-parity work.

Owner Review 6 accepted that complete multi-source correction and found OR8D-11: several catalog messages still drifted
from the source condition of otherwise single-source identities. Correction Pass 6 exhaustively enumerates all 65
single-source codes, so the accepted 19-code multi-source register plus the new register account for all 84 codes and
111 literal occurrences. `config.preview.stale` now means only draft hash/version drift and remains distinct from active
`config.revision.stale`; rollback provenance failure names the draft; snapshot preparation explicitly fails before
activation with current active configuration unchanged. The same audit corrected actor-bound confirmation ownership,
unknown confirmation terminal states, lossless stage add/remove guidance, absent remap-plan removal and the actual GUI
missing-replacement condition. A source-walk test fails on any unreviewed single-source code. Real defensive branches,
alternate catalogs and internal-English prohibitions pass without changing stable codes or production Java control flow.

Independent Owner Review 7 accepted the complete Phase 8D candidate with no remaining acceptance-blocking defect.
OR8D-01 through OR8D-11 are closed at the Phase 8D boundary.

The public Quick Start uses the existing Phase 6 command-driven setup session rather than a second setup architecture.
It creates the frozen generic `member` -> `adventurer` -> `veteran` profile, selects externally created LuckPerms groups
`Member`, `Adventurer` and `Veteran`, configures 60/180-second current-Prestige Paper play-time requirements, previews,
acknowledges and applies the canonical configuration, then runs Doctor. MaddPrestige never creates the external groups.
The public example contains no cost, reward or optional-provider dependency.

A fresh disposable Paper 26.1.2 build 74 process with the independently audited LuckPerms 5.5.71 artifact passed 29/29 first-boot assertions and 3/3
unchanged-restart assertions. It used the documented setup commands, proved exact Why deficits and zero-effect blocked
attempts, completed Member -> Adventurer -> Veteran -> Prestige once, retained the Paper-owned statistic, reset only the
current-Prestige baseline, and exercised an offline UUID path. Before Adventurer, the first stable read established exact
durable stage/Prestige/baseline state and one journaled, externally verified real LuckPerms `Member` projection. Repeated
initialization made no duplicate mutation; new Why, preview and blocked-operation identities each established real
Member state. Restart recovered exact configuration, state, history, LuckPerms projection and healthy Doctor status.
The automated run satisfies A02 and A70. It does not fabricate an independent blind human. By owner decision, A76
remains Partial and is intentionally deferred until the final Phase 8 release candidate is frozen; the published
owner/manual protocol will then qualify the actual release candidate.

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
all accepted scope identities. Authoritative reads, Why, previews and blocked operations establish the durable initial
state and journaled real rank projection before returning. An eligible operation still authorizes against virtual initial
state and performs that establishment only after PRE succeeds and authority is revalidated, so PRE cancellation or
listener failure creates no player state, projection, journal or other effect.

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

- Phase 8C: SQLite Persistence, Migration, Backup & Recovery Hardening — owner-accepted at checkpoint
  `d481a9db7cd67108ff77f97e2d64d737e9096276`.
- Phase 8D: i18n, a generic example, public documentation and admin UX — implementation/qualification candidate
  complete, awaiting owner review.
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

| Acceptance | Current classification | Current evidence / remaining gate |
|---|---|---|
| A02 | Satisfied | A fresh real-Paper process used the documented command-driven setup session to create, preview, acknowledge, apply and diagnose the exact active three-stage ladder; no second GUI architecture is required |
| A45 | Satisfied | Doctor command rendering now carries catalog-owned consequence/explanation plus affected path/code and safe actionable remediation for every diagnostic family; command and Paper rendering tests prove the public boundary |
| A46 | Satisfied | Rank-target and other actionable Doctor findings render stable semantic remediation identities without exposing arbitrary stored exception/diagnostic prose |
| A47 | Satisfied | RankUp/Prestige authorization emits exhaustive typed blocker identities and facts; the real command/service Why path distinguishes inactive state, illegal target, missing cost, maximum, cooldown, provider, cost-preflight, projection, requirements and boundaries without parsing diagnostic prose |
| A61 | Partial | Durable internal/offline paths and live async LuckPerms composition exist; complete provider-by-provider live offline qualification remains |
| A62 | Partial | Manual backpressure/health and cache-only rendering are corrected; per-player virtual-thread/SQLite fan-out still needs real Paper load/TPS qualification in Phase 8E |
| A63 | Partial | Populated fresh/prefix migration, native backup, restore rehearsal, corruption/history failures and real Paper restart are qualified; Phase 9 retains actual MaddKraft clone/deployment migration qualification |
| A64 | Later | MySQL/MariaDB production support and semantic parity are explicitly deferred post-2.0; no support or acceptance claim is made |
| A65 | Partial | A separate live harness proves owner-attested SDK registration, admin-configured metric evaluation, unregister and rebind; the broader independent provider qualification matrix remains |
| A66 | Partial | Live Paper proves correlation, distinct request/durable IDs, zero-effect cancellation and stale successful PRE, plus durable rank/Prestige POST; the complete uncertainty/listener-fault/reentrancy matrix remains |
| A67 | Partial | Callback/linkage failure is isolated and unrelated registry state survives; broader dependency service/reload/operation matrix remains Phase 8E |
| A68 | Satisfied | Stable semantic references preserve 51 typed blocker identities and occurrence-correct administration semantics across 111 sites/84 codes: 19 multi-source and all 65 single-source codes; five typed variants distinguish overloaded apply/validation states, applicable facts remain data, no English or diagnostic-fragment classifier remains, and real-branch plus alternate-catalog tests pass |
| A70 | Satisfied | The exact unbranded Member -> Adventurer -> Veteran -> Prestige profile passed 29 first-boot and three unchanged-restart assertions, including a real journaled LuckPerms Member projection before Adventurer and idempotent read/Why/preview/operation lifecycle ingress |
| A76 | Partial / Deferred | By owner decision, the independent blind-administrator protocol will run against the final frozen Phase 8 release candidate, not the intermediate Phase 8D snapshot |

The mechanically audited ledger is **57 Satisfied, 18 Partial and 1 Later**. A63 remains `Partial` because Phase 9
retains the live MaddKraft clone/deployment gate; A64 remains `Later`. A61/A62/A65/A66/A67 retain their broader Phase 8E
qualification gates, and A76 retains its independent blind-admin gate deferred to the final frozen Phase 8 release
candidate.

## Verification

The owner-accepted Phase 8D implementation is sealed by focused i18n, documentation, SDK-consumer and regression checks, the exact
two-boot Paper qualification, and two consecutive eight-module `clean verify` runs with no source or documentation
edits between them. Exact totals and reproducible artifact hashes are recorded in
`PHASE8D_OWNER_REVIEW_SUMMARY.txt` and the owner-review bundle.

## Owner handoff

- `docs/V2_PHASE8D_IMPLEMENTATION.md`
- `docs/V2_PHASE8D_A70_QUALIFICATION.md`
- `docs/V2_PHASE8D_A76_PROTOCOL.md`
- `docs/V2_PHASE8D_I18N_EVIDENCE.md`
- `docs/V2_PHASE8D_ADMINISTRATION_SEMANTIC_AUDIT.md`
- `docs/V2_PHASE8D_ADMINISTRATION_MULTI_THROW_AUDIT.md`
- `docs/V2_PHASE8D_ADMINISTRATION_SINGLE_SOURCE_AUDIT.md`
- `docs/V2_PHASE8D_FILE_MANIFEST.md`
- `docs/V2_PHASE8_FAILURE_REGISTER.md`
- `docs/V2_PHASE8B_STATIC_AUDIT.md`
- `docs/V2_ARCHITECTURE.md`
- `docs/V2_TRACEABILITY.md`
- `DECISIONS.md`
- `PHASE8D_OWNER_REVIEW_SUMMARY.txt`
- `PHASE8D_PAPER_BOOT_1.log`
- `PHASE8D_PAPER_BOOT_2.log`
- `PHASE8D_VERIFY_1.log`
- `PHASE8D_VERIFY_2.log`
- `target/MaddPrestige_Phase8D_Owner_Review.zip`

Phase 8B, Phase 8C and Phase 8D are owner-accepted. Independent Owner Review 7 closed OR8D-01 through OR8D-11 with no
remaining Phase 8D acceptance blocker. A76 remains Partial by explicit owner deferral to the final frozen Phase 8
release candidate. Phase 8E has not started.
