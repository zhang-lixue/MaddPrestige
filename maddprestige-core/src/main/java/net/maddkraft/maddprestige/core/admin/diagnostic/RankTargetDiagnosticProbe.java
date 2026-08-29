package net.maddkraft.maddprestige.core.admin.diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.rank.RankAdapter;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;

public final class RankTargetDiagnosticProbe implements DiagnosticProbe {
    private final Supplier<Optional<StageConfiguration>> stages;
    private final ProviderRegistry providers;

    public RankTargetDiagnosticProbe(
            Supplier<Optional<StageConfiguration>> stages,
            ProviderRegistry providers) {
        this.stages = Objects.requireNonNull(stages, "stages");
        this.providers = Objects.requireNonNull(providers, "providers");
    }

    @Override
    public Set<DiagnosticDomain> domains() {
        return Set.of();
    }

    @Override
    public CompletionStage<List<DiagnosticFinding>> inspect() {
        Optional<StageConfiguration> current = stages.get();
        if (current.isEmpty() || !current.orElseThrow().active()
                || current.orElseThrow().rankProvider().isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        StageConfiguration configuration = current.orElseThrow();
        var providerId = configuration.rankProvider().orElseThrow();
        var provider = providers.provider(providerId);
        if (provider.isEmpty() || !(provider.orElseThrow() instanceof RankAdapter adapter)) {
            return CompletableFuture.completedFuture(List.of(new DiagnosticFinding(
                    "rank.provider.unavailable", DiagnosticSeverity.BLOCKED, "rank-provider",
                    "progression.stages", "Configured rank adapter is unavailable: " + providerId.value(),
                    "Restore the adapter; MaddPrestige will not create or substitute external groups.")));
        }
        Set<String> groups = configuration.managedGroups(providerId);
        try {
            return adapter.validateTargets(groups).handle((result, failure) -> {
                if (failure != null || result == null || !result.isSuccess()) {
                    return List.of(new DiagnosticFinding("rank.targets.unavailable", DiagnosticSeverity.BLOCKED,
                            "rank-provider", "progression.stages", "External rank targets could not be verified.",
                            "Restore the provider storage/API and run doctor again."));
                }
                Set<String> found = result.value().orElse(Set.of());
                ArrayList<DiagnosticFinding> findings = new ArrayList<>();
                configuration.stages().values().stream().filter(stage -> stage.enabled())
                        .filter(stage -> stage.projection().providerId().filter(providerId::equals).isPresent())
                        .filter(stage -> stage.projection().groupName().filter(group -> !found.contains(group)).isPresent())
                        .forEach(stage -> findings.add(new DiagnosticFinding("rank.group.not_found",
                                DiagnosticSeverity.BLOCKED, "rank-provider",
                                "progression.stages." + stage.id().value() + ".projection.group",
                                "Stage '" + stage.id().value() + "' references missing external group '"
                                        + stage.projection().groupName().orElseThrow() + "'.",
                                "Create the group outside MaddPrestige or edit the stage to use an existing group.")));
                return List.copyOf(findings);
            });
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(List.of(new DiagnosticFinding("rank.targets.failed",
                    DiagnosticSeverity.BLOCKED, "rank-provider", "progression.stages",
                    "External rank target validation failed.", "Restore the provider and run doctor again.")));
        }
    }
}
