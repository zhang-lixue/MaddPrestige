package net.maddkraft.maddprestige.core.stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.rank.RankAdapter;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;

public final class StageConfigurationValidator {
    public CompletionStage<ValidationReport> validateExternalTargets(
            StageConfiguration configuration,
            ProviderRegistry providers) {
        if (!configuration.active()) {
            return CompletableFuture.completedFuture(ValidationReport.VALID);
        }
        Optional<ProviderId> providerId = configuration.rankProvider();
        if (providerId.isEmpty()) {
            return CompletableFuture.completedFuture(ValidationReport.VALID);
        }
        var snapshot = providers.find(providerId.orElseThrow());
        var provider = providers.provider(providerId.orElseThrow());
        if (snapshot.isEmpty() || provider.isEmpty() || !(provider.orElseThrow() instanceof RankAdapter adapter)) {
            return CompletableFuture.completedFuture(ValidationReport.of(List.of(finding(
                    "stage.rank_provider.unavailable", "progression.stages",
                    "Configured rank adapter '" + providerId.orElseThrow().value() + "' is not registered.",
                    "Install and register the adapter, or use projection: none."))));
        }
        if (snapshot.orElseThrow().activation() != ActivationState.ACTIVE
                || !healthy(snapshot.orElseThrow().health().state())) {
            return CompletableFuture.completedFuture(ValidationReport.of(List.of(finding(
                    "stage.rank_provider.unhealthy", "progression.stages",
                    "Configured rank adapter is not active and healthy: "
                            + snapshot.orElseThrow().health().reason(),
                    "Restore the provider before applying this active ladder."))));
        }
        Set<String> groups = configuration.managedGroups(providerId.orElseThrow());
        CompletionStage<net.maddkraft.maddprestige.api.result.Result<Set<String>>> validation;
        try {
            validation = adapter.validateTargets(groups);
        } catch (RuntimeException exception) {
            validation = CompletableFuture.failedFuture(exception);
        }
        return validation.handle((result, failure) -> {
            if (failure != null) {
                return ValidationReport.of(List.of(finding(
                        "stage.rank_targets.unavailable", "progression.stages",
                        "External target validation failed: " + rootMessage(failure),
                        "Restore the provider/storage service and retry validation.")));
            }
            if (!result.isSuccess()) {
                ArrayList<ValidationFinding> findings = new ArrayList<>();
                result.errors().forEach(error -> findings.add(finding(
                        error.code(), "progression.stages", error.message(),
                        "Create missing groups outside MaddPrestige or choose existing groups.")));
                return ValidationReport.of(findings);
            }
            Set<String> found = result.value().orElse(Set.of());
            ArrayList<ValidationFinding> findings = new ArrayList<>();
            groups.stream().filter(group -> !found.contains(group)).sorted().forEach(group -> findings.add(finding(
                    "rank.group.not_found", "progression.stages",
                    "Configured external group does not exist: " + group,
                    "Create the group outside MaddPrestige or choose an existing group; MaddPrestige never creates groups.")));
            return ValidationReport.of(findings);
        });
    }

    private static boolean healthy(ProviderHealthState state) {
        return state == ProviderHealthState.AVAILABLE || state == ProviderHealthState.ACTIVE;
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static ValidationFinding finding(
            String code, String path, String explanation, String remediation) {
        return new ValidationFinding(code, ValidationSeverity.ERROR, path, explanation,
                "The active ladder cannot safely use the requested external projection.", remediation);
    }
}
