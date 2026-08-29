# Phase 8F final Bukkit-free SDK public API inventory and classification

Generated mechanically from the compiled `maddprestige-api` classes on 2026-08-24 by `tools/Generate-Phase8FApiInventory.ps1`. This is surface A: the Bukkit-free core/service/provider SDK. Every public member shown by `javap -public` inherits its enclosing type classification. Compiler-standard record/enum members are included, so this is the exact review surface rather than a source-file summary.

- Phase 8A public top-level type count: **76**.
- Final Phase 8F public top-level type count: **109**.
- EXPERIMENTAL: **5** type(s).
- INTERNAL/SHOULD NOT BE PUBLIC: **17** type(s).
- LEGACY/PENDING REMOVAL: **41** type(s).
- STABLE 2.x BASELINE: **46** type(s).
- Baseline `2.x-stable-1` records the exact 46-type Stable surface. Changes require backwards-compatible extension or an explicitly reviewed later major-version baseline.

## Type classification

| Public type | Classification |
|---|---|
| `net.maddkraft.maddprestige.api.action.ActionCharacteristics` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.action.ActionExecutionResult` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.action.ActionExecutionStatus` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.action.PreflightStatus` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.annotation.Experimental` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.annotation.Stable` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.audit.AuditOutcome` | INTERNAL/SHOULD NOT BE PUBLIC |
| `net.maddkraft.maddprestige.api.audit.AuditRecord` | INTERNAL/SHOULD NOT BE PUBLIC |
| `net.maddkraft.maddprestige.api.audit.AuditValue` | INTERNAL/SHOULD NOT BE PUBLIC |
| `net.maddkraft.maddprestige.api.cost.CostDefinition` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.cost.CostPreflight` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.cost.CostProvider` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.cost.NativeRecoverableCostProvider` | INTERNAL/SHOULD NOT BE PUBLIC |
| `net.maddkraft.maddprestige.api.cost.PlannedCost` | INTERNAL/SHOULD NOT BE PUBLIC |
| `net.maddkraft.maddprestige.api.event.ConfigAppliedSnapshot` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.event.OperationEventSnapshot` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.event.ProviderHealthChangedSnapshot` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.explanation.ExplanationNode` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.explanation.ExplanationStatus` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.id.ConfigRevisionId` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.id.CostId` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.id.CurrencyId` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.id.EntitlementId` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.id.FieldId` | INTERNAL/SHOULD NOT BE PUBLIC |
| `net.maddkraft.maddprestige.api.id.IdentifierRules` | INTERNAL/SHOULD NOT BE PUBLIC |
| `net.maddkraft.maddprestige.api.id.MetricId` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.id.MilestoneId` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.id.OperationId` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.id.ProviderId` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.id.RequirementId` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.id.RewardId` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.id.ScopeId` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.id.SeasonId` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.id.StageId` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.id.StringIdentifier` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.metric.MetricDescriptor` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.metric.MetricDimension` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.metric.MetricMonotonicity` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.metric.MetricOperator` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.metric.MetricProvider` | EXPERIMENTAL |
| `net.maddkraft.maddprestige.api.metric.MetricQuery` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.metric.MetricReadMode` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.metric.MetricResetPolicy` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.metric.MetricSample` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.metric.MetricSampleStatus` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.metric.MetricValue` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.metric.MetricValueType` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.operation.ActionState` | INTERNAL/SHOULD NOT BE PUBLIC |
| `net.maddkraft.maddprestige.api.operation.Actor` | INTERNAL/SHOULD NOT BE PUBLIC |
| `net.maddkraft.maddprestige.api.operation.OperationActionPlan` | INTERNAL/SHOULD NOT BE PUBLIC |
| `net.maddkraft.maddprestige.api.operation.OperationPlan` | INTERNAL/SHOULD NOT BE PUBLIC |
| `net.maddkraft.maddprestige.api.operation.OperationState` | INTERNAL/SHOULD NOT BE PUBLIC |
| `net.maddkraft.maddprestige.api.provider.ActivationState` | INTERNAL/SHOULD NOT BE PUBLIC |
| `net.maddkraft.maddprestige.api.provider.CapabilityDescriptor` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.provider.DependencyDescriptor` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.provider.Provider` | EXPERIMENTAL |
| `net.maddkraft.maddprestige.api.provider.ProviderCallContext` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.provider.ProviderCancellationSignal` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.provider.ProviderDeclaration` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.provider.ProviderDescriptor` | EXPERIMENTAL |
| `net.maddkraft.maddprestige.api.provider.ProviderExecutionExpectation` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.provider.ProviderHealth` | INTERNAL/SHOULD NOT BE PUBLIC |
| `net.maddkraft.maddprestige.api.provider.ProviderHealthState` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.provider.ProviderLifecycle` | INTERNAL/SHOULD NOT BE PUBLIC |
| `net.maddkraft.maddprestige.api.provider.ProviderMetadata` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.provider.ProviderMetricDefinition` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.provider.ProviderMetricDimension` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.provider.ProviderMetricRequest` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.provider.ProviderMetricResult` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.provider.ProviderMetricStatus` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.provider.ProviderRegistrationHandle` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.provider.ProviderSnapshot` | EXPERIMENTAL |
| `net.maddkraft.maddprestige.api.provider.RequirementProvider` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.rank.AmbiguousRankMembership` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.rank.ManagedRankState` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.rank.RankAdapter` | EXPERIMENTAL |
| `net.maddkraft.maddprestige.api.rank.RankProjectionOutcome` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.rank.RankProjectionRequest` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.rank.RankProjectionResult` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.result.ErrorCategory` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.result.Result` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.result.StructuredError` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.reward.NativeRecoverableRewardProvider` | INTERNAL/SHOULD NOT BE PUBLIC |
| `net.maddkraft.maddprestige.api.reward.PlannedReward` | INTERNAL/SHOULD NOT BE PUBLIC |
| `net.maddkraft.maddprestige.api.reward.RewardDefinition` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.reward.RewardFailurePolicy` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.reward.RewardPreflight` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.reward.RewardProvider` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.reward.RewardRepeatability` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.service.CurrencyBalanceView` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.service.MaddPrestigeService` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.service.OperationEvaluation` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.service.OperationEvaluationStatus` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.service.OperationKind` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.service.OperationResult` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.service.OperationSimulationView` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.service.OperationStatus` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.service.PlayerProgressSnapshot` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.service.ProviderView` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.service.RequirementProgressStatus` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.service.RequirementProgressView` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.service.SeasonView` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.service.ServiceError` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.service.ServiceResult` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.service.StageView` | STABLE 2.x BASELINE |
| `net.maddkraft.maddprestige.api.validation.ValidationFinding` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.validation.ValidationReport` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.validation.ValidationSeverity` | LEGACY/PENDING REMOVAL |
| `net.maddkraft.maddprestige.api.value.ExactDecimal` | LEGACY/PENDING REMOVAL |

## Stable 2.x baseline review details

Nullability for every Stable baseline type is non-null by default at runtime; `Optional` represents absence and public constructors/callback adapters reject null. Normative service/event/provider/id/metric package and type/member Javadocs define bounds, mutability, threading/blocking, ownership/lifetime and structured failure semantics. Provider callbacks must return non-null stages/maps/results. No external SDK nullness dependency is required; explicit `Optional`, runtime validation, and normative Javadocs are authoritative.

| Stable 2.x type | Purpose | Javadoc | Thread model | Nullability | Bounds | Compatibility risk |
|---|---|---|---|---|---|---|
| `net.maddkraft.maddprestige.api.annotation.Experimental` | Stability classification marker. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral, or compile/runtime metadata only. | Non-null unless `Optional`; fail-fast validation. | Finite annotation target/retention only. | LOW/MEDIUM: validation tightening and semantic changes still require compatibility review. |
| `net.maddkraft.maddprestige.api.annotation.Stable` | Stability classification marker. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral, or compile/runtime metadata only. | Non-null unless `Optional`; fail-fast validation. | Finite annotation target/retention only. | LOW/MEDIUM: validation tightening and semantic changes still require compatibility review. |
| `net.maddkraft.maddprestige.api.event.ConfigAppliedSnapshot` | Immutable, Bukkit-free lifecycle event payload. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral, or compile/runtime metadata only. | Non-null unless `Optional`; fail-fast validation. | Immutable lists/text are explicitly bounded; no mutable implementation references. | HIGH: record component changes are constructor and binary incompatible. |
| `net.maddkraft.maddprestige.api.event.OperationEventSnapshot` | Immutable, Bukkit-free lifecycle event payload. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral, or compile/runtime metadata only. | Non-null unless `Optional`; fail-fast validation. | Immutable lists/text are explicitly bounded; no mutable implementation references. | HIGH: record component changes are constructor and binary incompatible. |
| `net.maddkraft.maddprestige.api.event.ProviderHealthChangedSnapshot` | Immutable, Bukkit-free lifecycle event payload. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral, or compile/runtime metadata only. | Non-null unless `Optional`; fail-fast validation. | Immutable lists/text are explicitly bounded; no mutable implementation references. | HIGH: record component changes are constructor and binary incompatible. |
| `net.maddkraft.maddprestige.api.id.ConfigRevisionId` | Validated stable identity/correlation value. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral, or compile/runtime metadata only. | Non-null unless `Optional`; fail-fast validation. | Validated conservative identifier syntax; ProviderId permits bounded owner:local form. | LOW/MEDIUM: validation tightening and semantic changes still require compatibility review. |
| `net.maddkraft.maddprestige.api.id.MetricId` | Validated stable identity/correlation value. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral, or compile/runtime metadata only. | Non-null unless `Optional`; fail-fast validation. | Validated conservative identifier syntax; ProviderId permits bounded owner:local form. | LOW/MEDIUM: validation tightening and semantic changes still require compatibility review. |
| `net.maddkraft.maddprestige.api.id.OperationId` | Validated stable identity/correlation value. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral, or compile/runtime metadata only. | Non-null unless `Optional`; fail-fast validation. | Validated conservative identifier syntax; ProviderId permits bounded owner:local form. | LOW/MEDIUM: validation tightening and semantic changes still require compatibility review. |
| `net.maddkraft.maddprestige.api.id.ProviderId` | Validated stable identity/correlation value. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral, or compile/runtime metadata only. | Non-null unless `Optional`; fail-fast validation. | Validated conservative identifier syntax; ProviderId permits bounded owner:local form. | LOW/MEDIUM: validation tightening and semantic changes still require compatibility review. |
| `net.maddkraft.maddprestige.api.id.StageId` | Validated stable identity/correlation value. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral, or compile/runtime metadata only. | Non-null unless `Optional`; fail-fast validation. | Validated conservative identifier syntax; ProviderId permits bounded owner:local form. | LOW/MEDIUM: validation tightening and semantic changes still require compatibility review. |
| `net.maddkraft.maddprestige.api.id.StringIdentifier` | Validated stable identity/correlation value. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral, or compile/runtime metadata only. | Non-null unless `Optional`; fail-fast validation. | Validated conservative identifier syntax; ProviderId permits bounded owner:local form. | LOW/MEDIUM: validation tightening and semantic changes still require compatibility review. |
| `net.maddkraft.maddprestige.api.metric.MetricMonotonicity` | Typed requirement-metric value or semantic enum. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral, or compile/runtime metadata only. | Non-null unless `Optional`; fail-fast validation. | Canonical values are bounded to 4096 characters; numeric/type parsing is fail-closed. | MEDIUM: added enum constants affect exhaustive consumer switches. |
| `net.maddkraft.maddprestige.api.metric.MetricOperator` | Typed requirement-metric value or semantic enum. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral, or compile/runtime metadata only. | Non-null unless `Optional`; fail-fast validation. | Canonical values are bounded to 4096 characters; numeric/type parsing is fail-closed. | MEDIUM: added enum constants affect exhaustive consumer switches. |
| `net.maddkraft.maddprestige.api.metric.MetricReadMode` | Typed requirement-metric value or semantic enum. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral, or compile/runtime metadata only. | Non-null unless `Optional`; fail-fast validation. | Canonical values are bounded to 4096 characters; numeric/type parsing is fail-closed. | MEDIUM: added enum constants affect exhaustive consumer switches. |
| `net.maddkraft.maddprestige.api.metric.MetricResetPolicy` | Typed requirement-metric value or semantic enum. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral, or compile/runtime metadata only. | Non-null unless `Optional`; fail-fast validation. | Canonical values are bounded to 4096 characters; numeric/type parsing is fail-closed. | MEDIUM: added enum constants affect exhaustive consumer switches. |
| `net.maddkraft.maddprestige.api.metric.MetricValue` | Typed requirement-metric value or semantic enum. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral, or compile/runtime metadata only. | Non-null unless `Optional`; fail-fast validation. | Canonical values are bounded to 4096 characters; numeric/type parsing is fail-closed. | LOW/MEDIUM: validation tightening and semantic changes still require compatibility review. |
| `net.maddkraft.maddprestige.api.metric.MetricValueType` | Typed requirement-metric value or semantic enum. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral, or compile/runtime metadata only. | Non-null unless `Optional`; fail-fast validation. | Canonical values are bounded to 4096 characters; numeric/type parsing is fail-closed. | MEDIUM: added enum constants affect exhaustive consumer switches. |
| `net.maddkraft.maddprestige.api.provider.ProviderCallContext` | Owner-attested provider SDK callback, metadata, request/result, or lifecycle capability. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral; handle unregister is asynchronous and exact-registration scoped. | Non-null unless `Optional`; fail-fast validation. | Metadata <=256 metrics; read batches <=256; IDs/text/maps/filter dimensions have explicit limits. | LOW/MEDIUM: validation tightening and semantic changes still require compatibility review. |
| `net.maddkraft.maddprestige.api.provider.ProviderCancellationSignal` | Owner-attested provider SDK callback, metadata, request/result, or lifecycle capability. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral; handle unregister is asynchronous and exact-registration scoped. | Non-null unless `Optional`; fail-fast validation. | Metadata <=256 metrics; read batches <=256; IDs/text/maps/filter dimensions have explicit limits. | LOW/MEDIUM: validation tightening and semantic changes still require compatibility review. |
| `net.maddkraft.maddprestige.api.provider.ProviderDeclaration` | Owner-attested provider SDK callback, metadata, request/result, or lifecycle capability. | Normative source contract present and included in the baseline review. | Invoked off registry locks on the bounded provider callback executor; deadline supplied in context. | Non-null unless `Optional`; fail-fast validation. | Metadata <=256 metrics; read batches <=256; IDs/text/maps/filter dimensions have explicit limits. | HIGH: adding abstract interface methods is binary/source breaking; default methods or a major version are required. |
| `net.maddkraft.maddprestige.api.provider.ProviderExecutionExpectation` | Owner-attested provider SDK callback, metadata, request/result, or lifecycle capability. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral; handle unregister is asynchronous and exact-registration scoped. | Non-null unless `Optional`; fail-fast validation. | Metadata <=256 metrics; read batches <=256; IDs/text/maps/filter dimensions have explicit limits. | LOW/MEDIUM: validation tightening and semantic changes still require compatibility review. |
| `net.maddkraft.maddprestige.api.provider.ProviderHealthState` | Owner-attested provider SDK callback, metadata, request/result, or lifecycle capability. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral; handle unregister is asynchronous and exact-registration scoped. | Non-null unless `Optional`; fail-fast validation. | Metadata <=256 metrics; read batches <=256; IDs/text/maps/filter dimensions have explicit limits. | LOW/MEDIUM: validation tightening and semantic changes still require compatibility review. |
| `net.maddkraft.maddprestige.api.provider.ProviderMetadata` | Owner-attested provider SDK callback, metadata, request/result, or lifecycle capability. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral; handle unregister is asynchronous and exact-registration scoped. | Non-null unless `Optional`; fail-fast validation. | Metadata <=256 metrics; read batches <=256; IDs/text/maps/filter dimensions have explicit limits. | HIGH: record component changes are constructor and binary incompatible. |
| `net.maddkraft.maddprestige.api.provider.ProviderMetricDefinition` | Owner-attested provider SDK callback, metadata, request/result, or lifecycle capability. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral; handle unregister is asynchronous and exact-registration scoped. | Non-null unless `Optional`; fail-fast validation. | Metadata <=256 metrics; read batches <=256; IDs/text/maps/filter dimensions have explicit limits. | HIGH: record component changes are constructor and binary incompatible. |
| `net.maddkraft.maddprestige.api.provider.ProviderMetricDimension` | Owner-attested provider SDK callback, metadata, request/result, or lifecycle capability. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral; handle unregister is asynchronous and exact-registration scoped. | Non-null unless `Optional`; fail-fast validation. | Metadata <=256 metrics; read batches <=256; IDs/text/maps/filter dimensions have explicit limits. | LOW/MEDIUM: validation tightening and semantic changes still require compatibility review. |
| `net.maddkraft.maddprestige.api.provider.ProviderMetricRequest` | Owner-attested provider SDK callback, metadata, request/result, or lifecycle capability. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral; handle unregister is asynchronous and exact-registration scoped. | Non-null unless `Optional`; fail-fast validation. | Metadata <=256 metrics; read batches <=256; IDs/text/maps/filter dimensions have explicit limits. | HIGH: record component changes are constructor and binary incompatible. |
| `net.maddkraft.maddprestige.api.provider.ProviderMetricResult` | Owner-attested provider SDK callback, metadata, request/result, or lifecycle capability. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral; handle unregister is asynchronous and exact-registration scoped. | Non-null unless `Optional`; fail-fast validation. | Metadata <=256 metrics; read batches <=256; IDs/text/maps/filter dimensions have explicit limits. | HIGH: record component changes are constructor and binary incompatible. |
| `net.maddkraft.maddprestige.api.provider.ProviderMetricStatus` | Owner-attested provider SDK callback, metadata, request/result, or lifecycle capability. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral; handle unregister is asynchronous and exact-registration scoped. | Non-null unless `Optional`; fail-fast validation. | Metadata <=256 metrics; read batches <=256; IDs/text/maps/filter dimensions have explicit limits. | MEDIUM: added enum constants affect exhaustive consumer switches. |
| `net.maddkraft.maddprestige.api.provider.ProviderRegistrationHandle` | Owner-attested provider SDK callback, metadata, request/result, or lifecycle capability. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral; handle unregister is asynchronous and exact-registration scoped. | Non-null unless `Optional`; fail-fast validation. | Metadata <=256 metrics; read batches <=256; IDs/text/maps/filter dimensions have explicit limits. | HIGH: adding abstract interface methods is binary/source breaking; default methods or a major version are required. |
| `net.maddkraft.maddprestige.api.provider.RequirementProvider` | Owner-attested provider SDK callback, metadata, request/result, or lifecycle capability. | Normative source contract present and included in the baseline review. | Invoked off registry locks on the bounded provider callback executor; deadline supplied in context. | Non-null unless `Optional`; fail-fast validation. | Metadata <=256 metrics; read batches <=256; IDs/text/maps/filter dimensions have explicit limits. | HIGH: adding abstract interface methods is binary/source breaking; default methods or a major version are required. |
| `net.maddkraft.maddprestige.api.service.CurrencyBalanceView` | Public service discovery, operation receipt, or immutable read DTO. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral once constructed. | Non-null unless `Optional`; fail-fast validation. | DTO strings/maps/lists are copied and bounded; Optional represents absence. | HIGH: record component changes are constructor and binary incompatible. |
| `net.maddkraft.maddprestige.api.service.MaddPrestigeService` | Public service discovery, operation receipt, or immutable read DTO. | Normative source contract present and included in the baseline review. | Potential-I/O reads/mutations are asynchronous; stages()/providers() are synchronous cached-only. | Non-null unless `Optional`; fail-fast validation. | DTO strings/maps/lists are copied and bounded; Optional represents absence. | HIGH: adding abstract interface methods is binary/source breaking; default methods or a major version are required. |
| `net.maddkraft.maddprestige.api.service.OperationEvaluation` | Public service discovery, operation receipt, or immutable read DTO. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral once constructed. | Non-null unless `Optional`; fail-fast validation. | DTO strings/maps/lists are copied and bounded; Optional represents absence. | LOW/MEDIUM: validation tightening and semantic changes still require compatibility review. |
| `net.maddkraft.maddprestige.api.service.OperationEvaluationStatus` | Public service discovery, operation receipt, or immutable read DTO. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral once constructed. | Non-null unless `Optional`; fail-fast validation. | DTO strings/maps/lists are copied and bounded; Optional represents absence. | MEDIUM: added enum constants affect exhaustive consumer switches. |
| `net.maddkraft.maddprestige.api.service.OperationKind` | Public service discovery, operation receipt, or immutable read DTO. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral once constructed. | Non-null unless `Optional`; fail-fast validation. | DTO strings/maps/lists are copied and bounded; Optional represents absence. | MEDIUM: added enum constants affect exhaustive consumer switches. |
| `net.maddkraft.maddprestige.api.service.OperationResult` | Public service discovery, operation receipt, or immutable read DTO. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral once constructed. | Non-null unless `Optional`; fail-fast validation. | DTO strings/maps/lists are copied and bounded; Optional represents absence. | HIGH: record component changes are constructor and binary incompatible. |
| `net.maddkraft.maddprestige.api.service.OperationSimulationView` | Public service discovery, operation receipt, or immutable read DTO. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral once constructed. | Non-null unless `Optional`; fail-fast validation. | DTO strings/maps/lists are copied and bounded; Optional represents absence. | HIGH: record component changes are constructor and binary incompatible. |
| `net.maddkraft.maddprestige.api.service.OperationStatus` | Public service discovery, operation receipt, or immutable read DTO. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral once constructed. | Non-null unless `Optional`; fail-fast validation. | DTO strings/maps/lists are copied and bounded; Optional represents absence. | MEDIUM: added enum constants affect exhaustive consumer switches. |
| `net.maddkraft.maddprestige.api.service.PlayerProgressSnapshot` | Public service discovery, operation receipt, or immutable read DTO. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral once constructed. | Non-null unless `Optional`; fail-fast validation. | DTO strings/maps/lists are copied and bounded; Optional represents absence. | HIGH: record component changes are constructor and binary incompatible. |
| `net.maddkraft.maddprestige.api.service.ProviderView` | Public service discovery, operation receipt, or immutable read DTO. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral once constructed. | Non-null unless `Optional`; fail-fast validation. | DTO strings/maps/lists are copied and bounded; Optional represents absence. | HIGH: record component changes are constructor and binary incompatible. |
| `net.maddkraft.maddprestige.api.service.RequirementProgressStatus` | Public service discovery, operation receipt, or immutable read DTO. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral once constructed. | Non-null unless `Optional`; fail-fast validation. | DTO strings/maps/lists are copied and bounded; Optional represents absence. | MEDIUM: added enum constants affect exhaustive consumer switches. |
| `net.maddkraft.maddprestige.api.service.RequirementProgressView` | Public service discovery, operation receipt, or immutable read DTO. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral once constructed. | Non-null unless `Optional`; fail-fast validation. | DTO strings/maps/lists are copied and bounded; Optional represents absence. | HIGH: record component changes are constructor and binary incompatible. |
| `net.maddkraft.maddprestige.api.service.SeasonView` | Public service discovery, operation receipt, or immutable read DTO. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral once constructed. | Non-null unless `Optional`; fail-fast validation. | DTO strings/maps/lists are copied and bounded; Optional represents absence. | HIGH: record component changes are constructor and binary incompatible. |
| `net.maddkraft.maddprestige.api.service.ServiceError` | Public service discovery, operation receipt, or immutable read DTO. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral once constructed. | Non-null unless `Optional`; fail-fast validation. | DTO strings/maps/lists are copied and bounded; Optional represents absence. | LOW/MEDIUM: validation tightening and semantic changes still require compatibility review. |
| `net.maddkraft.maddprestige.api.service.ServiceResult` | Public service discovery, operation receipt, or immutable read DTO. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral once constructed. | Non-null unless `Optional`; fail-fast validation. | DTO strings/maps/lists are copied and bounded; Optional represents absence. | HIGH: record component changes are constructor and binary incompatible. |
| `net.maddkraft.maddprestige.api.service.StageView` | Public service discovery, operation receipt, or immutable read DTO. | Normative source contract present and included in the baseline review. | Immutable/thread-neutral once constructed. | Non-null unless `Optional`; fail-fast validation. | DTO strings/maps/lists are copied and bounded; Optional represents absence. | HIGH: record component changes are constructor and binary incompatible. |

## Exact public members by type

### `net.maddkraft.maddprestige.api.action.ActionCharacteristics` - LEGACY/PENDING REMOVAL

```text
Compiled from "ActionCharacteristics.java"
public final class net.maddkraft.maddprestige.api.action.ActionCharacteristics extends java.lang.Record {
  public net.maddkraft.maddprestige.api.action.ActionCharacteristics(boolean, boolean, boolean, boolean);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public boolean idempotent();
  public boolean reversible();
  public boolean reconcilable();
  public boolean externalUncertaintyPossible();
}
```

### `net.maddkraft.maddprestige.api.action.ActionExecutionResult` - LEGACY/PENDING REMOVAL

```text
Compiled from "ActionExecutionResult.java"
public final class net.maddkraft.maddprestige.api.action.ActionExecutionResult extends java.lang.Record {
  public net.maddkraft.maddprestige.api.action.ActionExecutionResult(net.maddkraft.maddprestige.api.action.ActionExecutionStatus, java.util.Optional<java.lang.String>);
  public static net.maddkraft.maddprestige.api.action.ActionExecutionResult applied();
  public static net.maddkraft.maddprestige.api.action.ActionExecutionResult unchanged();
  public static net.maddkraft.maddprestige.api.action.ActionExecutionResult failed(java.lang.String);
  public static net.maddkraft.maddprestige.api.action.ActionExecutionResult uncertain(java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.action.ActionExecutionStatus status();
  public java.util.Optional<java.lang.String> detail();
}
```

### `net.maddkraft.maddprestige.api.action.ActionExecutionStatus` - LEGACY/PENDING REMOVAL

```text
Compiled from "ActionExecutionStatus.java"
public final class net.maddkraft.maddprestige.api.action.ActionExecutionStatus extends java.lang.Enum<net.maddkraft.maddprestige.api.action.ActionExecutionStatus> {
  public static final net.maddkraft.maddprestige.api.action.ActionExecutionStatus APPLIED;
  public static final net.maddkraft.maddprestige.api.action.ActionExecutionStatus UNCHANGED;
  public static final net.maddkraft.maddprestige.api.action.ActionExecutionStatus FAILED;
  public static final net.maddkraft.maddprestige.api.action.ActionExecutionStatus UNCERTAIN;
  public static net.maddkraft.maddprestige.api.action.ActionExecutionStatus[] values();
  public static net.maddkraft.maddprestige.api.action.ActionExecutionStatus valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.action.PreflightStatus` - LEGACY/PENDING REMOVAL

```text
Compiled from "PreflightStatus.java"
public final class net.maddkraft.maddprestige.api.action.PreflightStatus extends java.lang.Enum<net.maddkraft.maddprestige.api.action.PreflightStatus> {
  public static final net.maddkraft.maddprestige.api.action.PreflightStatus READY;
  public static final net.maddkraft.maddprestige.api.action.PreflightStatus BLOCKED;
  public static final net.maddkraft.maddprestige.api.action.PreflightStatus UNAVAILABLE;
  public static final net.maddkraft.maddprestige.api.action.PreflightStatus INVALID;
  public static net.maddkraft.maddprestige.api.action.PreflightStatus[] values();
  public static net.maddkraft.maddprestige.api.action.PreflightStatus valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.annotation.Experimental` - STABLE 2.x BASELINE

```text
Compiled from "Experimental.java"
public interface net.maddkraft.maddprestige.api.annotation.Experimental extends java.lang.annotation.Annotation {
}
```

### `net.maddkraft.maddprestige.api.annotation.Stable` - STABLE 2.x BASELINE

```text
Compiled from "Stable.java"
public interface net.maddkraft.maddprestige.api.annotation.Stable extends java.lang.annotation.Annotation {
}
```

### `net.maddkraft.maddprestige.api.audit.AuditOutcome` - INTERNAL/SHOULD NOT BE PUBLIC

```text
Compiled from "AuditOutcome.java"
public final class net.maddkraft.maddprestige.api.audit.AuditOutcome extends java.lang.Enum<net.maddkraft.maddprestige.api.audit.AuditOutcome> {
  public static final net.maddkraft.maddprestige.api.audit.AuditOutcome SUCCEEDED;
  public static final net.maddkraft.maddprestige.api.audit.AuditOutcome FAILED;
  public static final net.maddkraft.maddprestige.api.audit.AuditOutcome DENIED;
  public static final net.maddkraft.maddprestige.api.audit.AuditOutcome UNCERTAIN;
  public static final net.maddkraft.maddprestige.api.audit.AuditOutcome NEEDS_RECONCILIATION;
  public static net.maddkraft.maddprestige.api.audit.AuditOutcome[] values();
  public static net.maddkraft.maddprestige.api.audit.AuditOutcome valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.audit.AuditRecord` - INTERNAL/SHOULD NOT BE PUBLIC

```text
Compiled from "AuditRecord.java"
public final class net.maddkraft.maddprestige.api.audit.AuditRecord extends java.lang.Record {
  public net.maddkraft.maddprestige.api.audit.AuditRecord(java.util.UUID, net.maddkraft.maddprestige.api.operation.Actor, java.util.Optional<java.util.UUID>, java.util.Optional<net.maddkraft.maddprestige.api.id.OperationId>, java.util.Optional<net.maddkraft.maddprestige.api.id.ConfigRevisionId>, java.lang.String, java.util.Optional<net.maddkraft.maddprestige.api.audit.AuditValue>, java.util.Optional<net.maddkraft.maddprestige.api.audit.AuditValue>, java.lang.String, java.lang.String, net.maddkraft.maddprestige.api.audit.AuditOutcome, java.util.Optional<java.lang.String>, java.util.UUID, java.time.Instant);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.util.UUID auditId();
  public net.maddkraft.maddprestige.api.operation.Actor actor();
  public java.util.Optional<java.util.UUID> target();
  public java.util.Optional<net.maddkraft.maddprestige.api.id.OperationId> operationId();
  public java.util.Optional<net.maddkraft.maddprestige.api.id.ConfigRevisionId> configRevision();
  public java.lang.String providerAction();
  public java.util.Optional<net.maddkraft.maddprestige.api.audit.AuditValue> oldValue();
  public java.util.Optional<net.maddkraft.maddprestige.api.audit.AuditValue> newValue();
  public java.lang.String sourceSurface();
  public java.lang.String reason();
  public net.maddkraft.maddprestige.api.audit.AuditOutcome outcome();
  public java.util.Optional<java.lang.String> failureOrUncertainty();
  public java.util.UUID correlationId();
  public java.time.Instant timestamp();
}
```

### `net.maddkraft.maddprestige.api.audit.AuditValue` - INTERNAL/SHOULD NOT BE PUBLIC

```text
Compiled from "AuditValue.java"
public final class net.maddkraft.maddprestige.api.audit.AuditValue extends java.lang.Record {
  public static final java.lang.String REDACTED;
  public net.maddkraft.maddprestige.api.audit.AuditValue(java.lang.String, boolean);
  public java.lang.String render(boolean);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String value();
  public boolean sensitive();
}
```

### `net.maddkraft.maddprestige.api.cost.CostDefinition` - LEGACY/PENDING REMOVAL

```text
Compiled from "CostDefinition.java"
public final class net.maddkraft.maddprestige.api.cost.CostDefinition extends java.lang.Record {
  public net.maddkraft.maddprestige.api.cost.CostDefinition(net.maddkraft.maddprestige.api.id.CostId, net.maddkraft.maddprestige.api.id.ProviderId, java.lang.String, net.maddkraft.maddprestige.api.metric.MetricValue, java.util.Map<java.lang.String, java.lang.String>, java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.id.CostId id();
  public net.maddkraft.maddprestige.api.id.ProviderId providerId();
  public java.lang.String type();
  public net.maddkraft.maddprestige.api.metric.MetricValue amount();
  public java.util.Map<java.lang.String, java.lang.String> metadata();
  public java.lang.String displayName();
}
```

### `net.maddkraft.maddprestige.api.cost.CostPreflight` - LEGACY/PENDING REMOVAL

```text
Compiled from "CostPreflight.java"
public final class net.maddkraft.maddprestige.api.cost.CostPreflight extends java.lang.Record {
  public net.maddkraft.maddprestige.api.cost.CostPreflight(net.maddkraft.maddprestige.api.action.PreflightStatus, java.util.Optional<net.maddkraft.maddprestige.api.cost.PlannedCost>, java.lang.String);
  public static net.maddkraft.maddprestige.api.cost.CostPreflight ready(net.maddkraft.maddprestige.api.cost.PlannedCost);
  public static net.maddkraft.maddprestige.api.cost.CostPreflight blocked(java.lang.String);
  public static net.maddkraft.maddprestige.api.cost.CostPreflight unavailable(java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.action.PreflightStatus status();
  public java.util.Optional<net.maddkraft.maddprestige.api.cost.PlannedCost> plannedCost();
  public java.lang.String detail();
}
```

### `net.maddkraft.maddprestige.api.cost.CostProvider` - LEGACY/PENDING REMOVAL

```text
Compiled from "CostProvider.java"
public interface net.maddkraft.maddprestige.api.cost.CostProvider extends net.maddkraft.maddprestige.api.provider.Provider {
  public abstract net.maddkraft.maddprestige.api.action.ActionCharacteristics characteristics(net.maddkraft.maddprestige.api.cost.CostDefinition);
  public abstract net.maddkraft.maddprestige.api.validation.ValidationReport validate(net.maddkraft.maddprestige.api.cost.CostDefinition);
  public abstract java.util.concurrent.CompletionStage<net.maddkraft.maddprestige.api.cost.CostPreflight> preflight(net.maddkraft.maddprestige.api.cost.PlannedCost);
  public default java.util.concurrent.CompletionStage<java.util.List<net.maddkraft.maddprestige.api.cost.CostPreflight>> preflightBatch(java.util.List<net.maddkraft.maddprestige.api.cost.PlannedCost>);
  public abstract java.util.concurrent.CompletionStage<net.maddkraft.maddprestige.api.action.ActionExecutionResult> execute(net.maddkraft.maddprestige.api.cost.PlannedCost);
  public default java.util.concurrent.CompletionStage<net.maddkraft.maddprestige.api.action.ActionExecutionResult> compensate(net.maddkraft.maddprestige.api.cost.PlannedCost);
}
```

### `net.maddkraft.maddprestige.api.cost.NativeRecoverableCostProvider` - INTERNAL/SHOULD NOT BE PUBLIC

```text
Compiled from "NativeRecoverableCostProvider.java"
public interface net.maddkraft.maddprestige.api.cost.NativeRecoverableCostProvider extends net.maddkraft.maddprestige.api.cost.CostProvider {
}
```

### `net.maddkraft.maddprestige.api.cost.PlannedCost` - INTERNAL/SHOULD NOT BE PUBLIC

```text
Compiled from "PlannedCost.java"
public final class net.maddkraft.maddprestige.api.cost.PlannedCost extends java.lang.Record {
  public net.maddkraft.maddprestige.api.cost.PlannedCost(net.maddkraft.maddprestige.api.id.OperationId, java.lang.String, java.util.UUID, net.maddkraft.maddprestige.api.cost.CostDefinition, net.maddkraft.maddprestige.api.id.ConfigRevisionId, long, net.maddkraft.maddprestige.api.action.ActionCharacteristics, java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.id.OperationId operationId();
  public java.lang.String actionId();
  public java.util.UUID playerId();
  public net.maddkraft.maddprestige.api.cost.CostDefinition definition();
  public net.maddkraft.maddprestige.api.id.ConfigRevisionId configRevision();
  public long providerGeneration();
  public net.maddkraft.maddprestige.api.action.ActionCharacteristics characteristics();
  public java.lang.String redactedPreview();
}
```

### `net.maddkraft.maddprestige.api.event.ConfigAppliedSnapshot` - STABLE 2.x BASELINE

```text
Compiled from "ConfigAppliedSnapshot.java"
public final class net.maddkraft.maddprestige.api.event.ConfigAppliedSnapshot extends java.lang.Record {
  public net.maddkraft.maddprestige.api.event.ConfigAppliedSnapshot(net.maddkraft.maddprestige.api.id.ConfigRevisionId, java.util.Optional<net.maddkraft.maddprestige.api.id.ConfigRevisionId>, java.util.List<java.lang.String>, java.time.Instant);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.id.ConfigRevisionId revision();
  public java.util.Optional<net.maddkraft.maddprestige.api.id.ConfigRevisionId> priorRevision();
  public java.util.List<java.lang.String> changedDocuments();
  public java.time.Instant occurredAt();
}
```

### `net.maddkraft.maddprestige.api.event.OperationEventSnapshot` - STABLE 2.x BASELINE

```text
Compiled from "OperationEventSnapshot.java"
public final class net.maddkraft.maddprestige.api.event.OperationEventSnapshot extends java.lang.Record {
  public net.maddkraft.maddprestige.api.event.OperationEventSnapshot(java.util.UUID, java.util.Optional<net.maddkraft.maddprestige.api.id.OperationId>, java.util.UUID, net.maddkraft.maddprestige.api.service.OperationKind, java.util.Optional<net.maddkraft.maddprestige.api.id.StageId>, java.util.Optional<net.maddkraft.maddprestige.api.id.StageId>, net.maddkraft.maddprestige.api.id.ConfigRevisionId, java.util.Optional<net.maddkraft.maddprestige.api.service.OperationStatus>, java.time.Instant);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.util.UUID correlationId();
  public java.util.Optional<net.maddkraft.maddprestige.api.id.OperationId> durableOperationId();
  public java.util.UUID playerId();
  public net.maddkraft.maddprestige.api.service.OperationKind kind();
  public java.util.Optional<net.maddkraft.maddprestige.api.id.StageId> sourceStage();
  public java.util.Optional<net.maddkraft.maddprestige.api.id.StageId> targetStage();
  public net.maddkraft.maddprestige.api.id.ConfigRevisionId configRevision();
  public java.util.Optional<net.maddkraft.maddprestige.api.service.OperationStatus> terminalStatus();
  public java.time.Instant occurredAt();
}
```

### `net.maddkraft.maddprestige.api.event.ProviderHealthChangedSnapshot` - STABLE 2.x BASELINE

```text
Compiled from "ProviderHealthChangedSnapshot.java"
public final class net.maddkraft.maddprestige.api.event.ProviderHealthChangedSnapshot extends java.lang.Record {
  public net.maddkraft.maddprestige.api.event.ProviderHealthChangedSnapshot(net.maddkraft.maddprestige.api.id.ProviderId, net.maddkraft.maddprestige.api.provider.ProviderHealthState, net.maddkraft.maddprestige.api.provider.ProviderHealthState, java.lang.String, java.time.Instant);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.id.ProviderId providerId();
  public net.maddkraft.maddprestige.api.provider.ProviderHealthState previous();
  public net.maddkraft.maddprestige.api.provider.ProviderHealthState current();
  public java.lang.String code();
  public java.time.Instant occurredAt();
}
```

### `net.maddkraft.maddprestige.api.explanation.ExplanationNode` - LEGACY/PENDING REMOVAL

```text
Compiled from "ExplanationNode.java"
public final class net.maddkraft.maddprestige.api.explanation.ExplanationNode extends java.lang.Record {
  public net.maddkraft.maddprestige.api.explanation.ExplanationNode(java.lang.String, net.maddkraft.maddprestige.api.explanation.ExplanationStatus, java.lang.String, java.util.Map<java.lang.String, java.lang.String>, java.util.List<net.maddkraft.maddprestige.api.explanation.ExplanationNode>);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String code();
  public net.maddkraft.maddprestige.api.explanation.ExplanationStatus status();
  public java.lang.String summary();
  public java.util.Map<java.lang.String, java.lang.String> facts();
  public java.util.List<net.maddkraft.maddprestige.api.explanation.ExplanationNode> children();
}
```

### `net.maddkraft.maddprestige.api.explanation.ExplanationStatus` - LEGACY/PENDING REMOVAL

```text
Compiled from "ExplanationStatus.java"
public final class net.maddkraft.maddprestige.api.explanation.ExplanationStatus extends java.lang.Enum<net.maddkraft.maddprestige.api.explanation.ExplanationStatus> {
  public static final net.maddkraft.maddprestige.api.explanation.ExplanationStatus SATISFIED;
  public static final net.maddkraft.maddprestige.api.explanation.ExplanationStatus UNSATISFIED;
  public static final net.maddkraft.maddprestige.api.explanation.ExplanationStatus BLOCKED;
  public static final net.maddkraft.maddprestige.api.explanation.ExplanationStatus UNAVAILABLE;
  public static final net.maddkraft.maddprestige.api.explanation.ExplanationStatus INVALID;
  public static final net.maddkraft.maddprestige.api.explanation.ExplanationStatus ERROR;
  public static final net.maddkraft.maddprestige.api.explanation.ExplanationStatus INFORMATIONAL;
  public static net.maddkraft.maddprestige.api.explanation.ExplanationStatus[] values();
  public static net.maddkraft.maddprestige.api.explanation.ExplanationStatus valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.id.ConfigRevisionId` - STABLE 2.x BASELINE

```text
Compiled from "ConfigRevisionId.java"
public final class net.maddkraft.maddprestige.api.id.ConfigRevisionId extends java.lang.Record implements net.maddkraft.maddprestige.api.id.StringIdentifier {
  public net.maddkraft.maddprestige.api.id.ConfigRevisionId(java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String value();
}
```

### `net.maddkraft.maddprestige.api.id.CostId` - LEGACY/PENDING REMOVAL

```text
Compiled from "CostId.java"
public final class net.maddkraft.maddprestige.api.id.CostId extends java.lang.Record implements net.maddkraft.maddprestige.api.id.StringIdentifier {
  public net.maddkraft.maddprestige.api.id.CostId(java.lang.String);
  public java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String value();
}
```

### `net.maddkraft.maddprestige.api.id.CurrencyId` - LEGACY/PENDING REMOVAL

```text
Compiled from "CurrencyId.java"
public final class net.maddkraft.maddprestige.api.id.CurrencyId extends java.lang.Record implements net.maddkraft.maddprestige.api.id.StringIdentifier {
  public net.maddkraft.maddprestige.api.id.CurrencyId(java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String value();
}
```

### `net.maddkraft.maddprestige.api.id.EntitlementId` - LEGACY/PENDING REMOVAL

```text
Compiled from "EntitlementId.java"
public final class net.maddkraft.maddprestige.api.id.EntitlementId extends java.lang.Record implements net.maddkraft.maddprestige.api.id.StringIdentifier {
  public net.maddkraft.maddprestige.api.id.EntitlementId(java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String value();
}
```

### `net.maddkraft.maddprestige.api.id.FieldId` - INTERNAL/SHOULD NOT BE PUBLIC

```text
Compiled from "FieldId.java"
public final class net.maddkraft.maddprestige.api.id.FieldId extends java.lang.Record implements net.maddkraft.maddprestige.api.id.StringIdentifier {
  public net.maddkraft.maddprestige.api.id.FieldId(java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String value();
}
```

### `net.maddkraft.maddprestige.api.id.IdentifierRules` - INTERNAL/SHOULD NOT BE PUBLIC

```text
Compiled from "IdentifierRules.java"
public final class net.maddkraft.maddprestige.api.id.IdentifierRules {
  public static final int MAX_LENGTH;
  public static java.lang.String requireValid(java.lang.String, java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.id.MetricId` - STABLE 2.x BASELINE

```text
Compiled from "MetricId.java"
public final class net.maddkraft.maddprestige.api.id.MetricId extends java.lang.Record implements net.maddkraft.maddprestige.api.id.StringIdentifier {
  public net.maddkraft.maddprestige.api.id.MetricId(java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String value();
}
```

### `net.maddkraft.maddprestige.api.id.MilestoneId` - LEGACY/PENDING REMOVAL

```text
Compiled from "MilestoneId.java"
public final class net.maddkraft.maddprestige.api.id.MilestoneId extends java.lang.Record implements net.maddkraft.maddprestige.api.id.StringIdentifier {
  public net.maddkraft.maddprestige.api.id.MilestoneId(java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String value();
}
```

### `net.maddkraft.maddprestige.api.id.OperationId` - STABLE 2.x BASELINE

```text
Compiled from "OperationId.java"
public final class net.maddkraft.maddprestige.api.id.OperationId extends java.lang.Record {
  public net.maddkraft.maddprestige.api.id.OperationId(java.util.UUID);
  public static net.maddkraft.maddprestige.api.id.OperationId random();
  public java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.util.UUID value();
}
```

### `net.maddkraft.maddprestige.api.id.ProviderId` - STABLE 2.x BASELINE

```text
Compiled from "ProviderId.java"
public final class net.maddkraft.maddprestige.api.id.ProviderId extends java.lang.Record implements net.maddkraft.maddprestige.api.id.StringIdentifier {
  public net.maddkraft.maddprestige.api.id.ProviderId(java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String value();
}
```

### `net.maddkraft.maddprestige.api.id.RequirementId` - LEGACY/PENDING REMOVAL

```text
Compiled from "RequirementId.java"
public final class net.maddkraft.maddprestige.api.id.RequirementId extends java.lang.Record implements net.maddkraft.maddprestige.api.id.StringIdentifier {
  public net.maddkraft.maddprestige.api.id.RequirementId(java.lang.String);
  public java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String value();
}
```

### `net.maddkraft.maddprestige.api.id.RewardId` - LEGACY/PENDING REMOVAL

```text
Compiled from "RewardId.java"
public final class net.maddkraft.maddprestige.api.id.RewardId extends java.lang.Record implements net.maddkraft.maddprestige.api.id.StringIdentifier {
  public net.maddkraft.maddprestige.api.id.RewardId(java.lang.String);
  public java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String value();
}
```

### `net.maddkraft.maddprestige.api.id.ScopeId` - LEGACY/PENDING REMOVAL

```text
Compiled from "ScopeId.java"
public final class net.maddkraft.maddprestige.api.id.ScopeId extends java.lang.Record implements net.maddkraft.maddprestige.api.id.StringIdentifier {
  public net.maddkraft.maddprestige.api.id.ScopeId(java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String value();
}
```

### `net.maddkraft.maddprestige.api.id.SeasonId` - LEGACY/PENDING REMOVAL

```text
Compiled from "SeasonId.java"
public final class net.maddkraft.maddprestige.api.id.SeasonId extends java.lang.Record implements net.maddkraft.maddprestige.api.id.StringIdentifier {
  public net.maddkraft.maddprestige.api.id.SeasonId(java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String value();
}
```

### `net.maddkraft.maddprestige.api.id.StageId` - STABLE 2.x BASELINE

```text
Compiled from "StageId.java"
public final class net.maddkraft.maddprestige.api.id.StageId extends java.lang.Record implements net.maddkraft.maddprestige.api.id.StringIdentifier {
  public net.maddkraft.maddprestige.api.id.StageId(java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String value();
}
```

### `net.maddkraft.maddprestige.api.id.StringIdentifier` - STABLE 2.x BASELINE

```text
Compiled from "StringIdentifier.java"
public interface net.maddkraft.maddprestige.api.id.StringIdentifier {
  public abstract java.lang.String value();
}
```

### `net.maddkraft.maddprestige.api.metric.MetricDescriptor` - LEGACY/PENDING REMOVAL

```text
Compiled from "MetricDescriptor.java"
public final class net.maddkraft.maddprestige.api.metric.MetricDescriptor extends java.lang.Record {
  public net.maddkraft.maddprestige.api.metric.MetricDescriptor(net.maddkraft.maddprestige.api.id.ProviderId, net.maddkraft.maddprestige.api.id.MetricId, net.maddkraft.maddprestige.api.metric.MetricValueType, java.util.Set<net.maddkraft.maddprestige.api.metric.MetricOperator>, java.util.Set<net.maddkraft.maddprestige.api.metric.MetricReadMode>, boolean, net.maddkraft.maddprestige.api.metric.MetricMonotonicity, net.maddkraft.maddprestige.api.metric.MetricResetPolicy, java.util.Map<java.lang.String, net.maddkraft.maddprestige.api.metric.MetricDimension>, java.lang.String, java.lang.String, java.lang.String, java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.id.ProviderId providerId();
  public net.maddkraft.maddprestige.api.id.MetricId metricId();
  public net.maddkraft.maddprestige.api.metric.MetricValueType valueType();
  public java.util.Set<net.maddkraft.maddprestige.api.metric.MetricOperator> supportedOperators();
  public java.util.Set<net.maddkraft.maddprestige.api.metric.MetricReadMode> supportedReads();
  public boolean snapshotDeltaSupported();
  public net.maddkraft.maddprestige.api.metric.MetricMonotonicity monotonicity();
  public net.maddkraft.maddprestige.api.metric.MetricResetPolicy resetPolicy();
  public java.util.Map<java.lang.String, net.maddkraft.maddprestige.api.metric.MetricDimension> dimensions();
  public java.lang.String displayName();
  public java.lang.String description();
  public java.lang.String unit();
  public java.lang.String reliability();
}
```

### `net.maddkraft.maddprestige.api.metric.MetricDimension` - LEGACY/PENDING REMOVAL

```text
Compiled from "MetricDimension.java"
public final class net.maddkraft.maddprestige.api.metric.MetricDimension extends java.lang.Record {
  public net.maddkraft.maddprestige.api.metric.MetricDimension(java.lang.String, boolean, java.util.Set<java.lang.String>, java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String id();
  public boolean required();
  public java.util.Set<java.lang.String> allowedValues();
  public java.lang.String description();
}
```

### `net.maddkraft.maddprestige.api.metric.MetricMonotonicity` - STABLE 2.x BASELINE

```text
Compiled from "MetricMonotonicity.java"
public final class net.maddkraft.maddprestige.api.metric.MetricMonotonicity extends java.lang.Enum<net.maddkraft.maddprestige.api.metric.MetricMonotonicity> {
  public static final net.maddkraft.maddprestige.api.metric.MetricMonotonicity MONOTONIC;
  public static final net.maddkraft.maddprestige.api.metric.MetricMonotonicity NON_MONOTONIC;
  public static final net.maddkraft.maddprestige.api.metric.MetricMonotonicity UNKNOWN;
  public static net.maddkraft.maddprestige.api.metric.MetricMonotonicity[] values();
  public static net.maddkraft.maddprestige.api.metric.MetricMonotonicity valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.metric.MetricOperator` - STABLE 2.x BASELINE

```text
Compiled from "MetricOperator.java"
public final class net.maddkraft.maddprestige.api.metric.MetricOperator extends java.lang.Enum<net.maddkraft.maddprestige.api.metric.MetricOperator> {
  public static final net.maddkraft.maddprestige.api.metric.MetricOperator GREATER_THAN;
  public static final net.maddkraft.maddprestige.api.metric.MetricOperator GREATER_OR_EQUAL;
  public static final net.maddkraft.maddprestige.api.metric.MetricOperator LESS_THAN;
  public static final net.maddkraft.maddprestige.api.metric.MetricOperator LESS_OR_EQUAL;
  public static final net.maddkraft.maddprestige.api.metric.MetricOperator EQUAL;
  public static final net.maddkraft.maddprestige.api.metric.MetricOperator NOT_EQUAL;
  public static final net.maddkraft.maddprestige.api.metric.MetricOperator IN_RANGE;
  public static net.maddkraft.maddprestige.api.metric.MetricOperator[] values();
  public static net.maddkraft.maddprestige.api.metric.MetricOperator valueOf(java.lang.String);
  public static java.util.Set<net.maddkraft.maddprestige.api.metric.MetricOperator> compatibleWith(net.maddkraft.maddprestige.api.metric.MetricValueType);
  public boolean supports(net.maddkraft.maddprestige.api.metric.MetricValueType);
}
```

### `net.maddkraft.maddprestige.api.metric.MetricProvider` - EXPERIMENTAL

```text
Compiled from "MetricProvider.java"
public interface net.maddkraft.maddprestige.api.metric.MetricProvider extends net.maddkraft.maddprestige.api.provider.Provider {
  public abstract java.util.Collection<net.maddkraft.maddprestige.api.metric.MetricDescriptor> metrics();
  public default net.maddkraft.maddprestige.api.metric.MetricDescriptor requireMetric(net.maddkraft.maddprestige.api.id.MetricId);
  public abstract java.util.concurrent.CompletionStage<java.util.Map<net.maddkraft.maddprestige.api.metric.MetricQuery, net.maddkraft.maddprestige.api.metric.MetricSample>> read(java.util.UUID, java.util.List<net.maddkraft.maddprestige.api.metric.MetricQuery>, long);
}
```

### `net.maddkraft.maddprestige.api.metric.MetricQuery` - LEGACY/PENDING REMOVAL

```text
Compiled from "MetricQuery.java"
public final class net.maddkraft.maddprestige.api.metric.MetricQuery extends java.lang.Record {
  public net.maddkraft.maddprestige.api.metric.MetricQuery(net.maddkraft.maddprestige.api.id.MetricId, net.maddkraft.maddprestige.api.metric.MetricReadMode, java.util.Map<java.lang.String, java.lang.String>);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.id.MetricId metricId();
  public net.maddkraft.maddprestige.api.metric.MetricReadMode readMode();
  public java.util.Map<java.lang.String, java.lang.String> filters();
}
```

### `net.maddkraft.maddprestige.api.metric.MetricReadMode` - STABLE 2.x BASELINE

```text
Compiled from "MetricReadMode.java"
public final class net.maddkraft.maddprestige.api.metric.MetricReadMode extends java.lang.Enum<net.maddkraft.maddprestige.api.metric.MetricReadMode> {
  public static final net.maddkraft.maddprestige.api.metric.MetricReadMode CURRENT;
  public static final net.maddkraft.maddprestige.api.metric.MetricReadMode LIFETIME;
  public static net.maddkraft.maddprestige.api.metric.MetricReadMode[] values();
  public static net.maddkraft.maddprestige.api.metric.MetricReadMode valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.metric.MetricResetPolicy` - STABLE 2.x BASELINE

```text
Compiled from "MetricResetPolicy.java"
public final class net.maddkraft.maddprestige.api.metric.MetricResetPolicy extends java.lang.Enum<net.maddkraft.maddprestige.api.metric.MetricResetPolicy> {
  public static final net.maddkraft.maddprestige.api.metric.MetricResetPolicy FAIL_RECONCILIATION;
  public static final net.maddkraft.maddprestige.api.metric.MetricResetPolicy PROVIDER_DEFINED_RESET;
  public static final net.maddkraft.maddprestige.api.metric.MetricResetPolicy NOT_APPLICABLE;
  public static net.maddkraft.maddprestige.api.metric.MetricResetPolicy[] values();
  public static net.maddkraft.maddprestige.api.metric.MetricResetPolicy valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.metric.MetricSample` - LEGACY/PENDING REMOVAL

```text
Compiled from "MetricSample.java"
public final class net.maddkraft.maddprestige.api.metric.MetricSample extends java.lang.Record {
  public net.maddkraft.maddprestige.api.metric.MetricSample(net.maddkraft.maddprestige.api.metric.MetricSampleStatus, java.util.Optional<net.maddkraft.maddprestige.api.metric.MetricValue>, long, java.time.Instant, java.lang.String, java.util.Optional<java.lang.String>);
  public static net.maddkraft.maddprestige.api.metric.MetricSample available(net.maddkraft.maddprestige.api.metric.MetricValue, long, java.time.Instant, java.lang.String);
  public static net.maddkraft.maddprestige.api.metric.MetricSample unavailable(long, java.time.Instant, java.lang.String, java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.metric.MetricSampleStatus status();
  public java.util.Optional<net.maddkraft.maddprestige.api.metric.MetricValue> value();
  public long providerGeneration();
  public java.time.Instant observedAt();
  public java.lang.String provenance();
  public java.util.Optional<java.lang.String> detail();
}
```

### `net.maddkraft.maddprestige.api.metric.MetricSampleStatus` - LEGACY/PENDING REMOVAL

```text
Compiled from "MetricSampleStatus.java"
public final class net.maddkraft.maddprestige.api.metric.MetricSampleStatus extends java.lang.Enum<net.maddkraft.maddprestige.api.metric.MetricSampleStatus> {
  public static final net.maddkraft.maddprestige.api.metric.MetricSampleStatus AVAILABLE;
  public static final net.maddkraft.maddprestige.api.metric.MetricSampleStatus UNAVAILABLE;
  public static final net.maddkraft.maddprestige.api.metric.MetricSampleStatus INVALID;
  public static final net.maddkraft.maddprestige.api.metric.MetricSampleStatus ERROR;
  public static net.maddkraft.maddprestige.api.metric.MetricSampleStatus[] values();
  public static net.maddkraft.maddprestige.api.metric.MetricSampleStatus valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.metric.MetricValue` - STABLE 2.x BASELINE

```text
Compiled from "MetricValue.java"
public final class net.maddkraft.maddprestige.api.metric.MetricValue extends java.lang.Record implements java.lang.Comparable<net.maddkraft.maddprestige.api.metric.MetricValue> {
  public net.maddkraft.maddprestige.api.metric.MetricValue(net.maddkraft.maddprestige.api.metric.MetricValueType, java.lang.String);
  public static net.maddkraft.maddprestige.api.metric.MetricValue parse(net.maddkraft.maddprestige.api.metric.MetricValueType, java.lang.String);
  public static net.maddkraft.maddprestige.api.metric.MetricValue integer(long);
  public static net.maddkraft.maddprestige.api.metric.MetricValue decimal(java.lang.String);
  public static net.maddkraft.maddprestige.api.metric.MetricValue count(long);
  public static net.maddkraft.maddprestige.api.metric.MetricValue duration(java.time.Duration);
  public static net.maddkraft.maddprestige.api.metric.MetricValue bool(boolean);
  public java.math.BigDecimal asNumber();
  public net.maddkraft.maddprestige.api.metric.MetricValue subtract(net.maddkraft.maddprestige.api.metric.MetricValue);
  public static net.maddkraft.maddprestige.api.metric.MetricValue fromNumber(net.maddkraft.maddprestige.api.metric.MetricValueType, java.math.BigDecimal);
  public int compareTo(net.maddkraft.maddprestige.api.metric.MetricValue);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.metric.MetricValueType type();
  public java.lang.String canonical();
  public int compareTo(java.lang.Object);
}
```

### `net.maddkraft.maddprestige.api.metric.MetricValueType` - STABLE 2.x BASELINE

```text
Compiled from "MetricValueType.java"
public final class net.maddkraft.maddprestige.api.metric.MetricValueType extends java.lang.Enum<net.maddkraft.maddprestige.api.metric.MetricValueType> {
  public static final net.maddkraft.maddprestige.api.metric.MetricValueType INTEGER;
  public static final net.maddkraft.maddprestige.api.metric.MetricValueType EXACT_DECIMAL;
  public static final net.maddkraft.maddprestige.api.metric.MetricValueType DURATION;
  public static final net.maddkraft.maddprestige.api.metric.MetricValueType BOOLEAN;
  public static final net.maddkraft.maddprestige.api.metric.MetricValueType STRING;
  public static final net.maddkraft.maddprestige.api.metric.MetricValueType ENUM;
  public static final net.maddkraft.maddprestige.api.metric.MetricValueType COUNT;
  public static final net.maddkraft.maddprestige.api.metric.MetricValueType CURRENCY_AMOUNT;
  public static net.maddkraft.maddprestige.api.metric.MetricValueType[] values();
  public static net.maddkraft.maddprestige.api.metric.MetricValueType valueOf(java.lang.String);
  public boolean isNumeric();
  public boolean isDiscrete();
}
```

### `net.maddkraft.maddprestige.api.operation.ActionState` - INTERNAL/SHOULD NOT BE PUBLIC

```text
Compiled from "ActionState.java"
public final class net.maddkraft.maddprestige.api.operation.ActionState extends java.lang.Enum<net.maddkraft.maddprestige.api.operation.ActionState> {
  public static final net.maddkraft.maddprestige.api.operation.ActionState PENDING;
  public static final net.maddkraft.maddprestige.api.operation.ActionState STARTED;
  public static final net.maddkraft.maddprestige.api.operation.ActionState SUCCEEDED;
  public static final net.maddkraft.maddprestige.api.operation.ActionState VERIFIED;
  public static final net.maddkraft.maddprestige.api.operation.ActionState FAILED;
  public static final net.maddkraft.maddprestige.api.operation.ActionState COMPENSATED;
  public static final net.maddkraft.maddprestige.api.operation.ActionState UNCERTAIN;
  public static net.maddkraft.maddprestige.api.operation.ActionState[] values();
  public static net.maddkraft.maddprestige.api.operation.ActionState valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.operation.Actor` - INTERNAL/SHOULD NOT BE PUBLIC

```text
Compiled from "Actor.java"
public final class net.maddkraft.maddprestige.api.operation.Actor extends java.lang.Record {
  public net.maddkraft.maddprestige.api.operation.Actor(java.lang.String, java.util.Optional<java.util.UUID>, java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String type();
  public java.util.Optional<java.util.UUID> uuid();
  public java.lang.String displayName();
}
```

### `net.maddkraft.maddprestige.api.operation.OperationActionPlan` - INTERNAL/SHOULD NOT BE PUBLIC

```text
Compiled from "OperationActionPlan.java"
public final class net.maddkraft.maddprestige.api.operation.OperationActionPlan extends java.lang.Record {
  public net.maddkraft.maddprestige.api.operation.OperationActionPlan(java.lang.String, net.maddkraft.maddprestige.api.id.ProviderId, java.lang.String, java.lang.String, boolean, boolean);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String actionId();
  public net.maddkraft.maddprestige.api.id.ProviderId providerId();
  public java.lang.String actionType();
  public java.lang.String redactedDescription();
  public boolean reversible();
  public boolean idempotent();
}
```

### `net.maddkraft.maddprestige.api.operation.OperationPlan` - INTERNAL/SHOULD NOT BE PUBLIC

```text
Compiled from "OperationPlan.java"
public final class net.maddkraft.maddprestige.api.operation.OperationPlan extends java.lang.Record {
  public net.maddkraft.maddprestige.api.operation.OperationPlan(net.maddkraft.maddprestige.api.id.OperationId, java.lang.String, net.maddkraft.maddprestige.api.operation.Actor, java.util.UUID, long, net.maddkraft.maddprestige.api.id.ConfigRevisionId, java.util.Map<net.maddkraft.maddprestige.api.id.ProviderId, java.lang.Long>, java.lang.String, java.util.List<net.maddkraft.maddprestige.api.operation.OperationActionPlan>, java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.id.OperationId id();
  public java.lang.String operationType();
  public net.maddkraft.maddprestige.api.operation.Actor actor();
  public java.util.UUID target();
  public long expectedStateRevision();
  public net.maddkraft.maddprestige.api.id.ConfigRevisionId configRevision();
  public java.util.Map<net.maddkraft.maddprestige.api.id.ProviderId, java.lang.Long> providerGenerations();
  public java.lang.String idempotencyKey();
  public java.util.List<net.maddkraft.maddprestige.api.operation.OperationActionPlan> actions();
  public java.lang.String redactedPreview();
}
```

### `net.maddkraft.maddprestige.api.operation.OperationState` - INTERNAL/SHOULD NOT BE PUBLIC

```text
Compiled from "OperationState.java"
public final class net.maddkraft.maddprestige.api.operation.OperationState extends java.lang.Enum<net.maddkraft.maddprestige.api.operation.OperationState> {
  public static final net.maddkraft.maddprestige.api.operation.OperationState PLANNED;
  public static final net.maddkraft.maddprestige.api.operation.OperationState PREPARED;
  public static final net.maddkraft.maddprestige.api.operation.OperationState EXECUTING;
  public static final net.maddkraft.maddprestige.api.operation.OperationState STATE_COMMITTED;
  public static final net.maddkraft.maddprestige.api.operation.OperationState COMPLETED;
  public static final net.maddkraft.maddprestige.api.operation.OperationState COMPENSATING;
  public static final net.maddkraft.maddprestige.api.operation.OperationState COMPENSATED;
  public static final net.maddkraft.maddprestige.api.operation.OperationState FAILED;
  public static final net.maddkraft.maddprestige.api.operation.OperationState NEEDS_RECONCILIATION;
  public static net.maddkraft.maddprestige.api.operation.OperationState[] values();
  public static net.maddkraft.maddprestige.api.operation.OperationState valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.provider.ActivationState` - INTERNAL/SHOULD NOT BE PUBLIC

```text
Compiled from "ActivationState.java"
public final class net.maddkraft.maddprestige.api.provider.ActivationState extends java.lang.Enum<net.maddkraft.maddprestige.api.provider.ActivationState> {
  public static final net.maddkraft.maddprestige.api.provider.ActivationState INACTIVE;
  public static final net.maddkraft.maddprestige.api.provider.ActivationState ACTIVATING;
  public static final net.maddkraft.maddprestige.api.provider.ActivationState ACTIVE;
  public static final net.maddkraft.maddprestige.api.provider.ActivationState DEACTIVATING;
  public static net.maddkraft.maddprestige.api.provider.ActivationState[] values();
  public static net.maddkraft.maddprestige.api.provider.ActivationState valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.provider.CapabilityDescriptor` - LEGACY/PENDING REMOVAL

```text
Compiled from "CapabilityDescriptor.java"
public final class net.maddkraft.maddprestige.api.provider.CapabilityDescriptor extends java.lang.Record {
  public net.maddkraft.maddprestige.api.provider.CapabilityDescriptor(java.lang.String, java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String id();
  public java.lang.String category();
  public java.lang.String description();
  public java.util.Map<java.lang.String, java.lang.String> attributes();
}
```

### `net.maddkraft.maddprestige.api.provider.DependencyDescriptor` - LEGACY/PENDING REMOVAL

```text
Compiled from "DependencyDescriptor.java"
public final class net.maddkraft.maddprestige.api.provider.DependencyDescriptor extends java.lang.Record {
  public net.maddkraft.maddprestige.api.provider.DependencyDescriptor(java.lang.String, java.lang.String, java.util.Optional<java.lang.String>);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String identity();
  public java.lang.String supportedRange();
  public java.util.Optional<java.lang.String> detectedVersion();
}
```

### `net.maddkraft.maddprestige.api.provider.Provider` - EXPERIMENTAL

```text
Compiled from "Provider.java"
public interface net.maddkraft.maddprestige.api.provider.Provider {
  public abstract net.maddkraft.maddprestige.api.provider.ProviderDescriptor descriptor();
  public abstract net.maddkraft.maddprestige.api.provider.ProviderHealth health();
}
```

### `net.maddkraft.maddprestige.api.provider.ProviderCallContext` - STABLE 2.x BASELINE

```text
Compiled from "ProviderCallContext.java"
public final class net.maddkraft.maddprestige.api.provider.ProviderCallContext extends java.lang.Record {
  public net.maddkraft.maddprestige.api.provider.ProviderCallContext(java.util.UUID, java.time.Instant, java.lang.String, net.maddkraft.maddprestige.api.provider.ProviderExecutionExpectation, java.lang.String, java.util.Optional<net.maddkraft.maddprestige.api.id.ProviderId>, net.maddkraft.maddprestige.api.provider.ProviderCancellationSignal);
  public boolean expired(java.time.Instant);
  public boolean cancellationRequested();
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.util.UUID correlationId();
  public java.time.Instant deadline();
  public java.lang.String operationCode();
  public net.maddkraft.maddprestige.api.provider.ProviderExecutionExpectation execution();
  public java.lang.String ownerNamespace();
  public java.util.Optional<net.maddkraft.maddprestige.api.id.ProviderId> providerId();
  public net.maddkraft.maddprestige.api.provider.ProviderCancellationSignal cancellation();
}
```

### `net.maddkraft.maddprestige.api.provider.ProviderCancellationSignal` - STABLE 2.x BASELINE

```text
Compiled from "ProviderCancellationSignal.java"
public interface net.maddkraft.maddprestige.api.provider.ProviderCancellationSignal {
  public abstract boolean cancellationRequested();
}
```

### `net.maddkraft.maddprestige.api.provider.ProviderDeclaration` - STABLE 2.x BASELINE

```text
Compiled from "ProviderDeclaration.java"
public interface net.maddkraft.maddprestige.api.provider.ProviderDeclaration {
  public abstract net.maddkraft.maddprestige.api.provider.ProviderMetadata metadata(net.maddkraft.maddprestige.api.provider.ProviderCallContext);
  public abstract net.maddkraft.maddprestige.api.provider.RequirementProvider requirements();
  public default void registered(net.maddkraft.maddprestige.api.provider.ProviderRegistrationHandle);
  public default void unregistered();
}
```

### `net.maddkraft.maddprestige.api.provider.ProviderDescriptor` - EXPERIMENTAL

```text
Compiled from "ProviderDescriptor.java"
public final class net.maddkraft.maddprestige.api.provider.ProviderDescriptor extends java.lang.Record {
  public net.maddkraft.maddprestige.api.provider.ProviderDescriptor(net.maddkraft.maddprestige.api.id.ProviderId, java.lang.String, java.lang.String, java.lang.String, java.util.List<net.maddkraft.maddprestige.api.provider.DependencyDescriptor>, java.util.List<net.maddkraft.maddprestige.api.provider.CapabilityDescriptor>);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.id.ProviderId id();
  public java.lang.String ownerIdentity();
  public java.lang.String apiVersion();
  public java.lang.String implementationVersion();
  public java.util.List<net.maddkraft.maddprestige.api.provider.DependencyDescriptor> dependencies();
  public java.util.List<net.maddkraft.maddprestige.api.provider.CapabilityDescriptor> capabilities();
}
```

### `net.maddkraft.maddprestige.api.provider.ProviderExecutionExpectation` - STABLE 2.x BASELINE

```text
Compiled from "ProviderExecutionExpectation.java"
public final class net.maddkraft.maddprestige.api.provider.ProviderExecutionExpectation extends java.lang.Enum<net.maddkraft.maddprestige.api.provider.ProviderExecutionExpectation> {
  public static final net.maddkraft.maddprestige.api.provider.ProviderExecutionExpectation BOUNDED_WORKER;
  public static net.maddkraft.maddprestige.api.provider.ProviderExecutionExpectation[] values();
  public static net.maddkraft.maddprestige.api.provider.ProviderExecutionExpectation valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.provider.ProviderHealth` - INTERNAL/SHOULD NOT BE PUBLIC

```text
Compiled from "ProviderHealth.java"
public final class net.maddkraft.maddprestige.api.provider.ProviderHealth extends java.lang.Record {
  public net.maddkraft.maddprestige.api.provider.ProviderHealth(net.maddkraft.maddprestige.api.provider.ProviderHealthState, java.lang.String, java.lang.String, java.time.Instant);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.provider.ProviderHealthState state();
  public java.lang.String code();
  public java.lang.String reason();
  public java.time.Instant changedAt();
}
```

### `net.maddkraft.maddprestige.api.provider.ProviderHealthState` - STABLE 2.x BASELINE

```text
Compiled from "ProviderHealthState.java"
public final class net.maddkraft.maddprestige.api.provider.ProviderHealthState extends java.lang.Enum<net.maddkraft.maddprestige.api.provider.ProviderHealthState> {
  public static final net.maddkraft.maddprestige.api.provider.ProviderHealthState NOT_INSTALLED;
  public static final net.maddkraft.maddprestige.api.provider.ProviderHealthState AVAILABLE;
  public static final net.maddkraft.maddprestige.api.provider.ProviderHealthState ACTIVE;
  public static final net.maddkraft.maddprestige.api.provider.ProviderHealthState INACTIVE;
  public static final net.maddkraft.maddprestige.api.provider.ProviderHealthState DEGRADED;
  public static final net.maddkraft.maddprestige.api.provider.ProviderHealthState UNSUPPORTED;
  public static final net.maddkraft.maddprestige.api.provider.ProviderHealthState UNAVAILABLE;
  public static final net.maddkraft.maddprestige.api.provider.ProviderHealthState UNHEALTHY;
  public static net.maddkraft.maddprestige.api.provider.ProviderHealthState[] values();
  public static net.maddkraft.maddprestige.api.provider.ProviderHealthState valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.provider.ProviderLifecycle` - INTERNAL/SHOULD NOT BE PUBLIC

```text
Compiled from "ProviderLifecycle.java"
public final class net.maddkraft.maddprestige.api.provider.ProviderLifecycle extends java.lang.Enum<net.maddkraft.maddprestige.api.provider.ProviderLifecycle> {
  public static final net.maddkraft.maddprestige.api.provider.ProviderLifecycle REGISTERED;
  public static final net.maddkraft.maddprestige.api.provider.ProviderLifecycle STARTING;
  public static final net.maddkraft.maddprestige.api.provider.ProviderLifecycle RUNNING;
  public static final net.maddkraft.maddprestige.api.provider.ProviderLifecycle STOPPING;
  public static final net.maddkraft.maddprestige.api.provider.ProviderLifecycle STOPPED;
  public static net.maddkraft.maddprestige.api.provider.ProviderLifecycle[] values();
  public static net.maddkraft.maddprestige.api.provider.ProviderLifecycle valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.provider.ProviderMetadata` - STABLE 2.x BASELINE

```text
Compiled from "ProviderMetadata.java"
public final class net.maddkraft.maddprestige.api.provider.ProviderMetadata extends java.lang.Record {
  public net.maddkraft.maddprestige.api.provider.ProviderMetadata(java.lang.String, java.lang.String, java.lang.String, java.util.List<net.maddkraft.maddprestige.api.provider.ProviderMetricDefinition>);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String localId();
  public java.lang.String displayNameKey();
  public java.lang.String implementationVersion();
  public java.util.List<net.maddkraft.maddprestige.api.provider.ProviderMetricDefinition> metrics();
}
```

### `net.maddkraft.maddprestige.api.provider.ProviderMetricDefinition` - STABLE 2.x BASELINE

```text
Compiled from "ProviderMetricDefinition.java"
public final class net.maddkraft.maddprestige.api.provider.ProviderMetricDefinition extends java.lang.Record {
  public net.maddkraft.maddprestige.api.provider.ProviderMetricDefinition(net.maddkraft.maddprestige.api.id.MetricId, net.maddkraft.maddprestige.api.metric.MetricValueType, java.util.Set<net.maddkraft.maddprestige.api.metric.MetricOperator>, java.util.Set<net.maddkraft.maddprestige.api.metric.MetricReadMode>, boolean, net.maddkraft.maddprestige.api.metric.MetricMonotonicity, net.maddkraft.maddprestige.api.metric.MetricResetPolicy, java.util.Map<java.lang.String, net.maddkraft.maddprestige.api.provider.ProviderMetricDimension>, java.lang.String, java.lang.String, java.lang.String, java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.id.MetricId metricId();
  public net.maddkraft.maddprestige.api.metric.MetricValueType valueType();
  public java.util.Set<net.maddkraft.maddprestige.api.metric.MetricOperator> supportedOperators();
  public java.util.Set<net.maddkraft.maddprestige.api.metric.MetricReadMode> supportedReads();
  public boolean snapshotDeltaSupported();
  public net.maddkraft.maddprestige.api.metric.MetricMonotonicity monotonicity();
  public net.maddkraft.maddprestige.api.metric.MetricResetPolicy resetPolicy();
  public java.util.Map<java.lang.String, net.maddkraft.maddprestige.api.provider.ProviderMetricDimension> dimensions();
  public java.lang.String displayNameKey();
  public java.lang.String descriptionKey();
  public java.lang.String unitCode();
  public java.lang.String reliabilityCode();
}
```

### `net.maddkraft.maddprestige.api.provider.ProviderMetricDimension` - STABLE 2.x BASELINE

```text
Compiled from "ProviderMetricDimension.java"
public final class net.maddkraft.maddprestige.api.provider.ProviderMetricDimension extends java.lang.Record {
  public net.maddkraft.maddprestige.api.provider.ProviderMetricDimension(java.lang.String, boolean, java.util.Set<java.lang.String>, java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String id();
  public boolean required();
  public java.util.Set<java.lang.String> allowedValues();
  public java.lang.String descriptionKey();
}
```

### `net.maddkraft.maddprestige.api.provider.ProviderMetricRequest` - STABLE 2.x BASELINE

```text
Compiled from "ProviderMetricRequest.java"
public final class net.maddkraft.maddprestige.api.provider.ProviderMetricRequest extends java.lang.Record {
  public net.maddkraft.maddprestige.api.provider.ProviderMetricRequest(net.maddkraft.maddprestige.api.id.MetricId, net.maddkraft.maddprestige.api.metric.MetricReadMode, java.util.Map<java.lang.String, java.lang.String>);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.id.MetricId metricId();
  public net.maddkraft.maddprestige.api.metric.MetricReadMode readMode();
  public java.util.Map<java.lang.String, java.lang.String> filters();
}
```

### `net.maddkraft.maddprestige.api.provider.ProviderMetricResult` - STABLE 2.x BASELINE

```text
Compiled from "ProviderMetricResult.java"
public final class net.maddkraft.maddprestige.api.provider.ProviderMetricResult extends java.lang.Record {
  public net.maddkraft.maddprestige.api.provider.ProviderMetricResult(net.maddkraft.maddprestige.api.provider.ProviderMetricStatus, java.util.Optional<net.maddkraft.maddprestige.api.metric.MetricValue>, java.time.Instant, java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
  public static net.maddkraft.maddprestige.api.provider.ProviderMetricResult available(net.maddkraft.maddprestige.api.metric.MetricValue, java.time.Instant);
  public static net.maddkraft.maddprestige.api.provider.ProviderMetricResult unavailable(java.time.Instant, java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.provider.ProviderMetricStatus status();
  public java.util.Optional<net.maddkraft.maddprestige.api.metric.MetricValue> value();
  public java.time.Instant observedAt();
  public java.lang.String code();
  public java.lang.String messageKey();
  public java.util.Map<java.lang.String, java.lang.String> arguments();
}
```

### `net.maddkraft.maddprestige.api.provider.ProviderMetricStatus` - STABLE 2.x BASELINE

```text
Compiled from "ProviderMetricStatus.java"
public final class net.maddkraft.maddprestige.api.provider.ProviderMetricStatus extends java.lang.Enum<net.maddkraft.maddprestige.api.provider.ProviderMetricStatus> {
  public static final net.maddkraft.maddprestige.api.provider.ProviderMetricStatus AVAILABLE;
  public static final net.maddkraft.maddprestige.api.provider.ProviderMetricStatus UNAVAILABLE;
  public static net.maddkraft.maddprestige.api.provider.ProviderMetricStatus[] values();
  public static net.maddkraft.maddprestige.api.provider.ProviderMetricStatus valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.provider.ProviderRegistrationHandle` - STABLE 2.x BASELINE

```text
Compiled from "ProviderRegistrationHandle.java"
public interface net.maddkraft.maddprestige.api.provider.ProviderRegistrationHandle {
  public abstract net.maddkraft.maddprestige.api.id.ProviderId providerId();
  public abstract java.util.concurrent.CompletionStage<java.lang.Void> unregister();
}
```

### `net.maddkraft.maddprestige.api.provider.ProviderSnapshot` - EXPERIMENTAL

```text
Compiled from "ProviderSnapshot.java"
public final class net.maddkraft.maddprestige.api.provider.ProviderSnapshot extends java.lang.Record {
  public net.maddkraft.maddprestige.api.provider.ProviderSnapshot(net.maddkraft.maddprestige.api.provider.ProviderDescriptor, net.maddkraft.maddprestige.api.provider.ProviderLifecycle, net.maddkraft.maddprestige.api.provider.ActivationState, net.maddkraft.maddprestige.api.provider.ProviderHealth, long);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.provider.ProviderDescriptor descriptor();
  public net.maddkraft.maddprestige.api.provider.ProviderLifecycle lifecycle();
  public net.maddkraft.maddprestige.api.provider.ActivationState activation();
  public net.maddkraft.maddprestige.api.provider.ProviderHealth health();
  public long generation();
}
```

### `net.maddkraft.maddprestige.api.provider.RequirementProvider` - STABLE 2.x BASELINE

```text
Compiled from "RequirementProvider.java"
public interface net.maddkraft.maddprestige.api.provider.RequirementProvider {
  public abstract java.util.concurrent.CompletionStage<java.util.Map<net.maddkraft.maddprestige.api.provider.ProviderMetricRequest, net.maddkraft.maddprestige.api.provider.ProviderMetricResult>> read(net.maddkraft.maddprestige.api.provider.ProviderCallContext, java.util.UUID, java.util.List<net.maddkraft.maddprestige.api.provider.ProviderMetricRequest>);
}
```

### `net.maddkraft.maddprestige.api.rank.AmbiguousRankMembership` - LEGACY/PENDING REMOVAL

```text
Compiled from "AmbiguousRankMembership.java"
public final class net.maddkraft.maddprestige.api.rank.AmbiguousRankMembership extends java.lang.Record {
  public net.maddkraft.maddprestige.api.rank.AmbiguousRankMembership(java.lang.String, java.util.Map<java.lang.String, java.util.Set<java.lang.String>>, java.util.Optional<java.time.Instant>);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String groupName();
  public java.util.Map<java.lang.String, java.util.Set<java.lang.String>> contexts();
  public java.util.Optional<java.time.Instant> expiresAt();
}
```

### `net.maddkraft.maddprestige.api.rank.ManagedRankState` - LEGACY/PENDING REMOVAL

```text
Compiled from "ManagedRankState.java"
public final class net.maddkraft.maddprestige.api.rank.ManagedRankState extends java.lang.Record {
  public net.maddkraft.maddprestige.api.rank.ManagedRankState(java.util.UUID, java.util.Set<java.lang.String>, java.util.List<net.maddkraft.maddprestige.api.rank.AmbiguousRankMembership>);
  public boolean hasAmbiguity();
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.util.UUID playerId();
  public java.util.Set<java.lang.String> permanentContextFreeGroups();
  public java.util.List<net.maddkraft.maddprestige.api.rank.AmbiguousRankMembership> ambiguousMemberships();
}
```

### `net.maddkraft.maddprestige.api.rank.RankAdapter` - EXPERIMENTAL

```text
Compiled from "RankAdapter.java"
public interface net.maddkraft.maddprestige.api.rank.RankAdapter extends net.maddkraft.maddprestige.api.provider.Provider {
  public abstract java.util.concurrent.CompletionStage<net.maddkraft.maddprestige.api.result.Result<java.util.Set<java.lang.String>>> validateTargets(java.util.Set<java.lang.String>);
  public abstract java.util.concurrent.CompletionStage<net.maddkraft.maddprestige.api.result.Result<net.maddkraft.maddprestige.api.rank.ManagedRankState>> readManagedState(java.util.UUID, java.util.Set<java.lang.String>);
  public abstract java.util.concurrent.CompletionStage<net.maddkraft.maddprestige.api.result.Result<net.maddkraft.maddprestige.api.rank.RankProjectionResult>> project(net.maddkraft.maddprestige.api.rank.RankProjectionRequest);
}
```

### `net.maddkraft.maddprestige.api.rank.RankProjectionOutcome` - LEGACY/PENDING REMOVAL

```text
Compiled from "RankProjectionOutcome.java"
public final class net.maddkraft.maddprestige.api.rank.RankProjectionOutcome extends java.lang.Enum<net.maddkraft.maddprestige.api.rank.RankProjectionOutcome> {
  public static final net.maddkraft.maddprestige.api.rank.RankProjectionOutcome UNCHANGED;
  public static final net.maddkraft.maddprestige.api.rank.RankProjectionOutcome APPLIED;
  public static net.maddkraft.maddprestige.api.rank.RankProjectionOutcome[] values();
  public static net.maddkraft.maddprestige.api.rank.RankProjectionOutcome valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.rank.RankProjectionRequest` - LEGACY/PENDING REMOVAL

```text
Compiled from "RankProjectionRequest.java"
public final class net.maddkraft.maddprestige.api.rank.RankProjectionRequest extends java.lang.Record {
  public net.maddkraft.maddprestige.api.rank.RankProjectionRequest(java.util.UUID, net.maddkraft.maddprestige.api.id.OperationId, net.maddkraft.maddprestige.api.id.ConfigRevisionId, long, java.util.Set<java.lang.String>, java.util.Optional<java.lang.String>);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.util.UUID playerId();
  public net.maddkraft.maddprestige.api.id.OperationId operationId();
  public net.maddkraft.maddprestige.api.id.ConfigRevisionId configRevision();
  public long providerGeneration();
  public java.util.Set<java.lang.String> managedGroups();
  public java.util.Optional<java.lang.String> desiredGroup();
}
```

### `net.maddkraft.maddprestige.api.rank.RankProjectionResult` - LEGACY/PENDING REMOVAL

```text
Compiled from "RankProjectionResult.java"
public final class net.maddkraft.maddprestige.api.rank.RankProjectionResult extends java.lang.Record {
  public net.maddkraft.maddprestige.api.rank.RankProjectionResult(net.maddkraft.maddprestige.api.rank.ManagedRankState, net.maddkraft.maddprestige.api.rank.ManagedRankState, net.maddkraft.maddprestige.api.rank.RankProjectionOutcome);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.rank.ManagedRankState before();
  public net.maddkraft.maddprestige.api.rank.ManagedRankState after();
  public net.maddkraft.maddprestige.api.rank.RankProjectionOutcome outcome();
}
```

### `net.maddkraft.maddprestige.api.result.ErrorCategory` - LEGACY/PENDING REMOVAL

```text
Compiled from "ErrorCategory.java"
public final class net.maddkraft.maddprestige.api.result.ErrorCategory extends java.lang.Enum<net.maddkraft.maddprestige.api.result.ErrorCategory> {
  public static final net.maddkraft.maddprestige.api.result.ErrorCategory INVALID;
  public static final net.maddkraft.maddprestige.api.result.ErrorCategory UNAVAILABLE;
  public static final net.maddkraft.maddprestige.api.result.ErrorCategory DENIED;
  public static final net.maddkraft.maddprestige.api.result.ErrorCategory CONFLICT;
  public static final net.maddkraft.maddprestige.api.result.ErrorCategory FAILED;
  public static final net.maddkraft.maddprestige.api.result.ErrorCategory UNCERTAIN;
  public static net.maddkraft.maddprestige.api.result.ErrorCategory[] values();
  public static net.maddkraft.maddprestige.api.result.ErrorCategory valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.result.Result` - LEGACY/PENDING REMOVAL

```text
Compiled from "Result.java"
public final class net.maddkraft.maddprestige.api.result.Result<T> {
  public static <T> net.maddkraft.maddprestige.api.result.Result<T> success(T);
  public static <T> net.maddkraft.maddprestige.api.result.Result<T> failure(net.maddkraft.maddprestige.api.result.StructuredError);
  public boolean isSuccess();
  public java.util.Optional<T> value();
  public java.util.List<net.maddkraft.maddprestige.api.result.StructuredError> errors();
}
```

### `net.maddkraft.maddprestige.api.result.StructuredError` - LEGACY/PENDING REMOVAL

```text
Compiled from "StructuredError.java"
public final class net.maddkraft.maddprestige.api.result.StructuredError extends java.lang.Record {
  public net.maddkraft.maddprestige.api.result.StructuredError(java.lang.String, net.maddkraft.maddprestige.api.result.ErrorCategory, java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
  public static net.maddkraft.maddprestige.api.result.StructuredError unavailable(java.lang.String, java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String code();
  public net.maddkraft.maddprestige.api.result.ErrorCategory category();
  public java.lang.String message();
  public java.util.Map<java.lang.String, java.lang.String> details();
}
```

### `net.maddkraft.maddprestige.api.reward.NativeRecoverableRewardProvider` - INTERNAL/SHOULD NOT BE PUBLIC

```text
Compiled from "NativeRecoverableRewardProvider.java"
public interface net.maddkraft.maddprestige.api.reward.NativeRecoverableRewardProvider extends net.maddkraft.maddprestige.api.reward.RewardProvider {
}
```

### `net.maddkraft.maddprestige.api.reward.PlannedReward` - INTERNAL/SHOULD NOT BE PUBLIC

```text
Compiled from "PlannedReward.java"
public final class net.maddkraft.maddprestige.api.reward.PlannedReward extends java.lang.Record {
  public net.maddkraft.maddprestige.api.reward.PlannedReward(net.maddkraft.maddprestige.api.id.OperationId, java.lang.String, java.util.UUID, net.maddkraft.maddprestige.api.reward.RewardDefinition, net.maddkraft.maddprestige.api.id.ConfigRevisionId, long, net.maddkraft.maddprestige.api.action.ActionCharacteristics, java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.id.OperationId operationId();
  public java.lang.String actionId();
  public java.util.UUID playerId();
  public net.maddkraft.maddprestige.api.reward.RewardDefinition definition();
  public net.maddkraft.maddprestige.api.id.ConfigRevisionId configRevision();
  public long providerGeneration();
  public net.maddkraft.maddprestige.api.action.ActionCharacteristics characteristics();
  public java.lang.String redactedPreview();
}
```

### `net.maddkraft.maddprestige.api.reward.RewardDefinition` - LEGACY/PENDING REMOVAL

```text
Compiled from "RewardDefinition.java"
public final class net.maddkraft.maddprestige.api.reward.RewardDefinition extends java.lang.Record {
  public net.maddkraft.maddprestige.api.reward.RewardDefinition(net.maddkraft.maddprestige.api.id.RewardId, net.maddkraft.maddprestige.api.id.ProviderId, java.lang.String, net.maddkraft.maddprestige.api.metric.MetricValue, java.util.Map<java.lang.String, java.lang.String>, java.lang.String, net.maddkraft.maddprestige.api.reward.RewardFailurePolicy, net.maddkraft.maddprestige.api.reward.RewardRepeatability);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.id.RewardId id();
  public net.maddkraft.maddprestige.api.id.ProviderId providerId();
  public java.lang.String type();
  public net.maddkraft.maddprestige.api.metric.MetricValue value();
  public java.util.Map<java.lang.String, java.lang.String> metadata();
  public java.lang.String displayName();
  public net.maddkraft.maddprestige.api.reward.RewardFailurePolicy failurePolicy();
  public net.maddkraft.maddprestige.api.reward.RewardRepeatability repeatability();
}
```

### `net.maddkraft.maddprestige.api.reward.RewardFailurePolicy` - LEGACY/PENDING REMOVAL

```text
Compiled from "RewardFailurePolicy.java"
public final class net.maddkraft.maddprestige.api.reward.RewardFailurePolicy extends java.lang.Enum<net.maddkraft.maddprestige.api.reward.RewardFailurePolicy> {
  public static final net.maddkraft.maddprestige.api.reward.RewardFailurePolicy REQUIRED;
  public static final net.maddkraft.maddprestige.api.reward.RewardFailurePolicy OPTIONAL;
  public static net.maddkraft.maddprestige.api.reward.RewardFailurePolicy[] values();
  public static net.maddkraft.maddprestige.api.reward.RewardFailurePolicy valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.reward.RewardPreflight` - LEGACY/PENDING REMOVAL

```text
Compiled from "RewardPreflight.java"
public final class net.maddkraft.maddprestige.api.reward.RewardPreflight extends java.lang.Record {
  public net.maddkraft.maddprestige.api.reward.RewardPreflight(net.maddkraft.maddprestige.api.action.PreflightStatus, java.util.Optional<net.maddkraft.maddprestige.api.reward.PlannedReward>, java.lang.String);
  public static net.maddkraft.maddprestige.api.reward.RewardPreflight ready(net.maddkraft.maddprestige.api.reward.PlannedReward);
  public static net.maddkraft.maddprestige.api.reward.RewardPreflight unavailable(java.lang.String);
  public static net.maddkraft.maddprestige.api.reward.RewardPreflight invalid(java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.action.PreflightStatus status();
  public java.util.Optional<net.maddkraft.maddprestige.api.reward.PlannedReward> plannedReward();
  public java.lang.String detail();
}
```

### `net.maddkraft.maddprestige.api.reward.RewardProvider` - LEGACY/PENDING REMOVAL

```text
Compiled from "RewardProvider.java"
public interface net.maddkraft.maddprestige.api.reward.RewardProvider extends net.maddkraft.maddprestige.api.provider.Provider {
  public abstract net.maddkraft.maddprestige.api.action.ActionCharacteristics characteristics(net.maddkraft.maddprestige.api.reward.RewardDefinition);
  public abstract net.maddkraft.maddprestige.api.validation.ValidationReport validate(net.maddkraft.maddprestige.api.reward.RewardDefinition);
  public abstract java.util.concurrent.CompletionStage<net.maddkraft.maddprestige.api.reward.RewardPreflight> preflight(net.maddkraft.maddprestige.api.reward.PlannedReward);
  public default java.util.concurrent.CompletionStage<java.util.List<net.maddkraft.maddprestige.api.reward.RewardPreflight>> preflightBatch(java.util.List<net.maddkraft.maddprestige.api.reward.PlannedReward>);
  public abstract java.util.concurrent.CompletionStage<net.maddkraft.maddprestige.api.action.ActionExecutionResult> execute(net.maddkraft.maddprestige.api.reward.PlannedReward);
}
```

### `net.maddkraft.maddprestige.api.reward.RewardRepeatability` - LEGACY/PENDING REMOVAL

```text
Compiled from "RewardRepeatability.java"
public final class net.maddkraft.maddprestige.api.reward.RewardRepeatability extends java.lang.Enum<net.maddkraft.maddprestige.api.reward.RewardRepeatability> {
  public static final net.maddkraft.maddprestige.api.reward.RewardRepeatability ONCE_PER_OPERATION;
  public static final net.maddkraft.maddprestige.api.reward.RewardRepeatability REPEATABLE;
  public static net.maddkraft.maddprestige.api.reward.RewardRepeatability[] values();
  public static net.maddkraft.maddprestige.api.reward.RewardRepeatability valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.service.CurrencyBalanceView` - STABLE 2.x BASELINE

```text
Compiled from "CurrencyBalanceView.java"
public final class net.maddkraft.maddprestige.api.service.CurrencyBalanceView extends java.lang.Record {
  public net.maddkraft.maddprestige.api.service.CurrencyBalanceView(java.lang.String, net.maddkraft.maddprestige.api.metric.MetricValue, int, boolean);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String currencyId();
  public net.maddkraft.maddprestige.api.metric.MetricValue balance();
  public int scale();
  public boolean prestigeScoped();
}
```

### `net.maddkraft.maddprestige.api.service.MaddPrestigeService` - STABLE 2.x BASELINE

```text
Compiled from "MaddPrestigeService.java"
public interface net.maddkraft.maddprestige.api.service.MaddPrestigeService {
  public abstract java.util.concurrent.CompletionStage<net.maddkraft.maddprestige.api.service.ServiceResult<net.maddkraft.maddprestige.api.service.PlayerProgressSnapshot>> playerProgress(java.util.UUID);
  public abstract net.maddkraft.maddprestige.api.service.ServiceResult<java.util.List<net.maddkraft.maddprestige.api.service.StageView>> stages();
  public abstract java.util.concurrent.CompletionStage<net.maddkraft.maddprestige.api.service.ServiceResult<net.maddkraft.maddprestige.api.service.OperationEvaluation>> evaluateRankUp(java.util.UUID);
  public abstract java.util.concurrent.CompletionStage<net.maddkraft.maddprestige.api.service.ServiceResult<net.maddkraft.maddprestige.api.service.OperationEvaluation>> evaluatePrestige(java.util.UUID);
  public abstract java.util.concurrent.CompletionStage<net.maddkraft.maddprestige.api.service.ServiceResult<java.util.List<net.maddkraft.maddprestige.api.service.RequirementProgressView>>> requirementProgress(java.util.UUID);
  public abstract java.util.concurrent.CompletionStage<net.maddkraft.maddprestige.api.service.ServiceResult<java.util.List<net.maddkraft.maddprestige.api.service.CurrencyBalanceView>>> currencies(java.util.UUID);
  public abstract java.util.concurrent.CompletionStage<net.maddkraft.maddprestige.api.service.ServiceResult<java.util.Optional<net.maddkraft.maddprestige.api.service.SeasonView>>> activeSeason(java.util.UUID);
  public abstract java.util.concurrent.CompletionStage<net.maddkraft.maddprestige.api.service.ServiceResult<net.maddkraft.maddprestige.api.service.OperationResult>> rankUp(java.util.UUID);
  public abstract java.util.concurrent.CompletionStage<net.maddkraft.maddprestige.api.service.ServiceResult<net.maddkraft.maddprestige.api.service.OperationResult>> prestige(java.util.UUID);
  public abstract net.maddkraft.maddprestige.api.service.ServiceResult<java.util.List<net.maddkraft.maddprestige.api.service.ProviderView>> providers();
}
```

### `net.maddkraft.maddprestige.api.service.OperationEvaluation` - STABLE 2.x BASELINE

```text
Compiled from "OperationEvaluation.java"
public final class net.maddkraft.maddprestige.api.service.OperationEvaluation extends java.lang.Record {
  public net.maddkraft.maddprestige.api.service.OperationEvaluation(net.maddkraft.maddprestige.api.service.OperationKind, net.maddkraft.maddprestige.api.service.OperationEvaluationStatus, java.util.Optional<net.maddkraft.maddprestige.api.id.StageId>, java.util.Optional<net.maddkraft.maddprestige.api.id.StageId>, java.util.Optional<net.maddkraft.maddprestige.api.id.ConfigRevisionId>, java.util.List<net.maddkraft.maddprestige.api.service.ServiceError>, java.util.List<net.maddkraft.maddprestige.api.service.RequirementProgressView>, java.util.Optional<net.maddkraft.maddprestige.api.service.OperationSimulationView>, java.time.Instant);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.service.OperationKind kind();
  public net.maddkraft.maddprestige.api.service.OperationEvaluationStatus status();
  public java.util.Optional<net.maddkraft.maddprestige.api.id.StageId> sourceStage();
  public java.util.Optional<net.maddkraft.maddprestige.api.id.StageId> targetStage();
  public java.util.Optional<net.maddkraft.maddprestige.api.id.ConfigRevisionId> configRevision();
  public java.util.List<net.maddkraft.maddprestige.api.service.ServiceError> blockers();
  public java.util.List<net.maddkraft.maddprestige.api.service.RequirementProgressView> requirements();
  public java.util.Optional<net.maddkraft.maddprestige.api.service.OperationSimulationView> simulation();
  public java.time.Instant observedAt();
}
```

### `net.maddkraft.maddprestige.api.service.OperationEvaluationStatus` - STABLE 2.x BASELINE

```text
Compiled from "OperationEvaluationStatus.java"
public final class net.maddkraft.maddprestige.api.service.OperationEvaluationStatus extends java.lang.Enum<net.maddkraft.maddprestige.api.service.OperationEvaluationStatus> {
  public static final net.maddkraft.maddprestige.api.service.OperationEvaluationStatus ELIGIBLE;
  public static final net.maddkraft.maddprestige.api.service.OperationEvaluationStatus BLOCKED;
  public static final net.maddkraft.maddprestige.api.service.OperationEvaluationStatus UNAVAILABLE;
  public static net.maddkraft.maddprestige.api.service.OperationEvaluationStatus[] values();
  public static net.maddkraft.maddprestige.api.service.OperationEvaluationStatus valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.service.OperationKind` - STABLE 2.x BASELINE

```text
Compiled from "OperationKind.java"
public final class net.maddkraft.maddprestige.api.service.OperationKind extends java.lang.Enum<net.maddkraft.maddprestige.api.service.OperationKind> {
  public static final net.maddkraft.maddprestige.api.service.OperationKind RANK_UP;
  public static final net.maddkraft.maddprestige.api.service.OperationKind PRESTIGE;
  public static net.maddkraft.maddprestige.api.service.OperationKind[] values();
  public static net.maddkraft.maddprestige.api.service.OperationKind valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.service.OperationResult` - STABLE 2.x BASELINE

```text
Compiled from "OperationResult.java"
public final class net.maddkraft.maddprestige.api.service.OperationResult extends java.lang.Record {
  public net.maddkraft.maddprestige.api.service.OperationResult(java.util.UUID, java.util.Optional<net.maddkraft.maddprestige.api.id.OperationId>, net.maddkraft.maddprestige.api.service.OperationKind, net.maddkraft.maddprestige.api.service.OperationStatus, java.util.Optional<net.maddkraft.maddprestige.api.service.ServiceError>);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.util.UUID requestId();
  public java.util.Optional<net.maddkraft.maddprestige.api.id.OperationId> durableOperationId();
  public net.maddkraft.maddprestige.api.service.OperationKind kind();
  public net.maddkraft.maddprestige.api.service.OperationStatus status();
  public java.util.Optional<net.maddkraft.maddprestige.api.service.ServiceError> error();
}
```

### `net.maddkraft.maddprestige.api.service.OperationSimulationView` - STABLE 2.x BASELINE

```text
Compiled from "OperationSimulationView.java"
public final class net.maddkraft.maddprestige.api.service.OperationSimulationView extends java.lang.Record {
  public net.maddkraft.maddprestige.api.service.OperationSimulationView(java.util.List<java.lang.String>, java.util.List<java.lang.String>, java.util.Optional<net.maddkraft.maddprestige.api.id.ProviderId>);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.util.List<java.lang.String> costIds();
  public java.util.List<java.lang.String> rewardIds();
  public java.util.Optional<net.maddkraft.maddprestige.api.id.ProviderId> rankProjectionProvider();
}
```

### `net.maddkraft.maddprestige.api.service.OperationStatus` - STABLE 2.x BASELINE

```text
Compiled from "OperationStatus.java"
public final class net.maddkraft.maddprestige.api.service.OperationStatus extends java.lang.Enum<net.maddkraft.maddprestige.api.service.OperationStatus> {
  public static final net.maddkraft.maddprestige.api.service.OperationStatus COMPLETED;
  public static final net.maddkraft.maddprestige.api.service.OperationStatus BLOCKED;
  public static final net.maddkraft.maddprestige.api.service.OperationStatus CONFLICT;
  public static final net.maddkraft.maddprestige.api.service.OperationStatus FAILED;
  public static final net.maddkraft.maddprestige.api.service.OperationStatus NEEDS_RECONCILIATION;
  public static net.maddkraft.maddprestige.api.service.OperationStatus[] values();
  public static net.maddkraft.maddprestige.api.service.OperationStatus valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.service.PlayerProgressSnapshot` - STABLE 2.x BASELINE

```text
Compiled from "PlayerProgressSnapshot.java"
public final class net.maddkraft.maddprestige.api.service.PlayerProgressSnapshot extends java.lang.Record {
  public net.maddkraft.maddprestige.api.service.PlayerProgressSnapshot(java.util.UUID, java.util.Optional<net.maddkraft.maddprestige.api.id.StageId>, long, long, java.util.Optional<net.maddkraft.maddprestige.api.id.ConfigRevisionId>, java.util.Map<java.lang.String, java.lang.String>, java.time.Instant);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.util.UUID playerId();
  public java.util.Optional<net.maddkraft.maddprestige.api.id.StageId> stage();
  public long currentPrestige();
  public long lifetimePrestige();
  public java.util.Optional<net.maddkraft.maddprestige.api.id.ConfigRevisionId> configRevision();
  public java.util.Map<java.lang.String, java.lang.String> attributes();
  public java.time.Instant observedAt();
}
```

### `net.maddkraft.maddprestige.api.service.ProviderView` - STABLE 2.x BASELINE

```text
Compiled from "ProviderView.java"
public final class net.maddkraft.maddprestige.api.service.ProviderView extends java.lang.Record {
  public net.maddkraft.maddprestige.api.service.ProviderView(net.maddkraft.maddprestige.api.id.ProviderId, net.maddkraft.maddprestige.api.provider.ProviderHealthState, java.lang.String, java.time.Instant);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.id.ProviderId id();
  public net.maddkraft.maddprestige.api.provider.ProviderHealthState health();
  public java.lang.String healthCode();
  public java.time.Instant observedAt();
}
```

### `net.maddkraft.maddprestige.api.service.RequirementProgressStatus` - STABLE 2.x BASELINE

```text
Compiled from "RequirementProgressStatus.java"
public final class net.maddkraft.maddprestige.api.service.RequirementProgressStatus extends java.lang.Enum<net.maddkraft.maddprestige.api.service.RequirementProgressStatus> {
  public static final net.maddkraft.maddprestige.api.service.RequirementProgressStatus SATISFIED;
  public static final net.maddkraft.maddprestige.api.service.RequirementProgressStatus UNSATISFIED;
  public static final net.maddkraft.maddprestige.api.service.RequirementProgressStatus UNAVAILABLE;
  public static final net.maddkraft.maddprestige.api.service.RequirementProgressStatus INVALID;
  public static final net.maddkraft.maddprestige.api.service.RequirementProgressStatus ERROR;
  public static net.maddkraft.maddprestige.api.service.RequirementProgressStatus[] values();
  public static net.maddkraft.maddprestige.api.service.RequirementProgressStatus valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.service.RequirementProgressView` - STABLE 2.x BASELINE

```text
Compiled from "RequirementProgressView.java"
public final class net.maddkraft.maddprestige.api.service.RequirementProgressView extends java.lang.Record {
  public net.maddkraft.maddprestige.api.service.RequirementProgressView(net.maddkraft.maddprestige.api.service.OperationKind, java.lang.String, net.maddkraft.maddprestige.api.service.RequirementProgressStatus, java.util.Optional<net.maddkraft.maddprestige.api.metric.MetricValue>, java.util.Optional<net.maddkraft.maddprestige.api.metric.MetricValue>);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.service.OperationKind operation();
  public java.lang.String requirementId();
  public net.maddkraft.maddprestige.api.service.RequirementProgressStatus status();
  public java.util.Optional<net.maddkraft.maddprestige.api.metric.MetricValue> current();
  public java.util.Optional<net.maddkraft.maddprestige.api.metric.MetricValue> target();
}
```

### `net.maddkraft.maddprestige.api.service.SeasonView` - STABLE 2.x BASELINE

```text
Compiled from "SeasonView.java"
public final class net.maddkraft.maddprestige.api.service.SeasonView extends java.lang.Record {
  public net.maddkraft.maddprestige.api.service.SeasonView(java.lang.String, net.maddkraft.maddprestige.api.id.ConfigRevisionId, net.maddkraft.maddprestige.api.metric.MetricValue, java.time.Instant);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String seasonId();
  public net.maddkraft.maddprestige.api.id.ConfigRevisionId configRevision();
  public net.maddkraft.maddprestige.api.metric.MetricValue playerProgress();
  public java.time.Instant startedAt();
}
```

### `net.maddkraft.maddprestige.api.service.ServiceError` - STABLE 2.x BASELINE

```text
Compiled from "ServiceError.java"
public final class net.maddkraft.maddprestige.api.service.ServiceError extends java.lang.Record {
  public net.maddkraft.maddprestige.api.service.ServiceError(java.lang.String, java.lang.String, java.util.Map<java.lang.String, java.lang.String>);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String code();
  public java.lang.String messageKey();
  public java.util.Map<java.lang.String, java.lang.String> arguments();
}
```

### `net.maddkraft.maddprestige.api.service.ServiceResult` - STABLE 2.x BASELINE

```text
Compiled from "ServiceResult.java"
public final class net.maddkraft.maddprestige.api.service.ServiceResult<T> extends java.lang.Record {
  public net.maddkraft.maddprestige.api.service.ServiceResult(java.util.Optional<T>, java.util.Optional<net.maddkraft.maddprestige.api.service.ServiceError>);
  public static <T> net.maddkraft.maddprestige.api.service.ServiceResult<T> success(T);
  public static <T> net.maddkraft.maddprestige.api.service.ServiceResult<T> failure(net.maddkraft.maddprestige.api.service.ServiceError);
  public boolean successful();
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.util.Optional<T> value();
  public java.util.Optional<net.maddkraft.maddprestige.api.service.ServiceError> error();
}
```

### `net.maddkraft.maddprestige.api.service.StageView` - STABLE 2.x BASELINE

```text
Compiled from "StageView.java"
public final class net.maddkraft.maddprestige.api.service.StageView extends java.lang.Record {
  public net.maddkraft.maddprestige.api.service.StageView(net.maddkraft.maddprestige.api.id.StageId, boolean, int, java.util.Optional<java.lang.String>);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public net.maddkraft.maddprestige.api.id.StageId id();
  public boolean enabled();
  public int ordinal();
  public java.util.Optional<java.lang.String> requirementTreeId();
}
```

### `net.maddkraft.maddprestige.api.validation.ValidationFinding` - LEGACY/PENDING REMOVAL

```text
Compiled from "ValidationFinding.java"
public final class net.maddkraft.maddprestige.api.validation.ValidationFinding extends java.lang.Record {
  public net.maddkraft.maddprestige.api.validation.ValidationFinding(java.lang.String, net.maddkraft.maddprestige.api.validation.ValidationSeverity, java.lang.String, java.lang.String, java.lang.String, java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String code();
  public net.maddkraft.maddprestige.api.validation.ValidationSeverity severity();
  public java.lang.String path();
  public java.lang.String explanation();
  public java.lang.String consequence();
  public java.lang.String remediation();
}
```

### `net.maddkraft.maddprestige.api.validation.ValidationReport` - LEGACY/PENDING REMOVAL

```text
Compiled from "ValidationReport.java"
public final class net.maddkraft.maddprestige.api.validation.ValidationReport extends java.lang.Record {
  public static final net.maddkraft.maddprestige.api.validation.ValidationReport VALID;
  public net.maddkraft.maddprestige.api.validation.ValidationReport(java.util.List<net.maddkraft.maddprestige.api.validation.ValidationFinding>);
  public static net.maddkraft.maddprestige.api.validation.ValidationReport of(java.util.Collection<net.maddkraft.maddprestige.api.validation.ValidationFinding>);
  public boolean hasErrors();
  public boolean canApply(java.util.Set<java.lang.String>);
  public net.maddkraft.maddprestige.api.validation.ValidationReport combine(net.maddkraft.maddprestige.api.validation.ValidationReport);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.util.List<net.maddkraft.maddprestige.api.validation.ValidationFinding> findings();
}
```

### `net.maddkraft.maddprestige.api.validation.ValidationSeverity` - LEGACY/PENDING REMOVAL

```text
Compiled from "ValidationSeverity.java"
public final class net.maddkraft.maddprestige.api.validation.ValidationSeverity extends java.lang.Enum<net.maddkraft.maddprestige.api.validation.ValidationSeverity> {
  public static final net.maddkraft.maddprestige.api.validation.ValidationSeverity ERROR;
  public static final net.maddkraft.maddprestige.api.validation.ValidationSeverity ACKNOWLEDGEMENT_REQUIRED;
  public static final net.maddkraft.maddprestige.api.validation.ValidationSeverity WARNING;
  public static final net.maddkraft.maddprestige.api.validation.ValidationSeverity INFORMATION;
  public static net.maddkraft.maddprestige.api.validation.ValidationSeverity[] values();
  public static net.maddkraft.maddprestige.api.validation.ValidationSeverity valueOf(java.lang.String);
}
```

### `net.maddkraft.maddprestige.api.value.ExactDecimal` - LEGACY/PENDING REMOVAL

```text
Compiled from "ExactDecimal.java"
public final class net.maddkraft.maddprestige.api.value.ExactDecimal implements java.lang.Comparable<net.maddkraft.maddprestige.api.value.ExactDecimal> {
  public static final net.maddkraft.maddprestige.api.value.ExactDecimal ZERO;
  public static net.maddkraft.maddprestige.api.value.ExactDecimal parse(java.lang.String);
  public static net.maddkraft.maddprestige.api.value.ExactDecimal of(java.math.BigDecimal);
  public java.math.BigDecimal asBigDecimal();
  public net.maddkraft.maddprestige.api.value.ExactDecimal withScale(int, java.math.RoundingMode);
  public net.maddkraft.maddprestige.api.value.ExactDecimal add(net.maddkraft.maddprestige.api.value.ExactDecimal);
  public net.maddkraft.maddprestige.api.value.ExactDecimal subtract(net.maddkraft.maddprestige.api.value.ExactDecimal);
  public net.maddkraft.maddprestige.api.value.ExactDecimal negate();
  public int precision();
  public int scale();
  public int compareTo(net.maddkraft.maddprestige.api.value.ExactDecimal);
  public boolean equals(java.lang.Object);
  public int hashCode();
  public java.lang.String toString();
  public int compareTo(java.lang.Object);
}
```
