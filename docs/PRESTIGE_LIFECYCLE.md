# Prestige lifecycle

Prestige eligibility is explicit: enabled state, allowed source stages, reset stage, increments, maximum, cooldown,
costs/rewards, and reset policies compile with the same active revision.

The generic profile permits Prestige only from `veteran`. Success increments current and lifetime Prestige exactly once,
sets the stage to `member`, projects LuckPerms Member, and resets MaddPrestige-owned active requirement/baseline state.
It preserves Paper's lifetime statistic and unrelated player/server state.

The operation uses prepare/confirm semantics. PRE events occur after authorization but before durable initialization or
journal insertion; cancellation and listener failure are zero-effect. Authority is revalidated before journaling. POST
follows a durable terminal state. Interrupted external effects are reported as uncertain/reconcilable rather than
silently replayed.

After restart, run `/maddprestige player` and `/maddprestige doctor`. A repeated Prestige request from the reset baseline
stage is blocked and cannot add another count/history row.
