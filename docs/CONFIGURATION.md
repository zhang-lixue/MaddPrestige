# V2 configuration administration

MaddPrestige activates a complete immutable revision, never a partially edited live file. The canonical documents are
`progression.yml`, `requirements.yml`, `rewards.yml`, `lifecycle.yml`, and `integrations.yml`. Their supported schema is
the authority; unknown or malformed paths block publication with path-specific findings.

Use `/maddprestige setup ...` for a first stage-free numeric Prestige configuration. Use `/maddprestige config ...`
for later expert changes:

1. `config draft` creates a private resumable draft.
2. `config get`, `config explain`, `config search`, and `config list` inspect schema-owned paths.
3. `config set`, `config add`, and `config remove` edit active numeric Prestige paths in the draft.
4. `config validate <draft-id>` previews validation and semantic impact without publication.
5. A risky change uses `config acknowledge <draft-id>` and `config confirm <token> <reason>`.
6. A non-risky change may use `config apply <draft-id> <expected-revision|none> <reason>`.
7. `config history` and `config rollback <revision-id>` preserve append-only history; rollback creates a new revision.

Never edit `plugins/MaddPrestige/configuration/active` or SQLite. A stale base revision, changed provider generation,
missing referenced provider/reward, absent acknowledgement, or validation error
fails closed and leaves the
last known-good revision active.

The active generic profile is [`examples/numeric-prestige`](../examples/numeric-prestige). It has no stage ladder,
LuckPerms dependency, costs, rewards, milestones, currency, or production values. The older
`member-adventurer-veteran` profile is retained only as compatibility evidence.

## Locale configuration

`plugins/MaddPrestige/locale.yml` is UTF-8 and contains `locale: en_US` by default. Optional overrides go in
`plugins/MaddPrestige/locales/<locale>.yml`. Run `/maddprestige locale reload` with
`maddprestige.admin.locale.reload`. A catalog is published atomically only after complete validation; a rejected reload
retains the previous catalog. Missing selected keys fall back to built-in `en_US`.
