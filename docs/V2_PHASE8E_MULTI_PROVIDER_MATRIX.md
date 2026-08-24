# MaddPrestige V2 Phase 8E multi-provider matrix

**Qualified:** 2026-08-23
**Result:** PASS for A65
**Boundary:** Bukkit `ServicesManager`, Stable `ProviderDeclaration`/`ProviderRegistrationHandle`, Stable
`MaddPrestigeService`, canonical administration and public snapshots only. No internal registry shortcut was used.

Alpha and Beta are separate Paper plugins with independent implementation classes and owner namespaces. They register
after MaddPrestige startup; the coordinator owns neither declaration.

| Case | Executed stimulus | Observation | Result |
|---|---|---|---|
| Independent ownership/namespaces | Alpha and Beta registered their own declaration | Exact IDs `phase8e_alpha:alpha` and `phase8e_beta:beta` appeared once | PASS |
| Multiple metrics | Alpha declared `points`/`bonus`; Beta declared `tokens`/`online_only` | Metadata and valid result keys were accepted | PASS |
| Simultaneous requirements | Canonical target path required Alpha then Beta | 5,760 evaluations sampled both provider families; 0 service failures | PASS |
| Late registration/order | Both plugins enabled after MaddPrestige | Safely dormant setup discovered them without publishing progression; canonical apply activated exact requirements | PASS |
| Alpha unregister/rebind | Public handle unregister followed by a new ServicesManager declaration | Exact composition failed closed transiently and recovered without revision change; Beta stayed present | PASS |
| Generation replacement after PRE | Alpha declaration was replaced from a real PRE listener | Stale outer work did not complete; the replacement generation recovered | PASS |
| Beta unhealthy/offline | Beta returned unavailable/online-only while Alpha remained healthy | Only the Beta target blocked; Alpha stayed visible and usable; zero forbidden effect | PASS |
| Alpha deadline/cancellation | Alpha held the callback beyond production deadline | Operation failed closed in 3,100 ms, provider observed cancellation, Beta stayed usable, fresh generation recovered | PASS |
| Alpha cross-generation synchronous callback block | Generation 1 held all four stable logical-provider permits while generation 2 and repeated replacements attempted work | Generation 2 admitted 0/8 and repeated rebind admitted 0/4; unregister did not reset allowance; Beta remained usable within 100 ms; release recovered in 48 ms and later Alpha/Beta succeeded | PASS |
| Malformed maps | Alpha returned missing, extra, null and wrong-type output in separate cases | Each exact map was rejected; no implicit zero; Beta remained healthy | PASS |
| Duplicate/ambiguous registration | Alpha submitted a second declaration for the same ID | Collision failed safely; one original provider remained authoritative | PASS |
| Runtime/linkage failures | Alpha threw runtime and linkage-like errors | Error stayed local, operation failed closed, Beta/internal service remained functional | PASS |
| Normal shutdown | Both declarations remained registered until server stop | Public callbacks unregistered; no hang or bridge leak was observed | PASS |

Performance totals additionally recorded Alpha `3,009` and Beta `2,880` callback reads on first boot. The fault matrix
finished with both providers available and no false clean success. Detailed lines are in
`docs/evidence/phase8e/A65_A66_A67_OR8E05_FAULT_MATRIX_SANITIZED.log`.
