# MaddPrestige

MaddPrestige is MaddKraft's Paper 26.1.2 progression plugin. It keeps paid monarchy ranks separate from play-earned progression, gives individual players repeatable prestiges inside a shared chapter, and awards the unique cosmetic **MaddHatter** title through staff-run contests.

## What is included

- Free progression: **Curious → Odd → Mad → Unbound**.
- Individual prestiges with scaled objectives, fees, cooldowns, Tea Leaves, milestone messages, and rank-up discounts.
- Shared New Chapters with safe `ACTIVE`, `CLOSING`, `FROZEN`, and `RESETTING` stages.
- Lifetime Legacy Stars calculated when a chapter changes.
- Tea Leaf rewards for homes, QuickShop limits, and permanent GriefPrevention claim blocks.
- Paid monarchy ranks: **Knave, Duchess, King of Hearts, White Queen, Queen of Hearts**.
- Tebex-safe console fulfillment, including pending grants for customers who have never joined.
- Exactly one MaddHatter holder, one authentic soulbound Top Hat, contest history, inactivity vacancy, tie handling, and staff exclusion.
- A configurable six-row player GUI, persistent per-player display preferences, and a permission-gated staff dashboard for live compatibility checks, safe profile adjustments, reloads, and database flushes.
- A configurable relationship engine for all supplied plugins: outbound lifecycle commands, inbound Bukkit-event objective mappings, dynamic permission entitlements, provider aliases, and unlimited custom providers.
- MiniMessage output, PlaceholderAPI values, administrative audit records, SQLite WAL storage, transaction recovery records, and a public service/event API.

MaddHatter is intentionally not a purchasable rank. It has the cosmetic title and unique item but inherits no gameplay advantage beyond whatever paid/free rank the holder already has.

## Requirements

- Paper `26.1.2`, stable build 74 or compatible later build
- Java 25
- LuckPerms 5.5.x
- Vault/VaultUnlocked plus an economy provider (EssentialsX Economy works)
- mcMMO for the default skill objective
- PlaceholderAPI is recommended

SQLite is bundled inside the release JAR. Do not install a separate SQLite plugin.

## Install

1. Stop the server and back it up.
2. Copy `MaddPrestige-1.2.0.jar` into `plugins/`.
3. Start once, review `plugins/MaddPrestige/config.yml`, then stop.
4. Apply the EssentialsX and QuickShop fragments in [`docs/INSTALLATION.md`](docs/INSTALLATION.md).
5. Create Tebex packages using [`docs/TEBEX.md`](docs/TEBEX.md).
6. Start the server and run `/mp recovery list` followed by `/mp inspect <player>` as a basic operational check.

The plugin creates all managed LuckPerms groups automatically. Prefix presentation remains yours to style through LuckPerms/TAB; `%maddprestige_title%` is available for a unified display.

## Player commands

- `/rankup` — open free-rank progress; `/rankup confirm` validates and advances.
- `/prestige` — open the prestige menu; `/prestige confirm` performs it.
- `/prestige menu` — open the player's preferred page; `/prestige settings` changes sounds, number display, integration notices, and the preferred page.
- `/prestige rewards` and `/prestige buy <reward>` — Tea Leaf rewards.
- `/prestige top` and `/prestige history` — chapter and personal records.
- `/season` — current chapter status.
- `/maddhatter`, `/maddhatter top`, `/maddhatter history`, `/maddhatter claim`.

## Administrative workflow

Run `/mp help` for the compact list. Important operations are:

- `/mp gui` — open the staff dashboard, compatibility matrix, online-profile editor, reload control, and manual data flush.
- `/mp season status CLOSING`
- `/mp season status FROZEN`
- Perform the host/world maintenance and backups.
- `/mp season status RESETTING`
- `/mp season new <id> <display name>`
- `/mp contest start <metric> [days] [minimum-prestige]`
- `/mp contest end [tie-break-winner]`
- `/mp patron set <player> <tier|NONE>`
- `/mp recovery list`

`/mp season new` refuses to run until the current chapter is frozen/resetting and the active Hatter's Contest is finished. MaddPrestige never deletes worlds live; Multiverse/world replacement stays an explicit host-maintenance operation.

## What prestige resets

Prestige resets the free progression rank to Curious and clears that run's server earnings, mcMMO XP, Rabbit Holes, Decree objectives, and boss count. It does not reset the player's balance, inventory, builds, paid rank, lifetime prestige count, cosmetics, Legacy Stars, or permanent claim-block purchases.

A New Chapter resets seasonal prestige, Tea Leaves, seasonal home/shop upgrades, free rank, and the run ledger. It converts completed prestiges into Legacy Stars first. The player's paid rank, lifetime records, MaddHatter history, cosmetics, and permanent claim blocks remain.

## Verification

The project contains automated tests for configuration parsing, requirement math, rollback snapshots, SQLite persistence, interrupted transactions, contests, Hatter history, chapter conversion, and pending Tebex grants. The release was also booted and shut down cleanly on official Paper 26.1.2 build 74 with all 42 supplied JARs plus MaddPrestige (43 plugins total).

See [`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md) for the exact results and third-party issues discovered during that boot.
See [`docs/RELATIONSHIPS.md`](docs/RELATIONSHIPS.md) to connect arbitrary plugin commands, events, and permission-based features without recompiling.

## Build

```text
mvn clean package
```

The release JAR is written to `target/MaddPrestige-1.2.0.jar`.
