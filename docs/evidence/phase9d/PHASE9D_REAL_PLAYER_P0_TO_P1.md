# Phase 9D real-player P0 to P1 qualification

Status: **PASS**

This evidence records the owner's genuine Minecraft-client qualification in the outbound-isolated disposable
MaddKraft clone on 2026-08-30. The operation must not be replayed merely to reproduce this record.

## Subject and candidate

- Player: `tmydwc`
- UUID: `d7551bf9-6358-3218-89c4-06c9c57dc879`
- Repository branch/HEAD: `v2/phase-9d` at `a4b508576ab5c2e5a7d5abb07e5d92b1de609a69`
- Candidate SHA-256: `3F5B27B34C72084850CEE07C0360B43D547D410DE00E77432A799B2F25208DCD`
- Active qualification revision: `r_2efcbe81c516487fb09e45948aecd08d`
- Server boundary: `127.0.0.1:25569`, clone-specific Java with owner-managed outbound-block rules

## Owner-observed state

| State | Before | After |
| --- | ---: | ---: |
| Current Prestige | 0 | 1 |
| Lifetime Prestige / compatibility value | 0 | 1 |
| Vault balance | 20 | 16 |
| mcMMO `total_level` / Power Level | 1 | 1 |
| PlaceholderAPI `current_prestige` | 0 | 1 |
| PlaceholderAPI `lifetime_prestige` | 0 | 1 |

The exact Vault result is `20 - 5 + 1 = 16`: the independent cost withdrew 5 and the reward deposited 1.
The mcMMO requirement was check-only and its authoritative value remained 1.

LuckPerms direct parents remained `default` and `owner`. No LuckPerms group was created, no hierarchy was mutated,
and unrelated membership was preserved.

## Acceptance mapping

- Real Vault and mcMMO provider reads: **PASS**
- `ALL` requirement evaluation (`Vault >= 10`, mcMMO `total_level >= 1`): **PASS**
- Numeric transition exactly `P0 -> P1`: **PASS**
- Current/lifetime compatibility equality: **PASS** (`1 == 1`)
- Independent Vault cost: **PASS** (`-5`)
- Vault reward application: **PASS** (`+1`)
- mcMMO check-only persistence: **PASS** (`1 -> 1`)
- PlaceholderAPI current/lifetime output: **PASS** (`1`, `1`)
- Unrelated LuckPerms membership preservation: **PASS**
- LuckPerms group creation or hierarchy mutation: **0**

## Evidence provenance

The owner-observed client values above are the authoritative visual/provider evidence. The retained isolated-server
`latest.log` independently corroborates the player UUID and command sequence: login at `13:56:59`, player/why/
simulation/Prestige commands from `13:57:13` through `13:57:45`, confirmation
`45c2f929-1e56-4a48-8f35-03733567c7ec` at `13:57:49`, and post-transaction player, balance, mcMMO,
PlaceholderAPI, and LuckPerms checks from `13:59:49` through `14:00:11`. The server log does not contain the
player's rendered chat values; those are intentionally recorded as owner-observed evidence rather than fabricated
console evidence.

Production was not contacted or modified. The qualification profile used low test-only values and did not select
production balance.
