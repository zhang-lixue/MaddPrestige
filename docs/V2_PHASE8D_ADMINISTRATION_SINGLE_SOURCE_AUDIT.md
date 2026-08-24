# Phase 8D single-source administration semantic audit

**Candidate:** Owner Review Correction Pass 6 for independent Owner Review 7  
**Baseline:** `d481a9db7cd67108ff77f97e2d64d737e9096276`  
**Date:** 2026-08-22  
**Scope:** all 66 codes with exactly one literal production `AdministrationException` construction site

## Method and result

The current production inventory contains 112 literal exception occurrences and 85 public administration codes. The
19 codes with multiple occurrences remain covered by `V2_PHASE8D_ADMINISTRATION_MULTI_THROW_AUDIT.md`; the 66 remaining codes
are enumerated below. Each row was checked directly against the named production branch, not inferred from its code or
developer exception text. The review compares the exact trigger, safety consequence, catalog-owned remediation and
structured facts with the selected public semantic identity.

Owner Review Correction Pass 6 corrected the three known OR8D-11 cases and six additional single-source identities in
four drift groups found by this exhaustive review: actor ownership and terminal state for operation confirmations, the
lossless YAML conditions for structured stage add/remove, the absent-plan condition for remap removal, and the
missing-replacement condition for a GUI deletion action. No diagnostic code, Java production branch, structured-fact
contract or accepted architecture changed.

Phase 8E Correction Pass 2 additionally consolidates both enforcement paths for
`setup.integration.unconfigurable` through one structured exception helper. Both paths now supply the exact
`provider`, `component` and `requirement` facts, so that identity is included in this single-source register.

Result: all 66 single-source codes have accurate catalog semantics. Together with the current 19-code multi-source
register, every known public administration failure occurrence has been checked against its real production source
condition.

Source locations are relative to
`maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/`.
`ConfigurationAdministrationService` is abbreviated `CAS`.

## Complete single-source register

| Public code | Production source | Public semantic identity | Exact production trigger | Safety consequence | Correct catalog remediation | Structured facts |
|---|---|---|---|---|---|---|
| `command.player_required` | `admin/command/PhaseSixCommandService.java:682` | `command_player_required` | A self-context command has no player UUID on its actor. | The command does not guess a player target. | Supply the staff target UUID or run the self-service command in game. | — |
| `config.acknowledgement.expired` | `admin/config/CAS.java:614` | `config_acknowledgement_expired` | Confirmation occurs at or after the acknowledgement deadline. | The expired authority is removed and cannot apply. | Preview again and confirm a newly issued token before expiry. | — |
| `config.acknowledgement.findings_changed` | `admin/config/CAS.java:712` | `config_acknowledgement_findings_changed` | Re-preparation produces a finding seal different from the acknowledged seal. | The changed finding set receives no borrowed authority. | Review and acknowledge the current exact findings. | — |
| `config.acknowledgement.not_required` | `admin/config/CAS.java:487` | `config_acknowledgement_not_required` | Acknowledgement preparation finds no high-risk findings. | No unnecessary special authority is issued. | Use normal configuration apply for the exact preview. | — |
| `config.acknowledgement.server_authority_required` | `admin/config/CAS.java:1015` | `config_acknowledgement_server_authority_required` | A caller supplies finding codes directly to an apply entry point. | Caller data cannot create high-risk authority. | Request and confirm a server-issued token for the exact preview. | — |
| `config.acknowledgement.stale` | `admin/config/CAS.java:627` | `config_acknowledgement_stale` | Kind, draft version/hash, active base, retained preview or finding seal differs from the issued token. | The stale token is removed without applying. | Preview current state and request fresh exact-state authority. | — |
| `config.add.rejected` | `admin/config/CAS.java:233` | `config_add_rejected` | Lossless scalar append rejects the selected list path/value, including duplicate or invalid structure. | The draft document remains unmodified. | Add a unique schema-valid scalar through the structured list editor. | `path`, `value` |
| `config.draft.changed_during_prepare` | `admin/config/CAS.java:186` | `config_draft_changed_during_prepare` | Draft version or content hash changes while preview preparation is in flight. | The stale prepared candidate is not retained. | Stop concurrent edits and preview the stable current draft again. | — |
| `config.draft.concurrent_change` | `admin/config/CAS.java:462` | `config_draft_concurrent_change` | Draft cancellation loses its exact-state remove compare-and-set. | A newer draft version is not accidentally cancelled. | Review and cancel the latest version. | — |
| `config.draft.expired` | `admin/config/CAS.java:1066` | `config_draft_expired` | A retained draft has reached its configured lifetime. | The draft is removed and cannot authorize work. | Create a new draft from current active configuration. | — |
| `config.draft.owner_mismatch` | `admin/config/CAS.java:1070` | `config_draft_owner_mismatch` | The requesting actor differs from the draft creator. | Draft authority is not transferable. | Have its owner continue or open a separate draft. | — |
| `config.draft.unknown` | `admin/config/CAS.java:1061` | `config_draft_unknown` | The draft ID is absent from the retained registry. | No absent or discarded draft is revived. | Open a new draft from current active configuration. | — |
| `config.edit.rejected` | `admin/config/CAS.java:1137` | `config_edit_rejected` | Typed scalar conversion or lossless replacement rejects the value/YAML shape. | The draft remains unchanged. | Correct the value or use the structured editor owning that shape. | `path`, `type`, `value` |
| `config.history.finalize_failed` | `admin/config/CAS.java:837` | `config_history_finalize_failed` | Active publication succeeded but durable history could not replace `ATTEMPTED` with `APPLIED`. | Configuration is active while history needs reconciliation; retrying the draft is unsafe. | Do not retry; run Doctor and reconcile the attempted outcome. | `revision` |
| `config.list.rejected` | `admin/config/CAS.java:215` | `config_list_rejected` | Lossless list/map inspection encounters malformed or incompatible draft structure. | No unsafe structural interpretation is returned. | Correct and validate the draft before listing again. | `path` |
| `config.path.not_editable` | `admin/config/CAS.java:139` | `config_path_not_editable` | A schema path has no lossless scalar path resolver. | The service refuses a lossy scalar edit. | Use the owning structured editor or edit the YAML draft. | `path` |
| `config.path.type_mismatch` | `admin/config/CAS.java:978` | `config_path_type_mismatch` | The requested structural operation expects a different schema type. | No mismatched structural mutation is attempted. | Use the operation matching the schema-owned type. | `path`, `current`, `type` |
| `config.preview.candidate_missing` | `admin/config/CAS.java:695` | `config_preview_candidate_missing` | A retained preview has no matching prepared candidate object. | Apply stops before re-preparation or activation. | Preview the current draft again. | — |
| `config.remove.rejected` | `admin/config/CAS.java:253` | `config_remove_rejected` | Lossless scalar removal rejects an absent/invalid list path or value. | The draft document remains unmodified. | Select an existing scalar and use the structured list editor. | `path`, `value` |
| `config.rollback.not_applied` | `admin/config/CAS.java:117` | `config_rollback_not_applied` | The selected historical revision status is not `APPLIED`. | A failed/attempted revision cannot seed rollback. | Choose an `APPLIED` revision from history. | `revision`, `status` |
| `config.rollback.not_prepared` | `admin/config/CAS.java:417` | `config_rollback_not_prepared` | A draft submitted through rollback apply has no rollback-source provenance. | The draft is not applied as rollback. | Prepare a rollback draft from an `APPLIED` history revision, preview it, then apply. | — |
| `config.rollback.unknown` | `admin/config/CAS.java:113` | `config_rollback_unknown` | The requested target revision is absent from retained history. | No unknown content becomes a rollback draft. | List history and select an available revision. | `revision` |
| `config.snapshot.activate_failed` | `admin/config/CAS.java:800` | `config_snapshot_activate_failed` | Atomic activation of an already prepared snapshot throws. | Prior active pointer/runtime remain authoritative and the transition is marked failed-safe. | Keep prior state authoritative and run Doctor before retrying. | — |
| `config.snapshot.prepare_failed` | `admin/config/CAS.java:781` | `config_snapshot_prepare_failed` | Durable snapshot preparation throws before `activate()` is called. | Activation never occurs; current active configuration remains unchanged and authoritative. | Inspect validation, persistence/storage health and logs, correct the failure, then re-preview before apply. | — |
| `config.value.not_allowed` | `admin/config/CAS.java:1105` | `config_value_not_allowed` | A replacement is outside a non-empty static schema allowlist. | The disallowed scalar is not written. | Inspect config explanation and choose a documented value. | `path` |
| `confirmation.actor_mismatch` | `admin/OperationConfirmationService.java:61` | `confirmation_actor_mismatch` | The confirming actor differs from the actor that requested the operation confirmation. | The token remains bound to its original actor and is not consumed by the mismatch. | Its requesting actor may use it, or the current actor requests a fresh preview. | — |
| `confirmation.already_used` | `admin/OperationConfirmationService.java:116` | `confirmation_already_used` | Exact-token removal loses a concurrent consume race. | At most one execution uses the token. | Request a fresh operation preview/token. | — |
| `confirmation.config_stale` | `admin/OperationConfirmationService.java:73` | `confirmation_config_stale` | Active configuration no longer equals the previewed revision. | The token is removed and stale plans do not execute. | Request and review a new preview under current revision. | — |
| `confirmation.expired` | `admin/OperationConfirmationService.java:67` | `confirmation_expired` | Confirmation occurs at or after the operation-token deadline. | The token is removed and no operation executes. | Request and use a fresh preview before expiry. | — |
| `confirmation.unknown` | `admin/OperationConfirmationService.java:57` | `confirmation_unknown` | Initial lookup finds no token because it is unknown, expired/pruned or already consumed. | No absent authority can execute. | Request a new server-issued operation preview. | — |
| `gui.action.forged` | `admin/ui/GuiSessionService.java:258` | `gui_action_forged` | The clicked action ID is absent from the server-owned session map. | Player items cannot create authority. | Close and reopen the GUI. | — |
| `gui.action.stale` | `admin/ui/GuiSessionService.java:264` | `gui_action_stale` | A mutating action's expected active revision differs from current active revision. | The stale GUI mutation is not executed. | Reopen the GUI and obtain a fresh preview/action. | — |
| `gui.mutation.context_missing` | `admin/ui/CanonicalGuiMutationExecutor.java:133` | `gui_mutation_context_missing` | A server-owned mutation lacks a required draft, path, value, token or reason field. | No incomplete mutation executes. | Reopen the GUI to build a complete action from current state. | — |
| `gui.mutation.kind_invalid` | `admin/ui/CanonicalGuiMutationExecutor.java:60` | `gui_mutation_kind_invalid` | The selected action kind is outside canonical configuration mutations. | No unrelated action is treated as a mutation. | Reopen the control panel and choose a current configuration action. | — |
| `gui.session.actor_mismatch` | `admin/ui/GuiSessionService.java:253` | `gui_session_actor_mismatch` | The current actor differs from the session owner. | GUI authority is not transferable. | Open a separate GUI session. | — |
| `gui.session.expired` | `admin/ui/GuiSessionService.java:249` | `gui_session_expired` | Session is absent or past its expiry instant. | The session is removed and no action runs. | Reopen the GUI for current server-owned controls. | — |
| `gui.target.missing` | `admin/ui/CanonicalGuiActionExecutor.java:71` | `gui_target_missing` | A canonical player operation action has no target UUID. | No player is guessed or mutated. | Reopen the intended player view. | — |
| `operation.preview.blocked` | `admin/AdministrationException.java:60` | `operation_preview_blocked` | Canonical authorization returns one or more typed blockers. | No executable plan/confirmation is produced. | Resolve every typed blocker and preview again. | `operation`, typed blockers |
| `permission.denied` | `admin/PermissionSubject.java:30` | `permission_denied` | The permission subject lacks the exact required node. | The protected action does not execute. | Grant only the documented node or use an authorized actor. | `permission` |
| `setup.acknowledgement.unknown` | `admin/setup/SetupWizardService.java:290` | `setup_acknowledgement_unknown` | Token has no associated setup session because it is unknown, expired or non-setup. | Setup confirmation is not delegated to an unrelated token. | Preview setup and request a fresh setup acknowledgement. | — |
| `setup.already_active` | `admin/config/CAS.java:97` | `setup_already_active` | Initial-draft creation finds an active configuration. | First-run setup cannot replace production state. | Create a normal draft from active revision. | — |
| `setup.baseline.unknown` | `admin/setup/SetupWizardService.java:161` | `setup_baseline_unknown` | Selected baseline ID is absent from configured wizard stages. | The invalid baseline is not stored. | Choose a configured stage ID. | `stage` |
| `setup.draft.invalid` | `admin/config/CAS.java:432` | `setup_draft_invalid` | Setup apply receives a draft with active-base or rollback ancestry. | Existing deployments cannot be overwritten through initial setup. | Use normal edit or rollback workflow. | — |
| `setup.group.missing` | `admin/setup/SetupWizardService.java:236` | `setup_group_missing` | An externally projected post-initial stage has no selected existing group. | Setup preview stops; MaddPrestige creates no external group. | Select/create a valid group through the provider first. | `stage`, `provider` |
| `setup.incomplete` | `admin/setup/SetupWizardService.java:228` | `setup_incomplete` | Setup has fewer than two stages or no baseline. | No incomplete candidate draft is generated. | Add ordered stages and select the baseline. | — |
| `setup.integration.unconfigurable` | `admin/setup/SetupWizardService.java:670` | `setup_integration_unconfigurable` | A selected built-in requirement/reward needs dimensions or metadata that simple setup cannot generate; the same helper is called at selection time and defensively during document generation. | Preview/publication stops before an incomplete revision can disable its own required provider. | Choose a representable built-in selection or use canonical configuration administration for the exact integration metadata. | `provider`, `component`, `requirement` |
| `setup.prestige.stage_unknown` | `admin/setup/SetupWizardService.java:344` | `setup_prestige_stage_unknown` | Prestige eligibility/reset references a stage absent from the setup session. | Invalid prestige stage references are not stored. | Select a configured setup stage. | `stage`, `purpose` |
| `setup.provider.required` | `admin/setup/SetupWizardService.java:142` | `setup_provider_required` | A stage selects an external group while the setup has no rank provider. | The invalid projected stage is not added. | Choose the provider first or make the stage internal-only. | `stage`, `group` |
| `setup.requirement.baseline` | `admin/setup/SetupWizardService.java:183` | `setup_requirement_baseline` | Eligibility requirement targets the selected baseline stage. | Baseline cannot become gated by forward progress. | Assign the requirement to a later stage. | `stage` |
| `setup.requirement.duplicate` | `admin/setup/SetupWizardService.java:194` | `setup_requirement_duplicate` | Requirement ID already exists in general or another stage assignment. | Immutable requirement identity remains unique. | Choose one unique immutable ID for the requested stage. | `requirement`, `stage` |
| `setup.requirement.metric_unknown` | `admin/setup/SetupWizardService.java:683` | `setup_requirement_metric_unknown` | Active provider discovery does not advertise the chosen metric with a value type. | Setup does not guess metric type. | Run discovery and select an advertised metric. | `provider`, `metric` |
| `setup.session.owner_mismatch` | `admin/setup/SetupWizardService.java:322` | `setup_session_owner_mismatch` | Requesting actor differs from setup-session owner. | Session authority is not transferable. | Have the owner continue or start a separate session. | — |
| `setup.session.unknown` | `admin/setup/SetupWizardService.java:318` | `setup_session_unknown` | Setup session ID is absent after expiry pruning. | No absent session is resumed. | Start or resume a current session. | — |
| `setup.stage.duplicate` | `admin/setup/SetupWizardService.java:138` | `setup_stage_duplicate` | Immutable stage ID already occurs in the setup session. | Duplicate identity is not added. | Choose a unique immutable stage ID. | `stage` |
| `setup.text.control_character` | `admin/setup/SetupWizardService.java:740` | `setup_text_control_character` | Setup text contains an ISO control other than newline, carriage return or tab. | Unsafe control text is not stored. | Use printable Unicode; supported whitespace is escaped safely. | `component` |
| `stage.add.rejected` | `admin/config/CAS.java:298` | `stage_add_rejected` | Lossless `progression.yml` mapping/order insertion rejects duplicate identity or unsupported YAML structure. | No partial stage insertion is published to the draft. | Choose a unique ID and supported block-style document, then retry the structured edit. | `stage` |
| `stage.change.remap_candidate_invalid` | `admin/config/CAS.java:1159` | `stage_change_remap_candidate_invalid` | Candidate progression cannot compile before remap validation. | No remap is selected against an invalid candidate. | Correct `progression.yml` first. | — |
| `stage.change.remap_missing` | `admin/config/CAS.java:374` | `stage_change_remap_missing` | Mapping removal is requested when the draft has no remap plan. | No nonexistent plan is mutated. | Review current mappings and select replacements before removing one. | — |
| `stage.change.remap_required` | `admin/ui/GuiSessionService.java:269` | `stage_change_remap_required` | A GUI `DELETE_STAGE` action has no explicit replacement field. | The incomplete deletion action does not execute. | Use the remap selector to choose a replacement before submitting deletion. | — |
| `stage.change.remap_snapshot_stale` | `admin/config/CAS.java:703` | `stage_change_remap_snapshot_stale` | Sealed persisted-reference snapshot differs between preview and apply preparation. | The stale migration is not applied. | Regenerate/review remap preview against current persisted state. | `source`, `target`, `before`, `after` |
| `stage.change.remap_source_missing` | `admin/config/CAS.java:380` | `stage_change_remap_source_missing` | Requested source mapping is absent from the current draft plan. | No other mapping is removed accidentally. | Review the current draft remap. | `source` |
| `stage.change.remap_source_present` | `admin/config/CAS.java:1165` | `stage_change_remap_source_present` | Proposed remap source still exists in candidate progression. | Persisted references cannot be remapped away from a live source. | Remove the source stage before selecting its replacement. | `source`, `target` |
| `stage.change.remap_target_missing` | `admin/config/CAS.java:1172` | `stage_change_remap_target_missing` | Replacement is absent, disabled or outside candidate order. | Persisted references cannot target an invalid stage. | Select an enabled ordered candidate stage. | `source`, `target` |
| `stage.change.transition_reconciliation_pending` | `admin/config/CAS.java:847` | `stage_change_transition_reconciliation_pending` | Configuration became active but marking its unsafe-stage transition applied failed. | Active configuration is authoritative while reservation cleanup remains pending. | Do not retry; run transition recovery for the revision. | `revision` |
| `stage.change.transition_stale` | `admin/config/CAS.java:790` | `stage_change_transition_stale` | Complete unsafe-stage scope cannot be reserved/revalidated before activation. | No configuration is activated. | Preview current references and operation state, then retry. | — |
| `stage.remove.rejected` | `admin/config/CAS.java:338` | `stage_remove_rejected` | Lossless progression mapping/order removal rejects an absent stage, invalid replacement plan or unsupported YAML structure. | No partial stage removal is published to the draft. | Select an existing stage, valid replacement where required and supported block-style document. | `stage`, `target` |

## Mechanical completeness guard

`PaperMessageServiceTest.administrationSingleSourceInventoryMatchesSemanticAudit` walks every production Java source,
extracts literal constructor codes and derives the exact single-source set from occurrence counts. It parses the rows
above and requires exact equality: 66 single-source rows, 19 multi-source codes, and a union equal to all 85 known
public codes. A new, removed, renamed or reclassified single-source code fails until this source-level audit is updated
deliberately.

The built-in catalog test covers the corrected source objects, consequences, neighboring-code distinctions and
internal-English prohibition. Alternate-catalog coverage proves the catalog remains the prose authority while exact
structured facts survive key selection unchanged.
