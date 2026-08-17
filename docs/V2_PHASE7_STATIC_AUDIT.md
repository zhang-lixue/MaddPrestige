# Phase 7 correction-pass static, scope and security audit

Baseline/current HEAD is `36f8723f17209e8b18a180cbcf940a4770d5a265` on `v2/phase-7`. This audit covers the exact unstaged production/evidence inventory and final shaded distribution after the two required clean wrapper builds.

## Read-only Git and scope results

| Audit | Result | Finding |
|---|---|---|
| `git diff --check` | PASS | 0 findings |
| HEAD/branch | PASS | exact baseline SHA on `v2/phase-7` |
| Staged paths | PASS | 0 |
| Changed/new manifest | PASS | 62 paths: 15 modified, 47 new; 0 missing/extra |
| Protected V1 Java/test paths | PASS | 0 changed |
| Root descriptor | EXPECTED | authorized Phase 7 switch to `MaddPrestigeV2Plugin` and optional soft dependencies |
| Generic API extension | PASS | 0 API production changes; `GENERIC INTERFACE EXTENSION REQUIRED: NO` |
| Generic vendor imports | PASS | 0 Bukkit/LP/Vault/mcMMO/PAPI/shop/GP/WG/CE imports in API/core/persistence |
| Persistence-module scope | PASS | 0 persistence production/migration changes |
| Phase 8/9 production leakage | PASS | 0 matches |

## Required static/security scans

| Scan | Result | Finding |
|---|---|---|
| Optional shading | PASS | 0 vendor API entries in final JAR |
| Reflection/private access | PASS | 0 `Class.forName`, reflection package, declared-member or `setAccessible` production matches |
| Process execution | PASS | 0 `ProcessBuilder`/runtime-exec matches |
| Dynamic caller-built SQL | PASS | 0 matches; the durable Phase 5 event source uses fixed prepared SQL only |
| World/WorldGuard mutation | PASS | 0 create/load/unload/delete/add/remove/set-flag/drop matches |
| LuckPerms group creation | PASS | 0 create/load-create/modify-group matches |
| CraftEngine internal/proxy/raw identity | PASS | 0 prohibited imports/use; public `net.momirealms.craftengine.core.util.Key` is the qualified official key API |
| CraftEngine cached reload state | PASS | no cached definition, built stack or registry map authority |
| TODO/FIXME/HACK | PASS | 0 Phase 7 production matches |
| Generic MaddKraft/Court names | PASS | 0 production matches across API/core/persistence/integrations/Paper |
| Hard-coded secret values | PASS | 0 production matches |

## Build and artifact audit

The exact command `./mvnw.cmd --no-transfer-progress clean verify` (PowerShell form `.\mvnw.cmd`) passed twice consecutively. Mechanical Surefire aggregation reports 77 suites, 386 tests, 0 failures, 0 errors and 0 skips. Seven Checkstyle XML reports contain 0 errors. Enforcer Java/Maven, dependency convergence and duplicate dependency rules passed. Seven JaCoCo XML reports exist. The aggregate CycloneDX 1.6 SBOM has 74 components.

The final distribution has 1,234 JAR entries. It contains zero entries under `net/momirealms`, `com/sk89q`, `me/ryanhamshire`, `net/luckperms`, `com/gmail/nossr50`, `net/milkbowl/vault`, `me/clip/placeholderapi`, `me/gypopo/economyshopgui`, `com/ghostchu/quickshop` or `org/bukkit`. The descriptor main is `net.maddkraft.maddprestige.platform.paper.bootstrap.MaddPrestigeV2Plugin`.

| Artifact | Bytes | SHA-256 | Reproducibility |
|---|---:|---|---|
| `maddprestige-distribution/target/MaddPrestige-2.0.0-SNAPSHOT.jar` | 16,190,193 | `5B87E73A6CAEAD942CBD4E0C54137C025BA99194F30BFBFABB1BB270E4D1E331` | identical after both clean builds |
| `target/bom.json` aggregate SBOM | 190,988 | `584AEBC86E018F63FCCB93D3FF0C417242B9DD95CDCAB4A381803EDDD0BEA3AA` | identical after both clean builds |

## Optional classloading and CraftEngine boundary

Optional implementation classes are instantiated only after exact plugin name/enabled/version discovery. The eagerly loaded composition root carries no linked optional vendor symbols; composition bytecode tests pass. Linkage/runtime bootstrap failure is caught at the individual capability boundary. API dependencies are provided and excluded from shading.

CraftEngine production imports only the qualified public registry/identity/build/adaptor/reload surfaces. `loadedItems()` is used solely by the startup-only, non-empty readiness probe; it is neither cached nor used as per-operation identity. Re-enable cannot use that startup probe. Item identity is the exact public key, never material/name/lore/model/raw NBT/PDC. Capacity merges require exact key plus Bukkit similarity. No `CostProvider`, slot-debit workaround, ground drop, internal plugin/proxy/NMS API or reflective private access exists.

## Configuration and fallback security

The Phase 7 reload command does not read, compile, reconcile or publish configuration. It refuses direct mutation and leaves Phase 6 canonical administration authoritative. Phase 7 adds no command-execution primitive. AdvancedCrates, UltimateMobCoins and Discord fallbacks reuse the accepted structured command policy and retain external uncertainty. Placeholder inputs are typed, scheduled and freshness-bounded; rendering uses immutable cache snapshots. Shop listeners expose no progression capability and always grant zero credit.

## Evidence handling and residual boundaries

The A73 snapshot remained read-only. Qualification used disposable copies. Retained logs are sanitized; no tokens, credentials, IPs, databases or third-party generated configurations are bundled. The owner ZIP contains no third-party plugin JAR.

Folia is not claimed. External Vault/GP/inventory mutation remains outside one atomic MaddPrestige transaction and retains explicit uncertainty. Frozen V1 Java stays compiled for regression evidence but is inactive; only the root descriptor changed as authorized.
