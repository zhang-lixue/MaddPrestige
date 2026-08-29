# Configurable plugin relationships

> **Historical V1 document.** This is retained as accepted 1.x evidence, not current V2 installation, API, compatibility, or operator guidance. Start with [the V2 README](../README.md).

MaddPrestige 1.2 adds a general relationship layer for optional plugins. It covers the three common integration directions without linking MaddPrestige to a provider's implementation classes:

1. **Outbound actions:** run provider commands after a MaddPrestige lifecycle transaction saves.
2. **Inbound progress:** translate a provider's public Bukkit event into a MaddPrestige objective.
3. **Dynamic entitlements:** apply provider permission nodes from calculated homes, listing, and claim limits.

The `relationships.providers` section in `config.yml` includes all 38 plugins from the supplied server list. Additional providers can be added without recompiling MaddPrestige.

## Outbound command actions

Each provider may configure console commands for these triggers:

- `player-join`
- `progress`
- `rank-up`
- `prestige`
- `perk-purchase`
- `patron-change`
- `hatter-gained` and `hatter-lost`
- `season-status` and `season-new`
- `contest-start` and `contest-end`

Example:

```yaml
relationships:
  # Opt in only when another plugin needs an outward action for every accepted
  # progress update. Ordinary MaddPrestige progress tracking is unaffected.
  emit-progress-actions: false
  providers:
    discordsrv:
      plugin: DiscordSRV
      enabled: true
      require-plugin: true
      features: [prestige-announcements]
      commands:
        prestige:
          - 'discord broadcast {player} reached Prestige {prestige}!'
        hatter-gained:
          - 'discord broadcast {player} is now The MaddHatter!'
```

Available common tokens are `{action}`, `{player}`, `{uuid}`, `{rank}`, `{prestige}`, `{tea_leaves}`, `{legacy_stars}`, `{season}`, `{provider}`, and `{plugin}`. Triggers also expose their own values, including `{old_rank}`, `{new_rank}`, `{perk}`, `{cost}`, `{tea_reward}`, `{patron_tier}`, `{reason}`, `{metric}`, `{contest_id}`, `{progress_type}`, `{amount}`, and `{source}`.

Commands are dispatched as console only after the owning transaction succeeds. Newlines are rejected, command lengths are capped, and `max-commands-per-trigger` prevents a bad configuration from producing an unbounded command cascade. Set `log-dispatched-commands: true` while testing.

## Generic inbound event hooks

An event hook reads public zero-argument accessor methods. Dot-separated paths traverse nested return values and unwrap `Optional` values.

```yaml
relationships:
  providers:
    my-quests:
      plugin: MyQuests
      aliases: [MyQuests-Paper]
      enabled: true
      require-plugin: true
      features: [quest-objective-progress]
      event-hooks:
        quest-complete:
          event-class: com.example.myquests.api.QuestCompleteEvent
          player-path: getPlayer
          amount-path: getQuest.getPrestigePoints
          fixed-amount: 1
          weight: 1.0
          progress-type: DECREE_OBJECTIVE
          source: MyQuests
          equals:
            - 'getQuest.getCategory=prestige'
          ignore-cancelled: true
```

Supported progress types are `SERVER_EARNINGS`, `MCMMO_XP`, `RABBIT_HOLE`, `DECREE_OBJECTIVE`, and `BOSS`. `amount-path` is optional; `fixed-amount` is used when it is absent. The final credited amount is `amount × weight`.

Event classes and accessor paths must match the installed provider version. An invalid hook is logged and skipped without disabling MaddPrestige. Event-hook changes require a full restart so Bukkit listeners can be registered safely.

Do not configure a generic hook for an event already handled by MaddPrestige's built-in mcMMO, EconomyShopGUI, QuickShop-Hikari, or UltimateMobCoins adapter, or progress will be counted twice.

## Dynamic entitlement permissions

Permission templates can map any calculated entitlement to another plugin:

```yaml
relationships:
  providers:
    essentials:
      plugin: Essentials
      aliases: [EssentialsX]
      entitlement-permissions:
        homes:
          minimum-value: 2
          clear-nodes: [essentials.sethome.multiple]
          clear-prefixes: [essentials.sethome.multiple.maddkraft_]
          nodes:
            - essentials.sethome.multiple
            - essentials.sethome.multiple.maddkraft_{value}
```

Entitlement keys currently supplied are `homes`, `auction-listings`, and `claim-blocks`. Templates may use `{value}`, `{homes}`, `{auction_listings}`, and `{claim_blocks}`. `clear-nodes` removes only exact managed nodes; `clear-prefixes` is intended for MaddPrestige-owned numeric node families. Never configure a broad prefix owned by another plugin.

## Java API integration

Plugins with a stable Java API should prefer MaddPrestige's service API and events:

- Use `MaddPrestigeApi` or `MaddPrestigeProgressEvent` to award inbound progress.
- Listen for `MaddPrestigeActionEvent` to consume outbound lifecycle changes without commands.
- Read `MaddPrestigeApi#playerState` or PlaceholderAPI values for presentation.

All optional integrations fail closed. Missing providers, disabled providers, unknown event classes, rejected commands, and unavailable permission services are isolated and reported without becoming hard dependencies.
