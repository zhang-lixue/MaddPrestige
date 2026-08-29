# Phase 8F release documentation audit

Date: 2026-08-28

## Public release path

The active public documentation was checked against the executable candidate and docs-as-tests:

- `README.md` identifies `2.0.0-rc.1`, Java 25, Paper 26.1.2 build 74, LuckPerms 5.5.71 for the qualified projected
  profile, the SDK coordinate and the non-GA/Phase 9 boundary.
- `INSTALLATION_V2.md` covers required and optional dependencies, fresh startup, exact artifact placement, setup,
  Doctor/Why use, SQLite support and external-SQL non-support.
- `UPGRADE_ROLLBACK_V2.md` requires a clean shutdown and complete plugin-data backup, explains forward-only schema
  migration and restore rehearsal, and defines rollback as restoration of the complete pre-upgrade data directory.
- `API_SDK.md` and `EVENTS.md` name baseline `2.x-stable-1`, the 46/6 Stable counts, surface classifications,
  Optional/result/validation semantics and the owner-acceptance boundary.
- `PROVIDERS_INTEGRATIONS.md` describes the current owner-accepted provider/integration behavior without claiming
  bundled optional plugins or external-SQL support.

Historical V1 `INSTALLATION`, `COMPATIBILITY`, `API`, `RELATIONSHIPS` and `TEBEX` documents retain their historical
content but carry a visible non-V2 banner pointing to current guidance.

## Executable parity

`PhaseEightDPublicDocumentationTest` now checks the release version in all 15 active POMs, active public-document links,
commands and permissions, the exact SDK coordinate, upgrade/rollback links, and the schema versions used by every
public generic example. The canonical Member -> Adventurer -> Veteran configuration and Phase 6 generated setup remain
byte/schema validated by the accepted Phase 8D suite.

## Deliberate boundaries

The public operator documents do not call the candidate GA or production-ready. They do not claim MySQL/MariaDB
support, online backup commands, automatic LuckPerms group creation or live MaddKraft migration. Internal acceptance
bookkeeping now records the completed A76 run separately; Phase 9 remains mandatory for the deployment clone, live
migration/economy/exploit qualification and go-live decision.


## Final-RC setup correction

The public Quick Start now leads with the owner-facing current-session flow and exact `setup playtime adventurer PT1M`
and `setup playtime veteran PT3M` commands. `REQUIREMENTS_SCOPES.md` retains the explicit full canonical command for
power users and states that targets are typed/canonicalized at entry. Public help supplies command overview, exact setup
next steps and DURATION examples. Docs-as-tests cover both paths. The original owner-run failure remains recorded; the
documentation correction was not represented as acceptance by itself. The later real-player owner-operated run is the
authoritative A76 evidence and is recorded separately in `V2_PHASE8F_A76_OWNER_ACCEPTANCE.md`.
