# Phase 8D administration multi-throw-site audit

**Candidate:** Owner Review Correction Pass 6 for independent Owner Review 7  
**Baseline:** `d481a9db7cd67108ff77f97e2d64d737e9096276`  
**Date:** 2026-08-22  
**Scope:** 111 literal production `AdministrationException` construction sites, 84 public codes

## Mechanical inventory

`PaperMessageServiceTest.administrationMultiThrowInventoryMatchesReviewedCompatibilityRegistry` walks every
`src/main/java` tree, excludes generated `target` trees, extracts literal `AdministrationException` codes and compares
the complete 84-code set with `SemanticPresentation.knownAdministrationCodes()`. It then compares every code occurring
at least twice with the reviewed registry below. The current result is 19 multi-source codes: 17 compatible shared
contracts and two codes requiring typed occurrence discrimination. A future added, removed, renamed or newly repeated
literal code fails the inventory test until this register and the public presentation contract are deliberately
reviewed.

Source locations below are relative to `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/`.
`ConfigurationAdministrationService` is abbreviated `CAS`.

## Complete multi-source compatibility register

| Code and source locations | Exact conditions at the production sites | Consequence and correct remediation | Public facts | Verdict / discriminator |
|---|---|---|---|---|
| `config.acknowledgement.actor_mismatch` — `admin/config/CAS.java:592,608` | Confirm or discard is attempted by an actor other than the server token's owner. | No authority is consumed by the wrong actor; the owner continues or the current actor requests a new token. | None; actor identity is not exposed. | **Compatible shared contract.** |
| `config.acknowledgement.already_used` — `admin/config/CAS.java:642,653` | A terminal consumed-token marker is found during confirm, or an already consumed token is discarded. | The token cannot be reused; preview and request a new server-issued token. | None. | **Compatible shared contract.** |
| `config.acknowledgement.unknown` — `admin/config/CAS.java:587,646` | Confirm lookup cannot find live authority, or discard finds neither live nor consumed authority. | No server authority is available; generate a fresh preview/token. | None. | **Compatible shared contract.** |
| `config.active.absent` — `admin/config/CAS.java:85,110`; `admin/ui/GuiSessionService.java:286` | Edit, rollback or visual mutation requires an active configuration but none exists. | The requested production mutation cannot start; complete and activate initial setup. | None. | **Compatible shared contract.** The surface differs, not the state or remedy. |
| `config.apply.failed` — `admin/config/CAS.java:826,862` | Site 862 catches an unexpected pre-authoritative-state failure. Site 826 follows failed runtime publication after snapshot activation and then either restores the prior pointer or fails to restore it. | Unchanged: diagnose/correct and make a fresh preview. Restored: prior state is safe, then diagnose/correct and make a fresh preview. Restore failed: state is uncertain; do not retry and perform established recovery/reconciliation. | `revision`; typed variant is also passed as catalog data. | **Not compatible without discrimination.** `CONFIG_APPLY_PRIOR_STATE_UNCHANGED`, `CONFIG_APPLY_PRIOR_STATE_RESTORED`, `CONFIG_APPLY_RECONCILIATION_REQUIRED`. |
| `config.apply.kind_mismatch` — `admin/config/CAS.java:1006`; `admin/ui/CanonicalGuiMutationExecutor.java:86,98` | Normal/setup/rollback apply is invoked for a draft whose server-owned apply kind differs. | Nothing is applied; reopen the draft through its correct workflow. | None. | **Compatible shared contract.** |
| `config.document.missing` — `admin/config/CAS.java:145,278,321,961` | Scalar edit, stage add, stage remove or shared document access cannot find the canonical owning YAML document. | The requested draft operation cannot be lossless; restore the named document first. | `document`. | **Compatible shared contract.** |
| `config.draft.apply_in_progress` — `admin/config/CAS.java:1075,1228` | Owned-draft access observes the apply claim, or document replacement races an apply claim. | No concurrent edit is accepted; wait for the current outcome. | None. | **Compatible shared contract.** |
| `config.draft.cancelled` — `admin/config/CAS.java:179,729` | The draft disappears while preview preparation or apply validation is in flight. | The removed authority is never revived and the candidate is not activated; create and preview a new draft. | None. | **Compatible shared contract.** |
| `config.draft.changed_during_apply` — `admin/config/CAS.java:736,743` | Live version/content/actor no longer matches the prepared candidate, or compare-and-set apply claiming loses a race. | The older candidate is not activated; review and preview current state. | None. | **Compatible shared contract.** |
| `config.draft.concurrent_edit` — `admin/config/CAS.java:158,361,389,949` | Scalar edit, remap selection, remap removal or shared document replacement loses its draft-version compare-and-set. | The competing edit wins; reload/review and retry against the current draft. | None. | **Compatible shared contract.** |
| `config.path.not_listable` — `admin/config/CAS.java:203,210` | The path has no supported lossless collection resolver, or its schema type is scalar rather than `LIST`/`MAP`. | Listing is rejected; select a schema-owned list/map collection, or use `config get`/`config explain` for a scalar. | `path`. | **Compatible shared contract.** Public prose explicitly recognizes both list and map. |
| `config.path.unknown` — `admin/config/CAS.java:135,200,974`; `admin/config/ConfigurationIntrospectionService.java:52` | Edit, list, structural validation or introspection cannot resolve the canonical schema path. | No unknown path is accessed; use config search to select a schema-owned path. | `path`. | **Compatible shared contract.** |
| `config.preview.required` — `admin/config/CAS.java:474,684` | Acknowledgement preparation or configuration apply is requested without an exact retained preview. | No authority/apply occurs; preview and review the exact current draft. | None. | **Compatible shared contract.** |
| `config.preview.stale` — `admin/config/CAS.java:687,691` | Candidate hash or draft version differs from the last preview. | The draft-side stale candidate cannot apply; generate and review a fresh preview. Active-revision drift remains isolated to `config.revision.stale`. | None. | **Compatible shared contract.** |
| `config.revision.stale` — `admin/config/CAS.java:492,750` | Active revision changed before acknowledgement issuance or before apply. | Stale authority is rejected; reopen current state and obtain a fresh preview/token. | None. | **Compatible shared contract.** |
| `config.validation.blocked` — `admin/config/CAS.java:479,756` | Site 479: validation errors prevent acknowledgement preparation; high-risk findings are inputs, not the blocker. Site 756: apply is blocked by errors and/or unacknowledged high-risk findings. | Acknowledgement context: correct errors, preview, then request authority. Apply context: correct errors and complete server-issued acknowledgement for remaining high-risk findings. | `errors`, `findings`; typed variant is also passed as catalog data. | **Not compatible without discrimination.** `CONFIG_VALIDATION_ACKNOWLEDGEMENT_PREPARATION`, `CONFIG_VALIDATION_APPLY`. |
| `setup.preview.required` — `admin/setup/SetupWizardService.java:238,252` | Setup apply or setup acknowledgement is requested without the exact setup preview. | No setup authority/apply occurs; preview and review the candidate. | None. | **Compatible shared contract.** |
| `stage.change.remap_invalid` — `admin/config/CAS.java:313,352` | Stage removal or explicit remap selection maps the missing source stage to itself. | No invalid migration plan is accepted; choose a distinct enabled ordered target. | `source`, `target`. | **Compatible shared contract.** |

## Owner Review 5 single-source correction

`stage.change.remap_snapshot_stale` has one production exception site (`admin/config/CAS.java:703`), so it is not in
the multi-source register. Its prior catalog cause was nevertheless wrong. The site now emits source, target, previewed
entry count and current entry count after comparing the sealed persisted-reference snapshots. Public prose says that
persisted player stage references changed after preview and requires a fresh remap preview; it does not claim that the
candidate configuration changed.

## Presentation and compatibility result

The 84 stable diagnostic codes remain unchanged. `AdministrationSemanticVariant` is an immutable enumerated occurrence
discriminator whose constructor verifies that its diagnostic code matches the exception code. `SemanticPresentation`
selects five dedicated variant identities before the existing exact-code mapping, forwards only immutable facts plus
the enum name, and never reads exception message/remediation English. Built-in and alternate-catalog tests cover all
five variants plus the corrected stale-remap identity. Restore failure explicitly prohibits ordinary retry and requires
reconciliation.

Owner Review Correction Pass 6 deliberately rechecked this accepted register while auditing all remaining single-source
codes. OR8D-11 corrected the public `config.preview.stale` catalog prose to name only draft hash/version drift; it no
longer conflates the separate `config.revision.stale` condition. Multi-source counts, compatibility verdicts and typed
discriminators are otherwise unchanged.

The inventory, real-branch core tests, alternate-catalog tests and source-architecture guards establish occurrence-level
coverage. This supersedes any earlier statement that 84 code/message pairs alone proved every production occurrence.
