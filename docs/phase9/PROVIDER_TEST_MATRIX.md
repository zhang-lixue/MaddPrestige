# Phase 9 real-provider and economy exploit matrix

## Execution rule

Every row in this document is unexecuted Phase 9 procedure until an owner-reviewed disposable clone supplies the
named real plugin/service. Unit tests, fake providers, and historical Phase 7/8 evidence can establish prerequisites
but cannot turn a row into a current-clone pass. Record actor UUID, plugin versions/hashes, active configuration
revision, provider ID/generation/health, request and durable operation IDs, external/internal before-and-after state,
audit/Doctor/Why output, console span, and cleanup checkpoint for every execution.

Actors: `ADMIN` is an OP owner account; `STAFF` is an authorized non-OP; `PLAYER-A` is ordinary non-OP;
`PLAYER-B` holds `mad_hatter` plus a progression group; `PLAYER-C` is a cooperating ordinary account; `OFFLINE` is a
known UUID not logged in.

## LuckPerms

| Case | Preconditions / actor | Operation | Expected state and evidence | Cleanup |
|---|---|---|---|---|
| Profile preflight | Existing `curious`, `dreamer`, `tea_guest`, `wonderlander`, `madcap`; `ADMIN` | Canonical preview/apply of qualification profile | Valid exact order; `wanderer` projection none; no group/inheritance/weight/prefix change; configuration revision/audit captured | Roll back revision if run is only preflight |
| Missing group | Remove/rename one required group only in disposable LP checkpoint; `ADMIN` | Preview/apply | Action blocked before publication; Doctor names stage/group; group is not recreated | Restore LP checkpoint and revalidate |
| Normal rank path | `PLAYER-A` at Wanderer, requirements made safely attainable for test only | Rank through all stages | Exactly one managed permanent group changes per step; stage/history/audit and LP before/after agree | Restore player/DB/LP checkpoint |
| Supporter isolation | `PLAYER-B` has `mad_hatter`, unrelated permanent permission/group, and contextual/temporary membership | Rank, Prestige, reconcile, restart | Only current managed progression group changes; every unrelated node survives byte/semantic comparison | Restore checkpoint |
| Staff isolation | `PLAYER-B` additionally has staff group | Admin repair/rank/Prestige and restart | Staff/supporter nodes remain outside write set; actor/target audit exact | Restore checkpoint |
| Offline projection | `OFFLINE` cached/not online | Authorized repair/rank projection | Async UUID load/save/verification succeeds or fails closed; no Bukkit OP dependency | Restore LP and DB checkpoint |
| Manual drift warn-only | Alter one managed group externally | Reconcile/status/Doctor | Mismatch reported, no automatic hierarchy/group creation and no unrelated mutation under `warn-only` | Restore exact intended membership |
| LP service loss | Active configured profile | Disable/loss through accepted lifecycle, attempt rank, then rebind | Referencing operation blocks with zero stage/cost/reward effect; unrelated diagnostics run; new generation recovers; stale work fails | Re-enable/restore and confirm Doctor |

## mcMMO

| Case | Preconditions / actor | Operation | Expected state and evidence | Cleanup |
|---|---|---|---|---|
| Current level/power | Real mcMMO 2.2.053 player data | Compare `/mcmmo`/API-authoritative values with Why/simulation | Exact current values; invalid skill/unavailable data is not zero | Restore player checkpoint if mutated |
| Adjusted XP scope | `PLAYER-A` with a fresh Prestige scope/baseline | Earn known XP through normal gameplay | Only authenticated final XP events after baseline increment; value and durable manual total agree | Restore DB/mcMMO checkpoint |
| Choice path | Requirement tree permits mcMMO or another path | Complete only mcMMO option | Intended ANY/ANY_X choice satisfies without forcing money/PvE/quest categories | Restore checkpoint |
| Duplicate/replayed event defense | Controlled plugin-supported event delivery; do not inject fake live evidence | Observe normal event once; replay only in a dedicated harness if vendor boundary permits | No unreviewed duplicate credit; report exact provider guarantees/limitations | Restore checkpoint |
| Disable mid-evaluation | Active mcMMO requirement | Start preview/operation, deliver provider loss before consequence | Revalidation blocks; zero cost/stage/reward; health unavailable | Re-enable and verify new generation |
| Disable/re-enable | Existing XP total | Loss, Doctor/Why, lifecycle return, new XP | Old listener/generation cannot mutate; new binding resumes without resetting prior durable total | Restore checkpoint |
| Stale/unavailable data | Remove live player or make vendor value unavailable | Why/simulation/rank | Unavailable/fail-closed, never implicit zero/completed | Restore online/provider state |
| Restart | Dirty accepted XP progress then clean stop/start | Compare DB/provider/Why before and after | Progress/baseline survives flush/recovery exactly; no duplicate event credit | Restore checkpoint |
| Process interruption | Persist operation, interrupt only at an owner-approved safe harness boundary | Restart/reconcile | No blind replay; incomplete/uncertain state remains Doctor-visible | Archive evidence, restore checkpoint |

## Vault and Essentials economy

| Case | Preconditions / actor | Operation | Expected state and evidence | Cleanup |
|---|---|---|---|---|
| Balance read | Known Essentials balance | Compare Vault balance metric/Why with `/bal` | Exact finite balance and currency scale; read occurs on controlled server thread | Reset balance |
| Cost success | Test-only explicit cost; sufficient balance | Rank/Prestige once | Aggregate preflight first; exact debit once; committed stage and audit/journal agree | Restore DB/economy checkpoint |
| Insufficient cost | Balance below total cost | Preview/apply | Blocked before any debit, LP or internal change | Restore balance |
| Service missing | Withdraw Vault Economy service | Preview/apply | Referencing operation blocks; no partial state; non-economy paths remain available | Re-register service |
| Service replacement after PRE | Controlled Economy service churn | Begin operation, replace service generation | Stale operation fails closed; zero debit unless external outcome is explicitly uncertain and journaled | Restore checkpoint/service |
| Provider throw/failure | Test Economy returns supported failure/throws before change | Execute | Failed with zero state/balance effect; no clean POST success | Restore provider |
| Mismatched success | Dedicated controlled Economy reports success with wrong amount | Execute | `UNCERTAIN`/`NEEDS_RECONCILIATION`; no retry or false compensation claim | Manual reconcile then restore |
| Restart/retry | Complete one debit then restart/reissue same user action | Inspect journal/idempotency | Completed operation is not duplicated; new request is separately authorized and not inferred as retry | Restore checkpoint |
| Offline cost/reward | `OFFLINE` UUID and compatible Essentials provider | Authorized operation | Supported Vault OfflinePlayer path works or blocks explicitly; no online substitution | Restore balance/DB |

Vault balance is safe only as a current live condition/cost/reward source. It is not proof of earnings during a run and
must not be used to infer source-aware earned money.

## Shops, trade, MobCoins, and optional providers

| Provider/case | Preconditions / actor | Operation | Expected state and evidence | Cleanup |
|---|---|---|---|---|
| EconomyShopGUI normal sale | Real 7.2.0 shop and known item | `PLAYER-A` sells to server shop | Essentials balance changes as vendor defines; MaddPrestige records only compatibility diagnostics and zero progression because mixed currency units are not safely attributable | Restore inventory/balance |
| EconomyShopGUI unavailable/rebind | Listener bound | Loss/return and sale | No MaddPrestige crash or progress; listener binds exactly once after recovery | Restore plugin/check listener count |
| QuickShop normal P2P | `PLAYER-A` seller, `PLAYER-C` buyer | Complete public shop purchase | Buyer/seller balances/items change normally; MaddPrestige progression delta exactly zero | Reverse/reset transaction state |
| QuickShop lifecycle | Listener bound | Loss/rebind and one purchase | Diagnostic listener local, no progress capability, no duplicate listener after rebind | Restore plugin/check listeners |
| AxTrade P2P | Two controlled accounts | Trade money/items both directions | No MaddPrestige metric/event credit, cost, reward, or audit implying earnings | Restore balances/items |
| UltimateMobCoins | Known MobCoins and Vault balances | Earn/spend MobCoins | Vault earning metric/balance semantics remain separate; only explicit configured fallback may observe MobCoins | Reset both currencies |
| AxSellWands | Known sell wand inventory | Perform sale | Coexistence only; zero MaddPrestige earnings until a stable source-aware adapter exists | Restore inventory/balance |
| GriefPrevention | Native capability configured | Read claim state / apply test reward | Exact supported UUID state or fail-closed; unrelated provider outage isolated | Restore bonus blocks |
| WorldGuard | Online player, exact world/region | Enter/leave/read then provider loss | Read-only boolean correct; offline/missing region unavailable, never mutation | Restore position/provider |
| CraftEngine | Ready 26.7.4 registry | Count/reward, full inventory, reload, disable/re-enable | Exact item identity/count/capacity; no drop/replay; completed reload replaces generation; old authority rejected | Restore inventory/config |
| AdvancedCrates/UMC PAPI fallback | Explicit owner-approved typed placeholder only | Sample valid/missing/stale/unparseable values | Valid typed cache works; every invalid state is unavailable; no direct DB/plugin-internal scraping | Clear test cache/data |
| Stable third-party provider | Owner plugin registers through ServicesManager | late register, timeout, malformed map, unregister/rebind | Namespace/owner attested; failure isolated; stale generation rejected; cleanup exact | Unregister/restore |

## Economy exploit qualification

No production price, fee, earnings target, scaling factor, reward cadence, or milestone is approved by this matrix.

| Exploit case | Procedure | Required result | Production disposition |
|---|---|---|---|
| QuickShop self/circular trade | A sells to C, C sells same item/value back; repeat across restart | MaddPrestige earned progress remains exactly zero; no event/retry credit | Disabled/non-creditable |
| QuickShop cooperating accounts | Transfer stock/money across A/C with gross volume far above net value | Zero progress despite volume | Disabled/non-creditable |
| AxTrade cycles | Repeated direct trades in both directions | Zero progress and no provider registration | Disabled/non-creditable |
| `/pay`/Vault transfer | A pays C and C returns money | Balance moves but no earned metric exists; current-balance conditions reflect only current balance | Non-creditable as earnings |
| Buy/sell cycle | Buy from any shop then sell to EconomyShopGUI repeatedly | No progression credit in current adapter; record only actual net balance/vendor behavior | Disabled pending source-aware currency-homogeneous provider |
| EconomyShopGUI controlled sale | Sell server-approved sourced items | Current implementation still credits zero; future eligibility requires a separately reviewed native metric with currency/source filters | Candidate source only, currently disabled |
| Duplicate/replayed provider event | Repeat the same logical event or retry after timeout where controlled | No duplicate durable manual progress; uncertainty is visible rather than guessed | Block/reconcile on uncertain evidence |
| Restart after sale/event | Stop/restart at each observable boundary | No replay-derived credit or duplicate debit/reward | Must pass before any earnings metric activation |
| Admin/console grant | Grant money/MobCoins/items outside gameplay | Not classified as player-earned unless a provider can prove and filter source | Excluded by default |
| MobCoins versus Vault | Earn/transfer/spend MobCoins while observing Vault | No cross-currency conversion or Vault earnings inference | Separate source/currency |
| Negative/refund/failed transaction | Force vendor-supported cancel/refund/failure | No positive progress; diagnostic/audit records truthfully describe outcome | Excluded |
| Concurrent double submit | Issue two confirmations/commands for the same player | One authoritative transition wins; aggregate preflight and journal prevent double effect | Block/conflict |

For each exploit case record net Vault change, gross vendor volume, MaddPrestige progress before/after, event count,
operation IDs, and restart comparison. A test passes only when the unsafe source stays non-creditable; a warning in
documentation is not sufficient if progress changed.

## Restart, crash, and outage matrix

Run each consequential operation at these boundaries on disposable checkpoints where the harness can prove timing:

1. before plan persistence;
2. after pending operation persistence but before external consequence;
3. after external consequence is definitely not applied;
4. after external consequence applied and verified;
5. after external consequence outcome becomes uncertain;
6. after internal commit but before post-event/announcement;
7. during dirty manual-progress flush;
8. during provider disable/rebind;
9. during clean shutdown and during an explicit process kill.

Expected invariants: no operation without a durable ID is presented as committed; verified native work is idempotent
where claimed; arbitrary external work is never blindly replayed; `UNCERTAIN` and `NEEDS_RECONCILIATION` survive;
Doctor names pending/reconciliation state; player, cost, reward, LP projection, baseline, and history remain coherent;
unrelated players/providers continue; restart cannot convert unavailable/unknown into zero/success.

## Evidence naming and completion

Use a run ID such as `PH9-CLONE-YYYYMMDD-NN` and immutable evidence paths for environment manifest, preflight, backup,
mapping, migration dry run/apply, provider cases, exploit cases, full-stack boot 1/2, role tests, boundary tests,
regression verification, artifact hashes, and final gate. Each file records `PASS`, `FAIL`, `BLOCKED`, or `NOT RUN`.
Blank/missing evidence is `NOT RUN`, never an inferred pass.
