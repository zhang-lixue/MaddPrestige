# MaddPrestige V2 Phase 4 implementation

**Implementation date:** 2026-08-15
**Owner correction passes:** 2026-08-16 (Passes 1 and 2)
**Starting baseline:** `64b568b7c8da6e9c750003bbcb4b6950aa58b1fe`
**Scope:** Prestige, internal currency, entitlements, milestones, seasons, history, and bounded crash recovery

## Outcome and boundary

Phase 4 adds an inactive-by-default generic lifecycle layer over the accepted Phase 1–3 foundations. It does not wire V2 into the frozen production Paper bootstrap, implement commands/GUI, access production data, or add Phase 5 providers. `lifecycle.yml` is inert by default: Prestige and competition are disabled and all collections are empty.

The full optional competition engine is deferred. Phase 4 implements the narrower real A34 boundary: disabled means no service, command, UI, lifecycle, or noise; enabled configuration is rejected. There is no named legacy competition behavior.

## Canonical configuration

`PhaseFourSchema` extends the existing registry with lifecycle fields. `PhaseFourConfigurationCompiler` reads `lifecycle.yml` from the same immutable `CompiledConfiguration` produced by the lossless draft pipeline; it uses bounded YAML settings, rejects duplicate keys and converts parse/constructor failures into structured validation findings. The compiler covers Prestige, reset policy, currencies, entitlements, milestones, seasons and the competition boundary. Season reset policy is deliberately narrower than Prestige reset policy: the schema/compiler/model expose only the required `season-progress: RESET|PRESERVE` field, and any unrelated component or invalid value produces an actionable error before apply.

Semantic validation resolves required/reset stages and Phase 3 requirement-tree/cost/reward references. It transitively walks the enabled Prestige tree, validates every reachable leaf with the Phase 3 metric machinery, requires each consumed provider to be active and healthy, and seals its exact generation. Dormant, unreachable requirements remain unpinned and non-blocking. An active Phase 4 snapshot must share the revision with prior snapshots and retain every prior provider pin exactly. Structural apply never mutates player state.

Prestige scaling remains requirement-local: the referenced Phase 3 requirement tree owns exact scaling/catch-up formulas, while the trusted `PrestigeProgressContextSource` supplies current Prestige index, catch-up position and scope identities. UI/API callers cannot provide those values. Phase 4-level Prestige scaling/catch-up references and season requirement-override/catch-up maps are reserved and rejected when non-empty. Season timestamps are metadata for the manual lifecycle and do not schedule work.

## Prestige authorization and confirmation

`PrestigeIntent` contains only actor, UUID and idempotency intent. `PrestigeAuthorizationService` is the sole production authority issuer. It loads the current immutable configuration and authoritative stage/Prestige rows, checks arbitrary configured eligibility/reset stages, finite or unlimited cap, cooldown, all resulting counters/revisions for signed-long overflow, exact player CAS revisions, role-local provider pins/health and trusted progress context, then evaluates the configured requirement tree and preflights all costs and Prestige/milestone rewards. Only providers actually consumed by this operation are sealed; an unrelated optional unhealthy provider is not globally fatal.

Before mutation it produces one immutable `PrestigeSimulation` containing:

- source/reset stages and current/lifetime counts before/after;
- bound requirement evaluation, exact costs and rewards;
- each internal currency reset;
- every reset/preserve component;
- old/new Prestige scopes, next baselines and latch isolation;
- known milestone and season consequences;
- provider actions, idempotency and possible uncertainty;
- stored-player provenance, active revision, state revisions, provider pins and plan time.

Simulation has no repository writer and performs no mutation. Executable authority is package-opaque and binds the complete simulation, plan, actions, revisions, pins and projection. A blocked result receives denied authority. Both the executor and direct Prestige persistence boundary validate authority before journal insertion, so a reconstructed/tampered or stale confirmation cannot become executable or journaled.

Current Prestige is the configured active-cycle counter and is cap-governed. Lifetime Prestige is monotonically incremented historical state and is never reset by policy. Both increments are positive configuration values and arithmetic overflow fails closed.

## Reset/preserve and operation ordering

Every `ResetComponent` must be `RESET` or `PRESERVE`, but Phase 4 accepts only dispositions it can execute truthfully. `PROGRESSION_STAGE` is always `RESET`. `ACTIVE_REQUIREMENT_PROGRESS`, `BASELINES`, and `LATCHED_COMPLETIONS` must all be `RESET` or all be `PRESERVE`: reset allocates a new Prestige scope, samples only reachable `SINCE_PRESTIGE_START` requirements, and leaves prior evidence historical; preserve retains the current scope, baselines, progress, and latches with no new baseline rows. `PRESTIGE_SCOPED_CURRENCY` independently supports exact reset-to-zero or preserve. `PURCHASED_PERKS`, `MILESTONE_HISTORY`, `SEASON_PROGRESS`, and `HISTORICAL_STATISTICS` are preserve-only because Prestige has no truthful owner for those mutations; unsupported reset settings fail validation. The structured `ScopedRequirementStateConsequence` and exact component consequences are shared by simulation and persistence-facing plan data.

External-system resets are not inferred from metric capability. They are disabled and rejected because Phase 4 has no qualified reset provider. No generic command reset is executed.

The deterministic ordering is:

1. validate exact authority, active revision and provider generations;
2. insert the prepared operation, details and every pending action;
3. validate external managed-rank targets before debit;
4. execute and verify costs in configured order;
5. recheck the active configuration revision, every sealed provider generation, role activation, and health immediately after all costs;
6. execute and verify configured managed-rank projection, if any;
7. execute one SQLite transaction for authoritative internal commit;
8. mark internal actions and `STATE_COMMITTED` from persisted evidence;
9. execute rewards in configured order;
10. complete only when required results are known.

The internal transaction CAS-updates stage and Prestige rows, conditionally writes exact reachable Prestige baselines, conditionally resets configured Prestige-scoped currency with ledger rows, inserts milestone awards, and appends stage and Prestige history. Normal rank-up now also performs its stage CAS and stage-history append in one transaction, with operation, complete actor type/optional UUID/name, reason, revision, and entered-stage provenance. Prestige-reset history persists the same complete actor identity. A failed/stale CAS leaves no orphan history. External projection success followed by unknown/failed internal commit becomes reconciliation work. Post-commit reward failure/uncertainty cannot roll back authoritative state and is recorded truthfully.

## Internal currency

Currency definitions use immutable `CurrencyId`; display name and symbol are metadata. Values use `ExactDecimal`/`BigDecimal`, never floating point. Each definition bounds scale (0–18), precision (1–38), maximum balance and explicit rounding mode. Negative balances and unrepresentable deltas fail before commit.

SQLite maintains the existing account table plus an append-only `mp_currency_ledger`. Account update and ledger insert share one transaction. Independent store instances rely on SQLite transaction serialization plus bounded whole-transaction retry for `BUSY`, `LOCKED`, and busy-snapshot outcomes; there is no correctness dependency on a JVM monitor. `(operation_id, action_id)` is unique and authoritative; an exact replay returns the stored result only when player, currency, delta, kind, configuration revision, actor type/optional UUID/name, source, and reason all match. A fresh `occurredAt` is retry timing and is intentionally not identity. Any semantic provenance collision is rejected without a second mutation. Cross-instance tests cover lost-credit prevention, no overspend, independent actions, and simultaneous exact replay. History stores actor, source, reason, immutable currency ID, exact delta/balance, operation/action and config revision. Native debit, reward, and compensation provenance comes from the sealed `PlannedCost`/`PlannedReward` revision, never a mutable active-revision supplier. Administrative adjustment requires a non-player actor and reason.

## Entitlements

Entitlements have immutable IDs and typed integer, exact-decimal or boolean values. Contributions carry source identity and explicit priority. The merge engine sorts by priority and stable source ID, rejects duplicate source IDs and type mismatches, and returns the effective value plus deterministic contributing provenance.

Strategies are `MAX`, overflow-safe exact `SUM`, `MIN`, priority `OVERRIDE` and `BOOLEAN_OR`. Empty-source identity is explicit per strategy/type. `SUM` enforces configured numeric bounds; no hash-map iteration order influences a result.

## Milestones

Milestone definitions have stable IDs, typed thresholds, trigger type, repeatability and reward references. Prestige execution evaluates current/lifetime Prestige triggers because that lifecycle owns those values reliably. Enabled stage, season, and provider-metric triggers are rejected until their lifecycle hooks exist, so accepted configuration cannot imply an inert award path.

Repeatability keys are `once`, `prestige-<count>` or `season-<id>`. The database primary key prevents duplicate delivery, and the award row plus reward snapshot is inserted in the same internal Prestige transaction. Triggered rewards participate in canonical preflight, journal ordering and external uncertainty handling.

## Seasons

Seasons are progression-data containers, never world controllers. A stable season ID stores a display snapshot, optional configured timestamps, the season-specific progress disposition, requirement overrides and catch-up references. `RESET` initializes entry at zero; `PRESERVE` carries the most recently archived immutable progress value. Runtime lifecycle is manual in Phase 4.

SQLite enforces one active season with a partial unique index. Start allocates a new scope and records history. Player entry first prepares boundary values without writing, then reads and qualifies `ACTIVE`, conditionally inserts the player-season row, and persists every required `SINCE_SEASON_START` baseline in one database transaction. A concurrent archive either follows a committed entry or invalidates the stale write snapshot; it cannot leave a successful entry into an already archived season. Existing baseline replay must match exactly. Fault injection proves rollback, restart, duplicate-free retry, and the archive-vs-entry ordering. Progress updates include an `ACTIVE` predicate in the mutation statement; after archive they fail explicitly and the historical value remains readable and unchanged across reopen. End atomically archives the season and appends end/archive events before another can start. Bounded indexed history/progress queries survive restart. No world, Nether/End, resource-world, PvP or Court lifecycle is invoked.

## Recovery and history

`PendingOperationRecoveryService` scans at most 1–1000 incomplete operations ordered by indexed state/update time. Stored action views expose provider ID, action type, reversibility, idempotency, state, and failure evidence. Prestige preparation persists the exact sealed native cost/reward payload required for bounded recovery.

- `PREPARED` is safe to fail because execution never began.
- committed internal Prestige evidence advances known internal transaction actions to verified;
- a pending or started native idempotent reward is replayed only through the same healthy pinned native provider with its exact sealed payload and operation/action ID, making already-applied replay unchanged;
- before internal commit, known-applied reversible/idempotent native costs are compensated in reverse order from their exact persisted payload;
- terminal `COMPENSATED` is permitted only when every potentially consequential cost is proven absent or safely reversed; a mixed `STARTED`/`UNCERTAIN` external cost keeps the operation in `NEEDS_RECONCILIATION` even when known native costs were safely compensated;
- interrupted external `STARTED` actions become `UNCERTAIN`, and incomplete/uncertain external rewards remain reconciliation work with no replay;
- generic non-Prestige operations retain evidence without invented replay semantics.

Every automatic recovery transition writes `mp_recovery_events`. Prestige history starts as `STATE_COMMITTED` inside the atomic transaction and is updated to the truthful final `COMPLETED` or `NEEDS_RECONCILIATION` result. Mixed-cost restart tests exercise both cost orderings and prove one native compensation, external uncertainty retention, reconciliation, and no duplicate compensation on retry. Recovery does not claim universal external exactly-once or rollback.

Migration 4 adds player Prestige state, Prestige operation details, exact currency ledger, stage/Prestige history, milestone awards, seasons/player-season/history, exact native recovery payloads, and recovery events, plus bounded-query indexes and the one-active-season constraint. Migration 5 evolves the already-defined Phase 4 stage history with nullable `actor_uuid`; normal rank-up and Prestige-reset inserts bind it, reads reconstruct it, and UUID-less actors remain valid. Existing V1 data and protected roots are untouched.

## Performance and storage bounds

- no Phase 4 SQL write occurs per high-volume provider progress event;
- currency mutations, season lifecycle/progress writes and Prestige are explicit consequential operations;
- currency/Prestige/season/recovery queries require limits of 1–1000;
- recovery is indexed and bounded; it performs work per pending operation, not per online/offline player;
- no player cache, unbounded queue or full-table hot-path leaderboard scan was introduced;
- exact decimal scale/precision is bounded before persistence;
- core contains no Bukkit/Paper call and no placeholder render path.

## Honest deferrals

- Phase 6 owns player/staff commands, confirmation UI, administration and statistics presentation.
- Phase 5 owns real Vault, mcMMO, PlaceholderAPI, shop and playtime integrations; A24 remains Partial.
- Phase 8 owns stable public SDK/events and retention/export administration.
- Full generic competitions, stage/season/provider milestone award hooks, purchased-perk commerce, production bootstrap wiring, live-server qualification and MySQL/MariaDB implementations remain Later.
- A14/A15 remain Partial because production stage-entry/completion composition still does not invoke the Phase 3 baseline/latch services. A16/A17 are also Partial under the same project criterion: engine + persistence lifecycle semantics are implemented and restart-tested; production runtime composition remains later.

Final verification evidence is recorded in `STATUS.md`; exact acceptance evidence is in `docs/V2_TRACEABILITY.md`.
