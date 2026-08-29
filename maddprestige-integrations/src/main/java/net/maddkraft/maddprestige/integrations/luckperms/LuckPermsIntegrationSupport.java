package net.maddkraft.maddprestige.integrations.luckperms;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.DependencyDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.result.ErrorCategory;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.api.result.StructuredError;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardPreflight;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;

/** Shared external-state primitives for the active reward provider and frozen compatibility adapter. */
final class LuckPermsIntegrationSupport {
    private final ProviderId providerId;
    private final LuckPerms luckPerms;
    private final String implementationVersion;
    private final BooleanSupplier available;
    private final Clock clock;
    private final Function<String, InheritanceNode> inheritanceNodes;
    private final Function<String, PermissionNode> permissionNodes;

    LuckPermsIntegrationSupport(
            ProviderId providerId,
            LuckPerms luckPerms,
            String implementationVersion,
            BooleanSupplier available,
            Clock clock,
            Function<String, InheritanceNode> inheritanceNodes,
            Function<String, PermissionNode> permissionNodes) {
        this.providerId = Objects.requireNonNull(providerId, "provider id");
        this.luckPerms = Objects.requireNonNull(luckPerms, "LuckPerms API");
        this.implementationVersion = Objects.requireNonNull(implementationVersion, "implementation version");
        this.available = Objects.requireNonNull(available, "availability supplier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.inheritanceNodes = Objects.requireNonNull(inheritanceNodes, "inheritance node factory");
        this.permissionNodes = Objects.requireNonNull(permissionNodes, "permission node factory");
    }

    String implementationVersion() {
        return implementationVersion;
    }

    List<DependencyDescriptor> dependencies() {
        return List.of(new DependencyDescriptor("LuckPerms", "[5.5,6)", Optional.of(implementationVersion)));
    }

    List<CapabilityDescriptor> rewardCapabilities() {
        return List.of(new CapabilityDescriptor("additive-permission", "reward",
                "Adds one configured permanent permission without replacing unrelated nodes",
                Map.of("creates-groups", "false", "additive", "true")),
                new CapabilityDescriptor("additive-group", "reward",
                        "Adds one existing configured group without hierarchy or unrelated-node changes",
                        Map.of("creates-groups", "false", "additive", "true")));
    }

    ProviderHealth health() {
        boolean current = isAvailable();
        return new ProviderHealth(current ? ProviderHealthState.AVAILABLE : ProviderHealthState.UNAVAILABLE,
                current ? "luckperms.available" : "luckperms.unavailable",
                current ? "LuckPerms API service is available." : "LuckPerms API service is unavailable.",
                clock.instant());
    }

    boolean isAvailable() {
        return available.getAsBoolean();
    }

    InheritanceNode inheritanceNode(String groupName) {
        return inheritanceNodes.apply(groupName);
    }

    CompletableFuture<Void> saveUser(User user) {
        return luckPerms.getUserManager().saveUser(user);
    }

    CompletionStage<Result<Set<String>>> validateGroups(Set<String> groupNames) {
        Set<String> requested = Set.copyOf(Objects.requireNonNull(groupNames, "group names"));
        if (!isAvailable()) {
            return completedFailure("luckperms.unavailable", ErrorCategory.UNAVAILABLE,
                    "LuckPerms became unavailable before target validation.");
        }
        Set<String> found = ConcurrentHashMap.newKeySet();
        ArrayList<CompletableFuture<Void>> loads = new ArrayList<>();
        try {
            for (String groupName : requested) {
                if (groupName.isBlank()) {
                    return completedFailure("luckperms.group.invalid", ErrorCategory.INVALID,
                            "Configured LuckPerms group name cannot be blank.");
                }
                loads.add(luckPerms.getGroupManager().loadGroup(groupName)
                        .thenAccept(group -> group.ifPresent(ignored -> found.add(groupName))));
            }
        } catch (RuntimeException exception) {
            return completedFailure("luckperms.group.load_failed", ErrorCategory.UNAVAILABLE,
                    "LuckPerms group validation could not start: " + rootMessage(exception));
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
                .handle((ignored, failure) -> failure == null
                        ? Result.success(Set.copyOf(found))
                        : Result.<Set<String>>failure(error("luckperms.group.load_failed", ErrorCategory.UNAVAILABLE,
                                "LuckPerms group validation failed: " + rootMessage(failure))));
    }

    ActionCharacteristics characteristics(RewardDefinition definition) {
        Objects.requireNonNull(definition, "reward definition");
        return new ActionCharacteristics(true, false, true, true);
    }

    ValidationReport validateReward(RewardDefinition definition) {
        try {
            rewardNode(definition);
            return ValidationReport.VALID;
        } catch (IllegalArgumentException exception) {
            return ValidationReport.of(List.of(new ValidationFinding("luckperms.reward.invalid",
                    ValidationSeverity.ERROR, "rewards." + definition.id().value(), exception.getMessage(),
                    "The LuckPerms reward cannot be planned.",
                    "Use provider luckperms, type permission or group, and a nonblank configured node.")));
        }
    }

    CompletionStage<RewardPreflight> preflight(PlannedReward proposed) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(RewardPreflight.unavailable("LuckPerms is unavailable"));
        }
        final Node node;
        try {
            node = rewardNode(proposed.definition());
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(RewardPreflight.invalid(exception.getMessage()));
        }
        if (!(node instanceof InheritanceNode group)) {
            return CompletableFuture.completedFuture(RewardPreflight.ready(proposed));
        }
        return groupExists(group.getGroupName()).thenApply(exists -> exists
                ? RewardPreflight.ready(proposed)
                : RewardPreflight.invalid("Configured LuckPerms group does not exist; it will not be created"));
    }

    CompletionStage<ActionExecutionResult> execute(PlannedReward plannedReward) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed(
                    "LuckPerms disappeared before reward execution"));
        }
        final Node node;
        try {
            node = rewardNode(plannedReward.definition());
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed(exception.getMessage()));
        }
        CompletionStage<Boolean> targetReady = node instanceof InheritanceNode group
                ? groupExists(group.getGroupName())
                : CompletableFuture.completedFuture(true);
        return targetReady.thenCompose(exists -> exists
                ? addNode(plannedReward.playerId(), node)
                : CompletableFuture.completedFuture(ActionExecutionResult.failed(
                        "Configured LuckPerms group does not exist; it was not created")));
    }

    <T> CompletionStage<Result<T>> withLoadedUser(
            UUID playerId,
            Function<User, CompletionStage<Result<T>>> action) {
        boolean previouslyLoaded;
        CompletableFuture<User> loaded;
        try {
            previouslyLoaded = luckPerms.getUserManager().isLoaded(playerId);
            loaded = luckPerms.getUserManager().loadUser(playerId);
        } catch (RuntimeException exception) {
            return completedFailure("luckperms.user.load_failed", ErrorCategory.UNAVAILABLE,
                    "LuckPerms user load could not start: " + rootMessage(exception));
        }
        return loaded.thenCompose(user -> {
            CompletionStage<Result<T>> result;
            try {
                result = action.apply(user);
            } catch (RuntimeException exception) {
                if (!previouslyLoaded) {
                    cleanupSafely(user);
                }
                return completedFailure("luckperms.user.action_failed", ErrorCategory.UNCERTAIN,
                        "LuckPerms user action failed: " + rootMessage(exception));
            }
            return result.handle((outcome, failure) -> {
                if (!previouslyLoaded) {
                    cleanupSafely(user);
                }
                return failure == null ? outcome : Result.<T>failure(error(
                        "luckperms.user.action_failed", ErrorCategory.UNCERTAIN,
                        "LuckPerms user action failed after load: " + rootMessage(failure)));
            });
        }).handle((result, failure) -> failure == null ? result : Result.<T>failure(error(
                "luckperms.user.load_failed", ErrorCategory.UNAVAILABLE,
                "LuckPerms user load failed: " + rootMessage(failure))));
    }

    static StructuredError error(String code, ErrorCategory category, String message) {
        return new StructuredError(code, category, message, Map.of());
    }

    static <T> CompletionStage<Result<T>> completedFailure(
            String code, ErrorCategory category, String message) {
        return CompletableFuture.completedFuture(Result.failure(error(code, category, message)));
    }

    static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private CompletionStage<ActionExecutionResult> addNode(UUID playerId, Node node) {
        return withLoadedUser(playerId, user -> {
            if (user.data().toCollection().stream().anyMatch(existing ->
                    existing.getKey().equals(node.getKey()) && existing.getValue() == node.getValue()
                            && existing.getContexts().equals(node.getContexts())
                            && Objects.equals(existing.getExpiry(), node.getExpiry()))) {
                return CompletableFuture.completedFuture(Result.success(ActionExecutionResult.unchanged()));
            }
            if (!user.data().add(node).wasSuccessful()) {
                return CompletableFuture.completedFuture(Result.success(ActionExecutionResult.failed(
                        "LuckPerms rejected the configured additive reward node")));
            }
            return saveUser(user).handle((ignored, failure) -> failure == null
                    ? Result.success(ActionExecutionResult.applied())
                    : Result.success(ActionExecutionResult.uncertain(
                            "LuckPerms save failed after adding the configured reward node: "
                                    + rootMessage(failure))));
        }).thenApply(result -> result.value().orElseGet(() -> result.errors().stream()
                .findFirst().map(failure -> failure.category() == ErrorCategory.UNCERTAIN
                        ? ActionExecutionResult.uncertain(failure.message())
                        : ActionExecutionResult.failed(failure.message()))
                .orElseGet(() -> ActionExecutionResult.failed("LuckPerms reward failed safely"))));
    }

    private CompletionStage<Boolean> groupExists(String groupName) {
        return validateGroups(Set.of(groupName)).thenApply(result -> result.isSuccess()
                && result.value().orElseThrow().contains(groupName));
    }

    private Node rewardNode(RewardDefinition definition) {
        if (!providerId.equals(definition.providerId())) {
            throw new IllegalArgumentException("LuckPerms reward must target provider luckperms");
        }
        String configured = definition.value().canonical();
        if (configured.isBlank() || configured.length() > 256
                || configured.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("LuckPerms reward node must be nonblank and bounded");
        }
        return switch (definition.type()) {
            case "permission" -> permissionNodes.apply(configured);
            case "group" -> inheritanceNodes.apply(configured);
            default -> throw new IllegalArgumentException("LuckPerms reward type must be permission or group");
        };
    }

    private void cleanupSafely(User user) {
        try {
            luckPerms.getUserManager().cleanupUser(user);
        } catch (RuntimeException ignored) {
            // Cleanup cannot change or downgrade the already classified external operation outcome.
        }
    }
}
