# Changelog

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
