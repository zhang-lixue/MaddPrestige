# Phase 9D scaling runtime/preview qualification

Status: **ALL SIX SCALING CASES REAL-CLIENT PASS**

## Safety baseline

- Player: `tmydwc` / `d7551bf9-6358-3218-89c4-06c9c57dc879`
- Current/lifetime Prestige: `2 / 2`
- Next Prestige evaluated by every case: `3`
- Vault balance: `12`
- mcMMO `total_level`: `1`
- LuckPerms direct parents: `default`, `owner`
- Requirement tree: `ALL`; Vault plus unchanged mcMMO `1 / 1`
- Confirmation or Prestige execution: not authorized for this qualification

The scaling value is a multiplier applied to the requirement's configured base target. The accepted resolver selects
the segment for the target Prestige, resolves `EXPLICIT_BASE` or `CONTINUE`, applies FLAT/LINEAR/EXPONENTIAL/MANUAL,
then a per-level override, bounds, and rounding. Execution and preview consume this same compiled resolver.

## Six exact qualification profiles

All values are deliberately small and qualification-only. Exactly one profile is active at a time; later profiles
remain planned until the owner accepts the current normal client rendering.

| Case | Exact Vault scaling profile | P3 multiplier | Base target | Mathematical effective target | Compiled | Runtime | Client |
|---|---|---:|---:|---:|---:|---:|---|
| FLAT | compact `mode: FLAT`, `base: 3` | 3 | 2 | 6 | 6 PASS | 6 PASS | 6 PASS |
| LINEAR | compact `mode: LINEAR`, `base: 1`, `rate: 0.5` | `1 + 0.5 × (3-1) = 2` | 2 | 4 | 4 PASS | 4 PASS | 4 PASS |
| EXPONENTIAL | compact `mode: EXPONENTIAL`, `base: 1`, `rate: 2` | `1 × 2^(3-1) = 4` | 2 | 8 | 8 PASS | 8 PASS | 8 PASS |
| MANUAL | P1–P3 overrides `1:1, 2:2, 3:3`; P4–unlimited FLAT/CONTINUE | override 3 | 2 | 6 | 6 PASS | 6 PASS | 6 PASS |
| Segment transition | P1–P2 FLAT/base 2; P3–unlimited LINEAR/CONTINUE/rate 1 | prior P2 value 2 anchors P3 at 2 | 2 | 4 | 4 PASS | 4 PASS | 4 PASS |
| P3 override | P1–unlimited LINEAR/base 1/rate 0.5; override `3:3` | override 3, greater than derived 2 | 2 | 6 | 6 PASS | 6 PASS | 6 PASS |

For the override profile, adjacent unoverridden values remain P2 multiplier `1.5` / effective target `3` and P4
multiplier `2.5` / effective target `5`. For the transition profile, `CONTINUE` is not assumed: it is explicitly
configured, and its first P3 value anchors from the resolved P2 value as required by Phase 9B.

The per-level override document is active through a canonical structured scaling segment. Every preceding scaling
document remains retained as an immutable configuration revision and qualification evidence.

## Phase 9D structured segment command correction

Before the MANUAL round, the canonical scalar/list command surface could not materialize a scaling segment because a
segment is a structured map in a sequence. The underlying `ConfigurationAdministrationService` already owned
schema-bound structured add/edit/remove operations, but `config add` accepted only scalar list values and no
production command routed scaling segment objects to that authority. Activation therefore stopped before mutating a
draft; no manual YAML replacement or bypass was used.

The correction adds only bounded `config segment-add`, `config segment-edit`, and `config segment-remove`
adapters. They accept one requirement scaling path, typed positive Prestige bounds, canonical formula/transition
enums, non-negative exact decimals, and optional bounded MANUAL overrides, then delegate to the existing structured
mutation service. Permission-filtered command completion exposes the operations. Direct regression coverage proves
add, edit, and remove produce the exact same candidate hash and changed-document set as direct canonical structured
mutations. No scaling formula, preview, requirement, cost, reward, or transaction semantics changed.

The first live MANUAL draft then exposed a separate lossless-YAML boundary defect while appending the second segment
before the existing sibling mcMMO requirement. The failure was recorded before correction and rejected the draft
mutation safely; the active EXPONENTIAL revision remained unchanged. An exact clone-shaped regression reproduced
SnakeYAML reporting the preceding nested mapping's end mark at the indentation of the following sibling key. The
editor previously advanced to the end of that sibling's key line. The minimal correction now treats an end-mark line
prefix containing only indentation as the insertion boundary. The exact active-document regression plus the
lossless-YAML and production command/admin suites pass `41 / 41`; full clean verification passes `629` tests with no
failures, errors, or skips and zero Checkstyle violations.

The first CONTINUE draft exposed a second lossless boundary defect while replacing the first of two block-sequence
segments: using the following node's SnakeYAML start-mark line as the end boundary consumed that following segment's
standalone `-`, so the subsequent index-1 replacement failed closed with `YAML sequence index out of range: 1`.
The active MANUAL revision remained unchanged and the failed draft was discarded. The minimal correction now ends
non-final sequence replacement/removal at the exact start of the following sequence item. A clone-shaped regression
replaces both MANUAL-shaped segments, proves the sibling mcMMO requirement remains intact, and verifies first-item and
last-item removal independently. The affected production command/admin and lossless-YAML suites pass `41 / 41`; the
fresh full clean build again passes `629` tests with no failures, errors, or skips and zero Checkstyle violations.

## Active FLAT profile

- Draft: `556a8ecc-7cd5-41b4-8f55-ef1e235b2731`
- Base revision: `r_d4e2d373994f4a07a7849bc077877038`
- Active revision: `r_0764cc84c5554df2b9f2e63d7e498f74`
- Compiled content hash: `fe82a740a2e251ac911003340a52690abcf05e18a9dcb5379d5bc01e605904a3`
- Requirements document hash: `cc6c1cdbfa83e458017e1c444c88a1e9be510145a855b813c6ff04dd604f6076`
- Apply actor/reason: `CONSOLE` / `phase9d FLAT scaling runtime-preview qualification`
- Apply result: `APPLIED`

```yaml
requirements:
  phase9d_vault_balance:
    completion: LIVE
    metric: balance
    provider: vault_balance
    scope: ABSOLUTE
    target: "2"
    value-type: CURRENCY_AMOUNT
    scaling:
      mode: FLAT
      base: 3
```

The canonical workflow changed only the Vault base target and its compact scaling mode/base. An attempted removal of
the lifecycle cost list failed safely before draft mutation; it was not retried. The previously configured cost `13`
and reward `1` therefore remain visible, but no Prestige or confirmation command is part of this read-only round and
neither can execute.

`config validate` again suffered the retained Paper-console response-collapse defect. The subsequent canonical
`config acknowledge` response proved the preview was sealed, had no high-risk findings requiring acknowledgement, and
was ready for normal apply. Apply succeeded against the exact expected base revision.

## FLAT compiler/runtime/renderer agreement

At target P3, FLAT resolves multiplier `3`; configured Vault target `2 × 3 = 6`.

- Canonical configured values: target `2`, mode `FLAT`, base `3`
- Compiled detailed preview at `16:29:29`: Vault `12 / 6 — SATISFIED`, formula `scaled=base*3`
- Runtime target in detailed provenance: `6`
- Concise production renderer at `16:29:55`: `Money: 12 / 6 ✓`
- Agreement: expected `6` = compiled `6` = runtime `6` = rendered `6`

Both simulations were zero-write and created no confirmation. The state after them remained current/lifetime
Prestige `2 / 2`, Vault `12`, mcMMO `1`, and direct LuckPerms parents `default` and `owner`.

## FLAT real Minecraft-client result

At `16:36:59` the owner ran `/maddprestige simulate prestige` as the genuine non-OP player. Normal output displayed
`Prestige 2 → 3` and `Money: 12 / 6 ✓`. The mathematically expected, compiled, zero-write runtime, and client-visible
effective requirement were all exactly `6`. No Prestige transaction or confirmation was executed and player/provider
state remained unchanged.

Therefore **REAL-CLIENT FLAT SCALING / RUNTIME-PREVIEW AGREEMENT: PASS**.

## Active LINEAR profile

- Draft: `6084e487-b26e-4f46-bf36-2a2a296a5e73`
- Base revision: `r_0764cc84c5554df2b9f2e63d7e498f74`
- Active revision: `r_8c3d8dbec7dc433e8c4704abcc808244`
- Compiled content hash: `0aecf4f0ec572b2a44a7bb60c32cebac0399ad153841226f88147ff590f82811`
- Requirements document hash: `5103f64695a35f6f8a794774331962da49e85387a4a90dde8768b7a3f641b337`
- Apply actor/reason: `CONSOLE` / `phase9d LINEAR scaling runtime-preview qualification`
- Apply result: `APPLIED`

```yaml
target: "2"
scaling:
  mode: LINEAR
  base: 1
  rate: 0.5
```

At target P3 the compact segment starts at P1, so its offset is `3 - 1 = 2`. The exact multiplier is
`1 + 0.5 × 2 = 2`, and configured Vault target `2 × 2 = 4`.

- Canonical configured values: target `2`, mode `LINEAR`, base `1`, rate `0.5`
- Compiled detailed preview at `16:39:20`: Vault `12 / 4 — SATISFIED`, formula `scaled=base*2`
- Runtime target in detailed provenance: `4`
- Concise production renderer at `16:39:33`: `Money: 12 / 4 ✓`
- Agreement before owner interaction: expected `4` = compiled `4` = runtime `4` = rendered `4`

Both simulations were zero-write and created no confirmation. Post-simulation state remained current/lifetime Prestige
`2 / 2`, Vault `12`, mcMMO `1`, and direct LuckPerms parents `default` and `owner`. The configured cost/reward did not
execute.

## EXPONENTIAL real Minecraft-client result

At `16:48:45` the owner ran `/maddprestige simulate prestige` as the genuine non-OP player. Normal output displayed
`Prestige 2 → 3` and `Money: 12 / 8 ✓`. The mathematically expected, compiled, zero-write runtime, and client-visible
effective requirement were all exactly `8`. No Prestige transaction or confirmation was executed and player/provider
state remained unchanged.

Therefore **REAL-CLIENT EXPONENTIAL SCALING / RUNTIME-PREVIEW AGREEMENT: PASS**.

## LINEAR real Minecraft-client result

At `16:44:14` the owner ran `/maddprestige simulate prestige` as the genuine non-OP player. Normal output displayed
`Prestige 2 → 3` and `Money: 12 / 4 ✓`. The mathematically expected, compiled, zero-write runtime, and client-visible
effective requirement were all exactly `4`. No Prestige transaction or confirmation was executed and player/provider
state remained unchanged.

Therefore **REAL-CLIENT LINEAR SCALING / RUNTIME-PREVIEW AGREEMENT: PASS**.

## Active EXPONENTIAL profile

- Draft: `46aabf64-ee58-4bff-be7a-04b4546dc0c8`
- Base revision: `r_8c3d8dbec7dc433e8c4704abcc808244`
- Active revision: `r_6482e353ed134ed1bbec9379fe75fc94`
- Compiled content hash: `0814dc54da2b2744962262b9b50658abb2a553a7534a3a6ed7166143a75be7f3`
- Requirements document hash: `83e7ea0cfbbe6041552260b20a5f61d471f485979bed85c90bd181faee8026b2`
- Apply actor/reason: `CONSOLE` / `phase9d EXPONENTIAL scaling runtime-preview qualification`
- Apply result: `APPLIED`

```yaml
target: "2"
scaling:
  mode: EXPONENTIAL
  base: 1
  rate: 2
```

At target P3 the compact segment starts at P1, so its exponent is `3 - 1 = 2`. The exact multiplier is
`1 × 2^2 = 4`, and configured Vault target `2 × 4 = 8`.

- Canonical configured values: target `2`, mode `EXPONENTIAL`, base `1`, rate `2`
- Compiled detailed preview at `16:46:18`: Vault `12 / 8 — SATISFIED`, formula `scaled=base*4`
- Runtime target in detailed provenance: `8`
- Concise production renderer at `16:46:32`: `Money: 12 / 8 ✓`
- Agreement before owner interaction: expected `8` = compiled `8` = runtime `8` = rendered `8`

Both simulations were zero-write and created no confirmation. Post-simulation state remained current/lifetime Prestige
`2 / 2`, Vault `12`, mcMMO `1`, and direct LuckPerms parents `default` and `owner`. The configured cost/reward did not
execute.

## Active MANUAL profile

- Draft: `3e9e2224-fbd0-447f-8787-0f39ea6b7f50`
- Base revision: `r_6482e353ed134ed1bbec9379fe75fc94`
- Active revision: `r_b510e7a1b56c498eb37f90703e5d4607`
- Compiled content hash: `3a7e8c79bee73c8615e24923250c307c7a53c73d51f4452fc7de2eb52b613670`
- Requirements document hash: `cc5274b959302446163669e147c9ed28c14b0eec841acfbbea57e6a639baf15d`
- Apply actor/reason: `CONSOLE` / `Phase 9D MANUAL scaling qualification`
- Apply result: `APPLIED`

```yaml
target: "2"
scaling:
  mode: MANUAL
  base: 1
  rate: 0
  segments:
    -
      start-prestige: 1
      end-prestige: "3"
      mode: MANUAL
      transition: EXPLICIT_BASE
      base: 1
      rate: 0
      overrides:
        "1": 1
        "2": 2
        "3": 3
    -
      start-prestige: 4
      end-prestige: unlimited
      mode: FLAT
      transition: CONTINUE
      base: 1
      rate: 0
```

The repository's accepted MANUAL resolver selects target P3's explicit override `3`; configured Vault target
`2 × 3 = 6`. The P4–unlimited tail is required by the canonical segment topology and preserves the repository's
actual `CONTINUE` semantics without reinterpreting the formula.

- Canonical configured values: target `2`, mode `MANUAL`, base `1`, rate `0`, overrides P1/P2/P3 = `1/2/3`
- Compiled detailed preview at `17:29:24`: Vault `12 / 6 — SATISFIED`, formula `scaled=base*3`
- Runtime target in detailed provenance: `6`
- Concise production renderer at `17:29:34`: `Money: 12 / 6 ✓`
- Agreement before owner interaction: expected `6` = compiled `6` = runtime `6` = rendered `6`

Both simulations were zero-write and created no confirmation. Detailed simulation revalidated current/lifetime
Prestige `2 / 2`, Vault `12`, and mcMMO `1`; LuckPerms direct parents remained `default` and `owner`. The configured
cost `13` and reward `1` remained visible but did not execute.

## MANUAL real Minecraft-client result

The owner ran `/maddprestige simulate prestige` as the genuine non-OP player. Normal output displayed
`Prestige 2 → 3` and `Money: 12 / 6 ✓`. The mathematically expected, compiled, zero-write runtime, and client-visible
effective requirement were all exactly `6`. No Prestige transaction or confirmation was executed and player/provider
state remained unchanged.

Therefore **REAL-CLIENT MANUAL SCALING / RUNTIME-PREVIEW AGREEMENT: PASS**.

## Active CONTINUE-transition profile

- Draft: `d20db881-eb32-436c-b9d2-d94dc671c506`
- Base revision: `r_b510e7a1b56c498eb37f90703e5d4607`
- Active revision: `r_9218a719ab44442aad19f20c82592271`
- Compiled content hash: `cf40af340cd65da5b303c596fdb6e7085f82d379a168d85f4c20507f049a1b20`
- Requirements document hash: `8e52d5508bccd99e563504362414a79ab77456ce600c22c5a3fb553b29933414`
- Apply actor/reason: `CONSOLE` / `Phase 9D CONTINUE transition qualification`
- Apply result: `APPLIED`

```yaml
target: "2"
scaling:
  mode: LINEAR
  base: 1
  rate: 1
  segments:
    -
      start-prestige: 1
      end-prestige: "2"
      mode: FLAT
      transition: EXPLICIT_BASE
      base: 2
      rate: 0
    -
      start-prestige: 3
      end-prestige: unlimited
      mode: LINEAR
      transition: CONTINUE
      base: 1
      rate: 1
```

The next target P3 crosses from the finite P1–P2 FLAT segment into the P3+ LINEAR segment. Under the accepted
`CONTINUE` semantics, the second segment anchors its P3 value to the preceding segment's resolved P2 value `2`;
the LINEAR offset at the new segment's first level is zero. The configured Vault target is therefore
`2 × 2 = 4`.

The canonical workflow removed the two prior MANUAL segments, retained the existing sparse scaling fields, added
exactly these two typed segments, validated the resulting draft, and applied it against the exact MANUAL base
revision. The retained Paper-console response-collapse defect reduced `config validate` to `command.failed`, but
the immediate canonical acknowledgement response proved the sealed preview had zero high-risk findings and was ready
for the normal apply flow. Apply succeeded at `18:08:52`.

- Active runtime revision at `18:09:44`: `r_9218a719ab44442aad19f20c82592271`
- Canonical configured values: target `2`, P1–P2 `FLAT / EXPLICIT_BASE / base 2`,
  P3+ `LINEAR / CONTINUE / rate 1`
- Compiled detailed preview at `18:09:54`: Vault `12 / 4 — SATISFIED`, formula `scaled=base*2`
- Runtime target in detailed provenance: `4`
- Concise production renderer at `18:10:00`: `Money: 12 / 4 ✓`
- Agreement before owner interaction: expected `4` = compiled `4` = runtime `4` = rendered `4`

Both simulations were zero-write and created no confirmation. Detailed simulation revalidated current/lifetime
Prestige `2 / 2`, Vault `12`, and mcMMO `1`; console verification confirmed Vault `12` and direct LuckPerms
parents `default` and `owner`. The configured cost `13` remained blocked, the reward remained preview-only, and
no transaction or provider mutation executed.

## CONTINUE-transition real Minecraft-client result

At `18:23:31` the owner ran `/maddprestige simulate prestige` as the genuine non-OP player. Normal output displayed
`Prestige 2 → 3` and `Money: 12 / 4 ✓`. The mathematically expected, compiled, zero-write runtime, and client-visible
effective requirement were all exactly `4`. No Prestige transaction or confirmation was executed and player/provider
state remained unchanged.

Therefore **REAL-CLIENT CONTINUE SEGMENT TRANSITION / RUNTIME-PREVIEW AGREEMENT: PASS**.

## Active per-level override profile

- Draft: `937335fc-e1d1-4dba-a9cf-ec887a12f55e`
- Base revision: `r_9218a719ab44442aad19f20c82592271`
- Active revision: `r_3146674c5ca743738d3eb442758e269b`
- Compiled content hash: `873acfd9c81cce4a1ebafe184d3a398dc6d3ee313c1ddcfbfa6ae55de5939029`
- Requirements document hash: `046fb8798b11040693197fb9005bc199f5bd22f696e696cfb698b19dd6f69353`
- Apply actor/reason: `CONSOLE` / `Phase 9D per-level override qualification`
- Apply result: `APPLIED`

```yaml
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
```

The accepted resolver applies a matching per-level override after calculating the segment value. With this exact
profile, P2 has no override and resolves normally to multiplier `1 + 0.5 × (2-1) = 1.5` / effective requirement
`3`; P3's normal derived multiplier `2` is superseded by its sole explicit override `3` / effective requirement
`6`; P4 has no override and resumes the normal multiplier `1 + 0.5 × (4-1) = 2.5` / effective requirement `5`.
The adjacent values therefore retain normal segment calculation while the override has exact P3-only precedence.

The canonical workflow changed the root rate to `0.5`, removed the CONTINUE tail, and replaced the remaining segment
with this single unlimited typed segment and its sole `3=3` override. The retained Paper-console response-collapse
defect reduced `config validate` to `command.failed`, but the immediate canonical acknowledgement response proved
the sealed preview had zero high-risk findings and was ready for normal apply. Apply succeeded at `18:25:12`.

- Active runtime revision at `18:25:41`: `r_3146674c5ca743738d3eb442758e269b`
- Compiled detailed preview at `18:25:49`: Vault `12 / 6 — SATISFIED`, formula `scaled=base*3`
- Runtime target in detailed provenance: `6`
- Concise production renderer at `18:25:55`: `Money: 12 / 6 ✓`
- Agreement before owner interaction: expected `6` = compiled `6` = runtime `6` = rendered `6`

Both simulations were zero-write and created no confirmation. Detailed simulation and PlaceholderAPI revalidated
current/lifetime Prestige `2 / 2`; Vault remained `12`, mcMMO remained `1`, and direct LuckPerms parents remained
`default` and `owner`. The configured cost `13` remained blocked, the reward remained preview-only, and no
transaction or provider mutation executed.

## Per-level override real Minecraft-client result

At `18:47:50` the owner ran `/maddprestige simulate prestige` as the genuine non-OP player. Normal output displayed
`Prestige 2 → 3` and `Money: 12 / 6 ✓`. The mathematically expected, compiled, zero-write runtime, and client-visible
effective requirement were all exactly `6`. No Prestige transaction or confirmation was executed and player/provider
state remained unchanged.

Therefore **REAL-CLIENT PER-LEVEL OVERRIDE / RUNTIME-PREVIEW AGREEMENT: PASS**.

## Combined result

Real-client runtime/preview agreement is **PASS** for FLAT, LINEAR, EXPONENTIAL, MANUAL, CONTINUE segment transition,
and per-level override. Every case evaluated the same P2 → P3 transition through the accepted compiled resolver with
no Prestige transaction. Phase 9D scaling qualification is complete.
