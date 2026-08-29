# MaddKraft clone environment contract

## Qualification identity

The clone must be a disposable, access-controlled copy of the owner-designated MaddKraft server. It must not share a
server directory, database, port, RCON credential, Discord webhook/channel, backup target, plugin API token, or
scheduled task with production. Network-facing integrations are disabled or redirected to test endpoints before the
first boot. The V1 JAR is removed, not co-loaded; its entire data directory remains preserved by the migration backup.

The repository-supported runtime baseline is Java 25 and Paper 26.1.2 build 74. Broader Java/Paper compatibility is
not claimed. Phase 9 starts with exact distribution `MaddPrestige-2.0.0-rc.1.jar`, 16,507,914 bytes, SHA-256
`0D961DB73D6C44CF18986CA42B7950914CEA5A1598BA7D41D0E919BFB76C6513`. Record a new hash for every supplied clone
artifact and stop on an unexplained delta.

## Required qualification stack

| Component | Accepted identity or boundary | Qualification role |
|---|---|---|
| JVM | Temurin Java 25.0.3+9 LTS was accepted; Maven enforces Java 25 | Required process identity. Record vendor/full `java -version`. |
| Paper | 26.1.2 build 74, API `26.1.2.build.74-stable` | Required server. Record server JAR hash and Paper commit/banner. |
| MaddPrestige | Exact Phase 8F artifact above | Required candidate under test. No local rebuild substitution. |
| LuckPerms | 5.5.71 accepted for Phase 7/8 deployment evidence | Required rank authority and offline group projection. Export groups/users before and after. |
| Vault | VaultUnlocked 2.20.2 runtime; repository compiles against Vault API 1.7.1 | Required service boundary for economy tests. |
| Essentials economy | EssentialsX 2.22.0 in the accepted Phase 7 snapshot | Required configured Vault Economy implementation unless the owner supplies and records a later accepted replacement. |
| mcMMO | 2.2.053 | Required real progression provider. Preserve its player data and exact event/API behavior. |
| PlaceholderAPI | 2.12.2 production runtime gate / Phase 8E qualification; historical Phase 7 inventory used 2.12.3 | Required display/input coexistence and cache qualification where configured. The current clone version is unknown until freshly inventoried. |
| EconomyShopGUI | 7.2.0; API contract 1.10.1 | Required controlled sell-side scenario, but diagnostics-only/zero-credit in the current adapter. |
| QuickShop-Hikari | 6.2.0.11 | Required P2P exploit/coexistence scenario; always zero progression credit by default. |
| AxTrade | 1.27.0 in accepted stack | Required P2P coexistence/exploit scenario; no MaddPrestige integration or credit. |
| UltimateMobCoins | 2.1.0 | Required separate-currency coexistence. Any use is explicit PAPI/fallback, never Vault earnings. |
| GriefPrevention | 16.18.7 | Required if current MaddKraft config uses native claim metrics/reward; otherwise still present for coexistence. |
| AxPlayerWarps | 1.17.2 | Present for coexistence only; the accepted integration status is unavailable/deferred. |
| AxSellWands | 1.16.4 | Present for coexistence only; no earnings credit without a future verified source-aware public contract. |
| DiscordSRV | 1.30.5 in accepted snapshot | Present for coexistence/fallback; redirect or disable external messages in the clone. |
| WorldEdit / WorldGuard | WorldEdit 7.4.4+7546-f9e033f; WorldGuard 7.0.18+2392-fa605e6 | Present if WG predicates are configured and for full-stack coexistence. WG adapter is read-only. |
| CraftEngine | 26.7.4 | Present when the current MaddKraft stack uses it; exact public item/reload boundary is accepted. |

The exact runtime gates in `PhaseSevenOptionalIntegrationManager` are Vault 2.20.2, mcMMO 2.2.053,
PlaceholderAPI 2.12.2, EconomyShopGUI 7.2.0, QuickShop-Hikari 6.2.0.11, GriefPrevention 16.18.7, WorldGuard
7.0.18+2392-fa605e6, and CraftEngine 26.7.4. Do not guess that a different version is compatible. A changed current
MaddKraft artifact must be reviewed and qualified as a deliberate delta.

PlaceholderAPI has an explicit evidence split. Version 2.12.2 is the current production runtime gate and the accepted
Phase 8E qualification identity. Version 2.12.3 appears in the historical accepted Phase 7 full-stack inventory; that
inventory is not a fresh Phase 9 attestation and neither version may be called the current live MaddKraft version
without a new clone inventory. Before real clone qualification, record the installed PlaceholderAPI artifact and hash.
If it is 2.12.2, it matches the current gate. If it is 2.12.3 or any other version, do not silently treat it as
qualified: explicitly test and qualify that version or deliberately revise the runtime gate with appropriate evidence
before claiming production readiness. This is an evidence-precision requirement, not a dependency upgrade.

## Full-stack coexistence inventory

The accepted Phase 7 snapshot classified 47 JARs. The current clone must include its actual current equivalents and a
new descriptor/version/size/SHA-256 inventory. The historical list is evidence, not a license to omit current plugins.

| Relationship | Plugins from accepted snapshot |
|---|---|
| Native MaddPrestige boundary | LuckPerms, Vault/VaultUnlocked, mcMMO, PlaceholderAPI, EconomyShopGUI zero-credit diagnostics, QuickShop-Hikari zero-credit diagnostics, GriefPrevention, WorldGuard, CraftEngine |
| Reviewed generic fallback | AdvancedCrates, UltimateMobCoins, DiscordSRV |
| Deferred/unavailable direct integration | AxPlayerWarps, PlayTimeManager |
| Coexistence only | AxSellWands, Chunky, MaddMobCoins, MaddRTP, Multiverse-Core, Multiverse-Inventories, Multiverse-NetherPortals, Multiverse-Portals, VoidGen, VoidSpawn, VortexStacker, WorldEdit |
| Deliberate unrelated/no direct hook | AxGraves, AxTrade, Citizens, ClickVillagers, CoreProtect, Essentials/EssentialsSpawn, FancyHolograms, GrimAC, InventoryRollbackPlus, LiteBans, Maintenance, MiniMOTD, Plan, PurpurExtras, TAB, TreeFeller, VeinMiner, voicechat |
| External service/backups | DriveBackupV2; it does not replace the Phase 9 migration backup/restore rehearsal |
| Replaced artifact | MaddPrestige 1.2.0 is source evidence only and must not be loaded beside V2 |

Before the run, reconcile this inventory with `docs/evidence/phase7/A73_PLUGIN_METADATA_AND_DISPOSITION.md`. Any current
artifact not in the historical list is added to the current run manifest with its dependency descriptor, intended
relationship, owner, and failure expectation. No direct hook is invented merely because a plugin is installed.

## Unavailable and future systems

- `MaddResourceWorlds` was not an artifact in the accepted 47-JAR snapshot. If available in the owner-designated
  current stack, include and hash it as an external lifecycle owner. If unavailable, execute only the repository
  boundary/static checks and retain A74's real reset rerun as a production-readiness blocker.
- A future PvP/Court plugin is not yet deployed evidence. Represent it only by the accepted generic provider boundary
  until the actual owner plugin and public metric contract exist. Never implement or simulate drafting/scoring as a
  MaddPrestige responsibility.
- PlayerWarps/AxPlayerWarps remains coexistence-only/unavailable at the accepted boundary. Do not configure a provider
  ID that the runtime does not publish.
- CraftEngine is optional to generic MaddPrestige but required in a MaddKraft clone if the current server uses its
  item metric or reward. Registry readiness, reload completion, disable/re-enable, and stale generation are separate
  checks.

## Directory and data layout

The clone must preserve these inputs before V2 starts:

- legacy `plugins/MaddPrestige/config.yml`;
- legacy `plugins/MaddPrestige/maddprestige.db` plus any WAL/SHM/journal sidecars present after shutdown;
- every other file under legacy `plugins/MaddPrestige/`;
- LuckPerms storage/export and contextual membership evidence;
- current plugin configs/data needed to reproduce provider state;
- the exact plugin JAR inventory and server configuration.

V2 creates or consumes these separate local paths under `plugins/MaddPrestige/`:

- `maddprestige-v2.sqlite`;
- `backups/` for V2 schema-migration database/manifest pairs;
- `configuration/active-revision` and `configuration/revisions/<revision>/`;
- `progression.yml`, `requirements.yml`, `rewards.yml`, `lifecycle.yml`, and `integrations.yml`;
- locale/message files created by the production message service.

The legacy DB/config are never renamed over these V2 paths. Preserve the stopped clone checkpoint so rollback can
restore the entire directory rather than mixing individual files from two states.

## MaddKraft stage contract

Use `qualification/phase9a/maddkraft-clone/progression.yml` as the review fixture, then publish it only through the
canonical draft/validate/preview/acknowledge/apply workflow.

| Internal stage | Display | LP relationship | MaddPrestige ownership |
|---|---|---|---|
| `wanderer` | Wanderer | implicit LuckPerms `default` baseline | `projection: none`; no direct group mutation |
| `curious` | Curious | existing `curious` | managed permanent context-free direct membership |
| `dreamer` | Dreamer | existing `dreamer` | managed permanent context-free direct membership |
| `tea_guest` | Tea Guest | existing `tea_guest` | managed permanent context-free direct membership |
| `wonderlander` | Wonderlander | existing `wonderlander` | managed permanent context-free direct membership |
| `madcap` | Madcap | existing `madcap` | managed permanent context-free direct membership |

LuckPerms owns group definitions, inheritance, weights, prefixes, and display metadata. Before apply, prove all five
projected groups exist and record the implicit default group behavior. Missing groups block validation; operators must
fix LuckPerms outside MaddPrestige and rerun validation. `mad_hatter`, supporter, staff, title, event, temporary, and
contextual groups are never added to the managed set.

The profile deliberately includes no requirements, costs, rewards, currencies, milestones, Rabbit Holes, economy
thresholds, mcMMO threshold, scaling, or Tea Leaf cadence. Those remain disabled until separate owner approval based
on actual data.

## Boot, load-order, and log contract

MaddPrestige declares `load: POSTWORLD` and soft dependencies, not hard dependencies. Paper should order available
soft dependencies first, but runtime composition still validates exact plugin/service presence, version, health, and
generation. A configured capability can therefore block its feature without crashing unrelated core behavior.

Capture from JVM invocation through the final shutdown line:

- Java/Paper banner and plugin enable order;
- every plugin descriptor/version and dependency warning;
- MaddPrestige migration/backup/configuration/recovery banner;
- provider registration, health, generation, loss, and rebind evidence;
- command registration/alias conflicts;
- listener exceptions, scheduler/thread warnings, classloading/linkage failures, and watchdog/TPS evidence;
- Doctor verbose before operations, after each outage, after recovery, and after unchanged restart;
- complete shutdown order, final flush, service/listener unregister, and process exit status.

Reject any unexplained MaddPrestige-attributable classloading conflict, duplicate service/listener, unsafe thread call,
plugin disable, incomplete operation hidden from Doctor, or shutdown failure. Hash the raw log and retain a sanitized
copy; sanitization must not remove failure lines, identities, operation IDs, or timing needed to audit the result.

## Environment preflight checklist

- [ ] Owner names the disposable clone path and confirms it is not production.
- [ ] Java, Paper, candidate JAR, and every plugin hash are recorded.
- [ ] V1 JAR is absent from the load directory; V1 data is preserved by verified backup.
- [ ] Network endpoints, webhooks, stores, RCON, and backup schedules are redirected/disabled.
- [ ] Test ports and world paths cannot collide with production.
- [ ] Required LP groups already exist; LP export is captured.
- [ ] At least five controlled accounts/UUIDs exist: admin, non-OP staff, ordinary player, supporter+progression, offline.
- [ ] Vault Economy, mcMMO player data, shop/trade test funds/items, and rollback checkpoints are seeded.
- [ ] No production balance or rewards are configured.
- [ ] Full console and audit capture is enabled with secrets redacted.
- [ ] Stop/restore procedure has been rehearsed before any migration mutation is authorized.
