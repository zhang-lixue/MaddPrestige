# Stages and external ranks

A stage has a stable lowercase ID, display name, enabled state, ordered position, optional requirement tree, and
optional projection. The ID is durable authority; renaming display text or an external rank does not silently rewrite
player state.

LuckPerms projections consume existing groups. MaddPrestige never creates, deletes, inherits, or formats them. Preview
checks every configured target and blocks when LuckPerms is absent/unhealthy or a group is missing. During a successful
transition, MaddPrestige keeps exactly the intended permanent, context-free membership inside the configured managed
stage set and preserves unrelated permissions/groups.

The generic example maps `member`, `adventurer`, and `veteran` to external `Member`, `Adventurer`, and `Veteran`.
`member` is the baseline. Removing or disabling a referenced stage requires a previewed explicit remap; reservations
prevent concurrent rank/Prestige writes through an unsafe transition.

Offline UUID operations use the same asynchronous LuckPerms authority. Some optional providers may require an online
player or live server state; their unavailable result blocks only operations that reference them. Phase 8D exercises
one offline path but does not claim the complete Phase 8E provider/offline matrix.
