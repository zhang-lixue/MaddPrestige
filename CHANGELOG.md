# Changelog

## 2.0.1

- Improved SQLite reliability under concurrent currency updates.
- Reduced the chance of `SQLITE_BUSY` failures during brief writer contention.
- No changes to Prestige progression, configuration, or public APIs.

## 2.0.0

First stable V2 release.

- Added configurable numeric Prestige progression with requirements, costs, rewards, scaling, and per-level overrides.
- Added Player GUI progression tracking and safe Prestige previews.
- Added the Staff Dashboard, player Set/Reset tools, and history/audit views.
- Added guided editing for common configuration, including typed numeric input.
- Integrated Vault, mcMMO, LuckPerms, PlaceholderAPI, and custom providers.
- Added the stable Java API/provider SDK plus restart recovery and exactly-once operation protection.
- Uses SQLite persistence and ships publicly as `MaddPrestige.jar` under the MIT License.

## 2.0.0-rc.2

RC2 focuses on packaging, compatibility cleanup, and public-release polish following RC1.

- Standardized the plugin download and server filename as `MaddPrestige.jar`.
- Added the MIT license to the project and packaged plugin.
- Cleaned up the repository and documentation for public use.
- Kept the same V2 player and staff Prestige experience.
- Added cleaner provider and diagnostic names for new configurations while preserving legacy identifiers.
- Preserved historical migration compatibility and hardened release qualification.

## 2.0.0-rc.1

- Replaced stage/rank progression with provider-driven numeric Prestige and exact `P -> P + 1` execution.
- Added composable requirements, independent costs/rewards, exact-decimal currency handling, scaling, and milestones.
- Added Player and Staff GUIs, player/history inspection, audited Set/Reset Prestige, and safe guided configuration.
- Added session-bound confirmations, idempotent operation journals, restart recovery, and fail-closed provider behavior.
- Added the stable `2.x-stable-1` Java API/provider SDK and Paper event baseline.
- Qualified the release on Java 25, Paper 26.1.2 build 74, SQLite, and an isolated 42-plugin server stack.
- Deferred MySQL/MariaDB, shared-database operation, automatic V1 import, and the first-party Prestige Shop.

## 1.2.0

- Added a configurable relationship engine covering all 38 supplied plugin providers plus unlimited custom providers.
- Added outbound lifecycle commands for joins, progress, rank-ups, prestiges, perk purchases, patrons, chapters, contests, and MaddHatter transfers.
- Added generic reflective Bukkit event adapters so external plugin events can award any MaddPrestige objective without recompiling.
- Added configurable LuckPerms entitlement templates with exact-node and owned-prefix cleanup.
- Added the public `MaddPrestigeActionEvent` and live relationship feature/action/permission/event-hook counts in the staff GUI.
- Isolated optional command/event failures from committed rank, prestige, and reward transactions; added recursion, command-root, unresolved-token, thread, null-value, and high-frequency progress safeguards.

## 1.1.0

- Added a fully configurable 54-slot player GUI with persistent sounds, compact-number, integration-notice, and default-page preferences.
- Added `/mp gui`, a permission-gated staff dashboard with audited profile adjustments, compatibility status, config reload, and data flush controls.
- Added an explicit optional compatibility registry and soft dependencies for all 38 plugins in the supplied server list.
- Added direct/configurable progression hooks for mcMMO, EconomyShopGUI, QuickShop-Hikari, and UltimateMobCoins while preserving API bridges for LuckPerms, Vault, PlaceholderAPI, and GriefPrevention.
- Added SQLite schema v4 for durable player GUI preferences.

## 1.0.0

- Initial Paper 26.1.2 release.
- Free ranks, individual prestige, New Chapters, Legacy Stars, Tea Leaf rewards, monarchy patron ranks, Tebex fulfillment, and unique MaddHatter contests/items.
- LuckPerms, Vault, mcMMO, PlaceholderAPI, EconomyShopGUI, QuickShop-Hikari, GriefPrevention, UltimateMobCoins, TAB, EssentialsX, Multiverse, and Tebex compatibility paths.
- SQLite transaction recovery, admin audit records, tests, menus, placeholders, and public API.
