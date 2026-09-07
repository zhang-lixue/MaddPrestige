# Deployment runbook

This is an operator checklist, not deployment authorization.

## Release artifact

Build releases with `./mvnw --no-transfer-progress clean verify`, then attach
`maddprestige-distribution/target/MaddPrestige.jar` to the versioned GitHub release. Keep release tags and titles
versioned; the public plugin filename stays stable.

## Pre-deploy

1. Stop Paper and verify no process owns the server directory.
2. Back up the prior MaddPrestige JAR and the complete `plugins/MaddPrestige/` directory, including SQLite WAL/SHM,
   configuration authority, locale, and existing backup pairs. Record hashes.
3. Verify Java 25, Paper 26.1.2 build 74, the target MaddPrestige hash, and every referenced provider/plugin version.
4. Rehearse the exact snapshot and artifacts in an isolated staging copy. Never restore production credentials to it.
5. Confirm the intended canonical revision is valid and no production balance was inferred from qualification values.

## Install and first boot

1. Install exactly one MaddPrestige JAR. Preserve provider-owned data. V1 player data is not imported; fresh V2 state
   starts at numeric Prestige 0 unless an audited V2 administrator action says otherwise.
2. Start Paper and retain the complete first-boot log. A required migration must report checked history, backup,
   integrity, restore rehearsal, and successful activation; otherwise stop.
3. Run `maddprestige status`, `maddprestige doctor details`, and `maddprestige setup discover`. Confirm the active
   revision, SQLite health, and every referenced provider generation/capability.

## Post-boot smoke test

1. Use a designated non-production test account to inspect `player`, `why prestige`, and `simulate prestige`.
2. Verify Vault/mcMMO reads where configured and parse current/lifetime Prestige through PlaceholderAPI.
3. Request—but do not blindly consume—a Prestige confirmation; verify clickable controls, shorthand ownership, and
   logout invalidation. Execute a low-risk transaction only under the approved production test plan.
4. Stop cleanly, restart unchanged, and repeat state/Doctor checks.

## Rollback

1. Stop Paper; preserve the failed directory and logs unchanged.
2. Restore the complete clean-shutdown backup, prior JAR, configuration, and database as one coherent set. Do not
   point an older binary at a newer schema and do not edit migration/pointer rows.
3. Rehearse the restored set in a disposable directory. Verify startup, status, player state, Doctor, clean shutdown,
   and unchanged restart before service selection.
