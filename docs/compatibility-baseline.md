# Compatibility baseline

This document states what MaddPrestige 2.0.0-rc.1 was actually qualified against. It does not imply support for
untested later releases.

## Platform

| Component | Qualified baseline |
|---|---|
| Java | 25 |
| Paper | 26.1.2 build 74 |
| Persistence | Embedded SQLite, one Paper process and one server directory |
| LuckPerms | 5.5.71 when referenced by active configuration |
| PlaceholderAPI | 2.12.3 |

Optional integrations are capability- and configuration-driven. An installed plugin is not used unless the active
configuration references a capability that MaddPrestige can bind safely. Provider absence, generation change, or
unhealthy state fails closed.

See [Providers and integrations](integrations.md) and the
[provider capability matrix](PROVIDER_CAPABILITY_MATRIX.md).

## Persistence and upgrades

SQLite is the sole production persistence backend supported by 2.0. MySQL/MariaDB CI jobs exercise a retained
contract foundation only; they are not a production-support or multi-server claim. Shared databases, proxy
synchronization, and multi-process progression are unsupported.

The forward migration chain validates every historical prefix through the current schema. Migration 12 is the
numeric-Prestige activation boundary: it preserves auditable/provider-owned data, archives stage-era authority, and
initializes numeric Prestige safely without inferring a stage-to-Prestige mapping. A required migration proceeds only
after coordinated backup, integrity/history/configuration validation, and isolated restore rehearsal.

V1 player/configuration data is not automatically imported. A fresh V2 player starts at Prestige 0 unless an audited
V2 administrative action establishes another value.

See [Upgrading](operations/upgrading.md) and [Recovery](operations/recovery.md).
## Pre-GA identifier normalization

Before the 2.0 general-availability release, active provider IDs, locale keys, diagnostic codes, and provider metadata
were normalized to purpose-based names. New configuration, discovery, reports, and bundled messages emit only those
canonical names. Exact legacy aliases are accepted only at documented compatibility boundaries; arbitrary prefix
rewriting is not supported.

Historical migration versions 1 through 12 retain their original persisted descriptions, SQL, and checksums. Current
operational displays use a separate description catalog so wording changes cannot alter migration identity. The same
separation is required for future migrations.

## Stable extension surface

The owner-frozen `2.x-stable-1` baseline covers the accepted stable Bukkit-free Java SDK types and stable Paper event
types. Existing stable source/binary contracts remain available even where stage/rank compatibility operations now
fail closed because numeric Prestige is the sole active progression model. Experimental types are not part of that
guarantee.

Public consumers should depend on `net.maddkraft:maddprestige-api:2.0.0-rc.2` and follow the
[API guide](api.md). Paper listeners should use the documented [event contracts](events.md).

## Ownership guarantees

- MaddPrestige never creates LuckPerms groups.
- Only explicitly configured, exact direct memberships inside the managed set may be changed.
- Contextual, temporary, staff, supporter, event, and unrelated memberships are preserved.
- Provider-owned balances, skill levels, claims, and other state remain external.
- Placeholder output is cache-backed; configured Placeholder input is a separately sampled typed provider.

The archived V1 public documentation and frozen characterization fixtures are available under
[`docs/archive/v1`](archive/v1/README.md) and [`baseline/v1`](../baseline/v1/README.md).
