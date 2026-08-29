# Phase 8F A76 owner-operated fresh-RC protocol

**Acceptance status:** PASS — A76 Satisfied on 2026-08-28
**Policy:** owner-operated public-only fresh-release-candidate usability and functional acceptance
**Target:** Java 25, Paper 26.1.2 build 74, LuckPerms 5.5.71 and the exact independently accepted corrected RC

## Superseding owner decision

For this private pre-release, the owner supersedes the original independent blind external-administrator A76 protocol
with an owner-operated clean-install run. This is an intentional acceptance-policy decision. The result must not be
described as independent, a blind external-administrator run or the original A76 protocol unchanged.

The fresh-administrator boundary remains substantive. Before consulting internal information, the owner uses only the
exact RC, public plugin behavior, `README.md`, `docs/INSTALLATION_V2.md`, `docs/QUICK_START.md`, and public command help.
Any unclear step or need for undocumented knowledge is recorded as a usability/documentation finding first. Source,
architecture, traceability, owner-review evidence, qualification harnesses and implementation notes are excluded.

The first owner run against SHA-256 `112A0534F6166D8842F470A6C898A452284362761BF5350578453AE198961111`
stopped when the documented duration requirement was rejected at preview. That run is retained as blocker evidence. It
is not a pass or a completed test. The next fresh run against accepted Owner Review 2 SHA-256
`C5646F927789CFF95C8F8F8663ADB264C1498D8D8EA927F4C027369085A656D3` stopped on recurring dormant-state Placeholder
initialization warnings while a player was online before configuration. That run is also retained as blocker evidence,
not a pass. Targeted Owner Review 3 then accepted the narrow correction, and the owner started again in a completely
new server directory with the exact corrected JAR.

## Completed final run

The final run used exact SHA-256 `0D961DB73D6C44CF18986CA42B7950914CEA5A1598BA7D41D0E919BFB76C6513`,
Java 25, Paper 26.1.2 build 74, LuckPerms 5.5.71 and a real Minecraft player. Fresh boot, more than ten seconds dormant
online, guided setup, VALID preview, acknowledgement/apply, live Member projection, Why, both rank-ups, Prestige,
same-directory restart and final HEALTHY Doctor all passed. No source inspection or developer intervention was needed.
The complete result and deferred UX inventory are recorded in `V2_PHASE8F_A76_OWNER_ACCEPTANCE.md`.

## Required run

1. Install the exact Paper, LuckPerms and corrected MaddPrestige artifacts in an empty server directory.
2. Boot and confirm MaddPrestige loads, generates expected files and reports `2.0.0-rc.1`.
3. Use `/maddprestige`, help, tab completion and setup entrypoints; record confusing or undocumented behavior.
4. Create external LuckPerms groups `Member`, `Adventurer` and `Veteran`.
5. Follow Quick Start to configure Member -> Adventurer -> Veteran, with `paper_statistics/play_one_minute`,
   `GREATER_OR_EQUAL`, `PT1M`/`PT3M`, documented measurement scope/completion semantics, Veteran eligibility and Member
   Prestige reset. Preview, obtain the server acknowledgement and apply.
6. Run Doctor and record complete diagnostics.
7. Join with a fresh identity; verify durable Member state and real LuckPerms Member projection.
8. Attempt Adventurer early; verify zero progression/effect and exact actionable Why blocker.
9. Meet the documented requirements and progress to Adventurer and Veteran with correct projection.
10. Prestige once; verify count 1, Member reset, projection, declared preserved state, current-Prestige reset and no
    duplicate cost/reward.
11. Stop cleanly and restart the identical directory; verify stage, count, history, projection, preserve/reset state and
    configuration revision.
12. Run final Doctor and record healthy/expected state.

## Result record

Environment:

- Java:
- Paper:
- LuckPerms:
- MaddPrestige JAR path:
- MaddPrestige SHA-256:
- fresh directory:

Results:

- Fresh boot: PASS / FAIL
- Discoverability/help: PASS / FAIL
- Setup/preview/acknowledgement/apply: PASS / FAIL
- Doctor: PASS / FAIL
- Initial Member projection: PASS / FAIL
- Blocked progression + Why: PASS / FAIL
- Adventurer: PASS / FAIL
- Veteran: PASS / FAIL
- Prestige/reset/preserve: PASS / FAIL
- Restart coherence: PASS / FAIL
- Final Doctor: PASS / FAIL

For every issue record exact action, expected and actual behavior, exact plugin/console message, severity,
reproducibility, and whether public documentation/plugin UX was sufficient.

Overall: PASS / FAIL / PASS WITH NON-BLOCKING NOTES

## Acceptance rule

The owner completed and signed the required fresh run without source inspection, undocumented syntax or developer
intervention, so A76 is Satisfied. Any later runtime-affecting change still creates a new candidate and requires the
affected acceptance to repeat. Automated Paper harness evidence remains engineering proof and is not misrepresented as
the owner-run acceptance.

Historical note: the original Phase 8D protocol required an independent blind administrator. D-179 records the owner's
explicit replacement of that protocol for this private pre-release without erasing the earlier design intent.
