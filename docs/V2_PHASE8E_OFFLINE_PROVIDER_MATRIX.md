# MaddPrestige V2 Phase 8E offline provider matrix

**Qualified:** 2026-08-23
**Result:** PASS for A61
**Evidence boundary:** real Paper Phase 8D offline-LuckPerms qualification, Phase 8E two-boot performance run,
Phase 8E 48-assertion fault run, 15-assertion optional-absence boot, focused provider tests and public provider contracts.

`Supported` means the provider accepts a UUID/offline storage authority. `Explicitly limited` means the provider
returns unavailable/failed before a relevant effect and blocks only an operation that references it. No limited row
substitutes zero or fabricates progress.

| Provider/capability | Offline read | Offline mutation | Required live state | Qualified behavior | Result |
|---|---|---|---|---|---|
| Internal stage/Prestige/season state | Supported | Transactional SQLite | None | Random offline UUID operations, exact durable state and unchanged restart passed | PASS |
| Internal currency/cost/reward | Supported | Journaled SQLite | None | Existing deterministic action tests retain exact no-effect/verified/uncertain semantics | PASS |
| LuckPerms rank projection 5.5.71 | Supported by async UUID load | Supported by async save | LuckPerms service, not an online player | Accepted Phase 8D fresh-Paper run projected an offline UUID, verified membership and recovered it on restart | PASS |
| Paper statistics | Supported where Paper exposes an `OfflinePlayer` statistic | Read-only | Paper statistic storage | Phase 8D retained the Paper-owned statistic across offline operation/restart; unavailable sources return unavailable, not zero | PASS |
| Paper world context | Explicitly limited | None | Online `Player` and loaded world | Public provider returns unavailable for an offline UUID; focused test and contract prove no substitution/mutation | PASS — limited |
| Vault balance/cost/reward 2.20.2 | Supported through `OfflinePlayer` | Supported through Economy | Compatible Economy service | Phase 8E random offline UUIDs were debited exactly, service loss after PRE caused zero effect, and replacement recovered | PASS |
| mcMMO live skill metrics | Explicitly limited by vendor live API | None | Loaded mcMMO player | Missing/lost provider is unavailable and capability-local; no offline zero is authorized | PASS — limited |
| mcMMO adjusted-XP manual total | Supported after event capture | Live event capture only | Valid live mcMMO event for writes | 4,096 UUID rows and 104,960 accepted increments converged exactly and survived unchanged restart | PASS |
| Placeholder input metric | Supported only after a valid cached sample | None | Compatible resolver plus non-stale sample | Missing/invalid/stale sample is unavailable; cache/provider tests prove no implicit zero | PASS — cache limited |
| Placeholder output | Supported after materialization | None | Refresh publication; render is cache-only | 11,520 refresh requests and 180,000 renders converged; unchanged restart retained publication behavior | PASS |
| GriefPrevention claims/reward 16.18.7 | UUID-capable | UUID-capable reward | Compatible datastore | Official access uses UUID player data; lifecycle loss/rebind was capability-local and focused reward tests retain fail-closed preflight | PASS |
| WorldGuard region 7.0.18 | Explicitly limited | None | Online player, loaded world and region manager | Official adapter reports `Player is offline`; focused provider test distinguishes false from unavailable; lifecycle isolation passed | PASS — limited |
| CraftEngine item metric/reward 26.7.4 | Explicitly limited | Explicitly limited | Online inventory and ready registry | Metric/preflight explicitly require an online player; focused offline test and real reload rebind passed with no fabricated item/effect | PASS — limited |
| Stable SDK Alpha (`points`, `bonus`) | Supported | N/A: requirement metrics | Owner registration | Real offline UUID reads, malformed/throw/hang/unregister/rebind and generation recovery all passed | PASS |
| Stable SDK Beta (`tokens`) | Supported | N/A: requirement metrics | Owner registration | Simultaneous offline UUID requirement evaluation passed | PASS |
| Stable SDK Beta (`online_only`) | Explicitly limited | N/A: requirement metric | Online player | Real Paper set the provider to its explicit online-only mode: evaluation blocked with unchanged row/balance while Alpha stayed usable | PASS — limited |
| EconomyShopGUI/QuickShop compatibility listeners | N/A | N/A | Relevant vendor event | These are compatibility listeners, not offline progression/cost/reward providers | N/A — contract |

The real fault environment used UUIDs with no online Paper player. The explicit Beta online-only assertion proves the
unsupported shape end-to-end: it returned unavailable, the target evaluation was not eligible, balance remained `99`,
the existing player row count remained one, and Alpha stayed available. Source-contract rows are backed by the focused
tests named above and by real dependency availability/lifecycle qualification; they do not claim a vendor capability
that the production adapter does not expose.
