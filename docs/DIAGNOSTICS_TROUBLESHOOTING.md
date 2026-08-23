# Diagnostics and troubleshooting

Start with:

```text
/maddprestige status
/maddprestige doctor
/maddprestige why rankup
```

Doctor aggregates configuration, providers, external groups, requirements, costs/rewards, database, history,
operations, reconciliation, cache/scheduler, and optional integration state. Findings include a stable machine code,
exact path/identity, consequence, and remediation. Why uses the same authorization plan as execution and has no side
effects.

## Common blocked states

- **LuckPerms absent/unhealthy:** install/enable the exact supported artifact, then restart. A rank-projected revision is
  not healthy without it.
- **Group missing:** create the exact external group named by Doctor with LuckPerms, then preview/apply again.
  MaddPrestige will not create it.
- **Provider/metric unavailable:** restore the named plugin/service or remove its reference in a validated draft.
- **Insufficient play time:** Why reports the current-Prestige deficit; do not edit the database or Paper statistic.
- **Validation blocked:** correct the exact canonical path. No partial revision was published.
- **Acknowledgement required:** read the preview consequence, run the matching acknowledge command, and confirm the
  fresh server token before expiry.
- **Uncertain/reconciliation state:** stop repeating consequential commands; preserve logs and operation ID. The system
  will not claim an unverified external effect succeeded or failed.
- **Database/migration failure:** stop the server and preserve the complete data directory. Do not replace only the
  SQLite file or rewrite schema history.

## Locale reload

Edit only `plugins/MaddPrestige/locale.yml` or a selected file under `plugins/MaddPrestige/locales/`, save UTF-8, then
run `/maddprestige locale reload`. Invalid YAML, oversized/unbounded values, unsafe path selection, or malformed
MiniMessage rejects the whole candidate and retains the previous known-good catalog. Selected-locale omissions fall
back to built-in `en_US`; an unknown key has a visible bounded `[message:key]` diagnostic instead of a crash.

When reporting an issue, include supported versions, artifact hashes, the stable diagnostic/result code, bounded log
context, active revision ID, and whether the player was online. Remove secrets and do not attach a production database
without explicit secure handling.
