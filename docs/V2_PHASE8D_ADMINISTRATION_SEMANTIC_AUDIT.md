# Phase 8D administration semantic audit

**Candidate:** Owner Review Correction Pass 6 for independent Owner Review 7<br>
**Scope:** all 89 known public `AdministrationException` codes
**Result:** every production occurrence has an exact code identity plus a typed occurrence identity where one code is
semantically overloaded

## Audit method and architecture

Every production throw site was read against its exact failure condition and recovery instruction. The presentation
boundary selects one of 89 dedicated base identities by exact code equality and, for the two overloaded codes, one of
five typed occurrence identities. It copies only immutable structured facts and lets the selected locale own all public
prose. A genuinely unknown code still uses the bounded generic fallback.

The earlier statement that one identity per code was sufficient is superseded. The mechanical occurrence inventory
found 116 literal production sites and 19 codes occurring at least twice. Seventeen share a compatible public contract;
`config.apply.failed` and `config.validation.blocked` require `AdministrationSemanticVariant`. The deliberate site-level
register is `V2_PHASE8D_ADMINISTRATION_MULTI_THROW_AUDIT.md`. The remaining 70 codes each have one literal source site;
their exact trigger, consequence, remedy and facts are recorded in
`V2_PHASE8D_ADMINISTRATION_SINGLE_SOURCE_AUDIT.md` and mechanically reconciled to production.

## Complete reviewed register

| Public code | Semantic identity | Exact condition and correct action | Structured public facts |
|---|---|---|---|
| `command.player_required` | `command_player_required` | A self-context command has no player actor; supply the staff UUID or run it in game. | — |
| `config.acknowledgement.actor_mismatch` | `config_acknowledgement_actor_mismatch` | Another administrator owns the acknowledgement; its owner must confirm or the current actor must request a new one. | — |
| `config.acknowledgement.already_used` | `config_acknowledgement_already_used` | The single-use acknowledgement was consumed; preview and request another. | — |
| `config.acknowledgement.expired` | `config_acknowledgement_expired` | The acknowledgement lifetime elapsed; preview and request another. | — |
| `config.acknowledgement.findings_changed` | `config_acknowledgement_findings_changed` | The sealed finding set changed; review and acknowledge the current exact findings. | — |
| `config.acknowledgement.not_required` | `config_acknowledgement_not_required` | The exact preview has no high-risk finding; use normal apply without special acknowledgement. | — |
| `config.acknowledgement.server_authority_required` | `config_acknowledgement_server_authority_required` | Caller-supplied finding codes cannot authorize high risk; request and confirm a server-issued token for the exact preview. | — |
| `config.acknowledgement.stale` | `config_acknowledgement_stale` | Draft, active revision, or sealed findings changed; preview and acknowledge the new exact state. | — |
| `config.acknowledgement.unknown` | `config_acknowledgement_unknown` | The token is unknown, expired, consumed, or not server-issued; request a fresh token from preview. | — |
| `config.active.absent` | `config_active_absent` | No active configuration exists for edit, rollback, or GUI mutation; complete and activate initial setup. | — |
| `config.add.rejected` | `config_add_rejected` | A scalar list append is structurally invalid or duplicate; add a unique schema-valid value. | `path`, `value` |
| `config.apply.failed` | Base `config_apply_failed`; variants `config_apply_failed_prior_state_unchanged`, `config_apply_failed_prior_state_restored`, `config_apply_failed_reconciliation_required` | Failure before authoritative change permits diagnosis and a fresh preview; successful restoration reports the prior state safe; failed restoration reports uncertain state, prohibits ordinary retry and requires explicit reconciliation. | `revision`, typed `variant` |
| `config.apply.kind_mismatch` | `config_apply_kind_mismatch` | Normal, setup, and rollback authority do not match the draft; reopen the exact server-owned action. | — |
| `config.document.missing` | `config_document_missing` | A canonical YAML document is absent from the draft; restore that document before edit/apply. | `document` |
| `config.draft.apply_in_progress` | `config_draft_apply_in_progress` | The exact draft already has an apply owner; wait for its outcome. | — |
| `config.draft.cancelled` | `config_draft_cancelled` | The draft disappeared during preview/apply; create and preview a new draft. | — |
| `config.draft.changed_during_apply` | `config_draft_changed_during_apply` | The candidate no longer matches the live draft during apply; review and preview current state. | — |
| `config.draft.changed_during_prepare` | `config_draft_changed_during_prepare` | The draft changed while preview preparation ran; stop concurrent edits and preview again. | — |
| `config.draft.concurrent_change` | `config_draft_concurrent_change` | Cancellation lost a draft-version compare-and-set; review and cancel the latest version. | — |
| `config.draft.concurrent_edit` | `config_draft_concurrent_edit` | A scalar/structural/remap edit lost a draft-version compare-and-set; reload and retry against current state. | — |
| `config.draft.expired` | `config_draft_expired` | The retained draft exceeded its lifetime; create a new draft. | — |
| `config.draft.owner_mismatch` | `config_draft_owner_mismatch` | Another administrator owns the draft; its owner continues or the current actor creates another. | — |
| `config.draft.unknown` | `config_draft_unknown` | The draft is unknown or no longer retained; create a new draft from active state. | — |
| `config.edit.rejected` | `config_edit_rejected` | Scalar conversion or lossless YAML replacement failed; correct the typed value or use its structured editor. | `path`, `type`, `value` |
| `config.history.finalize_failed` | `config_history_finalize_failed` | The revision is active but durable history remains `ATTEMPTED`; do not retry the draft and reconcile history through Doctor. | `revision` |
| `config.list.rejected` | `config_list_rejected` | The selected list/map cannot be read from malformed draft structure; correct and validate the draft. | `path` |
| `config.path.not_editable` | `config_path_not_editable` | A schema path has no lossless scalar surface; use its owning YAML/structured editor. | `path` |
| `config.path.not_listable` | `config_path_not_listable` | A path is unresolved as a list/map or has scalar type; use the matching get/explain/structured operation. | `path` |
| `config.path.type_mismatch` | `config_path_type_mismatch` | The structural operation expects a different schema type; use the operation matching the path type. | `path`, `current`, `type` |
| `config.path.unknown` | `config_path_unknown` | The canonical schema path is unknown; discover a schema-owned path with config search. | `path` |
| `config.preview.candidate_missing` | `config_preview_candidate_missing` | The retained preview lacks its prepared candidate; preview the draft again. | — |
| `config.preview.required` | `config_preview_required` | Apply or acknowledgement was requested before preview; preview and review the exact draft. | — |
| `config.preview.stale` | `config_preview_stale` | Draft bytes/version changed after preview; create a new preview. | — |
| `config.remove.rejected` | `config_remove_rejected` | A scalar list removal is structurally invalid or absent; select an existing value. | `path`, `value` |
| `config.revision.stale` | `config_revision_stale` | Active revision changed after draft/preview preparation; reopen current state and obtain fresh preview/authority. | — |
| `config.rollback.not_applied` | `config_rollback_not_applied` | The historical revision is not `APPLIED`; choose an applied revision. | `revision`, `status` |
| `config.rollback.not_prepared` | `config_rollback_not_prepared` | A non-rollback draft reached rollback apply; prepare a rollback draft from applied history. | — |
| `config.rollback.unknown` | `config_rollback_unknown` | The requested revision is absent from retained history; choose an available applied revision. | `revision` |
| `config.snapshot.activate_failed` | `config_snapshot_activate_failed` | Atomic active-pointer selection failed; the prior pointer/runtime remain authoritative and Doctor must be checked. | — |
| `config.snapshot.prepare_failed` | `config_snapshot_prepare_failed` | Durable snapshot preparation failed before activation; active configuration remains unchanged, so correct preparation/storage health and re-preview before apply. | — |
| `config.validation.blocked` | Base `config_validation_blocked`; variants `config_validation_blocked_acknowledgement_preparation`, `config_validation_blocked_apply` | Acknowledgement preparation is blocked by validation errors, while apply is blocked by errors and/or unacknowledged high-risk findings. Each context renders its exact correction. | `errors`, `findings`, typed `variant` |
| `config.value.not_allowed` | `config_value_not_allowed` | A scalar is outside the schema allowlist; inspect the path and choose a documented value. | `path` |
| `confirmation.actor_mismatch` | `confirmation_actor_mismatch` | Another actor owns the operation token; its requester may confirm or the current actor must request a new preview. | — |
| `confirmation.already_used` | `confirmation_already_used` | The single-use operation token was consumed; request another preview. | — |
| `confirmation.config_stale` | `confirmation_config_stale` | Active configuration changed after operation preview; preview under the current revision. | — |
| `confirmation.expired` | `confirmation_expired` | The operation token lifetime elapsed; preview again. | — |
| `confirmation.unknown` | `confirmation_unknown` | Initial lookup finds an unknown, expired/pruned or already-consumed operation token; request a server-issued preview. | — |
| `gui.action.forged` | `gui_action_forged` | The clicked item is not a server-owned session action; reopen the GUI and trust no player item authority. | — |
| `gui.action.stale` | `gui_action_stale` | Active configuration changed after rendering a mutating action; reopen and preview again. | — |
| `gui.mutation.context_missing` | `gui_mutation_context_missing` | A server-owned mutation lacks a required field; reopen the GUI to build a complete action. | — |
| `gui.mutation.kind_invalid` | `gui_mutation_kind_invalid` | The selected action kind is not a canonical configuration mutation; choose a current control-panel action. | — |
| `gui.session.actor_mismatch` | `gui_session_actor_mismatch` | Another actor owns the GUI session; open a separate session. | — |
| `gui.session.expired` | `gui_session_expired` | The GUI session is absent or expired; reopen it. | — |
| `gui.target.missing` | `gui_target_missing` | A server-owned player action lacks its target; reopen the player view. | — |
| `operation.preview.blocked` | `operation_preview_blocked` | Typed canonical authorization blockers prevent planning; resolve the listed blockers and preview again. | `operation`, typed blockers |
| `permission.denied` | `permission_denied` | The actor lacks the exact required permission; grant only that permission or use an authorized actor. | `permission` |
| `setup.acknowledgement.unknown` | `setup_acknowledgement_unknown` | The token is unknown, expired, or not associated with setup; preview setup and request another. | — |
| `setup.already_active` | `setup_already_active` | First-run setup cannot replace an existing active configuration; use a normal draft. | — |
| `setup.baseline.unknown` | `setup_baseline_unknown` | The selected baseline ID is not a configured wizard stage; select a configured ID. | `stage` |
| `setup.draft.invalid` | `setup_draft_invalid` | Setup apply received a draft with active/rollback ancestry; use normal edit or rollback for existing deployments. | — |
| `setup.group.missing` | `setup_group_missing` | A projected post-baseline stage has no selected existing external group; select/create it externally because MaddPrestige never creates groups. | `stage`, `provider` |
| `setup.incomplete` | `setup_incomplete` | The ladder has fewer than two stages or no baseline; complete ordered stages and baseline. | — |
| `setup.integration.unconfigurable` | `setup_integration_unconfigurable` | Simple setup cannot represent required integration metadata; choose a representable selection or use canonical configuration administration. | `provider`, `component`, `requirement` |
| `setup.prestige.stage_unknown` | `setup_prestige_stage_unknown` | Prestige eligibility/reset references a stage absent from the session; select a configured stage. | `stage`, `purpose` |
| `setup.preview.required` | `setup_preview_required` | Setup apply/acknowledgement was requested before preview; generate and review the exact candidate. | — |
| `setup.provider.required` | `setup_provider_required` | A stage selected an external group before a rank provider; choose a provider or make it internal. | `stage`, `group` |
| `setup.requirement.baseline` | `setup_requirement_baseline` | Eligibility was assigned to the baseline stage; choose a later stage. | `stage` |
| `setup.requirement.completion.invalid` | `setup_requirement_completion_invalid` | The completion value is unknown; choose one of the listed completion modes. | `completion`, `allowed`, `provider`, `metric` |
| `setup.requirement.duplicate` | `setup_requirement_duplicate` | A requirement ID is already assigned in setup; choose a unique immutable ID for the requested stage. | `requirement`, `stage` |
| `setup.requirement.metric_unknown` | `setup_requirement_metric_unknown` | The provider does not advertise the metric with an authoritative type; run discovery and select an active advertised metric. | `provider`, `metric` |
| `setup.requirement.operator.invalid` | `setup_requirement_operator_invalid` | The operator is unknown or incompatible with the typed single target; choose one listed compatible operator. | `operator`, `type`, `allowed`, `provider`, `metric` |
| `setup.requirement.scope.invalid` | `setup_requirement_scope_invalid` | The measurement scope is unknown; choose one of the listed scopes. | `scope`, `allowed`, `provider`, `metric` |
| `setup.requirement.target.invalid` | `setup_requirement_target_invalid` | The target is not valid for the provider-advertised type; use one accepted typed representation. | `target`, `type`, `provider`, `metric` |
| `setup.session.owner_mismatch` | `setup_session_owner_mismatch` | Another administrator owns the session; its owner continues or the current actor starts another. | — |
| `setup.session.unknown` | `setup_session_unknown` | The setup session is unknown or expired; start/resume a current session. | — |
| `setup.stage.duplicate` | `setup_stage_duplicate` | The immutable stage ID already exists in the wizard; choose another ID. | `stage` |
| `setup.text.control_character` | `setup_text_control_character` | A setup field contains a prohibited control character; use printable Unicode. | `component` |
| `stage.add.rejected` | `stage_add_rejected` | Lossless progression insertion rejected duplicate identity or unsupported YAML structure; use a unique ID and supported block-style document. | `stage` |
| `stage.change.remap_candidate_invalid` | `stage_change_remap_candidate_invalid` | The candidate progression document cannot compile for remap validation; fix `progression.yml`. | — |
| `stage.change.remap_invalid` | `stage_change_remap_invalid` | A removed/missing stage maps to itself; choose a distinct enabled ordered target. | `source`, `target` |
| `stage.change.remap_missing` | `stage_change_remap_missing` | Mapping removal was requested from a draft with no remap plan; review/select mappings first. | — |
| `stage.change.remap_required` | `stage_change_remap_required` | A GUI deletion action lacks its required explicit replacement field; use the remap selector before submitting deletion. | — |
| `stage.change.remap_snapshot_stale` | `stage_change_remap_snapshot_stale` | Persisted player references changed after remap preview; regenerate and review the migration against current persisted state. | `source`, `target`, `before`, `after` |
| `stage.change.remap_source_missing` | `stage_change_remap_source_missing` | The current draft plan contains no mapping for that source; review the plan before removal. | `source` |
| `stage.change.remap_source_present` | `stage_change_remap_source_present` | The proposed removed source still exists in the candidate; remove it before mapping persisted references. | `source`, `target` |
| `stage.change.remap_target_missing` | `stage_change_remap_target_missing` | The replacement is absent, disabled, or unordered; select an enabled ordered candidate target. | `source`, `target` |
| `stage.change.transition_reconciliation_pending` | `stage_change_transition_reconciliation_pending` | Configuration is active but its unsafe-stage reservation needs cleanup; do not retry and run transition recovery. | `revision` |
| `stage.change.transition_stale` | `stage_change_transition_stale` | Unsafe-stage scope could not be fully reserved/revalidated before activation; preview current references/state and retry. | — |
| `stage.remove.rejected` | `stage_remove_rejected` | Lossless progression removal rejected an absent stage, invalid replacement plan or unsupported YAML structure; select an existing stage and valid supported structure. | `stage`, `target` |

## Mechanical proof

`PaperMessageServiceTest` compares the extracted literal production set with the exact 89-code register, asserts 89
distinct base identities, inventories the exact 19 multi-source codes and requires all five typed variant identities and
catalog pairs. It also derives the exact 70-code single-source set and requires exact equality with the dedicated audit
rows. It exercises outcome/context rendering, OR8D-11 object/consequence distinctions, list/map guidance,
persisted-reference cause, alternate-catalog control, immutable fact propagation, bounded unknown fallback and the
static prohibition on diagnostic-code fragment classification. `PhaseSixConfigurationAdministrationTest` exercises
the real preview hash/version checks, rollback-provenance check, snapshot-preparation failure before activation,
pre-change/restored/restore-failed apply paths, both validation contexts, list/map/scalar behavior and persisted-reference
change between preview/apply, in addition to the previously accepted real-source fact cases.

No public administration rendering consumes `exception.getMessage()` or remediation prose. Those developer diagnostics
remain available for logs and compatibility only.
