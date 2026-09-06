# V2 configuration administration

MaddPrestige activates a complete immutable revision, never a partially edited live file. Unknown or malformed paths
block publication. Never edit `plugins/MaddPrestige/configuration/active` or SQLite directly.

## Required documents, optional sections

The immutable revision/bootstrap contract requires all five documents. Their gameplay sections are optional and inherit
safe defaults:

| Document | Required content | Important inherited behavior |
|---|---|---|
| `progression.yml` | `schema-version: 3` | no active stage/rank progression fields |
| `requirements.yml` | `schema-version: 3` | no definitions/trees/costs; maximum tree depth 16 |
| `rewards.yml` | `schema-version: 3` | no rewards; external command actions disabled with the safe policy |
| `lifecycle.yml` | `schema-version: 4` | numeric Prestige disabled, unlimited maximum, zero cooldown, no costs/rewards/scaling |
| `integrations.yml` | `schema-version: 7` | every optional integration disabled; CraftEngine reward quantity cap 2304 |

Empty maps and disabled integration blocks should be omitted. The canonical editor materializes an omitted scalar or
list only when an administrator explicitly changes it. `config get` resolves an omitted scalar through schema defaults;
`config explain` shows configured, inherited, and effective provenance.

The safe Prestige reset defaults are:

- RESET: active requirement progress, latched completions, saved baselines, Prestige-scoped currency.
- PRESERVE: purchased perks, milestone history, season progress, historical statistics.

Archived stage-era state is outside the production authoring schema and is always non-authoritative; normal
configuration does not expose stage reset or reconciliation controls.

Write only reset-policy entries that intentionally differ. Provider-owned values remain external and are never mirrored
into configuration.

## One canonical definition model

A requirement reads a typed provider metric and decides eligibility. A requirement never consumes its target. A cost
names a provider-owned value consumed only by a confirmed operation. A reward is an independent additive action. The
guided Money editor is a bounded exception: it intentionally keeps the Money requirement and consumed cost paired at
one canonical effective amount, including scaling and overrides. Advanced YAML may still model independent requirements
and costs. The Prestige lifecycle references their stable IDs:

```yaml
prestige:
  enabled: true
  requirement-tree: prestige_eligibility
  costs: [money_cost]
  rewards: [permission_reward]
```

Requirements combine through `ALL`, `ANY`, or `X_OF_N`. `ALL` is inherited when omitted. Guided mcMMO configuration
uses `mcmmo:total_level`; deprecated `power_level` is not advertised. LuckPerms group rewards target groups that already
exist and preserve every unrelated membership. MaddPrestige does not create LP groups.

The canonical requirement fields are `scope` and `completion`; schema-driven edits and runtime compilation consume
those exact paths. A root requirement tree takes its ID from the `trees` map key, so sparse output omits a redundant
inner `id`. An explicitly supplied root `id` remains readable only when it exactly matches that key. Nested group IDs
remain explicit when stable addressing is useful.

## Scaling and inheritance

Requirements, costs, and rewards use the same deterministic segmented scaling model. Resolution order is:

1. global safe formula defaults;
2. optional profile `defaults` for that requirement, cost, or reward;
3. the selected range/segment;
4. a per-Prestige `overrides` value.

The global defaults are FLAT, EXPLICIT_BASE, base 1, rate 0, EXACT rounding, and quantum 1, with no floor or cap. A
compact profile is one range from Prestige 1 through unlimited:

```yaml
scaling:
  mode: LINEAR
  base: 1
  rate: 0.5
  rounding: CEILING
```

Advanced ranges use the same model:

```yaml
scaling:
  defaults:
    rounding: HALF_UP
    quantum: 1
  segments:
    - end-prestige: 10
      mode: LINEAR
      base: 1
      rate: 0.5
    - mode: EXPONENTIAL
      transition: CONTINUE
      rate: 1.10
      cap: 25
      overrides:
        25: 20
```

The first omitted `start-prestige` is 1. A later omitted start is the prior finite end plus one. The final omitted end is
unlimited. A non-final omitted end is inferred only from the next explicit start; otherwise validation blocks the
ambiguous profile. `CONTINUE` anchors at the preceding resolved value. MANUAL requires a finite range and one override
for every covered level. Floors/caps apply before rounding and are rechecked after rounding. Preview uses the exact same
resolver as execution.

Use the compact form unless behavior actually changes by range. Fully explicit segments remain valid and retain Phase
9B semantics. The older strategy syntax remains compatibility input, not a second product model and not the normal
authoring path.

## Draft workflow

1. `config draft` creates a private resumable draft and returns `[Copy Draft ID]`, `[Validate]`, `[Diff]`, and
   `[Cancel]` actions. Completion exposes only retained drafts owned by the authorized actor.
2. `config get`, `config explain`, `config search`, and `config list` inspect effective schema-owned paths.
3. `config set`, `config add`, and `config remove` edit scalar/list paths. Scaling segments use the bounded
   `config segment-add`, `config segment-edit`, and `config segment-remove` commands; each accepts typed
   start/end, formula, transition, base/rate, and optional `level=value,...` MANUAL overrides. These commands and
   visual editors use the same schema-confined administration service, which also creates, replaces, and removes
   structured draft objects such as requirement groups, costs, rewards, milestones, currencies, scaling segments,
   and overrides. New object types require a registered canonical schema before any command or visual editor may
   expose them.
4. `config validate <draft-id>` gives a concise result; `config diff <draft-id>` adds hash, provenance, every finding,
   and semantic diff.
5. A risky change uses `config acknowledge <draft-id>` and `config confirm <token> <reason>`.
6. A non-risky change may use `config apply <draft-id> <expected-revision|none> <reason>`.
7. `config history` and `config rollback <revision-id>` preserve append-only history.

A stale base revision, provider generation change, missing capability, absent acknowledgement, or validation error fails
closed and leaves the last known-good revision active.

## Confirmation and compatibility policy

Player confirmations are login-session-bound and are cleared on logout, restart, replacement, or configuration
revision change. Execution consumes an owned ID once and revalidates requirements, cost/provider state, target,
identity/session, revision, and authorization. `prestige.confirmation-maximum-lifetime` is an optional secondary cap:
it defaults to `PT12H` and accepts `PT1H` through `P7D`; it is not a short primary UX timer.

PlaceholderAPI output/input composition accepts exact plugin versions 2.12.2 and 2.12.3. Unlisted versions fail
closed. Guided mcMMO authoring advertises `total_level`; `power_level` remains deprecated compatibility input only.

## Locale configuration

`plugins/MaddPrestige/locale.yml` is UTF-8 and contains `locale: en_US` by default. Optional overrides go in
`plugins/MaddPrestige/locales/<locale>.yml`. Run `/maddprestige locale reload`. A catalog is published atomically only
after complete validation; missing selected keys fall back to built-in `en_US`.
