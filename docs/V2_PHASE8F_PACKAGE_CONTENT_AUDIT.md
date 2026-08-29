# Phase 8F release package content audit

Date: 2026-08-28<br>
Artifact: `MaddPrestige-2.0.0-rc.1.jar`, 1,355 entries, 16,507,914 bytes, SHA-256 `0D961DB73D6C44CF18986CA42B7950914CEA5A1598BA7D41D0E919BFB76C6513`

## Mechanically audited categories

The executable `PhaseEightFReleasePackageIT` opens the exact shaded artifact and enforces these categories against
the real archive rather than a prose-only allowlist.

### REQUIRED V2 RUNTIME

- one filtered `plugin.yml` naming version `2.0.0-rc.1` and the active
  `net.maddkraft.maddprestige.platform.paper.bootstrap.MaddPrestigeV2Plugin` bootstrap;
- `META-INF/MANIFEST.MF` with release channel `release-candidate`, implementation version `2.0.0-rc.1` and candidate baseline
  `2.x-stable-1`;
- dormant `defaults/progression.yml`, `defaults/requirements.yml`, `defaults/rewards.yml` and
  `defaults/lifecycle.yml`, plus `integrations.yml` and `locales/en_US.yml`;
- V2 production classes, SQLite JDBC, SnakeYAML Engine and exactly one
  `META-INF/services/java.sql.Driver` containing `org.sqlite.JDBC`;
- `sqlite-jdbc.properties`, `THIRD-PARTY-NOTICES.txt` and the retained Apache 2.0 license text.

### REQUIRED TRANSITIONAL / LEGACY

The root `config.yml` and `gg/maddkraft/prestige/**` implementation are deliberately retained by the distribution
POM's `retain-v1-main` and `retain-v1-resources` executions. The protected V1 bootstrap calls
`saveDefaultConfig()`/`getConfig()` and its characterization tests load that resource, so removing it would break the
explicitly retained transition implementation. The package gate now requires both `config.yml` and
`gg/maddkraft/prestige/MaddPrestigePlugin.class` and checks legacy-only markers including `progression-groups`, the
QuickShop weight and patron permission mapping.

This is not an active V2 configuration surface. `plugin.yml` selects the V2 bootstrap, and the mechanical production
source scan finds zero legacy `gg.maddkraft.prestige` references, Bukkit `getConfig`/`saveDefaultConfig`/`reloadConfig`
calls, or root `config.yml` references in the V2 modules. Public V2 documentation continues to describe the five
schema-versioned documents and does not direct operators to edit this retained file.

### PUBLIC REPOSITORY-ONLY

The complete generic Member/Adventurer/Veteran example under `examples/member-adventurer-veteran/` and the provider
SDK example are source-review and consumer-build material. They are not runtime defaults and are not packaged. The
archive gate rejects the `examples/` prefix and the generic-example directory name.

### BUILD/TEST-ONLY — EXCLUDED

Qualification harnesses, test sources/frameworks, fixtures and review evidence are repository evidence or build-time
inputs only. None is packaged in the runtime JAR.

### OPTIONAL DEPENDENCY — NOT BUNDLED

Paper and every optional plugin/API are `provided` or qualification-only. LuckPerms, Vault, PlaceholderAPI, mcMMO,
WorldGuard/WorldEdit, CraftEngine, GriefPrevention, QuickShop-Hikari and EconomyShopGUI implementations and APIs are
not redistributed. The package gate rejects their package prefixes.

### PROHIBITED — EXCLUDED

MySQL, MariaDB and Hikari production packages; third-party plugin implementations; nested JARs; runtime SQLite
databases or journals; worlds; logs; JFR data; Maven caches; EULA/runtime state; secrets; and local Windows, workspace
or CI paths are prohibited and mechanically rejected.

## Shade and dependency disposition

The production package shades only first-party runtime modules, Xerial SQLite JDBC `3.50.3.0` and SnakeYAML Engine
`3.0.1`. Optional Paper plugins and their APIs are not bundled. SQLite native resources are deliberately not relocated;
relocation would break the driver's native-image/native-library lookup and provides no namespace benefit here.

The clean shade audit reports one expected overlapping resource, `META-INF/MANIFEST.MF`, across input JARs. The project
manifest transformer owns the final result and the integration test verifies every required identity field. Dependency
convergence and duplicate-POM-version Maven Enforcer rules pass. The aggregate CycloneDX 1.6 SBOM validates during the
build and contains the complete resolved component graph.

## License disposition

`THIRD-PARTY-NOTICES.txt` identifies both shaded third-party libraries and their Apache 2.0 licenses. Xerial's retained
Apache license and Zentus attribution remain under `META-INF/maven/org.xerial/sqlite-jdbc/`. The generic Apache 2.0
license text also covers SnakeYAML Engine; optional runtime plugins are named as integrations, not redistributed.
