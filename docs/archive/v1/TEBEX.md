# Archived V1 Tebex setup

> **Historical V1 document.** This is retained as accepted 1.x evidence, not current 2.0 installation, API, compatibility, or operator guidance. Start with [the current README](../../../README.md).

Create one package for each paid monarchy rank. Configure the package's successful-payment console command as follows:

| Package | Console command |
|---|---|
| Knave | `maddprestige patron set {username} KNAVE` |
| Duchess | `maddprestige patron set {username} DUCHESS` |
| King of Hearts | `maddprestige patron set {username} KING_OF_HEARTS` |
| White Queen | `maddprestige patron set {username} WHITE_QUEEN` |
| Queen of Hearts | `maddprestige patron set {username} QUEEN_OF_HEARTS` |

The command removes other managed paid-rank groups before applying the selected tier, so upgrades do not stack. It also grants the tier's managed entitlement permission. If the username has never joined, the operation is stored in SQLite and applied automatically on first join.

For a manual refund or chargeback after reviewing the player's current tier, run:

```text
maddprestige patron set <username> NONE
```

Do not sell MaddHatter. It is the unique rotating cosmetic title awarded by a Hatter's Contest and carries no additional gameplay permissions.

Recommended store wording:

- Paid ranks support the server and grant only the published conveniences/cosmetics.
- A player's free Curious/Odd/Mad/Unbound progression is independent of their paid rank.
- Patron and prestige limits use the higher allowance, not the sum.
- Purchases never guarantee MaddHatter eligibility or contest victory.
