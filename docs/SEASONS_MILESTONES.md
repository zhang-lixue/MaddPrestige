# Seasons, milestones, and competition boundary

Seasons provide a stable active season identity that requirement scopes and lifecycle policies may reference.
Milestones are optional durable triggers at administrator-selected numeric Prestige values and may reference any
configured reward. There are no intrinsic milestone numbers. Reset/archive behavior must be explicit; it is never
inferred from a display name, a stage, or a world reset.

The generic profile uses `milestones: {}`, `seasons: {}`, and `competition.enabled: false`. A first setup therefore has
no hidden calendar, contest, or reward requirement.

Competition is an optional generic boundary, not a mandatory progression system. MaddKraft-specific contest, Court,
PvP, resource-world, or gameplay ownership is not part of the V2 generic core. External plugins can contribute typed
providers without transferring their lifecycle/state ownership to MaddPrestige.
