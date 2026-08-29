# Phase 8F release-hardening work matrix

**Prepared:** 2026-08-24<br>
**Branch:** `v2/phase-8f`<br>
**Frozen input:** merged `main` at `993599dc47cacc76b6372302d338d1850bf2896e`<br>
**Scope:** packaging, release hardening, final compatibility baseline, and an exact technical release-candidate Paper qualification. A76 and Phase 9 are excluded.

This matrix was written before broad Phase 8F implementation. It translates the master specification, accepted owner decisions, current traceability/failure state, build descriptors, public documentation, and Phase 8B API inventories into explicit work and gates. A result is not `PASS` until the named evidence exists for the final candidate.

| Area | Frozen input / observed gap | Phase 8F work | Mandatory gate | Planned evidence |
|---|---|---|---|---|
| Final API baseline | Owner accepted 46 Bukkit-free SDK Stable candidates and six Paper Stable event candidates, but D-138/D-149 and P8B-F035 explicitly leave them unfrozen. Existing inventories say `CANDIDATE`. | Reinventory exact compiled public members, confirm Javadocs/nullability/threading/bounds, freeze those two surfaces as the 2.x Stable baseline, and add a mechanical signature guard. Keep 5 Experimental, 17 Internal/Should Not Be Public, and 41 Legacy/Pending Removal types outside the guarantee. | Exact accepted type counts and signatures; no Bukkit/vendor/persistence leakage; example consumer builds; a changed Stable signature fails verification unless the recorded baseline is deliberately revised under compatibility policy. | `V2_PHASE8F_COMPATIBILITY_BASELINE.md`, `V2_PHASE8F_API_INVENTORY.md`, `V2_PHASE8F_PAPER_API_INVENTORY.md`, focused test/build logs. |
| Release identity and versioning | Reactor, descriptor, docs, CI, and artifact filename still use `2.0.0-SNAPSHOT`; README says release hardening is pending. | Select one semantic-style non-GA final Phase 8 release-candidate identity and make Maven coordinates, descriptor filtering, filename, docs, CI, and evidence agree. Preserve Phase 9/production-readiness boundary. | No `SNAPSHOT` in current V2 release surfaces; embedded descriptor and Maven metadata match the candidate; historical evidence remains historical. | Release identity rows in this matrix, package audit, install qualification, final documentation audit. |
| Reproducible builds | Fixed `project.build.outputTimestamp` and a two-build CI byte comparison exist, but the check is filename-specific and no final-candidate two-build evidence is recorded. | Make the check version-safe; run at least two independent clean builds of the same tree and compare distribution plus aggregate SBOM bytes/hashes. Record environment and commands. | Both build pairs are byte-identical; clean rebuilds do not require source edits or untracked inputs. | `V2_PHASE8F_REPRODUCIBLE_BUILD_EVIDENCE.md` and preserved logs/hashes. |
| Distribution package contents | Shade includes first-party modules and SQLite; optional APIs are `provided`. No final allow/deny package manifest or embedded release-document audit exists. | Inventory every entry and dependency origin; enforce required descriptor/defaults/locales/metadata and forbidden tests, qualification code, optional vendor APIs/implementations, signatures, caches, databases, worlds, secrets, and duplicate/conflicting descriptors. | Exact manifest reviewed; no third-party plugin implementation, external-SQL production code, qualification harness, V1 descriptor entry point, or build/runtime debris. | `V2_PHASE8F_PACKAGE_CONTENT_AUDIT.md`, exact entry manifest, automated package test/check. |
| Dependencies, shade, licenses, and SBOM | Enforcer convergence and aggregate CycloneDX run; optional APIs are provided; SQLite is intentionally shaded. Root metadata says all rights reserved, but public-release license/notice treatment and shaded-license preservation require an explicit audit. MySQL/MariaDB foundation dependencies must not be presented as support. | Reconcile dependency tree, shaded contents, license/notice files, Maven scopes, SBOM components, and documented support. Preserve embedded SQLite and optional-provider isolation; retain external-SQL work only as explicitly deferred architecture evidence. | Convergence/checkstyle/SBOM pass; no `systemPath`/system scope; no optional plugin implementation shaded; required legal metadata retained; SBOM matches the exact candidate. | Package audit, dependency/license matrix, aggregate SBOM hash and component audit. |
| Install, upgrade, rollback, and recovery | Phase 8C proves SQLite migration/backup/restore mechanics and public docs describe safe operation. Current installation text names the Phase 8D snapshot. | Update exact candidate install/upgrade/rollback instructions without inventing an online backup command. Verify clean install, supported schema-prefix migration, unchanged restart, and disposable restore/rollback rehearsal against the candidate. | No blind live DB copy; checksums checked; startup diagnostics and failure modes stay fail-closed; SQLite-only one-process boundary remains explicit. | `V2_PHASE8F_RELEASE_INSTALL_QUALIFICATION.md` and sanitized process logs. |
| Public documentation consistency | Phase 8D public V2 docs are docs-as-tested. Stable API docs still describe an unfrozen candidate and install/README use the snapshot filename. Historical V1 documents coexist and must not be mistaken for V2 instructions. | Audit the authoritative V2 document set, links, commands, permissions, version, support matrix, API policy, config/schema versions, artifact names, checksum workflow, upgrade/rollback, performance and troubleshooting. Label historical V1 material rather than rewriting its evidence. | Docs tests pass; no current V2 page claims GA/production readiness, MySQL/MariaDB support, shared DB support, or A76 completion; all named paths/commands/permissions exist. | Final release/documentation audit and focused documentation tests. |
| Config, example, and schema parity | Frozen generic example is byte-identical to wizard output and canonically compilable; schema is split across five documents with SQLite schema 11. | Reconfirm packaged defaults, exact public example, schema registry, generated setup documents, supported database history, and docs use the same versions/types/order. | Exact example compiles/applies; defaults remain dormant; no hidden field or stale schema/version claim; no accepted behavior change. | Config/example/schema section of final audit and focused tests. |
| Exact-candidate technical Paper qualification | Prior Phase 8D/8E evidence qualifies earlier artifacts. Phase 8F needs the final byte-identical candidate; A76 must remain unrun. | Install the exact candidate into a fresh disposable Paper 26.1.2 build 74 environment, verify dormant first boot, documented setup/active revision, operation/restart/recovery health, and repeat unchanged restart. Reuse only first-party qualification tooling and licensed external artifacts outside review bundles. | Candidate hash before/after run is identical; process reaches `Done`, assertions pass, clean shutdown/restart is coherent; no source inspection/manual hidden action is represented as A76. | Sanitized Phase 8F Paper logs plus `V2_PHASE8F_RELEASE_INSTALL_QUALIFICATION.md`. |
| Acceptance accounting | Phase 8E ledger is 62 Satisfied / 13 Partial / 1 Later. A63 is Partial, A64 Later, A76 Partial/Deferred. P8B-F035 is open for the baseline freeze. | Close only compatibility/release-hardening findings proven by Phase 8F. Preserve A63/A64/A76 classifications and Phase 9 ownership; recalculate totals mechanically if and only if an A01-A76 row changes. | 76 rows exactly; counts sum to 76; no `Later` is relabeled `Satisfied`; A76 is explicitly unrun. | Updated `V2_TRACEABILITY.md`, failure register, `STATUS.md`, `DECISIONS.md`, implementation summary. |
| Scope and publication boundary | Owner authorized local Phase 8F implementation/evidence only. | Keep all work unstaged, uncommitted, unpushed; do not open/merge a PR; do not touch protected V1 Java; do not begin Phase 9 or external-SQL production support. | Zero staged paths; zero protected V1 Java changes; zero Phase 9 paths; zero MySQL/MariaDB/HikariCP production paths; final exact worktree report. | `V2_PHASE8F_FILE_MANIFEST.md`, scope scans, `git diff --check`, final report. |

## Owner Review 1 correction disposition

Owner Review 1 rejected the evidence seal, not the accepted runtime behavior. OR8F-01 is addressed by an exact Git
SHA in the generated review manifest plus two independent unresolved-variable guards. OR8F-02 is addressed by tracing
every top-level/config/example category to the real shaded JAR: root `config.yml` and the protected
`gg.maddkraft.prestige` implementation are required transitional V1 material; the active V2 bootstrap and four dormant
V2 defaults plus integration metadata are separate; the generic example is repository-only; and test/qualification/runtime debris remains
prohibited. The correction is a candidate until Owner Review 2 accepts it.

## Release identity decision

The repository had no earlier release-candidate suffix convention beyond semantic-style versioning. Phase 8F therefore selects SemVer-compatible Maven version `2.0.0-rc.1`, artifact `MaddPrestige-2.0.0-rc.1.jar`, API coordinate `net.maddkraft:maddprestige-api:2.0.0-rc.1`, release channel `release-candidate`, and compatibility baseline `2.x-stable-1`. The embedded Paper descriptor and JAR manifest must report those same values. The pre-release suffix truthfully preserves the A76 and Phase 9 boundaries and makes no GA/production-readiness claim.

## Fixed acceptance boundaries

- A63 remains `Partial` pending the actual MaddKraft deployment-clone migration in Phase 9.
- A64 remains `Later`; MySQL/MariaDB/HikariCP production support, semantic parity, outage/failover, row-lock/deadlock, and shared-database/multi-process operation are deferred post-2.0.
- A76 was `Partial / Deferred` during technical qualification; the later D-179 owner-operated final-RC gate is now complete and Satisfied.
- The final acceptance ledger is 63 Satisfied / 12 Partial / 1 Later after the authorized A76 reclassification.
- The compatibility freeze covers only the accepted 46-type Bukkit-free Stable SDK surface and six-type Stable Paper event surface. Experimental, Internal/Should Not Be Public, Legacy/Pending Removal, implementation, persistence, and optional-vendor surfaces are excluded.

## Post-freeze A76 correction addendum — 2026-08-28

Owner Review 2 froze `2.x-stable-1`. A76 Setup Correction Owner Review 1 rejected OR8F-A76-01 because eager input
failures reused the ancestry-only `setup.draft.invalid` catalog identity. The corrected Owner Review 2 candidate owns
four exact requirement-input identities and preserves true draft ancestry unchanged. The later owner-operated public-only A76 run exposed delayed untyped requirement
target validation and material common-path UX friction. The correction keeps the frozen surfaces and canonical safety
model, changes only internal setup/composition behavior plus public guidance, and requires targeted owner review and a
new owner run. The predeclared matrix above remains historical planning evidence; at that boundary A76 was Partial /
Blocked, not unrun, waived, failed or Satisfied.

The next fresh owner run against the accepted Owner Review 2 artifact exposed OR8F-A76-02: the one-second Placeholder
refresh scheduler classified expected no-canonical-config player initialization as a failure and emitted recurring
warnings. The Owner Review 3 candidate introduces an explicit dormant lifecycle outcome, skips state materialization
without logging while dormant, retries normally after live activation and retains active genuine-failure diagnostics.
It does not redesign setup, add GUI work, alter a Stable surface or begin Phase 9. At that boundary A76 required
targeted review and a new owner-operated empty-directory run against the exact corrected artifact.

## Final A76 and Phase 8 exit reconciliation — 2026-08-28

Targeted Owner Review 3 accepted the exact corrected artifact. The owner then completed the replacement public-only
acceptance with a real player in a new directory: silent dormant operation, live activation, guided typed setup,
preview/acknowledgement/apply, rank-up, Prestige, restart and Doctor all passed. OR8F-A76-01/02 are closed and A76 is
Satisfied. Phase 8F and the Phase 8 work phase meet exit criteria. Phase 9 remains mandatory for A63, deployment,
migration and production qualification, so this is release-candidate readiness rather than GA readiness. UX-01 through
UX-13 remain deferred non-blocking work and neither output redesign nor GUI implementation begins here.
