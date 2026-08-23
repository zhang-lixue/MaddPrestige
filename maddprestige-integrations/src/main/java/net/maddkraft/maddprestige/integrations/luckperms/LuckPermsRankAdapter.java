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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.DependencyDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.rank.AmbiguousRankMembership;
import net.maddkraft.maddprestige.api.rank.ManagedRankState;
import net.maddkraft.maddprestige.api.rank.RankAdapter;
import net.maddkraft.maddprestige.api.rank.RankProjectionOutcome;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.api.rank.RankProjectionResult;
import net.maddkraft.maddprestige.api.result.ErrorCategory;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.api.result.StructuredError;

public final class LuckPermsRankAdapter implements RankAdapter {
    public static final ProviderId PROVIDER_ID = new ProviderId("luckperms");
    private final LuckPerms luckPerms;
    private final String implementationVersion;
    private final BooleanSupplier available;
    private final Clock clock;
    private final Function<String, InheritanceNode> inheritanceNodes;

    public LuckPermsRankAdapter(
            LuckPerms luckPerms,
            String implementationVersion,
            BooleanSupplier available,
            Clock clock) {
        this(luckPerms, implementationVersion, available, clock,
                groupName -> InheritanceNode.builder(groupName).build());
    }

    LuckPermsRankAdapter(
            LuckPerms luckPerms,
            String implementationVersion,
            BooleanSupplier available,
            Clock clock,
            Function<String, InheritanceNode> inheritanceNodes) {
        this.luckPerms = Objects.requireNonNull(luckPerms, "LuckPerms API");
        this.implementationVersion = Objects.requireNonNull(implementationVersion, "implementation version");
        this.available = Objects.requireNonNull(available, "availability supplier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.inheritanceNodes = Objects.requireNonNull(inheritanceNodes, "inheritance node factory");
    }

    @Override
    public ProviderDescriptor descriptor() {
        return new ProviderDescriptor(PROVIDER_ID, "maddprestige", "1", implementationVersion,
                List.of(new DependencyDescriptor("LuckPerms", "[5.5,6)", Optional.of(implementationVersion))),
                List.of(new CapabilityDescriptor("managed-direct-membership", "rank",
                        "Offline-safe projection of explicit permanent context-free managed group membership",
                        Map.of("creates-groups", "false", "offline-load", "true"))));
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
    public CompletionStage<Result<Set<String>>> validateTargets(Set<String> groupNames) {
        Set<String> requested = Set.copyOf(Objects.requireNonNull(groupNames, "group names"));
        if (!available.getAsBoolean()) {
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
                CompletableFuture<Void> load = luckPerms.getGroupManager().loadGroup(groupName)
                        .thenAccept(group -> group.ifPresent(ignored -> found.add(groupName)));
                loads.add(load);
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

    @Override
    public CompletionStage<Result<ManagedRankState>> readManagedState(UUID playerId, Set<String> managedGroups) {
        Set<String> pinnedGroups = Set.copyOf(Objects.requireNonNull(managedGroups, "managed groups"));
        if (!available.getAsBoolean()) {
            return completedFailure("luckperms.unavailable", ErrorCategory.UNAVAILABLE,
                    "LuckPerms became unavailable before user load.");
        }
        return withLoadedUser(playerId, user -> {
            try {
                return CompletableFuture.completedFuture(Result.success(snapshot(user, pinnedGroups)));
            } catch (RuntimeException exception) {
                return completedFailure("luckperms.user.read_failed", ErrorCategory.FAILED,
                        "LuckPerms managed membership read failed: " + rootMessage(exception));
            }
        });
    }

    @Override
    public CompletionStage<Result<RankProjectionResult>> project(RankProjectionRequest request) {
        Objects.requireNonNull(request, "projection request");
        return validateTargets(request.managedGroups()).thenCompose(validation -> {
            if (!validation.isSuccess()) {
                return CompletableFuture.completedFuture(Result.failure(validation.errors().getFirst()));
            }
            Set<String> found = validation.value().orElseThrow();
            if (!found.containsAll(request.managedGroups())) {
                Set<String> missing = new HashSet<>(request.managedGroups());
                missing.removeAll(found);
                return CompletableFuture.completedFuture(Result.failure(error(
                        "luckperms.group.not_found", ErrorCategory.INVALID,
                        "Configured LuckPerms group(s) do not exist: " + missing)));
            }
            if (!available.getAsBoolean()) {
                return completedFailure("luckperms.unavailable", ErrorCategory.UNAVAILABLE,
                        "LuckPerms became unavailable after target validation and before user mutation.");
            }
            return withLoadedUser(request.playerId(), user -> {
                try {
                    return mutateAndSave(user, request);
                } catch (RuntimeException exception) {
                    return completedFailure("luckperms.user.mutation_exception", ErrorCategory.UNCERTAIN,
                            "LuckPerms mutation raised an exception; reconciliation is required: "
                                    + rootMessage(exception));
                }
            });
        });
    }

    private CompletionStage<Result<RankProjectionResult>> mutateAndSave(User user, RankProjectionRequest request) {
        ManagedRankState before = snapshot(user, request.managedGroups());
        if (before.hasAmbiguity()) {
            return CompletableFuture.completedFuture(Result.failure(error(
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
        if (!available.getAsBoolean()) {
            return completedFailure("luckperms.unavailable", ErrorCategory.UNAVAILABLE,
                    "LuckPerms became unavailable before user mutation.");
        }

        Optional<InheritanceNode> addition = Optional.empty();
        if (request.desiredGroup().isPresent() && !desiredPresent) {
            InheritanceNode node = inheritanceNodes.apply(request.desiredGroup().orElseThrow());
            DataMutateResult added = user.data().add(node);
            if (!added.wasSuccessful()) {
                return CompletableFuture.completedFuture(Result.failure(error(
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
                return CompletableFuture.completedFuture(Result.failure(error(
                        "luckperms.membership.remove_failed", category,
                        rolledBack
                                ? "Managed membership removal failed before save; in-memory changes were restored."
                                : "Managed membership removal failed and in-memory rollback was incomplete.")));
            }
            removed.add(node);
        }
        if (!available.getAsBoolean()) {
            rollback(user, addition, removed);
            return completedFailure("luckperms.disabled_during_projection", ErrorCategory.UNCERTAIN,
                    "LuckPerms became unavailable after mutation began; reconciliation is required.");
        }
        return luckPerms.getUserManager().saveUser(user).handle((ignored, failure) -> {
            if (failure != null) {
                return Result.<RankProjectionResult>failure(error(
                        "luckperms.user.save_failed", ErrorCategory.UNCERTAIN,
                        "LuckPerms user save failed after mutation began: " + rootMessage(failure)));
            }
            try {
                ManagedRankState after = snapshot(user, request.managedGroups());
                return Result.success(new RankProjectionResult(before, after, RankProjectionOutcome.APPLIED));
            } catch (RuntimeException exception) {
                return Result.<RankProjectionResult>failure(error(
                        "luckperms.user.post_save_verification_failed", ErrorCategory.UNCERTAIN,
                        "LuckPerms save completed, but resulting membership could not be verified; "
                                + "reconciliation is required: " + rootMessage(exception)));
            }
        });
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
        })
                .handle((result, failure) -> failure == null ? result : Result.<T>failure(error(
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
