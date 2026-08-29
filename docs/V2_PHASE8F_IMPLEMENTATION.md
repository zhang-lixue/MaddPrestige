# Phase 8F implementation

Date: 2026-08-28<br>
Starting merged-main commit: `993599dc47cacc76b6372302d338d1850bf2896e`<br>
Candidate branch: `v2/phase-8f`<br>
Publication state: targeted Owner Review 3 and final owner-operated A76 PASS; Phase 8F accepted and complete; delta remains unstaged, uncommitted and unpushed

## Outcome

Phase 8F prepares `2.0.0-rc.1` as the final Phase 8 release candidate. It does not declare GA or production
readiness. Phase 9 still owns the MaddKraft deployment clone, production migration, live economy and go-live gates.
A76 ran under the superseding owner-operated public-only policy. Two earlier runs exposed preserved setup and dormant
warning blockers; the final real-player fresh-directory run against the accepted Owner Review 3 artifact passed. Phase
8F and Phase 8 are complete; Phase 9 still owns production readiness.

## Implemented release hardening

- Every active reactor, example and qualification POM uses `2.0.0-rc.1` for first-party artifacts.
- The production distribution is `MaddPrestige-2.0.0-rc.1.jar`; the public SDK coordinate is
  `net.maddkraft:maddprestige-api:2.0.0-rc.1`.
- Maven owns release channel `release-candidate` and proposed compatibility baseline `2.x-stable-1`. The shaded
  manifest and filtered `plugin.yml` expose matching identity.
- Mechanically generated final inventories classify 46 Stable SDK, five Experimental, 17 Internal/Should Not Be
  Public and 41 Legacy/Pending Removal public top-level types, plus six Stable Paper event types.
- Normalized signature tests enforce the exact accepted Stable SDK and Paper candidates. Existing leakage tests remain
  authoritative. No Stable production type or method changed during Phase 8F.
- The distribution package has an executable integration audit covering its manifest, descriptor, runtime resources,
  JDBC service metadata, embedded dependencies and forbidden development/runtime debris.
- `THIRD-PARTY-NOTICES.txt` names the shaded SQLite JDBC and SnakeYAML Engine dependencies and their Apache 2.0
  licensing; the Apache license text retained by SQLite JDBC is distributed in the JAR.
- CI evaluates the project version instead of assuming a snapshot filename and compares both distribution and
  aggregate-SBOM hashes across two clean builds.
- Public installation, upgrade/rollback, API, event and provider documentation distinguishes the release candidate
  from GA and retains the SQLite-only, external-SQL-deferred and Phase 9 boundaries.
- Historical V1 documentation is preserved but explicitly marked as non-V2 guidance.

## Owner Review Correction Pass 1

- **OR8F-01 — resolved candidate:** `V2_PHASE8F_FILE_MANIFEST.md` now records the exact starting SHA rather than an
  unresolved shell variable. `Generate-Phase8FFileManifest.ps1` derives SHA, branch and exact review paths from Git,
  rejects build/runtime leakage, proves representative unresolved-variable fixtures are caught, and supports a stale
  manifest check. `PhaseEightFEvidenceIntegrityTest` independently enforces an exact 40-character ancestor SHA,
  cross-document provenance and a repository-wide Phase 8F evidence placeholder scan.
- **OR8F-02 — resolved candidate:** the real archive is classified into required V2 runtime, required transitional V1,
  public repository-only, build/test-only and prohibited content. Root `config.yml` is retained only because the
  protected V1 transition bootstrap and characterization tests consume it. The active descriptor names the V2
  bootstrap; a production-source guard proves zero V2 use of the legacy package, Bukkit config API or root file; and
  the package gate requires the retained V1 class/config while rejecting repository examples from the JAR.

These corrections change documentation, tests and review tooling only. The authoritative correction-pass build reproduced the then-accepted distribution and aggregate SBOM bytes exactly.
Independent Owner Review 2 accepted that candidate and owner-froze baseline `2.x-stable-1`. The later A76 correction
does not alter either Stable surface, but it does change internal production setup behavior and therefore requires a
new exact artifact, targeted owner review and fresh owner-run qualification.

## Final-RC A76 setup correction

The initial owner-operated run proved the public boundary could accept raw target text without resolving its provider
value type or parsing/canonicalizing it until preview. The correction resolves DURATION and canonicalizes the documented
`PT1M`/`PT3M` targets at entry, while preview still recompiles the complete draft fail-closed. The normal path now uses
an owner-bound current session, generated immutable playtime IDs, shorter commands, contextual completion and actionable
help; the explicit full form remains available. All paths publish through the unchanged draft, preview, server-issued
acknowledgement and confirm authority.

Owner Review 1 then found OR8F-A76-01: the eager failure branches reused ancestry-only `setup.draft.invalid`, allowing
catalog output unrelated to the input error. Four exact target/operator/scope/completion identities now own truthful
catalog prose, structured facts and valid alternatives; true draft-ancestry behavior is unchanged. Command and Paper
presentation regressions cover both boundaries.

The owner-run blocker is evidence, not tester unfamiliarity. Targeted Owner Review 3 accepted the complete correction;
the subsequent public-only fresh run passed and closes the blocker without erasing either failed checkpoint.

## OR8F-A76-02 dormant Placeholder correction

The accepted Owner Review 2 artifact correctly booted dormant, but its scheduled Placeholder publisher retried player
lifecycle initialization once per second for online players. The runtime encoded the expected no-canonical-config
outcome in the same optional failure channel used for real active-state faults, so each retry logged a warning.

The narrow correction gives the publisher an explicit ready/dormant/failed initialization result. Dormant ticks skip
state materialization and logging, clear stale snapshots, and retain no marker. Live activation therefore initializes
the same player on the next refresh without restart. Active genuine failures still produce the exact actionable warning.
Unit coverage proves six dormant ticks, live activation and failure preservation; the real-Paper harness proves six
one-second dormant-online intervals, zero state rows/warnings and subsequent real Member projection before executing the
complete accepted A76 setup/progression/Prestige/restart flow.

The final owner run used the same exact distribution hash with a real player for more than ten dormant seconds, then
completed the guided `1m`/`3m` setup, acknowledgement/apply, live Member projection, both rank-ups, Prestige, restart
and HEALTHY Doctor. OR8F-A76-01/02 are closed and A76 is Satisfied. The thirteen observed verbosity/discoverability
findings are preserved as non-blocking debt in `V2_PHASE8F_A76_OWNER_ACCEPTANCE.md`; no output redesign or GUI work was
performed.

## Behavior and scope preservation

One production Java line was corrected after the exact Paper boot exposed a stale `Phase 8D candidate` startup banner; the banner now derives `2.0.0-rc.1` from Paper plugin metadata and the package test rejects regression. Protected V1 Java, Stable SDK and Stable Paper event sources are unchanged. No
MySQL/MariaDB/HikariCP production implementation, Phase 9 deployment path, Sonar exclusion or suppression was added.
The accepted transaction, journal, lease, uncertainty, reconciliation, configuration-publication, provider, event,
i18n and qualification behavior remains intact.

## Acceptance accounting

The mechanically audited ledger is **63 Satisfied / 12 Partial / 1 Later**:

- A63 remains Partial pending real MaddKraft clone/deployment migration qualification in Phase 9.
- A64 remains Later because external SQL production support is deferred post-2.0.
- A76 is Satisfied by the accepted real-player owner-operated fresh-RC run.

Phase 8F and the Phase 8 work phase meet their exit criteria. The broader 2.0 production gate remains open because A63
retains the real MaddKraft clone/deployment migration in Phase 9; no GA or production-readiness claim is made.
