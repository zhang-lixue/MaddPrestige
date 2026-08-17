# MaddPrestige V2 Phase 6 implementation

## Review boundary

Phase 6 starts from exact accepted SHA `2a9532fe48b82a24b716d060bfebfc6d69682c41` on branch `v2/phase-6` in the dedicated `MaddPrestige-Phase6` worktree. All work is unstaged and uncommitted. The main worktree and protected V1 paths were not modified. Phase 7+ work is outside this implementation.

The phase implements testable administration services and thin Paper adapters. It does not register those adapters in the frozen V1 bootstrap/plugin descriptor. Correction Pass 1 completed the first reviewed A42, A43 and A69 behavior. Correction Pass 2 added the first durable target-stage fence, immutable apply provenance, GUI rollback authority and rollback-remap surface. Correction Pass 3 extended that design to source plus target participation, journal-owned lease recovery and accumulated multi-source remaps. Correction Pass 4 adds fallback-safe replacement validation and durable configuration-owned authority over every removed/disabled stage, including zero-reference stages. Phase 6 module acceptance A38–A47, A56, A57 and A69 is satisfied on the corrected production and deterministic evidence; fresh live V2 deployment/setup qualification A01/A02 remains partial.

## Correction Pass 4 fallback and configuration-transition authority

### Invariants and authority lifecycle

Before row migration, `StageChangeImpactAnalyzer.coversAffected` requires each replacement to exist, be enabled and occur in the stage order under both the prior/current authority and the candidate. The source projection, prior-target projection and candidate-target projection must be exactly equal. Candidate-only, prior-disabled, prior-unordered and semantically changed targets fail before the persisted-reference snapshot becomes executable. Rollback is a new candidate over the current authority and runs this identical check; historical documents or caller draft state cannot weaken it.

The unsafe scope is `removed stages ∪ disabled stages`, independent of reference count. `PhaseSixConfigurationWorkflow.beginStageTransition` labels every member `REMOVED` or `DISABLED` and passes the complete scope to `SqliteStageReferenceMigrationStore.beginTransition`. The durable owner is the exact candidate revision's `ATTEMPTED` row in `mp_configuration_revisions_v2`; its parent revision and canonical hash must match. A separate configuration journal is unnecessary because this existing history row already owns preparation, terminal outcome and recovery identity.

One `BEGIN IMMEDIATE` acquisition transaction performs terminal operation-lease cleanup, exact history-owner validation, competing-transition rejection, source/target operation-lease intersection checks, transition/scope/reservation insertion, reference-count revalidation for every unsafe stage, exact remap-snapshot revalidation and all required CAS migrations. The reservation therefore exists before another SQLite writer can create the first reference to a previously empty D. For B/D/F, B→C and F→E migration and B/D/F reservation are one transaction; no remapped source is released early.

Operation leases and configuration authority remain separate. `mp_stage_transition_leases` is owned by an operation journal and binds source/current plus target/result participation. `mp_configuration_stage_reservations` is owned by one configuration revision and binds stages that are becoming unreferenceable. Both protocols serialize under `BEGIN IMMEDIATE`: an operation lease touching any unsafe stage makes configuration acquisition fail before migration/activation, while an existing reservation makes operation acquisition fail before cost/projection/reward. An unrelated operation is allowed. Direct insert/import/update/history and the nested Prestige stage reset call `SqliteStageTransitionGuard.requireNoPendingRemap` in their own immediate write transaction, so they cannot bypass either active reservation or legacy remap authority.

Release is evidence-based. Success first records durable `APPLIED` history and then changes the transition to `CONFIG_APPLIED` and deletes active reservations in one transaction. Safe failure first records durable `FAILED` history and may release as `CONFIG_FAILED_SAFE` only when the prior pointer/runtime authority is coherent. A failed pointer restore or any pointer/runtime/history contradiction becomes or remains `NEEDS_RECONCILIATION`. `ConfigurationAdministrationService.recoverConfigurationStageTransitions` compares the durable history owner, `active-revision` pointer and canonical runtime revision; it resolves only exact candidate or exact prior coherence and never uses age. Repeated cleanup is idempotent.

SQLite migration 10 adds `mp_configuration_stage_transitions`, immutable `mp_configuration_transition_stages` and active `mp_configuration_stage_reservations`, including owner foreign keys, uniqueness constraints and lookup indexes. A migration-9 pending/failed-safe remap cannot prove the absent zero-reference part of its original candidate, so it becomes incomplete `UNKNOWN_LEGACY`/`NEEDS_RECONCILIATION` authority. Incomplete scope, missing/extra reservation rows, missing/terminal active history owners, ownerless legacy remaps and terminal state with a lingering reservation fail globally closed. The transition query converts scope/reservation disagreement into incomplete diagnostic state, and Doctor reports healthy complete activity as a warning but every contradiction as blocked.

### Correction Pass 4 authoritative stage-write inventory

| Production route | Mutation | Enforcement |
|---|---|---|
| `RankUpOperationExecutor.execute` | source → target through `RepositoryStageTransitionCommitter` | Durable source+target operation lease before costs/projection/commit/rewards; acquisition rejects exact configuration reservations; repository repeats the write guard |
| `PrestigeOperationExecutor.execute` | source → configured reset target | Durable source+reset operation lease before costs/projection/atomic lifecycle commit/rewards; lifecycle repository repeats the guard |
| `RankProjectionOperationExecutor.execute` | expected source → projected internal target | Durable source+target operation lease before external adapter effect and internal mutation |
| `SqlitePlayerStageRepository.insert` / `importOnce` | absent row → supplied stage | `BEGIN IMMEDIATE` plus reservation/remap guard before insert; no orphanable ad-hoc lease |
| `SqlitePlayerStageRepository.update` / `updateAndAppendHistory` | authoritative CAS source → supplied target | Loads source and checks source+target inside the same immediate transaction before CAS/history |
| `SqlitePrestigeLifecycleRepository.commitInternal` | sealed source → reset target | Checks source+reset in the same immediate transaction as stage/Prestige/currency/scope/history commit; outer lease remains owned by executor |
| `SqliteStageReferenceMigrationStore.beginTransition` | exact unsafe referenced sources → sealed targets | Owns the destructive side: complete-scope reservation, exact snapshot validation and all row/history/remap writes are atomic |
| `PendingOperationRecoveryService` | no new stage write; resolves existing operation evidence | Adopts/retains the existing operation-owned lease; terminal evidence releases, unresolved external state stays fenced |
| Configuration-transition recovery | no player-stage rewrite; resolves reservation lifecycle | Uses configuration history + pointer + runtime coherence, then terminal-gated cleanup; ambiguity remains fail-closed |

Repository-wide SQL and indirect-call inspection found no other production writer of `mp_player_stage_state.stage_id`. Read repositories, diagnostics, history queries, configuration capture and restart reconstruction are intentionally non-mutating.

### Acceptance-proof matrix

| Requirement | Invariant | Production enforcement | Deterministic evidence |
|---|---|---|---|
| fallback-safe remap | Replacement remains a valid semantic stage if candidate activation fails | `StageChangeImpactAnalyzer.coversAffected` validates prior + candidate existence/enabled/order and exact source/prior-target/candidate-target projection | `StageChangeImpactAnalyzerTest.rejectsTargetIntroducedOnlyByCandidate`, `rejectsTargetDisabledByPriorAuthority`, `rejectsTargetUnorderedByPriorAuthority`, `rejectsProjectionMismatchAcrossEitherAuthority`, `oneInvalidTargetBlocksMixedMultiSourceRemap`, `acceptsEveryTargetValidUnderPriorAndCandidateAuthority` |
| zero-reference removal | No first D reference after final authority acquisition | `PhaseSixConfigurationWorkflow.beginStageTransition`; `SqliteStageReferenceMigrationStore.beginTransition`, `revalidateUnsafeReferences`; active per-stage row | `SqliteStageReferenceMigrationStoreTest.zeroReferenceRemovalFencesEveryDirectWriteAndReleasesIdempotently`, `directWritesWinningFirstMakeFinalRevalidationFailClosed` |
| disablement | A disabled D is reserved identically to a deleted D | Unsafe-scope construction labels `DISABLED`; the same reservation/guard protocol applies | `SqliteStageReferenceMigrationStoreTest.zeroReferenceDisablementUsesIdenticalFailClosedAuthority`; `SqlitePhaseSixStageRemovalIntegrationTest.normalApplyPersistsZeroReferenceDisablementScope` |
| operation wins | Existing source or target participation prevents the candidate before migration/activation | `SqliteStageReferenceMigrationStore.requireNoTransitionLeases` inside acquisition transaction | `SqliteStageReferenceMigrationStoreTest.operationWinsFirstForEitherStageRole`; `RankUpEngineTest.zeroReferenceTargetOperationWinsBeforeConfigurationReservation`; `RankProjectionOperationExecutorTest.projectionTargetOperationWinsBeforeConfigurationReservation`; `PhaseFourLifecycleTest.prestigeSourceOperationWinsThenRemapRetriesSafely` |
| configuration wins | All consequential operation effects stop before execution | `SqliteStageReferenceMigrationStore.acquire` calls the shared guard before executor claim/effects | `RankUpEngineTest.zeroReferenceConfigurationAuthorityBlocksRankUpSourceAndTargetBeforeEffects`; `RankProjectionOperationExecutorTest.zeroReferenceTargetReservationBlocksProjectionBeforeAdapterEffects`, `zeroReferenceSourceReservationBlocksStalePlanBeforeAdapterEffects`; `PhaseFourLifecycleTest.configurationFenceRechecksPrestigeBeforeEveryEffect` |
| mixed candidate | B, D and F remain one scope even though only B/F are referenced | Atomic scope insertion + B→C/F→E migration in `beginTransition` | `SqliteStageReferenceMigrationStoreTest.mixedReferencedAndZeroReferenceCandidateRetainsOneCoherentAuthority`; `SqlitePhaseSixStageRemovalIntegrationTest.normalApplyPersistsCompleteMixedUnsafeStageScope` |
| rollback | Rollback uses current authority as fallback and the same full reservation | `ConfigurationAdministrationService.applyRollback` delegates the same prepare/finalize path; analyzer and `beginStageTransition` are kind-independent | `SqlitePhaseSixStageRemovalIntegrationTest.rollbackRejectsCandidateOnlyFallbackTarget`, `rollbackPersistsCompleteMixedUnsafeStageScope`, `rollbackAppliesMultipleSourceMappingsWithoutReplacementLoss` |
| restart/recovery | Authority survives until candidate or prior coherence is proved | `ConfigurationAdministrationService.recoverConfigurationStageTransitions`; terminal-gated `SqliteStageReferenceMigrationStore.finishTransition` | `SqlitePhaseSixStageRemovalIntegrationTest.recoveryUsesHistoryPointerAndRuntimeEvidenceWithoutAgeHeuristics`, `recoveryResolvesPreActivationCrashToCoherentPriorAuthority`, `failedPointerRestoreLeavesRemapPendingAcrossRestart`, `activationFailuresReleaseOnlyAfterCoherentPriorAuthority` |
| direct writes/import | No repository bypass creates or moves a reserved-stage reference | `SqlitePlayerStageRepository` immediate transactions + `SqliteStageTransitionGuard.requireNoPendingRemap`; lifecycle equivalent | `SqliteStageReferenceMigrationStoreTest.zeroReferenceRemovalFencesEveryDirectWriteAndReleasesIdempotently`, `directWritesWinningFirstMakeFinalRevalidationFailClosed` |
| integrity/idempotency | Contradictory scope/owner cannot silently reopen authority | `SqliteStageTransitionGuard.requireNoMalformedConfigurationTransition`; terminal-history gate; bidirectional scope check | `SqliteStageReferenceMigrationStoreTest.concurrentTerminalReleaseIsIdempotent`, `simultaneousConfigurationTransitionsFailClosedWithoutConflictingReservations`, `contradictoryConfigurationAuthorityFailsGloballyClosedAndRemainsDiagnosable`; `SqliteMigrationTest.upgradesLegacyPendingRemapAsIncompleteConfigurationAuthority` |

## Correction Pass 3 stage-transition and remap model

`StageTransitionPermit` now identifies exact `(operation ID, source/current stage, target/result stage, configuration revision, lease token)` participation. Migration 9 rebuilds `mp_stage_transition_leases` with an operation foreign key, source and target columns, unique token, fixed `OPERATION` owner type and a completeness flag. `SqliteStageReferenceMigrationStore` compares each removed stage against both lease positions while holding the same `BEGIN IMMEDIATE` database write boundary used for acquisition. An incomplete migrated legacy row blocks destructive remap until its exact live owner adopts and supplies the source.

Journal ordering is deliberate. Rank-up, Prestige and standalone projection validate sealed authority/bindings, look up the idempotency tuple and insert the immutable `PREPARED` operation before lease acquisition. The lease store refuses an absent or terminal owner. Therefore a crash before journal creation cannot create a lease; a crash after the journal but before acquisition leaves an effect-free `PREPARED` operation that startup recovery fails safely. No consequential effect runs before acquisition.

Restart acquisition by the same operation/source/target/revision returns the persisted token. Re-entry does not manufacture a second row. Release is authorized by terminal journal evidence rather than a `newlyAcquired` flag: a nonterminal nested close is a no-op, and concurrent/repeated terminal releases delete at most the exact matching token. `NEEDS_RECONCILIATION` deliberately retains participation. Startup recovery cleans already-terminal owners; automatic terminal outcomes release by operation ID; `resolve` records an evidence-backed terminal reconciliation and then releases. Age alone is never deletion evidence.

Migration 9 handles migration-8 rows deterministically. A row with a nonterminal journal owner is copied with unknown source/incomplete participation and can be adopted. A row with a terminal or absent owner is not copied. The focused v8→v9 test covers all three states, adoption and final release.

Direct repository paths use transaction-scoped serialization rather than durable random owners. `SqlitePlayerStageRepository` insert/import/update/update-with-history starts an immediate transaction, determines the current source inside it for updates, checks every source/target against pending remap and writes before commit. `SqlitePrestigeLifecycleRepository.commitInternal` checks its source/reset stages in the same immediate transaction while the outer executor retains the durable operation lease. A failed direct mutation rolls back with no durable row. Phase 6 has manual Prestige count administration but exposes no separate manual current-stage command; any production caller of the public player-stage repository receives this same source/target check.

### Authoritative current-stage write audit

| Production route | Source → target | Durable authority | Gate and ordering | Restart/release |
|---|---|---|---|---|
| `RankUpOperationExecutor` | sealed plan source → target | Operation-owned lease | `PREPARED` journal, acquire both stages, recheck bindings/targets, claim `EXECUTING`, then costs/projection/CAS/rewards | Same operation adopts token; terminal executor/recovery releases; reconciliation retains |
| `PrestigeOperationExecutor` | simulation source → reset | Operation-owned lease | `PREPARED` lifecycle journal, acquire source/reset, validate projection, claim, then costs/projection/atomic lifecycle commit/rewards | Same operation adopts; terminal releases; reconciliation retains |
| `RankProjectionOperationExecutor` | expected authoritative player stage → projection target | Operation-owned lease | `PREPARED` journal, acquire source/target, recheck binding, claim/start action, then external projection and optional internal update | Same operation adopts; terminal releases; uncertain projection retains |
| `RepositoryStageTransitionCommitter` | plan source → target | Outer rank-up lease | Delegates to player-stage update; repository transaction repeats source/target pending-remap check before CAS/history | Does not release outer lease |
| `SqlitePlayerStageRepository.insert/importOnce` | no prior row → inserted target | None; transaction-scoped | `BEGIN IMMEDIATE`, target pending-remap check, insert/import, commit | Crash rolls transaction back; no durable owner exists |
| `SqlitePlayerStageRepository.update/updateAndAppendHistory` | source selected at expected revision → replacement target | Outer lease when invoked by executor; otherwise transaction-scoped | `BEGIN IMMEDIATE`, load authoritative source, check source+target, CAS and optional history, commit | Direct crash rolls back; nested call cannot release outer lease |
| `SqlitePrestigeLifecycleRepository.commitInternal` | sealed Prestige source → reset | Outer Prestige lease | `BEGIN IMMEDIATE`, source+reset pending-remap check, atomic stage/Prestige/currency/scope/history commit | Repository owns no separate lease and cannot release outer authority |
| `SqliteStageReferenceMigrationStore.migrate` | every sealed removed source → selected replacement | Destructive remap transaction | `BEGIN IMMEDIATE`, terminal cleanup, reject any lease touching a removed source, revalidate exact snapshot, journal/CAS all rows | `MIGRATED_PENDING_CONFIG` survives restart until applied or known failed-safe |
| import/bootstrap and direct test-visible repository APIs | absent/current source → supplied target | None; transaction-scoped | Same `SqlitePlayerStageRepository` entry points; no alternate SQL writer exists | No crash-orphanable durable row |
| `PendingOperationRecoveryService` | retained operation participation | Existing operation-owned lease | Cleans terminal evidence before scanning; never replays an unresolved external effect merely to clear a fence | Terminal recovery/explicit resolution releases; unresolved work remains fenced |

The only production SQL updates of `mp_player_stage_state.stage_id` are the player-stage repository, the Prestige lifecycle atomic commit and the remap store. The first two use the shared transaction guard; the third is the destructive side of that protocol. `RepositoryStageTransitionCommitter` is an adapter over the guarded repository rather than another write path.

### Deterministic race and multi-remap results

For rank-up B→C and Prestige B→A, operation-wins tests pause after durable source participation exists: B removal fails with no player migration, the operation completes once, releases, and a fresh snapshot then remaps a remaining B player. Remap-wins tests commit/fence B first and then resume the preauthorized operation: lease acquisition fails its effect-free `PREPARED` journal, with zero cost, zero external projection, zero reward and no unnecessary reconciliation artifact.

Draft remap selection now merges or replaces only one source. `config unmap` and GUI `REMOVE_STAGE_REMAP` remove one mapping. The complete result is compiled and validated before CAS: sources must be absent and targets must be present, enabled and ordered. An invalid second mapping returns a structured failure and preserves the existing plan. The SQLite integration test selects B→C followed by D→E, seals both row sets in one acknowledgement candidate, migrates all rows, activates a configuration containing neither B nor D, proves zero remaining B/D references and reopens the persisted result.

Doctor consumes `StageTransitionLease` diagnostics. A complete active nonterminal owner is a warning because it may be a legitimate short operation. Unknown/incomplete source participation, missing/terminal journal ownership and unresolved reconciliation are blocked findings with exact operation/source/target/config detail. An empty lease set remains healthy.

## Correction Pass 2 authority and transition model (historical, extended by Pass 3)

Every draft receives one immutable server-owned kind at creation: a normal editor draft is `NORMAL`, a history-derived draft is `ROLLBACK`, and the first-install wizard creates `SETUP`. The exact permission map is:

| Draft kind | Required current permission | Public apply route |
|---|---|---|
| `NORMAL` | `maddprestige.admin.config.apply` (`CONFIG_APPLY`) | `applyDraft` / normal GUI apply |
| `ROLLBACK` | `maddprestige.admin.config.rollback` (`CONFIG_ROLLBACK`) | `applyRollback` / rollback GUI apply |
| `SETUP` | `maddprestige.admin.setup` (`SETUP`) | `applySetup` / setup wizard apply |

Preview, acknowledgement preparation, acknowledgement confirmation and final mutation load the exact live draft and derive this kind. Cross-kind direct methods fail with `config.apply.kind_mismatch`; no public acknowledgement API accepts a kind. Finalization rechecks the matching current permission after asynchronous preparation and immediately before draft claim/durable work, so revocation after acknowledgement fails closed.

`GuiConfigurationAuthority` carries this server-owned classification into GUI sessions. The GUI renders and dispatches normal versus rollback confirmation from the draft kind, never from inventory state or player input. Setup remains owned by the setup session. The contextless destructive `openStageEditor(subject, stage, replacement)` overload was removed; every public destructive stage action now holds actor-owned `GuiMutationContext` with exact draft/base context and reaches `CanonicalGuiMutationExecutor`.

Rollback and other existing candidates may select a missing-stage replacement with the canonical `selectStageRemap` operation. Command `config remap` and GUI `SELECT_STAGE_REMAP` provide only source/replacement identifiers plus the exact draft ID. The service verifies candidate membership/enabled state, constructs its own revisioned plan, increments draft version and invalidates preview. `preview` no longer accepts a caller-submitted `StageRemapPlan`; it can only seal the plan stored on that server-owned draft.

SQLite migration 8 originally added target-stage-only `mp_stage_transition_leases`. `SqliteStageReferenceMigrationStore` was both the remap writer and the canonical `StageTransitionFence`, with remap start and acquisition serialized by `BEGIN IMMEDIATE`. Migration 9 and D-108–D-111 supersede the target-only ownership details while preserving the following pending-remap outcomes:

- if a stage operation owns participation in a stage to be removed, remap fails before any player row moves;
- if remap commits first, `MIGRATED_PENDING_CONFIG` plus its source-stage entries survives connection/process restart and rejects any later lease sourced from or targeting the removed stage;
- `CONFIG_APPLIED` is terminal after pointer/runtime authority is published;
- `CONFIG_FAILED_SAFE` is terminal only when old configuration authority was safely retained/restored and the migrated replacement is valid there;
- failed runtime publication plus failed pointer restoration remains `MIGRATED_PENDING_CONFIG`, fenced and explicitly reconcilable.

This closes the remap-commit-to-activation window without claiming a transaction across SQLite, the filesystem pointer and in-memory runtime state.

## Correction Pass 1 authority model

Every mutable surface now converges on `ConfigurationAdministrationService`. A draft has a server-owned monotonically increasing version; every edit clears its prior candidate. Preview binds draft ID/version, actor, base revision, complete content hash, findings and any persisted-reference remap seal. After the asynchronous second preparation, final apply compare-and-replaces the exact live draft with an applying claim before the first durable write. Editing, cancellation or two concurrent applies therefore cannot activate or discard stale same-draft work.

High-risk configuration/setup acknowledgement is a separate opaque server authority. It binds actor, draft ID/version/hash, base revision, apply kind, exact finding seal/codes and a five-minute expiry. Caller-supplied acknowledgement codes are rejected. Wrong actors do not consume the token; stale/expired authority is conditionally removed; compare-and-remove gives exactly one concurrent winner. Draft and setup sessions expire after two hours, GUI and operation confirmations have bounded configured lifetimes, and opportunistic pruning makes expired authority unusable.

`CanonicalGuiMutationExecutor` executes scalar, list, stage, preview, acknowledgement, apply and rollback actions using exact server-side `GuiMutationContext`. `GuiSessionService` binds every visual action to actor, permission and active revision. `/gui` returns the real `GuiSessionView`; the Paper command adapter opens it through `PaperPhaseSixGuiController` when the controller is bound. Neither inventory metadata nor user text carries authority. Live registration in the frozen V1 bootstrap remains deliberately unclaimed.

## Referenced-stage replacement (A69)

The migration-capable workflow captures the current persisted player rows and seals the remap plan, source/target stages, player UUIDs, state revisions, source config revisions and counts into `StageRemapSnapshot`. Valid targets must be different, enabled, ordered and projection-equivalent; missing, extra, self, nonexistent, disabled, unordered or projection-changing mappings fail closed. Apply re-prepares against persistence and requires the exact same seal.

SQLite migration 7 adds a dedicated remap operation journal and entry table. `SqliteStageReferenceMigrationStore` uses `BEGIN IMMEDIATE`, rechecks total counts and every sealed row, CAS-updates every matching player to the replacement with state revision +1 and target config revision, and writes remap entries plus stage-history reason evidence in the same transaction. Configuration history attempt and immutable file snapshot are prepared first; the SQL remap commits immediately before pointer activation. Success marks the remap `CONFIG_APPLIED`. If file/runtime activation fails and prior authority is known safe, the replacement remains valid in the prior configuration (the analyzer permits only projection-equivalent targets), and the journal becomes `CONFIG_FAILED_SAFE` with actionable reconciliation detail. If final status/history recording fails after activation, Doctor exposes the unresolved operation rather than inviting a stale retry.

Correction Pass 2 names the actual statuses precisely: success is `CONFIG_APPLIED`, safe retained/restored authority is `CONFIG_FAILED_SAFE`, and every incomplete or uncertain transition remains `MIGRATED_PENDING_CONFIG`. The pending state is itself the durable restart fence. It is not cleared when restoring a pointer fails.

All authoritative current-stage mutations share that boundary at their earliest consequential point:

- `RankUpOperationExecutor`: after sealed authorization/provider/config checks and `PREPARED` journal ownership, before execution claim, costs, projection, stage commit or reward;
- `PrestigeOperationExecutor`: after sealed authorization/provider/config checks and `PREPARED` journal ownership, before execution claim, costs, reset projection, lifecycle/current-stage commit or reward;
- `RankProjectionOperationExecutor`: after `PREPARED` journal ownership, before execution claim and the external rank adapter call;
- `SqlitePlayerStageRepository`: inside the direct write's `BEGIN IMMEDIATE` transaction before insert/import/update and atomic update-plus-history, including direct/manual repository use;
- `SqlitePrestigeLifecycleRepository`: inside the shared transaction guard before reset-stage/current-state commit, nested beneath the executor's lease where applicable;
- `SqliteStageReferenceMigrationStore`: before sealed remap revalidation and row movement.

Terminal operation outcomes release exact journal-owned leases, including leases adopted after restart. `NEEDS_RECONCILIATION`/uncertain outcomes retain them so a later destructive config transition cannot pass a possibly in-flight external effect. The operation journal is the recovery authority. A re-entering recovery call with the same operation/source/target/revision sees the same token rather than creating competing authority.

The legacy `StageConfigurationWorkflow` has no migration store and continues to emit `stage.change.remap_execution_required`; merely passing a plan to that read-only path cannot orphan persistence. End-to-end SQLite coverage proves A→B→C preview counts, secure acknowledgement, every B→C CAS migration, B removal from active configuration, readable history, clean reconciliation and restart persistence. Stale rows and injected mid-transaction failure roll back all SQL effects.

## Canonical administration layer

`maddprestige-core` now owns a platform-neutral application layer:

- one explicit permission catalog and immutable permission subject;
- canonical configuration draft, preview, validation, apply, history and rollback services;
- schema-backed config get/search/explain and context-aware help;
- provider-capability-backed completion catalogs;
- bounded doctor and canonical why reports;
- setup sessions for safe arbitrary ladders;
- exact zero-write rank-up/Prestige previews and sealed confirmations;
- read-only player progress views;
- opaque server-side GUI sessions/actions;
- manual Prestige adjustment with optimistic concurrency.

No core class imports Paper, Bukkit, LuckPerms, Vault, mcMMO, PlaceholderAPI or shop APIs. Commands and GUI actions are request adapters over these services, not independent domain implementations.

## Configuration workflow

A draft starts from exact active revision documents and records its source revision. Editing a draft changes only that draft. Scalar edits reuse the accepted `LosslessConfigurationEditor`; comments, key order, unknown keys, line ending style and untouched bytes remain stable. Structural mutation that cannot be represented safely by that editor is rejected unless a dedicated canonical workflow owns it.

Preview and apply compile the complete document set. `PhaseSixConfigurationWorkflow` combines the accepted stage/Phase 3/Phase 4 compiler with Phase 5 integration schema/validation extensions and current provider capabilities. Findings retain exact path/code/severity/remediation. No invalid provider, metric, stage reference or integration shape reaches preparation.

Apply transition:

1. Verify permission and source revision.
2. Compile the complete candidate with canonical extensions.
3. Append exact `ATTEMPTED` history.
4. Prepare a new immutable revision directory and verify manifest/document hashes.
5. If required, execute the exact sealed SQLite stage remap and journal it.
6. Immediately reverify manifest text, flat file inventory, every document byte and canonical hash.
7. Atomically switch the `active-revision` pointer.
8. Publish the immutable compiled runtime snapshot.
9. Finalize remap as `CONFIG_APPLIED` and history as `APPLIED`; any earlier failure is marked truthfully for recovery.

The previous active pointer remains authoritative until step 7. A post-switch history failure reports that activation occurred and requires reconciliation rather than claiming rollback. Doctor history diagnostics surface unfinished attempts.

Rollback reads exact stored documents for a previous applied revision, creates a new draft, recompiles against current capabilities, and runs the same apply transition. The restored behavior receives a new revision and actor/timestamp/source audit.

## Durable storage

`AtomicConfigurationFileStore` uses a caller-scoped configuration root containing immutable revision directories and one atomic pointer file. Every document is UTF-8 and every manifest binds revision, canonical configuration hash and per-document SHA-256. Preparation reads back and verifies all evidence before allowing activation. Backup verification rejects missing, extra or altered documents and mismatched canonical hashes.

SQLite migration 6 adds `mp_configuration_revisions_v2`, `mp_configuration_revision_documents` and bounded query indexes. Migration 7 adds `mp_stage_remap_operations`, `mp_stage_remap_entries` and status indexes. Migration 8 introduced target-only transition leases. Ordered migration 9 preserves accepted earlier checksums while rebuilding `mp_stage_transition_leases` for journal-owned source+target participation, a unique adoption token and completeness evidence; only live nonterminal legacy owners survive for exact adoption. Migration 10 adds configuration-revision-owned complete unsafe-stage scope and active reservation rows; legacy scope that cannot be reconstructed exactly remains incomplete and fail-closed. `SqliteConfigurationHistoryStore` persists exact document text and lifecycle outcome. Immutable config/history/stage/remap/transition evidence is never pruned by Phase 6; retention tuning remains deferred because activation must not perform heuristic cleanup.

`SqlitePrestigeAdministrationStore` applies a manual current/lifetime Prestige change and appends the accepted full audit contract inside one transaction. Target UUID, actor type/UUID/name, old/new values, expected state revision, reason, source and timestamp are durable. Concurrent stale state fails without mutation.

## Setup

Setup session discovery reports current rank, metric, cost and reward capabilities. Owner-bound resumable sessions define an arbitrary ordered ladder, baseline, existing external group targets, a simple requirement, simple cost, simple reward and enabled/disabled Prestige eligibility/reset behavior. External stages accept existing names only. Preview runs canonical configuration/rank/provider validation and renders the player experience before apply. No setup code can create a LuckPerms group.

The generated configuration remains conservative outside explicitly chosen simple features: seasons, milestones, currencies, entitlements, competitions and optional integrations remain dormant. Setup apply calls `ConfigurationAdministrationService`; it has no private activation mechanism. Cancel discards its underlying configuration draft, and expiry prunes abandoned session authority.

## Command, help and completion surface

`PhaseSixCommandService` provides one bounded dispatch surface for setup, configuration inspection/edit/validation/diff/history/apply/rollback, doctor, why, player inspection, rank-up/Prestige simulation and confirmation, and GUI opening. All argument counts and identifiers are bounded, failures are rendered actionably, and each operation verifies its exact permission.

Config search/explain uses canonical schema paths with wildcard resolution. Ambiguous short paths do not silently choose a setting. Context help is generated from canonical metric descriptors and describes units, current-versus-delta scope, monotonic/reset behavior and valid operators in operator-facing language.

`CommandCompletionService` refreshes provider, metric, stage, schema, path/value and draft/revision authority into an immutable catalog off the keystroke path. Completion is read-only, bounded, positional, path-specific, actor/permission-filtered and makes zero database/provider calls while suggesting. Generic maps remain listable but are not advertised as add/remove targets unless a real structural route exists. Player-only commands are omitted for console actors; no nonexistent root is suggested. Phase 6 provides and tests this refresh contract, but the intentionally disconnected production bootstrap does not yet automate live provider/draft/revision refresh; that composition limitation is explicit rather than described as self-registration.

## Diagnostics and explanation

Doctor probes are independent and bounded so one failed probe becomes an actionable finding rather than hiding the rest of the report. Eighteen declared diagnostic domains cover:

- active/attempted/failed configuration history;
- database health/status detail;
- provider activation/health/capabilities;
- every configured external rank target by exact stage path and group name;
- schema version, required metric/cost/reward bindings, pending operations and reconciliation;
- orphaned stages, duplicate immutable IDs and entitlement integrity;
- Placeholder, scheduler/cache, flush and unsupported/deferred capability state.

If a required domain has no bound probe, Doctor emits `doctor.not_checked.<domain>` with `DEFERRED`; it cannot report broad `HEALTHY` while hiding that blind spot. Dormant optional providers remain nonfatal.

A healthy result is concise. A broken external target names the exact stage, provider and target and recommends correcting the configured existing group; it never offers group creation.

Why output consumes the same canonical `RankUpAuthorization` used by simulation/execution. Current stage, target stage, exact blockers, recursive requirements and unavailable provider evidence cannot drift into a UI-specific evaluation.

## Simulation and confirmation security

`OperationPreviewService` invokes the accepted rank-up or Prestige authorization boundary and performs no mutation. Simulation and execution have separate permissions. An executable preview can create a `PreparedConfirmation` containing only server-trusted state: actor, operation kind, exact sealed authorization/plan, active/source revisions and expiration.

The opaque token is actor-bound, expiring, single-use and removed before executor dispatch. Consumption rechecks active revision and required execute permission. A forged/replayed/transferred/stale token or a blocked preview cannot invoke the executor. Client text and inventory items are never deserialized into authority.

## GUI security and parity

`GuiSessionService` stores server-side views and action IDs with actor and configuration revision. The canonical action executor repeats permission/revision checks and delegates to the same configuration, setup, diagnostics, simulation and remap callbacks used elsewhere. View-only audiences receive inspect actions but not mutation authority.

The Paper inventory wrapper renders a bounded set of display items. `PaperGuiInventoryGuard` denies drag, shift transfer, number-key swap, offhand swap, double-click collection and bottom-inventory actions while the administration inventory is open. A click selects only the server-side slot/action mapping; item material, display name, lore, NBT/PDC and client inventory contents are not authority.

Stage deletion or rollback to documents already lacking a referenced stage uses draft-bound `SELECT_STAGE_REMAP`. Missing replacement, stale session, absent permission, an extra/unreferenced source, or a failed persisted-reference migration prevents configuration mutation. No contextless destructive stage-editor API remains.

## Paper boundary

`PaperPermissionSubjects` snapshots Bukkit permission checks into the canonical subject. `PaperPhaseSixCommandAdapter` moves command service work to the configured administration executor and schedules audience output on the server thread. `PaperPhaseSixGuiController` opens and handles inventories on the server thread and delegates selected actions asynchronously when needed.

These adapters compile against the accepted Paper API and are contract-tested. They are intentionally not connected to the frozen V1 runtime descriptor/bootstrap. No claim of a live Paper-only Phase 6 deployment is made.

## Verification evidence

`\.\mvnw.cmd --no-transfer-progress clean verify` completes successfully in two consecutive post-fix full correction-pass runs:

- 360 tests / 72 suites;
- 0 failures / 0 errors / 0 skipped;
- module tests/suites: API 7/5, core 146/32, persistence 84/14, Paper 10/4, integrations 55/9, testkit 46/3 and distribution 12/5;
- Checkstyle 7 reports / 0 violations;
- Maven Enforcer and dependency convergence pass;
- 7 JaCoCo XML reports;
- aggregate CycloneDX 1.6 SBOM contains 67 components;
- distribution SHA-256 `42E342B65DC3A73CEB00D1ADF1DAFAFEE7002BB2737B5F616644B97C16DC28C8`;
- aggregate SBOM SHA-256 `9A0CD953432F5682AE723B2ABA954A93B4D4B6DA0197E1742E5A89F20496FFEE`.

Both final clean runs produced the same distribution and aggregate SBOM SHA-256 values.

The correction narrative includes two earlier exploratory full-verification failures. The first exposed Phase 6 test doubles that still assumed every transition had a remap: the command fixture rejected a legitimate zero-reference reservation and the administration fixture used the deliberately read-only migration store. Both fixtures were corrected to model the production capability; production fail-closed behavior was not weakened. The next full run exposed one stale migration assertion expecting schema 9 after forward migration 10; it was updated to the new exact version. A later focused invocation first hit sandboxed dependency resolution and then a PowerShell `-D` parsing error; the corrected focused command passed 16/16 before the two final clean builds.

Focused coverage maps directly to A38–A47, A56, A57 and A69. Tests also cover invalid/expired/stale confirmation, two-admin revision races, atomic manifest corruption, failed configuration outcome evidence, SQLite reopen history/audit, GUI transfer defense and Phase 5 provider schema participation.

## Explicit non-claims and deferred work

- No protected V1 file or runtime descriptor was modified.
- No live Paper server, LuckPerms installation, production config, production SQLite file or player record was opened or mutated.
- The module services are not production-registered; A01/A02 remain partial.
- MySQL/MariaDB Phase 6 administration persistence is not claimed.
- Arbitrary structural YAML rewriting is not claimed; purpose-built canonical mutations are required.
- Provider discovery cannot enumerate every externally existing LuckPerms group through the accepted generic rank contract; operators supply names and preview validates them.
- A failed/abandoned history attempt is diagnosed for operator reconciliation; no heuristic silently decides whether an interrupted external filesystem transition succeeded.
- Phase 7 competitions, presets, web administration and public-release composition are not implemented.

## Owner review entry points

Start with `STATUS.md`, `DECISIONS.md` D-083–D-118, this document, `docs/V2_TRACEABILITY.md`, and `docs/V2_PHASE6_FILE_MANIFEST.md`. The review ZIP also contains changed/new source, relevant unchanged canonical contracts, reports, SBOM and shaded distribution artifact.
