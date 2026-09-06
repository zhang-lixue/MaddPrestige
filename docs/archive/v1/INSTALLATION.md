# Archived V1 installation and first-run configuration

> **Historical V1 document.** This is retained as accepted 1.x evidence, not current 2.0 installation, API, compatibility, or operator guidance. Start with [the current README](../../../README.md).

## EssentialsX home limits

MaddPrestige grants `essentials.sethome.multiple.maddkraft_<number>`. Merge the following into EssentialsX's existing `sethome-multiple` section:

```yaml
sethome-multiple:
  default: 1
  maddkraft_2: 2
  maddkraft_3: 3
  maddkraft_4: 4
  maddkraft_5: 5
  maddkraft_6: 6
  maddkraft_7: 7
  maddkraft_8: 8
  maddkraft_9: 9
  maddkraft_10: 10
  maddkraft_18: 18
  maddkraft_25: 25
```

The values 18 and 25 correspond to the White Queen and Queen of Hearts defaults. If you change patron entitlements in MaddPrestige, add matching EssentialsX entries.

## QuickShop-Hikari limits

MaddPrestige grants `quickshop.maddkraft.limit.<number>`. Replace QuickShop-Hikari's existing `limits` block with:

```yaml
limits:
  use: true
  default: 3
  old-algorithm: false
  ranks:
    quickshop.maddkraft.limit.3: 3
    quickshop.maddkraft.limit.4: 4
    quickshop.maddkraft.limit.5: 5
    quickshop.maddkraft.limit.6: 6
    quickshop.maddkraft.limit.7: 7
    quickshop.maddkraft.limit.8: 8
    quickshop.maddkraft.limit.9: 9
    quickshop.maddkraft.limit.10: 10
    quickshop.maddkraft.limit.11: 11
    quickshop.maddkraft.limit.12: 12
    quickshop.maddkraft.limit.13: 13
    quickshop.maddkraft.limit.14: 14
    quickshop.maddkraft.limit.15: 15
    quickshop.maddkraft.limit.25: 25
    quickshop.maddkraft.limit.40: 40
```

Reload/restart QuickShop after applying this. Patron and earned allowances use the higher value; they are never added together.

## TAB and chat

Useful PlaceholderAPI values:

```text
%maddprestige_title%          MaddHatter, paid rank, or free rank
%maddprestige_patron%         Paid rank only
%maddprestige_rank_display%   Free rank only
%maddprestige_prestige%
%maddprestige_tea_leaves%
%maddprestige_legacy_stars%
%maddprestige_season_name%
%maddprestige_contest_score%
```

Use `%maddprestige_title%` in TAB's name-tag/tab formatting wherever you want one authoritative title. LuckPerms groups are still created and maintained so other permission-based plugins see the in-server ranks.

## Chapter launch checklist

1. Confirm Vault economy, LuckPerms, mcMMO, PlaceholderAPI, QuickShop-Hikari, and GriefPrevention are green in the MaddPrestige startup compatibility report.
2. Run `/mp recovery list`; investigate every pending transaction before launch.
3. Run `/mp contest start MCMMO_XP 28 10` when the first Hatter's Contest should begin.
4. Test one staff account and confirm `maddprestige.staff` excludes it from scoring.
5. Test `/rankup`, `/prestige`, and all three Tea Leaf reward types on a staging player.
6. Back up `plugins/MaddPrestige/maddprestige.db` with the server stopped or via a SQLite-aware backup process.
