package net.maddkraft.maddprestige.integrations.luckperms;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.rank.AmbiguousRankMembership;
import net.maddkraft.maddprestige.api.rank.ManagedRankState;
import net.maddkraft.maddprestige.api.rank.RankAdapter;
import net.maddkraft.maddprestige.api.rank.RankProjectionOutcome;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.api.rank.RankProjectionResult;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardPreflight;
import net.maddkraft.maddprestige.api.reward.RewardProvider;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.result.ErrorCategory;
import net.maddkraft.maddprestige.api.result.Result;

public final class LuckPermsRankAdapter implements RankAdapter, RewardProvider {
    public static final ProviderId PROVIDER_ID = new ProviderId("luckperms");
    private final LuckPermsIntegrationSupport support;

    public LuckPermsRankAdapter(
            LuckPerms luckPerms,
            String implementationVersion,
            BooleanSupplier available,
            Clock clock) {
        this(luckPerms, implementationVersion, available, clock,
                groupName -> InheritanceNode.builder(groupName).build(),
                permission -> PermissionNode.builder(permission).value(true).build());
    }

    LuckPermsRankAdapter(
            LuckPerms luckPerms,
            String implementationVersion,
            BooleanSupplier available,
            Clock clock,
            Function<String, InheritanceNode> inheritanceNodes) {
        this(luckPerms, implementationVersion, available, clock, inheritanceNodes,
                permission -> PermissionNode.builder(permission).value(true).build());
    }

    LuckPermsRankAdapter(
            LuckPerms luckPerms,
            String implementationVersion,
            BooleanSupplier available,
            Clock clock,
            Function<String, InheritanceNode> inheritanceNodes,
            Function<String, PermissionNode> permissionNodes) {
        this.support = new LuckPermsIntegrationSupport(PROVIDER_ID, luckPerms, implementationVersion,
                available, clock, inheritanceNodes, permissionNodes);
    }

    @Override
    public ProviderDescriptor descriptor() {
        ArrayList<CapabilityDescriptor> capabilities = new ArrayList<>();
        capabilities.add(new CapabilityDescriptor("managed-direct-membership", "rank",
                "Offline-safe projection of explicit permanent context-free managed group membership",
                Map.of("creates-groups", "false", "offline-load", "true")));
        capabilities.addAll(support.rewardCapabilities());
        return new ProviderDescriptor(PROVIDER_ID, "maddprestige", "1", support.implementationVersion(),
                support.dependencies(), List.copyOf(capabilities));
    }

    @Override
    public ProviderHealth health() {
        return support.health();
    }

    @Override
    public CompletionStage<Result<Set<String>>> validateTargets(Set<String> groupNames) {
        return support.validateGroups(groupNames);
    }

    @Override
    public CompletionStage<Result<ManagedRankState>> readManagedState(UUID playerId, Set<String> managedGroups) {
        Set<String> pinnedGroups = Set.copyOf(Objects.requireNonNull(managedGroups, "managed groups"));
        if (!support.isAvailable()) {
            return LuckPermsIntegrationSupport.completedFailure(
                    "luckperms.unavailable", ErrorCategory.UNAVAILABLE,
                    "LuckPerms became unavailable before user load.");
        }
        return support.withLoadedUser(playerId, user -> {
            try {
                return CompletableFuture.completedFuture(Result.success(snapshot(user, pinnedGroups)));
            } catch (RuntimeException exception) {
                return LuckPermsIntegrationSupport.completedFailure(
                        "luckperms.user.read_failed", ErrorCategory.FAILED,
                        "LuckPerms managed membership read failed: "
                                + LuckPermsIntegrationSupport.rootMessage(exception));
            }
        });
    }

    @Override
    public CompletionStage<Result<RankProjectionResult>> project(RankProjectionRequest request) {
        Objects.requireNonNull(request, "projection request");
        return support.validateGroups(request.managedGroups()).thenCompose(validation -> {
            if (!validation.isSuccess()) {
                return CompletableFuture.completedFuture(Result.failure(validation.errors().getFirst()));
            }
            Set<String> found = validation.value().orElseThrow();
            if (!found.containsAll(request.managedGroups())) {
                Set<String> missing = new HashSet<>(request.managedGroups());
                missing.removeAll(found);
                return CompletableFuture.completedFuture(Result.failure(LuckPermsIntegrationSupport.error(
                        "luckperms.group.not_found", ErrorCategory.INVALID,
                        "Configured LuckPerms group(s) do not exist: " + missing)));
            }
            if (!support.isAvailable()) {
                return LuckPermsIntegrationSupport.completedFailure(
                        "luckperms.unavailable", ErrorCategory.UNAVAILABLE,
                        "LuckPerms became unavailable after target validation and before user mutation.");
            }
            return support.withLoadedUser(request.playerId(), user -> {
                try {
                    return mutateAndSave(user, request);
                } catch (RuntimeException exception) {
                    return LuckPermsIntegrationSupport.completedFailure(
                            "luckperms.user.mutation_exception", ErrorCategory.UNCERTAIN,
                            "LuckPerms mutation raised an exception; reconciliation is required: "
                                    + LuckPermsIntegrationSupport.rootMessage(exception));
                }
            });
        });
    }

    @Override
    public ActionCharacteristics characteristics(RewardDefinition definition) {
        return support.characteristics(definition);
    }

    @Override
    public ValidationReport validate(RewardDefinition definition) {
        return support.validateReward(definition);
    }

    @Override
    public CompletionStage<RewardPreflight> preflight(PlannedReward proposed) {
        return support.preflight(proposed);
    }

    @Override
    public CompletionStage<ActionExecutionResult> execute(PlannedReward plannedReward) {
        return support.execute(plannedReward);
    }

    private CompletionStage<Result<RankProjectionResult>> mutateAndSave(User user, RankProjectionRequest request) {
        ManagedRankState before = snapshot(user, request.managedGroups());
        if (before.hasAmbiguity()) {
            return CompletableFuture.completedFuture(Result.failure(LuckPermsIntegrationSupport.error(
                    "luckperms.membership.ambiguous", ErrorCategory.CONFLICT,
                    "Contextual or temporary managed-group membership is ambiguous and was preserved.")));
        }
        Map<String, List<InheritanceNode>> currentNodes = permanentManagedNodes(user, request.managedGroups());
        boolean desiredPresent = request.desiredGroup().filter(currentNodes::containsKey).isPresent();
        List<InheritanceNode> removals = currentNodes.entrySet().stream()
                .filter(entry -> request.desiredGroup().filter(entry.getKey()::equals).isEmpty())
                .flatMap(entry -> entry.getValue().stream()).toList();
        if ((desiredPresent && removals.isEmpty())
                || (request.desiredGroup().isEmpty() && currentNodes.isEmpty())) {
            return CompletableFuture.completedFuture(Result.success(
                    new RankProjectionResult(before, before, RankProjectionOutcome.UNCHANGED)));
        }
        if (!support.isAvailable()) {
            return LuckPermsIntegrationSupport.completedFailure(
                    "luckperms.unavailable", ErrorCategory.UNAVAILABLE,
                    "LuckPerms became unavailable before user mutation.");
        }

        Optional<InheritanceNode> addition = Optional.empty();
        if (request.desiredGroup().isPresent() && !desiredPresent) {
            InheritanceNode node = support.inheritanceNode(request.desiredGroup().orElseThrow());
            DataMutateResult added = user.data().add(node);
            if (!added.wasSuccessful()) {
                return CompletableFuture.completedFuture(Result.failure(LuckPermsIntegrationSupport.error(
                        "luckperms.membership.add_failed", ErrorCategory.CONFLICT,
                        "Desired membership could not be added; no old managed membership was removed.")));
            }
            addition = Optional.of(node);
        }

        ArrayList<InheritanceNode> removed = new ArrayList<>();
        for (InheritanceNode node : removals) {
            if (!user.data().remove(node).wasSuccessful()) {
                boolean rolledBack = rollback(user, addition, removed);
                ErrorCategory category = rolledBack ? ErrorCategory.FAILED : ErrorCategory.UNCERTAIN;
                return CompletableFuture.completedFuture(Result.failure(LuckPermsIntegrationSupport.error(
                        "luckperms.membership.remove_failed", category,
                        rolledBack
                                ? "Managed membership removal failed before save; in-memory changes were restored."
                                : "Managed membership removal failed and in-memory rollback was incomplete.")));
            }
            removed.add(node);
        }
        if (!support.isAvailable()) {
            rollback(user, addition, removed);
            return LuckPermsIntegrationSupport.completedFailure(
                    "luckperms.disabled_during_projection", ErrorCategory.UNCERTAIN,
                    "LuckPerms became unavailable after mutation began; reconciliation is required.");
        }
        return support.saveUser(user).handle((ignored, failure) -> {
            if (failure != null) {
                return Result.<RankProjectionResult>failure(LuckPermsIntegrationSupport.error(
                        "luckperms.user.save_failed", ErrorCategory.UNCERTAIN,
                        "LuckPerms user save failed after mutation began: "
                                + LuckPermsIntegrationSupport.rootMessage(failure)));
            }
            try {
                ManagedRankState after = snapshot(user, request.managedGroups());
                return Result.success(new RankProjectionResult(before, after, RankProjectionOutcome.APPLIED));
            } catch (RuntimeException exception) {
                return Result.<RankProjectionResult>failure(LuckPermsIntegrationSupport.error(
                        "luckperms.user.post_save_verification_failed", ErrorCategory.UNCERTAIN,
                        "LuckPerms save completed, but resulting membership could not be verified; "
                                + "reconciliation is required: "
                                + LuckPermsIntegrationSupport.rootMessage(exception)));
            }
        });
    }

    private static ManagedRankState snapshot(User user, Set<String> managedGroups) {
        HashSet<String> permanent = new HashSet<>();
        ArrayList<AmbiguousRankMembership> ambiguous = new ArrayList<>();
        for (Node node : user.data().toCollection()) {
            if (node instanceof InheritanceNode inheritance) {
                Optional<String> configuredGroup = configuredGroup(managedGroups, inheritance.getGroupName());
                if (configuredGroup.isEmpty()) {
                    continue;
                }
                if (node.hasExpiry() || !node.getContexts().isEmpty()) {
                    Optional<java.time.Instant> expiry = node.hasExpiry()
                            ? Optional.of(node.getExpiry()) : Optional.empty();
                    ambiguous.add(new AmbiguousRankMembership(configuredGroup.orElseThrow(),
                            node.getContexts().toMap(), expiry));
                } else {
                    permanent.add(configuredGroup.orElseThrow());
                }
            }
        }
        return new ManagedRankState(user.getUniqueId(), permanent, ambiguous);
    }

    private static Map<String, List<InheritanceNode>> permanentManagedNodes(User user, Set<String> managedGroups) {
        return user.data().toCollection().stream()
                .filter(InheritanceNode.class::isInstance)
                .map(InheritanceNode.class::cast)
                .filter(node -> configuredGroup(managedGroups, node.getGroupName()).isPresent())
                .filter(node -> !node.hasExpiry() && node.getContexts().isEmpty())
                .collect(java.util.stream.Collectors.groupingBy(
                        node -> configuredGroup(managedGroups, node.getGroupName()).orElseThrow(),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
    }

    private static Optional<String> configuredGroup(Set<String> managedGroups, String luckPermsGroup) {
        return managedGroups.stream().filter(group -> group.equalsIgnoreCase(luckPermsGroup)).findFirst();
    }

    private static boolean rollback(
            User user,
            Optional<InheritanceNode> addition,
            List<InheritanceNode> removed) {
        boolean successful = true;
        for (InheritanceNode node : removed) {
            successful &= user.data().add(node).wasSuccessful();
        }
        if (addition.isPresent()) {
            successful &= user.data().remove(addition.orElseThrow()).wasSuccessful();
        }
        return successful;
    }

}
