# Phase 9B numeric Prestige policy and architecture alignment

Phase 9B starts from merged `main` at `f97d812be7a5963e2f9f3f042722ff77356e8363`. It freezes the
authoritative V2 progression state as one non-negative numeric Prestige level. A successful operation advances
`P` to `P + 1`; the configured maximum may be finite or `unlimited`. No stage, rank, LuckPerms group, world reset,
or season boundary is intrinsic to this transition.

This document supersedes stage-driven product guidance in normal documentation. Earlier Phase 0-9A specifications,
tests, evidence, and acceptance records remain historical evidence and are not silently rewritten.

## Repository conflict audit

| Accepted component or assumption | Classification | Phase 9B disposition |
|---|---|---|
| Prestige authorization required a final stage and selected a reset stage | MUST REFACTOR | Authorization now reads only durable Prestige state and derives exactly the next numeric level. Stage metadata is ignored. |
| Prestige execution acquired a stage-transition lease and committed a stage CAS/history row | MUST REFACTOR | Numeric execution keeps the journal, provider effects, Prestige CAS, scope/baseline updates, currency reset, milestone, and history transaction; it performs no stage mutation. |
| Fresh runtime initialization created paired stage and Prestige rows | MUST REFACTOR | Fresh V2 initialization creates only a zero-valued Prestige row and its scoped baselines. The old paired initializer remains compatibility code. |
| Production rank-up commands and stage catalog represented the main product path | MUST REFACTOR | Production exposes an empty stage catalog and rejects rank-up as compatibility-only. Prestige remains independently available. |
| LuckPerms rank projection was coupled to Prestige reset | MUST REFACTOR | Prestige performs no projection. LuckPerms is an optional additive permission/group reward provider. |
| One global legacy scaling formula | CAN GENERALIZE | Requirement scaling now accepts segments; costs and rewards have independent segmented multiplier maps. |
| ALL/ANY/ANY_X_OF_Y requirement groups | CAN GENERALIZE | `X_OF_N` is canonical; the old enum spelling remains deprecated for source/binary compatibility. |
| Stable API types containing stage/rank reads and rank-up operations | LEGACY-ONLY | Signatures remain unchanged. Production returns empty/blocked compatibility results, avoiding a breaking stable-API change. |
| Stage configuration, stage remap, and rank execution implementation | LEGACY-ONLY | Retained for compatibility and historical recovery; it is not consumed by numeric Prestige. |
| Migrations 1-11, Phase 8 fixtures, acceptance logs, and owner-review summaries | HISTORICAL EVIDENCE — DO NOT ALTER | Preserved with their accepted checksums and historical semantics. Migration 12 appends numeric authority. |
| Provider registry, typed requirements, distinct costs, operation journal, recovery, generic currency, milestones, seasons | NO CONFLICT | Reused through existing generic provider and transactional boundaries. |
| First-party configurable Prestige shop | AUTHORIZED LATER PHASE 9 | Not implemented in Phase 9B. Generic currency remains available, but no shop entries, prices, purchases, discounts, or GUI exist. |

## Authoritative state and persistence

MaddPrestige owns the player's UUID, current and lifetime numeric Prestige counters, state revision, configuration
revision, Prestige scope, and transition timestamp. Scope baselines, latches, currency ledgers, milestones, operation
journals, and audit history exist only where the configured feature or recoverability invariant requires them. Current
Vault, MobCoins, mcMMO, LuckPerms, homes, claims, warps, items, crates, and cosmetic values remain owned by their
providers and are queried at authorization/preflight time.

SQLite migration 12 adds a checked progression-model marker to Prestige operation details and Prestige history.
Preexisting rows are marked `LEGACY_STAGE`; new rows default to `NUMERIC_LEVEL`. Existing active stage and Prestige
rows are copied to dedicated legacy archival tables, active stage rows are cleared, and active numeric state starts at
P0 without mapping a stage position or projecting a group. It appends to migrations 1-11 without changing accepted
definitions or checksums. Numeric state and
the journal continue to use compare-and-set, transaction, idempotency, provider-generation, uncertainty, and restart
recovery guarantees. Acknowledged internal completion is durable; ambiguous external effects remain reconcilable and
are never blindly replayed.

Fresh V2 state starts at Prestige 0. V1 player rows, ranks, counters, currency balances, requirements, perks,
milestones, seasons, titles, preferences, competitions, operations, and active audit history are not read or imported.
V1 files may be archived outside the active V2 database. Administrators may later use the existing audited V2 manual
Prestige command to set an explicit value.

World identity and world lifecycle do not occur in `PlayerPrestigeState`, authorization, or execution. Resource,
Nether, End, or other world refreshes therefore cannot increment, reset, or otherwise mutate Prestige.

## Requirements and provider authority

`lifecycle.yml` selects one reusable requirement tree for each Prestige interval. Trees support `ALL`, `ANY`, and
`X_OF_N`; `threshold` supplies X for `X_OF_N`. Leaves use typed provider metrics, comparison operators, measurement
scope, completion policy, and their own scaling. mcMMO advertises `total_level` as the canonical Prestige metric; the
legacy `power_level` alias remains readable for compatibility. Individual skill metrics are not selected by guided
Prestige setup.

A requirement answers whether the transition is allowed. A cost is a separately configured provider mutation.
Neither implies the other. A check-only requirement has no matching entry in `prestige.costs`; a partial or full cost
is an explicit `CostDefinition` chosen by its ID. Missing required providers, malformed values, stale generations, or
failed required preflight block before the numeric state commit.

## Piecewise scaling

Requirement definitions accept `scaling.segments`. `lifecycle.yml` accepts `prestige.cost-scaling.<cost-id>.segments`
and `prestige.reward-scaling.<reward-id>.segments`. Each map is independent, so two providers or two action IDs can
scale differently.

Segments are inclusive, contiguous, and begin at target Prestige 1. Only the last may be open-ended. Supported modes
are `FLAT`, `LINEAR`, `EXPONENTIAL`, and `MANUAL`. `EXPLICIT_BASE` starts at `base`; `CONTINUE` anchors at the
preceding segment's value for the immediately previous Prestige level, including a per-level override at that level.
Optional fields are `rate`, `rounding`, `quantum`, `floor`, `cap`, and level-to-value
`overrides`. A manual segment must be finite and define every covered level. Open FLAT/LINEAR segments do not impose
an artificial Prestige maximum; EXPONENTIAL work, every computed magnitude (`1E+100`), precision, and arithmetic are
bounded and fail closed. The underlying numeric Prestige state remains uncapped unless `maximum` is configured.

```yaml
scaling:
  segments:
    - start-prestige: 1
      end-prestige: 10
      mode: FLAT
      transition: EXPLICIT_BASE
      base: 1
    - start-prestige: 11
      end-prestige: 20
      mode: LINEAR
      transition: CONTINUE
      rate: 0.25
      rounding: CEILING
      quantum: 0.01
      overrides:
        15: 4
    - start-prestige: 21
      end-prestige: unlimited
      mode: EXPONENTIAL
      transition: EXPLICIT_BASE
      base: 4
      rate: 1.05
      cap: 100
```

These values demonstrate syntax only and are not a MaddKraft production balance.

## Rewards, milestones, LuckPerms, currency, and shop

`prestige.rewards` runs configured every-Prestige rewards. Milestones independently select arbitrary levels/provider
metrics and reward IDs. Either list may be empty, and a level can use normal scaling, an override, every-Prestige
rewards, and milestone rewards together. Numeric reward values may use independent range scaling.

LuckPerms rewards use provider `luckperms`, type `permission` or `group`, and a configured string value. They add only
that permanent context-free node. Existing unrelated groups, permissions, hierarchy, weights, prefixes, and
inheritance are untouched. Group rewards require the group to exist; missing groups fail preflight and are never
created. No LuckPerms installation or health is required when the operation has no LuckPerms action.

The internal currency remains a generic configurable capability with an administrator-owned display name and can be
disabled. No product name or V1 balance is embedded in Java or imported. A configurable first-party Prestige shop is
authorized later Phase 9 work, but is not implemented: Phase 9B has no shop entries, prices, purchase engine, discount
behavior, or shop GUI. This correction does not implement it.

## Stable API review

No stable public API signature was changed. Stage/rank records, service methods, and Paper events remain compatibility
surfaces because removing them would be a breaking change. Production no longer treats them as Prestige authority:
stage reads are empty, rank-up is blocked as compatibility-only, and Prestige evaluation/result optionals contain no
stage transition. Core-internal constructor/source signatures were safely generalized. No unresolved breaking-API
owner decision remains.

## Scope boundary

Phase 9B chooses no MaddKraft money, MobCoin, mcMMO, reward, milestone, currency, or shop values. It performs no V1
player migration, live server/database change, real clone qualification, production balance activation, Phase 9C,
or Phase 10/web work.
