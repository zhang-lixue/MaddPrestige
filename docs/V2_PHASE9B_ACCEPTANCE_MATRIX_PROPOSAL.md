# Phase 9B proposed acceptance-matrix update

This is a proposal for the next owner-approved matrix revision. It does not edit accepted historical evidence.

| Existing row/theme | Proposed numeric interpretation |
|---|---|
| A02 setup | A fresh setup can activate numeric Prestige with no stage ladder or LuckPerms dependency; it directly selects a requirement tree, distinct costs, rewards, maximum, cooldown, and reset policy. |
| A28 Prestige state | Prestige is one durable non-negative integer, advances exactly one, keeps current/lifetime compatibility fields equal, and supports a finite or unlimited maximum without stage eligibility/reset. |
| A36 fresh V2/no V1 import | Prove a fresh V2 database never reads or imports any V1 per-player rank, Prestige, currency, perk, progress, pending-operation, season/history/competition, or preference data; every new V2 player starts at P0 and V1/external state remains untouched. The abandoned mapping is not a gate. |
| A37 legacy group creation | Preserve the independent guarantee that migration and active numeric Prestige never create old or progression LuckPerms groups. |
| A46 broken LP action | Exercise exact optional LuckPerms reward/provider diagnostics, including a missing configured group; the operation blocks before effects and creates no group. |
| A47 Why blockers | Preserve typed blockers, but numeric maximum/cooldown/requirements/cost/provider failures are authoritative; inactive-ladder/rank blockers are compatibility-only. |
| A48 mcMMO | Canonical Prestige input is total mcMMO level; individual skills remain general integration metrics, not guided Prestige requirements. |
| A58 internal recovery | Preserve durable numeric state, journal authority, and exactly-once recovery for internal recoverable effects without stage mutation. |
| A59 external pre-apply failure | Preserve proof that a definitely-not-applied external failure performs no numeric increment and is not misclassified as completed. |
| A60 external uncertainty | Preserve explicit reconciliation and no blind replay when an external effect cannot be proven after interruption. |
| A61 | Keep offline/provider limitations; LuckPerms is needed only for explicitly configured LP actions. |
| A63 populated pre-Phase9B V2 SQLite upgrade | Independently retain the full real populated pre-Phase9B V2 SQLite upgrade/preservation/reporting gate: verified backup first, every supported schema prefix, populated stage-era rows archived truthfully, active numeric P0 initialization, rollback/recovery evidence, integrity checks, exact report, and unchanged-restart idempotency. Do not weaken this to a fresh-database or V1-import check. |
| A70 numeric end-to-end Prestige | Independently prove configured live/provider reads, requirement evaluation, independent costs, configured rewards/milestones, one P0 -> P1 commit, and the exact persisted Prestige after restart. Provider-owned external state remains externally authoritative, and the full path has no rank/stage dependency. |
| A71 LuckPerms structure | Independently prove numeric Prestige creates no LuckPerms groups and performs no LuckPerms hierarchy, inheritance, weight, prefix, or suffix mutation. Additive rewards may target only configured existing groups or permissions. |
| A72 unrelated LuckPerms state | Independently prove supporter/staff/groups/permissions, contexts, and all unrelated player nodes survive successful, blocked, failed, and restarted numeric Prestige unchanged. |
| A76 guided setup | Replace stage/rank-up walkthrough with a numeric Prestige setup and P0→P1 confirmation/restart proof. |

Suggested new rows:

- numeric Prestige works indefinitely without creating ranks/groups and obeys an optional finite maximum;
- requirements combine through ALL/ANY/X_OF_N and remain distinct from costs;
- requirements, costs, and numeric rewards each support independent FLAT/LINEAR/EXPONENTIAL/MANUAL segments,
  transitions, bounds, and overrides;
- every-Prestige, milestone-only, combined, and no-reward configurations are valid;
- current external values are queried from providers rather than mirrored as MaddPrestige authority;
- world/resource reset concepts are absent from Prestige state transitions;
- optional LuckPerms permission/group rewards are additive and never create groups.

Historical A02/A28/A70/A76 stage/rank evidence remains an immutable record of the version tested. It does not satisfy
the proposed numeric gates by reinterpretation alone; numeric clone qualification must produce new evidence.
