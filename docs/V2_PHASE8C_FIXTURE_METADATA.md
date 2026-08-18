# Phase 8C representative fixture metadata

This document describes deterministic synthetic authority only. It contains no live MaddKraft player, configuration,
credential, path or plugin data.

## JVM migration fixture

- Fixed UTC time: `2026-08-17T20:00:00Z` where the selected historical table supports it.
- UUIDs are reserved synthetic values with a Phase 8C prefix.
- Stage ID: generic `novice`; Prestige scope: generic `global`.
- Exact decimal examples are stored as canonical text and compared byte-for-byte after migration.
- Populated structures are added only when introduced by the selected schema prefix.

Representative categories:

| Category | Preserved evidence |
|---|---|
| Player progression | UUID, stage, state revision, configuration revision, timestamps |
| Prestige | current/lifetime counts, scope, state revision, last-prestiged time |
| Currency | account balance and append-only ledger exact decimals/provenance |
| Requirement state | baseline, latch, manual value/version and scope fingerprints |
| Operation | durable operation UUID, separate request/idempotency evidence, terminal or reconciliation state |
| Action/recovery | action index/provider/state including UNCERTAIN; recovery decision/detail |
| Configuration | revision graph, application status, exact document bytes/hashes/canonical hash |
| Stage transitions | remap operation/entries, lease owner/token/participation, declared scope/reservations |

## Real Paper historical input

The disposable Paper process first used the accepted Phase 8B first-party setup harness to create a generic two-stage
configuration and one player that completed rank-up and Prestige. No third-party binary or generated runtime data is
retained in review artifacts.

After a clean shutdown, the first-party `Phase8CFixtureDowngrader`:

- used fixed UTC time `2026-08-17T20:30:00Z`, distinct from the JVM fixture time;

- required a current schema-11 database;
- removed only migration-11 history to form a declared schema-10 prefix;
- inserted synthetic stage-only player `8c000000-0000-0000-0000-000000000001` at `novice`, state revision 17;
- inserted synthetic operation `8c000000-0000-0000-0000-000000000002` as `NEEDS_RECONCILIATION`;
- inserted its one UNCERTAIN external action and one recovery event;
- used prepared parameters for every fixture value;
- required `PRAGMA integrity_check=ok` before commit;
- wrote only sanitized identities needed by the qualification marker.

Production startup then migrated schema 10 to 11. The backfill created exactly one zeroed Prestige pair for the
stage-only player without changing the stage row or its revision. The harness separately proved the earlier player's
stage/Prestige identity survived, and proved the uncertainty evidence survived without external replay. It then used
the public service for a real rank-up and Prestige and sealed only exact generic state/revision/count values for the
unchanged restart.

## Handling and exclusion policy

- Fixture databases, Paper runtime directories, worlds, logs with environmental paths and third-party JARs remain under
  ignored disposable build output and are not bundled.
- The owner bundle includes this metadata, sanitized pass/fail evidence and first-party Java/YAML/POM source only.
- Phase 9, not this fixture, owns any MaddKraft clone, mapping and production-deployment qualification.
