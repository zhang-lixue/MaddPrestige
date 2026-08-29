# MaddKraft legacy migration mapping and rollback contract

## Truth boundary

The repository contains a frozen V1 schema-1 fixture, not production MaddKraft player data. Its
`baseline/v1/runtime/maddprestige.db` is 118,784 bytes with SHA-256
`24AF22D780CBD5CF55B3089090FD2E4C5158477851F9D80978923014A07D89A4`; its config is 16,749 bytes with SHA-256
`5E471E20DCE60312D19D7C49872B2C677533D5CB4C8464A5A47A81EA20C014B8`. Read-only inspection found one `schema_info`
row, one `seasons` row, the singleton empty `hatter_holder`, and zero rows in every player, ledger, transaction,
contest, grant, audit, and preference table. It cannot prove a real player-data migration.

`qualification/phase9a/maddkraft-clone/legacy-migration-mapping.yml` is an owner-review template, not an executable
manifest. Execution remains `blocked` while any `OWNER_DECISION_REQUIRED` value exists. No V1-to-V2 mutation executor
is accepted yet; Phase 9A deliberately does not invent one before the data and meanings are approved.

## Exact V1 data inventory

| V1 table/fields | Semantic category | Default disposition | Reason |
|---|---|---|---|
| `schema_info(version)` | schema authority | preserve/archive; validate only | V1 version is evidence, not a V2 migration-history row. |
| `seasons(id, number, display_name, status, starts_at, ends_at, created_at)` | season/history | explicit mapping or archive | Current V2 season IDs/scopes and reset policy must be owner-approved. |
| `player_lifetime(uuid, legacy_stars, lifetime_prestiges, claim_perk_level, selected_title, last_seen_at)` | lifetime state/entitlements/metadata | field-by-field explicit mapping | Prestige count may be semantically usable; stars, perk levels, and titles have no safe automatic destination. |
| `player_season(uuid, season_id, progression_rank, prestige_level, tea_leaves, homes_perk_level, listings_perk_level, claim_perk_level, last_prestige_at, joined_at, updated_at)` | current stage, Prestige, currency, entitlements | explicit mapping; block affected player when unresolved | Old fixed ranks do not map cleanly to six stages; currency/perk meaning requires explicit V2 IDs and merge policy. |
| `run_ledger(uuid, season_id, server_earnings, mcmmo_xp, rabbit_holes, decree_objectives, bosses, updated_at)` | provider progress/snapshots | preserve/archive only by default | Source attribution/baselines are insufficient; P2P credit and obsolete objectives make silent import unsafe. |
| `prestige_transactions(id, uuid, season_id, target_prestige, state, fee, snapshot, error, created_at, completed_at)` | pending/recovery/history | archive and block affected pending cases | V1 text snapshots and states are not V2 operation/action journals. Never import them as executable work. |
| `hatter_contests`, `hatter_scores`, `hatter_holder`, `hatter_history` | obsolete MaddKraft competition | preserve/archive only | MaddHatter identity is not V2 core; no destination exists without a future generic competition owner decision. |
| `pending_patron_grants(username_key, supplied_name, tier, created_at)` | commercial/supporter fulfillment | preserve/archive and resolve externally | MaddPrestige V2 must not recreate or own old patron groups. Pending grants need owner/store review. |
| `admin_audit(actor, action, target, details, created_at)` | historical audit | immutable archive; optional separately approved import | Preserve provenance, but do not forge V2 typed actors/revisions/operation IDs. |
| `player_preferences(uuid, menu_sounds, compact_numbers, show_integration_status, default_page, updated_at)` | UI preferences | explicit compatible-field mapping or archive | Only map fields that still exist with identical meaning; obsolete pages are not guessed. |

## Deterministic field decisions

| Source category | Allowed decision | Required evidence before decision | Automatic behavior |
|---|---|---|---|
| `progression_rank` | map each distinct opaque value to one enabled ordered V2 stage ID | source distinct values, exact target profile, owner signature | No default. Missing/conflicting/invalid targets block all affected rows. |
| `prestige_level` | map to V2 current Prestige, reset to zero, or archive | active-season meaning and owner policy | No default; bounds and stage/Prestige pairing must validate. |
| `lifetime_prestiges` | map to V2 lifetime Prestige or archive | confirm V1 value is authoritative and nonnegative | No silent max/sum with seasonal value. |
| `tea_leaves` | map to a configured internal currency ID with exact 1:1 or explicit conversion, or archive | approved V2 currency ID, decimal policy, conversion, balance policy | No built-in Tea Leaf identity; no mapping until approved. |
| perk levels / entitlements | map to named V2 entitlement contribution, convert, or archive | destination ID, value type, merge strategy, source priority, supporter coexistence | No old permission/group creation and no silent listing-limit translation. |
| `legacy_stars` / selected title | map to explicit future entitlement/history ID or archive | named destination and display/lifecycle policy | Archive by default. |
| V1 run ledger | import only to a named manual/provider metric with provenance and baseline semantics, otherwise archive | trusted source definition, units, scope, monotonicity, reset, exploit review | Archive by default; old money/mcMMO/objective totals do not seed active requirements. |
| seasons | map one source season to an explicit V2 season/scope or archive all | owner selection and reset/preserve policy | No automatic selection based on latest row. |
| completed Prestige transaction history | archive or separately translate to history-only rows | terminal state semantics and actor/config provenance policy | Never creates V2 actions or rewards. |
| pending/needs-recovery transactions | manually resolve in restored V1 clone, or quarantine player | transaction state, balance, LP membership, source snapshot, operator decision | Block affected migration; never retry/refund automatically. |
| supporter/staff/other LP groups | preserve external state | LP export before/after | Never part of the mapping manifest's managed progression set. |

The accepted `LegacyStageMigrationPlanner` covers only the first row above. It seals a manifest revision, validates
enabled ordered target stages, rejects missing/ambiguous mappings, requires verified backup metadata for non-dry
plans, and returns an empty planned map on any error. Phase 9A extended read-only detection to the actual nested V1
config shape; it did not add mutation.

## Legacy configuration inventory

| V1 config area | Disposition |
|---|---|
| `database.file`, flush | archive; V2 uses `maddprestige-v2.sqlite` and its own production persistence settings |
| `integrations.luckperms.progression-groups` | explicit stage mapping input; never create `odd`, `mad`, `unbound`, or any missing current group |
| `progression.ranks` | requirements/cost prototypes only; do not activate as current MaddKraft policy |
| `prestige` fees, requirements, scaling, cooldown, Tea Leaf cadence, discounts, milestones | archive as historical prototype; owner must approve a new balance separately |
| `integrations.installed-stack` weights | discard as active policy; QuickShop 0.25 is forbidden, MobCoins remain distinct, EconomyShopGUI is currently diagnostics-only |
| `rewards.base` and prestige perks | explicit entitlement mapping only; `auction-listings` does not silently become a QuickShop limit |
| `rewards.patron-permissions`, `patron.tiers` | archive/read-only reference; commercial groups remain external and are never recreated |
| `season` and catch-up | explicit future policy or archive; no global reduction is activated |
| `maddhatter` | archive; no V2 Court/competition ownership |
| `relationships.providers`, commands, reflection-style event hooks | discard/replace with accepted typed providers or reviewed command fallbacks; never migrate arbitrary reflection |
| `gui`, `messages`, MaddKraft branding | archive/reference only; V2 canonical locale/UI configuration is separate |
| world/chapter lifecycle commands | discard from MaddPrestige; external world systems remain owners |

## Source inventory and manifest procedure

With Paper stopped and the clone checkpoint immutable:

1. Hash the complete source directory inventory and record files, sizes, timestamps, and SHA-256 values.
2. Record V1 DB file and sidecars, SQLite header, `PRAGMA integrity_check`, `PRAGMA foreign_key_check`, schema SQL,
   schema version, table counts, and all distinct enum/state/rank/tier values. Any integrity failure stops migration and
   routes to recovery from an earlier known-good source backup.
3. Hash the legacy config bytes before parsing. Run `LegacyStageDetector` read-only and reconcile its detected stage
   values with `SELECT DISTINCT progression_rank`.
4. Export the LP groups/users relevant to every source UUID, including permanent, inherited, contextual, temporary,
   supporter, staff, and unrelated nodes. Hash the export.
5. Copy the review template to a run-specific manifest, replace every owner decision, and add source directory hash,
   DB/config hashes, source row counts, target profile hash, candidate JAR hash, decision-log reference, operator, and
   creation time. Hash and sign/freeze the manifest before dry run.
6. The dry-run report must enumerate every source row by category, exact destination or archive decision, skips,
   blockers, warnings, and expected destination counts. Aggregate-only output is insufficient.

## Pre-migration backup contract

The server must be cleanly stopped before capturing the legacy source. A backup is accepted only when all of these are
true:

- the entire `plugins/MaddPrestige/` source directory and any plugin-local state named by the owner are captured;
- the destination is a new controlled path outside the source tree and production service path;
- no symlink/reparse-point or path escape is present;
- DB/config/sidecar sizes and SHA-256 values match a strict manifest;
- the source DB and the copied DB pass read-only integrity and foreign-key checks;
- config bytes and every copied file match the manifest;
- a second copy is restored into a disposable clone and V1 can read the expected schema/counts without mutation;
- the backup, manifest, migration mapping, LP export, and generated report are retained together;
- failure or partial copy removes/quarantines only the new incomplete destination and never changes the source or a
  prior accepted backup.

The V2 `SqliteBackupService` remains mandatory for later V2 schema migrations but cannot be presented as the legacy
directory backup: it validates V2 schema/history and is composed only for `maddprestige-v2.sqlite`. The generic
`FileBackupService` is valid only for already quiesced fixture files; the Phase 9 operator/host boundary must prove
the source is stopped.

## Migration execution contract

A future executor may run only after the owner has accepted the manifest and dry-run report. It must:

- open the source read-only and verify its complete fingerprint again;
- require the accepted backup/restore-rehearsal identity;
- target a fresh/restored clone V2 DB and one exact configuration revision;
- operate in deterministic UUID/key order with exact decimals and bounded values;
- validate every LP target exists before any player mutation and expose no group-creation operation;
- keep unrelated LP memberships out of the write set and verify before/after equality;
- persist a unique migration run ID, source/manifest/candidate hashes, every decision, result, skip, and blocker;
- commit internal state atomically where possible and record external LP uncertainty honestly;
- either be idempotent for the identical run identity and inputs or reject repetition before mutation;
- reject changed source, mapping, configuration, provider generation, or prior destination state;
- leave unresolved/failed players blocked and visible rather than silently defaulted.

Because that executor is currently absent, actual clone migration is a production-readiness blocker, not work hidden by
the Phase 9A plan.

## Post-migration reconciliation

For each mapped UUID, compare source decision, target stage/Prestige/currency/entitlements/history, LP managed group,
unrelated LP memberships, and audit/run report. Compare source and destination aggregates by category, then sample all
exceptional/pending/unmapped players and at least owner-approved normal players. Run Doctor/Why, restart unchanged,
repeat the comparison, and ensure no operation/reward/cost replay occurred.

The run is rejected if any source row lacks a report disposition, any destination appears without a source/explicit
initialization reason, any unrelated LP node changes, a missing group is created, a pending V1 transaction is replayed,
or the second boot changes migration results.

## Rollback criteria and procedure

Rollback immediately on backup/manifest mismatch, source fingerprint drift, unresolved affected mapping, V2 startup
failure, schema/integrity failure, unexpected LP membership change, count mismatch, provider/reconciliation ambiguity,
unexplained console error, duplicate effect, or any failed readiness gate with uncertain data impact.

1. Stop the clone and preserve its failed directory, logs, DB/config, LP export, audit, and report read-only for review.
2. Do not copy individual old files into the failed V2 directory. Move the failed clone aside and restore the complete
   pristine stopped-server checkpoint to a new clone path.
3. Verify the restored directory, legacy DB/config hashes, SQLite integrity/counts, LP export, plugin inventory, and
   network isolation against the accepted backup manifest.
4. Boot only after the restored inputs match. If proving V1 rollback behavior is required, boot V1 only in the
   isolated clone and never beside V2.
5. Record the rollback reason, failed run ID, restored backup ID, operator/time, verification results, and whether any
   external test endpoint required independent cleanup.

No Phase 9 rollback authorizes production changes. Promotion to production is a separate later owner action.

## Owner decisions required before clone mutation

1. Map each actual distinct legacy progression value to `wanderer`, `curious`, `dreamer`, `tea_guest`,
   `wonderlander`, `madcap`, or archive/reject.
2. Decide current and lifetime Prestige treatment, including source season selection.
3. Decide whether Tea Leaf balances map to a named V2 currency and at what exact conversion.
4. Decide each homes/listings/claim perk disposition and entitlement merge policy.
5. Decide whether any run-ledger metric is trustworthy enough for history-only import; active progress defaults to no.
6. Resolve every pending V1 Prestige transaction and pending patron grant outside automatic migration.
7. Decide season, legacy star, selected title, preference, and historical audit/competition archive policy.
8. Approve the later executor/report implementation against the frozen manifest format.
9. Separately approve actual MaddKraft requirements/rewards/balance; migration approval does not activate them.
