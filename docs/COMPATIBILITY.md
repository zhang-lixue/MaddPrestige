# Plugin compatibility

MaddPrestige treats every third-party plugin as optional. All integrations are loaded after their provider, discovered at runtime, and isolated from provider implementation classes. A missing, disabled, or changed optional plugin therefore cannot create a hard class-loading dependency in MaddPrestige.

Use `/mp gui` → **Compatibility dashboard** to see the live installed/enabled/version state of every entry below. Operators can disable a direct adapter under `integrations.compatibility` and restart. GUI appearance, slots, permissions, and staff edit increments are under `gui` in `config.yml`.

## Direct adapters

| Plugin | Integration contract |
|---|---|
| LuckPerms | Public API for progression/patron groups, Hatter metadata, and entitlement permissions. |
| Vault / VaultUnlocked | Standard service-provider API for balances, withdrawals, deposits, and formatting. |
| PlaceholderAPI | Registers the `maddprestige` expansion; TAB and FancyHolograms can consume it. |
| mcMMO | Reflective public XP-event listener; no mcMMO classes are linked at plugin startup. |
| EconomyShopGUI | Successful server-shop sell events contribute configurable weighted server earnings. |
| QuickShop-Hikari | Successful seller proceeds contribute configurable weighted server earnings. |
| UltimateMobCoins | Receive events can contribute configurable weighted earnings; the default weight is `0.0`. |
| GriefPrevention | Permanent claim rewards use public player-data methods and fail closed if unavailable. |
| Essentials / EssentialsX | Economy is reached through Vault; home allowances are emitted as permission nodes. |

## Explicit coexistence support

The following plugins are registered as optional soft dependencies and shown in the staff dashboard. MaddPrestige does not replace their listeners, data, worlds, packets, inventories, punishments, NPCs, regions, or scheduled tasks:

- Chunky, Citizens, ClickVillagers, CoreProtect, DiscordSRV, DriveBackupV2
- EssentialsSpawn, EzATN, FancyHolograms, GrimAC, InventoryRollbackPlus, LiteBans
- Maintenance, MaxCrates, MiniMOTD, Plan, PlayTimeManager, PurpurExtras
- Multiverse-Core, Multiverse-Inventories, Multiverse-NetherPortals, Multiverse-Portals
- SilkSpawners_v2, TAB, TreeFeller, Veinminer, voicechat, WorldEdit, WorldGuard

Crate, quest, boss, portal, or custom-content plugins that need to award a MaddPrestige objective should call the public `MaddPrestigeApi` or fire `MaddPrestigeProgressEvent`; see `docs/API.md`. This avoids brittle plugin-specific reflection.

For plugins that cannot compile against the API, `relationships.providers` can map public Bukkit events into objectives, run commands after successful lifecycle actions, and manage calculated permission entitlements. See [`RELATIONSHIPS.md`](RELATIONSHIPS.md).

## What compatibility means

For direct adapters, compatibility means MaddPrestige consumes the provider's public service/event surface and disables that adapter safely when unavailable. For coexistence entries, it means load ordering is declared and MaddPrestige does not patch or take ownership of that plugin's domain. It cannot make a third-party build support a Paper version that the third party itself does not support.

After changing plugin JARs, restart and review both the startup `Compatibility:` lines and the in-game dashboard. Event-hook changes require a restart; ordinary progression and GUI configuration can be applied with `/mp reload` or the staff GUI.
