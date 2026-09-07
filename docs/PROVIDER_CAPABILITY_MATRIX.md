# V2 provider capability matrix

Capabilities below are the production adapters actually advertised by this repository. Installed plugin presence is
not capability.

| Provider ID / integration | Requirement read | Cost mutation | Reward mutation | Condition | Coexistence only / notes |
|---|---|---|---|---|---|
| `vault_balance` | balance | — | — | — | Reads the active Vault economy. |
| `vault_economy_cost` | — | exact withdraw | — | — | Aggregate preflight and provider-generation fencing. |
| `vault_economy_reward` | — | — | exact deposit | — | Additive reward. |
| `mcmmo` | `skill_level`, canonical `total_level`; deprecated `power_level` input | — | — | — | Live online profile read; provider-owned values are never mirrored. |
| `luckperms` | — | — | permission or existing-group grant | — | Additive only; creates no group and preserves unrelated nodes/parents. Rank adapter is compatibility-only. |
| PlaceholderAPI output | — | — | — | — | Cached, read-only display expansion; 2.12.2 and 2.12.3 are accepted. |
| `placeholder_input` | administrator-configured typed placeholders | — | — | typed comparison | Explicit input only; recursion and type checks fail closed. |
| `griefprevention_claims` | remaining/accrued/bonus claim blocks, owned claim count | — | — | — | Provider-owned claim state. |
| `griefprevention_claim_blocks_reward` | — | — | additive bonus claim blocks | — | Bound by configured maximum and recovery policy. |
| `worldguard_region` | `inside_region` | — | — | world/region membership | No region mutation. |
| `craftengine_item_count` | item count | — | — | — | Inventory-backed typed item read. |
| `craftengine_item_reward` | — | — | bounded item grant | — | Additive reward. |
| `paper_statistics` | typed vanilla statistics | — | — | — | Includes play-time guidance. |
| `paper_world_context` | world name/UUID/environment/in-set | — | — | world context | Read-only; not a world-reset listener. |
| `event_progress` | durable explicitly configured event counters | — | — | — | Includes adjusted mcMMO XP event metric; not `total_level`. |
| Stable external `ProviderDeclaration` | as declared and attested | as declared | as declared | as declared | Owner plugin, namespace, generation, health, and capabilities are validated. |
| EconomyShopGUI / QuickShop-Hikari | — | — | — | — | Coexistence/diagnostics only; zero progression credit. |
| UltimateMobCoins / other MobCoins plugin | — | — | — | — | No native MaddPrestige balance/cost/reward adapter. Use only a real configured PAPI/external SDK capability. |
| Other MaddKraft plugins | — | — | — | — | Coexistence only unless a provider declaration is actually registered. |

Every referenced capability is pinned to the validated provider generation. Missing, inactive, stale, unhealthy,
timed-out, malformed, or type-incompatible data blocks the dependent operation without fabricating a value.
