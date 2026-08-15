# MaddPrestige V2 acceptance traceability

**Imported matrix:** master specification A01–A76
**Current classification:** Phase 2
**Legend:** `Satisfied` means the final acceptance criterion itself is demonstrably satisfied through Phase 2; `Partial` means Phase 1/2 establishes a required contract, implementation, or focused test but later activation/qualification evidence is still required; `Later` means the criterion belongs wholly to a later phase.
**Current totals:** 3 Satisfied, 46 Partial, 27 Later.

| ID | Acceptance criterion (condensed from master specification) | Phase relationship | Current evidence / owner |
|---|---|---|---|
| A01 | Fresh Paper-only install starts safely with no active progression | Partial | Inactive empty default and `StageRuntimeBootstrap` inert-state tests pass; full Paper-only plugin bootstrap remains Phase 6 qualification. |
| A02 | Setup wizard creates a simple active ladder | Later | Phase 6. |
| A03 | Generic core contains no MaddKraft gameplay/rank names | Satisfied | Generic production-source/resource scan plus arbitrary-stage compiler tests; fixed legacy names remain only in frozen V1/test evidence. |
| A04 | Display-name changes do not change stored stage ID | Satisfied | UUID-first `PlayerStageState` persists immutable `StageId`; display/order/projection stay in configuration; SQLite round-trip/CAS tests pass. |
| A05 | Missing LuckPerms group blocks apply and is never created | Partial | Real LuckPerms 5.5 API adapter uses read-only `loadGroup`; missing targets and invalid explicit projection types block validation, provider generation is pinned through apply, and proxy-backed API tests prove no user load/mutation or creation path. Live plugin qualification remains. |
| A06 | Rank-up changes only managed progression membership | Partial | Real-API adapter tests prove exact managed-node isolation across permissions, unrelated groups, temporary/contextual nodes, cached/offline users, and repeats; live plugin qualification remains. |
| A07 | LP reconciliation follows configured policy | Partial | All three policies, persistence coordination, ambiguity, import-once, repeated/idempotent behavior, immediate pre-insert configuration/provider rechecks, and uncertainty are tested; lifecycle wiring/live qualification remain. |
| A08 | Reordering stages shows impact before apply | Satisfied | `StageChangeImpactAnalyzer` reports old/new order, high-risk semantic diff, stored-player counts, acknowledgement, and explicit remap gating before canonical apply. |
| A09 | ALL requirements require every child | Later | Phase 3. |
| A10 | ANY_X_OF_Y evaluates exact threshold | Later | Phase 3. |
| A11 | Nested requirements evaluate/explain correctly | Partial | Recursive `ExplanationNode` foundation; requirement tree is Phase 3. |
| A12 | Weighted requirement threshold evaluates correctly | Later | Phase 3. |
| A13 | Live completion can become incomplete | Later | Phase 3. |
| A14 | Latched completion remains complete in scope | Later | Phase 3. |
| A15 | Stage-scope baseline counts only post-entry delta | Partial | `ScopeId` foundation; baselines are Phase 3/4. |
| A16 | Prestige-scope baseline excludes prior lifetime value | Partial | `ScopeId` foundation; Phase 3/4. |
| A17 | Season-scope baseline uses current season delta | Partial | `ScopeId` foundation; Phase 3/4. |
| A18 | Linear scaling matches formula | Later | Phase 3. |
| A19 | Exponential scaling matches formula | Later | Phase 3. |
| A20 | Stepped scaling selects correct threshold | Later | Phase 3. |
| A21 | Disabled catch-up does not reduce target | Later | Phase 3. |
| A22 | Enabled catch-up is local and respects floor/rounding | Later | Phase 3. |
| A23 | Insufficient cost blocks without consumption | Partial | Immutable operation planning and fail-closed result contracts; cost engine is Phase 3. |
| A24 | Vault cost withdraws exactly once in normal execution | Partial | Idempotency/action persistence foundation; Vault adapter is Phase 5. |
| A25 | Reward applies after committed state | Partial | `STATE_COMMITTED` ordering model; reward engine is Phase 3. |
| A26 | Simulation shows exact plan without mutation | Partial | Immutable/redacted `OperationPlan`; simulation surface is Phase 3/6. |
| A27 | Destructive prestige requires clear confirmation | Later | Phase 4/6. |
| A28 | Prestige required/reset stage is not hardcoded | Partial | Generic IDs contain no special stage; prestige engine is Phase 4. |
| A29 | Finite and unlimited prestige caps work | Later | Phase 4. |
| A30 | Renaming currency display does not change ID/data | Partial | `CurrencyId` + exact value/repository tests; currency engine/UI is Phase 4. |
| A31 | Entitlement max merge returns highest source | Later | Phase 4. |
| A32 | Entitlement sum merge is exact | Later | Phase 4. |
| A33 | Season archive preserves configured history | Later | Phase 4. |
| A34 | Disabled competitions add no command/UI noise | Partial | Generic safe schema default is disabled; module/UI is later. |
| A35 | Legacy schema upgrade creates backup first | Partial | Backup-first migration framework remains regression-tested; Phase 2 non-dry legacy plans also require verified backup metadata, but mutation-capable production legacy upgrade remains Phase 9. |
| A36 | Ambiguous legacy rank mapping stops for explicit choice | Partial | Explicit revisioned mapping rejects missing/conflicting opaque values and invalid targets; erroneous plans expose no planned player mappings and cannot proceed. Planner remains read-only; production migration UI/execution remain Phase 9. |
| A37 | Old progression/patron groups are never auto-created | Partial | V2 rank API and real-API adapter have no creation capability; migration planner is read-only and no legacy names are embedded in production V2 code. Live qualification remains. |
| A38 | Config edits preserve designed comments/order | Partial | Surgical scalar edits pass hardened golden tests; complete command/GUI editing and unsupported structural edits remain later. |
| A39 | Draft edits do not affect production until apply | Partial | Immutable draft/active-reference behavior is tested without a production runtime apply path. |
| A40 | Invalid provider metric/config cannot apply | Partial | Structured apply guards exist; real provider-specific semantic validation remains later. |
| A41 | Rollback restores prior valid behavior | Partial | Rollback creates a new validated revision; file/runtime integration is Phase 6. |
| A42 | YAML/command/GUI produce equivalent canonical model | Partial | One schema/config service exists; command/GUI adapters are Phase 6. |
| A43 | Provider/metric tab completion is capability-driven | Partial | Capability registry exists; command completion is Phase 6. |
| A44 | Measurement help is plain language | Partial | Schema descriptions/explanation contracts exist; help surface is Phase 6. |
| A45 | Healthy `/doctor` is concise/actionable | Partial | Structured health states/reasons exist; `/doctor` is Phase 6. |
| A46 | `/doctor` identifies exact broken LP stage | Partial | Validator finding contains stage path/code/remediation; command is Phase 6. |
| A47 | `/why` identifies exact blockers | Partial | Immutable recursive explanation model; evaluator/command is Phase 3/6. |
| A48 | mcMMO absolute metric is authoritative | Later | Phase 5 real adapter qualification. |
| A49 | mcMMO scoped XP uses current delta | Later | Phase 5 after Phase 3 baselines. |
| A50 | Active mcMMO outage fails closed without stopping core | Partial | Structured unavailability/health simulation exists; an actual mcMMO adapter/fault test remains Phase 5. |
| A51 | Vault outage blocks operation without partial state | Partial | Structured unavailable results/operation journal; actual Vault fault test is Phase 5. |
| A52 | PAPI output is cached and does not query DB per render | Later | Phase 5. |
| A53 | Missing/unparseable PAPI input is unavailable/actionable | Partial | Structured provider failure contract; PAPI provider is Phase 5. |
| A54 | QuickShop P2P cycling gives zero default credit | Partial | Safe policy/schema defaults are tested; the actual QuickShop adapter and cycling test remain Phase 5. |
| A55 | Blocked command actions are refused | Partial | External commands disabled/high-risk in schema; action validator/executor is Phase 3. |
| A56 | View-only staff cannot apply | Partial | Separate schema edit/apply permissions; command/GUI enforcement is Phase 6. |
| A57 | Manual prestige edit produces complete audit | Partial | Full audit/redaction contract + SQLite append test; player edit surface is Phase 6. |
| A58 | Dirty cached progress survives restart | Later | Phase 4/8 cache/recovery implementation. |
| A59 | Interrupted pending internal operation reconciles without duplicate reward | Partial | Operation/action states, persistence, transition tests, idempotency uniqueness; recovery coordinator later. |
| A60 | External side-effect crash records uncertainty | Partial | Persisted executor tests cover save uncertainty and successful LuckPerms save followed by failed after-state verification; both become `UNCERTAIN`/`NEEDS_RECONCILIATION` without internal commit. Broader action recovery remains later. |
| A61 | Offline player rank/repair is safe or explicitly limited | Partial | Adapter uses asynchronous UUID load/save, preserves cached users, cleans up newly loaded users, and surfaces save uncertainty in proxy-backed real-API tests; live LuckPerms storage qualification remains. |
| A62 | High-volume progress avoids per-event SQL/TPS harm | Partial | Paper thread/scheduler boundary and fake ingestion foundations; load/event engine later. |
| A63 | SQLite upgrade preserves real data and reports migration | Partial | Disposable migration/report, prefix, retry, and backup-order tests pass; production-like data upgrade remains Phase 9. |
| A64 | MySQL/MariaDB match SQLite progression semantics | Partial | CI container contract harness exists; no backend support claim and no progression engine yet. |
| A65 | Third-party provider registers and appears dynamically | Partial | Owner-bound registry/capabilities/generations and fake providers; UI/evaluation later. |
| A66 | Rank/prestige API event ordering is documented/correct | Later | Phase 8 public event API after engines exist. |
| A67 | One optional adapter failure leaves unrelated features working | Partial | Health evaluator/registry isolation is tested; a real optional-adapter fault test remains later. |
| A68 | Unicode/MiniMessage language replacement renders correctly | Partial | UTF-8 build/YAML golden proof; language/UI rendering later. |
| A69 | Deleting referenced stage requires migration/replacement | Partial | Stored reference counts block removal/disablement even with a complete remap plan; persisted rows and active configuration remain unchanged. Atomic remap execution and UI remain later. |
| A70 | Unbranded Member→Adventurer→Veteran loop works | Later | Phase 8 qualification. |
| A71 | MaddKraft six-stage profile consumes existing groups | Later | Phase 7/9 preset and qualification only. |
| A72 | `mad_hatter` supporter survives MaddKraft prestige | Partial | Generic isolation test proves unrelated memberships survive; real profile/LP is Phase 9. |
| A73 | Full MaddKraft stack boots without conflicts | Later | Phase 9 clone/test server. |
| A74 | Resource reset remains outside MaddPrestige | Later | Architectural boundary is retained; Phase 7/9 coexistence verification. |
| A75 | PvP/Court plugin remains provider-only | Later | Architectural boundary is retained; Phase 7/9 verification. |
| A76 | Fresh admin succeeds using Quick Start only | Later | Phase 8 documentation/usability gate. |

## Phase 2 hard-safety mapping

| Hard-safety gate | Evidence |
|---|---|
| Missing external rank group is an error; no creation path | `StageConfigurationValidator`, `LuckPermsRankAdapterTest`; V2 adapter exposes only `loadGroup` validation |
| Managed direct membership is isolated | `ManagedMembershipPolicyTest`, `LuckPermsRankAdapterTest` |
| Temporary/contextual managed nodes fail closed and survive | `LuckPermsRankAdapterTest` ambiguity cases |
| Offline/cached users use explicit asynchronous lifecycle | `LuckPermsRankAdapterTest` load/save/cleanup and save-failure cases |
| Stage identity is UUID + immutable ID, not ordinal/display | `SqlitePlayerStageRepositoryTest` and `StageChangeImpactAnalyzerTest` |
| Reorder/removal impact is visible; a plan cannot substitute for remap execution | `StageChangeImpactAnalyzerTest`, canonical workflow and SQLite persisted-row tests |
| Reconciliation policy cannot silently form a feedback loop | `RankReconcilerTest`, deterministic import worker race tests, `RankProjectionOperationExecutorTest` |
| External uncertainty never becomes false success | adapter post-save verification test, persisted executor save/race tests, and action/operation journal assertions |
| Legacy mapping is explicit, read-only, backup-gated, and exposes no plan on error | `LegacyStageMigrationPlannerTest` |
| YAML lossless round trip | `LosslessYamlDocumentTest` + golden files |
| Backup occurs before history/schema DDL | `SqliteMigrationTest.backupFailureLeavesPreexistingSchemaIntact` and applied-prefix variant |
| Partial schema cannot be marked current | `SqliteMigrationTest.refusesPartialSchema` |
| Applied history is a valid prefix; failed attempts remain repeatable | SQLite prefix/gap/checksum/unknown/retry regression tests |
| Revisions immutable/content-addressed | `ConfigurationServiceTest`, `RevisionHasher` |
| Invalid config cannot activate | `ConfigurationServiceTest` |
| Illegal operation transitions rejected | `OperationStateMachineTest` and repository transition guard |
| Duplicate operation/idempotency prevented | `SqliteRepositoryTest.preventsDuplicateOperations` |
| Decimal currency stays exact | API and SQLite exact-decimal tests |
| Provider failure is unavailable, not zero | `TestkitTest.simulatesProviders` |
| Sensitive schema/audit values redact | schema test, `AuditValueTest`, SQLite audit test |
| V1 frozen evidence unchanged | release checksum/characterization tests plus CI `git diff` gate |
