package net.maddkraft.maddprestige.api.provider;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.annotation.Stable;

/**
 * Asynchronous third-party metric callback used by requirement evaluation. Implementations must be thread-safe;
 * MaddPrestige may invoke different calls concurrently on bounded workers.
 */
@FunctionalInterface
@Stable
public interface RequirementProvider {
    /**
     * Reads a bounded immutable batch and returns exactly one result for each requested key. Implementations must
     * honor the context deadline and cancellation state. Operational failures belong in unavailable results; the
     * returned stage and map must be non-null and must not contain extra, missing, null, or type-incompatible values.
     *
     * @param context non-null owner-attested call context valid only through callback completion
     * @param playerId non-null player identity
     * @param queries immutable bounded request list owned by MaddPrestige
     * @return non-null asynchronous result map whose keys are exactly {@code queries}
     */
    CompletionStage<Map<ProviderMetricRequest, ProviderMetricResult>> read(
            ProviderCallContext context,
            UUID playerId,
            List<ProviderMetricRequest> queries);
}
