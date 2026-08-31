# Phase 9D X_OF_N one-of-two qualification profile

Status: **REAL-CLIENT PASS**

## Canonical revision

- Prior `ANY` revision: `r_9d1d477d52e8483ea14f86996031d8d9`
- Active revision: `r_378ce8085d904cdab161f43a7e620a16`
- Compiled content hash: `4f83ddb38f5223fe8c8ecca204c79fd82a2621f348a385ecdea57f1900b1e26e`
- Apply actor/reason: `CONSOLE` / `phase9d X_OF_N one-of-two eligibility qualification`
- Apply result: `APPLIED`

The persisted `active-revision` pointer, live `/maddprestige status`, and read-only operation preview all identify the
same compiled revision.

## Exact qualification-only profile

```yaml
requirements:
  phase9d_vault_balance:
    completion: LIVE
    metric: balance
    provider: vault_balance
    scope: ABSOLUTE
    target: "13"
    value-type: CURRENCY_AMOUNT
  phase9d_mcmmo_total_level:
    completion: LIVE
    metric: total_level
    provider: mcmmo
    scope: ABSOLUTE
    target: "1"
    value-type: INTEGER_COUNT
trees:
  phase9d_prestige_eligibility:
    children:
      - requirement: phase9d_vault_balance
      - requirement: phase9d_mcmmo_total_level
    mode: X_OF_N
    threshold: 1
```

- Required count: `1`
- Total reachable requirements: `2`
- Exactly two reachable requirements: Vault balance and mcMMO `total_level`
- MobCoins, additional requirements, scaling variants, and nested groups: not active

The prior requirements document changed only from `mode: ANY` to `mode: X_OF_N` plus `threshold: 1`. The lifecycle,
provider thresholds, integrations, costs, and rewards are unchanged. The existing qualification cost and reward remain
configured, but this round authorizes no Prestige preparation or confirmation, so neither can execute.

## Runtime proof and non-mutation

Read-only console checks before apply, after apply, and after simulation all remained:

- Current/lifetime Prestige: `2 / 2`
- Vault balance: `12`
- mcMMO `total_level`: `1`

The explicit-player simulation at `15:54:34` returned `ELIGIBLE`, `blockers=0`, mode `X_OF_N`, Vault
`12 / 13 — UNSATISFIED`, mcMMO `1 / 1 — SATISFIED`, root requirement `SATISFIED`, and plan revision
`r_378ce8085d904cdab161f43a7e620a16`. Thus satisfied count `1` meets required count `1` across total count `2`.
Simulation created no confirmation and executed no operation, cost, or reward.

## Owner-observed real-client result

The owner completed the genuine Minecraft-client status, Why, and simulation round with current Prestige `2`. The
client displayed mode `X_OF_N`, required count `1 of 2`, overall `Ready` / requirements `Met`, Vault
`12 / 13` as **UNSATISFIED**, mcMMO `1 / 1` as **SATISFIED**, and preview `Prestige 2 -> 3`.

Therefore real-client `X_OF_N` one-of-two semantics are **PASS**: satisfied count `1` met required count `1` across
two reachable requirements. The retained server log corroborates `/maddprestige player` at `15:57:31`,
`/maddprestige why prestige` at `15:57:37`, and `/maddprestige simulate prestige` at `15:57:42`. No Prestige
preparation, confirmation, or execution command occurred.

## Canonical validation observation

Draft creation and both scalar edits succeeded through the canonical command workflow. `config validate` produced and
sealed a usable preview, proven by the subsequent `config acknowledge` response that acknowledgement was not required
and directed the normal apply flow. The Paper console nevertheless collapsed the validate response to generic
`[command.failed]`; this presentation defect is retained as evidence and did not bypass validation or alter the draft.
The sealed preview then applied normally against its exact expected base revision.

## Planned follow-up — not active

The later two-of-two failure case changes only the same root `threshold` from `1` to `2`. With unchanged provider
state, its expected result is satisfied count `1`, required count `2`, and overall `BLOCKED`. No draft or revision for
that follow-up has been created or activated.

## Boundary

The server remains on `127.0.0.1:25569`, running only the clone-specific Java executable under the two owner-managed
outbound-block firewall rules. This profile uses low qualification-only values and does not select production balance.
