# Phase 8B implementation

## Scope and stop line

Phase 8B supplies the public service/provider/event API candidate and activates the already accepted runtime engines. It does not freeze API compatibility, implement MySQL/MariaDB, finish offline/load/fault qualification, provide the Quick Start/public documentation set, create i18n, or execute any Phase 8C–8F/Phase 9 work.

## Startup and public service

`MaddPrestigeV2Plugin` starts in this order:

1. create missing MaddPrestige-owned seed templates and select only a checksum/history-verified active Phase 6 snapshot;
2. migrate/verify SQLite;
3. construct the one provider registry and health observer;
4. register built-ins and live LuckPerms;
5. validate/compose accepted optional integrations and manual progress;
6. import owner-attested public provider declarations;
7. hydrate the exact stored active revision, or remain safely dormant when no active pointer exists;
8. recover pending operations;
9. compose `ProductionRuntime`, Phase 6 commands/GUI and Placeholder publication;
10. mark ready and register `MaddPrestigeService` through Paper `ServicesManager`.

The public service is never exposed during recovery. Its corrected candidate contract is:

- `playerProgress(UUID)`: asynchronous SQLite materialized read;
- `stages()`: synchronous immutable canonical stage catalog;
- `evaluateRankUp(UUID)` / `evaluatePrestige(UUID)`: asynchronous side-effect-free authorization, requirement
  progress and plan simulation;
- `requirementProgress(UUID)`: asynchronous bounded requirement-leaf views;
- `currencies(UUID)`: asynchronous exact configured internal-currency balances;
- `activeSeason(UUID)`: asynchronous optional active-season/player-progress view;
- `rankUp(UUID)`: asynchronous canonical authorization plus journaled rank-up;
- `prestige(UUID)`: asynchronous canonical authorization plus journaled Prestige;
- `providers()`: synchronous immutable registry cache snapshot with no provider/database callback.

Operational database, provider and lifecycle failures complete with one bounded `ServiceResult`/`ServiceError` policy.
Public mutation ingress allocates exactly one request UUID before authorization. The same UUID is sealed into the
authorization/execution plan, delivered in PRE and POST snapshots, and returned by `OperationResult`; it is never
derived from the separately allocated durable `OperationId`. The durable ID is exposed only when a journal row exists.
Entitlement values are not exposed as Stable because the accepted entitlement merge
engine has no durable per-player aggregate read authority; adding that read remains an explicit pre-baseline decision,
not a fabricated empty surface.

Caller cancellation affects only the detached caller future. Accepted durable work continues to a terminal journal outcome. Shutdown unregisters the service, rejects new mutations with bounded `service.closed` results, rejects new reads, and gives the operation executor a bounded drain window.

## Live operation composition

The production runtime owns real `SqlitePlayerStageRepository`, `SqlitePlayerPrestigeRepository`, requirement state,
currency ledger, Prestige lifecycle, operation journal and stage-transition fence/committer objects. Live requirement
contexts read authoritative stage, current/lifetime Prestige and active-season player progress, derive scaling from
current Prestige and catch-up from active-season progress, and supply absolute/lifetime/stage/Prestige/season scope
identities. Reads and PRE authorization use a virtual initial state for an unknown player. After successful PRE, exact
virtual-or-durable player state, active revision and provider generations are revalidated before the single atomic
stage-plus-Prestige initializer may write either row. A second exact validation and duplicate fence runs after
initialization and before journal insertion.

Rank-up and Prestige both use canonical configuration/provider pins and player revision fences. PRE delivery is
inserted after authorization and immediate bindings checks and before initialization/duplicate/journal insertion. A
stale successful PRE returns without initializing an unknown player. Only after both the pre-initialization and final
pre-journal exact checks pass may a `PREPARED` operation exist.

POST delivery is conditional on the stored operation being durably `COMPLETED`, `COMPENSATED`, `FAILED` or `NEEDS_RECONCILIATION`. The immutable event status preserves uncertainty rather than converting it into success.

## LuckPerms

The Paper plugin retrieves the live `LuckPerms` instance from `ServicesManager`, validates the providing plugin is enabled, wraps it in the accepted `LuckPermsRankAdapter`, and activates it in the one internal registry. It remains a consumer of configured existing groups. No group creation, hierarchy mutation or fabricated default-group ownership was added.

## Provider SDK and bridge

External plugins register `ProviderDeclaration` as a Paper service. The external declaration supplies only:

- bounded generation-free metadata and metric definitions;
- a bounded asynchronous requirement read callback;
- optional lifecycle notifications;
- an opaque exact-registration handle with canonical provider ID and asynchronous unregister.

The bridge derives the owner from `RegisteredServiceProvider.getPlugin()`, then cross-checks
`JavaPlugin.getProvidingPlugin(declaration.getClass())` against that registered owner before canonicalizing the enabled
plugin name into `plugin_namespace:local_id`. Lossy namespace collisions are rejected. This prevents accidental or
cross-plugin claims using supported public Paper APIs. Installed server plugins remain inside the trusted-server
boundary; Paper does not provide cryptographic attestation against a hostile plugin with server-level code execution.
Caller text cannot assert the owner or generation. Metadata is obtained before the registry monitor, then rebound
into the internal legacy metric/provider contract. The internal generation is added only on the trusted side.

Registration/unregistration work is serialized on one dedicated daemon lifecycle executor, so a large startup import
cannot occupy callback workers while waiting for nested metadata callbacks. The callback executor is separately
bounded from 2 to 8 daemon threads with direct handoff and rejection. Metadata/lifecycle callbacks have a two-second
deadline; metric reads have a three-second deadline applied to both callback invocation and returned completion.
`ProviderCallContext` supplies correlation, deadline, live cancellation, bounded-worker expectation and attested
owner/provider identity without exposing a journal or registry generation. Duplicate/normalized-colliding metric and
dimension metadata, extra/missing/null/type-incompatible result-map entries, throws, linkage failures, timeouts and
saturation fail that provider safely and do not invoke another provider under the registry lock. Late/stale handles
cannot remove a replacement generation.

The public manual-progress writer proposed during audit is omitted. The existing internal source is safe for accepted first-party event bridges, but public mutation needs a separate authority/rate/schema policy.

## Events

Stable event classes:

- `PreRankUpEvent` / `PostRankUpEvent`;
- `PrePrestigeEvent` / `PostPrestigeEvent`;
- `ConfigAppliedEvent`;
- `ProviderHealthChangedEvent`.

Operation payloads are immutable API snapshots with IDs, player, kind, source/target, configuration revision, terminal status and time. They expose no plan, repository, registry, mutable core or vendor object.

PRE/POST listener invocation iterates registered Paper listeners synchronously on the Paper server thread. PRE listener exceptions propagate to the executor's pre-journal fail-closed boundary. POST listener exceptions are logged per listener and delivery continues without changing the durable outcome. No registry/configuration/operation lock is held during delivery.

`PaperOperationLifecycle` tracks the player UUID while a listener stack is active. A public mutation request for the same player returns `CONFLICT`/`operation.reentrant` immediately. Read calls remain available, and another player is not synchronously locked by that marker.

Configuration events are scheduled only from the accepted Phase 6 applied callback. Provider health events are emitted outside the registry monitor only when state/code/reason meaning changes; timestamp-only refreshes are coalesced.

## Full Phase 6 administration and durable restart selection

The production root now constructs the accepted schema, configuration history, immutable snapshot store, administration, introspection, preview/confirmation, doctor, why, player view, setup, manual Prestige, GUI mutation/action/session, command and completion services. `PaperPhaseSixCommandAdapter` and `PaperPhaseSixGuiController` are the live command/GUI boundary; no shortcut reload authority was added.

`AtomicConfigurationFileStore.activeDocuments()` verifies pointer syntax, manifest, every document checksum,
aggregate content hash and exact flat inventory. Startup then requires the same exact revision in durable APPLIED
history and hydrates that identity without a synthetic startup revision. Corruption or pointer/history disagreement
fails startup closed. Root seed documents are templates only: when no active pointer exists the runtime stays dormant
and the canonical setup workflow remains available.

An accepted configuration apply first makes the Phase 6 revision authoritative, marks operation publication
unavailable, and then reconciles optional integrations in deterministic order before hydrating/publishing the exact
revision, completion/schema state and `ConfigAppliedEvent`. Placeholder enable/disable, provider activation and
CraftEngine limits therefore cannot retain stale old authority. Provider lifecycle changes queue a coalesced
Paper-thread recomposition of the same stored revision; a missing generation remains visibly fail-closed and rebind
recovers without inventing a revision.

## Placeholder output

`PlaceholderSnapshotPublisher` schedules online-player refresh at join and every 20 ticks. SQLite stage/Prestige reads run outside the Paper thread, are bounded to one in-flight refresh per UUID, and update the existing bounded immutable cache. Quit removes the snapshot. PlaceholderAPI rendering continues to use only the cache and performs no repository/provider call.

## Manual-progress backpressure

The prior `flushAsync()` created a new task and potentially one very large repository write for every request. Phase 8B retains one shared in-flight future and drains at most 1,024 dirty records per repository call. Concurrent callers coalesce onto that future; updates arriving during a write remain dirty by version; a failed batch remains retryable; close stops new writes, drains the same pipeline and has bounded platform shutdown waits.

Persistence failures retain dirty records, transition the existing provider health to `DEGRADED`, and are logged
through a five-minute coalescing throttle. A later successful drain restores `AVAILABLE` health and resets the throttle.
Focused tests cover repeated coalesced requests, concurrent updates, 2,100 records split into three batches,
failure/retry health and retention, log throttling/recovery and close behavior.

## API reduction and compatibility posture

The corrected owner-review candidate adds 33 public SDK top-level types while preventing the new stable surface from
exposing legacy journals/generations:

- service and immutable read/result DTOs;
- stability annotations;
- immutable event payloads;
- owner-attested provider declaration/context/metadata/metric request-result/handle contracts;
- the minimum ID/metric semantic types required by those contracts.

The old `Provider`, `ProviderDescriptor`, `ProviderSnapshot`, `MetricProvider` and `RankAdapter` are explicitly
Experimental. Surface A is the 109-type Bukkit-free SDK inventory in `V2_PHASE8B_API_INVENTORY.md`. Surface B is the
mechanically generated six-type Paper Stable event inventory in `V2_PHASE8B_PAPER_API_INVENTORY.md`.
`StableApiLeakageTest` recursively checks all public constructors/methods/interfaces of the 44 functional SDK Stable
types; `StablePaperEventSurfaceTest` discovers every public Paper `@Stable` event and permits only the minimum Bukkit
event types plus immutable Stable SDK snapshots. Both surfaces remain future baseline candidates; no baseline is frozen.

## Evidence and truthful limitations

Unit/integration evidence covers authoritative progress/scaling/catch-up/scopes, registry lock isolation, cached
snapshots, health failure/coalescing, manual persistence health/backpressure, complete public read/evaluation surfaces,
service cancellation/shutdown/reentrancy, implementation-owner attestation and namespace collision, provider metadata
and result validation, rank/Prestige unknown-player PRE cancellation/listener failure, post durability, exact restart
revision and deterministic configuration/integration recomposition.

The first-party `qualification/phase8b-paper-harness` was built as a separate plugin and booted with the actual shaded
`MaddPrestigeV2Plugin`, Paper 26.1.2, LuckPerms 5.5.58 and PlaceholderAPI 2.12.2. A fresh boot passed 22 canonical
setup/service/provider/PRE/POST/config-reconcile/Placeholder/shutdown assertions, including PRE/POST/result correlation,
distinct request/journal identities, deliberate post-PRE authority invalidation with zero player materialization, and
an incomplete two-key provider result failing closed. A second unchanged boot passed three exact-revision/state/
provider/Placeholder recovery assertions. Sanitized evidence is in
`PHASE8B_PAPER_QUALIFICATION.log`; third-party JARs remain only under ignored disposable `target` data.

A65 and A66 remain Partial because one first-party process exercise does not complete their broader independent
provider and live uncertainty/fault matrices. A61/A62/A67/A70 remain Partial pending their specified later
qualification. In particular, the current per-player virtual-thread/SQLite Placeholder refresh fan-out remains an
A62 Phase 8E load/TPS item. A02/A76 remain Partial pending Phase 8D usability and public-document validation.
