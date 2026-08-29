# Providers and integrations

Providers advertise typed metrics or effects with stable IDs, health, provenance, value types, supported operators,
read modes, dimensions, and reset behavior. An active configuration pins the exact validated provider generation.
Missing, unhealthy, stale, timed-out, or malformed data fails closed only where referenced.

Built-in Paper statistics and LuckPerms are sufficient for the generic Quick Start. Vault, mcMMO, PlaceholderAPI,
CraftEngine, GriefPrevention, WorldGuard, shops, crates, currencies, and external SDK providers are not required by that
profile. PAPI output is cache-only; configured PAPI input is sampled and typed. Optional providers may have truthful
offline limitations—run Doctor and Why rather than assuming an offline value is available.

MaddPrestige never creates LuckPerms groups. A missing configured group blocks preview/activation and Doctor names the
stage/group remediation. One optional adapter failure must not stop unrelated dormant features, but an operation that
depends on it remains blocked.

External plugins register the Stable `ProviderDeclaration` service through Paper `ServicesManager`; MaddPrestige
attests the owning plugin and assigns the namespace/generation. See the compilable
[`examples/provider-sdk`](../examples/provider-sdk). That example is API documentation; the accepted Phase 8E evidence separately proves the independent multi-provider and
dependency/fault matrix.
