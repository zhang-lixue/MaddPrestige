# MaddPrestige V2 — Phase 1 file manifest

**Date:** 2026-08-15
**Scope:** Phase 1 working-tree changes relative to `v1.2.0-baseline`

This manifest identifies the complete Phase 1 change surface. Build output under ignored `target/` directories is not source and is excluded.

## Owner-review correction pass

The correction pass added no new source path or module. It changed these existing Phase 1 files:

- `.github/workflows/phase1-ci.yml`
- `DECISIONS.md`
- `STATUS.md`
- `docs/V2_PHASE1_FILE_MANIFEST.md`
- `docs/V2_PHASE1_IMPLEMENTATION.md`
- `docs/V2_TRACEABILITY.md`
- `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/yaml/LosslessYamlDocument.java`
- `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/yaml/LosslessYamlDocumentTest.java`
- `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/migration/MigrationRunner.java`
- `maddprestige-persistence/src/test/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteMigrationTest.java`

`maddprestige-persistence/pom.xml` was reviewed and intentionally not changed: the existing `backend-contracts` profile correctly binds Maven Failsafe to `integration-test` and `verify`.

## Modified files

- `pom.xml`
- `DECISIONS.md`
- `STATUS.md`

The V1 `src/`, `dist/`, `baseline/v1/runtime/`, and `baseline/v1/external/` trees are unchanged.

## Added repository/build/CI files

- `.github/workflows/phase1-ci.yml`
- `.mvn/wrapper/maven-wrapper.properties`
- `mvnw`
- `mvnw.cmd`
- `config/checkstyle.xml`

`mvnw.cmd` contains a narrow Windows compatibility guard for ordinary non-symlinked Maven home directories; the generated wrapper otherwise remains at version `3.3.4` with Maven `3.9.16` pinned.

## Added documentation and characterization fixtures

- `baseline/v1/characterization/README.md`
- `baseline/v1/characterization/release-evidence.yml`
- `baseline/v1/characterization/v1-behavior.yml`
- `docs/V2_ARCHITECTURE.md`
- `docs/V2_PHASE1_FILE_MANIFEST.md`
- `docs/V2_PHASE1_IMPLEMENTATION.md`
- `docs/V2_TRACEABILITY.md`

## Added Maven modules

Every file in the following seven newly created module roots belongs to the Phase 1 change. The listed source packages and tests are exhaustive.

### `maddprestige-api`

- module file: `maddprestige-api/pom.xml`
- main packages: `api.audit` (`AuditOutcome`, `AuditRecord`, `AuditValue`), `api.explanation` (`ExplanationNode`, `ExplanationStatus`), `api.id` (`ConfigRevisionId`, `CurrencyId`, `EntitlementId`, `FieldId`, `IdentifierRules`, `MetricId`, `OperationId`, `ProviderId`, `ScopeId`, `StageId`, `StringIdentifier`), `api.operation` (`ActionState`, `Actor`, `OperationActionPlan`, `OperationPlan`, `OperationState`), `api.provider` (`ActivationState`, `CapabilityDescriptor`, `DependencyDescriptor`, `Provider`, `ProviderDescriptor`, `ProviderHealth`, `ProviderHealthState`, `ProviderLifecycle`, `ProviderSnapshot`), `api.result` (`ErrorCategory`, `Result`, `StructuredError`), `api.validation` (`ValidationFinding`, `ValidationReport`, `ValidationSeverity`), and `api.value` (`ExactDecimal`)
- tests: `AuditValueTest`, `IdentifierTest`, `ValidationReportTest`, `ExactDecimalTest`

### `maddprestige-core`

- module file: `maddprestige-core/pom.xml`
- main packages: `core.config` (`ActiveConfiguration`, `BackupMetadata`, `CompiledConfiguration`, `ConfigCompiler`, `ConfigDraft`, `ConfigRevision`, `ConfigurationService`, `ContentHash`, `DiffKind`, `RevisionHasher`, `SemanticDiff`, `SemanticDiffEntry`), `core.economy` (`PeerTransferProgressionPolicy`), `core.operation` (`IllegalTransitionException`, `OperationStateMachine`), `core.provider` (`ProviderRegistration`, `ProviderRegistry`), `core.rank` (`ExternalGroupCatalog`, `ManagedMembershipDelta`, `ManagedMembershipPolicy`, `ProjectionPolicy`, `RankProjectionValidator`, `ReconciliationPolicy`), `core.schema` (`AllowedValues`, `Deprecation`, `PhaseOneSchema`, `ReloadBehavior`, `RiskLevel`, `SchemaConstraint`, `SchemaNode`, `SchemaRegistry`, `SchemaValueType`), and `core.yaml` (`LosslessYamlDocument`, `YamlPath`)
- tests: `ConfigurationServiceTest`, `PeerTransferProgressionPolicyTest`, `OperationStateMachineTest`, `ProviderRegistryTest`, `ManagedMembershipPolicyTest`, `SchemaRegistryTest`, `LosslessYamlDocumentTest`
- golden resources: `src/test/resources/golden/roundtrip-input.yml`, `src/test/resources/golden/roundtrip-expected.yml`

### `maddprestige-persistence`

- module file: `maddprestige-persistence/pom.xml`
- main package: `persistence` (`AuditRepository`, `BackupService`, `CurrencyAccountRepository`, `FileBackupService`, `OperationRepository`, `PersistenceException`, `StoredOperation`, `TransactionUnit`, `VerifiedBackup`)
- JDBC package: `persistence.jdbc` (`ConnectionProvider`)
- migration package: `persistence.migration` (`Migration`, `MigrationRecord`, `MigrationReport`, `MigrationResult`, `MigrationRunner`)
- SQLite package: `persistence.sqlite` (`SqliteAuditRepository`, `SqliteConfigRevisionRepository`, `SqliteCurrencyAccountRepository`, `SqliteFoundation`, `SqliteMigrations`, `SqliteOperationRepository`)
- tests: `FileBackupServiceTest`, `ExternalBackendContractIT`, `SqliteMigrationTest`, `SqliteRepositoryTest`

### `maddprestige-platform-paper`

- module file: `maddprestige-platform-paper/pom.xml`
- main classes: `ExecutionThread`, `PaperTaskScheduler`, `PaperThreadGuard`
- test: `PaperTaskSchedulerContractTest`

### `maddprestige-integrations`

- module file: `maddprestige-integrations/pom.xml`
- main class: `DependencyHealthEvaluator`
- test: `DependencyHealthEvaluatorTest`

### `maddprestige-testkit`

- module file: `maddprestige-testkit/pom.xml`
- main classes: `DisposableSqliteFixture`, `FailureInjector`, `FakeCurrencyProvider`, `FakeOperationAction`, `FakeProgressionProvider`, `FakeProvider`, `FakeRankProvider`, `GoldenConfigHelper`, `ProviderHealthSimulator`
- test: `TestkitTest`

### `maddprestige-distribution`

- module file: `maddprestige-distribution/pom.xml`
- added test: `V1CharacterizationTest`
- no V1 Java source or resource was moved or edited; this module compiles the frozen root V1 tree in place and shades the inactive V2 foundations.
