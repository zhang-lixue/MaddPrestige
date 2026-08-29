# Quick start: numeric Prestige

MaddPrestige starts dormant. The guided setup can create numeric Prestige without a stage ladder or LuckPerms.
Server-specific thresholds and economic amounts below are placeholders chosen by the administrator; this guide does
not define a MaddKraft production balance.

Run as an operator or a subject with `maddprestige.admin.setup`:

```text
/maddprestige setup discover
/maddprestige setup start
```

Choose a provider-advertised metric and configure a typed requirement. The generic form is:

```text
/maddprestige setup requirement <id> <provider> <metric> <operator> <target> <scope> <completion>
```

For mcMMO Prestige, select the advertised `mcmmo total_level` metric rather than an individual skill. Add a cost only
when something should actually be consumed; the requirement threshold and cost amount are independent:

```text
/maddprestige setup cost <session> <id> <provider> <type> <value-type> <amount> <display-name>
```

An optional every-Prestige reward uses:

```text
/maddprestige setup reward <session> <id> <provider> <type> <value-type> <value> <display-name>
```

Enable the numeric lifecycle with no rank/reset arguments:

```text
/maddprestige setup prestige enabled
/maddprestige setup preview
```

Preview must be valid. Read every consequence. If the preview requires a high-risk acknowledgement, use the emitted
token and `setup confirm`; otherwise use the normal setup apply path. Then run `/maddprestige doctor`.

Players use `/maddprestige prestige` and confirm the returned ID. A successful transition changes only numeric
Prestige plus explicitly configured MaddPrestige scopes/costs/rewards. Stop and restart the unchanged server, then run
`/maddprestige player` and `/maddprestige doctor`; the numeric value must be unchanged.

LuckPerms is not required unless a configured action targets it. An LP group reward references an already existing
group. MaddPrestige never creates that group and never removes unrelated permissions or memberships.
