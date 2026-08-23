# Phase 8C exact changed/new file manifest

Baseline: `c71709ccd3e6c375bc6a9490856024979a66c17e` on `v2/phase-8`

State: all paths unstaged and uncommitted for owner review

Total review paths: 42 (17 modified, 25 new)

## Modified tracked paths (17)

1. `DECISIONS.md`
2. `STATUS.md`
3. `docs/V2_ARCHITECTURE.md`
4. `docs/V2_PHASE8_FAILURE_REGISTER.md`
5. `docs/V2_TRACEABILITY.md`
6. `docs/spec/MaddPrestige_V2_Master_Spec.md`
7. `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/config/phase3/PhaseThreeConfigurationCompiler.java`
8. `maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/stage/StageConfigurationCompiler.java`
9. `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/config/phase3/PhaseThreeConfigurationCompilerTest.java`
10. `maddprestige-core/src/test/java/net/maddkraft/maddprestige/core/stage/StageConfigurationCompilerTest.java`
11. `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/FileBackupService.java`
12. `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/migration/MigrationRunner.java`
13. `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteFoundation.java`
14. `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteMigrations.java`
15. `maddprestige-persistence/src/test/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteMigrationTest.java`
16. `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/bootstrap/MaddPrestigeV2Plugin.java`
17. `maddprestige-testkit/src/main/java/net/maddkraft/maddprestige/testkit/DisposableSqliteFixture.java`

## New review paths (25)

1. `PHASE8C_OWNER_REVIEW_SUMMARY.txt`
2. `PHASE8C_PAPER_QUALIFICATION.log`
3. `PHASE8C_VERIFY_1.log`
4. `PHASE8C_VERIFY_2.log`
5. `docs/V2_PHASE8C_BACKUP_RESTORE_EVIDENCE.md`
6. `docs/V2_PHASE8C_FILE_MANIFEST.md`
7. `docs/V2_PHASE8C_FIXTURE_METADATA.md`
8. `docs/V2_PHASE8C_IMPLEMENTATION.md`
9. `docs/V2_PHASE8C_MIGRATION_MATRIX.md`
10. `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/migration/MigrationHistoryRules.java`
11. `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/migration/MigrationHistorySchema.java`
12. `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteBackupManifest.java`
13. `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteBackupService.java`
14. `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteDatabaseValidator.java`
15. `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteSchemaContractValidator.java`
16. `maddprestige-persistence/src/main/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteValidationResult.java`
17. `maddprestige-persistence/src/test/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteBackupServiceTest.java`
18. `maddprestige-persistence/src/test/java/net/maddkraft/maddprestige/persistence/sqlite/SqliteMigrationQualificationTest.java`
19. `maddprestige-persistence/src/test/java/net/maddkraft/maddprestige/persistence/sqlite/SqlitePhase8cFixture.java`
20. `maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper/bootstrap/StartupPersistenceCompatibility.java`
21. `maddprestige-platform-paper/src/test/java/net/maddkraft/maddprestige/platform/paper/bootstrap/StartupPersistenceCompatibilityTest.java`
22. `qualification/phase8c-paper-harness/pom.xml`
23. `qualification/phase8c-paper-harness/src/main/java/net/maddkraft/qualification/phase8c/Phase8CFixtureDowngrader.java`
24. `qualification/phase8c-paper-harness/src/main/java/net/maddkraft/qualification/phase8c/Phase8CQualificationHarness.java`
25. `qualification/phase8c-paper-harness/src/main/resources/plugin.yml`

## Generated allowlisted bundle artifacts

- `maddprestige-distribution/target/MaddPrestige-2.0.0-SNAPSHOT.jar`
- `target/bom.json`
- `target/MaddPrestige_Phase8C_Owner_Review.zip`

Ignored module build output, Maven caches, disposable Paper server/world/database data, third-party JARs, and fixture
database files are not review paths and are not included in the bundle.
