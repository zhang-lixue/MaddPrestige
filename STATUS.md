# MaddPrestige V2 status

**Current phase:** Phase 9C configuration ergonomics and administrator usability; owner-review candidate

**Last updated:** 2026-08-29

**Branch:** `v2/phase-9c`

**Starting merged-main HEAD:** `45fdd108aa2df7d1307be4f27831e2026f10dda8`

**Candidate scope:** repository-only configuration/admin ergonomics; no numeric semantic change, live/clone work, V1 import, balance selection, GUI/shop, Phase 9D, or Phase 10 work

## Phase 9C candidate outcome

Phase 9C retains the Phase 9B numeric architecture and makes the normal configuration path smaller. All five immutable
revision documents remain required, but empty maps, disabled optional integrations, and deterministic safe values are
omitted. The canonical lossless editor materializes omitted scalar/list paths on explicit admin edit. Reset-policy
omissions inherit the existing safe policy without validation noise.

Requirement, cost, and reward scaling now has one canonical compact form plus advanced ranges using the same resolver.
Inheritance is global safe defaults, optional profile defaults, selected segment, then per-level override. Existing
explicit segmented and compatibility inputs retain their meaning. Normal Doctor, player, Why, simulation, and config
validation output is concise; `details`, `config explain`, and `config diff` expose provenance and full diagnostics.

The audit and implementation contract are recorded in `docs/V2_PHASE9C_CONFIGURATION_ERGONOMICS.md`; the exact
38-path scope is in `docs/V2_PHASE9C_FILE_MANIFEST.md`. Fresh clean verification passes 588 tests in 106 suites with
zero failures, errors, or skips and zero Checkstyle violations. The candidate JAR is 16,537,299 bytes with SHA-256
`F7D7A77CD3673DDAA500939C3122E3F946BE5E376D6E928CE424B0880809B0CD`; the 190,831-byte aggregate SBOM remains
`3797F16C617B3207229EFD8A846BE2EC31FB9CD56BE681A2069447A859D5568E`. No live server/database, real clone, V1 import,
production balance, GUI, Prestige shop, Phase 9D, or Phase 10 work occurred.

## Phase 9B candidate outcome

Phase 9B makes a durable non-negative numeric Prestige level the sole production progression authority. Each accepted
transition advances exactly one level under an optional finite maximum or `unlimited`. Authorization, initialization,
execution, persistence, production reads, previews, defaults, setup, and normal documentation no longer require or
mutate a stage/rank ladder. Stable stage/rank API signatures remain compatibility-only and production returns
empty/blocked results rather than breaking the owner-frozen API surface.

Requirement trees support ALL/ANY/X_OF_N. Requirements, costs, and rewards remain distinct and provider-owned values
are queried rather than mirrored. Requirement leaves plus cost/reward IDs now support independent contiguous
FLAT/LINEAR/EXPONENTIAL/MANUAL scaling segments, configurable transition/base/rate/rounding/floor/cap, and per-level
overrides. mcMMO `total_level` is canonical for guided Prestige. LuckPerms is an optional additive permission/existing-
group reward provider and never creates a group or removes unrelated nodes.

SQLite migration 12 appends checked `LEGACY_STAGE`/`NUMERIC_LEVEL` markers without changing migrations 1-11. It archives
old stage-era active rows, removes them from authority, and initializes active numeric state at Prestige 0 without
mapping stage position or projecting groups. Fresh V2 players start at Prestige 0 with no stage row; V1 player state is
not imported. Journal/CAS/transaction/recovery semantics, generic currency, milestones, and provider uncertainty
handling remain intact. A configurable first-party Prestige shop is authorized later Phase 9 work and is not yet
implemented. The audit and proposed
acceptance reinterpretation are recorded in `docs/V2_PHASE9B_NUMERIC_PRESTIGE_POLICY.md` and
`docs/V2_PHASE9B_ACCEPTANCE_MATRIX_PROPOSAL.md`.

No live server/database was touched, no disposable clone was run, no production economic/reward balance was selected,
and no Phase 10 work was started. The change remains uncommitted and unpushed for owner review.

Final repository verification is clean: 581 tests in 106 XML suites (580 Surefire tests plus the one distribution
Failsafe package test), with 0 failures, 0 errors, and 0 skips; configured Checkstyle reports 0 violations and
`git diff --check` passes. The distribution JAR is 16,528,792 bytes with SHA-256
`A38479D6BA9187A504FE502E5BF91EC1B4A0548E15BC6A5B3D188239E775B9A7`; the aggregate SBOM is 190,831 bytes with
SHA-256 `3797F16C617B3207229EFD8A846BE2EC31FB9CD56BE681A2069447A859D5568E`. The exact review scope is recorded in
`docs/V2_PHASE9B_FILE_MANIFEST.md`.

## Phase 9A candidate outcome

Phase 9A added repository-only qualification evidence for a future disposable MaddKraft clone. Its legacy mapping
template is now explicitly HISTORICAL, SUPERSEDED, and NON-EXECUTABLE. The eight preserved decision markers are not
deployment gates: the owner selected fresh V2, no V1 per-player import, and no V1-to-V2 player mutation executor.
Qualification must instead prove V2 never reads/imports V1 player state, initializes at Prestige 0, and leaves V1 and
external plugin data untouched. No production data mutation or balance activation occurred.

The pre-change baseline verification passed 553 tests in 103 suites with zero failures, errors, or skips and zero
Checkstyle violations. The Phase 9A candidate clean verification passed 557 tests in 104 suites with zero failures,
errors, or skips and zero Checkstyle violations. The rebuilt candidate distribution is 16,508,810 bytes with SHA-256
`5C5E116ACF12B6A28F91523AF33004BA91F0E728F802AC38865A3E22AD598B95`; the aggregate SBOM is 190,831 bytes with
SHA-256 `3797F16C617B3207229EFD8A846BE2EC31FB9CD56BE681A2069447A859D5568E`.

The only production-code change broadens read-only legacy discovery to the actual nested V1 configuration shape and
classifies configured external groups and obsolete competition configuration. Tests mechanically hold the exact
qualification profile, blocked mapping template, frozen V1 detection, and documentation truth boundaries. No server
was cloned or deployed, no live database was opened or changed, no production values were finalized, and no Phase 10
or web work was started.

The acceptance ledger remains 63 Satisfied / 12 Partial / 1 Later: A63 remains Partial, A64 remains Later, A76 remains
Satisfied, and A71-A75 retain their accepted Phase 7 Satisfied evidence. The future Phase 9 clone reruns are deployment
gates, not a downgrade or replacement of that accepted evidence.

## Phase 8F candidate outcome

Phase 8F defines `2.0.0-rc.1` as the non-GA final Phase 8 release-candidate identity and owns the owner-frozen
compatibility baseline `2.x-stable-1`. Mechanical signature tests freeze the accepted 46-type
Bukkit-free Stable SDK candidate and six-type Stable Paper event candidate without changing either production surface.
The final package owns its manifest identity, third-party notices, SQLite driver service metadata, required defaults,
and explicit exclusions for qualification/test/runtime debris. Two independent clean builds, exact artifact/SBOM
comparison, isolated SDK/example/qualification builds, and a fresh exact-JAR Paper two-boot technical qualification are
the release gates recorded in the Phase 8F evidence set. This is not GA or production readiness: Phase 9 remains the
MaddKraft deployment/migration qualification boundary.

The final acceptance ledger is 63 Satisfied / 12 Partial / 1 Later. A76 is Satisfied by the completed real-player,
fresh-directory owner-operated run against the exact accepted Owner Review 3 artifact. A63 remains Partial for the
Phase 9 deployment-clone gate; A64 remains Later because external SQL is deferred post-2.0. Phase 8F and the Phase 8
work phase meet their exit criteria, but GA/production readiness is not claimed.

## Phase 8F Owner Review Correction Pass 1

Owner Review 1 did not accept the candidate because OR8F-01 found an unresolved provenance variable in the review
manifest and OR8F-02 found that package evidence did not classify the legacy root `config.yml` precisely enough.
Correction Pass 1 replaces the variable with exact starting commit
`993599dc47cacc76b6372302d338d1850bf2896e`, generates the manifest mechanically, and adds both PowerShell and JUnit
guards that reject representative unresolved evidence variables.

The root `config.yml` is retained because the distribution deliberately includes the protected V1 transition
implementation, whose bootstrap and characterization tests consume that resource. It is not an active V2
configuration surface: the packaged descriptor selects the V2 bootstrap, all V2 production modules have zero legacy
Bukkit-config/root-file references, and the generic Member/Adventurer/Veteran example remains repository-only. The
package integration gate now proves those boundaries from the real JAR. No production source, Stable API/event surface,
runtime behavior or accepted Paper qualification changed. Independent Owner Review 2 accepted the correction and froze compatibility baseline `2.x-stable-1`. The frozen
Stable SDK and Paper event surfaces are unchanged by the later internal setup correction.

## Phase 8F final-RC owner acceptance correction

The owner explicitly superseded the original independent-blind-administrator A76 protocol for this private pre-release
with an owner-operated, public-document-only fresh-release-candidate usability and functional acceptance test. This is
an intentional acceptance-policy change, not an independent or blind external-administrator run.

The first owner run against distribution SHA-256
`112A0534F6166D8842F470A6C898A452284362761BF5350578453AE198961111` stopped at setup preview. The public requirement
command accepted raw target text without resolving its provider value type or parsing/canonicalizing it; only preview
performed that work. A rejected leaf then produced the expected downstream unknown-reference/empty-group findings.
The canonical contract remains DURATION with `PT1M`/`PT3M`; those exact ASCII values parse successfully. The defect is
the delayed, untyped public entry boundary and its misleading success response.

The correction candidate validates and canonicalizes requirement targets when entered, retains independent fail-closed
preview compilation, and keeps the draft -> preview -> acknowledgement -> confirm publication sequence. It also adds
an owner-bound current session, generated immutable playtime requirement IDs, guided common-case commands, contextual
completion and actionable setup/measurement help while retaining the explicit full-form command surface. Owner Review 1 rejected the first correction candidate as OR8F-A76-01: eager target/operator/scope/completion failures
incorrectly reused the ancestry-only `setup.draft.invalid` identity, so catalog rendering could give false rollback
guidance. The Owner Review 2 candidate assigns four exact requirement-input identities and catalog messages with typed
facts and valid alternatives while preserving the true ancestry identity unchanged. That correction required targeted
review and another empty-directory owner run; the failed checkpoint remains historical evidence rather than a waiver.

The next fresh owner run against the accepted Owner Review 2 artifact exposed OR8F-A76-02. With no canonical
configuration active, the one-second Placeholder refresh scheduler represented expected dormant player initialization
through the same failure channel used by active operational faults, emitting a warning every tick. The Owner Review 3
candidate adds an explicit dormant outcome: it skips state materialization and logging, retains no dormant marker,
initializes the same player after live activation without restart, and preserves exact active failure diagnostics. This
is a narrow lifecycle correction; setup design, GUI scope, Stable surfaces and Phase 9 remain unchanged. Targeted Owner
Review 3 accepted exact SHA-256 `0D961DB73D6C44CF18986CA42B7950914CEA5A1598BA7D41D0E919BFB76C6513`.

The final owner-operated run used Java 25, Paper 26.1.2 build 74, LuckPerms 5.5.71, a completely fresh directory and a
real Minecraft player. It passed more than ten seconds dormant online without warning/error spam; owner-bound setup;
`1m`/`3m` typed playtime requirements; VALID preview; acknowledgement/apply; live Member initialization; both rank-ups;
Prestige/reset/projection; same-directory restart; and HEALTHY Doctor. OR8F-A76-01 and OR8F-A76-02 are closed and A76
is Satisfied. UX-01 through UX-13 are retained as non-blocking post-Phase-9 output/admin-UX debt; no polish or GUI work
was implemented here.

## Accepted Phase 8E input

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
A64 remains `Later`, and at that accepted Phase 8E boundary A76 remained `Partial / Deferred` and had not run.

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
The automated run satisfies A02 and A70 but does not substitute for administrator usability acceptance. The later
owner policy replaced the original blind-external protocol, and the first owner-operated final-RC run is now retained
as blocked evidence pending the targeted setup correction described above.

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
- Phase 8E: performance plus fault/dependency qualification — owner-accepted and merged at `993599dc47cacc76b6372302d338d1850bf2896e`.
- Phase 8F: packaging and release hardening, including final compatibility-baseline responsibility — owner-accepted; exit criteria satisfied.

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
| A63 | Partial | Synthetic populated fresh/prefix V2 migrations, native backup, restore rehearsal, corruption/history failures and real Paper restart are qualified; the independent real populated pre-Phase9B V2 SQLite upgrade/preservation/report/restart gate remains |
| A64 | Later | MySQL/MariaDB production support and semantic parity are explicitly deferred post-2.0; no support or acceptance claim is made |
| A65 | Satisfied | Two independently owned Stable provider plugins simultaneously compose multiple metrics and prove late registration, exact metadata/maps, offline limitation, duplicate rejection, timeout/cancellation, failure isolation, unregister, generation replacement and recovery |
| A66 | Satisfied | The real-Paper matrix proves normal RankUp/Prestige PRE/POST durability, unknown-player cancellation, listener failure, same-player conflict, cross-player completion, rebind-after-PRE, service loss, definitely-not-applied, throw, verified apply and uncertain reconciliation semantics |
| A67 | Satisfied | Ten-dependency absence, exact lifecycle including EconomyShopGUI/QuickShop-Hikari, CraftEngine reload, provider failure/async timeout/malformed output and deterministic old/new Alpha overlap prove the four-call budget survives provider generations, Beta remains usable, and state recovers/reclaims without worker leakage |
| A68 | Satisfied | Stable semantic references preserve 51 typed blocker identities and occurrence-correct administration semantics across 116 sites/89 codes: 19 multi-source and all 70 single-source codes; the Phase 8E setup diagnostic has one structured helper supplying exact provider/component/requirement facts, five typed variants distinguish overloaded apply/validation states, and no English or diagnostic-fragment classifier returns |
| A70 | Satisfied | The exact unbranded Member -> Adventurer -> Veteran -> Prestige profile passed 30 first-boot and three unchanged-restart assertions, including silent dormant-online behavior, live activation, real journaled LuckPerms Member projection before Adventurer and idempotent read/Why/preview/operation lifecycle ingress |
| A76 | Satisfied | Under the D-179 owner-operated policy, a real player in a fresh Paper/LuckPerms directory completed dormant-online operation, guided setup, typed playtime, acknowledgement/apply, live Member projection, both rank-ups, Prestige, restart and HEALTHY Doctor against exact accepted SHA `0D961DB73D6C44CF18986CA42B7950914CEA5A1598BA7D41D0E919BFB76C6513` without source inspection or developer intervention |

The mechanically audited final ledger is **63 Satisfied, 12 Partial and 1 Later**. A63 remains `Partial` because Phase 9
retains the live MaddKraft clone/deployment gate; A64 remains `Later`. The other eleven Partial rows retain their exact
recorded later integration/composition boundaries. No acceptance criterion remains Blocked.

## Verification

The corrected Phase 8F candidate passes 89 focused tests / six suites, the changed Phase 8D Paper harness isolated
build, two consecutive authoritative clean verifies at 553 tests / 103 suites with byte-identical artifacts, the
mechanical 46/6 Stable compatibility and leakage gates, package/content/license/SBOM audits, and a
fresh exact-JAR Paper run at 30/30 plus 3/3 unchanged restart. Checkstyle is zero. The owner-accepted Phase 8E Sonar result remains the
applicable published baseline because this unpushed branch has no SonarCloud analysis; no Sonar configuration,
suppression or exclusion changed. A cached OWASP scan completed without its CVSS 7 failure gate firing, but the
unauthenticated NVD refresh was rate-limited and interrupted at 3%, hosted suppressions were unavailable, and OSS Index
requires credentials; this limited result is not represented as a complete current vulnerability-feed pass.

After final owner-acceptance reconciliation, an additional authoritative clean verify passed 553 tests / 103 suites
with zero failures, errors, skips or Checkstyle violations. Distribution and aggregate SBOM hashes remained exactly
`0D961DB73D6C44CF18986CA42B7950914CEA5A1598BA7D41D0E919BFB76C6513` and
`3797F16C617B3207229EFD8A846BE2EC31FB9CD56BE681A2069447A859D5568E`; the accepted runtime binary did not change.

## Owner handoff

- `docs/V2_PHASE8F_IMPLEMENTATION.md`
- `docs/V2_PHASE8F_A76_SETUP_CORRECTION.md`
- `docs/V2_PHASE8F_A76_OWNER_ACCEPTANCE.md`
- `docs/V2_PHASE8F_RELEASE_MATRIX.md`
- `docs/V2_PHASE8F_COMPATIBILITY_BASELINE.md`
- `docs/V2_PHASE8F_API_INVENTORY.md`
- `docs/V2_PHASE8F_PAPER_API_INVENTORY.md`
- `docs/V2_PHASE8F_REPRODUCIBLE_BUILD_EVIDENCE.md`
- `docs/V2_PHASE8F_PACKAGE_CONTENT_AUDIT.md`
- `docs/V2_PHASE8F_RELEASE_INSTALL_QUALIFICATION.md`
- `docs/V2_PHASE8F_RELEASE_DOCUMENTATION_AUDIT.md`
- `docs/V2_PHASE8F_FILE_MANIFEST.md`
- `docs/V2_PHASE8_FAILURE_REGISTER.md`
- `docs/V2_TRACEABILITY.md`
- `DECISIONS.md`
- `docs/evidence/phase8f/`
- `PHASE8F_OWNER_REVIEW_SUMMARY.txt`
- `target/MaddPrestige_Phase8F_A76_Setup_Correction_Owner_Review_3.zip`

Phase 8B through Phase 8F are owner-accepted at their respective boundaries. The complete Phase 8F delta remains
unstaged and uncommitted pending a separate publication action; compatibility baseline `2.x-stable-1` is owner-frozen
and unchanged. Earlier exact RCs remain blocker evidence, not silently substituted. A76 and Phase 8 exit are PASS;
Phase 9, the deferred output/admin-UX polish pass and GUI work have not started.
