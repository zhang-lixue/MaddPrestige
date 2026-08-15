# MaddPrestige V2 Status

**Current phase:** Phase 1 generic foundations implemented and locally verified; Phase 2 not started
**Last updated:** 2026-08-15
**Implementation status:** Phase 1 owner-review correction pass complete locally, pending owner acceptance and the first observed green CI run for both external-backend harness jobs

## Outcome

Phase 1 established the approved seven-module V2 foundation alongside the frozen V1 baseline. The V2 code is isolated under Maven group `net.maddkraft` and Java root package `net.maddkraft.maddprestige`; it is not wired into the production plugin bootstrap, real player progression, production configuration, production databases, or LuckPerms.

The principal handoff documents are:

- [docs/V2_ARCHITECTURE.md](docs/V2_ARCHITECTURE.md);
- [docs/V2_PHASE1_IMPLEMENTATION.md](docs/V2_PHASE1_IMPLEMENTATION.md);
- [docs/V2_TRACEABILITY.md](docs/V2_TRACEABILITY.md);
- [DECISIONS.md](DECISIONS.md).

## Implemented foundations

- reproducible Java 25 Maven reactor and wrapper for `maddprestige-api`, `maddprestige-core`, `maddprestige-persistence`, `maddprestige-platform-paper`, `maddprestige-integrations`, `maddprestige-testkit`, and `maddprestige-distribution`;
- Maven Enforcer dependency convergence, Checkstyle, JaCoCo, aggregate CycloneDX SBOM, optional OWASP audit profile, CI, and deterministic distribution output;
- immutable generic IDs, exact decimals, structured results/errors, validation levels, explanation trees, provider descriptors/health, operation/action states, and redaction-aware audit values;
- schema registry plus representative safe-default fields;
- lossless YAML document foundation selected only after golden-file proof;
- immutable configuration drafts, compiled candidates, semantic diffs, revisions, hashes, backup metadata, active references, and new-revision rollback;
- read-only external-group validation, managed-membership isolation, safe rank projection defaults, provider registry generation checks, and fake providers;
- operation/action state-transition validation and persistence contracts;
- deterministic SQLite migrations, repository implementations, verified-backup gate, exact-text decimals, UTC timestamps, constraints, indexes, and append-only redacted audits;
- MySQL and MariaDB contract harness jobs without a support claim;
- V1 behavior/defect fixtures and characterization tests;
- A01-A76 traceability classification.

## Owner-review correction pass

- migration preflight now inspects pending/applied state without mutation and obtains the required verified backup before any history DDL;
- SQLite history uniqueness applies only to successful versions, while repeated failed attempts remain independently auditable and retryable;
- applied history must be an exact ordered prefix; gaps, newer-without-older history, unknown versions, and checksum mismatches fail before SQL executes;
- successful commit and autocommit restoration are separated so a post-commit restoration error cannot create a false `FAILED` record;
- all YAML scalar replacement methods reject block scalars; code-point mark conversion, CRLF, emoji, ambiguous strings, anchors, and tags are regression-tested;
- final A01-A76 classification now distinguishes fully satisfied final criteria from Phase 1 foundations;
- MySQL/MariaDB CI jobs require an unskipped, successful Failsafe XML result for `ExternalBackendContractIT`.

## Verification completed

- `mvn --no-transfer-progress clean verify`: **passed**;
- reactor tests: **50 run, 0 failures, 0 errors, 0 skipped**;
- Maven Enforcer and dependency convergence: **passed**;
- Checkstyle: **0 violations**;
- JaCoCo reports and aggregate CycloneDX SBOM: **generated**;
- Maven Wrapper: **Maven 3.9.16 on Java 25 confirmed**;
- two clean corrected builds produced the same distribution SHA-256: `d98f34a782751bece418fabb94cd527bc0350a073abe3758037c9eac1a23c79a`;
- protected-path comparison against `v1.2.0-baseline`: **clean** for `src`, `dist`, `baseline/v1/runtime`, and `baseline/v1/external`;
- generic V2 source scan for fixed legacy ranks/MaddKraft gameplay terms: **clean**.

MySQL and MariaDB containers were not available in the local environment. The repository contains CI service-container jobs that invoke the Failsafe profile with every required property and then assert the exact integration-test report ran one test with zero skips/failures/errors. No backend-support claim is made until both jobs are observed passing and the repository contract suite is complete.

## YAML hard-gate decision

The selected Phase 1 strategy is SnakeYAML Engine `3.0.1` for YAML 1.2 parsing, comments, node styles, and source marks, combined with a MaddPrestige source-range editor that retains the original UTF-8 document and changes only a selected scalar token. Golden/regression tests prove exact no-op round trips and preservation of comments, ordering, dotted keys, nested structures, unknown compatible keys, UTF-8/emoji, CRLF, safe quoting, scalar semantics, and multiple documents.

Every replacement method rejects block scalars. Anchored and explicitly tagged scalar edits also fail closed to preserve alias/tag semantics and presentation. Whole-document serializer approaches were not accepted as canonical writers. The candidate matrix and limitations are documented in [docs/V2_PHASE1_IMPLEMENTATION.md](docs/V2_PHASE1_IMPLEMENTATION.md).

## Remaining risks and gates

1. The MySQL `8.4.10` and MariaDB `11.8.8` CI jobs have not yet been observed in this workspace; neither backend is supported or network-safe by implication.
2. `FileBackupService` is suitable only for quiesced/disposable SQLite files. Live WAL-mode production backup needs a coordinated checkpoint/online-backup implementation before activation.
3. The V2 API is foundational and not yet declared stable for third-party publication.
4. The distribution intentionally continues to bootstrap frozen V1 behavior; it is migration/build evidence, not a production V2 release.
5. Real Paper lifecycle, provider adapters, serialized write coordination, recovery execution, and player-domain repositories remain later-phase work.
6. Existing V1 compiler/dependency warnings remain characterization evidence and were not changed to make Phase 1 pass.

## Deviations

No approved safety or architecture requirement was weakened. Correction decisions D-031 and D-035 document the fail-closed YAML boundary and backup-first/retryable SQLite migration-history design. The distribution still compiles frozen V1 in place rather than moving it. External database jobs remain harness-only and intentionally carry no support claim.

## Phase 2 recommendation and stop point

The code-level Phase 1 entry criteria for a later Phase 2 are satisfied. Proceed to Phase 2 only after:

1. owner acceptance of this Phase 1 handoff;
2. one green CI run, including both external-backend harness jobs; and
3. explicit Phase 2 authorization.

Phase 2 has not begun. No production player data, production database, production configuration, production server, or LuckPerms state was accessed or modified.
