# Phase 9E production-readiness consolidation and backend freeze

Baseline: `f25821c173bc7f2c320c7f0281378d8f41cb71c8` on `v2/phase-9e`.

## Phase 9D correction classification

| Correction | Classification | Frozen behavior |
|---|---|---|
| PlaceholderAPI 2.12.2/2.12.3 policy | FINAL PRODUCTION CONTRACT | Those exact versions may compose; other versions fail closed. Output stays cached/read-only and configured input stays typed. |
| Numeric command routing | FINAL PRODUCTION CONTRACT | Normal player routes use only numeric Prestige `P -> P+1`; typed rank-up compatibility remains blocked. |
| concise Why/simulation/player/Prestige rendering | FINAL PRODUCTION CONTRACT | Normal output is gameplay-oriented and bounded; `details` retains diagnostics and provenance. |
| visible rendering-failure fallback | FINAL PRODUCTION CONTRACT | A render failure produces an explicit localized failure instead of silent command loss. |
| live mcMMO `total_level` read | FINAL PRODUCTION CONTRACT | Online reads use the live player profile; offline behavior remains provider-owned and fail-closed. |
| provider unavailable behavior | FINAL PRODUCTION CONTRACT | A referenced unavailable/stale/unhealthy/malformed capability blocks only its dependent operation. |
| session-bound confirmation lifecycle | FINAL PRODUCTION CONTRACT | Login-session ownership, supersession, revision checks, reauthorization, single consumption, and restart/logout invalidation are authoritative. |
| confirmation shorthand and completion | FINAL PRODUCTION CONTRACT | No-ID form requires exactly one valid owned confirmation; completion exposes only the player's valid IDs. |
| structured scaling administration | FINAL PRODUCTION CONTRACT | Canonical typed mutation covers profiles, segments, transitions, and overrides; preview and execution share the resolver. |
| `INTEGER_COUNT` provider rendering/compilation | FINAL PRODUCTION CONTRACT | Exact integer values remain typed and render as gameplay values on normal surfaces. |
| lossless YAML sequence editing | FINAL PRODUCTION CONTRACT | Schema-confined insertion, replacement, and removal preserve unrelated YAML. |
| acknowledged-operation recovery | FINAL PRODUCTION CONTRACT | Durable `STATE_COMMITTED` recovery completes one coherent result without replaying committed mutations. |
| Phase 9D regression fixtures/listeners/mocks | TEST/QUALIFICATION SUPPORT ONLY | Remain under test sources and are never packaged as runtime hooks. |
| Phase 9D real-player and clone records | DOCUMENTATION/EVIDENCE ONLY | Remain under `docs/evidence/phase9d`; they are historical evidence, not runtime configuration. |
| debugger, breakpoint, fault hook, clone profile | OBSOLETE / REMOVE | None is present in production source/resources or the Phase 9E candidate manifest. |

The exact 46-path disposition is recorded in `V2_PHASE9E_PHASE9D_PATH_AUDIT.md`.

## Backend contracts frozen for UI consumers

Player reads are supplied by the production `MaddPrestigeService`, `OperationPreviewService`,
`PlayerProgressViewService`, requirement explanations, semantic presentation, currency queries, and lifecycle history
repository. They expose current/next Prestige, eligibility/blockers, effective requirements, costs, rewards, milestones,
confirmation state, currency balances, and persisted history where supported. Presentation objects do not require a UI
to interpret provider internals, compiler provenance, revision hashes, journal states, or stage/rank compatibility.

Staff reads are supplied by configuration introspection/administration, provider descriptors and snapshots, validation,
revision history, Doctor/Why, operation preview, and player progress services. Staff mutations use one canonical
authority: owner-bound draft, schema-confined scalar/list/structured changes, validate, preview/diff, acknowledge,
apply, and rollback. Structured mutations cover requirement trees (`ALL`, `ANY`, `X_OF_N`), costs, rewards, milestones,
currencies, scaling ranges/segments/transitions/overrides, provider selections, and player Prestige adjustment. A later
GUI must call these services and must not write YAML or persistence directly.

The existing history boundary is intentionally internal; a later UI may add a presentation adapter over the durable
repository without changing progression semantics. The future shop is not represented as an implemented service.

## Production discovery corrections

Phase 9E removes unreachable legacy Phase 1 fields from the production schema: rank reconciliation,
`competitions.enabled`, QuickShop progression income, external DB password, compatibility stage reset, and
`STAGE_REACHED` milestone authoring. It also removes rank-up from ordinary plugin usage/help and prevents a subject
holding only the compatibility rank permission from discovering Prestige confirmation IDs. Typed Stable rank/stage
APIs and permission nodes remain source/binary compatible and fail closed.

## Leakage, release, security, and performance audits

Production Java/resources contain no Phase 9D clone path, qualification port, player identity, firewall rule, JDWP
activation, breakpoint, crash hook, fault-injection switch, low-value qualification profile, or qualification-only
environment/property. Two real-player UUID copies found in test fixtures were replaced with RFC 4122-shaped synthetic
IDs; historical accepted evidence remains unchanged. Qualification strings elsewhere are confined to tests,
qualification projects, historical documents, or explicit non-production evidence.

Dependency scope and package exclusions remain enforced by the Phase 8F release gates. SQL uses bound statements and
checked migrations. Configuration and filesystem paths remain schema/data-directory confined. Sensitive schema values
are redacted, exceptions are rendered through stable codes, permission checks precede consequential commands, and
provider failures remain isolated/fail-closed. No new Stable API signature was changed.

No clearly pathological Phase 9B-9D performance behavior was found. Provider reads are capability-scoped; previews
compile from the active immutable revision; scaling resolution is bounded by validated segments; confirmation maps are
per-session and cleared on quit/restart; join/quit work is constant-time; startup recovery scans durable unresolved
work rather than the whole player population. No speculative optimization was added.

## Defaults and boundaries

The five V2 bootstrap documents remain two lines each (`schema-version` plus newline): 10 total lines. The built-in
locale is 798 lines, `plugin.yml` is 108 lines, and the 515-line root `config.yml` remains protected V1 transition
support, not an active V2 configuration surface. No shipped default selects a requirement, cost, reward, provider,
currency identity, shop item, or MaddKraft production balance.

Confirmation maximum lifetime defaults to `PT12H`, accepts `PT1H` through `P7D`, and is only a secondary safety cap.
The login session is the primary lifetime. External resets remain disabled and validation rejects enabling them.

MaddPrestige owns no resource-world reset listener and no Court/PvP progression. Resource-world state is external;
A74 remains partial until staging proves `Prestige N` before and after an actual reset. A75 remains not applicable until
a real Court plugin exists. Internal currency remains generic, exact-decimal, ID/name/symbol configurable, durable,
operation-ledgered, and not imported from V1. The first-party Prestige shop remains later Phase 9 work: it will consume
the canonical currency/action/transaction/admin boundaries, but no shop entry, price, purchase, discount, or UI exists.

## Freeze decision

Final Phase 9E verification passed twice and the non-GUI backend has no known progression, provider-ownership,
configuration-model, confirmation/recovery, or planned-GUI administration gap. The backend is suitable to freeze for
the final GUI/UI phase; deployment still requires owner approval and the documented production runbook.
