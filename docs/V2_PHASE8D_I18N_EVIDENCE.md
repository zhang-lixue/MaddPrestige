# Phase 8D i18n evidence

**Acceptance:** A68 Satisfied candidate  
**Implementation:** `PaperMessageService`, `LocaleReloadResult`, packaged `locales/en_US.yml`  
**Focused test:** `PaperMessageServiceTest`

## Frozen behavior

| Requirement | Implementation/evidence |
|---|---|
| Server-global locale | One `locale.yml` selection; no per-player locale claim |
| Built-in default | Complete packaged `en_US` loaded as fallback authority |
| UTF-8 | Explicit UTF-8 file/resource readers and Unicode test values |
| Paper MiniMessage boundary | Strict Adventure MiniMessage deserialization in the Paper module |
| Catalog-driven public prose | Semantic `MessageReference` values cross command/setup/Why/GUI/Phase 7 boundaries; functional selected-catalog tests observe the actual rendered output |
| Semantic-information preservation | Doctor, validation/setup, Why, preview, administration and config descriptions retain explanation/action/plan meaning through stable keys; authorization uses typed identities; 84 administration codes retain exact mappings, all 65 single-source codes have source-level audit rows, and five typed occurrence variants distinguish overloaded recovery/action contexts; codes/counts remain supplemental facts |
| Stored-English prohibition | Internal diagnostic summary/remediation and preformatted plan text never become MiniMessage arguments; sentinel tests enforce the boundary |
| Stable machine identity | Core/API codes and message keys remain unchanged language-neutral values |
| Safe reload | Candidate files are fully parsed/validated before one immutable snapshot publication |
| Invalid reload | Returns rejected result and retains the previous snapshot/version |
| Missing selected key | Deterministic built-in English fallback |
| Missing selected and English key | Bounded visible `[message:<key>]` representation and diagnostic |
| Malformed template | Candidate snapshot rejection; startup selected catalog falls back safely |
| Untrusted values | Bounded control-sanitized unparsed arguments cannot create MiniMessage authority |
| Concurrency | Render/reload test observes only complete old or complete new catalogs |
| Completeness | Source-derived command/GUI/Phase 7 key inventory is contained in built-in English; generic wrappers are forbidden |

## Focused adversarial cases

`PaperMessageServiceTest` contains 32 tests:

1. `deterministicFallbacks` — selected-locale override, UTF-8/non-ASCII text, English fallback and bounded globally
   missing key.
2. `rejectsMalformedReloadAtomically` — malformed MiniMessage rejects and the prior selected value/version survives.
3. `dynamicArgumentsCannotGainFormattingAuthority` — tag/click/hover-like and control-bearing input is rendered as
   bounded text, not nested markup.
4. `concurrentReloadNeverPublishesPartialCatalog` — concurrent readers see one whole snapshot.
5. `successfulReloadPublishesNewVersion` — a valid complete candidate replaces the old snapshot once.
6. `builtInCatalogHasCompleteCoverage` — every declared public key exists in built-in English.
7. `selectedCatalogControlsRepresentativeSemanticPresentation` — selected values change actual command, GUI and Phase 7
   output while identifiers/revisions remain named data.
8. `presentationSitesUseCatalog` — approved V2 public Paper presentation sites contain no literal Component prose outside
   the documented bootstrap exception.
9. `publicPresentationKeyInventoryIsCompleteAndHasNoGenericWrappers` — source-derived semantic keys all exist and
   `command.line`, GUI line/title/action wrappers and `phase7.line` are absent.
10. `acceptsContainedRegularLocaleFiles` — ordinary contained selection/catalog files load normally.
11. `rejectsSymbolicLocaleFiles` — final catalog and selection-file symlink/reparse escapes reject before content read.
12. `rejectsSymbolicAncestorDirectories` — locale-directory and intermediate-ancestor escapes reject.
13. `rejectsTraversalAndAbsoluteLocaleSelections` — traversal/absolute input rejects and fallback remains authoritative.
14. `semanticWhyRendersEveryBlockerIdentity` — public output contains requirement and provider facts; every one of the
    51 stable blocker kinds has a direct bundled catalog key, diagnostics do not escape, and maximum differs from
    inactive ladder.
15. `restoredDiagnosticSurfacesRenderCatalogOwnedMeaningAndRemediation` — Doctor, validation/setup, administration and
    config explanation retain path/problem/consequence/remediation/description semantics without stored-English output.
16. `semanticPreviewRendersStructuredPlanAndBlockers` — eligible and blocked plans expose stage change, cost, reward,
    blocker, eligibility and revision from structured message facts.
17. `alternateCatalogControlsRestoredSemanticConcepts` — selected-catalog Why blocker, Doctor summary/remediation and
    preview cost prose changes while provider/path/amount runtime values remain correct.
18. `configurationDescriptionCatalogIsComplete` — every Phase 6 schema field identity has a bundled English semantic
    description.
19. `exactAdministrationSemanticsAreNotGuessedFromCodeFragments` — baseline/path/duplicate-stage failures retain their
    own explanations, remediations and facts and explicitly reject the former misleading missing-resource guidance.
20. `administrationMappingIsComplete` — the mechanically inventoried set of 84 public exception codes equals the
    explicit exact mapping table.
21. `administrationSemanticIdentityAuditHasNoCoarseFamilies` — all 84 codes have 84 distinct, reviewable semantic
    identities, both bundled catalog keys and no membership in the rejected broad families.
22. `administrationMultiThrowInventoryMatchesReviewedCompatibilityRegistry` — a source walk proves the complete
    111-site/84-code inventory, exact 19-code multi-source registry, 17 compatible contracts and five typed variant
    identities for the two overloaded codes.
23. `administrationSingleSourceInventoryMatchesSemanticAudit` — the mechanically derived 65-code single-source set
    equals the dedicated source/identity/condition/consequence/remediation/fact audit rows and, with 19 multi-source
    codes, accounts for all 84 public identities.
24. `administrationOccurrenceVariantsRenderExactSemantics` — unchanged/restored/reconciliation apply outcomes,
    acknowledgement/apply validation contexts, list/map guidance and persisted-reference remap staleness render exact
    consequences/remedies without internal English.
25. `correctedAdministrationCatalogMatchesSingleSourceConditions` — draft-only preview staleness, separate active
    revision staleness, rollback-draft provenance, pre-activation snapshot failure, actor/token state and exact
    stage/remap conditions render the real objects, consequences and remedies.
26. `alternateCatalogControlsCorrectedAdministrationSemantics` — alternate prose controls representative OR8D-11
    identities while exact codes and stage facts remain unchanged and internal diagnostics remain absent.
27. `alternateCatalogControlsAdministrationOccurrenceVariants` — an alternate catalog controls all five typed
    occurrence identities plus stale-remap cause while revision/error/finding/remap facts remain intact.
28. `reviewedAdministrationCasesRenderExactSemantics` — server authority, no-acknowledgement, projected group,
    duplicate requirement, unknown Prestige stage/metric, missing document and remap source/target cases retain their
    exact meaning/action/facts and remain distinct from actor/workflow/resource failures.
29. `alternateCatalogControlsReviewedAdministrationFamiliesAndFacts` — selected catalogs replace corrected
    acknowledgement, group and duplicate-requirement prose while exact facts remain unchanged.
30. `administrationFactsSurviveSelectionAndUnknownCodesUseSafeFallback` — immutable facts reach both messages and a
    genuinely unknown code alone selects bounded generic guidance without internal English.
31. `alternateCatalogControlsPreciseBlockerAdministrationAndNoPlanSemantics` — selected catalogs replace exact Why,
    administration and real no-plan semantic prose while structured facts remain visible.
32. `semanticPresentationDoesNotUseLossyPublicClassifiers` — source architecture rejects blocker-text parsing and
    administration classification from `unknown`, `missing`, `stale`, `invalid` or `failed` fragments.

`PhaseSevenCommandExecutorPresentationTest` separately drives the actual Phase 7 Paper executor with a selected catalog
and observes its semantic output rather than a generic wrapper.

`PhaseSixCommandServiceTest` separately proves the actual command boundary emits non-count Why blocker keys and paired
Doctor summary/remediation messages carrying exact path/code facts. It also proves malformed input carries canonical
usage syntax through the catalog-backed usage identity rather than exposing an exception paragraph.

`PhaseSixSimulationAndConfirmationTest` drives real RankUp authorization through inactive-ladder, illegal/stale-target,
unknown-cost and cost-provider/preflight rejection. Its no-plan test invokes `OperationPreviewService` without manually
constructing a preview and observes the exact typed blocker. `PrestigeAuthorizationServiceTest` proves maximum,
cooldown and counter-overflow identities and facts. `PhaseSixConfigurationAdministrationTest` drives real permission,
missing document, projected group, duplicate requirement, unknown Prestige stage/metric and prior accepted facts. It
also drives all three apply-recovery outcomes, acknowledgement/apply validation contexts, LIST/MAP/scalar paths and a
persisted-reference change between preview and apply. Correction Pass 6 additionally drives both real preview-stale
defensive comparisons, rollback-provenance rejection and snapshot preparation failure before activation.

The complete code register is `V2_PHASE8D_ADMINISTRATION_SEMANTIC_AUDIT.md`; the occurrence compatibility register is
`V2_PHASE8D_ADMINISTRATION_MULTI_THROW_AUDIT.md`.

The focused log is `PHASE8D_I18N_TESTS.log`; the complete regression logs are `PHASE8D_VERIFY_1.log` and
`PHASE8D_VERIFY_2.log`.

## Bounds and failure behavior

Locale name, key, argument, catalog size, catalog entry count and rendered value lengths are bounded. Selection and
catalog paths must remain beneath the real plugin-data/locale directories and every existing component must be regular,
non-symbolic and non-reparse. Controls are replaced with U+FFFD and never amplified in diagnostics. Diagnostics report
bounded locale/key/reason data, not catalog contents or secrets.

Reload failure is not partial publication: the current immutable snapshot remains reachable. Fallback values are always
from the immutable built-in snapshot, so a selected catalog cannot replace English fallback authority. A globally
missing key is deliberately visible to operators without becoming a null, NPE or arbitrary template.

## Claim boundary

Phase 8D ships and reviews English only. The architecture can load another server-global catalog, but no translated
language is claimed. Localization does not alter stable operation/provider/error identifiers and does not expand the
public compatibility-baseline claim.
