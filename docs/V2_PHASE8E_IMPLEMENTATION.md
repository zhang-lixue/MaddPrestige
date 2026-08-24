# MaddPrestige V2 Phase 8E implementation

**Baseline:** `73076fe0341024780e3652fda8b0b7f5e38b3cea`
**Qualified:** 2026-08-23
**Candidate state:** accepted by Independent Owner Review 3; exact Phase 8E publication payload

## Scope and outcome

Phase 8E qualifies performance, dependency failure, multi-provider composition, offline behavior and public operation
ordering. It does not begin packaging/release hardening, freeze the compatibility baseline, execute A76 or change the
external-SQL scope decision. Independent Owner Review 3 accepts A61, A62, A65, A66 and A67 as `Satisfied`, producing
the mechanical ledger 62 Satisfied / 13 Partial / 1 Later. A63 remains `Partial`, A64 remains `Later`, and A76 remains
`Partial / Deferred` until the final frozen Phase 8 release candidate.

The qualification uses public Paper/plugin/service/event surfaces and read-only SQLite observation. The Paper harnesses
do not replace production logic. The initial qualification/correction work is preserved; Owner Review Correction Pass
2 closes OR8E-04 through OR8E-06 and requalifies the exact final distribution.

## Production corrections

### Reentrant optional-integration binding

**Root cause:** a synchronous Bukkit service or lifecycle notification could re-enter integration reconciliation while
the same dependency was being unbound or rebound. The earlier binding-local protection did not cover the whole
transition, so a provider generation could be duplicated or composed against an intermediate state.

**Correction:** `BindingAttemptGuard` owns one dependency-name attempt for the full unbind/reconcile/bind transition.
Nested notification for that exact dependency returns without starting another generation. Independent dependency
names remain free to progress. CraftEngine, Vault service churn, plugin lifecycle and canonical recomposition now share
the same guarded boundary.

**Focused proof:** `BindingAttemptGuardTest` proves same-key recursion is rejected and ownership is released afterward.
The real fault run proves duplicate declaration rejection, Alpha unregister/rebind, Vault loss/replacement,
CraftEngine reload and independent Alpha/Beta operation without duplicate publication.

### Safely dormant first-boot setup discovery and complete generated integration state

**Root cause:** on a first boot with no active configuration, all optional integrations were disabled. The canonical
setup validator therefore could not discover real selected integration providers and could not generate a usable
initial configuration, even though safely dormant startup was otherwise correct. Correction Pass 1 then mapped the
literal mcMMO requirement but missed the built-in manual alias `phase5_events:mcmmo_adjusted_xp_total`, which is backed
by `integrations.mcmmo.enabled`.

**Correction:** first-boot setup discovery may register exact compatible optional provider metadata while progression
remains dormant. The first canonical reconciliation ends discovery and activates only providers reachable from the
published integration configuration. Setup now derives integration enablement from all selected requirements, costs
and rewards: Vault balance/cost/reward enables Vault, mcMMO power-level enables mcMMO, and GriefPrevention claims or
claim-block reward enables GriefPrevention. Correction Pass 2 replaces dispersed recognition with one authoritative
15-entry built-in selection map: ten representable identities enable exactly Vault, mcMMO or GriefPrevention and five
unrepresentable identities reject before publication. The ten include Vault balance/cost/reward, mcMMO power,
`phase5_events:mcmmo_adjusted_xp_total`, four GriefPrevention claim metrics and its reward. The five rejected shapes are
mcMMO skill-filter, Placeholder input, WorldGuard region, CraftEngine count and CraftEngine reward because simple setup
cannot encode their required metadata. No provider is silently enabled for an unrelated setup.

During first-boot discovery, the manual alias may be provisionally active only when the real mcMMO plugin is enabled,
so validation can observe it without publishing progression. The first canonical reconciliation either retains it
because generated `mcmmo.enabled=true` is active or deactivates it exactly. Every semantic source of
`setup.integration.unconfigurable` calls one helper with truthful `provider`, `component` and `requirement` facts.

**Focused proof:** `SetupWizardBuiltInMappingTest` asserts the complete exact 15-entry map;
`PhaseSixConfigurationAdministrationTest` covers generation/application and both unconfigurable semantic paths;
`InitialManualProgressActivationTest` covers configured, appropriate discovery, missing mcMMO and non-discovery
activation. Built-in and alternate catalogs render all three facts with no missing placeholder. A separate
exact-distribution Paper boot passed 26/26 assertions: the combined Vault/mcMMO/manual/GriefPrevention profile survived
reconciliation, the alias completed a real paid transition and WorldGuard/CraftEngine stayed disabled.

### Java 25 virtual-thread observation

**Root cause:** the original qualification counted threads with `Thread.getAllStackTraces()`, which cannot enumerate
Java 25 virtual threads and therefore did not prove operation/Placeholder task fan-out.

**Correction:** qualification-only `VirtualThreadObservation` streams JFR `jdk.VirtualThreadStart`,
`jdk.VirtualThreadEnd`, `jdk.VirtualThreadPinned` and `jdk.VirtualThreadSubmitFailed` events. It classifies the exact
production operation and Placeholder publisher name families while the existing sampler remains explicitly limited to
platform threads. The corrected frozen plan and fresh two-boot Paper run record exact active/high-water/start/end,
backlog and convergence values without changing production scheduling.

**Proof:** operation tasks reached high-water 128 with 6,034 starts/6,034 ends; Placeholder publication reached
high-water 12 with 11,648 starts/11,648 ends; both were active zero at final convergence, pin and submission-failure
counts were zero, and no task family grew after load ended. All service and Placeholder backlogs also converged to zero
in 3,399 ms.

### Stable logical-provider synchronous callback bulkhead

**Root cause:** an incomplete returned stage proved asynchronous timeout behavior but not a provider callback that
blocks the bounded callback executor before returning its stage. Correction Pass 1 added four permits to each
transient provider generation. Old Alpha generation callbacks could therefore retain four workers while a replacement
generation received four fresh permits and consumed the remaining shared workers, starving Beta.

**Correction:** one bridge-owned `ProviderCallbackBulkhead` registry keys non-blocking four-permit state by canonical
owner-qualified provider identity (`namespace:localId`). Transient generation leases share that state. Closing a
generation rejects its new work but cannot erase permits held by its already-running callbacks. State is reclaimed only
when generation count and active callback count are both zero. The permit covers the synchronous provider invocation
up to stage return; an asynchronous stage does not retain it. Independent Alpha and Beta keys never share allowance;
deadlines, cancellation and stale-generation fencing remain unchanged.

**Proof:** unit tests hold four generation-1 callbacks, acquire generation 2 and prove it receives no fresh permits;
Beta remains independently usable, 20 rapid rebind cycles retain exactly one Alpha state, and the state reclaims after
the final callback/generation release. The exact real-Paper 48/48 run held four old-generation callbacks, attempted
eight generation-2 calls (0 admitted/8 rejected), attempted four calls after repeated rebinds (0/4), exercised
unregister without resetting the allowance, and kept Beta usable with maximum 100 ms latency. Release recovered in
48 ms, final Alpha active count was zero, callback workers were six of the bounded eight, and later Alpha/Beta calls
succeeded. The run also withdrew and rebound the exact EconomyShopGUI and QuickShop-Hikari diagnostic listeners once.

### Zero-effect PRE cancellation and Placeholder publication

**Root cause:** the public asynchronous RankUp/Prestige completion hook refreshed Placeholder output for every terminal
result. A PRE-cancelled or listener-failed unknown-player operation could therefore materialize cached output even
though the accepted event contract requires zero effect before durability.

**Correction:** `OperationPlaceholderRefreshPolicy` permits publication only when the operation completed normally and
has a durable operation ID. Cancellation, pre-journal rejection and exceptional completion do not refresh.

**Focused proof:** `OperationPlaceholderRefreshPolicyTest` covers durable, pre-durable and exceptional outcomes. The
real fault matrix cancels an unknown-player PRE event and observes no player row, no debit, no durable ID, no POST and
no forbidden publication side effect.

### Optional LuckPerms absence linkage

**Root cause:** bootstrap resolved the LuckPerms API class while asking Bukkit for a registration even when the plugin
was absent. Because the API is an optional compile-only boundary, that direct resolution caused a
`NoClassDefFoundError` instead of a safely dormant boot.

**Correction:** bootstrap first checks the dependency by plugin name and enabled state. It resolves the typed service
only when the LuckPerms plugin is actually present. A configured missing rank authority remains fail-closed; no rank or
group is fabricated.

**Focused proof:** a fresh real Paper process containing only MaddPrestige plus the two qualification providers boots,
runs Doctor, exposes healthy built-ins/SDK providers and shuts down cleanly while all supported optional dependencies
are absent.

## Qualification components

Four isolated Maven modules under `qualification/` build test-only Paper plugins:

- Alpha owns two offline-capable metrics and controlled unavailable, throw, linkage-like, malformed, asynchronous
  timeout, synchronous latch block, unregister and replacement behavior;
- Beta independently owns an offline metric and an explicit online-only metric;
- Economy owns a controlled Vault Economy service with loss, replacement, failure, throw, applied/verified and
  uncertain outcomes;
- the Paper harness coordinates the predeclared load and fault matrices through Stable public services/events,
  Bukkit lifecycle/service events and read-only database observations.

These artifacts are qualification tools, not production dependencies. Third-party JARs, disposable worlds,
databases, caches and runtime configuration remain outside Git and the owner-review bundle.

## Evidence map

| Acceptance / register | Evidence | Candidate disposition |
|---|---|---|
| A61 | `V2_PHASE8E_OFFLINE_PROVIDER_MATRIX.md` | Satisfied |
| A62 / P8B-F014 | `V2_PHASE8E_LOAD_PLAN.md`, `V2_PHASE8E_PERFORMANCE_EVIDENCE.md` | Satisfied / closed |
| A65 / P8B-F016 | `V2_PHASE8E_MULTI_PROVIDER_MATRIX.md` | Satisfied / resolved |
| A66 / P8B-F017 | `V2_PHASE8E_EVENT_FAULT_MATRIX.md` | Satisfied / resolved |
| A67 / P8B-F015 | `V2_PHASE8E_DEPENDENCY_FAULT_MATRIX.md`, `A65_A66_A67_OR8E05_FAULT_MATRIX_SANITIZED.log` | Satisfied / closed |

Sanitized runtime logs are under `docs/evidence/phase8e/`. Verification and isolated-build logs are generated only
after the final documentation state is frozen. Exact test totals and artifact hashes are recorded in the owner-review
summary and are not inferred from a prior phase.

## Preserved boundaries

- No Stable SDK or Paper event surface changed.
- Protected V1 Java is untouched.
- MySQL/MariaDB/HikariCP production paths remain absent; A64 is not relabeled.
- SQLite transaction, journal, lease, uncertainty, reconciliation, migration, backup and configuration-publication
  invariants remain authoritative.
- Phase 8F packaging/release hardening and final compatibility freeze have not started.
- Phase 9 deployment/migration qualification has not started.
- A76 has not run and remains intentionally deferred.
