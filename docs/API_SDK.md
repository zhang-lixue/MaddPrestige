# MaddPrestige V2 API and provider SDK

The Phase 8 owner accepts the current Bukkit-free Stable SDK candidate and six-type Paper Stable event candidate. They
are approved candidates, not yet the final frozen compatibility baseline; that freeze remains Phase 8 release-hardening
work. Use only documented Stable types and compile against the exact candidate API JAR.

## Reading and mutating progression

After startup migration, recovery, provider composition, and active-configuration validation complete, Paper publishes
`net.maddkraft.maddprestige.api.service.MaddPrestigeService` through `ServicesManager`. It is absent while bootstrap is
blocked and unregisters before shutdown rejects new work.

The service exposes immutable structured reads for player progress, stage catalog, rank-up/Prestige evaluation,
requirement progress, currencies, active season, and providers. `rankUp(UUID)` and `prestige(UUID)` enter the canonical
operation engine. Potential I/O is asynchronous. Operational failures use `ServiceResult`/stable machine codes rather
than exceptional localized prose.

The caller request/correlation UUID is not a durable operation UUID. A request may be blocked before a journal identity
exists. Do not synthesize identities, inspect SQLite, or use internal recovery markers.

## External metric providers

Implement `ProviderDeclaration` and register it with Paper `ServicesManager`. Metadata discovery and requirement reads
run off the Paper thread with deadline/cancellation context. Return exactly one compatible result for every query;
represent operational failure with `ProviderMetricResult.unavailable`. The owner namespace is attested from the actual
providing plugin, and MaddPrestige supplies the generation-specific `ProviderRegistrationHandle`.

The complete compilable example is [`examples/provider-sdk`](../examples/provider-sdk). It imports only public API and
Paper types, exposes one generic count metric, reports unavailable on shutdown/deadline/cancellation, and unregisters
cleanly. Its existence does not complete the broader Phase 8E provider/dependency qualification matrix.

No separate external nullness-annotation dependency is required for this candidate. Javadocs, explicit `Optional`,
immutable return contracts, and runtime argument validation are authoritative. Paper-specific JetBrains annotations may
appear only where Paper conventions require them.
