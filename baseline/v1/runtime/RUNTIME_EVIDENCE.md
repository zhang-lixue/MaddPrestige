# V1 Runtime Evidence

Source: retained local `runtime-test/logs/latest.log`, SHA-256 `5c3a5add5917d1eed84ea6ba03610750b79d40de4f546dbfa3ae6957c11401d8`.

The raw log is intentionally not tracked because it contains local filesystem paths and a third-party server identifier. The local runtime folder remains ignored. This document preserves the facts relevant to MaddPrestige qualification without those identifiers.

Observed runtime on 2026-08-07:

- Java: Temurin 25.0.3;
- Paper: 26.1.2 build 74, API `26.1.2.build.74-stable`;
- initialized plugins: 43;
- MaddPrestige: 1.2.0;
- LuckPerms: 5.5.58;
- Vault/VaultUnlocked: 2.20.2;
- mcMMO: 2.2.053;
- PlaceholderAPI: 2.12.2;
- QuickShop-Hikari: 6.2.0.11;
- UltimateMobCoins: 2.1.0;
- GriefPrevention: 16.18.7.

Relevant MaddPrestige outcomes:

```text
[MaddPrestige] Loading server plugin MaddPrestige v1.2.0
[MaddPrestige] Enabling MaddPrestige v1.2.0
[MaddPrestige] Hooked mcMMO through McMMOPlayerXpGainEvent
[MaddPrestige] Hooked QuickShop-Hikari through ShopSuccessPurchaseEvent
[MaddPrestige] Compatibility: LuckPerms [API_BRIDGE] = 5.5.58 (enabled)
[MaddPrestige] Compatibility: Vault [API_BRIDGE] = 2.20.2 (enabled)
[MaddPrestige] Compatibility: PlaceholderAPI [API_BRIDGE] = 2.12.2 (enabled)
[MaddPrestige] MaddPrestige enabled for Chapter One on Paper 26.1.2.
[MaddPrestige] Disabling MaddPrestige v1.2.0
```

EconomyShopGUI 7.0.0 failed its own Paper-version parsing during that boot, so MaddPrestige reported it as not installed and did not register its hook. This is historical smoke evidence, not a supported-version certification.
