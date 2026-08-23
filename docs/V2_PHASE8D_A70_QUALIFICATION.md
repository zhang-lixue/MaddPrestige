# Phase 8D A70 real-Paper qualification

**Result:** PASS, 29/29 fresh boot plus 3/3 unchanged restart  
**Date:** 2026-08-23  
**Evidence:** `PHASE8D_PAPER_BOOT_1.log`, `PHASE8D_PAPER_BOOT_2.log`

Correction Pass 6 changed the authoritative administration catalog, so the complete authoritative two-boot
qualification was rerun rather than reusing rejected Owner Review 6 logs. The results below are the fresh Pass 6 run.

## Fixed environment

| Artifact | Exact identity |
|---|---|
| Java | Eclipse Adoptium Temurin OpenJDK 25.0.3+9-LTS, 64-bit |
| Paper | 26.1.2 build 74, file size 52,893,229 bytes, SHA-256 `1D70B1DAB9CF4A6DE615209A536F3A45A2186240253C428213CE2188AB95E5F7` |
| LuckPerms | Bukkit 5.5.71, file size 1,501,521 bytes, SHA-256 `49CECB66FA1FD22A133039A490E9C1E5095A238E7CD66EB9D2A16FE6C897550D` |
| MaddPrestige | Phase 8D shaded distribution; exact final size/hash are in `PHASE8D_OWNER_REVIEW_SUMMARY.txt` |
| Persistence | fresh SQLite in a fresh disposable single-server Paper directory |

The accepted Owner Review 2 evidence records the former checksum-pinned 5.5.58 environment. Its official Jenkins
artifact had aged out of the ten-build retention window before this rerun, so the fresh Correction Pass 6 logs use the
repository's independently audited 5.5.71 Bukkit artifact and record that substitution explicitly.

The qualification harness is a separate plugin under `qualification/phase8d-paper-harness/`. It invokes public
commands and the published Stable service boundary; it does not modify the database. Supported Bukkit statistic fixture
APIs advance play time without sleeping. Third-party artifacts, world/runtime data and the disposable database are not
review-bundle contents.

## First boot: 29 assertions

1. The fresh server publishes the production services in safely dormant mode.
2. The administrator creates external `Member`, `Adventurer` and `Veteran`; MaddPrestige creates none.
3. Public Quick Start discovery succeeds.
4. The public setup session starts.
5. Existing LuckPerms is selected.
6. `Member` is added as the baseline stage.
7. `Adventurer` is added.
8. `Veteran` is added.
9. `Member` is selected as baseline.
10. A 60-second current-Prestige play-time requirement is attached to Adventurer.
11. A 180-second current-Prestige play-time requirement is attached to Veteran.
12. Veteran-only Prestige reset to Member is configured with no cost/reward.
13. Canonical preview validates the exact three-stage profile.
14. Activation risk is acknowledged with the server-issued token.
15. The sealed profile applies as exact revision `r_3e48f7c0cb92455c942eda09705a3156`.
16. Doctor reports the active profile healthy.
17. A genuinely fresh stable `playerProgress` read atomically establishes durable Member/Prestige/baselines and actual
    LuckPerms `Member`; the journal contains exactly one verified initial projection and no stage transition.
18. Repeating lifecycle establishment returns Member with the same one operation and no duplicate external mutation.
19. Insufficient Adventurer and exact Why are actionable from durable Member with zero progression effects.
20. A brand-new Why identity has actual LuckPerms `Member` before its explanation returns.
21. A brand-new preview identity has actual LuckPerms `Member` before its result returns.
22. A brand-new blocked progression-operation identity has actual LuckPerms `Member` before its result returns.
23. At 60 seconds the offline rank path completes Member to Adventurer once with one managed LuckPerms group.
24. Below 180 seconds Veteran remains blocked with the exact deficit and zero effects.
25. At 180 seconds Adventurer advances to Veteran once with coherent durable history and LuckPerms projection.
26. Prestige increments once, resets Member/LuckPerms/baseline, preserves the Paper statistic and durable history.
27. The Stable facade exposes no caller idempotency token; repeating the terminal request produces no duplicate effect.
28. One supported offline UUID statistic/rank/projection path completes deterministically without a client.
29. Fresh-boot evidence is sealed and the server shuts down cleanly.

The logical profile, generic example and public player/admin output scan contains zero MaddKraft gameplay/rank branding
findings.

## Unchanged restart: 3 assertions

The same directory and artifacts restart with no configuration, database, world or plugin changes.

1. Member, Prestige 1, revision, history, Paper statistic and LuckPerms projection recover exactly.
2. The new current-Prestige baseline remains authoritative and blocks premature Adventurer.
3. Doctor remains healthy.

The server then shuts down cleanly. Boot two reports
`PHASE8D-Q COMPLETE two-boot A70 qualification pass-count=3`.

## Side-effect and durability observations

- Both insufficient attempts create no rank projection, player transition, operation or history effect.
- Initial Member projection is a distinct completed journaled projection operation, not virtual stage resolution.
- Repeat initialization recognizes its durable identity and makes no second LuckPerms mutation.
- Each successful rank change and Prestige transition occurs exactly once.
- Repeated terminal intent has no caller-supplied replay identity at this facade and produces no duplicate effect/history.
- Paper's underlying play-time statistic is never reset by MaddPrestige.
- Prestige writes a new `SINCE_PRESTIGE_START` baseline, so scoped progress becomes zero while lifetime ownership remains.
- Restart preserves the exact active configuration identity rather than recompiling a synthetic revision.
- The offline exercise is evidence for this supported built-in path only; A61 remains Partial.

## Acceptance conclusion

A70 is Satisfied by the exact required profile and real two-boot evidence. A02 is Satisfied because the same fresh
process creates the active ladder through the documented canonical setup wizard. This automated qualification is only a
proxy for A76 and is not represented as an independent blind administrator.
