# MaddPrestige V2 status

**Current phase:** Phase 8E owner-accepted; publication authorized

**Last updated:** 2026-08-23

**Branch:** `v2/phase-8e`

**Frozen pre-Phase-8 HEAD:** `7fc5c3532f0614634933ff66ee0e03bf55b8e5cf`

**Candidate scope:** performance, offline/provider/event/fault/dependency qualification and the focused corrections
exposed by those matrices; accepted Phase 1-8D behavior retained

## Outcome

Independent Owner Review 3 accepted the exact 59-path Phase 8E candidate and its exact final-artifact qualification.
OR8E-01 through OR8E-06 and P8B-F014/P8B-F015 are closed. Publication does not begin Phase 8F, freeze the final
compatibility baseline, execute A76 or change the 62 Satisfied / 13 Partial / 1 Later ledger.

Phase 8E executes the predeclared Paper load plan and the complete offline, multi-provider, public-event and optional
dependency fault matrices. The final exact-artifact performance boot passed 23/23 assertions under 102,400 real mcMMO
adjusted-XP events, 5,760 public evaluations, 11,520 Placeholder refresh requests and 180,000 cache renders. Controlled
SQLite contention produced the expected public `DEGRADED` state without a Paper stall; release/retry restored
`AVAILABLE` and converged exactly. After 2,048 contention increments and 512 dirty-shutdown increments, an unchanged
restart passed 3/3 and recovered all 104,960 accepted increments without loss or duplicate. Qualification-only JFR
measured the actual Java 25 operation and Placeholder virtual-thread families: high-water 128/12, exact 6,034/6,034
and 11,648/11,648 start/end convergence, active zero, no pin and no submission failure. The corrected fault environment
passed 48/48 assertions across two independently owned Stable providers. Four synchronously blocked old-generation
Alpha callbacks retained one stable logical budget across replacement and repeated rebinds: generation 2 admitted
0/8, repeated generations admitted 0/4, Beta remained usable within 100 ms, and release recovered in 48 ms with final
active count zero. A separate final-artifact boot with all ten optional dependency artifacts absent passed 15/15.

Owner Review Correction Pass 2 closes the remaining setup, callback-lifecycle and diagnostic-fact defects. One
authoritative built-in selection map covers all 15 integration-backed setup identities, including
`phase5_events:mcmmo_adjusted_xp_total` -> `integrations.mcmmo.enabled`; the final setup run passed 26/26 and proved
the selected manual metric remained usable after apply/reconciliation without enabling WorldGuard or CraftEngine.
Every `setup.integration.unconfigurable` path now calls one helper supplying exact
`provider`/`component`/`requirement` facts. Callback permits are owned by stable owner-qualified provider identity,
survive transient generations while work is active, and are reclaimed only after the final generation and callback
release. Focused tests and fresh exact-JAR Paper reruns qualify every correction. No Stable SDK/Paper event surface or
accepted semantic behavior changed.

A61/A62/A65/A66/A67 are owner-accepted as `Satisfied`. P8B-F014/P8B-F015 are closed; P8B-F016/P8B-F017 are
resolved at the Phase 8E boundary. The mechanical ledger is 62 Satisfied / 13 Partial / 1 Later. A63 remains `Partial`,
A64 remains `Later`, and A76 remains `Partial / Deferred` and has not run.

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
- Phase 8D: i18n, a generic example, public documentation and admin UX — owner-accepted and merged.
- Phase 8E: performance plus fault/dependency qualification — owner-accepted; publication authorized.
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
| A61 | Satisfied | The exhaustive offline matrix proves UUID-capable internal/LuckPerms/Vault/manual/SDK paths and explicitly fail-closed online/cache-limited Paper/mcMMO/Placeholder/WorldGuard/CraftEngine paths without fabricated zero or mutation |
| A62 | Satisfied | The exact final-artifact real-Paper load retained 19.999-20.005 TPS, bounded platform threads/backlogs and cache-only rendering while JFR proved operation/Placeholder high-water 128/12 with exact 6,034/6,034 and 11,648/11,648 convergence; all 104,960 accepted manual increments converged exactly through SQLite contention, shutdown and unchanged restart; deterministic batches were exactly 1,024/1,024/52 |
| A63 | Partial | Populated fresh/prefix migration, native backup, restore rehearsal, corruption/history failures and real Paper restart are qualified; Phase 9 retains actual MaddKraft clone/deployment migration qualification |
| A64 | Later | MySQL/MariaDB production support and semantic parity are explicitly deferred post-2.0; no support or acceptance claim is made |
| A65 | Satisfied | Two independently owned Stable provider plugins simultaneously compose multiple metrics and prove late registration, exact metadata/maps, offline limitation, duplicate rejection, timeout/cancellation, failure isolation, unregister, generation replacement and recovery |
| A66 | Satisfied | The real-Paper matrix proves normal RankUp/Prestige PRE/POST durability, unknown-player cancellation, listener failure, same-player conflict, cross-player completion, rebind-after-PRE, service loss, definitely-not-applied, throw, verified apply and uncertain reconciliation semantics |
| A67 | Satisfied | Ten-dependency absence, exact lifecycle including EconomyShopGUI/QuickShop-Hikari, CraftEngine reload, provider failure/async timeout/malformed output and deterministic old/new Alpha overlap prove the four-call budget survives provider generations, Beta remains usable, and state recovers/reclaims without worker leakage |
| A68 | Satisfied | Stable semantic references preserve 51 typed blocker identities and occurrence-correct administration semantics across 112 sites/85 codes: 19 multi-source and all 66 single-source codes; the Phase 8E setup diagnostic has one structured helper supplying exact provider/component/requirement facts, five typed variants distinguish overloaded apply/validation states, and no English or diagnostic-fragment classifier returns |
| A70 | Satisfied | The exact unbranded Member -> Adventurer -> Veteran -> Prestige profile passed 29 first-boot and three unchanged-restart assertions, including a real journaled LuckPerms Member projection before Adventurer and idempotent read/Why/preview/operation lifecycle ingress |
| A76 | Partial / Deferred | By owner decision, the independent blind-administrator protocol will run against the final frozen Phase 8 release candidate, not the intermediate Phase 8D snapshot |

The mechanically audited candidate ledger is **62 Satisfied, 13 Partial and 1 Later**. A63 remains `Partial` because
Phase 9 retains the live MaddKraft clone/deployment gate; A64 remains `Later`. A76 retains its independent blind-admin
gate deferred to the final frozen Phase 8 release candidate and has not run.

## Verification

The Phase 8E candidate is sealed by focused correction tests, four isolated qualification-plugin builds, the exact
two-boot real-Paper performance qualification, the real-Paper 48-assertion fault matrix, the clean optional-absence
boot and an authoritative eight-module `clean verify`. Exact totals and reproducible artifact hashes are recorded in
  the Phase 8E Owner Review 3 summary and bundle.

## Owner handoff

- `docs/V2_PHASE8E_IMPLEMENTATION.md`
- `docs/V2_PHASE8E_LOAD_PLAN.md`
- `docs/V2_PHASE8E_PERFORMANCE_EVIDENCE.md`
- `docs/V2_PHASE8E_OFFLINE_PROVIDER_MATRIX.md`
- `docs/V2_PHASE8E_MULTI_PROVIDER_MATRIX.md`
- `docs/V2_PHASE8E_EVENT_FAULT_MATRIX.md`
- `docs/V2_PHASE8E_DEPENDENCY_FAULT_MATRIX.md`
- `docs/V2_PHASE8E_FILE_MANIFEST.md`
- `docs/V2_PHASE8_FAILURE_REGISTER.md`
- `docs/V2_TRACEABILITY.md`
- `DECISIONS.md`
- `docs/evidence/phase8e/`
- `PHASE8E_OWNER_REVIEW_SUMMARY.txt`
- `target/MaddPrestige_Phase8E_Owner_Review_3.zip`

Phase 8B through Phase 8E are owner-accepted at their respective boundaries. Phase 8E publication is authorized, but
merge and post-merge verification remain separate gates. A76 remains Partial by explicit owner deferral to the final
frozen Phase 8 release candidate and was not run. Phase 8F and Phase 9 have not started.
