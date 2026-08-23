# Phase 8D implementation

**Status:** Owner-accepted by Independent Owner Review 7; publication in progress  
**Baseline:** `d481a9db7cd67108ff77f97e2d64d737e9096276` on `v2/phase-8`  
**Date:** 2026-08-23

## Scope delivered

Phase 8D implements the authorized i18n, exact generic example, public documentation and administration UX scope. It
does not start Phase 8E/8F/9, alter protected V1 Java, add MySQL/MariaDB/HikariCP production work, freeze the Stable API
baseline or claim production readiness.

Phase 8D adds:

- one server-global UTF-8 locale, complete built-in `en_US`, strict MiniMessage Paper rendering, bounded untrusted
  arguments, deterministic fallback and atomic known-good reload retention;
- the exact public `examples/member-adventurer-veteran/` canonical profile;
- the existing command-driven canonical setup path documented as the 2.0 Quick Start, with the smallest coherent
  stage-specific requirement correction;
- public installation, configuration, operation, recovery, API and event documentation plus mechanical docs-as-tests;
- a minimal compilable owner-attested external provider example using only the public API and Paper;
- a disposable real-Paper harness for the exact A70 sequence and unchanged restart;
- a repeatable independent A76 blind-admin protocol without fabricating its result.

## Internationalization

`PaperMessageService` loads the packaged `locales/en_US.yml` and an optional selected catalog beneath the plugin data
directory. Catalog and selection files are bounded regular files. Every existing path component rejects symbolic/reparse
indirection, and both the real data directory and real candidate file must remain contained before bytes are parsed.
YAML must be a bounded flat key/string map. All templates pass strict MiniMessage deserialization before publication.

The immutable snapshot is constructed completely before a single atomic reference replacement. Failed reload keeps the
previous snapshot. On initial boot, an invalid selected catalog falls back to built-in English with a bounded diagnostic.
Missing selected keys use English; globally absent keys render bounded `[message:<key>]`. Dynamic arguments
are stripped of control amplification, length-bounded and inserted through unparsed placeholders. They cannot create
formatting, click, hover or nested-template authority.

V2 command, setup, Why, GUI and Phase 7 presentation sites now exchange immutable `MessageReference` values containing a
stable semantic catalog key and named machine-data arguments. No generic line/title/action wrapper can hide precomposed
English. Functional selected-catalog tests drive the real command adapter, GUI inventory rendering and Phase 7 Paper
executor and observe changed visible prose. Machine codes and message keys remain stable language-neutral core/API data.
Early bootstrap messages before the message service exists are the documented exception. No translation beyond reviewed
built-in English is claimed.

## Canonical setup and exact example

The existing Phase 6 `SetupWizardService` remains the sole setup architecture. Its backward-compatible command contract
now permits the session's requirement to target a selected stage. Existing callers that omit the target keep the
previous global behavior. Ordered session state and ordered generated documents are explicit invariants. For the frozen
generic profile, the generator's five configuration documents are byte-identical to the public example, including
schema 7 integrations, the complete disabled optional-integration set, `value-type: DURATION`, order and line endings.
The wizard still owns provider selection, ordered stages, baseline, canonical requirements, empty cost/reward, Prestige
eligibility/reset, preview, server-issued acknowledgement and immutable apply.

The frozen public profile is:

| Stable stage ID | External LuckPerms group | Requirement |
|---|---|---|
| `member` | `Member` | none |
| `adventurer` | `Adventurer` | Paper `play_one_minute >= PT1M`, `SINCE_PRESTIGE_START`, `LIVE` |
| `veteran` | `Veteran` | Paper `play_one_minute >= PT3M`, `SINCE_PRESTIGE_START`, `LIVE` |

Prestige is enabled only from `veteran`, increments current/lifetime counts by one, resets stage to `member`, resets the
active requirement baseline and preserves Paper-owned historical statistics and unrelated durable state. Cost, reward,
currency, entitlement, milestone, season, external reset and optional integration authority are empty/disabled.
Administrators create all three external groups explicitly; MaddPrestige contains no group-creation path.

## Owner Review Correction Pass 1

The first owner review rejected the candidate at 55 Satisfied / 20 Partial / 1 Later. The correction closes the complete
six-finding classes:

1. **OR8D-01:** generic `command.line`, GUI title/action and `phase7.line` wrappers were replaced by semantic references;
   command/help/error/setup/Why/GUI/Phase 7 output and bundled-key completeness now have functional regression coverage.
2. **OR8D-02:** one lifecycle boundary covers stable progress, requirement/evaluation reads, Why, previews, commands,
   operations, staff manual-Prestige mutation, Placeholder/join and Paper-facing stable service paths. It atomically
   creates stage/Prestige/baselines and
   performs one idempotent journaled/verified initial rank projection through the accepted executor. Completed identity
   short-circuits; incomplete/uncertain identity blocks for recovery. Progressed players are not overwritten. Eligible
   mutations retain zero-effect PRE cancellation by initializing only in the post-PRE callback.
3. **OR8D-03:** lexical validation is followed by component-level symlink/reparse rejection and real-path containment
   before locale content is read. Tests cover final files, selection files, locale directory, intermediate ancestors,
   traversal, absolute input, normal input and fallback.
4. **OR8D-04:** explicit ordered session/document representations make canonical output deterministic; repeated output,
   preview order and exact bytes are regression-tested.
5. **OR8D-05:** the frozen wizard output and public fixture are one exact five-document byte contract, not merely two
   independently compilable equivalents.
6. **OR8D-06:** the manifest uses `DECISIONS.md`; missing keys are documented as `[message:<key>]`; controls are documented
   as U+FFFD replacement; counts, qualification revision, hashes and logs are regenerated from the corrected candidate.

## Owner Review Correction Pass 2

Owner Review 2 accepted OR8D-02 through OR8D-06 and identified one remaining failure class: Correction Pass 1 fixed the
generic-wrapper architecture but reduced several previously actionable surfaces to code/count/status/path output.
OR8D-07 is corrected without restoring arbitrary English arguments:

1. `SemanticPresentation` maps stable Doctor diagnostic families to catalog-owned summary/consequence and remediation
   keys. Exact severity, path, code and component remain named data.
2. Why emits every authorization blocker. Requirement and non-requirement provider/cost/reward/projection/boundary/
   configuration/state/authorization/currency concepts have stable keys; blocker count is supplemental only.
3. Operation preview construction retains structured stage and Prestige transitions, cost/reward identities and values,
   reset-component disposition, currency arithmetic, rank projection and external-uncertainty identities. The command
   boundary emits those facts, requirements, blockers, eligibility and revision instead of a one-line count summary.
4. Validation/setup findings map stable code families to catalog-owned problem/consequence and remediation messages.
5. Administration failures map bounded codes to safe actionable summary/remediation families; internal exception prose
   and stack details are not exposed. Paper GUI failures use the same mapping.
6. Schema field IDs select one complete catalog description per setting, while canonical path/value remain data.

Rendering-boundary tests cover requirement plus non-requirement/multiple Why blockers; Doctor consequence/remediation;
validation/setup meaning; eligible and blocked preview facts; administration guidance; configuration descriptions;
complete schema/public key inventory; generic-wrapper prohibition; and alternate-catalog replacement of restored Why,
Doctor/remediation and preview concepts. Sentinel internal English proves summary/remediation/preformatted-plan text does
not escape into message arguments. OR8D-02 through OR8D-06 remain unchanged and their regression suites remain green.

## Owner Review Correction Pass 3

Owner Review 3 accepted OR8D-02 through OR8D-06 and found that Correction Pass 2 still derived some public meaning by
parsing English blocker sentences and by grouping administration codes through fragments such as `unknown`, `missing`
or `stale`. Correction Pass 3 removes both lossy classifiers:

1. RankUp and Prestige authorization now emit immutable `AuthorizationBlocker` values containing an exhaustive stable
   `AuthorizationBlockerKind`, blocker-specific machine facts and secondary diagnostic text. The diagnostic is retained
   for compatibility/debugging but is never the semantic localization input.
2. The same typed blockers pass through authorization results, plans, Why, eligible/blocked previews, real no-plan
   preview failures, simulation and operation rejection. Maximum, cooldown, inactive ladder, illegal/stale target,
   missing definitions, provider, requirement, cost/reward preflight, projection, state and boundary causes remain
   distinct.
3. `SemanticPresentation` maps each blocker kind directly to its catalog identity. All 84 known public
   `AdministrationException` codes have exact mappings; genuinely unknown internal codes alone use the safe fallback.
   Baseline-stage, canonical-path and duplicate-stage errors retain their actual facts and exact remediation.
4. Cost-preflight blockers retain cost ID, provider, status, amount, type and bounded detail. The no-plan path preserves
   the same structured blocker values that `/why` uses instead of flattening an English list.
5. Malformed command input retains its canonical syntax as a structured argument to a catalog-owned usage message.
6. Exhaustive blocker/catalog and administration-map completeness tests, real authorization/no-plan/cost paths,
   alternate-catalog tests and static architecture guards prevent regression to prose or fragment classification.

Owner Review 4 accepted OR8D-08 and OR8D-10 and preserved OR8D-02 through OR8D-07. Pass 3 proved all 84 administration
codes were mapped, but OR8D-09 remained open because broad exact-code families still gave several codes inaccurate
meaning or remediation.

## Owner Review Correction Pass 4

Correction Pass 4 resolves the remaining administration subclass without rewriting the accepted typed authorization
architecture:

1. All 84 known public administration codes were audited at every production throw site. Each now owns one explicit
   dedicated semantic identity and bundled summary/remediation pair; only a genuinely unknown code uses the safe
   generic fallback.
2. The previous 10 broad administration families were removed. Eighty codes moved to new dedicated identities, while
   the four already-exact operation-preview, setup-baseline, configuration-path and setup-stage identities were kept.
3. The owner-cited server-authority, no-acknowledgement-required, projected-group and duplicate-requirement cases now
   describe their exact gates and actions. Unknown Prestige stage/metric, missing canonical document and remap cases
   retain stage/purpose, provider/metric, document and source/target facts.
4. The complete audit also corrected rollback eligibility, setup-draft ancestry, validation acknowledgement,
   configuration-history finalization and unsafe-stage transition recovery semantics found during source review.
5. Production exceptions now retain additional immutable permission, path/value/type, document, requirement/stage,
   provider/metric, revision/status and remap facts where those values guide public remediation. Diagnostic English
   remains log/compatibility data and never enters localized rendering.
6. Tests expose the complete code-to-identity register, require 84 distinct identities and catalog pairs, exercise the
   real exception sources, prove family distinctness and safe unknown fallback, and replace acknowledgement/group/
   requirement prose through an alternate catalog without changing facts.

The explicit register and its reviewed condition/action/fact contract are recorded in
`V2_PHASE8D_ADMINISTRATION_SEMANTIC_AUDIT.md`. Owner Review 5 accepted the code-level corrections but found that two
exact codes still collapsed incompatible production occurrences, so it returned A68 to Partial and kept OR8D-09 open.

## Owner Review Correction Pass 5

Correction Pass 5 closes the remaining occurrence-level failure class without changing the accepted diagnostic codes:

1. A production-source walk inventories 111 literal exception construction sites, all 84 public codes and the exact 19
   codes occurring more than once. The test fails if the production inventory and reviewed register diverge.
2. Deliberate site review found 17 compatible shared contracts. `config.apply.failed` and
   `config.validation.blocked` require structured discrimination; the full sources, triggers, consequences, remedies,
   facts and verdicts are recorded in `V2_PHASE8D_ADMINISTRATION_MULTI_THROW_AUDIT.md`.
3. `AdministrationSemanticVariant` preserves stable codes while distinguishing prior state unchanged, prior state
   restored safely, restoration failed/reconciliation required, acknowledgement-preparation validation and apply
   validation. The exception constructor rejects a variant belonging to a different code.
4. Real branches prove history failure before authoritative change, runtime failure with successful restore, runtime
   failure with failed restore, acknowledgement preparation blocked by errors, apply blocked by errors, and apply
   blocked by unacknowledged high-risk findings. Restore failure explicitly requires reconciliation and prohibits an
   ordinary retry.
5. `config.path.not_listable` now says list/map collection and directs scalar users to `config get`/`config explain`.
   `stage.change.remap_snapshot_stale` now reports changed persisted player references plus source, target and
   preview/current counts, requiring a fresh remap preview without claiming candidate configuration drift.
6. All five occurrence variants and the stale-remap state are alternate-catalog controlled. The test helper supplies
   sentinel internal diagnostics and proves none reach public rendering.

OR8D-09 is resolved in the Pass 5 candidate. OR8D-02 through OR8D-08 and OR8D-10 remain closed.

## Owner Review Correction Pass 6

Owner Review 6 accepted the complete multi-source correction and opened OR8D-11 for remaining catalog drift in
otherwise single-source administration identities. Correction Pass 6 changes catalog meaning and verification only;
stable codes, `AdministrationSemanticVariant`, structured-fact contracts and production Java control flow remain intact.

1. `config.preview.stale` now identifies only candidate-hash/draft-version drift after preview. Its public explanation
   does not mention active revision; `config.revision.stale` remains the separate active-configuration condition.
2. `config.rollback.not_prepared` identifies the submitted draft as lacking rollback-workflow provenance and directs the
   administrator to prepare a rollback draft from an `APPLIED` history revision before preview/apply.
3. `config.snapshot.prepare_failed` explicitly says durable preparation failed before activation and current active
   configuration remains unchanged/authoritative. It does not imply partial activation or reconciliation.
4. A final source walk derives the exact 65 single-source codes after excluding the accepted 19 multi-source codes.
   `V2_PHASE8D_ADMINISTRATION_SINGLE_SOURCE_AUDIT.md` records source, identity, trigger, consequence, remediation and
   facts for each row. A test parses that evidence and fails when the production single-source set changes unreviewed.
5. The deliberate review found six additional single-source identities in four drift groups and corrected them: actor
   rather than player ownership plus expired/consumed operation confirmation states; lossless YAML stage add/remove
   failure conditions; mapping removal from a draft with no remap plan; and a GUI deletion action missing an explicit
   replacement.
6. Real production checks exercise draft hash and version drift, rollback provenance and snapshot preparation failure.
   Built-in/alternate catalog tests prove exact neighboring semantics, unchanged facts and no internal English input.

Independent Owner Review 7 accepted the Pass 6 correction. OR8D-01 through OR8D-11 are closed with no remaining Phase
8D acceptance-blocking defect.

## Runtime corrections found by the exact process test

The first real process pass exposed four narrow composition defects. Each correction preserves the accepted 8B/8C
transaction, journal, lease, uncertainty, reconciliation and configuration-publication model.

1. Production Doctor had declared domains without installing all applicable probes. `ProductionRuntime` now supplies
   database, operational, rank-target and configuration-history probes and a bounded operational snapshot. Dormant
   optional-provider findings stay visible without making an otherwise applicable generic profile unhealthy.
2. A previously unseen player's current-Prestige delta had no starting sample. Stage, zero Prestige and every initial
   `SINCE_PRESTIGE_START` baseline are now created atomically after pinned samples are available; partial initialization
   fails closed and existing players remain idempotent.
3. LuckPerms normalizes group node names. The adapter maps nodes case-insensitively back to exact configured external
   spelling so `Member` remains canonical without weakening exact configured-group validation.
4. Windows can transiently deny the already-atomic configuration revision rename. Only `AccessDeniedException` receives
   eight bounded retries with short interruptible backoff; unsupported/non-atomic and all other failures still fail
   closed. No blind replacement or partial publication was added.

`VanillaStatisticsProvider` now resolves supported offline UUIDs through Bukkit `OfflinePlayer`; the actual statistic
read remains Paper-owned. The qualification exercised this deterministic offline path but does not broaden A61.

## Public documentation and examples

The root README and public guides describe only implemented 2.0 candidate behavior. They state that SQLite is the sole
2.0 production backend, support is one Paper process/server, MySQL/MariaDB is deferred post-2.0 and no production-ready
claim exists before Phase 9. Operator documentation explains external groups, canonical setup, permissions, files,
requirements/scopes, lifecycle, uncertainty/reconciliation, backup/rehearsal/recovery, diagnostics and optional-provider
offline limits.

`PhaseEightDPublicDocumentationTest` verifies local links/paths, public command and permission inventories, absence of
stale V1 instructions, generic-branding constraints and canonical compile/apply of the exact example. The provider SDK
example compiles separately against `maddprestige-api` and Paper only, registers `ProviderDeclaration` through Paper
`ServicesManager`, supplies one visits metric and health/lifecycle/cancellation behavior, and unregisters on disable.
It uses no internal package, recovery marker or manual-progress authority and is not required by A70.

## Acceptance treatment

| Acceptance | Final Phase 8D treatment | Reason |
|---|---|---|
| A02 | Satisfied | The owner-defined command setup wizard created and activated the exact ladder through the documented real process path |
| A45 | Satisfied | Public Doctor output preserves catalog-owned consequence/explanation, exact affected identity and safe actionable remediation |
| A46 | Satisfied | Exact rank/provider/path failures remain actionable without creating groups or passing internal prose as presentation data |
| A47 | Satisfied | Why consumes the exhaustive typed RankUp/Prestige blocker identity and facts; real inactive, illegal-target, missing-cost, maximum, cooldown, provider and cost-preflight paths remain exact, and maximum cannot collapse into inactive ladder |
| A68 | Satisfied | Catalog selection is direct from typed blocker identities and occurrence-correct administration state across 111 sites/84 codes: 19 accepted multi-source and all 65 single-source codes; five typed variants distinguish overloaded states, facts survive, alternate catalogs control every corrected state, and prose/code-fragment classifiers are absent |
| A70 | Satisfied | Exact 29/29 fresh plus 3/3 unchanged-restart real-Paper evidence includes real initial Member projection before Adventurer and idempotent read/Why/preview/operation ingress |
| A76 | Partial / Deferred | By owner decision, the independent blind-human protocol will run against the final frozen Phase 8 release candidate; it was not run or claimed for Phase 8D |
| A61/A62/A65/A66/A67 | unchanged Partial | Phase 8D narrow evidence does not complete their Phase 8E matrices |
| A63 | unchanged Partial | Actual MaddKraft deployment-clone qualification remains Phase 9 |
| A64 | unchanged Later | External SQL remains deferred post-2.0 |

The mechanical ledger is 57 Satisfied / 18 Partial / 1 Later.

## Evidence map

- `V2_PHASE8D_I18N_EVIDENCE.md`
- `V2_PHASE8D_ADMINISTRATION_SEMANTIC_AUDIT.md`
- `V2_PHASE8D_ADMINISTRATION_MULTI_THROW_AUDIT.md`
- `V2_PHASE8D_ADMINISTRATION_SINGLE_SOURCE_AUDIT.md`
- `V2_PHASE8D_A47_AUTHORIZATION_EVIDENCE.md`
- `V2_PHASE8D_ACCEPTANCE_EVIDENCE.md`
- `V2_PHASE8D_A70_QUALIFICATION.md`
- `V2_PHASE8D_A76_PROTOCOL.md`
- `V2_PHASE8D_FILE_MANIFEST.md`
- `PHASE8D_PAPER_BOOT_1.log`
- `PHASE8D_PAPER_BOOT_2.log`
- `PHASE8D_FOCUSED_TESTS.log`
- `PHASE8D_DOCS_AS_TESTS.log`
- `PHASE8D_PROVIDER_SDK_VERIFY.log`
- `PHASE8D_VERIFY_1.log`
- `PHASE8D_VERIFY_2.log`
- `PHASE8D_OWNER_REVIEW_SUMMARY.txt`

All Phase 8D changes remain unstaged and uncommitted for owner review.
