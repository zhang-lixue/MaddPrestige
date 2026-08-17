# MaddPrestige V2 Phase 7 implementation map

## Frozen boundary

Phase 7 keeps the generic API, engine and persistence contracts unchanged. It activates the V2 Paper entry and composes every accepted Phase 5 runtime capability alongside the Phase 7 native providers. Optional vendor code remains behind post-discovery bootstraps; absence, service loss, incompatible linkage or exact-version mismatch is capability-local. Provider activation, health and registry generation are independent fail-closed authorities.

The live `reload` command is deliberately non-mutating. It does not read files, compile candidates, reconcile providers or swap runtime state. It refuses the shortcut and identifies the canonical Phase 6 draft/preview/acknowledgement/apply/history/remap route. This preserves stage-transition reservations, active pointer/history/runtime coherence and A69 concurrency semantics.

## Module ownership

| Module | Phase 7 ownership |
|---|---|
| `maddprestige-api` | No production change. Existing typed metric, cost, reward, health, activation, generation, validation and result contracts are sufficient. |
| `maddprestige-core` | No plugin API dependency. Existing planning, authorization, canonical configuration/setup, journal, recovery, command, GUI and registry services remain authoritative. |
| `maddprestige-persistence` | Existing configuration history, stage reservations, recovery, journals and player state remain authoritative. No persistence-module migration was added. |
| `maddprestige-integrations` | Accepted Vault, mcMMO, PlaceholderAPI and shop compatibility implementations plus isolated GP/WG/CE public-API adapters and strict integration configuration. |
| `maddprestige-platform-paper` | Production composition, durable mcMMO manual-event store, server-thread boundary, output cache, optional discovery/service/lifecycle ownership and built-in Paper providers. |
| `maddprestige-testkit` | Exact A71/A72 persistence/lifecycle and complete A75 fake-provider evidence. |
| `maddprestige-distribution` | First-party V2 modules only. Third-party APIs are provided and never redistributed. |

## Production startup and shutdown

Startup prepares missing defaults, opens SQLite and runs backup-first migrations, compiles the canonical configuration family, creates the provider registry and built-in providers, creates the durable Phase 5 manual-event source, discovers and composes enabled optional capabilities, recovers pending operations, then binds minimal administration and reports ready. A fatal core failure tears down completed stages in reverse. Shutdown stops scheduled input work, output expansion, vendor listeners and optional providers before built-ins and persistence flush/close.

Exact supported current identities are Vault 2.20.2, mcMMO 2.2.053, PlaceholderAPI 2.12.3, EconomyShopGUI 7.2.0, QuickShop-Hikari 6.2.0.11, GriefPrevention 16.18.7, WorldGuard `7.0.18+2392-fa605e6` and CraftEngine 26.7.4.

## Phase 5 live composition

| Configuration | Live production result |
|---|---|
| `vault.enabled` | Accepted balance metric, cost and reward providers bind to the exact current Vault Economy service; service unregister/register invalidates/rebinds authority. |
| `mcmmo.enabled` | Accepted current metrics plus authenticated adjusted-XP event listener bind; durable mutation is guarded by current healthy registry generation. |
| configured PAPI inputs | Accepted scheduled typed input provider binds, caches bounded typed/fresh observations and never calls PAPI on the requirement-read path. |
| PAPI output enabled | Accepted persistent expansion registers and reads immutable bounded cache snapshots only. |
| EconomyShopGUI compatibility | Exact version listener binds for diagnostics only; progression credit is structurally zero. |
| QuickShop compatibility | Exact version listener binds for diagnostics only; progression credit is structurally zero. |

AdvancedCrates uses `%advancedcrates_virtual_keys_total%` as a typed COUNT input when configured. UltimateMobCoins uses `%ultimatemobcoins_balance%` as a distinct EXACT_DECIMAL input. Their reviewed generic command rewards remain external, non-idempotent and uncertain; no undocumented native API is invented.

## Independent Phase 7 dispositions

| Surface | Requirement | Cost | Reward | Cosmetic/collectible reward |
|---|---|---|---|---|
| GriefPrevention 16.18.7 | Native current remaining/accrued/bonus/owned-claim reads | Unavailable | Native bonus claim block grant; uncertain after possible mutation | Not applicable |
| WorldGuard deployed `7.0.18+2392-fa605e6` | Native exact read-only configured-region predicate | Not applicable | Not applicable | Not applicable |
| Paper world context | Generic read-only world name/UUID/environment/configured-set predicates | Not applicable | Not applicable | Not applicable |
| CraftEngine 26.7.4 | Native exact storage-only custom-item count | Consumable turn-in unavailable | Native exact item reward with capacity preflight and post-verification | Item-backed collectible uses native reward; permanent non-item entitlement unavailable |
| AdvancedCrates | Typed PAPI fallback | Unavailable | Reviewed generic command fallback | Native crate-open/collectible surface unavailable |
| UltimateMobCoins | Typed PAPI fallback, distinct from Vault | Native debit unavailable | Reviewed generic command fallback | Not applicable |
| DiscordSRV | Not applicable | Not applicable | Optional reviewed command output only | Not applicable |
| AxPlayerWarps | Unavailable | Unavailable | Unavailable | Unavailable |
| AxSellWands / MaddMobCoins / MaddRTP / resource-world systems | Coexistence only | Coexistence only | Coexistence only | Coexistence only |
| Court metric | Fake generic A75 provider only | Not applicable | Not applicable | Not applicable |

## CraftEngine lifecycle and identity

`CraftEngineRegistryLifecycle` has four states: `ABSENT`, `WAITING_FOR_REGISTRY`, `AVAILABLE`, `DISABLED_OR_UNAVAILABLE`. Discovery/enable installs observation but does not infer readiness. Initial startup alone may use the bounded readiness probe, which requires a non-empty public `CraftEngineItems.loadedItems()` result. Empty/transient registry state remains waiting. The public completed-reload event is authoritative on startup and after re-enable. Re-enable returns to waiting without the startup probe.

Every completed reload re-resolves configured IDs, unregisters old bindings and binds new generations. A removed ID becomes unavailable/invalid; a changed same-ID definition is rebuilt; old sealed authority cannot execute. No definition, built stack or registry map crosses reload.

Exact identity uses only the public CraftEngine key. Item reads scan online-player storage contents only. Reward validation requires positive integral bounded COUNT, builds legal stacks on the server thread, exact-verifies identity/amount, simulates capacity without writes and excludes lookalike stacks from merge capacity. Inventory is checked again before one insertion attempt. Leftovers or exceptions after possible mutation are uncertain and never replayed or dropped.

## A71–A75 hard gates

- A71 exercises every transition in the exact six-stage pre-existing LuckPerms ladder, Prestiges/resets to `wanderer`, persists/reopens SQLite and reconciles without group/hierarchy creation.
- A72 preserves `mad_hatter`, unrelated permanent group, unrelated permission and temporary/contextual membership through ordinary progression, Prestige/reset and reopened reconciliation. Only permanent context-free membership in the exact managed set may change.
- A73 includes complete 47-JAR metadata/disposition, Paper-only/full/GP-absent/CE-absent/WG-absent/WE-absent runs, live Phase 5/7 bindings and real CraftEngine read/reward/capacity/forgery/reload/lifecycle/no-replay evidence.
- A74 source/bytecode evidence proves no world lifecycle or WorldGuard mutation ownership.
- A75 proves unconfigured/absent/unhealthy/removed failure, healthy evaluation, stale rejection and new authority after rebind using only a fake generic provider. Production has no Court code.

## Generic contract decision

`GENERIC INTERFACE EXTENSION REQUIRED: NO`. Current provider, metric, cost/reward, health, generation, validation, uncertainty, canonical configuration, journal and recovery contracts express the full Phase 7 result without vendor concepts in API/core/persistence.
