# Phase 8F final compatibility baseline

**Baseline ID:** `2.x-stable-1`<br>
**Release-candidate identity:** `2.0.0-rc.1`<br>
**Prepared:** 2026-08-24; owner-frozen after Independent Owner Review 2<br>
**Owner status:** Owner Review 2 froze this baseline; targeted Owner Review 3 and final A76 acceptance passed without changing a Stable SDK or Paper-event signature.

## Frozen surfaces

The baseline contains exactly two public surfaces:

1. **Surface A — Bukkit-free SDK:** 46 public top-level types classified `STABLE 2.x BASELINE` in
   `V2_PHASE8F_API_INVENTORY.md`.
2. **Surface B — Paper events:** six public event types classified `PAPER STABLE 2.x BASELINE` in
   `V2_PHASE8F_PAPER_API_INVENTORY.md`.

There were no Stable source or binary contract changes between the owner-accepted Phase 8B candidates and this Phase
8F baseline. Phase 8F records and enforces the accepted surfaces. The SDK signature hash is
`357646DE87CE5B06883B8678CCB3A45D655E5F08A7219B63BDEDDA4B68402C4E`; the Paper-event signature hash is
`48F8824B83FF998C9387FB53EC5363E4BED51FF929F751FE28E3B1C52171EC1C`. These hashes describe normalized reflected
type, superclass/interface, constructor, method, field, record-component, nested-type, default-value and annotation
signatures; the inventories remain the human-reviewable exact `javap -public` authority.

## Compatibility policy

- `@Stable` contracts are the supported 2.x extension boundary. Removing or incompatibly changing a Stable type,
  superclass/interface, constructor, method, field, record component, enum constant, validation contract, threading
  contract, ownership/lifetime rule, failure model, or documented meaning requires a later major-version baseline.
- A backwards-compatible extension still requires deliberate review, regenerated inventories, an updated mechanical
  signature baseline, and the appropriate semantic-style version change. The guard intentionally blocks even additions
  until that review occurs.
- Adding an abstract interface method is breaking. A reviewed default method may be a compatible extension. Record
  component changes are breaking. Added enum constants require source-compatibility review because exhaustive consumer
  switches can fail to compile.
- `@Experimental` types may change incompatibly before promotion. `INTERNAL/SHOULD NOT BE PUBLIC` and
  `LEGACY/PENDING REMOVAL` types are not supported extension points. Their presence in the API artifact is transitional,
  not a 2.x compatibility promise. Deprecation and migration guidance should precede removal where practical.
- Surface A remains Bukkit-free. Surface B may expose only `Event`, `Cancellable`, `HandlerList`, and immutable Stable
  SDK snapshots. Persistence, journals/recovery, mutable implementation state, registry generations/tokens, and
  optional-vendor types are forbidden from both Stable surfaces.
- Public parameters, components, and return values are non-null unless `Optional` explicitly represents absence.
  Constructors and synchronous validation reject null/invalid values as documented. No external SDK nullness-annotation
  dependency is required; explicit `Optional`, runtime validation, and normative Javadocs are authoritative.
- Operational failures from `MaddPrestigeService` complete as `ServiceResult`/`ServiceError`. Exceptional completion is
  reserved for documented programmer-contract violations or catastrophic JVM failure. Caller cancellation detaches the
  caller and does not imply rollback of accepted work.

## Mechanical enforcement

- `StableApiCompatibilityBaselineTest` discovers the complete Stable SDK classification, requires exactly 46 top-level
  types, and compares the normalized complete signature with the recorded hash.
- `StableApiLeakageTest` rejects any non-allowlisted MaddPrestige API type from Stable SDK signatures.
- `StablePaperEventSurfaceTest` discovers exactly six Stable Paper events and rejects unrelated Bukkit, persistence,
  vendor, or implementation exposure.
- `StablePaperEventCompatibilityBaselineTest` compares the complete six-event signature with the recorded hash.
- `tools/Generate-Phase8FApiInventory.ps1` regenerates both human-review inventories from Java 25 compiled classes.

Any failure is a release blocker. Updating a hash without reviewing and recording the corresponding complete inventory
is not an accepted compatibility change.

## Excluded release concerns

This baseline does not make `2.0.0-rc.1` GA or production-ready. Under the superseding owner policy, the owner-operated
public-only post-freeze A76 gate passed against the exact accepted artifact.
Phase 9 retains MaddKraft deployment/migration/production qualification. MySQL/MariaDB and shared-database support
remain deferred post-2.0 and are not implied by the SDK baseline.
