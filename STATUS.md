# MaddPrestige V2 Status

**Current phase:** Phase 0 approved; repository/baseline preparation complete  
**Last updated:** 2026-08-15  
**Implementation status:** Phase 1 not started; explicit subsequent authorization required

## Outcome

The owner approved the Phase 0 audit and architecture proposal on 2026-08-15 subject to the binding decisions recorded in `DECISIONS.md`. The existing MaddPrestige repository, retained Paper runtime, release artifact, configuration, SQLite schema, installed integration JARs, dependency graph, commands, GUIs, permissions, API, and tests were audited.

The complete deliverable is [docs/V2_PHASE0_AUDIT.md](docs/V2_PHASE0_AUDIT.md). Architecture recommendations and owner decisions are tracked in [DECISIONS.md](DECISIONS.md).

## Dedicated repository and baseline

- dedicated repository: `C:\Users\zhang\Documents\MC Development\MaddPrestige`;
- baseline branch: `main`;
- annotated baseline tag: `v1.2.0-baseline`;
- baseline commit: the commit referenced by that tag;
- V1 source/tests/resources/docs at their original paths: unchanged;
- existing release artifacts under `dist/`: unchanged and tracked;
- local `runtime-test/` and Maven `target/`: retained locally and ignored;
- selected V1 config, empty test DB, runtime/plugin hash evidence, and separately supplied JAR/config: tracked under `baseline/v1/`;
- master specification and kickoff prompt: tracked under `docs/spec/`;
- Phase 0 audit/decisions/status and audit helpers: tracked in the project.

The repository preparation added documentation, evidence copies, checksums, and Git metadata only. It did not modify V1 Java, tests, Maven metadata, bundled resources/configuration, existing V1 documentation, or release JAR bytes.

## Verification completed

- clean temporary-copy `mvn clean verify`: **passed**;
- main Java files compiled: 47;
- test Java files compiled: 4;
- tests: 7 run, 0 failures, 0 errors, 0 skipped;
- dependency tree: resolved and audited;
- release/download JAR identity: byte-identical SHA-256;
- retained Paper startup log: inspected;
- relevant installed plugin descriptors/API signatures: inspected;
- V1 configuration parser probe: source, runtime, and Downloads YAML all reduce the five dotted patron permission keys to `{maddkraft=0}`;
- runtime SQLite copy: integrity `ok`, zero foreign-key findings, schema inventory complete.

The available SQLite file contains no player/progress/transaction/audit records. Per the owner decision, this does not block Phase 1: it is frozen as a legacy fixture and synthetic edge-case migration fixtures may be created only after Phase 1 authorization. Any real production data located later remains untouched and requires backup before future migration work.

## Immediate blockers identified

1. V1 creates missing LuckPerms groups at startup.
2. Staff GUI view permission also authorizes mutation.
3. new-season legacy-star conversion can double-credit cached players and is not idempotent.
4. Bukkit config saves destroy round-trip quality and collapse dotted permission keys, breaking intended patron-entitlement parsing.
5. fixed obsolete ranks and MaddKraft concepts exist throughout Java, YAML, database, commands, API, and UI.
6. rank/prestige/reward operations lack a complete recoverable per-action journal.
7. database versioning is not a deterministic migration system.
8. QuickShop P2P volume counts by default at weight 0.25.

## Binding Phase 1 baseline decisions

- Java 25 and Paper 26.1.2 only; no broader compatibility claim without qualification;
- Maven group `net.maddkraft`, Java root package `net.maddkraft.maddprestige`;
- Maven retained with the approved seven-module direction;
- LuckPerms default reconciliation `warn-only`, Wanderer/default `projection: none`, and groups are never created;
- canonical file split and lowercase immutable `a-z0-9._-` IDs approved;
- YAML library remains unselected until the Phase 1 round-trip spike/golden-test gate;
- SQLite is the default single-server backend; every publicly claimed MySQL/MariaDB backend requires contract testing and implies no network safety;
- external command actions and competitions are disabled in the generic default;
- protected retention/redaction and future player-data export/deletion/anonymization requirements shape persistence from Phase 1.

## Stop point

Repository/baseline preparation is complete. No V2 module, foundation implementation, refactor, synthetic migration fixture, player-data migration, production deployment, server data, or LuckPerms state was changed. Phase 1 will not begin until the owner explicitly authorizes it in a subsequent task.
