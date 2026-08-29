# Stage and rank compatibility

Numeric Prestige has no intrinsic stage/rank ladder. It does not require a final group, project a reset group, acquire
a stage-transition lease, or write stage history. Production stage catalog reads are empty and rank-up execution is
blocked as compatibility-only; players advance through `/maddprestige prestige`.

Stable stage/rank API types, events, configuration parsing, and historical persistence remain so Phase 8 public
contracts and recovery evidence are not broken. They are not current V2 Prestige authority. The historical
`member-adventurer-veteran` example is correspondingly non-authoritative; use `examples/numeric-prestige`.

LuckPerms remains an optional additive reward provider. A configured permission or existing group is granted without
creating groups or changing hierarchy, weights, prefixes, inheritance, or unrelated player nodes. Missing configured
groups/providers fail closed.
