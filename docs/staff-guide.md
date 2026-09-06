# Staff guide

Run `/maddprestige admin` with `maddprestige.admin.gui` to open the Staff Dashboard. Every deeper read or action
also checks its narrow permission.

## Player management

Online and known offline players can be resolved through the canonical player directory. The selected-player view
shows progress, balance, requirements, rewards, a read-only Prestige preview, history, and identity. Provider values
that cannot be read truthfully for an offline player display as unavailable.

Set Prestige accepts a validated positive integer and performs a separate audited administrative operation. Reset
Prestige is the only GUI path to baseline 0 and uses a distinct destructive review. Neither action runs normal player
requirements, costs, rewards, milestones, or provider actions.

## History and audit

Player History shows the selected player's operations; History & Audit shows server-wide operational records. Entries
retain actor, target, operation classification, outcome, and canonical before/after values when persisted. When a
historic balance pair is unavailable, the UI shows a truthful cost fallback rather than reconstructing history from
current state.

Command equivalents are documented in [Commands and permissions](commands-permissions.md).

## Configuration

Configuration views show the active revision, provider availability, Prestige levels, requirements, rewards, and
scaling. Bounded editors support the simple structures that can be mutated losslessly, including guided Money,
unscaled monetary Reward, Total Skill Level, linear Base/Increment, and per-level Override.

Every edit follows:

```text
typed input -> validation -> review -> revision revalidation -> persist -> compile -> activate -> audit
```

Complex or ambiguous trees remain read-only. Use the actor-owned draft commands for advanced changes, inspect the
diff, and never bypass validation by editing active pointers or database rows. See [Configuration](configuration.md).

## Operational health

System Status and `/maddprestige doctor` report configuration, provider, persistence, and recovery health. Treat
unavailable required providers, stale revisions, unknown drafts, and unresolved operations as fail-closed conditions.
Use the [troubleshooting guide](operations/troubleshooting.md) and retain the stable diagnostic code.

The Staff GUI does not grant authority through item text. Sessions bind the acting staff member, selected target,
configuration revision, and action; stale, forged, replayed, and cross-staff actions are rejected.
