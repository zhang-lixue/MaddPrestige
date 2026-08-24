# MaddPrestige V2 Phase 8E load plan

**Prepared before harness implementation/execution:** 2026-08-23
**Baseline:** `73076fe0341024780e3652fda8b0b7f5e38b3cea`
**Correction amendments frozen before Owner Review Correction Pass 1 and Pass 2 reruns:** 2026-08-23
**State:** corrected execution plan; results belong in `V2_PHASE8E_PERFORMANCE_EVIDENCE.md`

Owner Review 1 established that `Thread.getAllStackTraces()` does not enumerate Java 25 virtual threads. The corrected
run therefore uses qualification-only JFR streaming of `jdk.VirtualThreadStart`, `jdk.VirtualThreadEnd`,
`jdk.VirtualThreadPinned` and `jdk.VirtualThreadSubmitFailed`. It classifies the two production name families
`maddprestige-operation-*` and `maddprestige-placeholder-publisher-*`, and records exact active, start, end and
high-water counters. `Thread.getAllStackTraces()` is retained only for the platform-thread population it can actually
observe. This amendment also makes the warm-up and collected-metric descriptions match the executable harness.

## Authority and pass-budget search

The master specification, architecture, traceability register, failure register, status file and prior Phase 8
evidence define qualitative safety budgets but no numerical player-count, TPS, MSPT, heap, latency or throughput
acceptance threshold. Phase 8E therefore will not invent a number and label it authoritative. It will retain raw paired
control/load measurements for owner review and enforce the repository's existing structural invariants:

- manual progress performs no repository write per accepted event;
- one shared drain is in flight, dirty keys coalesce, each repository batch contains at most 1,024 rows, newer dirty
  versions survive an older write, and accepted progress converges exactly once;
- provider callbacks use the bounded production bridge, deadlines and cancellation; one owner-qualified logical
  provider retains a single four-call allowance across transient generations while unrelated providers remain isolated;
- Placeholder refresh has at most one in-flight publication per UUID and rendering reads only the immutable cache;
- public-stage backlog reaches zero, both JFR-observed production virtual-thread families reach active zero with
  starts equal to ends, and named platform-thread populations return to their post-start steady state after work stops;
- no sustained Paper-thread stall attributable to MaddPrestige is present in the recorded TPS/MSPT samples;
- shutdown drains accepted dirty work within the existing bounded shutdown contract, with exact restart proof.

## Environments

Two new disposable Paper 26.1.2 build 74 environments are created below ignored build output. The performance
environment contains the production distribution, the exact supported LuckPerms 5.5.71, PlaceholderAPI 2.12.2 and
mcMMO 2.2.053 artifacts, plus only first-party Phase 8E harness/provider JARs. The fault environment adds exact
supported optional dependencies needed by the dependency matrix. Worlds, databases, third-party JARs and runtime
configuration are excluded from Git and owner-review artifacts.

Each environment records Java/Paper/plugin versions and SHA-256, complete startup and shutdown, harness assertion
totals, and sanitized logs. Performance uses a fresh first boot followed by an unchanged restart. Fault scenarios use
fresh disposable state where isolation between scenarios is required.

## Fixed workload shape

The fixed workload is deliberately declared before implementation so a failing run cannot be made easier silently.

| Window | Duration | Work |
|---|---:|---|
| Startup stabilization/control | 30 s | No generated Phase 8E load; one-second TPS/MSPT/task/thread/heap samples establish the same-process control window. |
| Warm-up | 20 s | No generated workload; retain a separate JVM/JIT/runtime stabilization sample window immediately before measurement. |
| Measurement | 90 s | Sustained event, public-service, provider, Placeholder refresh and cache-render work described below. |
| Recovery/convergence | up to 30 s | Generation stops; observe exact durable totals, backlog zero, provider/manual health recovery and steady task/thread counts. |
| Persistence-stall/recovery | bounded by observed public health | Hold a qualification-owned SQLite write reservation without changing data, accept additional events, observe degraded health, release deterministically, and prove retry/convergence. |
| Dirty shutdown/restart | one shutdown plus unchanged boot | Accept a final marked dirty set immediately before normal shutdown; restart without changing artifacts/config and prove exact durable totals and no duplicate. |

Measurement load:

- 4,096 deterministic synthetic UUIDs receive 102,400 real `McMMOPlayerXpGainEvent` deliveries (25 adjusted-XP
  increments each), paced uniformly across the 90-second window on the Paper thread;
- 256 deterministic UUIDs receive repeated public `MaddPrestigeService.evaluateRankUp` calls at 64 calls/second;
- two independently owned Stable providers answer every configured evaluation, so each evaluation samples both
  provider generations and at least two distinct metrics overall;
- 128 deterministic player proxies receive repeated Bukkit join refresh signals at 128 refresh requests/second;
- the same 128 UUIDs receive 2,000 PlaceholderAPI cache-render calls/second after publication is observable;
- the temporary persistence-stall segment accepts 2,048 additional marked XP events over 256 UUIDs;
- the dirty-shutdown segment accepts 512 additional marked XP events over 256 UUIDs.

The coordinator uses scheduler ticks, completion stages, latches/barriers, public health changes and durable observed
values as synchronization. Wall-clock delays are measurement windows or production deadlines, never the sole proof
that a fault transition happened.

## Backlog and convergence

For manual progress, accepted work is the exact sum of positive adjusted-XP events delivered while the production
mcMMO capability is active. Durable work is the exact `phase5_events/mcmmo_adjusted_xp_total` sum and row count read
from the disposable database by a read-only observer. Backlog is `accepted sum - durable sum`; negative backlog,
unexpected rows, or a final non-zero value fails the run. Convergence requires equality on two consecutive observer
samples after generation stops. Restart must reproduce the same total and row count.

For public service reads, backlog is started stages minus terminal stages. Every terminal value is classified; a lost,
duplicate or still-incomplete stage at convergence fails. For Placeholder publication, backlog is requested UUIDs
without the expected materialized public render; cache rendering returning a stale/missing value after publication
convergence fails.

## Collected measurements

Every second the harness records:

- Paper's reported one-, five- and fifteen-minute TPS values and average tick time (MSPT);
- generated/accepted/completed/failed service calls and latency distribution inputs;
- manual accepted sum, durable sum, durable rows, backlog and SQLite observer `data_version` change;
- Bukkit pending-task count;
- live platform-thread counts grouped for `maddprestige-*`, provider callback/lifecycle and JVM totals;
- JFR-observed active/high-water/started/ended counts for production operation and Placeholder virtual-thread families,
  plus matching pin and global virtual-thread submission-failure observations;
- public provider health state in each sample and the explicitly asserted degraded/recovered transitions;
- Placeholder refresh requests, materialized UUIDs, render calls and mismatches;
- used/committed/max heap and non-heap memory where the JVM exposes it.

The deterministic manual-provider test additionally records exact batch sizes for 2,100 dirty keys and must observe
`[1024, 1024, 52]`. The real Paper observer records durable row/amount changes and SQLite `data_version` movement;
it does not claim access to private production counters or an exact private commit count. JFR streaming starts before
the canonical setup work so pre-measurement operation fan-out is also retained rather than hidden.

## Failure rules

The run fails on any structural-invariant violation, uncaught harness/production failure, main-thread blocking warning
attributable to MaddPrestige, non-converging/lost/duplicate accepted work, unbounded task/thread growth, wrong health
transition, failed bounded shutdown, restart mismatch, or incomplete mandatory sample series. Raw TPS/MSPT/memory
observations are retained without relabeling an invented numerical threshold as repository policy.
