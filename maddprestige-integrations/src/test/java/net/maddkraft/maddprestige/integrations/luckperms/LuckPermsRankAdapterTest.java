package net.maddkraft.maddprestige.integrations.luckperms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.rank.RankProjectionOutcome;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.api.result.ErrorCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LuckPermsRankAdapterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("[A05] Missing configured group fails before user load/removal and is never created")
    void missingGroupFailsWithoutCreationOrRemoval() {
        Harness harness = new Harness(false, Set.of("first_group"), List.of(group("first_group")));
        LuckPermsRankAdapter adapter = harness.adapter(() -> true);
        var result = adapter.project(request(harness.playerId, Set.of("first_group", "missing_group"),
                Optional.of("missing_group"))).toCompletableFuture().join();
        assertFalse(result.isSuccess());
        assertEquals("luckperms.group.not_found", result.errors().getFirst().code());
        assertEquals(Set.of("first_group"), harness.inheritanceGroups());
        assertEquals(0, harness.userLoads.get());
        assertEquals(0, harness.groupCreations.get());
        assertTrue(java.util.Arrays.stream(LuckPermsRankAdapter.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().toLowerCase().contains("creategroup")));
    }

    @Test
    @DisplayName("[A06][A61] Online/cached projection reloads safely and changes only exact managed membership")
    void isolatesManagedMembershipForCachedUser() {
        Node permission = permission("example.permission");
        InheritanceNode temporaryUnrelated = temporaryGroup("event_group", Duration.ofMinutes(10));
        Harness harness = new Harness(true, Set.of("first_group", "second_group"), List.of(
                group("first_group"), group("supporter_group"), group("staff_group"),
                group("unrelated_group"), temporaryUnrelated, permission));
        LuckPermsRankAdapter adapter = harness.adapter(() -> true);
        var result = adapter.project(request(harness.playerId, Set.of("first_group", "second_group"),
                Optional.of("second_group"))).toCompletableFuture().join();
        assertTrue(result.isSuccess(), result.errors().toString());
        assertEquals(RankProjectionOutcome.APPLIED, result.value().orElseThrow().outcome());
        assertEquals(Set.of("second_group", "supporter_group", "staff_group", "unrelated_group", "event_group"),
                harness.inheritanceGroups());
        assertTrue(harness.nodes.contains(permission));
        assertTrue(harness.nodes.contains(temporaryUnrelated));
        assertEquals(1, harness.userLoads.get());
        assertEquals(1, harness.userSaves.get());
        assertEquals(0, harness.cleanups.get());
        assertEquals(0, harness.groupCreations.get());

        var repeated = adapter.project(request(harness.playerId, Set.of("first_group", "second_group"),
                Optional.of("second_group"))).toCompletableFuture().join();
        assertEquals(RankProjectionOutcome.UNCHANGED, repeated.value().orElseThrow().outcome());
        assertEquals(1, harness.userSaves.get());
    }

    @Test
    @DisplayName("[A06][A70] LuckPerms-normalized group nodes retain configured external spelling")
    void mapsNormalizedGroupNodesToConfiguredNames() {
        Harness harness = new Harness(false, Set.of("Member", "Adventurer"), List.of(group("member")));

        var result = harness.adapter(() -> true).project(request(harness.playerId,
                Set.of("Member", "Adventurer"), Optional.of("Adventurer")))
                .toCompletableFuture().join();

        assertTrue(result.isSuccess(), result.errors().toString());
        assertEquals(Set.of("Member"),
                result.value().orElseThrow().before().permanentContextFreeGroups());
        assertEquals(Set.of("Adventurer"),
                result.value().orElseThrow().after().permanentContextFreeGroups());
        assertFalse(harness.inheritanceGroups().contains("member"));
    }

    @Test
    @DisplayName("[A06][A07] Contextual and temporary managed nodes are reported and preserved")
    void ambiguousManagedNodesFailClosed() {
        InheritanceNode contextual = contextualGroup("second_group", "server", "example");
        InheritanceNode temporary = temporaryGroup("first_group", Duration.ofMinutes(5));
        Harness harness = new Harness(false, Set.of("first_group", "second_group"),
                List.of(group("first_group"), contextual, temporary, group("supporter_group")));
        var result = harness.adapter(() -> true).project(request(harness.playerId,
                Set.of("first_group", "second_group"), Optional.of("second_group")))
                .toCompletableFuture().join();
        assertFalse(result.isSuccess());
        assertEquals("luckperms.membership.ambiguous", result.errors().getFirst().code());
        assertTrue(harness.nodes.contains(contextual));
        assertTrue(harness.nodes.contains(temporary));
        assertTrue(harness.inheritanceGroups().contains("supporter_group"));
        assertEquals(0, harness.userSaves.get());
    }

    @Test
    @DisplayName("[A61] Offline users use load/save/cleanup and save failure remains uncertain")
    void offlineSaveFailureIsUncertain() {
        Harness harness = new Harness(false, Set.of("first_group", "second_group"), List.of(group("first_group")));
        harness.failSave = true;
        var result = harness.adapter(() -> true).project(request(harness.playerId,
                Set.of("first_group", "second_group"), Optional.of("second_group")))
                .toCompletableFuture().join();
        assertFalse(result.isSuccess());
        assertEquals(ErrorCategory.UNCERTAIN, result.errors().getFirst().category());
        assertEquals(1, harness.userLoads.get());
        assertEquals(1, harness.userSaves.get());
        assertEquals(1, harness.cleanups.get());
    }

    @Test
    @DisplayName("[A60][A61] Post-save verification and cleanup failure preserve post-effect uncertainty")
    void postSaveVerificationFailureIsUncertain() {
        Harness harness = new Harness(false, Set.of("first_group", "second_group"), List.of(group("first_group")));
        harness.failSnapshotAfterSave = true;
        harness.failCleanup = true;
        var result = harness.adapter(() -> true).project(request(harness.playerId,
                Set.of("first_group", "second_group"), Optional.of("second_group")))
                .toCompletableFuture().join();
        assertFalse(result.isSuccess());
        assertEquals("luckperms.user.post_save_verification_failed", result.errors().getFirst().code());
        assertEquals(ErrorCategory.UNCERTAIN, result.errors().getFirst().category());
        assertEquals(1, harness.userSaves.get());
        assertEquals(1, harness.cleanups.get());
        assertEquals(Set.of("second_group"), harness.inheritanceGroups());
    }

    @Test
    @DisplayName("[A07][A61] Provider unavailability and disable during mutation fail closed")
    void providerUnavailabilityFailsClosed() {
        Harness unavailable = new Harness(false, Set.of("first_group", "second_group"), List.of(group("first_group")));
        var absent = unavailable.adapter(() -> false).project(request(unavailable.playerId,
                Set.of("first_group", "second_group"), Optional.of("second_group")))
                .toCompletableFuture().join();
        assertFalse(absent.isSuccess());
        assertEquals(ErrorCategory.UNAVAILABLE, absent.errors().getFirst().category());
        assertEquals(0, unavailable.userLoads.get());

        Harness disabled = new Harness(false, Set.of("first_group", "second_group"), List.of(group("first_group")));
        AtomicInteger availabilityChecks = new AtomicInteger();
        BooleanSupplier disableAfterMutation = () -> availabilityChecks.incrementAndGet() <= 3;
        var interrupted = disabled.adapter(disableAfterMutation).project(request(disabled.playerId,
                Set.of("first_group", "second_group"), Optional.of("second_group")))
                .toCompletableFuture().join();
        assertFalse(interrupted.isSuccess());
        assertEquals(ErrorCategory.UNCERTAIN, interrupted.errors().getFirst().category());
        assertEquals(Set.of("first_group"), disabled.inheritanceGroups());
        assertEquals(0, disabled.userSaves.get());
    }

    @Test
    @DisplayName("[A71][A72] Full owner ladder and Prestige reset preserve every unmanaged LuckPerms node")
    void ownerLadderAndPrestigeResetPreserveEveryUnmanagedNode() {
        Node permission = permission("maddkraft.owner.fixture");
        InheritanceNode temporary = temporaryGroup("event_guest", Duration.ofMinutes(30));
        InheritanceNode contextual = contextualGroup("build_team", "server", "survival");
        Set<String> ladder = Set.of("wanderer", "curious", "dreamer", "tea_guest", "wonderlander", "madcap");
        Harness harness = new Harness(true, ladder, List.of(group("curious"), group("mad_hatter"),
                group("supporter"), temporary, contextual, permission));

        LuckPermsRankAdapter adapter = harness.adapter(() -> true);
        for (String stage : List.of("dreamer", "tea_guest", "wonderlander", "madcap")) {
            var result = adapter.project(request(harness.playerId, ladder, Optional.of(stage)))
                    .toCompletableFuture().join();
            assertTrue(result.isSuccess(), result.errors().toString());
            assertEquals(RankProjectionOutcome.APPLIED, result.value().orElseThrow().outcome());
            assertEquals(Set.of(stage, "mad_hatter", "supporter", "event_guest", "build_team"),
                    harness.inheritanceGroups());
            assertUnmanagedSurvives(harness, permission, temporary, contextual);
        }

        var prestigeReset = adapter.project(request(harness.playerId, ladder, Optional.empty()))
                .toCompletableFuture().join();
        assertTrue(prestigeReset.isSuccess(), prestigeReset.errors().toString());
        assertEquals(Set.of("mad_hatter", "supporter", "event_guest", "build_team"),
                harness.inheritanceGroups());
        assertUnmanagedSurvives(harness, permission, temporary, contextual);

        Harness reopened = new Harness(false, ladder, List.copyOf(harness.nodes));
        var afterReopen = reopened.adapter(() -> true).project(request(
                reopened.playerId, ladder, Optional.of("curious"))).toCompletableFuture().join();
        assertTrue(afterReopen.isSuccess(), afterReopen.errors().toString());
        assertEquals(Set.of("curious", "mad_hatter", "supporter", "event_guest", "build_team"),
                reopened.inheritanceGroups());
        assertUnmanagedSurvives(reopened, permission, temporary, contextual);
        assertEquals(0, harness.groupCreations.get());
        assertEquals(0, reopened.groupCreations.get());
    }

    private static void assertUnmanagedSurvives(Harness harness, Node permission,
            InheritanceNode temporary, InheritanceNode contextual) {
        assertTrue(harness.nodes.contains(permission));
        assertTrue(harness.nodes.contains(temporary));
        assertTrue(harness.nodes.contains(contextual));
        assertTrue(harness.inheritanceGroups().contains("mad_hatter"));
    }

    private static RankProjectionRequest request(UUID playerId, Set<String> managed, Optional<String> desired) {
        return new RankProjectionRequest(playerId, OperationId.random(), new ConfigRevisionId("revision_1"),
                1, managed, desired);
    }

    private static InheritanceNode group(String groupName) {
        return inheritance(groupName, Map.of(), Optional.empty());
    }

    private static InheritanceNode contextualGroup(String groupName, String key, String value) {
        return inheritance(groupName, Map.of(key, Set.of(value)), Optional.empty());
    }

    private static InheritanceNode temporaryGroup(String groupName, Duration duration) {
        return inheritance(groupName, Map.of(), Optional.of(CLOCK.instant().plus(duration)));
    }

    private static InheritanceNode inheritance(
            String groupName, Map<String, Set<String>> contexts, Optional<Instant> expiry) {
        ImmutableContextSet contextSet = Harness.proxy(ImmutableContextSet.class,
                (method, args, returnType) -> switch (method) {
                    case "isEmpty" -> contexts.isEmpty();
                    case "size" -> contexts.values().stream().mapToInt(Set::size).sum();
                    case "toMap" -> contexts;
                    case "toFlattenedMap" -> contexts.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey, entry -> entry.getValue().iterator().next()));
                    case "isImmutable" -> true;
                    case "immutableCopy" -> null;
                    default -> Harness.defaultValue(returnType);
                });
        return Harness.proxy(InheritanceNode.class, (method, args, returnType) -> switch (method) {
            case "getGroupName" -> groupName;
            case "getKey" -> "group." + groupName;
            case "getValue" -> true;
            case "hasExpiry" -> expiry.isPresent();
            case "getExpiry" -> expiry.orElse(null);
            case "hasExpired" -> false;
            case "getExpiryDuration" -> expiry.map(value -> Duration.between(CLOCK.instant(), value)).orElse(null);
            case "getContexts" -> contextSet;
            default -> Harness.defaultValue(returnType);
        });
    }

    private static Node permission(String key) {
        return Harness.proxy(Node.class, (method, args, returnType) -> switch (method) {
            case "getKey" -> key;
            case "getValue" -> true;
            case "hasExpiry", "hasExpired" -> false;
            case "getContexts" -> Harness.proxy(ImmutableContextSet.class,
                    (nestedMethod, nestedArgs, nestedReturn) -> "isEmpty".equals(nestedMethod)
                            ? true : Harness.defaultValue(nestedReturn));
            default -> Harness.defaultValue(returnType);
        });
    }

    private static final class Harness {
        private final UUID playerId = UUID.randomUUID();
        private final Set<String> existingGroups;
        private final LinkedHashSet<Node> nodes;
        private final AtomicBoolean loaded;
        private final AtomicInteger userLoads = new AtomicInteger();
        private final AtomicInteger userSaves = new AtomicInteger();
        private final AtomicInteger cleanups = new AtomicInteger();
        private final AtomicInteger groupCreations = new AtomicInteger();
        private final User user;
        private final UserManager userManager;
        private final GroupManager groupManager;
        private final LuckPerms luckPerms;
        private boolean failSave;
        private boolean failSnapshotAfterSave;
        private boolean failCleanup;

        private Harness(boolean initiallyLoaded, Set<String> existingGroups, Collection<Node> initialNodes) {
            this.existingGroups = Set.copyOf(existingGroups);
            this.nodes = new LinkedHashSet<>(initialNodes);
            this.loaded = new AtomicBoolean(initiallyLoaded);
            NodeMap nodeMap = proxy(NodeMap.class, (method, args, returnType) -> switch (method) {
                case "toCollection" -> {
                    if (failSnapshotAfterSave && userSaves.get() > 0) {
                        throw new IllegalStateException("post-save snapshot unavailable");
                    }
                    yield List.copyOf(nodes);
                }
                case "add" -> nodes.add((Node) args[0])
                        ? DataMutateResult.SUCCESS : DataMutateResult.FAIL_ALREADY_HAS;
                case "remove" -> nodes.remove(args[0])
                        ? DataMutateResult.SUCCESS : DataMutateResult.FAIL_LACKS;
                case "toMap" -> Map.of();
                case "clear" -> null;
                default -> defaultValue(returnType);
            });
            user = proxy(User.class, (method, args, returnType) -> switch (method) {
                case "getUniqueId" -> playerId;
                case "getUsername", "getFriendlyName" -> "Fixture Player";
                case "data", "transientData", "getData" -> nodeMap;
                case "getNodes", "getDistinctNodes" -> Set.copyOf(nodes);
                case "getPrimaryGroup" -> "default";
                default -> defaultValue(returnType);
            });
            userManager = proxy(UserManager.class, (method, args, returnType) -> switch (method) {
                case "isLoaded" -> loaded.get();
                case "loadUser" -> {
                    userLoads.incrementAndGet();
                    loaded.set(true);
                    yield CompletableFuture.completedFuture(user);
                }
                case "getUser" -> loaded.get() ? user : null;
                case "saveUser" -> {
                    userSaves.incrementAndGet();
                    yield failSave ? CompletableFuture.failedFuture(new IllegalStateException("save unavailable"))
                            : CompletableFuture.completedFuture(null);
                }
                case "cleanupUser" -> {
                    cleanups.incrementAndGet();
                    loaded.set(false);
                    if (failCleanup) {
                        throw new IllegalStateException("cleanup unavailable");
                    }
                    yield null;
                }
                case "getLoadedUsers" -> loaded.get() ? Set.of(user) : Set.of();
                default -> defaultValue(returnType);
            });
            groupManager = proxy(GroupManager.class, (method, args, returnType) -> switch (method) {
                case "loadGroup" -> CompletableFuture.completedFuture(
                        existingGroups.contains(args[0]) ? Optional.of(groupProxy((String) args[0])) : Optional.empty());
                case "createAndLoadGroup" -> {
                    groupCreations.incrementAndGet();
                    yield CompletableFuture.completedFuture(groupProxy((String) args[0]));
                }
                case "getLoadedGroups" -> Set.of();
                case "isLoaded" -> existingGroups.contains(args[0]);
                default -> defaultValue(returnType);
            });
            luckPerms = proxy(LuckPerms.class, (method, args, returnType) -> switch (method) {
                case "getUserManager" -> userManager;
                case "getGroupManager" -> groupManager;
                case "getServerName" -> "fixture";
                default -> defaultValue(returnType);
            });
        }

        private LuckPermsRankAdapter adapter(BooleanSupplier available) {
            return new LuckPermsRankAdapter(luckPerms, "5.5-fixture", available, CLOCK,
                    LuckPermsRankAdapterTest::group);
        }

        private Set<String> inheritanceGroups() {
            return nodes.stream().filter(InheritanceNode.class::isInstance).map(InheritanceNode.class::cast)
                    .map(InheritanceNode::getGroupName).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        private static Group groupProxy(String name) {
            return proxy(Group.class, (method, args, returnType) -> "getName".equals(method) ? name
                    : defaultValue(returnType));
        }

        private static <T> T proxy(Class<T> type, Invocation invocation) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                    (target, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "equals" -> target == args[0];
                                case "hashCode" -> System.identityHashCode(target);
                                case "toString" -> type.getSimpleName() + "Fixture";
                                default -> null;
                            };
                        }
                        return invocation.invoke(
                                method.getName(), args == null ? new Object[0] : args, method.getReturnType());
                    }));
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                if (type == Optional.class) {
                    return Optional.empty();
                }
                if (type == Set.class) {
                    return Set.of();
                }
                if (type == List.class || type == Collection.class) {
                    return List.of();
                }
                if (type == CompletableFuture.class) {
                    return CompletableFuture.completedFuture(null);
                }
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == int.class || type == short.class || type == byte.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            return null;
        }
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args, Class<?> returnType) throws Throwable;
    }
}
