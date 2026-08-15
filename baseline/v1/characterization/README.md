# V1 characterization fixtures

These files are immutable migration evidence added during V2 Phase 1. They describe observed V1 behavior, including unsafe behavior, without approving it for V2.

- `v1-behavior.yml` records fixed ranks, operation representations, season conversion, QuickShop weight, LuckPerms creation, and the dotted-key defect.
- `release-evidence.yml` pins release metadata and checksums.
- `../runtime/config.yml` is the generated V1 configuration shape.
- `../runtime/maddprestige.db` is the empty SQLite schema-version-4 fixture and is opened read-only by characterization tests.

Tests must never migrate or rewrite these files.
