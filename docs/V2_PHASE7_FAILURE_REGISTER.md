# Phase 7 complete failure-class register

The common disposition is fail closed before mutation. `UNAVAILABLE` means authoritative evidence cannot be obtained; `FAILED` means execution is rejected before external mutation; `UNCERTAIN` means an external mutation may have occurred and automatic replay is forbidden. Activation, operational health and exact registry generation are independent authorities.

## Core composition and canonical configuration

| Failure class | Detection/enforcement | Disposition/evidence |
|---|---|---|
| Missing default document | Create only missing packaged defaults | Fresh Paper-only boot remains dormant |
| Malformed/unknown/wrong-typed/out-of-range configuration | Strict canonical compilers | Startup fails before ready; no partial publication |
| SQLite/migration/registry/recovery/command-bind failure | Ordered composition catches fatal core failure | Reverse-safe teardown and plugin disable |
| Direct live file reload | Phase 7 command has no read/compile/reconcile/swap path | Explicit refusal and Phase 6 canonical-authority guidance |
| Destructive stage change bypass | Only Phase 6 draft/preview/ack/apply/remap owns publication | Reservation/remap/history/pointer invariants remain enforced |
| Invalid/stale/concurrent candidate | Canonical Phase 6 generation/base-revision/ack checks | Prior pointer/history/runtime retained |
| Provider loss between plan and execution | Registry token, generation and provider health rechecked | Failed before mutation or uncertain after possible external mutation |
| Shutdown | Reverse ownership order stops tasks/expansion/listeners/providers before persistence | No new work; durable event store flushed/closed |

## Common optional lifecycle and classloading

| Failure class | Enforcement | Disposition |
|---|---|---|
| Plugin absent/disabled | Name, enabled state and exact version checked before isolated bootstrap | Capability absent; unrelated providers remain |
| Wrong/decorated version | Exact supported runtime identity allowlist | Local warning; no binding |
| Missing class/linkage/runtime bootstrap failure | Linked types exist only below isolated bootstrap; boundary catches linkage/runtime failure | Only affected capability unavailable |
| Optional API accidentally shaded | Provided dependencies plus final JAR namespace scan | Build/audit failure; current result zero vendor entries |
| Configuration unreachable | Exact provider-set reconciliation leaves registrations inactive/unregistered | Cannot be planned |
| Disable after bind | Listener unregisters exact handles and invalidates generation | Old authority rejected |
| Re-enable | Fresh discovery/bind or integration-specific waiting state | New authority only after readiness/health proof |
| Same configuration reapplied | Unchanged healthy registration preserved where contract permits | No fabricated generation/health change |
| Outage followed by config off/on | Configuration does not manufacture health | Remains unusable until exact recovery/rebind |
| Listener/task/expansion leak | Every bootstrap returns an owned close handle | Reverse cleanup tests/live shutdown |

## Vault

| Failure class | Enforcement | Disposition |
|---|---|---|
| Vault absent/wrong version | Exact optional discovery | Vault providers absent only |
| Economy service missing/unregistered | Bukkit ServicesManager observation | Metric/cost/reward unavailable; binding invalidated |
| Service re-registers | Exact current service creates fresh gate/registrations | New generation; stale operations rejected |
| Non-positive/non-finite/over-scale/double-inexact amount | Accepted exact-decimal validation | Failed before Vault call |
| Mixed player/operation/config/generation batch | Sealed aggregate preflight checks equality | Failed with zero Vault calls |
| Insufficient balance/provider refusal | Exact preflight/result mapping | Failed; no MaddPrestige state advance |
| Exception/ambiguous result after deposit/withdraw | Vault has no receipt/idempotency/reconcile API | Uncertain; never replay automatically |

## mcMMO

| Failure class | Enforcement | Disposition |
|---|---|---|
| mcMMO absent/wrong version/linkage failure | Isolated exact discovery | mcMMO providers/listener absent only |
| Unknown skill/offline player/API exception | Read-only public API boundary | Metric unavailable |
| Current skill/power decreases | Descriptors are non-monotonic current observations | Lower valid value accepted; no fake reset |
| Adjusted XP event not final/authenticated | Only official final adjusted event and current gate accepted | No durable increment |
| Listener from old/disabled/unhealthy generation | Registry token/generation/health gate checked at event time | Event ignored, no store mutation |
| Durable event write/flush failure | SQLite event source reports failure | No false credit; diagnostic failure retained |

## PlaceholderAPI

| Failure class | Enforcement | Disposition |
|---|---|---|
| PAPI absent/wrong version | Isolated exact discovery | Inputs/output absent only |
| Empty/malformed/recursive token | Strict `%identifier_parameters%` compiler and self-expansion guard | Candidate rejected or observation unavailable |
| Unparseable/non-finite/out-of-range/wrong typed result | Declared typed parser/bounds | Unavailable, never numeric zero |
| Scheduled observation stale | Maximum age checked on requirement read | Unavailable |
| Registry deactivated/unhealthy/rebound during scheduled call | Current gate checked before publication | Sample discarded |
| Output expansion render | Reads immutable bounded snapshot only | No DB/provider/PAPI recursion on render path |
| Expansion registration/unregistration failure | Owned optional handle | Capability-local failure/cleanup warning |
| AdvancedCrates/UMC conflation | Separate configured IDs/types; UMC never uses Vault money | Independent fail-closed observations |

## EconomyShopGUI and QuickShop-Hikari

| Failure class | Enforcement | Disposition |
|---|---|---|
| Plugin absent/wrong version/linkage failure | Exact isolated listener bootstrap | Compatibility diagnostics absent only |
| Mixed economy units/transaction ambiguity | No metric/manual-progress/cost/reward registration | Exactly zero progression credit |
| Self-trade/alt/wash/resale/refund/duplicate event | No progression authority exists | Exactly zero progression credit |
| Stale listener after disable/rebind | Owned unregister handle plus gate | Event ignored |
| Configuration requests progression credit | Strict schema fixes value false | Candidate rejected |

## GriefPrevention

| Failure class | Enforcement | Disposition |
|---|---|---|
| Missing/unhealthy/offline/read exception | Controlled public server-thread access | Metric unavailable |
| Invalid provider/type/value/metadata | Exact descriptors and positive INTEGER reward | Validation failure |
| Bonus overflow | `Math.addExact` before setter | Failed, zero mutation |
| Loss before execute | Health/generation gate | Failed before mutation |
| Setter/save/reread exception or mismatch after possible write | Exact post-read cannot prove outcome | Uncertain; never replay |
| Duplicate action | Explicitly non-idempotent/non-reconcilable | Journal must not retry uncertain action |
| Claim create/delete temptation | No mutation API in adapter | Prohibited/absent |

## WorldGuard and Paper world context

| Failure class | Enforcement | Disposition |
|---|---|---|
| Malformed UUID/region/filter | Exact dimensions/parsing | Unavailable |
| Offline player or missing loaded world | Public Bukkit lookup | Unavailable |
| Different world | Exact UUID comparison | Available false |
| Missing manager/region/virtual applicable set | Public read-only query | Unavailable, never false |
| WorldGuard absent | Optional lifecycle isolation | WG provider absent; Paper/others remain |
| WorldEdit absent | WG hard dependency unusable | WG provider absent; Paper/others remain |
| Wrong thread | Server-thread scheduler | Capability failure, no mutation |
| World/region/flag mutation | No such API surface; source/bytecode scan | Prohibited/zero matches |

## CraftEngine registry lifecycle

| Failure class | Enforcement | Disposition/evidence |
|---|---|---|
| Plugin absent | State `ABSENT`; no linked bootstrap | CE-absent/Paper-only runs |
| Present but registry not ready | State `WAITING_FOR_REGISTRY` | No providers; no missing-ID conclusion |
| Enable before first reload | Observation installed, readiness not inferred | Remains waiting |
| Startup registry empty/transient | Startup-only probe requires non-empty `loadedItems()` | Remains waiting |
| Startup registry demonstrably non-empty | Startup-only public probe | May bind available authority |
| Completed first reload | Public completed event revalidates/binds | `AVAILABLE`, new generations |
| Disable | Exact handles removed | `DISABLED_OR_UNAVAILABLE`; old generations rejected |
| Re-enable | Observation reinstalled; startup probe prohibited | `WAITING_FOR_REGISTRY` |
| Completed reload after re-enable | Public event proves readiness | `AVAILABLE`, fresh generations |
| Configured ID removed on reload | Per-reload validation plus per-operation lookup | Read unavailable/reward invalid; no old definition |
| Same ID definition changed | No cached definition/stack/map | New definition builds under new generation |
| Old operation after reload | Registry generation seal | Rejected before inventory mutation |
| API/linkage failure | Isolated bootstrap | CE unavailable only |

## CraftEngine item read, reward and forgery

| Failure class | Enforcement | Disposition/evidence |
|---|---|---|
| Malformed/unqualified key | Strict namespace plus public `Key.of` | Unavailable/invalid |
| Unknown/removed key | `exists`/`byId` rechecked | Unavailable/invalid |
| Offline player | Online lookup per read/preflight/execute | Unavailable/failed |
| Null/empty slot | Ignored | Correct current count |
| Vanilla visual/material lookalike | Public CE identity absent | Does not count or merge |
| Different CE key with same visuals | Exact key comparison | Does not count or merge |
| Same key with changed visuals | Visual fields irrelevant | Counts as exact logical identity under current definition |
| Arithmetic overflow | Checked addition and exact positive bounded quantity | Failure before mutation |
| Build throws/null/wrong key/wrong amount | Every public build verified | Invalid/failed before mutation |
| Stack splitting | Legal public maximum and verification per stack | Exact quantity |
| Full/partial capacity | Zero-write exact storage simulation | Invalid before insertion if all stacks cannot fit |
| Forged similar stack inflates merge capacity | Similarity and exact CE key both required | Rejected; live material forgery proof |
| Capacity changes after preflight | Immediate second simulation before insertion | Failed with zero insertion |
| Unexpected leftovers | Checked after one insertion attempt; no world drop | Uncertain; no replay |
| Exception/crash after possible insertion | Extent cannot be proven | Uncertain; no replay |
| Duplicate invocation | Non-idempotent characteristics are explicit | May duplicate; deterministic test exposes this truth |
| Consumable cost accidentally inferred from read | No CE `CostProvider`/slot debit/removal path | Independently unavailable |
| Permanent cosmetic entitlement inferred from item | No account/profile entitlement adapter | Independently unavailable |
| Privileged raw-state forgery | Outside ordinary identity threat model | No unforgeable-provenance claim |

## Fallbacks, coexistence and ownership

| Surface/failure | Disposition |
|---|---|
| AdvancedCrates reward | Accepted reviewed generic command only; external uncertainty retained |
| AdvancedCrates requirement | Typed scheduled PAPI only; stale/unparseable fails closed |
| AdvancedCrates crate-open native metric | Unavailable; undocumented event not used |
| UMC balance | Typed PAPI only and distinct from Vault |
| UMC debit/reward | Native debit unavailable; reviewed command reward only |
| DiscordSRV | Optional generic command output; no JDA/admin/remote-control adapter |
| AxPlayerWarps | Unavailable |
| AxSellWands/MaddMobCoins/MaddRTP/resource worlds | Coexistence only; no credit interception or lifecycle ownership |
| Full-stack unrelated plugin warning | Does not become MaddPrestige success/failure evidence; retained only when relevant and sanitized |

## A71–A75 hard-gate failures

| Gate | Failure class | Enforcement/evidence |
|---|---|---|
| A71 | Wrong stage ID/label/order, non-baseline reset, lost reopen state, group/hierarchy creation | Exact full lifecycle/SQLite fixture; pre-existing group proxy; production creation scan zero |
| A72 | Removal of `mad_hatter`, unrelated group/permission or temporary/contextual membership | Ordinary progression, Prestige and reopened reconciliation assertions |
| A73 | Artifact omission, old/candidate co-load, optional coupling, inert Phase 5, CE active-only claim, unsafe runtime reload | Complete 47-row metadata/disposition; replacement only; six server runs; live bindings; real CE read/reward/lifecycle; direct reload refusal |
| A74 | World create/load/unload/delete or WG mutation | Source/bytecode scans and coexistence evidence |
| A75 | Hard-coded Court semantics, use while unconfigured/absent/unhealthy/removed, stale authority acceptance | Complete fake generic provider/config/health/generation test; production Court scan zero |

## Residual boundaries

Folia is not qualified. MySQL/MariaDB remain contract targets rather than locally exercised Phase 7 deployments. Vault, GriefPrevention and Bukkit inventory changes cannot be made atomic with MaddPrestige persistence; uncertainty is preserved. CraftEngine public identity blocks ordinary lookalikes but does not claim provenance against privileged server code.
