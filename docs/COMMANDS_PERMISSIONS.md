# V2 commands and permissions

The registered command is `/maddprestige`; aliases are `/mprestige` and `/mp`. Arguments shown in angle brackets are
required and square brackets are optional.

## Player and observation commands

| Command | Permission | Purpose |
|---|---|---|
| `/maddprestige help [topic]` | `maddprestige.use` as applicable | Schema/provider help |
| `/maddprestige status` | `maddprestige.use` | Active revision status |
| `/maddprestige player [player-uuid]` | self; `maddprestige.admin.players.view` for staff | Canonical progress preview |
| `/maddprestige rankup` | `maddprestige.rankup` | Retained compatibility command; production execution is blocked |
| `/maddprestige prestige` | `maddprestige.prestige` | Prepare a Prestige confirmation |
| `/maddprestige confirm <confirmation-id>` | matching operation permission | Execute the exact fresh preview |
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
| `/maddprestige config draft\|set\|add\|remove\|remap\|unmap\|validate\|diff\|cancel ...` | `maddprestige.admin.config.edit` |
| `/maddprestige config apply\|acknowledge\|confirm ...` | `maddprestige.admin.config.apply` |
| `/maddprestige config rollback\|rollback-apply ...` | `maddprestige.admin.config.rollback` |
| `/maddprestige locale reload` | `maddprestige.admin.locale.reload` |
| `/maddprestige staff prestige set ...` | `maddprestige.admin.players.prestige` |

`maddprestige.admin` grants every listed V2 administrative child and defaults to operator. `maddprestige.use`,
`maddprestige.rankup`, and `maddprestige.prestige` default true. `maddprestige.admin.execute` is the separate permission
for consequential GUI execution. A view-only subject cannot obtain or consume apply authority.

Use tab completion and `/maddprestige help`. Stable result/error codes in logs and API results are intentionally not
localized even when rendered prose is.

Normal player and administrator preview includes the effective Prestige transition, requirements, costs, rewards, and
blockers. `details` expands Doctor, Why, setup preview, and simulation with provenance. `config get` returns one
effective value with `CONFIGURED`, `INHERITED_DEFAULT`, or `NOT_SET`; `config explain` adds provenance and schema
metadata. `config validate` is concise and `config diff` is the full draft diagnostic view.

Numeric Prestige is the only active progression operation. Rank-up options remain in stable command/help surfaces for
compatibility and return an explicit compatibility-only blocker; they do not mutate stage, rank, or Prestige state.
