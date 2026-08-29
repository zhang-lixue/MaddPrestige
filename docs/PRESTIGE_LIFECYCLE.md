# Numeric Prestige lifecycle

The authoritative V2 state is a durable non-negative integer. A successful confirmed operation changes Prestige
`P → P + 1`. `maximum` may be a configured integer or `unlimited`; there is no prerequisite rank, stage, LuckPerms
group, or world lifecycle event.

The active revision selects one optional requirement tree, independent cost IDs, every-Prestige reward IDs, optional
milestones, cooldown, maximum, segmented scaling maps, and reset/preserve behavior for MaddPrestige-owned scoped data.
Provider-owned current values are read during authorization and preflight. Check-only requirements consume nothing.

PRE occurs after authorization but before durable initialization or journaling; cancellation/listener failure is
zero-effect. Exact configuration, provider generations, and Prestige state are revalidated. Internal commit uses a
journaled SQLite transaction and compare-and-set; uncertain external effects remain explicitly reconcilable. POST
observes a durable terminal outcome. Restart reloads the acknowledged numeric level and never derives it from worlds
or external plugins.

LuckPerms is optional. Configured permission/group rewards are additive; missing groups fail without creation. See
[the Phase 9B policy](V2_PHASE9B_NUMERIC_PRESTIGE_POLICY.md) for configuration and compatibility details.
