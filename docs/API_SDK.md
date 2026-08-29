# MaddPrestige V2 API and provider SDK

MaddPrestige `2.0.0-rc.1` records owner-frozen compatibility baseline `2.x-stable-1`: 46 Bukkit-free Stable SDK types
and six Stable Paper event types. This is the accepted final Phase 8 freeze. Use only documented Stable types and
compile against the exact candidate API JAR. Stable changes are
blocked mechanically and require backwards-compatible extension review or a later major-version baseline.

The Maven coordinate is `net.maddkraft:maddprestige-api:2.0.0-rc.1`. Experimental, Internal/Should Not Be Public,
and Legacy/Pending Removal types are excluded from the 2.x compatibility guarantee.

## Reading and mutating progression

After startup migration, recovery, provider composition, and active-configuration validation complete, Paper publishes
`net.maddkraft.maddprestige.api.service.MaddPrestigeService` through `ServicesManager`. It is absent while bootstrap is
blocked and unregisters before shutdown rejects new work.

The service exposes immutable structured reads for numeric player progress, requirement progress, currencies, active
season, providers, and retained compatibility stage/rank surfaces. `prestige(UUID)` enters the numeric operation
engine. Production stage catalogs are empty and `rankUp(UUID)` returns a compatibility-only blocker. Those signatures
remain to preserve `2.x-stable-1`; they are not an alternate progression model. Potential I/O is asynchronous.
Operational failures use `ServiceResult`/stable machine codes rather than exceptional localized prose.

The caller request/correlation UUID is not a durable operation UUID. A request may be blocked before a journal identity
exists. Do not synthesize identities, inspect SQLite, or use internal recovery markers.

## External metric providers

Implement `ProviderDeclaration` and register it with Paper `ServicesManager`. Metadata discovery and requirement reads
run off the Paper thread with deadline/cancellation context. Return exactly one compatible result for every query;
represent operational failure with `ProviderMetricResult.unavailable`. The owner namespace is attested from the actual
providing plugin, and MaddPrestige supplies the generation-specific `ProviderRegistrationHandle`.

The complete compilable example is [`examples/provider-sdk`](../examples/provider-sdk). It imports only public API and
Paper types, exposes one generic count metric, reports unavailable on shutdown/deadline/cancellation, and unregisters
cleanly. Phase 8E separately qualified independent Alpha/Beta providers and dependency/fault isolation; the example remains
minimal documentation rather than that qualification harness.

No separate external nullness-annotation dependency is required for this candidate. Javadocs, explicit `Optional`,
immutable return contracts, and runtime argument validation are authoritative. Paper-specific JetBrains annotations may
appear only where Paper conventions require them.
