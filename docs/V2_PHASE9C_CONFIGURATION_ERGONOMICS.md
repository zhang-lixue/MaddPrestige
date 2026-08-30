# Phase 9C configuration ergonomics and administrator usability

Phase 9C starts from merged `main` at `45fdd108aa2df7d1307be4f27831e2026f10dda8`. It changes authoring and
presentation ergonomics only. Numeric Prestige remains the sole active model and still advances exactly `P -> P + 1`.

## Pre-edit audit

| Surface | Classification | Finding / disposition |
|---|---|---|
| `progression.yml` defaults | REPETITIVE | `active: false` and `warn-only` restated compiler defaults; schema-only is sufficient. |
| `requirements.yml` defaults | REPETITIVE | empty maps and maximum depth restated deterministic defaults. |
| `rewards.yml` defaults | ADVANCED-OPTION-LEAK | a full disabled command policy obscured the ordinary no-reward case. |
| `lifecycle.yml` defaults | IMPLEMENTATION-LEAK | every reset disposition and empty feature map was required in examples despite a safe policy existing in code. |
| `integrations.yml` defaults | REPETITIVE | all disabled optional plugins and their nested blocks were shown. |
| schema metadata | UNCLEAR | segment-only paths omitted compact/profile-default authoring; segment base metadata said 0 while the compiler used 1. |
| setup wizard output | TOO VERBOSE | stage-free numeric setup emitted every empty map, disabled policy, reset disposition, and integration. |
| admin scalar/list editing | IMPLEMENTATION-LEAK | omission made inherited values readable but not materializable through the canonical editor. |
| `config get` / `explain` | UNCLEAR | both produced the same verbose response and did not identify inherited effective values. |
| config validation / diff | TOO VERBOSE | both aliases emitted identical hashes, findings, remediation, and diffs. |
| Doctor | TOO VERBOSE | normal mode emitted two lines for every healthy/deferred finding. |
| Why / simulation / player view | TOO VERBOSE | normal mode always expanded requirements, consequences, and revision provenance. |
| provider discovery and tab completion | GOOD AS-IS | capability-driven IDs are cached, permission-aware, and do not invent providers. |
| validation/apply/rollback safety | GOOD AS-IS | complete revisions, stale checks, acknowledgement, CAS, and fail-closed provider binding remain authoritative. |
| Quick Start / numeric example | REPETITIVE | normal operators first encountered fully explicit implementation syntax. |

## Accepted Phase 9C design

- All five documents remain required by the immutable revision, bootstrap, and active-runtime contract. Optional
  gameplay/integration sections may be absent.
- Omitted scalar defaults remain visible through `config get` and materialize losslessly only when explicitly edited.
  Omitted lists/maps list as empty; omitted lists materialize on first canonical addition.
- Missing reset components inherit `ResetPreservePolicy.safeDefaults()`. Explicit overrides retain identical meaning.
- Scaling has one resolver. A map with `mode` is a compact Prestige-1-to-unlimited profile. Advanced profiles use
  optional `defaults` plus `segments`. Starts/ends are inferred only when deterministic; ambiguous gaps fail closed.
- Resolution precedence is global formula defaults, profile defaults, segment values, per-level overrides. Runtime and
  preview both consume the same compiled segment model.
- Normal Prestige preview is a compact effective view: transition, eligibility, requirement mode and values, costs,
  rewards, and blockers. `details` adds compiled-source/provider/scope/completion/formula/revision provenance.
  Setup preview follows the same split and never emits the diagnostic representation without an explicit `details`.
- Scaling validation reports its actual category: gap, overlap, invalid range/boundary/override/bounds, or incomplete
  finite/open-ended coverage. Validation remains fail-closed.
- Canonical draft administration now supports lossless schema-confined structured add/edit/remove. Commands, YAML,
  and future GUI actions share that revisioned service; invalid structures never reach or partially mutate active state.
- Guided mcMMO remains `total_level`. No MobCoins or other provider alias is created without an advertised canonical
  provider capability. Vault and other built-ins retain their existing stable provider IDs.

## Preserved architecture and exclusions

Requirements, independent costs, additive rewards, ALL/ANY/X_OF_N, FLAT/LINEAR/EXPONENTIAL/MANUAL, rounding, bounds,
transitions, and overrides remain supported. LuckPerms remains optional and additive, never progression authority, and
never creates a group. Provider-owned values remain external. Stable APIs are unchanged.

This phase performs no live server/database work, real clone qualification, V1 import/mutation, production balance
selection, final GUI, first-party Prestige shop, Phase 9D, or Phase 10 work.
