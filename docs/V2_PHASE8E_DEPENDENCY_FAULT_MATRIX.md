# MaddPrestige V2 Phase 8E dependency and fault matrix

**Qualified:** 2026-08-23
**Result:** PASS for A67 and closure evidence for P8B-F015

`N/A` means the vendor surface has no safely controlled callback/reload semantic. Generic callback deadline, malformed
output and linkage behavior is qualified through the Stable provider bridge instead of being falsely attributed to a
vendor API.

| Dependency/capability | Absent boot | Disable/loss | Hung/throw/malformed | Replacement/rebind | Reload/during operation | Isolation result |
|---|---|---|---|---|---|---|
| LuckPerms 5.5.71 | PASS after production linkage guard; plugin stays safely dormant | Accepted adapter/focused loss semantics | N/A vendor injection | Async UUID service binding and Phase 8D restart passed | Provider authority revalidated before execution | Internal/Doctor and SDK providers continue |
| Vault 2.20.2 + Economy | PASS | Real service unregister and lifecycle loss | Controlled fail/throw/mismatched success | New Economy service generation rebound exact providers | Service removed from PRE before consequence | Referencing operation blocks; Alpha/Beta remain usable |
| mcMMO 2.2.053 | PASS | Paper lifecycle loss/return | N/A vendor hang | Fresh integration generation returned | Loss while runtime active was capability-local | Unreferenced SDK progression remains eligible |
| PlaceholderAPI 2.12.2 | PASS | Paper lifecycle loss/return | Missing/stale input focused tests | Output binding returned | Refresh/publication path stayed bounded | Progress/SQLite/provider paths continue |
| EconomyShopGUI 7.2.0 | PASS | Exact public compatibility listener withdrawn | N/A: no progression callback authority | Exact listener rebound once | N/A: no authoritative reload contract used by MaddPrestige | Diagnostic-only lifecycle stayed local; unrelated progression remained usable |
| QuickShop-Hikari 6.2.0.11 | PASS | Exact public compatibility listener withdrawn | N/A: no progression callback authority | Exact listener rebound once | N/A: no authoritative reload contract used by MaddPrestige | Diagnostic-only lifecycle stayed local; unrelated progression remained usable |
| GriefPrevention 16.18.7 | PASS | Paper lifecycle loss/return | N/A vendor injection | Fresh generation returned | N/A for an unreferenced vendor operation | Unrelated operation stayed eligible |
| WorldEdit 7.4.4 + WorldGuard 7.0.18 | PASS | WorldGuard lifecycle loss/return | N/A vendor injection | Fresh generation returned | N/A for an unreferenced vendor operation | Unrelated operation stayed eligible |
| CraftEngine 26.7.4 | PASS | Missing boot proven separately | Readiness/linkage focused tests | Completed public reload rebound provider generation | Real `craftengine reload config` completion event | Alpha/Beta and unrelated evaluation stayed usable |
| Stable Alpha | PASS | Public unregister | 3,100 ms async deadline/cancellation; runtime/linkage; missing/extra/null/wrong type; deterministic synchronous latch block | New declaration generation shares stable owner-qualified allowance | Rebind from PRE invalidated stale work | Old generation held 4; generation 2 admitted 0/8 and repeated rebind admitted 0/4; Beta stayed usable in 100 ms; release recovered in 48 ms |
| Stable Beta | PASS | Explicit unavailable/online-only | Runtime/linkage capability exists; unhealthy case executed | Independent declaration lifecycle | Offline limitation during evaluation | Alpha stayed usable; zero forbidden effect |
| SQLite manual writer | N/A (required persistence) | Controlled write reservation | Real `SQLITE_BUSY` caused public `DEGRADED` health | Release/retry restored `AVAILABLE` | Load continued, then dirty shutdown/restart | 20 TPS; exact totals; no unrelated corruption |

## Absence and lifecycle protocol

A fresh Paper boot installed only MaddPrestige, Alpha and Beta. All ten optional dependency artifacts represented by
the qualification matrix were absent: LuckPerms, Vault, mcMMO, PlaceholderAPI, EconomyShopGUI, QuickShop-Hikari,
GriefPrevention, WorldEdit, WorldGuard and CraftEngine.
MaddPrestige published safely dormant, Doctor ran, built-ins plus Alpha/Beta were healthy, and shutdown was clean. The
final-artifact absence harness passed 15/15 assertions. This boot retains the LuckPerms optional-linkage proof.

The exact-dependency run used real supported JARs. Paper closes a third-party plugin classloader during an actual
disable, so re-enabling the same vendor instance in-process is not a supported operation. For mcMMO,
GriefPrevention, WorldGuard, PlaceholderAPI and Vault, the harness delivered the real registered
`PluginDisableEvent`/`PluginEnableEvent` only to MaddPrestige's public Paper listeners. This is the accepted Phase 7
lifecycle-boundary protocol and avoids pretending a closed vendor classloader is reusable. CraftEngine is
reload-authoritative and therefore used its real public reload command/completed event. Vault service loss/replacement
was real ServicesManager churn. Every row states which protocol was used.

## Versions

- Java: Temurin 25.0.3+9 LTS
- Paper: 26.1.2 build 74 (`e4e17fc`)
- LuckPerms 5.5.71; Vault 2.20.2; mcMMO 2.2.053; PlaceholderAPI 2.12.2;
  EconomyShopGUI 7.2.0; QuickShop-Hikari 6.2.0.11
- GriefPrevention 16.18.7; WorldEdit `7.4.4+7546-f9e033f`; WorldGuard `7.0.18+2392-fa605e6`;
  CraftEngine 26.7.4

## Executed artifact identities

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Paper 26.1.2 server | 28,865,701 | `BFDB3DB598E2D9B066F65E7E1A3BE1ACC8B11B3FA38360593375E8DD1D060DAC` |
| MaddPrestige distribution | 16,496,982 | `2C0705823860404014908801330CA5D41436FC2FA84F585B7D0B6CE0F2AADA07` |
| LuckPerms 5.5.71 | 1,501,521 | `49CECB66FA1FD22A133039A490E9C1E5095A238E7CD66EB9D2A16FE6C897550D` |
| Vault 2.20.2 | 134,928 | `BD9E7A31F1B2D31A591497174887EEA7AE7E632C6B179DA13E4F0AD732DE2DF7` |
| mcMMO 2.2.053 | 3,104,834 | `7B7C48AF96CDE8F6CCE20C4E0ABFFA06D096E323CCD0FA534310B96B134BE143` |
| PlaceholderAPI 2.12.2 | 1,154,620 | `FF76AF20C7ACF327FF2A28FB2DBD6694E3F946503E72635A5F7B6CB2E64FC014` |
| EconomyShopGUI 7.2.0 | 1,775,871 | `E6B41CEBE12B7BC9D679CC02A7EB3E576E731CD0F050AB6233D781F7DB0955B7` |
| QuickShop-Hikari 6.2.0.11 | 4,444,734 | `EB3D2F01FF1357544221EE06870ADAE1094A4631382AA87AFF931F342D3B38B6` |
| GriefPrevention 16.18.7 | 380,232 | `45E9907C61222E559ED2E099FFEE0EDE706A21243A31D7DB549BAF678708FDF0` |
| WorldEdit 7.4.4 | 7,707,538 | `44C97EE6C1DF9AFA127DF3C5A2C6A7108F826FB44AB7B255A7EC4250FEB89B9D` |
| WorldGuard 7.0.18 | 1,198,472 | `08F3EF58BC521C635D8C78AEDACA96F151D2D397E7CD8018584955DD7468EB05` |
| CraftEngine 26.7.4 | 8,970,694 | `8771A23713BEABCFEFA46DD0B4C768D7EB1112DBDE803243C972C08EC157D406` |
| Phase 8E Alpha | 14,117 | `C067C53165711704095C422E1EED1F3EB7E2F6636018913C8B990AA9ED0B210E` |
| Phase 8E Beta | 10,220 | `9895FF1739744ED1EEAA096611F55E4DE972E884517C4BFBCF270E1F56AEEF77` |
| Phase 8E Economy | 9,545 | `C27A44B0A5B0C1CFE7FA138E7FC1FBBE47BBABC4A37D2FB3FB06B91293AF0505` |
| Phase 8E fault harness | 80,881 | `FF795623ED5E2DF7B9CC8BDF54A263E12CC1CE5ABD05772889E01A99612E0D60` |

The first-party qualification JARs were also rebuilt independently after the evidence documents were frozen. Those
four builds pass; ordinary Maven archive timestamps mean a rebuilt qualification JAR is not presented as byte-for-byte
reproducible. The table records the exact build instances executed by Paper.

The fault log contains one Paper fresh-world `No key layers in MapLike[{}]` data-pack diagnostic, controlled listener
and provider failures, intentional fail-closed warnings during generation churn, and vendor update notices. None is an
unexplained MaddPrestige failure, watchdog stall or failed harness assertion.

## Synchronous blocking result

Alpha generation 1's `SYNC_BLOCK` mode enters a qualification-owned latch before returning its `CompletionStage` and
held all four permits. While those old callbacks remained blocked, Alpha generation 2 attempted eight calls and
admitted zero; three rapid rebinds then attempted four more and admitted zero. Unregistering Alpha did not erase the
old active count or create a new allowance. Throughout the overlap, Beta remained independently executable with
maximum latency 100 ms. Every stale public Alpha stage terminated without false success. After the explicit release,
Alpha active count reached zero/completed four; recovery took 48 ms, the callback worker population was six within the
bounded executor size eight, and later healthy Alpha/Beta evaluations passed. Unit tests additionally prove 20 rapid
rebinds retain one identity state and reclaim it when both generation and callback counts reach zero. The exact
final-distribution fault environment completed 48/48 assertions. Sanitized proof is
`docs/evidence/phase8e/A65_A66_A67_OR8E05_FAULT_MATRIX_SANITIZED.log`.
