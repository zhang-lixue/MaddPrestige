# Phase 8F final-RC owner-acceptance setup correction

Date: 2026-08-28<br>
Baseline HEAD: `993599dc47cacc76b6372302d338d1850bf2896e`<br>
Branch: `v2/phase-8f`<br>
State: targeted Owner Review 3 PASS; final real-player owner-operated run PASS; OR8F-A76-01/02 closed; A76 Satisfied

## Owner-run evidence and root cause

The owner-operated fresh-server run stopped at setup preview. The public full-form command accepted a raw requirement
target, but setup stored that target as untyped text and did not resolve its provider-owned value type, parse it, or
canonicalize it until preview generated YAML and invoked the canonical compiler. That deferred boundary allowed a
command to report success even when the exact stored target could later produce `requirement.target.invalid`; rejecting
the leaf then correctly caused downstream unknown-reference and empty-group findings.

The canonical metric contract was not wrong. `paper_statistics/play_one_minute` advertises `DURATION`, and the exact
ASCII literals `PT1M` and `PT3M` parse as ISO-8601 durations. The frozen RC's automated Paper run had exercised those
exact literals successfully, so the evidence does not support describing `PT1M` itself as invalid. The defect was the
untyped, delayed-validation public entry boundary and its misleading success response. The correction removes that
ambiguity: setup now resolves the active provider descriptor, validates operator/scope/completion, parses the target,
stores its canonical typed representation, and only then reports the command as accepted. Preview still independently
compiles the complete draft and remains the publication authority.

## Owner Review 1 blocker and correction

Owner Review 1 rejected the first correction candidate because its new eager requirement validation reused
`setup.draft.invalid`. That established code means only that first-run setup received a draft with active or rollback
ancestry. Since public command failures render through semantic catalog identities, invalid target, operator, scope and
completion inputs could therefore produce factually false ancestry/rollback guidance.

Owner Review 2 separates those conditions into `setup.requirement.target.invalid`,
`setup.requirement.operator.invalid`, `setup.requirement.scope.invalid` and
`setup.requirement.completion.invalid`. Each identity owns catalog prose and structured actual/expected/provider/metric
facts plus valid alternatives where applicable. `setup.draft.invalid` and its ancestry rendering are unchanged. The
command and Paper-presentation boundaries prove `one-minute` reports an invalid DURATION with `PT1M`/`PT3M` and genuinely
supported `1m`/`3m` remediation, while no target error mentions active configuration, first-run ancestry or rollback.

## Owner-facing workflow correction

- `setup start` records an owner-bound current session for two hours. Normal commands omit its UUID; explicit UUID
  forms remain supported for resumption and automation.
- `setup playtime <target-stage> <duration>` generates an immutable requirement ID and uses
  `paper_statistics/play_one_minute`, `GREATER_OR_EQUAL`, `SINCE_PRESTIGE_START`, and `LIVE`.
- The full `setup requirement [session] [target-stage] ...` form remains available and now validates/canonicalizes at
  entry. The exact former `PT1M`/`PT3M` commands remain regression-covered.
- `setup provider`, `stage`, `baseline`, `prestige`, `preview`, `acknowledge`, `apply`, and `cancel` accept the current
  session form. The draft -> preview -> server acknowledgement -> confirm safety sequence is unchanged.
- Prestige completion advances from enabled/disabled to the configured session stages instead of repeating the mode.
  Provider, metric, operator, duration, scope, and completion positions are contextual.
- `/maddprestige help` now opens a command overview. `help setup` gives exact next-step syntax, and
  `help measurement` gives duration target examples and the common playtime command.
- Completion reads only an immutable in-memory session snapshot on the keystroke path; it performs no database,
  provider, filesystem, or draft-cleanup work.

## Regression and qualification boundary

Core regressions cover all four exact public eager-validation identities, the unchanged true ancestry identity, the exact full-form `PT1M` command, guided `PT3M`, eager DURATION typing/canonicalization,
invalid-target rejection before preview, current-session lifecycle, generated IDs, contextual completion, Prestige
syntax, help, preview, acknowledgement, confirm, and public-document text. The exact-JAR Paper harness covers fresh
Member projection, early blocked Adventurer plus Why, Adventurer, Veteran, Prestige 1, reset to Member, LuckPerms
projection, preserved Paper lifetime statistic, reset current-Prestige baseline, exact durable history/configuration,
and unchanged restart.

The previous distribution hash remains historical evidence for the owner-run blocker and is not the corrected
candidate. At that pre-acceptance boundary A76 was not passed, failed, waived, or marked Satisfied; a public-only
fresh-server rerun against an accepted exact corrected artifact was still required. Phase 9 had not started.
## Owner Review 2 candidate evidence (historical)

- focused verification: 87 tests / five suites / zero failures, errors or skips;
- two consecutive clean verifies: 551 tests / 102 suites / zero failures, errors or skips, Checkstyle zero;
- isolated changed Paper harness build: PASS;
- fresh Paper 26.1.2 build 74 / LuckPerms 5.5.71: 29/29;
- identical-directory restart: 3/3;
- distribution: 16,505,971 bytes,
  SHA-256 `C5646F927789CFF95C8F8F8663ADB264C1498D8D8EA927F4C027369085A656D3`;
- aggregate SBOM: 190,831 bytes,
  SHA-256 `3797F16C617B3207229EFD8A846BE2EC31FB9CD56BE681A2069447A859D5568E`;
- both artifacts are byte-reproducible across clean builds.

These are engineering and Owner Review 2 candidate results. They do not complete the owner-operated acceptance
run and do not change A76 from Partial / Blocked.

## Owner-operated run blocker 2 and narrow correction

The new empty-directory owner run against the accepted Owner Review 2 artifact reached the supported dormant state,
but an online player caused `PlaceholderSnapshotPublisher` to invoke player lifecycle initialization every second.
`ProductionRuntime.initializePlayerLifecycle` represented absence of an active canonical configuration as an
`Optional<String>` failure, and the publisher treated every present value as an operational failure. It therefore
logged `Placeholder snapshot initialization failed safely: No canonical configuration is active for player
initialization` on every scheduled refresh even though no configuration was an expected first-install state.

Owner Review 3 separates the outcomes at the lifecycle boundary. The publisher receives an explicit ready, dormant or
failed result. Dormant refreshes remove any stale cached snapshot, skip stage/Prestige repository materialization, and
emit no warning or error. This is a state guard, not rate limiting or message suppression. The same player is retried
normally after live canonical activation, so there is no persistent dormant marker and no restart requirement. Active
initialization failures still remove stale cache state and log the original actionable failure detail.

The focused publisher regression runs six dormant scheduler ticks, proves zero logging and zero state reads, activates
the same player live and proves a Member/Prestige snapshot, then injects an active LuckPerms failure and proves its exact
warning remains. The exact-JAR Paper run invokes the real MaddPrestige join listener for a synthetic online player over
six one-second dormant intervals, proves zero Placeholder warning and zero player stage/Prestige rows, activates the
documented configuration, and proves that same player receives durable Member state and real LuckPerms projection
without restart. The complete PT1M/PT3M setup, progression, Prestige and unchanged-directory restart remains green.

## Owner Review 3 accepted correction evidence

- focused verification: 89 tests / six suites / zero failures, errors or skips;
- two consecutive clean verifies: 553 tests / 103 suites / zero failures, errors or skips, Checkstyle zero;
- isolated changed Paper harness build: PASS;
- fresh Paper 26.1.2 build 74 / LuckPerms 5.5.71: 30/30;
- identical-directory restart: 3/3;
- distribution: 16,507,914 bytes,
  SHA-256 `0D961DB73D6C44CF18986CA42B7950914CEA5A1598BA7D41D0E919BFB76C6513`;
- aggregate SBOM: 190,831 bytes,
  SHA-256 `3797F16C617B3207229EFD8A846BE2EC31FB9CD56BE681A2069447A859D5568E`;
- both artifacts are byte-reproducible across clean builds.

Targeted Owner Review 3 accepted these engineering results and the exact distribution. They did not substitute for the
replacement owner-operated acceptance run.

## Final owner-operated acceptance

The owner then ran the accepted exact hash with a real Minecraft player in another completely fresh Paper/LuckPerms
directory. More than ten dormant-online seconds produced zero Placeholder warning/error spam. The same player
initialized live without restart after the owner-bound setup accepted `1m`/`3m`, produced a VALID preview and applied
revision `r2-7c783d729af843f196ba7414798288bd`. Member -> Adventurer -> Veteran, Prestige to Member, real LuckPerms
projection, clean restart and final HEALTHY Doctor all passed through public/admin behavior.

OR8F-A76-01 and OR8F-A76-02 are closed; A76 is Satisfied. The complete owner record and non-blocking UX-01 through
UX-13 inventory are in `V2_PHASE8F_A76_OWNER_ACCEPTANCE.md`. No output redesign, GUI work or Phase 9 work occurred.
