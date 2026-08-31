# Phase 9D real-player P1 to P2 qualification

Status: **PASS**

This evidence records the owner's genuine Minecraft-client qualification in the outbound-isolated disposable
MaddKraft clone on 2026-08-30. The operation must not be replayed merely to reproduce this record.

## Subject and candidate

- Player: `tmydwc`
- UUID: `d7551bf9-6358-3218-89c4-06c9c57dc879`
- Repository branch/HEAD: `v2/phase-9d` at `a4b508576ab5c2e5a7d5abb07e5d92b1de609a69`
- Candidate SHA-256: `F62062BFBD8C304D0A280106ABDAA16F03076E7BD143801F3837FA029D2EC8F2`
- Active qualification revision: `r_2efcbe81c516487fb09e45948aecd08d`
- Server boundary: `127.0.0.1:25569`, clone-specific Java with owner-managed outbound-block rules

## Owner-observed state

| State | Before | After |
| --- | ---: | ---: |
| Current Prestige | 1 | 2 |
| Lifetime Prestige / compatibility value | 1 | 2 |
| Vault balance | 16 | 12 |
| mcMMO `total_level` / Power Level | 1 | 1 |

The exact repeated Vault result is `16 - 5 + 1 = 12`: the independent cost withdrew 5 and the reward deposited 1.
The mcMMO requirement was check-only and its authoritative value remained 1.

LuckPerms direct parents remained `default` and `owner`. No LuckPerms group was created, no hierarchy was mutated,
and unrelated membership was preserved.

## Acceptance mapping

- Repeated numeric Prestige execution (`P1 -> P2`): **PASS**
- Numeric progression exactly `+1`: **PASS**
- Current/lifetime compatibility equality: **PASS** (`2 == 2`)
- Repeated independent Vault cost: **PASS** (`-5`)
- Repeated Vault reward application: **PASS** (`+1`)
- Exact repeated net balance change: **PASS** (`-4`)
- mcMMO check-only persistence: **PASS** (`1 -> 1`)
- Unrelated LuckPerms membership preservation: **PASS**
- LuckPerms group creation or hierarchy mutation: **0**

## Evidence provenance

The owner-observed client values above are the authoritative visual/provider evidence. The retained isolated-server
`latest.log` independently corroborates the player UUID and command sequence: login at `15:31:49`, player, balance,
mcMMO, simulation, and Prestige commands from `15:31:53` through `15:32:16`, confirmation
`fad0441e-122d-4120-b823-039d37a1db2f` at `15:32:54`, and post-transaction player, balance, mcMMO,
PlaceholderAPI, and LuckPerms checks from `15:32:58` through `15:33:25`. The server log does not contain the
player's rendered chat values; those are intentionally recorded as owner-observed evidence rather than fabricated
console evidence.

Production was not contacted or modified. The qualification profile used low test-only values and did not select
production balance.
