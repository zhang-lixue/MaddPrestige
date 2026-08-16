# MaddPrestige V2 Phase 3 implementation

**Implementation date:** 2026-08-15
**Baseline:** accepted Phase 2 merge `ca13fa4`
**Scope:** authorization requirements, exact scaling/catch-up, generic costs/rewards, safe command rewards, vanilla/manual metrics, immutable rank-up planning, and Phase 3 persistence

## Outcome

Phase 3 implements the generic authorization and operation-planning engine without activating V2 in the production plugin. Requirements are typed provider metrics arranged in recursive trees. Costs and rewards are separate provider-owned actions. Simulation uses the same canonical authorization and preflight path but cannot mutate; blocked previews carry denied rather than reusable execution authority. Execution first validates the complete authorization seal, then persists an immutable plan, consumes verified costs, projects configured managed external rank state, commits authoritative internal stage state, and only then applies rewards.

The frozen V1 bootstrap/data/release evidence is unchanged. Prestige and season scopes are synthetic lifecycle contracts only; Phase 4 owns those lifecycle engines. Vault, mcMMO, PlaceholderAPI, QuickShop, other external provider integrations, runtime commands/UI, and Paper bootstrap wiring remain in their assigned later phases.

## Typed metric and requirement model

`MetricValue` supports integer, exact decimal, count, duration, boolean, string, enum, and currency-amount values. Decimal/currency parsing remains text-based and exact. Metric descriptors advertise value type, valid operators, current/lifetime reads, delta capability, monotonic/reset behavior, dimensions/allowed filters, unit, reliability, and provenance. A provider batch returns `AVAILABLE`, `UNAVAILABLE`, `INVALID`, or `ERROR`; unavailable is never coerced to zero.

Numeric families support ordered/equality/range comparison. Boolean/string/enum support equality only. Configuration validation rejects target/type mismatch, invalid range shape, unsupported operator, unsupported current/lifetime scope, missing delta capability, unknown filters, missing required dimensions, and invalid dimension values.

Every requirement has an immutable ID, exact provider/metric binding, operator and typed target, measurement scope, live/latched completion, scaling, catch-up, filters, display metadata, and a versioned SHA-256 semantic fingerprint. `rsf2:` hashes a deterministic length-prefixed binary encoding of the typed lower/upper targets, every scaling/catch-up/rounding field, and sorted filters with explicit key/value boundaries. Display fields are intentionally excluded. Filter controls and excessive lengths are rejected; valid Unicode remains unchanged. SQLite accepts legacy 64-hex keys for existing rows and the new `rsf2:` format for new state.

## Recursive tree semantics

Trees support `ALL`, `ANY`, `ANY_X_OF_Y`, `WEIGHTED`, and arbitrary nesting within a bounded validated depth. IDs must be unique within a tree. Empty groups, unreachable/non-integral thresholds, non-positive weights, and impossible weighted thresholds fail validation.

Outage propagation is decision-aware:

- `ALL` is unsatisfied when any known child is unsatisfied; otherwise it is unavailable only when an unknown child can still decide completion;
- `ANY` succeeds when a known child succeeds, otherwise an outage that could decide the result remains unavailable;
- `ANY_X_OF_Y` and `WEIGHTED` succeed when known progress already reaches the threshold, are unavailable when unknown progress could reach it, and are unsatisfied only when it cannot;
- `INVALID` and `ERROR` children remain severe failures rather than being converted to ordinary false values.

Evaluation produces a deterministic recursive explanation with current/effective target, scope, completion mode, operator, formula, threshold/progress, and provider provenance. Evaluation reads supplied samples and persistent state only; it never writes baselines or latches.

## Scopes, baselines, and latches

Supported scopes are absolute/current, lifetime, since-stage-start, synthetic since-prestige-start, and synthetic since-season-start. Delta scopes require an explicit lifecycle-owned `ScopeId` and a provider that advertises snapshot/delta support. `BaselineInitializationService` performs idempotent insert-at-boundary; an evaluator never invents a missing baseline.

Baseline and latch keys contain UUID, requirement ID, measurement scope, scope-instance ID, and semantic fingerprint. Baselines additionally retain the provider generation and observed value. The evaluator requires a returned baseline or latch record to carry the exact key requested; a repository/source cannot substitute state from another player, requirement, scope instance, or semantic revision. A monotonic sample below its baseline under fail-reconciliation policy becomes unavailable/reconciliation-needed instead of producing a negative or misleading delta.

Live requirements reflect a later value drop. Latched requirements expose an eligible transition when first satisfied; `RequirementCompletionTransitionService` persists it explicitly, and it remains complete only for the same semantic/scope key. This separates pure preview/simulation from lifecycle mutation. The transition and baseline services and their persistence semantics are implemented and tested, but Phase 3 has no production lifecycle owner invoking them outside tests. Future production stage completion/entry composition must own A14/A15 calls; Phase 4 owns Prestige/season lifecycle variants. Simulation performs neither write.

## Scaling and catch-up formulas

Let `B` be the configured numeric target and `i` the bounded scaling index:

- none: `S = B`;
- linear: `S = B × (1 + rate × i)`;
- exponential: `S = B × factor^i`;
- stepped: `S = B × multiplier(greatest configured threshold <= i)`, or `B` before the first threshold.

Scaling is applied first. If catch-up is enabled and position `p` exceeds its start `s`, reduction is `r = min(maxReduction, (p - s) × reductionRate)`; otherwise it is zero. The transformed target is `max(optionalFloor, S × (1 - r))`, then the explicit rounding policy and quantum are applied. Catch-up is local to the requirement/group carrying the profile and is disabled by default. Index, magnitude, parameter, reduction, quantum, discrete-value, duration, and exact-rounding bounds fail closed. Exponential profiles additionally bound parameter precision/scale and reject predicted precision work or magnitude before invoking `BigDecimal.pow`; values are never silently clamped.

## Provider batching and failure semantics

`RequirementMetricCollector` groups leaves by provider, coalesces identical queries, performs one asynchronous provider read per batch, and requires an explicit active/healthy generation pin. A missing/stale pin, provider outage, synchronous throw, failed future, null future, or omitted sample produces structured unavailability for the affected leaves without converting failure to zero.

Configuration preparation walks only enabled stages in the active order and validates their reachable requirement leaves, cost IDs, reward IDs, and rank projection. Active metrics, costs, required rewards, and configured projection are mandatory. Planning/simulation rechecks the configured projection's retained exact generation, active/healthy state, and `RankAdapter` contract; `projection: none` has no rank-provider dependency. An optional reward is validated and pinned only when usable, and its later outage remains independently omittable. Dormant definitions and disabled-stage references remain syntax/type checked but do not activate a provider. Provider validation throws become structured findings.

Stage and Phase 3 snapshots are published together as one immutable `ActiveStageConfiguration` after canonical revision apply. The Phase 3 snapshot stores the exact complete generation map validated by prepare/apply, including rank projection. Replacement providers cannot rewrite it; mixed revisions cannot be constructed; failed apply leaves the prior pair and pins intact. Evaluation, authorization, and execution consume these retained pins and fail closed when the mutable registry no longer matches.

Provider futures that fail after execution starts are external uncertainty. They update the operation/action journal and return `NEEDS_RECONCILIATION`; they do not escape as false clean failure or success.

## Costs, rewards, and rank-up ordering

Costs are never requirement leaves. `RankUpAuthorizationService` is the production trust boundary. `RankUpIntent` carries only actor, player UUID, optional intended target, and idempotency key. The service reads the atomic active snapshot and authoritative `PlayerStageState`; an injected `RankUpProgressContextSource` supplies canonical scaling index, catch-up position, and scope identities, while an injected `RequirementStateReader` supplies persistent baseline/latch state. A caller cannot provide any of those values. The service rejects missing/unknown/disabled/terminal source stages and illegal or skipped targets, resolves the exact tree/cost/reward/projection definitions, evaluates with retained revision/generation/scope/tree/player bindings, and invokes provider-batched preflight. Caller-composed `RankUpPlanningRequest` objects are explicitly untrusted and cannot issue an executable plan. A blocked result carries `RankUpAuthorization.denied()`. An executable seal binds operation/player/stages, state and configuration revisions, provider generations, the complete requirement result and provenance, costs/rewards, configured `externalRankProjection`, generated `rankProjectionRequest`, blockers/execution allowance, and `operationPlan`. The executor validates it before journal insertion. `unavailableProviders` is diagnostic only and deliberately unsealed because no production execution decision consumes it.

`PlayerStageState.configRevision` is historical provenance for the active configuration that last wrote the row, not a requirement that every player row equal the current active revision. A safe R1 player can rank under active R2 when the stored source stage still exists, is enabled, and has the same legal next transition. The authorization and plan bind `expectedPlayerConfigRevision` to the observed source row and `configRevision` to active R2. Commit compares UUID, state revision, source stage, and source provenance, then writes R2. Removal/disablement, structural remap gates, target legality, and optimistic compare-and-set remain fail-closed.

Batch preflight exists so one provider can validate the aggregate debit/quota rather than incorrectly accepting individually affordable costs. Synchronous throws and exceptional futures normalize identically: required cost/reward failures block; optional reward failures omit that reward; planning itself does not mutate. The conservative default refuses multiple coupled costs until a provider implements aggregate preflight.

Required reward/provider failure blocks the immutable plan before any cost consumption. Optional unavailable rewards are recorded and omitted. Provider preflight must return the exact proposed immutable plan; altered player, amount, action, generation, or characteristics block execution.

The persisted execution order is:

1. reject denied, reconstructed, tampered, blocked, stale-configuration, or stale-provider work, with authorization validation before any journal insertion;
2. persist the immutable prepared operation and all pending actions;
3. require each configured projection's exact retained provider generation to remain active and healthy with the `RankAdapter` contract, validate every managed group before any cost, and never create a missing group;
4. execute costs in plan order and journal each original effect;
5. for projected stages, recheck the rank-provider pin, execute the Phase 2 generic `RankAdapter` action, recheck bindings, and verify the exact managed after-state;
6. commit authoritative internal state through `RepositoryStageTransitionCommitter`, comparing the expected source-row configuration provenance and optimistic state revision before writing the current active plan revision;
7. move the journal to `STATE_COMMITTED`;
8. execute rewards in plan order;
9. complete only when required outcomes are known.

The deliberate order is costs → external projection → internal commit → rewards. Thus this executor cannot create internal-success/external-failure divergence. External-success/internal-failure or uncertainty is explicitly journaled for reconciliation; preexisting divergence in either direction remains owned by the Phase 2 reconciler. `projection: none` skips the external action. Managed-membership policy touches only exact permanent/context-free memberships in the pinned managed set, preserving supporter/staff/unrelated nodes.

Every reversible cost has a separate preplanned `cost-compensation` action. The original cost remains `VERIFIED`; the compensation record stores its original action ID, `APPLIED`/`UNCHANGED`/`FAILED`/`UNCERTAIN`, redacted detail, and reconciliation flag. Known pre-commit failure compensates in reverse order. Failed, uncertain, or exceptional compensation identifies the exact action after reload and never implies blind replay. Known optional reward failure may be recorded and skipped. A required reward failure after authoritative commit or any uncertain post-start effect becomes reconciliation work. Duplicate idempotency tuples suppress external replay.

`RankUpSimulationService` returns this exact plan/preflight evidence without inserting an operation, consuming a cost, committing a stage, persisting a latch, or applying a reward. Phase 6 owns command/UI surfaces for simulation.

## Generic command reward safety

`CommandRewardProvider` is the production Phase 3 reward proving authorized command actions. It is inactive by default and accepts only a configured template ID plus structured token values. Policy enforces enabled state, normalized allowlisted roots, a blocklist with mandatory `stop`, `restart`, `op`, `deop`, `reload`, `execute`, `function`, and MaddPrestige mutation aliases, declared/allowed/exact tokens, maximum commands, length, and runtime trigger depth.

Templates and rendered values reject controls/newlines, unresolved braces, whitespace-bearing or unsafe tokens, `;`, `&&`, and `||`. UUID and player-name tokens have dedicated validators. Namespaced and leading-slash roots normalize before allow/block comparison; the mandatory blocklist wins. Operation-wide batch preflight enforces the command-count limit across separate reward definitions.

Trigger depth is not reward metadata. A trusted `CommandExecutionContext` begins at depth zero using the operation ID as correlation; nested command-triggered work carries the same correlation and can only increment depth. Over-limit work fails before dispatch. The Phase 3 provider boundary has a deterministic nested-dispatch test, but no production command-triggered rank-up ingress exists until Phase 6; A55 is therefore reported Partial rather than overclaiming end-to-end runtime wiring.

The adapter dispatches no shell, process, SQL, filesystem, reflection, or code. A validated console command is still an external non-idempotent/non-reversible side effect; dispatcher uncertainty is retained as `UNCERTAIN` and never blindly replayed.

## Vanilla and manual progress providers

`VanillaStatisticsProvider` discovers identifiers from the actual supported Bukkit `Statistic` enum. An injected serverless-testable dimension catalog advertises separate valid block materials, item materials, and entity types; the Paper implementation derives them from authoritative `Material.isBlock`, `Material.isItem`, and `EntityType`. Invalid BLOCK/ITEM filters fail configuration validation before activation. The provider advertises current/lifetime reads, duration units for tick statistics, and monotonic/reset behavior. All requested statistics for one player are read in one explicit Paper-server-thread scheduler task. Unsupported IDs/read modes, invalid filters, runtime failures, or unavailable players fail closed. No statistic identifiers are invented.

`ManualProgressProvider.bootstrap` issues the provider and its opaque, non-serializable owner capability together. Human-readable owner identity remains descriptor/audit metadata and grants no authority. Only that capability can register a metric, obtain its provider/metric/generation-scoped handle, or attest mutation provenance; another provider's capability, a stale generation, and fabricated provenance fail closed. The synchronized asynchronous-registration commit point rechecks closing, duplicate ID, maximum metric slots, and restored-entry bounds, so only one concurrent request consumes the final slot and close wins an in-flight registration race. Typed increments/sets aggregate bounded UUID state and persist versioned batches through explicit flush/shutdown barriers. Ten thousand test increments produce one aggregate persisted record, not ten thousand SQL writes.

## Configuration and persistence

`requirements.yml` owns typed requirements, trees, cost definitions, maximum depth, scaling, catch-up, filters, scopes, and completion modes. `rewards.yml` owns generic reward definitions and command policy/templates. `progression.yml` schema 3 adds stable requirement-tree, cost, and reward ID references to stages. All three documents flow through the Phase 1 lossless draft/revision/validation/apply service. Empty defaults are inert; command actions are disabled.

SQLite migration 3 adds:

- `mp_requirement_baselines`, keyed by UUID/requirement/scope/scope-instance/semantic fingerprint;
- `mp_requirement_latches`, with the same semantic/scope isolation;
- `mp_manual_progress`, keyed by provider/metric/UUID with type, canonical value, update version, and provenance;
- indexes for player/scope and manual player lookups.

All statements are static/prepared. Baseline/latch insert is idempotent. Manual batch UPSERT accepts only a newer update version, making delayed duplicate flushes harmless. Disposable fixtures now migrate through Phase 3.

## Verification and qualification boundary

The focused matrix covers every typed metric family, operators/ranges, invalid parsing/capabilities, mixed-status group truth tables, nesting/depth/malformed trees, pure evaluation, exact returned baseline/latch keys, scope/latch isolation, collision-resistant semantic changes, monotonic reset safety, exact scaling/catch-up formulas and pre-computation resource bounds, active/dormant/optional provider semantics, intent-only canonical authorization with injected trusted progress/state, denied authority for blocked previews, executor-boundary blocker/execution-gate escalation rejection, complete requirement-outcome sealing, configured-projection tamper rejection, untouched authorized execution, cross-revision R1→R2 progression plus source/CAS/remap guards, role-local projection health/contract checks, aggregate costs, journaled rank projection and compensation, repository-backed internal commit, same-path simulation, runtime command-depth propagation, Paper block/item dimension rejection, manual capability/races/batching/reload, SQLite migration/state, stale bindings, duplicate suppression, and synchronous/exceptional provider failures.

Real production code is exercised for the Paper/Bukkit `Statistic` API boundary, command reward provider, configuration compiler/workflow, SQLite repositories/migrations, planner, journaled executor, and manual provider. Cost/reward business integrations use test providers because Vault and other integrations are explicitly Phase 5. Paper statistic tests use real API enums/signatures and a proxy `Player`, not a running server. No live Paper server, production database, Vault service, or external economy was used.

Final clean-build totals, quality scans, reproducibility hash, and protected-path proof are recorded in `STATUS.md` after the final verification run.

## Explicit deferrals

- A24 remains partial: the generic cost foundation is implemented, but real Vault execution and qualification belong to Phase 5.
- A14/A15 remain partial: transition/baseline services and persistence semantics are implemented and tested, but future production stage completion/entry composition must invoke them; Phase 3 has no hidden lifecycle write path.
- A16/A17 prove synthetic baseline semantics only; Prestige/season lifecycle creation and transitions belong to Phase 4.
- A26 is engine-level simulation only; no Phase 6 command or UI is claimed.
- A55 is partial: runtime context/depth propagation is implemented and tested at the command reward boundary, but Phase 6 owns production command-triggered rank-up ingress.
- V2 remains disconnected from the production Paper bootstrap and frozen V1 data.
- Restart recovery/operator replay tooling, live-server qualification, external metrics/economies, setup/doctor/why/UI, prestige, currencies, entitlements, seasons, competitions, public events, presets, and migrations remain in their assigned later phases.
