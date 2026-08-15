# MaddPrestige V2 — Phase 1 implementation notes

**Date:** 2026-08-15
**Scope:** generic foundations only
**Production activation:** none

## Build and verification

Requirements:

- Java 25;
- the checked-in Maven Wrapper (Maven 3.9.16);
- network access on the first build to resolve pinned dependencies.

Windows:

```powershell
.\mvnw.cmd --no-transfer-progress clean verify
```

Linux/macOS/CI:

```bash
bash ./mvnw --no-transfer-progress clean verify
```

The default build runs unit tests, real disposable SQLite tests, V1 characterization tests, Maven Enforcer dependency convergence, Checkstyle, JaCoCo reports, and aggregate CycloneDX SBOM generation. The optional vulnerability report is:

```bash
./mvnw verify -Psecurity-audit
```

That profile needs current vulnerability feeds and can fail independently of normal offline development. The CycloneDX SBOM includes dependency/license metadata. GitHub dependency review runs for pull requests only when the repository is public and the feature is supported. This private personal repository intentionally skips that optional GitHub-specific check; Phase 1 does not require GitHub Advanced Security. CycloneDX SBOM generation and the optional OWASP audit remain available independently. CI also rejects any diff to the frozen V1 `src`, `dist`, runtime fixture, or external evidence paths.

## YAML hard-gate evaluation

### Candidates considered

| Candidate | Finding | Decision |
|---|---|---|
| Bukkit `FileConfiguration` | Existing V1 evidence proves comments/order are lost and dotted keys are rewritten. | Rejected as the canonical V2 writer. |
| Jackson YAML object/data binding | Strong object binding, but its data model does not retain YAML comments and presentation details required for surgical human-file edits. | Rejected for the canonical lossless document. |
| Configurate YAML | Useful typed nodes and comment metadata, but normal save rewrites the document presentation rather than retaining untouched source text byte-for-byte. | Not selected for the canonical document. |
| SnakeYAML Engine high-level load/emit | YAML 1.2 parsing and comment nodes are available, but ordinary re-emission is not a lossless source editor. | Rejected as a whole-document writer. |
| SnakeYAML Engine parser + MaddPrestige lossless source-range edits | The parser supplies marks and scalar styles; MaddPrestige retains the original UTF-8 text, replaces only the selected scalar token, and reparses before returning the result. | Selected for the Phase 1 canonical document foundation. |

The selected dependency is SnakeYAML Engine `3.0.1`, whose published artifact describes it as a YAML 1.2 parser/emitter and exposes comment parsing plus source marks. References used during the spike: [Maven Central artifact](https://central.sonatype.com/artifact/org.snakeyaml/snakeyaml-engine), [LoadSettings comment parsing API](https://javadoc.io/static/org.snakeyaml/snakeyaml-engine/2.3/org/snakeyaml/engine/v2/api/LoadSettingsBuilder.html), and [Compose low-level API](https://javadoc.io/static/org.snakeyaml/snakeyaml-engine/2.2/org/snakeyaml/engine/v2/api/lowlevel/Compose.html).

### Golden proof

`LosslessYamlDocumentTest` begins with an exact unmodified round trip, then changes values across two YAML documents. The expected golden output proves retention of:

- leading and inline comments;
- key and document order;
- a dotted key as one scalar key;
- nested mapping/list paths;
- an unknown compatible subtree;
- UTF-8 and Unicode values;
- original single/double quoting on untouched values;
- safe quoting and escaping on edited strings;
- appropriate plain boolean/decimal output;
- multiple documents separated by `---`.

Phase 1 intentionally rejects every string/boolean/decimal edit to literal or folded block scalars instead of risking formatting loss. Anchored scalars and explicitly tagged scalars (including `!!` standard tags) are also rejected because SnakeYAML's scalar source range can include their presentation and an edit could remove or redirect semantics. Such nodes still round-trip unchanged. Collection insertion/deletion and comment synthesis are future operations built on the same lossless document; they are not silently emulated with a destructive serializer.

The correction regression suite also proves CRLF retention, code-point-to-UTF-16 range conversion when supplementary-plane Unicode precedes an edited scalar, and safe string quoting. Plain replacements that resemble booleans, nulls, or numbers—or contain spaces, `:`, `#`, quotes, backslashes, or control escapes—are quoted/escaped and reparse as strings.

## Configuration foundation

- `SchemaRegistry` rejects duplicate immutable field IDs, canonical paths, and aliases.
- Representative fields prove type/default/description/examples/constraints/allowed values/sensitivity/risk/edit permission/apply permission/reload behavior/deprecation/migration metadata.
- Safe defaults include `warn-only` rank reconciliation, disabled external commands, disabled competitions, and zero QuickShop progression-income weight.
- `ConfigDraft`, `CompiledConfiguration`, `ConfigRevision`, `SemanticDiff`, `BackupMetadata`, and `ActiveConfiguration` are immutable.
- Revision hashing sorts document names and hashes length-prefixed UTF-8 names/contents, making revisions order-independent and collision-resistant against concatenation ambiguity.
- `ConfigurationService` refuses errors, missing high-risk acknowledgements, and unverified backups. Rollback creates a new active revision rather than mutating an old revision.

## Provider and rank safety foundation

- Provider registration binds caller identity to declared owner identity.
- Duplicate IDs and stale generations are rejected.
- Lifecycle, activation, dependency metadata, capabilities, implementation/API versions, health, and structured health reasons are modeled.
- All required health states from the authorization are present.
- `ExternalGroupCatalog` has only a read-only existence query; no V2 group-creation path exists.
- Missing/unavailable configured groups produce structured validation errors.
- `ManagedMembershipPolicy` preserves every direct membership outside the explicitly configured managed progression set.
- Baseline stages can request no projection.
- Fake providers simulate health and failures without connecting to external plugins.

## Operation and audit foundation

- Immutable plans contain operation UUID/type, actor, target, expected state revision, configuration revision, provider generations, idempotency key, ordered action descriptions, and a redacted preview.
- Operation/action transition tables reject illegal jumps.
- SQLite uniqueness on `(operation_type, target_uuid, idempotency_key)` prevents duplicate prepared operations.
- Per-action state and redacted action descriptions are persisted.
- Audit contracts include actor, target, operation/config revision, provider/action, old/new values, source, reason, outcome, uncertainty, correlation, and timestamp.
- Sensitive audit values redact before reaching SQLite.

## Persistence and backend status

SQLite Phase 1 foundation is implemented and locally tested only against disposable V2 databases. Migration preflight uses read-only metadata and reads an existing history table without DDL. If work is pending, a verified backup is produced before history-table/index initialization or any migration statement. Backup failure therefore leaves fresh and preexisting logical schemas unchanged.

The applied-version invariant uses a SQLite partial unique index on `version WHERE result = 'APPLIED'`; failed attempts use independent UUIDs and may repeat. Applied records must be an exact ordered prefix of the requested chain. Gaps, newer-without-older history, unknown versions, duplicate/non-prefix history, and checksum mismatches fail before pending SQL executes. Migration SQL plus its `APPLIED` record commit together. Failure SQL is rolled back before an auditable `FAILED` attempt is inserted, and autocommit restoration occurs outside the successful commit catch path. If commit acknowledgement itself throws, the attempt UUID is queried after rollback: a visible `APPLIED` record prevents false failure audit, while an unverifiable outcome remains explicitly uncertain without a misleading `FAILED` row.

The first Phase 1 migration creates configuration revision, operation, action, exact-currency, and audit tables with foreign keys/checks/indexes. A deliberately partial schema fails migration and remains without an applied version marker.

`FileBackupService` is a verified byte-copy foundation suitable for quiesced disposable files. It is not claimed as a safe live-WAL production backup mechanism. A future live SQLite apply/migration path must use a coordinated checkpoint/online-backup strategy and will continue to fail closed until verification succeeds.

MySQL and MariaDB repository semantics are contract targets. CI definitions use MySQL `8.4.10` and MariaDB `11.8.8` containers with Connector/J `9.7.0` and MariaDB Connector/J `3.5.8`. The `backend-contracts` profile binds Maven Failsafe's `integration-test` and `verify` goals; its default `*IT` discovery selects `ExternalBackendContractIT`. Each CI job passes the profile, enabling property, JDBC URL, user, password, and driver. After Maven succeeds, the job requires the exact Failsafe XML report and asserts `tests="1"`, `skipped="0"`, `failures="0"`, and `errors="0"`, preventing a compile-only or condition-skipped green result.

The harness exists, but neither backend is claimed supported by Phase 1 and the current local environment has not executed those container jobs. MySQL/MariaDB availability never implies multi-server progression safety.

## Known limitations and later-phase gates

- No V2 bootstrap, player repository, stage engine, requirements engine, costs/rewards, or real provider adapter is active.
- The distribution continues to boot V1 behavior. It is a development/migration-evidence artifact, not a production V2 release.
- The generic API is a Phase 1 foundation and is not declared stable for third-party publication yet.
- SQLite serialized write coordination/backpressure, online backups, and recovery coordinator execution remain later implementation work behind established interfaces.
- MySQL/MariaDB CI results must be observed before even a narrow backend claim; full shared repository contract suites are still required before public support.
- Paper integration tests and actual dependency-version tests are later-phase gates.

No Phase 2 feature was implemented and no production configuration, database, player data, or LuckPerms state was accessed or changed.
