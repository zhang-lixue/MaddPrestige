# Seasons, milestones, and competition boundary

Seasons provide a stable active season identity that requirement scopes and lifecycle policies may reference.
Milestones are durable first-crossing records tied to canonical player/configuration authority. Reset/archive behavior
must be explicit; it is never inferred from a display name.

The generic profile uses `milestones: {}`, `seasons: {}`, and `competition.enabled: false`. A first setup therefore has
no hidden calendar, contest, or reward requirement.

Competition is an optional generic boundary, not a mandatory progression system. MaddKraft-specific contest, Court,
PvP, resource-world, or gameplay ownership is not part of the V2 generic core. External plugins can contribute typed
providers without transferring their lifecycle/state ownership to MaddPrestige.
