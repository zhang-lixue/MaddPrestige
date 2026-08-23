# Stable Paper events

The Phase 8 candidate exposes six synchronous Paper events:

- `PreRankUpEvent` — cancellable;
- `PostRankUpEvent`;
- `PrePrestigeEvent` — cancellable;
- `PostPrestigeEvent`;
- `ConfigAppliedEvent`;
- `ProviderHealthChangedEvent`.

Rank/Prestige PRE fires on the Paper server thread after canonical authorization and before unknown-player
materialization, leases, journaling, or effects. Cancellation or listener failure is zero-effect. MaddPrestige then
revalidates the exact player/config/provider authority before durability. POST fires after a durable terminal state and
before the caller's completion stage resolves; listener failure cannot rewrite that result.

The immutable operation snapshot carries one request UUID through PRE, POST, and service result. PRE has no durable
operation ID; POST has one where journaling occurred. These IDs must not be conflated. Recursive same-player mutation is
rejected as a conflict rather than deadlocking or nesting effects.

Configuration events follow successful publication. Provider-health events describe already-materialized registry
state; listeners must not block, call private implementation types, or treat events as recovery/manual-progress
authority. The six event classes are Paper-bound while their snapshot/value types come from the Bukkit-free API.
