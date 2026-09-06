# Quick start: numeric Prestige

MaddPrestige V2 has one progression model: a successful operation advances numeric Prestige from `P` to `P + 1`.
The examples below use placeholder values chosen by the administrator; they do not select a MaddKraft production
balance.

## Smallest useful configuration

Every immutable revision contains five documents. Only `schema-version` and values that differ from safe defaults need
to be written. A one-minute Paper play-time example is:

`progression.yml`

```yaml
schema-version: 3
```

`requirements.yml`

```yaml
schema-version: 3
requirements:
  example_playtime:
    provider: paper_statistics
    metric: play_one_minute
    value-type: DURATION
    target: PT1M
    scope: SINCE_PRESTIGE_START
trees:
  prestige_eligibility:
    children:
      - requirement: example_playtime
```

`rewards.yml`

```yaml
schema-version: 3
```

`lifecycle.yml`

```yaml
schema-version: 4
prestige:
  enabled: true
  requirement-tree: prestige_eligibility
```

`integrations.yml`

```yaml
schema-version: 7
```

This inherits `GREATER_OR_EQUAL`, `LIVE`, unlimited maximum, zero cooldown, no costs/rewards, disabled command actions
and integrations, and the canonical safe reset policy. The complete copyable example is
[`examples/numeric-prestige`](../examples/numeric-prestige).

## Guided setup

Run as an operator or a subject with `maddprestige.admin.setup`:

```text
/maddprestige setup discover
/maddprestige setup start
/maddprestige setup requirement example_playtime paper_statistics play_one_minute GREATER_OR_EQUAL PT1M SINCE_PRESTIGE_START LIVE
/maddprestige setup prestige enabled
/maddprestige setup preview
/maddprestige setup preview details
```

The current session is owner-bound, so its UUID is optional on normal follow-up commands. `discover` reports the exact
provider and metric IDs that may be used. Normal preview is the concise effective summary; request `details` only for
compiled provenance and diagnostics. For mcMMO, use the guided `mcmmo total_level` metric; do not use deprecated
`power_level` terminology. MaddPrestige does not invent a MobCoins alias: a MobCoins plugin must advertise a canonical
provider capability before it can be referenced.

The requirements, costs, and rewards are independent. Requirements decide eligibility. Costs are consumed only by a
confirmed operation. Rewards are additive actions after eligibility and cost preflight. Add them only when needed:

```text
/maddprestige setup cost <id> <provider> <type> <value-type> <amount> <display-name>
/maddprestige setup reward <id> <provider> <type> <value-type> <value> <display-name>
```

Preview must be valid. Read every consequence. If it requires high-risk acknowledgement, use the emitted server token
and `setup confirm`; otherwise use the normal setup apply path. Then run `/maddprestige doctor`.

## Operate and inspect

Players open the Player GUI with `/prestige`. Authorized staff open the Staff Dashboard with `/maddprestige admin`; player lookup, history, Set/Reset, and guided configuration controls remain permission-scoped. The advanced `/maddprestige prestige` and `/maddprestige confirm`
forms remain available; the no-ID confirmation form requires exactly one owned confirmation. `[Copy ID]` and the
explicit UUID form remain available. Confirmations belong to the current
login session, expire on logout/restart/replacement/revision change, and revalidate state before execution; the
configurable maximum lifetime is only a secondary cap. Normal preview includes the Prestige transition,
effective requirements, costs, rewards, and blockers without provider internals. Add `details` to Doctor, Why, setup
preview, or simulation when provenance is needed; use `config diff` for the detailed draft view:

```text
/maddprestige doctor details
/maddprestige why prestige details
/maddprestige simulate prestige details
/maddprestige config get prestige.maximum
/maddprestige config explain prestige.maximum
```

`config get` shows the effective value and whether it is configured or inherited. `config explain` adds configured and
default provenance, type, reload behavior, risk, description, and allowed values.

## Advanced scaling

Most servers need one compact formula. This requirement target grows by 25 percent of its base per Prestige:

```yaml
scaling:
  mode: LINEAR
  base: 1
  rate: 0.25
```

Use advanced `defaults` plus `segments` only for real range changes. FLAT, LINEAR, EXPONENTIAL, and MANUAL remain the
same canonical model; there is no separate simple/advanced runtime. See [configuration](CONFIGURATION.md) for exact
inheritance, boundaries, transitions, rounding, floors, caps, and overrides.

LuckPerms is not progression authority. It is needed only when a configured additive reward targets an existing group.
MaddPrestige never creates that group and never removes unrelated permissions or memberships.
