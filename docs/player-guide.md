# Player guide

## Open Prestige

Run `/prestige` to open the Player GUI. It shows:

- current and next Prestige;
- whether you are ready;
- requirement progress;
- cost and reward;
- a preview of the resulting balance when it can be determined truthfully.

When blocked, the preview shows the current balance and a canonical shortfall rather than an impossible negative
balance.

## Confirm safely

If every requirement and cost is satisfied, the preview offers **Confirm**. Confirmations belong to your current
login session and are revalidated at click time. They stop being valid after use, replacement, logout, restart,
relevant configuration change, or any safety-critical state change.

MaddPrestige never asks you to race a short timer or manually transcribe an identifier during ordinary GUI use.

## Commands

- `/prestige` — open the Player GUI
- `/maddprestige player` — concise status
- `/maddprestige why prestige` — concise blockers
- `/maddprestige simulate prestige` — zero-effect preview
- `/maddprestige why prestige details` — detailed diagnostic explanation
- `/maddprestige simulate prestige details` — detailed zero-effect projection

The `/maddprestige` namespace also contains permission-scoped staff and diagnostic functions. See
[Commands and permissions](commands-permissions.md).
