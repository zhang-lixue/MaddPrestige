# Phase 7 authoritative artifact and A73 qualification audit

## Evidence authority

The owner-supplied directory `C:\Users\zhang\Documents\MC Development\MaddKraft-A73-Plugin-Snapshot` is read-only deployment evidence. It contains 47 plugin JARs. The disposable qualification copied those JARs outside the repository and replaced only `MaddPrestige-1.2.0.jar` with the candidate `MaddPrestige-2.0.0-SNAPSHOT.jar`; the authoritative snapshot itself was not modified. Historical audit artifacts were used for design context only and were not substituted for this snapshot.

The server was Paper `26.1.2-74-ver/26.1.2@e4e17fc`, API `26.1.2.build.74-stable`, downloaded from Paper's official downloads service. The server JAR is 52,893,229 bytes with SHA-256 `1D70B1DAB9CF4A6DE615209A536F3A45A2186240253C428213CE2188AB95E5F7`.

## CraftEngine authoritative identity and API boundary

The deployed artifact is `craft-engine-paper-plugin-26.7.4.jar`, version `26.7.4`, 8,970,694 bytes, SHA-256 `8771A23713BEABCFEFA46DD0B4C768D7EB1112DBDE803243C972C08EC157D406`.

The official compile-only API artifacts are:

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `net.momirealms:craft-engine-core:26.7.4` | 3,277,316 | `8D6932093122492B8CDB4AE5EBC95D42B8BD119D8EB404E8F07EFC4E5EA9558C` |
| `net.momirealms:craft-engine-bukkit:26.7.4` | 1,938,001 | `DDEB6377E33DD539C1A29106CA1DB1DDD7ECFD5B8776434DB993B0078515B62E` |

Production uses only public `CraftEngineItems.byId`, `exists`, `loadedItems`, `isCustomItem` and `getCustomItemId`, public `Key.of`, `BukkitItemDefinition.buildBukkitItem`, `ItemBuildContext`, `BukkitAdaptor.adapt`, and the public reload-complete event. `loadedItems` is restricted to the bounded startup-only non-empty registry-readiness probe; it is not cached or used as item identity/operation authority. Production does not use `craft-engine-bukkit-proxy`, plugin implementation packages, `BukkitItemManager`, NMS/proxy utilities, raw identity NBT/components, reflection, private constructors, or private members. The official API artifacts are `provided`; the distribution contains zero `net/momirealms/craftengine/` entries.

The read-only item metric requires one fully namespaced `item-id`, re-resolves it for each read, returns unavailable for unknown/removed IDs, scans online-player storage contents only, compares the exact public CraftEngine identity key, and uses checked addition. Display name, lore, material, model data, texture and visual similarity are never identity.

The reward accepts a positive integral COUNT up to the configured safety maximum (default 2,304), re-resolves and builds through the public item definition on the server-thread boundary, splits legal stacks, and verifies the exact identity and amount of every built stack before mutation. Exact storage capacity is simulated without writes and forged visually/similarly matching stacks do not contribute merge capacity. Bukkit insertion is non-idempotent, non-reversible and non-reconcilable. Leftovers or exceptions after possible mutation are uncertain; no leftovers are dropped and automatic replay is forbidden.

An item-backed cosmetic/collectible uses that same item reward. A consumable item cost/turn-in is independently deferred because Bukkit/CraftEngine expose no atomic multi-slot debit, durable operation receipt, idempotent consumption, exact rollback, or operation-specific reconciliation query. A permanent non-item cosmetic entitlement is independently deferred because the qualified public API exposes no safe account/profile entitlement contract.

The lifecycle is explicit: `ABSENT`, `WAITING_FOR_REGISTRY`, `AVAILABLE`, and `DISABLED_OR_UNAVAILABLE`. Discovery and `PluginEnableEvent` install observation but never establish readiness by themselves. Initial startup may use a non-empty public-registry probe; an empty/transient registry remains waiting. A completed public reload event establishes readiness, revalidates configured IDs, rebinds both providers and advances both registry generations. Re-enable returns to waiting without the startup probe. Definition objects, built items and registry maps are not cached across operations or reloads. The linked implementation is isolated behind post-discovery bootstrap classes, so absence or incompatible linkage removes only this capability.

Security claim: exact public identity rejects ordinary visual lookalikes and a different CraftEngine key. It is not claimed to prove unforgeable provenance against trusted server plugins or commands capable of manipulating raw identity state.

## Other native artifact boundaries

GriefPrevention is deployed as version `16.18.7`, 380,232 bytes, SHA-256 `45E9907C61222E559ED2E099FFEE0EDE706A21243A31D7DB549BAF678708FDF0`. Its adapter uses public player data, remaining/accrued/bonus values, owned-claim enumeration, bonus setter and synchronous save. The Maven tag artifact is the official compile-only dependency but is not represented as byte-identical to the deployed JAR.

WorldGuard is deployed as `7.0.18+2392-fa605e6` in `worldguard-bukkit-7.0.18.jar`, 1,198,472 bytes, SHA-256 `08F3EF58BC521C635D8C78AEDACA96F151D2D397E7CD8018584955DD7468EB05`. The adapter uses the public platform, region container/query and applicable-region APIs read-only. Its Maven release coordinate is `com.sk89q.worldguard:worldguard-bukkit:7.0.18`; the runtime allowlist deliberately uses the deployed artifact's exact decorated version.

## Complete 47-JAR snapshot manifest

| File | Bytes | SHA-256 |
|---|---:|---|
| AdvancedCrates-4.8.9.jar | 2347185 | 06A6CF938040EA44DC2192CDD77DAE359C354C824D5BB3DE6DFBBAB86A2F5166 |
| AxGraves-1.29.0.jar | 4020473 | 0E2957642AC18618EF71A0DF86E2686F734835EF85A583DC88F60C9D465B6376 |
| AxPlayerWarps-1.17.2.jar | 4462731 | 7BC201C945E6B6DCC6553650FB72914FDFA9CAF5F04B78624AAC81F7C2D95423 |
| AxSellwands-1.16.4.jar | 4064671 | 375861D9DE32033B74F6B57E289E8BBF27E307A49AD48E179C1003EB87F9D634 |
| AxTrade-1.27.0.jar | 4044607 | C6F82B1DBD1B80D206D43F23BDE2C80AF30226E906435124E6946097C462DD46 |
| Chunky-Bukkit-1.5.3.jar | 304616 | 530D2C7430A96A39957391B7088BE144DAA3108F7665896D1C23AA8DD4AF32F3 |
| Citizens.jar | 3943420 | 59A5A42A671762D4A69286631F3DE99B1166AC47DBCF689A06E6637DD24528B7 |
| ClickVillagers-paper-1.6.3.jar | 262632 | 6CACFAA07F113E6AD5230D7EFBA5963DFB6A45D018196A186FA81D251BBAB686 |
| CoreProtect-CE-24.0.jar | 1102175 | 66CD362089BB8430E5A018EE77E9B433BF0DC9E65590D5F1A043A78D60415696 |
| craft-engine-paper-plugin-26.7.4.jar | 8970694 | 8771A23713BEABCFEFA46DD0B4C768D7EB1112DBDE803243C972C08EC157D406 |
| DiscordSRV-Build-1.30.5.jar | 10653915 | EF2FA1F2EB146C7C77412B7190A7DD33F1FC91282683FE45407739554C8AEFEF |
| DriveBackupV2.jar | 27948134 | C531E8FB6DC446FC710C59440C33CA2C71F15EC3933487CF4AE266B5617C591F |
| EconomyShopGUI-7.2.0.jar | 1775871 | E6B41CEBE12B7BC9D679CC02A7EB3E576E731CD0F050AB6233D781F7DB0955B7 |
| EssentialsX-2.22.0.jar | 4861125 | BDA4685105977FCA2E209820A9F0AD24275BD103390A03236F38E59BFDAC58E6 |
| EssentialsXSpawn-2.22.0.jar | 18661 | DD5377C4C921B9B67814209F4F6646FFBB959729003E721EC5E63C47C7C010B8 |
| FancyHolograms-2.11.0.jar | 1168379 | CD14A633A312C098C186C5F0BB07A15A2E52E2F36808F6525FEF86B2CEA93077 |
| GriefPrevention.jar | 380232 | 45E9907C61222E559ED2E099FFEE0EDE706A21243A31D7DB549BAF678708FDF0 |
| grimac-bukkit-2.3.74-2614909.jar | 12488026 | BE21741D9C70F5124118281D009B1A83165A211F53AF10DAD4B3196FF53C97ED |
| InventoryRollbackPlus-1.8.4.jar | 1200252 | ED3706E35D311FCA5EB0D45BFACAEFCB9FFA180B4542ED3B60920979967BED30 |
| LiteBans.jar | 1613859 | 11EDA6F708DB8B2D9C35BFAB20E4C360740771C4A257C86D59EF148282E60790 |
| LuckPerms-Bukkit-5.5.71.jar | 1501521 | 49CECB66FA1FD22A133039A490E9C1E5095A238E7CD66EB9D2A16FE6C897550D |
| MaddMobCoins-1.0.3.jar | 46364 | C3FBCA07D0F586D12E9EB2708414C7FDE3DEEAC1CEDA4600CC6791EB64E70364 |
| MaddPrestige-1.2.0.jar | 14595906 | 1DE0B772EF11DE8DD1F70DFDA71E65CB307CCE5D47EEBA4AF26205EDA36A3A08 |
| MaddRTP-1.0.0.jar | 195824 | AD7A8975C771021F178537A6F2E0971A6020431E80FD5C5E10969E1EA0CBFA6C |
| Maintenance-Paper-5.1.0.jar | 233744 | AB08939D28B3EF1A68AA9194092652282CC3405F7FCF7CBB059A74C58F1DCBA7 |
| mcMMO.jar | 3104834 | 7B7C48AF96CDE8F6CCE20C4E0ABFFA06D096E323CCD0FA534310B96B134BE143 |
| minimotd-paper-2.2.4.jar | 758774 | 7C18F55ECD9F50C675D6C9C123A21EEA9EDF1D9CC83F4EC92A95EE7D77CB36DB |
| multiverse-core-5.7.3.jar | 4456458 | C91A7C2C25AD7D878257B08C980381E8B5FFBA8DF63FAF402242BFB56A058884 |
| multiverse-inventories-5.3.5.jar | 1673897 | 27568B35AEA1042BAEA1FBC0D5256C17B51C596B8F2D166BE35BA1F77E6847A9 |
| multiverse-netherportals-5.1.0.jar | 96162 | A4B3750970B5B710AA942BAAFF07A8FCEF56802CF286B9DCDA7E69DD904492CC |
| multiverse-portals-5.2.3.jar | 173532 | 2159D8107EA517A258AD9D83FBFA4B89379A4706B80051FDB7BF3560EFD034DD |
| PlaceholderAPI-2.12.3.jar | 1160690 | FDE03259F5AF6938F3C33EEB4D814000A1ADABF1D2304CE14970BE81F609A437 |
| Plan-5.8-build-3579.jar | 18272156 | 70EEC6F34A1B7092559A0634B7F4F107D43B047594C6957C022B001687302182 |
| PlayTimeManager-3.6.5.jar | 7214108 | 1F71EE17C83990254A25411934D1BCB04C839D505321D84CC4946990441508AA |
| PurpurExtras-1.37.2.jar | 1097407 | 4D8623ADB1732599F4AD10E0B3AACC3F761569FFDB10594F06BCE31EAB5DD6D3 |
| QuickShop-Hikari-6.2.0.11.jar | 4444734 | EB3D2F01FF1357544221EE06870ADAE1094A4631382AA87AFF931F342D3B38B6 |
| TAB v6.1.1.jar | 8502720 | C2EF388495CF461B1CDCDACEA6558F70F626488C57B4EFE86A75BD5F3A280701 |
| TreeFeller-1.30.2.jar | 1749227 | DAAB222133FE2ACEFACE4695473ADD1E34190B75093605CB84DA719A543F837D |
| UltimateMobCoins-Paper-v2.1.0.jar | 752580 | 4AFD1976ACA5547C3146FF0EEE18A379E73E41ECEC8917F9A1584EDC40547B5C |
| VaultUnlocked-2.20.2.jar | 134928 | BD9E7A31F1B2D31A591497174887EEA7AE7E632C6B179DA13E4F0AD732DE2DF7 |
| veinminer-paper-2.11.2.jar | 684254 | 19FA174D569BA928FEFD96F8E9826EFAE3939D145D701B97A9D2EB362C84F10D |
| voicechat-bukkit-2.6.21.jar | 939287 | C08145770C19EC5550881B13C897E2A357E43EC419F7DA4039F73DCF279B94D8 |
| VoidGen-2.3.8.jar | 457795 | FA4D882F0923AD1C2BB25B4BCA33920076976BFE08F9BE0C02BC8929302D92CC |
| VoidSpawn.jar | 115550 | E4D74370B4B5CF34D1C77277301F7A6EF562C5FBB0D3F509B16AEBC336E79CE6 |
| VortexStacker-3.0.0.jar | 3966829 | 481506784639D77D0D62463F4723B4DED72A4787EBF08CE9D709C8F975B83EDB |
| worldedit-bukkit-7.4.4.jar | 7707538 | 44C97EE6C1DF9AFA127DF3C5A2C6A7108F826FB44AB7B255A7EC4250FEB89B9D |
| worldguard-bukkit-7.0.18.jar | 1198472 | 08F3EF58BC521C635D8C78AEDACA96F151D2D397E7CD8018584955DD7468EB05 |

## Disposable A73 procedure and results

All server runs used disposable directories outside the repository and snapshot. The production JAR was copied over the historical `MaddPrestige-1.2.0.jar`; the two were never co-loaded. Commands were issued through the Paper console. The full configuration explicitly enabled Vault, mcMMO, PlaceholderAPI output, two typed Placeholder inputs (`%advancedcrates_virtual_keys_total%` as COUNT and `%ultimatemobcoins_balance%` as EXACT_DECIMAL), EconomyShopGUI/QuickShop zero-credit compatibility, GriefPrevention, WorldGuard and CraftEngine. The first-party live-item harness source/build/output remained outside production source and plugin distribution.

The retained qualification command set was:

```powershell
# Candidate, from the repository root (run twice consecutively for final verification)
.\mvnw.cmd --no-transfer-progress clean verify

# Disposable harness, from A73_DISPOSABLE_SERVER
mvn --no-transfer-progress -f .\harness-src\pom.xml clean package

# Paper, from each disposable run directory
java -jar ..\paper-26.1.2-74.jar --nogui
```

The exact relevant console inputs were `maddprestige providers` and `stop` for Paper-only/matrix inspection. In the full run the first-party harness dispatched `maddprestige reload`, `craftengine reload`, then a second `craftengine reload` after dependency re-enable; `stop` ended the run after `A73_HARNESS_PASS`. Each matrix copy removed exactly one filename before the same Paper command: `GriefPrevention.jar`, `craft-engine-paper-plugin-26.7.4.jar`, `worldguard-bukkit-7.0.18.jar`, or `worldedit-bukkit-7.4.4.jar`. The full copy retained every current artifact and replaced only the historical MaddPrestige JAR.

The harness artifact was `A73Qualification-1.0.0.jar`, 15,155 bytes, SHA-256 `F81C11B2CDB20E6C36BE96EFDFE32BBD581273DE4F980EE9781271CAAAEEAD06`. It selected a buildable ID from the real non-empty public CE registry, used the actual shaded production access/metric/reward classes, a Citizens-backed disposable player plus online lookup facade, and the real provider generations. Harness-only reflection/proxies accessed the composition for observation and invoked MaddPrestige's existing Bukkit dependency handlers; no production testing method, public SDK or backdoor was added. The harness was removed from `plugins` after the run and is not in the distribution or owner bundle.

The Paper-only run initialized MaddPrestige without optional plugins, reached `Done`, registered the two built-in providers, remained safely dormant and stopped normally.

The full run initialized the 47 production artifacts plus one disposable first-party harness and reached `Done`. MaddPrestige bound two Paper providers, the durable manual-event capability, mcMMO, two typed PAPI inputs, cache-only PAPI output, both zero-credit shop listeners, three Vault providers, two GriefPrevention providers, one WorldGuard provider and two CraftEngine providers. Missing credentials caused unrelated DiscordSRV to disable as expected; it did not affect MaddPrestige. Sanitized evidence contains no secret or generated third-party configuration.

The harness invoked the actual shaded production `OfficialCraftEngineItemAccess`, `CraftEngineItemMetricProvider` and `CraftEngineItemRewardProvider` boundary against the real registry item `default:amethyst_standing_torch`. The initial read was 0; the exact reward returned `APPLIED` once; the next read was 1. Full storage returned invalid capacity with zero write. A material-only lookalike failed exact identity. A real CraftEngine reload changed generation 2→3 and the old generation was rejected. The MaddPrestige reload command refused direct configuration mutation and retained the active binding. CraftEngine explicitly refuses unsafe runtime plugin restart, so the harness invoked only MaddPrestige's real public Bukkit dependency-disable/enable handlers: state changed `AVAILABLE → DISABLED_OR_UNAVAILABLE → WAITING_FOR_REGISTRY`; a subsequent real CraftEngine reload rebound generation 4 and returned `AVAILABLE`. No uncertain item action was replayed.

The optional-dependency matrix reused the production set without the harness:

| Run | Expected unavailable surface | Result |
|---|---|---|
| GriefPrevention absent | GP metrics/reward only | Reached `Done`; Phase 5, WG and CE capabilities remained |
| CraftEngine absent | CE state `ABSENT`; CE metric/reward only | Reached `Done`; Phase 5, GP and WG capabilities remained |
| WorldGuard absent | WG predicate only | Reached `Done`; Phase 5, GP and CE capabilities remained |
| WorldEdit absent | WorldGuard hard dependency unusable, therefore WG predicate unavailable | Reached `Done`; Phase 5, GP and CE capabilities remained |
| Full authoritative stack | none of the configured current capabilities | Reached `Done`; all configured Phase 5/7 bindings and live CE item proof passed |

Sanitized Paper-only/full/matrix logs and the complete descriptor/hash/disposition manifest are in `docs/evidence/phase7`. The full 47-row table above is retained as an independent hash inventory; `A73_PLUGIN_METADATA_AND_DISPOSITION.md` adds descriptor identity, main/bootstrap, dependencies and explicit disposition for every artifact. No third-party JAR is copied into owner-review artifacts.
