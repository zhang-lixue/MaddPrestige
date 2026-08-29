package net.maddkraft.maddprestige.integrations.luckperms;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.result.ErrorCategory;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.api.result.StructuredError;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardPreflight;
import net.maddkraft.maddprestige.api.reward.RewardProvider;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;

/** Active LuckPerms boundary: additive configured rewards only, never Prestige progression. */
public final class LuckPermsRewardProvider implements RewardProvider {
    public static final ProviderId PROVIDER_ID = new ProviderId("luckperms");
    private final LuckPerms luckPerms;
    private final String implementationVersion;
    private final BooleanSupplier available;
    private final Clock clock;
    private final Function<String, InheritanceNode> inheritanceNodes;
    private final Function<String, PermissionNode> permissionNodes;

    public LuckPermsRewardProvider(
            LuckPerms luckPerms,
            String implementationVersion,
            BooleanSupplier available,
            Clock clock) {
        this(luckPerms, implementationVersion, available, clock,
                groupName -> InheritanceNode.builder(groupName).build(),
                permission -> PermissionNode.builder(permission).value(true).build());
    }

    LuckPermsRewardProvider(
            LuckPerms luckPerms,
            String implementationVersion,
            BooleanSupplier available,
            Clock clock,
            Function<String, InheritanceNode> inheritanceNodes,
            Function<String, PermissionNode> permissionNodes) {
        this.luckPerms = Objects.requireNonNull(luckPerms, "LuckPerms API");
        this.implementationVersion = Objects.requireNonNull(implementationVersion, "implementation version");
        this.available = Objects.requireNonNull(available, "availability supplier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.inheritanceNodes = Objects.requireNonNull(inheritanceNodes, "inheritance node factory");
        this.permissionNodes = Objects.requireNonNull(permissionNodes, "permission node factory");
    }

    @Override
    public ProviderDescriptor descriptor() {
        return new ProviderDescriptor(PROVIDER_ID, "maddprestige", "1", implementationVersion,
                List.of(new DependencyDescriptor("LuckPerms", "[5.5,6)", Optional.of(implementationVersion))),
                List.of(new CapabilityDescriptor("additive-permission", "reward",
                        "Adds one configured permanent permission without replacing unrelated nodes",
                        Map.of("creates-groups", "false", "additive", "true")),
                        new CapabilityDescriptor("additive-group", "reward",
                                "Adds one existing configured group without hierarchy or unrelated-node changes",
                                Map.of("creates-groups", "false", "additive", "true"))));
    }

    @Override
    public ProviderHealth health() {
        boolean current = available.getAsBoolean();
        return new ProviderHealth(current ? ProviderHealthState.AVAILABLE : ProviderHealthState.UNAVAILABLE,
                current ? "luckperms.available" : "luckperms.unavailable",
                current ? "LuckPerms API service is available." : "LuckPerms API service is unavailable.",
                clock.instant());
    }

    @Override
    public ActionCharacteristics characteristics(RewardDefinition definition) {
        return new ActionCharacteristics(true, false, true, true);
    }

    @Override
    public ValidationReport validate(RewardDefinition definition) {
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

    @Override
    public CompletionStage<RewardPreflight> preflight(PlannedReward proposed) {
        if (!available.getAsBoolean()) {
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

    @Override
    public CompletionStage<ActionExecutionResult> execute(PlannedReward plannedReward) {
        if (!available.getAsBoolean()) {
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
            return luckPerms.getUserManager().saveUser(user).handle((ignored, failure) -> failure == null
                    ? Result.success(ActionExecutionResult.applied())
                    : Result.success(ActionExecutionResult.uncertain(
                            "LuckPerms save failed after adding the configured reward node: "
                                    + rootMessage(failure))));
        }).thenApply(result -> result.value().orElseGet(() -> result.errors().stream()
                .findFirst().map(error -> error.category() == ErrorCategory.UNCERTAIN
                        ? ActionExecutionResult.uncertain(error.message())
                        : ActionExecutionResult.failed(error.message()))
                .orElseGet(() -> ActionExecutionResult.failed("LuckPerms reward failed safely"))));
    }

    private CompletionStage<Boolean> groupExists(String groupName) {
        if (!available.getAsBoolean()) {
            return CompletableFuture.completedFuture(false);
        }
        try {
            return luckPerms.getGroupManager().loadGroup(groupName)
                    .handle((group, failure) -> failure == null && group.isPresent());
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(false);
        }
    }

    private Node rewardNode(RewardDefinition definition) {
        if (!PROVIDER_ID.equals(definition.providerId())) {
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

    private <T> CompletionStage<Result<T>> withLoadedUser(
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

    private void cleanupSafely(User user) {
        try {
            luckPerms.getUserManager().cleanupUser(user);
        } catch (RuntimeException ignored) {
            // Cleanup cannot change or downgrade the already classified external operation outcome.
        }
    }

    private static StructuredError error(String code, ErrorCategory category, String message) {
        return new StructuredError(code, category, message, Map.of());
    }

    private static <T> CompletionStage<Result<T>> completedFailure(
            String code, ErrorCategory category, String message) {
        return CompletableFuture.completedFuture(Result.failure(error(code, category, message)));
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
