# V2 commands and permissions

The ordinary player command is `/prestige`, which silently opens the Player GUI and uses `maddprestige.use`.
`/maddprestige` remains the advanced and administrative namespace; its compatibility aliases are `/mprestige` and
`/mp`. Arguments shown in angle brackets are required and square brackets are optional.

## Player and observation commands

| Command | Permission | Purpose |
|---|---|---|
| `/prestige` | `maddprestige.use` | Open the canonical Player GUI |
| `/maddprestige help [topic]` | `maddprestige.use` as applicable | Schema/provider help |
| `/maddprestige status` | `maddprestige.use` | Active revision status |
| `/maddprestige player [player-uuid]` | self; `maddprestige.admin.players.view` for staff | Canonical progress preview |
| `/maddprestige prestige` | `maddprestige.prestige` | Prepare a Prestige confirmation |
| `/maddprestige confirm [confirmation-id]` | `maddprestige.prestige` | Execute an owned valid preview; no-ID form requires exactly one |
| `/maddprestige why prestige [player-uuid] [details]` | operation permission; staff view for another UUID | Exact blockers; optional requirement/revision provenance |
| `/maddprestige simulate prestige [player-uuid] [details]` | `maddprestige.admin.simulate` for staff target | Zero-effect preview; optional full consequences/provenance |
| `/maddprestige gui` | `maddprestige.use`; staff UI needs `maddprestige.admin.gui` | Server-owned player/staff view |

## Administration commands

| Command family | Permission |
|---|---|
| `/maddprestige doctor [details]` | `maddprestige.admin.doctor` |
| `/maddprestige setup ...` | `maddprestige.admin.setup` |
| `/maddprestige setup preview [session-id] [details]` | `maddprestige.admin.setup` |
| `/maddprestige config get\|list\|search\|explain\|history ...` | `maddprestige.admin.config.view` |
| `/maddprestige config draft\|set\|add\|remove\|segment-add\|segment-edit\|segment-remove\|validate\|diff\|cancel ...` | `maddprestige.admin.config.edit` |
| `/maddprestige config apply\|acknowledge\|confirm ...` | `maddprestige.admin.config.apply` |
| `/maddprestige config rollback\|rollback-apply ...` | `maddprestige.admin.config.rollback` |
| `/maddprestige locale reload` | `maddprestige.admin.locale.reload` |
| `/maddprestige staff prestige set ...` | `maddprestige.admin.players.prestige` |
| Staff GUI player inspection | `maddprestige.admin.players.view` |
| Staff GUI Set Prestige | `maddprestige.admin.players.prestige.set` |
| Staff GUI Reset Prestige | `maddprestige.admin.players.prestige.reset` |

`maddprestige.admin` grants every listed V2 administrative child and defaults to operator. `maddprestige.use`,
`maddprestige.prestige` defaults true. The retained `maddprestige.rankup` compatibility node does not expose an active
command or confirmation. `maddprestige.admin.execute` is the separate permission
for consequential GUI execution. A view-only subject cannot obtain or consume apply authority.
The Staff GUI exposes Set and Reset separately, and each action requires its narrow permission in addition to player
inspection authority. The legacy `maddprestige.admin.players.prestige` parent delegates both narrow permissions.

Use tab completion and `/maddprestige help`. Stable result/error codes in logs and API results are intentionally not
localized even when rendered prose is.

Normal player and administrator preview includes the effective Prestige transition, requirements, costs, rewards, and
blockers. `details` expands Doctor, Why, setup preview, and simulation with provenance. `config get` returns one
effective value with `CONFIGURED`, `INHERITED_DEFAULT`, or `NOT_SET`; `config explain` adds provenance and schema
metadata. `config validate` is concise and `config diff` is the full draft diagnostic view.

Numeric Prestige is the only active progression operation. A caller that invokes the old typed `rankup` form receives
an explicit compatibility-only blocker, but ordinary usage/help/completion does not advertise it. No stage-management,
reset-stage, or progression-group command is active.
