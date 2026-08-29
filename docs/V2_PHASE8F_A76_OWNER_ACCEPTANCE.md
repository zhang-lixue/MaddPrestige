# Phase 8F A76 owner-operated final-RC acceptance

Date: 2026-08-28<br>
Policy authority: D-179 owner-operated replacement for the private pre-release<br>
Result: **PASS — A76 Satisfied**

## Exact tested candidate

- Version: `2.0.0-rc.1`
- Distribution SHA-256: `0D961DB73D6C44CF18986CA42B7950914CEA5A1598BA7D41D0E919BFB76C6513`
- Java: 25
- Paper: 26.1.2 build 74
- LuckPerms: 5.5.71
- Server state: completely fresh directory
- Player boundary: real Minecraft player, not the qualification harness

This was an owner-operated public/admin-surface run under the superseding acceptance policy. It is not described as
independent, blind external administration, or the original A76 protocol unchanged. Repository inspection found no
evidence contradicting the owner record, and the repository distribution hash is byte-identical to the tested hash.

## Reconciled execution record

1. Fresh install enabled successfully as `2.0.0-rc.1` and reported safe dormant startup with zero recovery work.
2. A real player remained online while dormant for more than ten seconds with zero repeated Placeholder warnings,
   zero MaddPrestige error spam, and no later restart requirement.
3. The owner created external LuckPerms groups `member`, `adventurer`, and `veteran`.
4. `setup start` established the implicit owner-bound session without repeated UUID entry.
5. LuckPerms was discovered healthy and selected.
6. Member -> Adventurer -> Veteran stages and projections were configured.
7. `member` was selected as the baseline.
8. `setup playtime adventurer 1m` and `setup playtime veteran 3m` were accepted and canonicalized to the documented
   `PT1M` and `PT3M` DURATION targets.
9. Prestige was enabled at Veteran with reset to Member.
10. Preview was VALID, showed the exact ladder and Prestige rule, and identified the expected acknowledgement findings.
11. Server acknowledgement and confirmation applied canonical revision `r2-7c783d729af843f196ba7414798288bd`.
12. The same player initialized after live activation without restart.
13. Initial LuckPerms parents were `default` plus `member`.
14. Why reported the Adventurer `PT1M` requirement Satisfied.
15. Eligible Member -> Adventurer confirmation completed with only `default` plus `adventurer` afterward.
16. Why reported `veteran_playtime` Satisfied at `PT3M`.
17. Eligible Adventurer -> Veteran confirmation completed with only `default` plus `veteran` afterward.
18. Eligible Prestige completed Veteran -> Member and incremented both current and lifetime Prestige from zero to one.
19. LuckPerms returned to `default` plus `member`; a new Prestige attempt was correctly blocked at Member.
20. Clean same-directory restart preserved Member projection and Prestige state without recovery or Placeholder failure.
21. Final Doctor was HEALTHY. `provider.phase8_events` was correctly DEFERRED because the dormant provider was not
    required by the active configuration; Doctor explicitly required no action.

These results close the setup-entry blocker, OR8F-A76-01 diagnostic blocker, and OR8F-A76-02 dormant warning blocker.
The initial failed runs remain historical blocker evidence and are not waived or reinterpreted as passes.

## Deferred non-blocking owner UX inventory

The following findings are accepted UX debt. They do not invalidate correctness, A76, Phase 8F, or Phase 8 exit, and
must not be implemented during this reconciliation.

| ID | Deferred finding |
|---|---|
| UX-01 | Setup is functional but exposes command grammar instead of behaving as a progressively guided wizard. |
| UX-02 | `setup provider` does not directly surface valid choices. |
| UX-03 | `setup discover` produces an excessive technical wall of raw provider capabilities and metrics. |
| UX-04 | Setup preview is too verbose for Minecraft chat and exposes implementation-level details. |
| UX-05 | Acknowledgement wording says configuration is inconsistent and should be corrected even when VALID and acknowledgement is the intended next step. |
| UX-06 | Requirement previews expose internal IDs instead of concise human descriptions. |
| UX-07 | `player` combines multiple verbose previews and scrolls relevant information out of view. |
| UX-08 | `why rankup` exposes requirement IDs, `UNAVAILABLE` sentinels, raw ISO durations, and revision IDs. |
| UX-09 | Rank-up and Prestige previews expose excessive operation/configuration detail. |
| UX-10 | Doctor produces a large technical wall even when HEALTHY. |
| UX-11 | Root `confirm` and `setup confirm` are easy to confuse. |
| UX-12 | Admin player inspection requires UUID rather than naturally accepting a player name. |
| UX-13 | Default chat output should be a concise human summary, relevant result, and next action; exhaustive details belong behind verbose/advanced commands, YAML comments, external documentation, and the future GUI. |

Owner sequencing is preserved: Phase 9 correctness, then a dedicated output/admin-UX polish pass, then comprehensive
GUI work. None begins in this reconciliation.

## Acceptance and release boundary

A76 is Satisfied. The mechanical ledger becomes **63 Satisfied / 12 Partial / 1 Later**. Phase 8F and the Phase 8
work phase satisfy their exit criteria. This does not declare GA or production readiness: A63 remains Partial pending
the actual MaddKraft clone/deployment migration in Phase 9, A64 remains Later by the external-SQL scope decision, and
the other accepted Partial criteria retain their recorded later integration boundaries.
