# MaddPrestige V2 Phase 8E performance evidence

**Qualified:** 2026-08-23
**Result:** PASS for A62 and candidate closure of P8B-F014
**Plan authority:** corrected amendment in `docs/V2_PHASE8E_LOAD_PLAN.md`, frozen before the final Correction Pass 2 rerun

## Exact environment

- Paper 26.1.2 build 74 (`e4e17fc`)
- Eclipse Temurin 25.0.3+9 LTS
- LuckPerms 5.5.71, mcMMO 2.2.053 and PlaceholderAPI 2.12.2
- production MaddPrestige distribution plus the isolated Alpha, Beta and Phase 8E harness plugins
- fresh SQLite/database/world on boot one; unchanged artifacts, configuration and data on boot two

Executed artifact identities:

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Paper 26.1.2 server | 28,865,701 | `BFDB3DB598E2D9B066F65E7E1A3BE1ACC8B11B3FA38360593375E8DD1D060DAC` |
| MaddPrestige distribution | 16,496,982 | `2C0705823860404014908801330CA5D41436FC2FA84F585B7D0B6CE0F2AADA07` |
| LuckPerms 5.5.71 | 1,501,521 | `49CECB66FA1FD22A133039A490E9C1E5095A238E7CD66EB9D2A16FE6C897550D` |
| mcMMO 2.2.053 | 3,104,834 | `7B7C48AF96CDE8F6CCE20C4E0ABFFA06D096E323CCD0FA534310B96B134BE143` |
| PlaceholderAPI 2.12.2 | 1,154,620 | `FF76AF20C7ACF327FF2A28FB2DBD6694E3F946503E72635A5F7B6CB2E64FC014` |
| Phase 8E Alpha | 14,117 | `C067C53165711704095C422E1EED1F3EB7E2F6636018913C8B990AA9ED0B210E` |
| Phase 8E Beta | 10,220 | `9895FF1739744ED1EEAA096611F55E4DE972E884517C4BFBCF270E1F56AEEF77` |
| Phase 8E performance harness | 80,881 | `FF795623ED5E2DF7B9CC8BDF54A263E12CC1CE5ABD05772889E01A99612E0D60` |

The predeclared plan found no repository-owned numerical TPS/MSPT/player-count budget. The run therefore does not
invent one after observing results. Its mandatory gates are exact convergence, no per-event SQL architecture,
bounded/single-flight work, no attributable Paper-thread stall, stable recovery populations, correct public health,
bounded shutdown and exact unchanged restart. Raw observations are retained for owner judgment.

## Fixed workload and exact results

| Work | Result |
|---|---:|
| Deterministic player UUIDs | 4,096 |
| Real mcMMO adjusted-XP events during measurement | 102,400 |
| Public `evaluateRankUp` calls | 5,760 |
| Placeholder refresh requests | 11,520 |
| Placeholder cache renders | 180,000 |
| SQLite-reservation increments | 2,048 |
| Dirty-shutdown increments | 512 |
| Total accepted manual increments | 104,960 |
| Durable before dirty shutdown | 104,448 |
| Durable after unchanged restart | 104,960 exact |
| Service failures | 0 |
| First boot assertions | 23/23 |
| Unchanged restart assertions | 3/3 |

The deterministic unit path separately proves 2,100 dirty keys are persisted in exact repository batches
`[1024, 1024, 52]`. The Paper observer saw the production database converge to every accepted increment with no
negative backlog, unexpected row, loss or duplicate. A real competing SQLite write reservation produced expected
`SQLITE_BUSY`, public manual health became `DEGRADED`, and Paper remained responsive. Releasing the reservation allowed
the production retry path to converge exactly and restore `AVAILABLE`. Normal shutdown then drained the final accepted
dirty set; unchanged restart recovered exactly 104,960.

## One-second observations

| Window | Samples | TPS average / minimum / maximum | MSPT average / minimum / maximum | Pending tasks | Platform threads | MaddPrestige platform threads | Used heap range |
|---|---:|---|---|---|---|---|---|
| Control | 30 | 20.002 / 20.000 / 20.005 | 0.250 / 0.114 / 0.933 | 7 | 83-88 | 4 | 332,709,040-1,023,423,840 B |
| Warm-up | 20 | 20.001 / 20.000 / 20.001 | 0.107 / 0.098 / 0.120 | 7 | 75-80 | 4 | 361,915,136-912,718,280 B |
| Load | 90 | 20.000 / 19.999 / 20.001 | 0.464 / 0.105 / 0.841 | 7 | 75-83 | 4 | 338,833,184-925,985,120 B |
| Recovery | 4 | 20.000 / 20.000 / 20.000 | 0.733 / 0.408 / 1.057 | 7 | 79 | 4 | 457,968,328-642,517,704 B |

There was no watchdog event, main-thread blocking warning attributable to MaddPrestige, sustained pending-task growth,
named-thread growth or unexplained production error. Queue/backlog convergence, not elapsed time alone, ended recovery.

## Java 25 virtual-thread/task and backlog observations

Qualification-only JFR streamed virtual-thread start/end/pinned/submit-failed events and classified the exact
`maddprestige-operation-*` and `maddprestige-placeholder-publisher-*` families. This replaces the invalid original
assumption that `Thread.getAllStackTraces()` could enumerate Java 25 virtual threads; that API is used only for the
platform-thread columns above.

| Family | Active high-water | Started | Ended | Active at convergence |
|---|---:|---:|---:|---:|
| Production operations | 128 | 6,034 | 6,034 | 0 |
| Placeholder publishers | 12 | 11,648 | 11,648 | 0 |

The 128-operation fan-out occurred during canonical pre-load progression and was visible in the first control sample;
it fell to zero by the second sample and never grew again. JFR recorded zero virtual-thread submission failures and
zero pinned events across the observed families. Final exact convergence took 3,399 ms and required two equal observer
samples. Maximum manual
backlog was 10,980, service backlog remained zero, Placeholder backlog peaked at seven and both publication backlogs
ended at zero. At convergence, 5,760/5,760 public stages were terminal, 11,520 refresh requests had materialized the
128 expected UUIDs, and every JFR family had equal start/end counts.

## Provider and Placeholder observations

Both independently owned provider families were active during the workload. The first boot recorded 3,009 Alpha and
2,880 Beta callback reads. All 5,760 public service stages terminated and none failed.

The initial 1,052 Placeholder render mismatches occurred before the corresponding first asynchronous publication and
are retained rather than erased. After publication convergence, render values matched the expected immutable cache;
rendering itself never accessed SQLite or a provider. The fixed pass condition concerns post-publication convergence,
not a false claim that a cache has data before its first refresh.

## Evidence integrity

- `docs/evidence/phase8e/A62_PERFORMANCE_FIRST_BOOT_SANITIZED.log` retains the full harness samples/assertions from the
  fresh boot with absolute repository paths, UUIDs and revision identities redacted.
- `docs/evidence/phase8e/A62_PERFORMANCE_RESTART_SANITIZED.log` retains the unchanged-restart proof with the same
  redaction policy.
- Original disposable runtime logs remain under ignored `target/` output and are not publication artifacts.

The distribution and qualification-tool identities above are byte-for-byte the instances copied into both performance
boots. The distribution SHA-256 also equals the final clean-verify artifact and the distribution packaged for Owner
Review 3. No production, catalog or runtime-affecting source changed after this run. Final isolated qualification builds
pass from source; their ordinary Maven JAR timestamps make their hashes build-instance identities rather than a claim
of reproducible qualification-tool bytes.

## Disposition

A62 is `Satisfied` for the authorized Phase 8E boundary. The run proves the accepted manual provider avoids per-event
SQL, uses bounded coalesced persistence, keeps Placeholder rendering cache-only, remains responsive through the fixed
load and controlled SQLite contention, bounds the measured virtual-thread/task families, converges exactly, shuts down
boundedly and recovers exact populated state on an unchanged restart. It does not claim an invented universal hardware
capacity guarantee.
