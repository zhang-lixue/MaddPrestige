# Phase 8D A47 authorization evidence

**Acceptance:** A47 Satisfied candidate  
**Correction:** Owner Review Correction Pass 3 architecture, preserved by Correction Passes 4 and 5  
**Date:** 2026-08-18

## Authoritative model

RankUp and Prestige authorization publish immutable `AuthorizationBlocker` records. Each record contains a stable
`AuthorizationBlockerKind`, immutable blocker-specific facts and optional secondary diagnostic text. The kind and facts
are the public-semantic authority; diagnostic prose is retained only for compatibility, logging and debugging.

The exhaustive kind inventory covers snapshot/configuration/state, ladder/stage/target, Prestige eligibility/maximum/
overflow/cooldown, missing cost/reward/requirement definitions, provider and trusted-context failures, requirement
evidence/provenance mismatches, cost/reward validation and preflight, projection and boundary sampling/read failures.

Typed blockers flow unchanged through:

- `RankUpAuthorizationResult` and `PrestigeAuthorizationResult`;
- `RankUpPlan`, `PrestigePlan` and their preflight/boundary components;
- `WhyService` and the real command presentation path;
- `OperationPreview`, real no-plan `AdministrationException`, simulation and operation rejection;
- `SemanticPresentation`, which selects `command.why.blocker.<catalog_identity>` directly from the blocker kind.

No public blocker identity is inferred by lowercasing or searching English text.

## Exactness evidence

The focused suites prove real inactive/no-snapshot, illegal or stale target, unknown configured cost, Prestige maximum,
cooldown, provider and cost-preflight paths. Cost preflight retains cost ID, provider, status, canonical amount, value
type and bounded detail. Maximum and inactive ladder are explicitly asserted to render different catalog meanings.
Every enum member has a bundled direct catalog key and is exercised by the exhaustive presentation table.

The real no-plan test causes `OperationPreviewService` to receive an authorization result with no plan. The resulting
public administration response carries the same typed blockers used by `/why`; it does not flatten them into an English
list or reduce them to `operation_unavailable`.

Static architecture guards reject a return of the former `whyBlocker(String)` prose classifier. Functional
alternate-catalog tests prove that precise semantic prose comes from the selected locale while provider, target, cost,
cooldown and other runtime facts remain structured arguments.

## Acceptance conclusion

A47 is Satisfied in the Correction Pass 3 candidate because `/why` exposes the exact authoritative blocker identities
and applicable facts from real RankUp and Prestige authorization. A blocker count remains supplemental and cannot
replace blocker identity.
