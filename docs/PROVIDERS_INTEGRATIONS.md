# Providers and integrations

Providers advertise typed metrics or effects with stable IDs, health, provenance, value types, supported operators,
read modes, dimensions, and reset behavior. An active configuration pins the exact validated provider generation.
Missing, unhealthy, stale, timed-out, or malformed data fails closed only where referenced.

No optional provider, including LuckPerms, is required by the stage-free numeric example. Vault, mcMMO, PlaceholderAPI,
CraftEngine, GriefPrevention, WorldGuard, shops, crates, currencies, and external SDK providers are not required by that
profile. PAPI output is cache-only; configured PAPI input is sampled and typed. Optional providers may have truthful
offline limitations—run Doctor and Why rather than assuming an offline value is available.

MaddPrestige never creates LuckPerms groups. A missing configured reward group blocks the affected operation and
Doctor names the exact remediation. Permission/group rewards add only their configured node and preserve unrelated
permissions, groups, hierarchy, weights, prefixes, and inheritance. One optional adapter failure must not stop
unrelated dormant features, but an operation that depends on it remains blocked. mcMMO `total_level` is the canonical
guided Prestige metric; external current values remain provider-owned and are queried rather than mirrored.

External plugins register the Stable `ProviderDeclaration` service through Paper `ServicesManager`; MaddPrestige
attests the owning plugin and assigns the namespace/generation. See the compilable
[`examples/provider-sdk`](../examples/provider-sdk). That example is API documentation; the accepted Phase 8E evidence separately proves the independent multi-provider and
dependency/fault matrix.
