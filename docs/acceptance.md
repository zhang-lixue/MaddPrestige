# Release acceptance

MaddPrestige 2.0.0-rc.1 was accepted from merge commit
[`b23ff4e048b8cc6245af223148c21842455fa68c`](https://github.com/zhang-lixue/MaddPrestige/commit/b23ff4e048b8cc6245af223148c21842455fa68c)
and published as [release `v2.0.0-rc.1`](https://github.com/zhang-lixue/MaddPrestige/releases/tag/v2.0.0-rc.1).
The final implementation was reviewed in [pull request #16](https://github.com/zhang-lixue/MaddPrestige/pull/16).

## Automated evidence

- Full clean Maven reactor: 757 tests across 117 suites
- Failures, errors, skips: 0 / 0 / 0
- Focused final acceptance union: 379 / 379 passed
- Checkstyle: 0 violations
- Package/resource integrity: passed
- Reproducible distribution and aggregate SBOM: passed
- Protected V1 source changes: 0
- Stable API breaking changes: 0

Accepted release artifacts:

| Artifact | Size | SHA-256 |
|---|---:|---|
| `MaddPrestige-2.0.0-rc.1.jar` | 16,851,095 bytes | `20908B3D1CD573FF21429AA1159722915C13EC3BB25CFF5667A3E124D5C075C7` |
| Aggregate CycloneDX `bom.json` | 190,831 bytes | `3797F16C617B3207229EFD8A846BE2EC31FB9CD56BE681A2069447A859D5568E` |

## Runtime evidence

An outbound-isolated disposable copy of the MaddKraft server initialized all 42 plugins with no MaddPrestige startup
errors. Real-player qualification covered:

- numeric P0 -> P1 and P1 -> P2 progression;
- `ALL`, `ANY`, and both eligible/blocked `X_OF_N` cases;
- independent cost/reward behavior and insufficient-cost fail-closed handling;
- flat, linear, exponential, manual, segment-transition, and per-level override scaling;
- live Vault, mcMMO total level, LuckPerms preservation, and PlaceholderAPI 2.12.3;
- logout invalidation and deterministic acknowledged-operation crash/recovery;
- Player/Staff GUI navigation, history/audit, Set/Reset, and guided configuration edits.

Production data, permissions, balance, and services were not changed by qualification.

## Acceptance boundaries

- Populated pre-numeric V2 schema upgrade and activation: passed.
- Resource-world-reset evidence: partial because the external environment did not provide an actual reset; this is not
  a MaddPrestige implementation defect.
- Court integration: not applicable because no Court plugin exists.
- MySQL/MariaDB and shared-database operation: deferred beyond 2.0 and not a launch gate.
- No known product, persistence, runtime, or implementation blocker remained at publication.

Full historical evidence and phase-by-phase records are preserved by the release tag and Git history. See the
[development archive index](archive/v2-development/README.md).
