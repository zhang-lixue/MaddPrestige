# MaddPrestige V2 Phase 5 implementation

**Implementation date:** 2026-08-16

**Starting baseline:** `38cde9a084712a3f8c06edb0e77dc31333204374`

**Scope:** real flagship integration adapters, lifecycle/outage behavior, typed configuration and exploit-safe compatibility

## Outcome and boundary

Phase 5 adds real compile-time adapters for Vault, mcMMO, PlaceholderAPI, EconomyShopGUI and QuickShop-Hikari. It uses only published public Java APIs/events; there is no reflection, command scraping, plugin database/file access or internal implementation coupling. Every optional integration is disabled by default, third-party artifacts have Maven `provided` scope, and no implementation jar is shaded.

The frozen V1 bootstrap, source/resources/tests, plugin descriptor and runtime configuration remain unchanged. The V2 provider factories, event listeners and expansion are implementation-ready/testable boundaries but are not registered by V1. Phase 6 command/UI/bootstrap administration and all Phase 7 integration scope remain unstarted.

## Artifact qualification

| Plugin | Runtime/version evidence | Compile/test API | Phase 5 outcome |
|---|---|---|---|
| Vault / VaultUnlocked | VaultUnlocked 2.20.2; installed jar SHA-256 `BD9E...` recorded during audit | `com.github.MilkBowl:VaultAPI:1.7.1` | Cost, reward and balance providers |
| mcMMO | 2.2.053; installed jar SHA-256 `7B7C48AF96CDE8F6CCE20C4E0ABFFA06D096E323CCD0FA534310B96B134BE143` | `com.gmail.nossr50.mcMMO:mcMMO:2.2.053` from the official NeetGames repository | Skill/power metric provider and adjusted-XP event source |
| PlaceholderAPI | 2.12.2 API; 2.12.3 binary surface also checked | `me.clip:placeholderapi:2.12.2` from ExtendedClip | Cache-only output expansion and scheduled typed input provider |
| EconomyShopGUI | Runtime 7.2.0 SHA-256 `E6B41CEBE12B7BC9D679CC02A7EB3E576E731CD0F050AB6233D781F7DB0955B7` | official published `com.github.Gypopo:EconomyShopGUI-API:1.10.1`; used post-event members are binary-compatible with runtime 7.2.0 | Optional compatibility diagnostics only; progression deferred |
| QuickShop-Hikari | 6.2.0.11 SHA-256 `EB3D...` recorded during audit | `com.ghostchu:quickshop-api:6.2.0.11` from CodeMC | Compatibility diagnostics only; hard zero progression credit |
| PlayTimeManager | Installed 3.6.5 SHA-256 `1F71...` recorded during audit | No dedicated public service artifact needed by MaddPrestige | Deferred as redundant/less authoritative than existing Paper statistic provider |

The shortened hashes above retain the audit-record prefix for artifacts whose full local hash is not an accepted runtime dependency identity in the build; Maven coordinates and build-generated SBOM are the reproducible dependency evidence. No local downloaded implementation jar is copied or bundled.

## Configuration and activation

`maddprestige-integrations/src/main/resources/integrations.yml` is schema version 5 and disables every integration. The integration-owned schema extends `PhaseFourSchema` without placing plugin concepts in core. The strict YAML compiler is bounded, disallows duplicate/recursive/non-scalar keys, rejects unknown fields and produces a structured error with a disabled configuration fallback. Every present integration/nested configuration object must be a mapping. Absent optional objects receive dormant defaults; explicit null, scalar and sequence shapes fail with the exact path, expected type and practical actual type. Schema version and booleans are actual YAML integer/boolean values rather than string-coerced values.

Placeholder inputs require an actual string mapping key accepted by canonical `MetricId`, external placeholder string, canonical `MetricValueType` and positive ISO-8601 maximum age. Numeric/boolean keys and invalid identifiers fail at the exact input path. Every closed `%identifier_parameters%` token is inspected; identifier `maddprestige` is rejected case-insensitively, including mixed-case/multiple-placeholder input, while `%someplugin_maddprestige_value%` remains valid because its identifier is `someplugin`. EconomyShopGUI and QuickShop progression credit accept only `false`; `true` is rejected rather than silently ignored. `PhaseFiveIntegrationPlan` consumes every supported enablement value into provider/event/expansion decisions.

`IntegrationProviderLifecycle` owns one dependency's exact registry bindings. Registry activation represents configuration reachability; `MutableProviderHealth` represents operational dependency truth. Discovery registers inactive without rewriting health. Configuration application reconciles only the exact desired activation set: newly desired providers activate, removed providers deactivate, and unchanged providers are untouched. Config off→on preserves `NOT_INSTALLED`, `INACTIVE`, `DEGRADED`, `UNSUPPORTED`, `UNAVAILABLE` and `UNHEALTHY`; it cannot manufacture `AVAILABLE`/`ACTIVE`. Reapplying or toggling a plan is deterministic and does not fabricate a generation. A successful probe can call exact-registration recovery to set that still-current binding `AVAILABLE`; dependency disappearance marks the old health unavailable and unregisters it, while successful reacquisition registers a new healthy binding and advances generation. A stale registration cannot recover or pass a `ProviderRegistrationGate`. Phase 3/4 canonical paths retain activation, exact generation and the same usable-health rule before consequential execution. Dormant integrations, including unrelated unhealthy ones, remain non-critical.

## Vault

Vault uses the public `Economy` service through an immutable binding containing the exact service instance, offline-player resolver, controlled server-thread scheduler and mutable health state. The binding is discarded on disable/rebind; no operation silently looks up or substitutes another economy service.

The three provider IDs are:

- `vault_economy_cost` — `CostProvider`, type `vault_economy`;
- `vault_economy_reward` — `RewardProvider`, type `vault_economy`;
- `vault_balance` — current `balance` `MetricProvider`.

Cost preflight first validates every definition and requires one player UUID, operation ID, configuration revision and sealed provider generation across the complete proposed batch. Mixed identity blocks before the scheduler and makes zero Vault calls. A coherent batch aggregates every proposal into one balance check and never calls withdraw. Reward preflight validates representability and never deposits. Values must be positive `CURRENCY_AMOUNT`, fit the economy's fractional-digit policy without rounding and round-trip exactly through Vault's `double` API. Non-finite values are rejected.

Execute applies the shared fail-closed health allowlist before calling even `Economy.isEnabled()`, then rechecks service enablement immediately before mutation. Only `AVAILABLE` and `ACTIVE` may use Vault; all six other states make zero provider calls. Failed service/response returns failure without internal success. A nominal success reporting an amount other than the requested amount is `UNCERTAIN`. Vault offers no MaddPrestige idempotency key, durable effect query or truthful compensation primitive, so both adapters declare `idempotent=false`, `reversible=false`, `reconcilable=false`, `externalUncertaintyPossible=true`. Canonical journal authority/generation checks provide the normal single execution; no blind retry or fabricated compensation is introduced.

## mcMMO

`OfficialMcMmoExperienceAccess` calls only `ExperienceAPI.isValidSkillType`, `getLevelOffline(UUID,String)` and `getPowerLevelOffline(UUID)`. `McMmoMetricProvider` exposes:

- `skill_level` (`INTEGER`, required `skill` dimension, current read);
- `power_level` (`INTEGER`, current read).

Both reads run through the controlled server-thread scheduler and carry the registry generation in their sample. These are externally owned absolute `CURRENT` observations that may decrease, so both descriptors use `MetricMonotonicity.NON_MONOTONIC` and `MetricResetPolicy.NOT_APPLICABLE`. Lower later skill/power samples remain valid rather than falsely triggering monotonic reset reconciliation. Invalid skill/read/filter or any health other than `AVAILABLE`/`ACTIVE` produces `UNAVAILABLE`; MaddPrestige invokes no mcMMO setter/reset/storage API.

`McMmoAdjustedXpListener` receives the official final XP gain event at monitor priority with cancelled events ignored. Its constructor requires a mcMMO `ProviderRegistrationGate`; production cannot substitute an arbitrary `() -> true`. The gate binds one exact token/generation and checks current registry identity, activation and usable health for every event before the finite positive adjusted raw value may increment the capability-authenticated manual `EXACT_DECIMAL` total. Old listeners reject on outage, config deactivation, unregister and rebind; only the current recovered registration resumes. The manual provider remains a separate monotonic accumulated metric, aggregates memory updates and flushes batches rather than writing SQL per event. Existing `SINCE_PRESTIGE_START` baseline logic snapshots/subtracts this total, so current-level XP rollover does not erase scoped progress.

## PlaceholderAPI

Output uses `MaddPrestigePlaceholderExpansion` with identifier `maddprestige`, `persist=true`, and the official `PlaceholderExpansion.onRequest` contract. Supported canonical snapshot keys are:

- `%maddprestige_stage%`;
- `%maddprestige_current_prestige%`;
- `%maddprestige_lifetime_prestige%`;
- `%maddprestige_requirement_status%`;
- explicitly published immutable extra keys.

The hot path receives only a bounded `MaddPrestigePlaceholderCache`. It cannot query a repository/provider or mutate state. Canonical runtime owners publish complete immutable snapshots outside rendering. A null player returns an empty string; an absent snapshot/unknown key remains unresolved. LRU eviction bounds player entries.

Input uses `OfficialPlaceholderResolver`/`PlaceholderAPI.setPlaceholders` only during an explicit controlled refresh. The actual scheduled task requires its exact current registration, active configuration and `AVAILABLE`/`ACTIVE` health before each resolver call and again before caching the result. Unhealthy-before-schedule, outage-after-schedule and stale-generation tasks therefore make zero resolver calls and do not replace prior cache state. If an outage begins during a resolver call, the returned value is discarded and remaining inputs are not resolved. A valid refresh parses each sample to its configured generic type and inserts it into the bounded LRU cache with observation time; recovery/new binding resumes normally. `MetricProvider.read` performs no PAPI call and returns unavailable for unsampled, unresolved, unparseable, stale, non-usable health, wrong-read or filtered input. Token-aware, case-insensitive configuration validation prevents this provider from calling the MaddPrestige output expansion recursively. PAPI data is an explicitly configured external metric and never becomes MaddPrestige's canonical internal state or mutates requirement state.

## EconomyShopGUI, QuickShop and PlayTimeManager

`EconomyShopGuiCompatibilityListener` consumes the official `PostTransactionEvent` only for bounded compatibility/successful-sale diagnostics. It has no `MetricProvider`, cost, reward or `ManualMetricHandle`, and `progressionCredits()` is always zero. Progression is intentionally deferred: `getPrice()` cannot identify every economy represented by multi-item sales, and Vault money, EXP, levels, item currencies, PlayerPoints and custom economy units cannot be summed truthfully. The default-off schema exposes a compatibility flag and a separate hard-false progression flag; enabling progression is an actionable compile error.

`QuickShopCompatibilityListener` consumes the official `ShopSuccessPurchaseEvent` only to count compatibility/self-transaction diagnostics. It has no `MetricProvider`, reward, cost or `ManualMetricHandle` and `progressionCredits()` is always zero. This is deliberate: event success cannot prove absence of self-trading, alts, circular money, wash trading, resale, duplication, rollback/refund or restart gaps. The compiler rejects any attempt to enable progression credit.

PlayTimeManager is deferred. Inspection of the real 3.6.5 artifact found no dedicated stable service/API that improves MaddPrestige's semantics; the accepted `VanillaStatisticsProvider` already exposes Paper's authoritative `PLAY_ONE_MINUTE` statistic. A duplicate PAPI-derived provider would be less authoritative and adds no accepted capability.

## Threading, outage and security

External APIs are treated as server-thread-confined. Vault, mcMMO and PAPI input calls occur only through `IntegrationTaskScheduler`; asynchronous repository work is not blocked onto that path. PAPI output is an immutable cache lookup. Shop compatibility event listeners perform only bounded in-memory diagnostics and cannot reach manual progression.

Focused tests cover strict root/nested YAML shapes, canonical Placeholder metric keys, token-aware case-insensitive recursion rejection, exact active-set shrink/expand/repeat/config reload, stable unchanged generations, the complete usable/unusable health matrix, outage-preserving config off→on, explicit recovery, unregister/rebind, stale gates, unrelated unhealthy dormant providers and compiled-config→plan→lifecycle→canonical-collector fail-closed behavior. Vault covers coherent/mixed batches, zero-mutation preflight, complete health gating before calls, exact one-call execution and external mismatch uncertainty. mcMMO covers decreasing current values, descriptor ownership, exact-registration event acceptance/rejection, recovery and cancellation. Placeholder covers healthy, pre-schedule outage, deferred-task outage, unavailable, stale/rebound and recovery refresh behavior plus deterministic cache retention. Existing 20,000 repeated output renders, zero-credit EconomyShopGUI events and QuickShop hard-zero observations remain. Real published artifact types/methods are compiled and reflected in contract tests.

The narrow security audit found no provider overwrite bypass (registry owner/token checks remain), reflection, process execution, command pseudo-API, plugin DB/file access, simulation mutation, fail-open missing dependency, external compensation overclaim or player-to-player credit path. External Vault uncertainty remains explicitly reconcilable evidence rather than false success.

## Acceptance mapping

| Acceptance | Status | Phase 5 evidence |
|---|---|---|
| A48 | Satisfied | Official mcMMO current reads accept decreases and advertise `NON_MONOTONIC`/`NOT_APPLICABLE` |
| A49 | Satisfied | Final XP event mutation requires exact current active healthy registration; stale/outage/config-off paths reject |
| A50 | Satisfied | Activation/health separation, full outage toggle matrix, canonical-consumer block, explicit recovery and rebind generations |
| A51 | Satisfied | Complete Vault health allowlist blocks before provider calls; coherent/mixed preflight and uncertainty proofs retained |
| A52 | Satisfied | Official persistent expansion; immutable bounded cache and repeated-render proof |
| A53 | Satisfied | Scheduled-task exact-registration/health gate, outage race/stale generation zero-call proof, deterministic cache and recursion proof |
| A54 | Satisfied | Real QuickShop success contract; no progression provider/handle; compiler rejection and zero-credit cycling proof |

## Verification

The inherited baseline before implementation was 214 tests in 54 suites with zero failures/errors/skips. Checkstyle, Enforcer, dependency convergence, seven JaCoCo XML reports and aggregate CycloneDX passed. Correction pass 2 expands the focused integration module to 53 tests, with the Paper boundary still at 8 tests. The complete Phase 5 delta is 49 tests (46 integration and 3 Paper PlaceholderAPI), including 12 correction-pass-2 regressions.

Two final full-reactor `clean verify` runs each pass 263 tests in 61 suites with zero failures/errors/skips. Checkstyle has seven zero-violation reports, Enforcer/dependency convergence pass, seven JaCoCo XML reports are generated, and aggregate CycloneDX 1.6 contains 67 components and all five Phase 5 API coordinates. The optional API package count in the shaded distribution is zero. The distribution hash is reproducibly `E8F91C7DA78000849D5CD291108680ACCA1B281D948858FA9A10882393D612DA`; the aggregate SBOM hash remains reproducibly `9A0CD953432F5682AE723B2ABA954A93B4D4B6DA0197E1742E5A89F20496FFEE`. Closing audit totals are recorded in `STATUS.md` and `PHASE5_THIRD_OWNER_REVIEW_SUMMARY.txt`.
