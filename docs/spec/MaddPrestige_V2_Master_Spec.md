# MaddPrestige V2 — Master Product & Engineering Specification

**Status:** Architecture / product specification draft v1<br>
**Primary deployment:** MaddKraft SMP<br>
**Product goal:** A production-grade, reusable Paper progression/prestige framework that can be deployed by unrelated servers without MaddKraft-specific assumptions.

**Owner product-scope amendment — 2026-08-17:** MaddPrestige 2.0 officially supports SQLite as its production
persistence backend. MySQL/MariaDB production support and semantic parity are deferred post-2.0 unless explicitly
re-authorized. This narrows the release scope without weakening SQLite correctness or removing the backend-neutral
boundaries retained for a proper future external-SQL implementation. A64 is deferred, not satisfied.

**Phase 8C implementation note — 2026-08-17:** the owner-authorized review candidate implements coordinated
SQLite-native backup, strict manifest/hash/integrity/history validation, disposable restore rehearsal, populated
prefix-to-current migration qualification and DB/config startup compatibility. This is candidate evidence, not owner
acceptance and not Phase 9 live MaddKraft migration evidence. A63 remains Partial; A64 remains Later.
**Phase 8F final acceptance note — 2026-08-28:** the final Phase 8 candidate uses semantic pre-release identity
`2.0.0-rc.1` and owner-frozen Stable compatibility baseline `2.x-stable-1`. Targeted Owner Review 3 and the owner-operated
real-player fresh-RC run pass against exact SHA-256 `0D961DB73D6C44CF18986CA42B7950914CEA5A1598BA7D41D0E919BFB76C6513`;
A76 is Satisfied under the D-179 replacement policy. Phase 8F and the Phase 8 work phase meet their exit criteria. This
does not claim GA or production readiness: A63 remains Partial for Phase 9 deployment-clone qualification and A64
remains Later.

---

## 0. Executive mandate

MaddPrestige V2 is not to be treated as a one-off MaddKraft rank plugin. It is to be engineered as a reusable public-quality progression platform for Paper servers.

A competent server owner should be able to install MaddPrestige, connect it to their existing rank/progression/economy stack, configure their own progression ladder, requirements, costs, rewards, prestige rules, entitlements, seasons and integrations, and operate the system without modifying Java source code.

MaddKraft is the flagship deployment and integration test environment, but no MaddKraft-specific rank names, balance values, Wonderland terminology, supporter names, plugin assumptions, or server-specific paths may be hardcoded into the core implementation.

### Core product promise

> If a behavior, threshold, mapping, requirement, reward, message, GUI element, provider option, integration setting, entitlement rule, season rule, or operational policy could reasonably vary between servers, it should be configurable rather than hardcoded.

### Administration parity promise

> Normal MaddPrestige configuration must be available through YAML, commands, and the in-game staff GUI, all backed by the same canonical configuration model and validation service.

A future web interface is optional. The architecture should permit one cleanly, but V2 must not sacrifice core reliability or security to ship an embedded website.

---

# 1. Product principles — non-negotiable

## 1.1 Generic core, branded configuration

The Java core understands generic concepts:

- progression stages;
- rank adapters;
- requirements;
- requirement groups;
- costs;
- rewards;
- prestige cycles;
- currencies;
- entitlements;
- milestones;
- seasons/chapters;
- competitions;
- provider integrations;
- history/statistics;
- configuration;
- persistence;
- transactions;
- diagnostics;
- APIs/events.

The Java core must not intrinsically understand concepts such as:

- Curious;
- Dreamer;
- Tea Guest;
- Wonderlander;
- Madcap;
- Tea Leaves;
- Rabbit Holes;
- MaddHatter;
- MaddKraft supporter ranks.

Those belong only to server configuration, examples, presets, or optional server-specific adapters.

## 1.2 Existing ranks are consumed, never invented

MaddPrestige must be able to use premade ranks/groups from an external rank provider such as LuckPerms.

When a configured external group does not exist, MaddPrestige must:

1. report the missing group clearly;
2. mark the affected stage/configuration invalid or unavailable;
3. fail closed for affected operations;
4. never silently create the missing rank;
5. never alter the external rank hierarchy, weights, prefixes, inheritance, or metadata unless an explicitly configured and documented feature requires a narrowly scoped change.

For MaddKraft specifically, LuckPerms owns group definitions, hierarchy, prefixes, weights and supporter/staff architecture. MaddPrestige only manages direct membership in groups explicitly declared as MaddPrestige-managed progression groups.

## 1.3 Progression and supporter/staff identities are independent

MaddPrestige must not assume that a player's progression rank is their only external group.

A player may simultaneously hold:

- a progression group;
- a supporter group;
- a staff group;
- specialist groups;
- event/title groups;
- temporary groups;
- unrelated permissions.

Prestige/rank-up operations must manipulate only configured managed progression membership. They must never remove unrelated groups.

## 1.4 Configuration-first

There should be no normal server-owner setting that requires recompiling the plugin.

Examples of things that belong in configuration when applicable:

- stage IDs and order;
- display names;
- external group mappings;
- requirement definitions and amounts;
- costs;
- rewards;
- measurement scopes;
- completion behavior;
- scaling strategies;
- prestige reset/preserve rules;
- currency names and behavior;
- entitlement merge rules;
- season rules;
- catch-up rules;
- competition rules;
- messages;
- GUI layouts and materials;
- sounds;
- integrations;
- provider options;
- cache/reconciliation intervals;
- permissions exposed for administrative features;
- logging/audit settings;
- storage backend and connection settings;
- safe generic command actions.

Internal implementation invariants such as thread safety, transaction correctness, schema integrity, locking and security boundaries remain code responsibilities.

## 1.5 Intuitive by design

Ease of use is a first-class feature.

A competent Minecraft server owner should not need to:

- read source code;
- guess YAML syntax;
- memorize provider internals;
- repeatedly search external documentation;
- decode Java stack traces for ordinary configuration mistakes.

MaddPrestige should teach the owner how to use it through comments, guided setup, tab completion, contextual help, clear validation, previews, diagnostics and safe undo/rollback.

## 1.6 Safe failure over silent corruption

If a required provider is missing, outdated, unhealthy or returns invalid data, affected progression must become unavailable rather than being interpreted as zero, completed, or reset.

If a configuration is invalid, the plugin must preserve the last known-good active configuration where possible and report the invalid draft/file clearly.

## 1.7 Public-quality reliability

The design target is not “works on MaddKraft once.” It is:

- predictable updates;
- backward-compatible migrations where possible;
- safe database handling;
- clean dependency isolation;
- graceful provider outages;
- actionable diagnostics;
- strong tests;
- stable documented API contracts;
- performance suitable for servers larger than MaddKraft.

---

# 2. Explicit non-goals for V2 core

The following are not required core responsibilities:

- creating or redesigning LuckPerms hierarchies;
- managing store/Tebex/CraftingStore purchases;
- owning paid/supporter ranks;
- world deletion/regeneration;
- PvP matchmaking or faction/court drafting;
- replacing mcMMO, Jobs, quests, skills or economy plugins;
- running an Auction House;
- implementing a class-based RPG;
- implementing arbitrary Java reflection/event bindings from YAML;
- implementing a public web control panel in the initial V2 release;
- guaranteeing atomic rollback of arbitrary external console command side effects;
- supporting branching/multiple independent progression tracks in the first V2 acceptance target.

The architecture should avoid preventing future multi-track/branching progression, but the required V2 model may focus on one ordered progression ladder plus prestige cycles.

---

# 3. Existing MaddPrestige audit

The current plugin/config contains useful foundations that should be preserved or generalized where technically sound:

- SQLite persistence;
- configurable flush behavior;
- LuckPerms, Vault, mcMMO and PlaceholderAPI concepts;
- dynamic mcMMO XP event tracking;
- integration/compatibility registry concept;
- lifecycle relationships/triggers;
- command safety blocklist concept;
- player and staff GUI concepts;
- progression/rank requirements;
- prestige requirements/scaling;
- prestige currency (“Tea Leaves”);
- milestones;
- entitlement aggregation intent;
- seasons/chapters;
- late-join catch-up;
- competition/holder concept;
- staff player editing.

## 3.1 Existing concepts to retire or redesign

The following current assumptions are obsolete or too server-specific:

- hardcoded progression enum names `CURIOUS`, `ODD`, `MAD`, `UNBOUND`;
- old LuckPerms mappings to `odd`, `mad`, `unbound`;
- patron tiers `KNAVE`, `DUCHESS`, `KING_OF_HEARTS`, `WHITE_QUEEN`, `QUEEN_OF_HEARTS`;
- MaddPrestige owning commercial/supporter groups;
- `MADDHATTER` as a special built-in competition identity;
- `auction-listings` terminology;
- direct stale compatibility entries such as EzATN, MaxCrates and SilkSpawners_v2;
- fixed mandatory Rabbit Hole/decree/boss requirements;
- global QuickShop earnings credit of 0.25 by default;
- globally hardcoded linear prestige scaling;
- globally hardcoded catch-up reduction semantics;
- static command relationship/provider registry that treats every installed plugin as though it needs a direct integration;
- generic reflection-based inbound event hook examples.

## 3.2 Migration safety

The existing `maddprestige.db` and old YAML must never be destructively overwritten during migration.

Migration requirements:

1. Detect old schema/config versions.
2. Back up config and database before mutation.
3. Version all migration steps.
4. Migrate deterministic fields automatically.
5. Require explicit mappings when semantics are ambiguous.
6. Never infer missing external groups by creating them.
7. Produce a human-readable migration report.
8. Support dry-run migration where practical.
9. If migration is ambiguous, stop safely and explain required administrator choices.

For MaddKraft, old progression stages do **not** map cleanly to the new five-stage ladder. Codex must not guess a mapping from ODD/MAD/UNBOUND into Dreamer/Tea Guest/Wonderlander/Madcap. Provide an explicit migration mapping workflow.

Old patron groups must not be recreated.

Old `auction-listings` entitlements must not be silently translated to QuickShop limits. Require an explicit entitlement mapping if data needs preservation.

Tea Leaf balances may be migrated into a configured internal currency ID if that mapping is explicit.

---

# 4. High-level architecture

Recommended module boundaries (exact package structure may differ if justified):

```text
MaddPrestige
├── core-domain
│   ├── stages
│   ├── requirements
│   ├── costs
│   ├── rewards
│   ├── prestige
│   ├── currencies
│   ├── entitlements
│   ├── seasons
│   ├── competitions
│   └── history
├── configuration
│   ├── schema
│   ├── validation
│   ├── drafts
│   ├── revisions
│   └── migrations
├── persistence
│   ├── repositories
│   ├── transactions
│   ├── sqlite
│   └── mysql-mariadb (deferred post-2.0 extension)
├── providers
│   ├── registry
│   ├── rank
│   ├── progression
│   ├── economy
│   ├── currency
│   ├── reward
│   └── predicates
├── integrations
│   ├── luckperms
│   ├── vault
│   ├── mcmmo
│   ├── placeholderapi
│   └── optional adapters
├── admin
│   ├── commands
│   ├── setup-wizard
│   ├── staff-gui
│   ├── diagnostics
│   └── audit
├── player-ui
├── api
└── plugin-bootstrap
```

The implementation must favor interfaces/services over cross-module static coupling.

Optional integrations must not be able to crash the core during class loading simply because their dependency is absent or incompatible.

---

# 5. Canonical identifiers and data model

## 5.1 Immutable internal IDs

Every configurable entity that can be referenced by player data or another configuration object must have a stable internal ID distinct from display text.

Examples:

```yaml
id: tea_guest
display-name: "Tea Guest"
external-group: tea_guest
```

Player data stores `tea_guest`, not the display name.

IDs should be validated for a safe normalized format. Display names may use MiniMessage and Unicode independently.

Where an ID rename is unavoidable, configuration migrations/aliases must be supported so existing player data is not orphaned.

## 5.2 Player state

At minimum, persist:

- player UUID;
- current progression stage ID;
- current prestige count;
- lifetime prestige count where seasons can reset current prestige;
- configured currency balances owned by MaddPrestige;
- active requirement progress/baselines/snapshots;
- latched requirement completions;
- purchased/unlocked prestige perks;
- entitlement state where necessary;
- season participation/current season state;
- milestone completion/history;
- rank-up/prestige history;
- pending/recoverable transaction state;
- audit-relevant staff modifications;
- timestamps/generation/version identifiers needed for reconciliation.

Names are metadata only; UUID is authoritative.

---

# 6. External rank adapter model

## 6.1 Internal stage vs external rank

MaddPrestige stores an internal progression stage. An external rank adapter projects that state into another plugin such as LuckPerms.

Example:

```text
MaddPrestige stage: tea_guest
        ↓
LuckPerms adapter
        ↓
Direct membership: tea_guest
```

The two are related but not the same object.

## 6.2 Sync policy

Support configurable reconciliation policies, for example:

- `maddprestige-authoritative` — internal stage is authoritative and external membership is repaired to match;
- `warn-only` — mismatches are reported but not automatically repaired;
- `import-once` / migration mode — external rank can seed MaddPrestige state during setup/migration.

Avoid an always-bidirectional mode that can create feedback loops.

## 6.3 LuckPerms first-party adapter

LuckPerms is the flagship rank adapter.

Requirements:

- use the public API;
- never create configured groups;
- never change group inheritance/weights/prefixes by default;
- manipulate only direct membership in groups explicitly declared as managed progression groups;
- preserve supporter, staff, specialist and unrelated groups;
- validate group existence on config apply;
- detect and report mismatches;
- support offline users safely using the LuckPerms API;
- avoid relying on Bukkit OP state.

LuckPerms should be an optional dependency at plugin level. It becomes functionally required only when the active configuration uses the LuckPerms rank adapter or LuckPerms-specific reward/entitlement capabilities.

---

# 7. Progression stage engine

V2 must support an arbitrary ordered list of stage definitions.

Example:

```yaml
stages:
  member:
    display-name: "Member"
    external-group: member
  adventurer:
    display-name: "Adventurer"
    external-group: adventurer
  veteran:
    display-name: "Veteran"
    external-group: veteran

order:
  - member
  - adventurer
  - veteran
```

No stage ID is special in Java.

Configurable per-stage properties should support at minimum:

- enabled/disabled;
- display name;
- icon/material/display metadata;
- external rank mapping;
- requirements;
- costs;
- rewards;
- entry actions;
- completion/rank-up actions;
- GUI presentation;
- optional permission predicates;
- optional prestige availability rules;
- optional stage-specific scaling modifiers.

Baseline/default stage behavior must be configurable. A server should be able to use an external default group as baseline without MaddPrestige modifying it if desired.

---

# 8. Provider framework

## 8.1 Provider categories

At minimum support typed provider interfaces for:

- rank providers;
- progression/stat providers;
- economy providers;
- currency providers;
- reward providers;
- entitlement providers;
- predicate/eligibility providers;
- optional reset-capability providers.

## 8.2 Capability discovery

Providers advertise their capabilities rather than the core assuming fixed metrics.

Example mcMMO adapter capabilities may include, if available through the supported API/version:

- power level;
- individual skill level;
- individual skill XP;
- total XP gained;
- skill XP gained during a measurement scope;
- skill levels gained during a measurement scope.

A Jobs/skills plugin adapter could advertise different metrics without changing the requirement engine.

Commands and GUI must populate valid metrics dynamically from provider capability metadata.

## 8.3 Metric typing

Provider metrics should be typed where practical:

- integer;
- decimal;
- duration;
- boolean;
- string/enum;
- count;
- currency amount.

Requirements must validate operators against metric type.

## 8.4 Provider health/status

Expose states such as:

- not installed;
- installed / integration available;
- active;
- inactive / unused;
- degraded;
- unsupported version;
- unavailable;
- unhealthy.

A provider used by an active requirement/cost/reward must fail closed when unavailable.

## 8.5 Lazy activation

An installed plugin should not necessarily cause a heavy adapter to initialize if no configured feature uses it.

The Integrations UI should distinguish:

```text
Installed: yes
Integration available: yes
Currently used: no
```

## 8.6 Public provider SDK

Third-party developers must be able to register providers through a stable public API without modifying MaddPrestige source.

Provide documented examples for at least:

- progression provider;
- economy/currency provider;
- requirement metric;
- reward provider.

Do not make arbitrary reflection-based `event-class` + method-path YAML hooks the primary extension mechanism.

---

# 9. Built-in generic progression providers

To make MaddPrestige useful without a large plugin stack, V2 should include safe built-in providers where Paper/Bukkit exposes authoritative data.

Recommended built-ins:

## 9.1 Vanilla statistics provider

Expose supported Bukkit/Paper statistics such as, where available and reliable:

- playtime;
- deaths;
- mob kills;
- player kills;
- blocks mined by material/statistic;
- items crafted;
- items used/broken/picked up where available;
- distance traveled;
- animals bred;
- fish caught;
- damage dealt/taken where suitable;
- advancements completed/count where feasible.

Do not invent statistics that the API cannot reliably provide.

## 9.2 Manual/API progress provider

Allow other plugins or staff-controlled systems to submit typed progress to registered counters through the public API.

This is preferable to hardcoding every future MaddKraft system into MaddPrestige.

## 9.3 Placeholder requirement provider

Optionally allow PlaceholderAPI values to be used as requirements using typed parsing/comparison.

This should be clearly labeled less authoritative than a native API adapter where applicable.

Requirements:

- configurable polling/reconciliation frequency;
- no database/expensive provider call per GUI lore render;
- clear errors for unparseable values;
- caching;
- fail closed when required placeholder expansion is unavailable.

---

# 10. Requirement engine

## 10.1 Requirement definition

Each requirement should have at least:

- stable ID;
- provider ID;
- metric ID;
- operator;
- target value;
- measurement scope;
- completion mode;
- scaling strategy/parameters;
- catch-up policy;
- optional filters;
- display metadata/messages;
- optional hidden/secret presentation flag if justified;
- provider health requirements.

## 10.2 Operators

Support type-valid operators such as:

- greater-than / greater-or-equal;
- less-than / less-or-equal;
- equal / not equal;
- range;
- boolean true/false;
- enum/string equality where safe.

## 10.3 Measurement scopes

Required scopes:

- `absolute` / current value;
- `since-stage-start`;
- `since-prestige-start`;
- `since-season-start`;
- `lifetime` where meaningful.

The provider framework must indicate whether a metric can support snapshot/delta measurement.

## 10.4 Snapshot/baseline tracking

When entering a measurement scope, persist the relevant baseline or an equivalent monotonic event counter.

Do not assume current external plugin lifetime values equal “earned this run.”

## 10.5 Completion modes

At minimum:

- `live` — requirement is satisfied only while the condition is currently true;
- `latched` — once achieved during its scope it remains achieved for the remainder of that scope.

Example:

- “Have $1,000,000 balance” should normally be live.
- “Earn $1,000,000 during this Prestige” should normally be latched/accumulated.

## 10.6 Nested requirement trees

Support recursive logical groups with a configurable maximum depth for safety.

Required group modes:

- `ALL`;
- `ANY`;
- `ANY_X_OF_Y`;
- `WEIGHTED/POINTS`.

Example:

```text
ALL
├── universal requirement
└── ANY 2 OF
    ├── economy path
    ├── mcMMO path
    ├── exploration path
    └── community path
```

This is essential for servers that want multiple playstyles to qualify for the same rank/prestige.

## 10.7 Scaling

Scaling must be configurable per requirement or reusable scaling profile.

Required strategies:

- none;
- linear;
- exponential;
- stepped/table-based.

Do not force every requirement to use one global prestige multiplier.

## 10.8 Catch-up

Catch-up must be opt-in per requirement or requirement group.

Support controls such as:

- enabled;
- start date/day threshold;
- reduction rate;
- maximum reduction;
- floor/minimum target;
- rounding rules.

Do not apply a global fractional reduction to discrete objectives unless explicitly configured.

---

# 11. Costs are separate from requirements

A condition and a consumed cost are different concepts.

Example:

- requirement: earn $1,000,000 this run;
- requirement: currently hold $500,000;
- cost: pay $250,000 when ranking up.

Costs must be modeled independently.

Supported cost types should be provider-based and include, where integrations exist:

- Vault economy withdrawal;
- internal MaddPrestige currency;
- external currency;
- item removal;
- configurable safe provider-defined costs.

Before execution:

1. validate all requirements;
2. validate all costs can be paid;
3. validate all required providers/rewards are available;
4. create/persist the operation plan;
5. execute according to transaction semantics.

---

# 12. Reward engine

Reward types should be provider-driven and configurable.

Core/first-party candidates:

- internal currency;
- Vault money;
- existing LuckPerms group/permission node where safe;
- claim blocks via GriefPrevention adapter;
- configurable entitlement unlock;
- vanilla item reward;
- command reward;
- provider-defined reward (crate key, custom item, external currency, etc.).

Each reward should have:

- stable ID;
- type/provider;
- amount/value;
- conditions;
- repeatability;
- display metadata;
- failure policy;
- transaction/idempotency characteristics.

## 12.1 Generic command actions

Generic console command actions are useful but dangerous.

Requirements:

- configurable enabled/disabled;
- allowlist and blocklist support;
- token validation;
- maximum commands per operation;
- recursion/trigger-depth protection;
- no unresolved token execution by default;
- audit logging;
- preview in simulation;
- explicit documentation that arbitrary external command side effects cannot be guaranteed exactly-once across every crash boundary.

Default blocked roots should include critical server/process privilege operations such as `stop`, `restart`, `op`, `deop`, with the exact safety policy configurable only by highly privileged administrators.

Do not permit arbitrary Java reflection, arbitrary SQL, arbitrary filesystem access, or arbitrary code execution through configuration.

---

# 13. Prestige engine

Prestige must be generic and configurable.

At minimum support:

- enabled/disabled;
- required stage(s);
- reset stage;
- prestige count increment;
- current vs lifetime prestige counters;
- prestige requirements;
- prestige costs;
- prestige rewards;
- cooldown;
- maximum prestige or unlimited;
- scaling profiles;
- milestone hooks;
- configurable reset/preserve policy;
- confirmation UI;
- simulation;
- history/audit.

No specific stage such as `madcap` may be hardcoded as the max stage.

## 13.1 Reset/preserve policy

The server owner must explicitly control what a prestige resets or preserves.

Possible internal components:

- current progression stage;
- active requirement progress;
- latched completions;
- baselines/snapshots;
- current prestige-scoped currency;
- purchased perks;
- milestone history;
- season progress;
- historical/lifetime stats.

External system resets must be separate, explicit and disabled by default.

If a provider exposes a safe reset capability (for example a skill plugin), MaddPrestige may support it as an opt-in external reset action. Generic command-based external resets must be marked dangerous/non-transactional and must never be silently enabled.

---

# 14. Prestige currency system

“Tea Leaves” must not be a hardcoded Java concept.

Implement a generic internal currency capability or currency-provider abstraction.

Example server configuration:

```yaml
currencies:
  prestige_points:
    display-name: "Tea Leaves"
    symbol: "🍃"
    decimals: 0
```

Support:

- configurable display name;
- decimal policy;
- earn/spend history;
- balances;
- admin adjustment with audit;
- placeholders;
- reward/cost usage;
- optional external currency provider instead of internal storage.

---

# 15. Entitlement engine

Entitlements represent numeric/boolean limits or benefits contributed by multiple sources.

Examples:

- home limit;
- player warp limit;
- shop limit;
- claim-block bonus;
- cosmetic unlock flag;
- future QoL capacity.

Entitlements need configurable merge strategies:

- `max`;
- `sum`;
- `min`;
- `override` with priority;
- `boolean-or`;
- provider-defined strategy if justified.

This avoids accidental stacking such as base + supporter + prestige home limits when the intended policy is “highest source wins.”

Supporter groups should be read through generic rank/permission sources; MaddPrestige must not own the commercial rank lifecycle.

---

# 16. Milestones

Milestones should be generic, optional and configurable.

Triggers may include:

- prestige count;
- lifetime prestige count;
- stage reached;
- season progress;
- configured provider metric.

Rewards/actions use the normal reward engine.

Milestone IDs and display names are config-defined.

No built-in “Hatter Candidate,” “Looking Glass” or similar names in Java.

---

# 17. Seasons / chapters

Seasons are optional lifecycle containers for progression data, not world-reset controllers.

Support:

- stable season ID;
- display name;
- start/end timestamps or manual lifecycle;
- active/archived status;
- configurable reset/preserve policy;
- season-specific requirement profiles/overrides;
- per-requirement catch-up;
- archive/history;
- leaderboards/statistics where enabled;
- events/API hooks.

A world reset is a separate server event. MaddPrestige may consume external lifecycle events through an adapter but must not own world deletion/regeneration.

---

# 18. Competition module

Replace the hardcoded `MADDHATTER` system with a generic optional competition framework.

Configurable competition fields may include:

- stable ID;
- display name/title;
- eligibility;
- metric/provider;
- duration;
- inactivity/forfeit policy;
- leaderboard size;
- rewards;
- title/group/metadata reward integration;
- start/end/manual controls;
- history/archive.

Competition support should be modular enough to disable entirely.

---

# 19. History and statistics

Do not store only current values.

Persist enough history to answer questions such as:

- when did the player enter each stage?;
- when did they prestige?;
- how many prestiges lifetime/current season?;
- what rewards/costs were applied?;
- what requirement completions occurred?;
- what manual staff changes were made?;
- how much internal prestige currency has been earned/spent?;
- what milestones were earned?;
- what configuration revision governed the action?;

Optional leaderboard/statistics support should use cached/indexed data, not expensive live scans.

---

# 20. Transaction and crash-recovery model

Rank-up, prestige, perk purchase and other consequential state changes must use a recoverable transaction/operation model.

A typical operation:

1. Build immutable operation plan.
2. Validate player state.
3. Validate requirements.
4. Validate provider health.
5. Validate costs.
6. Validate rewards.
7. Persist pending operation with unique operation ID.
8. Execute internal/provider actions in defined order.
9. Persist per-action status where needed.
10. Commit new authoritative MaddPrestige state.
11. Mark operation complete.
12. Emit post-events/announcements after committed state.

On restart, reconcile unfinished operations rather than blindly re-running every step.

## 20.1 Exactly-once limitation

Do not claim impossible guarantees for arbitrary external command actions.

Native providers should be designed idempotently where possible and receive operation IDs when useful.

Generic command rewards/actions are inherently external side effects and may not be safely reversible/deduplicated after a process crash. Document this and prefer native provider APIs for high-value actions.

---

# 21. Persistence

## 21.1 Supported 2.0 backend and deferred extensions

- SQLite — the official MaddPrestige 2.0 production backend, default and recommended for a single Paper server.
- MySQL/MariaDB — deferred post-2.0. No 2.0 production implementation, semantic-parity, outage/failover, shared-database,
  or network-safety claim may be made.

Existing backend-neutral repository and transaction boundaries should remain extensible for a future external-SQL
implementation. They are not evidence that an external backend is supported. Architecture should not preclude
PostgreSQL later, but PostgreSQL is not required for 2.0.

## 21.2 Persistence requirements

- UUID-first records;
- prepared statements/parameter binding;
- schema version table;
- deterministic migrations;
- indexes for common player/history/leaderboard queries;
- asynchronous DB operations where Paper-safe;
- no blocking long queries on the main thread;
- bounded queues/backpressure;
- clean shutdown flush;
- configurable pool/timeout settings when an external DB is implemented post-2.0;
- connection health diagnostics;
- safe startup when DB unavailable according to configured fail policy;
- no manual editing of internal DB required for ordinary administration.

## 21.3 Multi-server/network stance

Single-Paper-server SQLite correctness is required for 2.0.

Multi-process/shared-database deployment support is deferred post-2.0. A future MySQL/MariaDB design must not assume
only one process will ever connect and must qualify its row-lock, deadlock, outage and failover semantics before being
advertised. Until then, configuration and documentation must not imply external-DB or network-mode support.

---

# 22. Event tracking, reconciliation and performance

MaddPrestige may receive high-volume events from XP, economy, blocks, mobs and other plugins.

Requirements:

- do not write to SQL for every XP tick/event;
- cache active player progress;
- batch/flush dirty state;
- use provider reconciliation for authoritative metrics where possible;
- configurable reconciliation intervals;
- reconcile after reload/reconnect/provider recovery where appropriate;
- never perform expensive database queries per PlaceholderAPI render;
- bound internal queues;
- avoid unbounded maps/listeners;
- expose timing/health diagnostics;
- ensure async operations never call Bukkit/Paper APIs from unsafe threads.

Provider adapters should document whether a metric is:

- authoritative queried state;
- event-tracked with reconciliation;
- event-tracked only;
- best-effort/placeholder-derived.

---

# 23. Configuration architecture

## 23.1 Canonical schema

Every exposed configuration field should have schema metadata such as:

- canonical path;
- data type;
- default;
- description;
- examples;
- allowed values;
- validation rules;
- risk level;
- permission required to edit;
- hot-reloadable vs restart-required;
- deprecation/migration metadata.

YAML, commands, GUI and any future web interface operate on this same schema/service.

## 23.2 Recommended file layout

Exact names can change if a better structure is justified, but avoid one 5,000-line config.

Suggested layout:

```text
plugins/MaddPrestige/
  config.yml
  database.yml
  progression.yml
  prestige.yml
  requirements.yml
  rewards.yml
  entitlements.yml
  integrations.yml
  seasons.yml
  competitions.yml
  messages.yml
  gui/
    player.yml
    staff.yml
    setup.yml
  lang/
    en_US.yml
  examples/
  backups/
  revisions/
```

## 23.3 YAML comments

Generated/default YAML should be unusually good.

Comments should explain:

- what the setting does;
- valid values;
- safe examples;
- side effects;
- reload/restart requirements;
- risk warnings.

Avoid both extremes: uncommented cryptic config and encyclopedic comment spam.

## 23.4 Preserve comments/formatting

Command/GUI edits must not destroy useful comments or turn human-readable YAML into a machine-generated dump.

Use a comment-preserving YAML strategy or a safe structured writer capable of retaining canonical comments/order. Unknown forward-compatible keys should be preserved where safe.

## 23.5 Draft / validate / apply

Administrative configuration changes should support a transaction-like draft workflow:

```text
active config
  ↓
editable draft
  ↓
validate
  ↓
diff/preview
  ↓
apply
  ↓
revision + backup
```

Invalid drafts must not replace the last known-good active config.

## 23.6 Revision history

Store configurable number of revisions with:

- revision ID;
- timestamp;
- actor UUID/name/console;
- source (YAML reload, command, GUI, API);
- diff summary;
- validation result.

Support safe rollback.

---

# 24. Command system

Everything that is reasonably configurable through YAML must also be configurable through commands, subject to schema permissions and safety rules.

## 24.1 Human-friendly command layer

Examples (exact syntax may be improved):

```text
/maddprestige setup
/maddprestige stage add <id>
/maddprestige stage remove <id>
/maddprestige stage move <id> <position>
/maddprestige stage group <id> <external-group>
/maddprestige requirement add ...
/maddprestige requirement edit ...
/maddprestige reward add ...
/maddprestige prestige ...
/maddprestige season ...
/maddprestige integration ...
```

## 24.2 Generic schema command layer

Required generic operations:

```text
/maddprestige config get <path>
/maddprestige config set <path> <value>
/maddprestige config add <path> <value>
/maddprestige config remove <path> [value]
/maddprestige config list <path>
/maddprestige config search <text>
/maddprestige config explain <path>
/maddprestige config diff
/maddprestige config validate
/maddprestige config apply
/maddprestige config history
/maddprestige config rollback <revision>
```

## 24.3 Command usability

Use Paper/Brigadier command capabilities where appropriate.

Requirements:

- context-aware tab completion;
- stage IDs autocomplete;
- provider IDs autocomplete;
- provider metric autocomplete;
- mcMMO skill autocomplete when adapter supports it;
- valid enum/value suggestions;
- descriptive validation messages;
- console parity for administrative commands;
- aliases configurable.

Commands should teach the user the valid next step rather than expecting memorization.

---

# 25. Setup wizard

`/maddprestige setup` should be a flagship usability feature.

On first install, if no active progression is configured:

1. Detect available rank/progression/economy providers.
2. Explain optional vs required dependencies.
3. Select rank provider or internal-only stages.
4. If LuckPerms selected, list existing groups and let the owner select progression groups.
5. Order stages.
6. Select baseline behavior.
7. Configure simple requirements/costs/rewards.
8. Configure prestige eligibility/reset behavior.
9. Preview player experience.
10. Validate.
11. Apply as revision 1.

The wizard must never create external ranks without an explicit separate feature that is outside this V2 requirement. For the standard flow, missing groups are errors.

Support resume/cancel without partially applying production configuration.

---

# 26. Staff GUI

The staff GUI is a visual editor for the same canonical configuration model, not a separate config system.

Suggested top-level pages:

```text
MaddPrestige Control Panel
[Setup / Overview]
[Progression]
[Requirements]
[Costs]
[Rewards]
[Prestige]
[Entitlements]
[Integrations]
[Seasons]
[Competitions]
[Players]
[Messages / UI]
[Diagnostics]
[Configuration History]
```

## 26.1 Progressive disclosure

Basic/common controls should be easy to find. Advanced implementation-level controls should live under advanced pages rather than overwhelming a first-time user.

GUI terminology should be human-readable:

- “Progress gained during this Prestige” rather than “snapshot-relative provider delta.”
- “Keep completed once reached?” rather than only “LATCHED.”

## 26.2 Preview and confirmation

Before applying structural/dangerous changes, show:

- exact diff;
- affected stages/players;
- provider availability;
- migration consequences;
- whether restart/reload is required.

Deleting/renaming a stage referenced by players must not be a one-click destructive action. Require replacement/migration handling.

---

# 27. Player UI

Player-facing commands/GUI should make progression transparent.

Suggested capabilities:

- current stage;
- next stage;
- exact requirements and progress;
- costs;
- expected rewards;
- can-rank-up status;
- prestige eligibility;
- what Prestige resets;
- what Prestige preserves;
- prestige rewards;
- internal currency balance;
- milestones;
- history;
- optional leaderboards/statistics.

Prestige should require a clear confirmation screen when it has meaningful resets/costs.

Configurable notification channels may include:

- chat;
- action bar;
- title;
- boss bar;
- sound.

---

# 28. Contextual help and diagnostics

## 28.1 Help

Support contextual help topics instead of one giant command dump:

```text
/maddprestige help stages
/maddprestige help requirements
/maddprestige help measurement
/maddprestige help mcmmo
/maddprestige help scaling
```

Descriptions should come from the canonical schema/provider metadata where possible.

## 28.2 `doctor`

Implement `/maddprestige doctor` and a verbose mode.

Checks should include:

- core health;
- DB connection/schema;
- pending migrations;
- pending/incomplete operations;
- config schema/version;
- rank provider health;
- missing external groups;
- provider health/version support;
- requirements referencing unavailable metrics;
- invalid rewards/costs;
- orphaned stage IDs/player data;
- duplicate IDs;
- broken entitlement mappings;
- PlaceholderAPI expansion health;
- scheduler/cache status;
- last flush/reconciliation status.

Output should be concise by default and actionable.

## 28.3 `why`

Support staff/player-friendly explanations such as:

```text
/maddprestige why <player> rankup
/maddprestige why <player> prestige
```

Return the blocking conditions and exact current/target values.

## 28.4 Simulation / dry-run

Required:

```text
/maddprestige simulate rankup <player>
/maddprestige simulate prestige <player>
/maddprestige config validate
```

Simulation shows:

- requirement results;
- costs that would be consumed;
- stage changes;
- rewards/actions;
- entitlements;
- external groups changed;
- reset/preserve effects;
- unavailable providers;
- no actual mutation.

---

# 29. Error messages

Configuration errors must be written for server owners, not Java developers.

Bad:

```text
Invalid value at requirements.3.value
```

Good:

```text
Could not load requirement "veteran_mining".
Expected a positive integer for:
progression.veteran.requirements.veteran_mining.amount
Found: "seventy-five"
Example: amount: 75
```

Stack traces belong in debug logs when appropriate, not as the only explanation.

---

# 30. Internationalization / text formatting

- UTF-8 everywhere.
- All player-facing text externalized.
- MiniMessage support for formatting where appropriate.
- Language architecture supporting `lang/en_US.yml` and additional locale files.
- English may be the only bundled language initially, but hardcoded English player messages should be avoided.
- GUI titles/lore/messages must be configurable.

---

# 31. PlaceholderAPI

Provide an extensive, cached PlaceholderAPI expansion when PlaceholderAPI is installed.

Candidate placeholders:

- current stage ID/display;
- next stage;
- prestige count;
- lifetime prestige count;
- prestige currency balances;
- season ID/name;
- progress percent;
- requirements completed/total;
- can rank up;
- can prestige;
- milestone status;
- competition/leaderboard data where enabled.

Avoid SQL/provider calls on every placeholder evaluation. Use cached state.

---

# 32. Public API and events

Expose a stable API through Bukkit ServicesManager or another documented Paper-appropriate mechanism.

Capabilities should include:

- query player progression state;
- query stage definitions;
- query prestige count;
- query requirements/progress;
- query entitlement values;
- query currencies;
- register providers;
- submit progress to manual/custom counters;
- request/evaluate rank-up/prestige through supported service methods;
- access read-only season state;
- listen to lifecycle events.

Candidate events:

- pre-rank-up (cancellable where safe);
- post-rank-up;
- pre-prestige;
- post-prestige;
- progress changed;
- milestone achieved;
- season changed;
- competition started/ended;
- config applied;
- provider health changed.

Do not expose internal mutable collections/entities directly.

Document API compatibility/versioning policy.

---

# 33. Security model

Requirements:

- granular permissions;
- owner wildcard available but not required for normal administration;
- separate view/edit/apply/rollback permissions;
- separate force/migration/debug permissions;
- no arbitrary reflection from YAML;
- no arbitrary SQL from commands/config;
- no arbitrary filesystem paths exposed to normal config actions;
- no dynamic class loading from untrusted config;
- safe command-action restrictions;
- sensitive values (DB passwords/tokens) never shown to players or normal GUI users;
- audit all consequential staff changes;
- remote/web control disabled because V2 web UI is not required.

Example permission families:

```text
maddprestige.use
maddprestige.rankup
maddprestige.prestige
maddprestige.admin.gui
maddprestige.admin.config.view
maddprestige.admin.config.edit
maddprestige.admin.config.apply
maddprestige.admin.config.rollback
maddprestige.admin.players
maddprestige.admin.integrations
maddprestige.admin.simulate
maddprestige.admin.migrate
maddprestige.admin.debug
```

Exact nodes may be refined but should remain granular.

---

# 34. Audit logging

Log consequential actions with:

- timestamp;
- actor UUID/name/console;
- target player when applicable;
- operation ID;
- config revision;
- old/new value;
- provider/action;
- outcome/failure reason.

Examples:

- config change;
- config apply/rollback;
- manual progress adjustment;
- stage override;
- prestige override;
- currency adjustment;
- reward/cost operation;
- migration;
- reconciliation repair;
- provider health failure affecting progression.

Support configurable retention/storage strategy.

---

# 35. Integration strategy

For every external plugin, classify the relationship as one or more of:

- **coexistence** — known not to conflict; no direct API hook needed;
- **hook available** — adapter exists but activates only if used;
- **active integration** — current config uses it;
- **required for configured feature** — progression must fail closed if unavailable.

Do not create meaningless direct hooks merely to claim compatibility.

Preferred integration order:

1. official/public API;
2. documented service/event interface;
3. PlaceholderAPI/read-only fallback where acceptable;
4. validated command action for outbound side effects;
5. no integration rather than brittle private/reflection hacks.

---

# 36. Flagship first-party integrations

## 36.1 LuckPerms

See rank adapter requirements above.

## 36.2 Vault

Use as generic economy service where a Vault-compatible economy is configured.

Capabilities may include:

- current balance requirement;
- rank/prestige costs;
- money rewards;
- balance predicates.

“Earnings this run” must not be inferred from current balance. Earnings requires a source-aware transaction/progression provider.

## 36.3 mcMMO

mcMMO is a flagship progression integration and must receive deeper treatment than ordinary coexistence plugins.

Use supported public APIs/events for the actual installed/supported version.

Potential metrics, only where reliable:

- power level;
- per-skill level;
- per-skill XP;
- total XP gained;
- skill XP gained in measurement scope;
- levels gained in measurement scope.

Requirements:

- snapshot/delta support;
- event tracking where useful;
- authoritative reconciliation where API allows;
- provider capability discovery;
- clear unavailable state if mcMMO adapter breaks;
- no mcMMO reset unless explicitly configured through a safe provider reset capability;
- no donor/rank XP multiplier assumption.

## 36.4 PlaceholderAPI

Provide both:

- MaddPrestige output expansion;
- optional generic input requirement provider.

## 36.5 EconomyShopGUI

If a stable API/event makes seller income attributable, expose a server-generated earnings metric.

This is a strong candidate for legitimate “server earnings” because it is a faucet from the server rather than circular player transfers.

Do not scrape private internals if no stable API exists.

## 36.6 QuickShop-Hikari

Support compatibility and useful shop metrics if reliable APIs exist.

Raw player-to-player sales volume must default to **zero progression credit** because circular trading between players/alts can manufacture revenue.

If server owners deliberately enable it, provide clear exploit warning and source/filter options if the API supports them.

Potential non-economic useful capability: entitlement/shop-limit integration if QuickShop exposes a supported mechanism.

## 36.7 UltimateMobCoins / MaddMobCoins

Treat MobCoins as a separate currency/progression source rather than Vault money.

Support native currency requirement/reward/cost only if a stable API exists.

MaddMobCoins may expose server-specific Trial Chamber earning metrics later through the public provider API; this should not be hardcoded into generic core.

## 36.8 PlayTimeManager

Use as an optional progression provider if it exposes useful authoritative values. Also retain a built-in Paper/Bukkit playtime metric so MaddPrestige does not require PlayTimeManager for basic playtime requirements.

## 36.9 GriefPrevention

Useful optional capabilities:

- claim block reward;
- entitlement/bonus claim block adjustment;
- claim-related metrics only where stable API exists.

## 36.10 AdvancedCrates

Useful optional capabilities:

- native virtual key reward where supported;
- crate-open progress metric where reliable events/API exist.

Prefer native API over console commands; keep command reward fallback.

## 36.11 AxPlayerWarps

Potential optional capabilities:

- owned public warp count;
- warp entitlement/limit if supported;
- community/discovery progression metrics if reliably exposed.

## 36.12 CraftEngine

Future/high-value integration for MaddKraft and other servers using CraftEngine:

- custom item rewards;
- item requirement/turn-in validation where supported;
- cosmetic/collectible rewards.

Do not make CraftEngine a core dependency.

---

# 37. MaddKraft stack compatibility baseline

MaddPrestige must be audited/tested to coexist with the current MaddKraft stack. Direct hooks are only required where useful and supported.

## Paper plugins

- EconomyShopGUI — optional active economy/progression hook.
- FancyHolograms — coexistence; use PlaceholderAPI output rather than hard dependency where possible.
- MiniMOTD — coexistence; optional lifecycle command/API use only if a real feature needs it.
- PurpurExtras — coexistence.
- UltimateMobCoins — optional currency/progression hook.
- VeinMiner — coexistence by default; optional progress provider only if reliable events/API are exposed.

## Bukkit/custom plugins

- AdvancedCrates — optional reward/progress hook.
- AxGraves — coexistence.
- AxPlayerWarps — optional progress/entitlement hook.
- AxSellWands — optional source-aware server-earnings hook only if reliable API/event exists.
- AxTrade — coexistence; raw trade volume should not be progression by default due abuse risk.
- Chunky — coexistence.
- Citizens — coexistence; can open commands/GUI through NPC commands without hard dependency.
- ClickVillagers — coexistence / optional API progress source only if later needed.
- CoreProtect — coexistence/audit aid; not a normal progression provider.
- DiscordSRV — optional announcement integration, preferably API where stable or configured command fallback.
- DriveBackupV2 — coexistence; no need to own backups.
- Essentials — coexistence via Vault/permissions; optional entitlement integration.
- EssentialsSpawn — coexistence.
- GriefPrevention — optional entitlement/reward hook.
- GrimAC — coexistence; do not tie player progression to anti-cheat state by default.
- InventoryRollbackPlus — coexistence.
- LiteBans — coexistence; optional eligibility predicate only if explicitly desired later.
- LuckPerms — flagship rank/permission integration.
- MaddMobCoins — optional custom provider via public API.
- MaddRTP — coexistence; optional exploration provider if MaddRTP later exposes useful API/events.
- Maintenance — coexistence/lifecycle awareness if needed.
- mcMMO — flagship progression integration.
- Multiverse-Core — coexistence; optional world-context predicates.
- Multiverse-Inventories — coexistence.
- Multiverse-NetherPortals — coexistence.
- Multiverse-Portals — coexistence.
- PlaceholderAPI — flagship display + generic input integration.
- Plan — coexistence/analytics; do not depend on its DB internals.
- PlayTimeManager — optional progression provider.
- QuickShop-Hikari — optional shop metrics/entitlement hook; player-to-player revenue disabled for progression by default.
- TAB — coexistence through PlaceholderAPI.
- TreeFeller — coexistence; optional provider only if stable event/API exists.
- Vault — flagship economy service integration.
- Simple Voice Chat (`voicechat`) — coexistence.
- VoidGen — coexistence.
- VoidSpawn — coexistence.
- VortexStacker — coexistence; progression providers counting mob kills must avoid double/count artifacts from stacking behavior where relevant.
- WorldEdit — coexistence.
- WorldGuard — coexistence; optional region predicate/provider.

## Current/future MaddKraft additions

- CraftEngine — optional custom item/cosmetic integration.
- MaddResourceWorlds — coexistence by default; optional lifecycle/progress provider through public API/events if later desired.
- future PvP/Court system — separate owner of Court drafting/scoring; optional stats provider only.

Every plugin above must appear in a compatibility audit report for the MaddKraft deployment. “Compatible” is allowed to mean deliberate coexistence with no direct hook.

---

# 38. Economy/progression exploit resistance

Progression systems are exploitable when they count activity rather than value creation.

MaddPrestige must make the data source explicit.

Examples:

- `/pay` transfers must not count as earnings by default.
- QuickShop gross sales must not count by default.
- player-to-player trades must not count by default.
- server sell-shop income may count if attributable reliably.
- server-issued rewards may be filterable/excluded depending on requirement semantics.
- admin/console-granted values should be excluded from “earned” metrics when the provider can distinguish them.

Providers should expose source filters where supported, for example:

```yaml
filters:
  count-server-shop: true
  count-player-transfers: false
  count-admin-granted: false
  count-self-transactions: false
```

Do not pretend a provider can detect a filter it cannot actually distinguish.

Diagnostics/UI should state metric reliability.

---

# 39. Web interface stance

Do not make an embedded web dashboard a V2 launch blocker.

Instead:

- keep the canonical admin/config service UI-agnostic;
- expose stable internal/public APIs;
- keep authentication/network concerns outside the core;
- consider a separate optional web module/companion after core stability.

If a web UI is later implemented, it must use the same draft/validation/revision service and must not directly edit YAML/SQL.

Security requirements would include HTTPS/reverse proxy guidance, strong authentication, CSRF protection, session expiry, audit, origin controls and disabled-by-default network exposure.

---

# 40. Public-plugin onboarding and documentation

A fresh install should be safe and understandable.

## 40.1 Startup output

Concise example:

```text
[MaddPrestige] v2.x starting...
[MaddPrestige] Database: SQLite ✓
[MaddPrestige] LuckPerms: detected ✓
[MaddPrestige] Vault: detected ✓
[MaddPrestige] mcMMO: detected ✓
[MaddPrestige] PlaceholderAPI: detected ✓
[MaddPrestige] No active progression ladder configured.
[MaddPrestige] Run /maddprestige setup to begin.
```

Do not spam dozens of coexistence plugins on every startup unless debug mode is enabled.

## 40.2 Documentation deliverables

Ship/document:

- installation;
- quick start;
- setup wizard;
- YAML structure;
- commands;
- permissions;
- stages/rank adapters;
- requirements;
- measurement scopes;
- costs;
- rewards;
- prestige;
- currencies;
- entitlements;
- providers/integrations;
- mcMMO examples;
- economy examples;
- PlaceholderAPI;
- seasons;
- competitions;
- migration;
- backups/recovery;
- diagnostics;
- API/provider SDK;
- performance guidance;
- troubleshooting;
- changelog/breaking-change policy.

## 40.3 Examples/presets

Include examples such as:

```text
examples/simple-ranks.yml
examples/mcmmo-progression.yml
examples/economy-progression.yml
examples/choice-requirements.yml
examples/seasons.yml
examples/custom-rewards.yml
examples/provider-sdk/
```

A MaddKraft example/preset may exist, but it must not be the generic default.

---

# 41. Versioning and compatibility policy

MaddPrestige should use semantic-style release versioning and explicit config/database schema versions.

Requirements:

- deprecate before removing when practical;
- log clear deprecation warnings;
- automated migrations for deterministic changes;
- backup before migration;
- migration report;
- API version policy;
- provider adapter compatibility ranges;
- unsupported dependency versions reported clearly;
- optional integration failure must not crash unrelated core features.

---

# 42. Testing strategy

Testing must go beyond a successful Gradle/Maven build.

## 42.1 Unit tests

Cover at minimum:

- stage ordering/validation;
- immutable IDs;
- requirement operators;
- nested requirement logic;
- scaling;
- catch-up;
- measurement delta math;
- entitlement merge strategies;
- config schema validation;
- migration transforms;
- transaction state machine;
- permission checks;
- command parsing where testable;
- provider registry/capability logic.

## 42.2 Database tests

For 2.0, run the complete persistence suite against real SQLite. MySQL/MariaDB test environments remain useful
architecture experiments, but are not a 2.0 release gate or support evidence. If external SQL is re-authorized
post-2.0, run the full repository/progression/recovery contract against each claimed backend and version family.

Cover:

- fresh schema;
- migrations;
- rollback/backup behavior;
- concurrent/rapid writes;
- dirty flush;
- restart recovery;
- index/query correctness;
- history retention;
- pending operation recovery.

## 42.3 Paper integration tests

Use a real Paper test server process for acceptance-critical behavior; mocks alone are insufficient.

Test:

- plugin startup/shutdown;
- reload/restart recovery;
- offline players;
- LuckPerms group transitions;
- provider missing/recovery;
- PlaceholderAPI;
- event tracking;
- GUI commands;
- permission contexts;
- high-volume progress events;
- exact supported Minecraft/Paper target used by MaddKraft.

## 42.4 Dependency integration tests

For flagship integrations, test against actual supported plugin versions/jars where licensing/distribution permits.

At minimum:

- LuckPerms;
- Vault + configured economy;
- mcMMO;
- PlaceholderAPI.

For MaddKraft release qualification, test all active MaddKraft adapters and coexistence assumptions on a clone/test server.

## 42.5 Fault injection

Explicitly test:

- DB unavailable at startup;
- DB lost mid-operation;
- provider disabled/reloaded;
- missing LuckPerms group;
- invalid YAML;
- corrupted/partial migration state;
- server shutdown during rank-up/prestige;
- external command reward failure;
- duplicate event delivery;
- missing placeholder expansion;
- unsupported plugin version.

---

# 43. Acceptance-test matrix

The following are release gates unless a dated owner scope amendment explicitly classifies an item `Later`. A64 is the
only current post-2.0 deferral; it has not passed and must be re-authorized before it can become a release gate again.

| ID | Area | Acceptance test | Expected result |
|---|---|---|---|
| A01 | Fresh install | Start with only Paper + MaddPrestige | Starts safely, generates documented config, no progression active until configured |
| A02 | Setup | Run `/maddprestige setup` | Guided setup can create an active simple ladder without file editing |
| A03 | Genericity | Search compiled source/resources for MaddKraft rank names | No MaddKraft-specific names in generic core logic/default public profile |
| A04 | IDs | Change a stage display name | Existing player stage remains valid because internal ID is unchanged |
| A05 | LP validation | Configure nonexistent LP group | Apply fails clearly; group is not created |
| A06 | LP isolation | Player holds progression + supporter + staff groups, then ranks up | Only managed progression group changes |
| A07 | LP reconcile | Manually alter managed progression group | Configured reconciliation policy behaves exactly as documented |
| A08 | Rank order | Reorder stages in draft | Validation/diff shows impact before apply |
| A09 | Requirement ALL | Configure two required metrics | Rank-up blocks until both complete |
| A10 | Requirement ANY_X | Configure any 2 of 4 | Exactly two qualifying children satisfy group |
| A11 | Nested requirements | Nest groups within groups | Evaluation and GUI explanation are correct |
| A12 | Weighted requirements | Configure point-weighted electives | Threshold evaluates correctly |
| A13 | Live completion | Balance requirement falls below target after reaching it | Requirement becomes incomplete |
| A14 | Latched completion | Earned-total requirement reaches target then value changes | Remains completed for scope |
| A15 | Stage baseline | Use `since-stage-start` | Only delta after stage entry counts |
| A16 | Prestige baseline | Use `since-prestige-start` | Pre-existing lifetime value does not count |
| A17 | Season baseline | Use `since-season-start` | Correct season delta used |
| A18 | Scaling linear | Configure linear scaling | Values match documented formula |
| A19 | Scaling exponential | Configure exponential scaling | Values match documented formula |
| A20 | Scaling stepped | Configure table/steps | Correct threshold chosen |
| A21 | Catch-up disabled | Requirement catch-up false | No reduction occurs |
| A22 | Catch-up enabled | Requirement catch-up true | Only that requirement is reduced, respecting floor/rounding |
| A23 | Cost distinction | Requirement met but insufficient cost balance | Rank-up blocked without consuming anything |
| A24 | Cost execution | Sufficient Vault balance | Correct amount withdrawn exactly once under normal operation |
| A25 | Reward execution | Rank reward configured | Reward applied after successful committed operation |
| A26 | Simulation | Simulate rank-up | Exact planned changes shown; no mutation occurs |
| A27 | Prestige confirmation | Player initiates destructive Prestige | Clear reset/preserve confirmation before commit |
| A28 | Prestige stage | Config required/reset stages | No hardcoded max/reset stage assumptions |
| A29 | Prestige cap | Configure unlimited and finite max | Both behaviors work |
| A30 | Currency | Rename internal currency display | Logic/data remain valid; UI uses new name |
| A31 | Entitlement max | Base 1, supporter 15, prestige 8 with merge=max | Effective value 15 |
| A32 | Entitlement sum | Configure sum strategy | Values sum exactly as documented |
| A33 | Season archive | End/start season | History preserved according to configured reset policy |
| A34 | Competition disabled | Disable module | No competition commands/UI noise |
| A35 | Migration backup | Upgrade old schema | Config/DB backup created before migration |
| A36 | Ambiguous migration | Old rank mapping cannot be inferred | Migration stops and asks for explicit mapping; does not guess |
| A37 | Old groups | Upgrade MaddKraft old config | `odd`, `mad`, `unbound`, patron groups are never auto-created |
| A38 | Config comments | Change setting through command/GUI | Canonical comments/order remain intact where designed |
| A39 | Config draft | Edit values | Production behavior unchanged until apply |
| A40 | Config validation | Draft invalid provider metric | Apply blocked with actionable error |
| A41 | Config rollback | Apply revision then rollback | Previous valid behavior restored |
| A42 | Command parity | Modify representative settings via YAML/command/GUI | All produce equivalent canonical model |
| A43 | Tab completion | Enter provider/metric command | Only valid providers/metrics/values suggested |
| A44 | Help | Ask help for measurement mode | Clear plain-language explanation displayed |
| A45 | Doctor healthy | Healthy deployment | Reports healthy with concise provider/config/database status |
| A46 | Doctor broken group | Remove configured LP group | Doctor identifies exact affected stage |
| A47 | Why | Player cannot rank up | Returns exact blocking requirements/cost/provider |
| A48 | mcMMO absolute | Skill/power requirement | Reads correct authoritative value |
| A49 | mcMMO delta | XP gained since Prestige | Only current-scope progress counts |
| A50 | mcMMO outage | Disable/unavailable mcMMO with active requirement | Requirement becomes unavailable/fail-closed; core remains running |
| A51 | Vault outage | Economy cost requires Vault then Vault unavailable | Operation blocked safely; no partial state |
| A52 | PAPI output | Render MaddPrestige placeholders repeatedly | Correct cached values, no DB query per render |
| A53 | PAPI input failure | Input placeholder unavailable/unparseable | Requirement unavailable with actionable diagnostic |
| A54 | QuickShop default | Player cycles money through QuickShop | No progression earnings credit under default policy |
| A55 | Command safety | Configure blocked command action | Validation/execution refuses according to policy |
| A56 | Permissions | Staff with view but not apply permission | Can inspect but cannot mutate/apply |
| A57 | Audit | Staff manually edits player Prestige | Actor/target/old/new/timestamp logged |
| A58 | Restart | Restart with dirty cached progress | Progress persists after flush/recovery |
| A59 | Crash recovery | Interrupt a persisted pending internal operation | Restart reconciles without blindly duplicating internal rewards |
| A60 | External side effect | Fail during generic command reward boundary | State/diagnostic records uncertainty; plugin does not falsely claim exact rollback |
| A61 | Offline player | Rank/repair offline player | Works safely or clearly documents online-only limitation per provider |
| A62 | High event volume | Generate sustained XP/progress events | No SQL write per event; TPS impact remains acceptable |
| A63 | DB migration | SQLite schema upgrade with real data | Data preserved and migration report generated |
| A64 | MySQL/MariaDB (deferred post-2.0 by owner decision dated 2026-08-17) | When external SQL is re-authorized, run the same core progression/recovery tests | Not a 2.0 gate; any future supported backend must match the applicable SQLite semantics before support is claimed |
| A65 | API provider | Register example third-party progression provider | Metric appears dynamically in commands/GUI and evaluates correctly |
| A66 | API events | Listen for rank/prestige events | Pre/post ordering matches documented contract |
| A67 | Optional adapter failure | Break one optional integration | Unrelated features remain functional |
| A68 | Internationalization | Replace language strings with Unicode/MiniMessage | UI/messages render correctly |
| A69 | GUI safety | Delete referenced stage via GUI | Requires migration/replacement; no orphaning |
| A70 | Public example | Configure Member→Adventurer→Veteran server with no MaddKraft names | Full rank/prestige loop works |
| A71 | MaddKraft profile | Configure Wanderer→Curious→Dreamer→Tea Guest→Wonderlander→Madcap | Uses existing LP groups and never creates hierarchy |
| A72 | Supporter coexistence | MaddKraft player holds `mad_hatter` + progression group | Prestige only changes progression membership |
| A73 | Stack coexistence | Boot MaddKraft full plugin stack | No classloading/conflict errors attributable to MaddPrestige |
| A74 | Resource-world separation | Resource reset occurs | MaddPrestige does not attempt world lifecycle management |
| A75 | PvP separation | Future PvP/Court plugin present | MaddPrestige consumes metrics only when configured; does not own drafting/scoring |
| A76 | Documentation | Fresh admin follows Quick Start only | Can configure a working simple deployment without source inspection |

Codex may add more tests but may not silently remove these gates without explicit approval.

---

# 44. MaddKraft deployment profile

This section is a flagship deployment profile, not generic core behavior.

## 44.1 Progression groups

Current external LuckPerms progression architecture:

```text
default / Wanderer
  ↓
curious / Curious
  ↓
dreamer / Dreamer
  ↓
tea_guest / Tea Guest
  ↓
wonderlander / Wonderlander
  ↓
madcap / Madcap
```

LuckPerms remains authoritative for group definitions, inheritance, weights, prefixes and display.

MaddPrestige may manage direct membership only in configured progression groups.

## 44.2 Supporter groups

Supporter groups are parallel to progression and are external to MaddPrestige ownership.

Current working five-tier direction includes Tea Patron, Hatter and Mad Hatter, with two middle names still not final. This must not block MaddPrestige core development.

MaddPrestige may read supporter permissions/groups as entitlement sources if configured but does not create/remove commercial groups as part of store fulfillment.

## 44.3 MaddKraft economy/progression context

- Essentials/Vault money is main trading currency.
- EconomyShopGUI is sell-only and acts as a controlled server money faucet.
- QuickShop-Hikari is player-to-player physical commerce and should not count toward Prestige earnings by default.
- AxTrade is player-to-player direct trade and should not count toward economic progression by default.
- MobCoins are a separate currency earned through the Trial Chamber system; they should remain conceptually separate from Vault earnings.
- mcMMO is an existing progression layer and a major MaddPrestige provider.
- future weekly objectives/Rabbit Holes are deferred and must not be mandatory requirements until implemented.
- Prestige should preserve playstyle choice rather than force all players through identical money + mcMMO + PvE + quest checklists.

## 44.4 Prestige balance is intentionally not frozen here

Do not hardcode or treat current prototype values as final:

- old rank costs;
- $25M base Prestige fee;
- $40M earnings requirement;
- 500k mcMMO XP;
- Rabbit Hole/decree/boss counts;
- 18% linear scaling;
- Tea Leaf reward cadence;
- old milestone thresholds.

The framework should support such values, but MaddKraft production numbers should be tuned later using actual economy/progression data.

## 44.5 World/resource/PvP boundaries

MaddPrestige does not own:

- `resource_end` resets;
- `resource_nether` resets;
- permanent world resets;
- Court drafting;
- PvP season scoring.

MaddResourceWorlds and future PvP systems own those domains. MaddPrestige may consume their public events/metrics later through optional providers.

---

# 45. Development phases and hard gates

Do not attempt the entire product as one uncontrolled rewrite. Implement in phases. Each phase must leave the repository building and tested.

## Phase 0 — Repository audit and implementation plan

Deliver:

- current architecture map;
- current commands/data/config schema inventory;
- useful code to preserve;
- technical debt/obsolete code inventory;
- dependency/API audit;
- migration risks;
- proposed package/module architecture;
- test harness plan;
- exact build tool/Java/Paper target based on repository/environment;
- list of unresolved choices requiring owner approval.

**Gate:** No destructive migration or large rewrite until Phase 0 report is complete.

## Phase 1 — Core foundations

Implement/refactor:

- immutable IDs/domain entities;
- canonical config schema/metadata;
- validation service;
- draft/revision model;
- SQLite persistence abstraction;
- schema versioning/migrations framework;
- audit framework;
- provider registry/capability model;
- transaction/operation state machine skeleton;
- baseline test infrastructure.

**Gate:** unit/database tests green; old data untouched except explicit test fixtures.

## Phase 2 — Stage/rank architecture

Implement:

- arbitrary ordered stages;
- internal stage state;
- rank adapter interface;
- LuckPerms adapter;
- managed-group isolation;
- reconciliation policies;
- old-config detection/migration scaffolding.

**Gate:** A01–A08, A35–A37 subset relevant to stage migration.

## Phase 3 — Requirements, costs and rewards

Implement:

- typed metrics;
- requirement tree;
- measurement scopes;
- snapshots;
- live/latched modes;
- scaling;
- catch-up;
- cost engine;
- reward engine;
- generic command action safety;
- built-in vanilla statistics provider;
- manual/API progress provider.

**Gate:** A09–A26, A55.

## Phase 4 — Prestige, currency, entitlements, seasons/history

Implement:

- Prestige lifecycle;
- reset/preserve policies;
- internal currency;
- entitlement merge engine;
- milestones;
- season/chapter lifecycle;
- generic competition module or defer competition behind feature flag if core scope is too large, but do not retain hardcoded MaddHatter;
- history/statistics;
- pending-operation recovery.

**Gate:** A27–A34, A58–A60.

## Phase 5 — Flagship integrations

Implement and test:

- Vault;
- mcMMO;
- PlaceholderAPI;
- EconomyShopGUI if stable public API/event is verified;
- QuickShop-Hikari compatibility and optional metrics if stable API verified;
- PlayTimeManager if beneficial.

Do not fake integrations where no reliable API exists.

**Gate:** A48–A54 plus provider outage tests.

## Phase 6 — Administration and usability

Implement:

- setup wizard;
- full command parity;
- tab completion;
- staff GUI;
- player GUI;
- config search/explain;
- doctor;
- why;
- simulation;
- previews;
- configuration history/rollback;
- high-quality error messages;
- comment-preserving YAML updates.

**Gate:** A38–A47, A56–A57, A69.

## Phase 7 — Additional MaddKraft/public integrations

Audit and implement only useful stable hooks for:

- AdvancedCrates;
- UltimateMobCoins;
- GriefPrevention;
- AxPlayerWarps;
- AxSellWands;
- CraftEngine when available;
- DiscordSRV;
- optional WorldGuard/world predicates;
- MaddKraft custom provider hooks.

For the remainder of MaddKraft stack, perform coexistence validation and document status.

**Gate:** A71–A75.

## Phase 8 — Public API, hardening, docs and performance

The original Phase 8 design included MySQL/MariaDB. The owner product-scope decision dated 2026-08-17 preserves that
future design intent but defers its production implementation and parity work post-2.0. The authoritative decomposition
is now:

- **8B — runtime/API/events:** owner-accepted checkpoint.
- **8C — SQLite Persistence, Migration, Backup & Recovery Hardening:** coordinated safe SQLite backup; backup
  metadata/checksum/integrity verification; disposable restore rehearsal; populated old-schema-to-current migration
  fixtures; migration interruption/failure handling; schema version/checksum/gap/future-version handling;
  corrupt/truncated backup or database handling where practical; database/config compatibility and startup diagnostics;
  exact populated-state restart/recovery qualification; preservation of accepted transaction, journal, lease,
  uncertainty, reconciliation and configuration-publication invariants; retention of existing backend-neutral
  boundaries; explicit documentation that external SQL is deferred rather than supported.
- **8D — i18n, generic example, public docs and admin UX.**
- **8E — performance plus fault/dependency qualification.**
- **8F — packaging and release hardening.**

The following are explicitly deferred post-2.0: HikariCP integration solely for MySQL/MariaDB; MySQL and MariaDB
production repositories; three-backend parity suites; MySQL/MariaDB row-lock/deadlock semantics; external-DB
outage/failover qualification; and multi-process/shared-database deployment support.

**2.0 gate:** A61–A63, A65–A68, A70, A76 plus the full regression suite. A64 is `Later`, not `Satisfied`.

**Phase 8 exit reconciliation — 2026-08-28:** A76 and every Phase 8-owned release-hardening gate pass, so Phase 8 is
complete. The broader 2.0 gate above is not yet complete because A63 deliberately retains the real MaddKraft
clone/deployment migration in Phase 9. Phase completion therefore authorizes release-candidate publication work, not
GA or production deployment.

## Phase 9 — MaddKraft migration and production qualification

On a clone/test environment:

- back up old MaddPrestige DB/config;
- explicitly map/migrate desired old player data;
- deploy current MaddKraft progression groups;
- verify supporter/staff isolation;
- run real mcMMO/economy scenarios;
- test restart/crash/provider outage;
- run full stack coexistence test;
- inspect economy exploit vectors;
- manually test with OP/admin and non-OP accounts;
- do not enable production Prestige balance until actual requirements/rewards are approved.

## Phase 10 — Optional web UI

Only after V2 core is stable and if it materially improves administration.

Prefer a separate module/companion using the same canonical admin/config service.

---

# 46. Codex working rules

Codex must follow these engineering rules while implementing this specification:

1. **Inspect before changing.** Read the current repository, build files, current source, config, database/migration code and tests before designing replacements.
2. **Do not guess third-party APIs.** Use the actual installed/supported plugin versions, official public docs/source and build artifacts. If an API is unavailable, say so and use a documented fallback or leave the integration coexistence-only.
3. **Prefer public APIs.** Avoid private/internal/reflection hooks unless there is no reasonable alternative and the tradeoff is explicitly documented.
4. **No silent scope reduction.** If a requirement cannot be implemented safely, report it rather than omitting it.
5. **No TODOs in production-critical paths** when marking a phase complete.
6. **Keep repository buildable.** Commit/checkpoint logically and keep tests green between phases.
7. **Preserve old data.** Never destructively overwrite production-like config/DB fixtures during development.
8. **Write tests with each feature.** Do not postpone all testing to the end.
9. **Document deviations.** If the repository/API environment makes a specified design impractical, explain the issue and propose the smallest safer alternative.
10. **Performance is a feature.** Do not perform sync DB/provider work in hot event/placeholder paths.
11. **Fail closed.** Unavailable required providers block affected progression rather than granting it.
12. **Use exact external group allowlists.** Never scan/remove arbitrary LP groups based on naming assumptions.
13. **Do not make MaddKraft branding core behavior.** MaddKraft belongs in deployment config/examples only.
14. **No web UI before core acceptance gates.**
15. **Maintain project status.** Keep a concise `STATUS.md` or equivalent with completed phase, current tests, known issues and next actions.
16. **Maintain architectural decisions.** Record important implementation deviations/decisions in `DECISIONS.md` or equivalent.

---

# 47. Definition of done for V2

MaddPrestige V2 is not “done” because the JAR compiles or the MaddKraft server boots.

V2 is release-ready when:

- an unrelated server owner can configure a functioning rank/prestige ladder without source edits;
- arbitrary existing LuckPerms progression groups work safely;
- no missing group is auto-created;
- supporter/staff/unrelated groups are preserved;
- requirements support multiple providers, measurement scopes and choice logic;
- mcMMO integration works and survives restart/provider failure;
- economy costs/rewards are transactional as far as provider contracts permit;
- exploit-prone economic sources are disabled/safeguarded by default;
- YAML, commands and GUI share one canonical model;
- YAML remains human-readable and documented;
- setup/help/doctor/why/simulation make the plugin understandable;
- SQLite passes the complete applicable core, migration, backup, restore and recovery tests; MySQL/MariaDB remains an
  explicit post-2.0 deferral rather than an implied support claim;
- migrations are backed up and tested;
- public API/provider SDK are documented;
- optional integrations fail independently;
- performance tests show no obvious hot-path DB abuse;
- the acceptance matrix passes;
- MaddKraft migration passes on a test clone;
- documentation is sufficient for a new owner to deploy the plugin.

---

# 48. Final product vision

The quality bar is:

> A server owner can install MaddPrestige alongside LuckPerms, Vault, mcMMO or other progression/economy plugins; select their existing ranks; define how players progress; decide what Prestige means; configure everything through commented YAML, commands or a staff GUI; diagnose problems without source code; safely update/migrate the plugin; and extend it through a documented provider API.

MaddKraft should be able to run:

```text
Wanderer → Curious → Dreamer → Tea Guest → Wonderlander → Madcap → Prestige
```

while another server can run:

```text
Member → Adventurer → Veteran → Elite → Master → Prestige
```

with the same JAR and no source modification.

That is the required standard for MaddPrestige V2.
