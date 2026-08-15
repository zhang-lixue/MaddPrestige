# MaddPrestige V1 Immutable Baseline

This directory preserves selected V1.2.0 inputs and runtime evidence at the repository baseline tag `v1.2.0-baseline`.

The existing V1 Maven source, tests, resources, documentation, and release artifacts remain at their original repository paths and are captured by the baseline commit. They were not rewritten during repository preparation.

Selected evidence copied here:

- `runtime/config.yml` — generated runtime configuration;
- `runtime/maddprestige.db` — empty retained test database, schema version 4;
- `runtime/RUNTIME_EVIDENCE.md` — redacted facts/excerpts from the retained full-stack Paper log; the original local log hash is recorded without committing identifiers/path-bearing raw output;
- `runtime/PLUGIN_SHA256SUMS.txt` — exact names and hashes of runtime plugin JARs without committing the roughly 500 MB server folder;
- `external/config-download.yml` — separately supplied V1 configuration;
- `external/MaddPrestige-1.2.0-download.jar` — separately supplied JAR, byte-identical to `dist/MaddPrestige-1.2.0.jar`;
- `SHA256SUMS.txt` — hashes of critical inputs plus deterministic pre-preparation tree digests.

The full `runtime-test/` directory and Maven `target/` remain local and ignored. The evidence copies are fixtures only. They must never be treated as production data or migrated in place. The test database contains no player/progression/audit rows.
