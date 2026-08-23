# Quick Start: Member → Adventurer → Veteran

This is the canonical command-driven setup flow. It creates one active revision without editing YAML or SQLite. Allow
about 10–15 minutes of active administrator time; downloads, server startup, and the three-minute play demonstration
are excluded. Replace `<session>` and `<token>` with the UUIDs printed by the server.

## 1. Create the external ranks

As a LuckPerms administrator, run:

```text
/lp creategroup Member
/lp creategroup Adventurer
/lp creategroup Veteran
```

If a command says the group already exists, verify that its exact name is the intended group. MaddPrestige never
creates, deletes, or formats these groups.

## 2. Discover and start

Run as a Paper operator or subject with `maddprestige.admin.setup`:

```text
/maddprestige setup discover
/maddprestige setup start
```

Record the printed setup session UUID as `<session>`, then run these commands in order:

```text
/maddprestige setup provider <session> luckperms
/maddprestige setup stage <session> member Member Member
/maddprestige setup stage <session> adventurer Adventurer Adventurer
/maddprestige setup stage <session> veteran Veteran Veteran
/maddprestige setup baseline <session> member
/maddprestige setup requirement <session> adventurer playtime_60_seconds paper_statistics play_one_minute GREATER_OR_EQUAL PT1M SINCE_PRESTIGE_START LIVE
/maddprestige setup requirement <session> veteran playtime_180_seconds paper_statistics play_one_minute GREATER_OR_EQUAL PT3M SINCE_PRESTIGE_START LIVE
/maddprestige setup prestige <session> enabled veteran member
```

The first target is one minute and the second is three minutes of Paper's lifetime `PLAY_ONE_MINUTE` statistic measured
against the current-Prestige baseline. Costs and rewards are left empty.

## 3. Preview, acknowledge, and apply

```text
/maddprestige setup preview <session>
/maddprestige setup acknowledge <session>
```

Preview must say `Validation: VALID`. Read every diff, warning, consequence, and remediation. Record the short-lived
server acknowledgement UUID as `<token>`, then apply it:

```text
/maddprestige setup confirm <token> Initial generic progression setup
/maddprestige doctor
```

Do not use `setup apply` to bypass an acknowledgement requirement. Doctor must not report a blocked active
configuration. If it names LuckPerms or one of the three groups, correct that external dependency and start a new
preview/acknowledgement.

## 4. Grant and use player permissions

`maddprestige.use`, `maddprestige.rankup`, and `maddprestige.prestige` default to true. A least-privilege server may
grant them explicitly through LuckPerms:

```text
/lp group default permission set maddprestige.use true
/lp group default permission set maddprestige.rankup true
/lp group default permission set maddprestige.prestige true
```

A new player resolves at `member`/Member. Before one minute, `/maddprestige why rankup` reports the exact deficit and
`/maddprestige rankup` cannot change rank or history. After one minute:

```text
/maddprestige rankup
/maddprestige confirm <confirmation-id>
```

The player advances exactly to Adventurer. At less than three minutes since the current Prestige baseline, Why blocks
Veteran. At three minutes, repeat rank-up/confirm to reach Veteran. Then:

```text
/maddprestige prestige
/maddprestige confirm <confirmation-id>
```

Prestige becomes 1 and stage/group returns to Member. Paper's lifetime statistic is not reset; MaddPrestige records a
new baseline, so current-Prestige play-time progress starts at zero.

## 5. Restart check

Run `/maddprestige player`, stop Paper cleanly, start the unchanged directory, then run:

```text
/maddprestige player
/maddprestige doctor
```

Stage Member, Prestige 1, the active revision, and LuckPerms projection must remain coherent. Use
[Troubleshooting](DIAGNOSTICS_TROUBLESHOOTING.md) if any check differs.
