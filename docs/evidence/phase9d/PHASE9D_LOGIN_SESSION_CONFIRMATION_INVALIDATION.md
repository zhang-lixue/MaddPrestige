# Phase 9D login-session confirmation invalidation

Status: **PASS — OWNER REAL-CLIENT LOGIN-SESSION INVALIDATION OBSERVED**

## Safety baseline

- Player: `tmydwc` / `d7551bf9-6358-3218-89c4-06c9c57dc879`
- Current/lifetime Prestige: `2 / 2`
- Vault balance: `12`
- mcMMO `total_level`: `1`
- LuckPerms direct parents: `default`, `owner`
- Server: disposable clone on `127.0.0.1:25569`
- Runtime: clone-specific firewall-isolated JDK only
- Confirmation or Prestige execution by automation: none

## Active qualification profile

- Draft: `9b0624ec-c3c6-4f22-bdc6-7596a7366299`
- Base revision: `r_3146674c5ca743738d3eb442758e269b`
- Active revision: `r_d30689cab75d406bb08e0edee970d33a`
- Compiled content hash: `309bda1f04523875fde5c202db397bdcabd51a5935bbbb69a030e335908591ad`
- Requirements document hash: `7888b583cd5cf5c705610a2f0c004be23aa153d2f0dc48512422e7f80e1e6286`
- Apply actor/reason: `CONSOLE` / `Phase 9D login-session confirmation invalidation qualification`
- Apply result: `APPLIED`

The canonical draft changed only the existing qualification Vault cost from the deliberately blocked value `13`
to the low non-production value `5`. The accepted per-level override requirement profile and reward `1` remain
unchanged:

```yaml
requirements:
  phase9d_vault_balance:
    target: "2"
    scaling:
      mode: LINEAR
      base: 1
      rate: 0.5
      segments:
        -
          start-prestige: 1
          end-prestige: unlimited
          mode: LINEAR
          transition: EXPLICIT_BASE
          base: 1
          rate: 0.5
          overrides:
            "3": 3
  phase9d_mcmmo_total_level:
    target: "1"
costs:
  phase9d_vault_fee:
    amount: "5"
```

The retained Paper-console response-collapse defect reduced `config validate` to `command.failed`; the immediate
canonical acknowledgement response proved the sealed preview had zero high-risk findings and was ready for normal
apply. Apply succeeded against the exact expected base revision at `18:50:10`.

## Pre-owner zero-write verification

Detailed production simulation at `18:50:35` reported:

- decision: `ELIGIBLE`
- blockers: `0`
- transition: Prestige `2 → 3`; lifetime `2 → 3` if later executed
- Vault requirement: `12 / 6 — SATISFIED`
- mcMMO requirement: `1 / 1 — SATISFIED`
- Vault cost: `5`, payable from current balance `12`
- Vault reward: `1`
- compiled/runtime revision: `r_d30689cab75d406bb08e0edee970d33a`

The simulation was zero-write and created no confirmation. Post-simulation reads proved current/lifetime Prestige
`2 / 2`, Vault `12`, mcMMO `1`, and direct LuckPerms parents `default` and `owner`.

## Session-invalidation objective

The owner will create one pending confirmation in the current login session, copy its UUID, disconnect, reconnect as
the same player, and attempt that old UUID. Player quit must route `endPlayerSession`; the new login starts a distinct
session. The old UUID must not be usable, discoverable by tab completion, or selected by shorthand.

Expected explicit old-ID rejection:

```text
The operation confirmation is unknown, replaced, logged out, expired, or already used.
Diagnostic code: confirmation.unknown.
Next: request a new Prestige preview in the current login session.
```

Expected shorthand rejection after reconnect:

```text
No valid operation confirmation exists in this login session.
Diagnostic code: confirmation.none_pending.
Next: request a fresh Prestige preview, then select Confirm.
```

Tab completion for `/maddprestige confirm ` must not expose the invalidated UUID. Prestige/lifetime must remain
`2 / 2`; Vault must remain `12`; mcMMO must remain `1`; LuckPerms parents must remain `default`, `owner`;
no cost, reward, or provider mutation may execute.

## Automated production gates

The current upstream reactor passes `17 / 17` focused tests across
`OperationConfirmationSessionTest`, `PaperConfirmationSessionListenerTest`, and
`PaperPhaseSixNumericCommandRoutingTest`, with zero failures, errors, or skips. These directly cover session
authority, logout/restart invalidation, replacement, revalidation, replay rejection, player isolation, Paper
join/quit/kick routing, shorthand, confirmation controls, and player-scoped tab completion.

A preceding module-only Maven invocation used stale installed upstream artifacts and failed with binary-signature and
old lifetime-guard mismatches. It made no source or runtime change. The correctly scoped `-am` reactor rerun used the
current Phase 9D classes and passed all `17 / 17` gates.

## Owner-observed result

The genuine client created confirmation `4324e69f-6d13-41c6-990e-a302c2445ab1` at `19:26:50`, then disconnected
at `19:27:14` and reconnected as the same UUID at `19:27:22`. The owner attempted the old explicit ID at
`19:27:31`; it was rejected with:

```text
The operation confirmation is unknown, replaced, logged out, expired, or already used.
Diagnostic code: confirmation.unknown.
```

The owner then attempted shorthand `/maddprestige confirm` at `19:28:06`; it was rejected with:

```text
No valid operation confirmation exists in this login session.
Diagnostic code: confirmation.none_pending.
```

The old confirmation was not executed. No cost, reward, Prestige, mcMMO, or LuckPerms mutation occurred.
Current/lifetime Prestige remained `2 / 2`, Vault remained `12`, mcMMO `total_level` remained `1`, and direct
LuckPerms parents remained `default`, `owner`.

Result: **REAL-CLIENT LOGIN-SESSION CONFIRMATION INVALIDATION — PASS**.
