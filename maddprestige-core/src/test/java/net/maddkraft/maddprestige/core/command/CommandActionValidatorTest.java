package net.maddkraft.maddprestige.core.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.action.ActionExecutionStatus;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardFailurePolicy;
import net.maddkraft.maddprestige.api.reward.RewardRepeatability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommandActionValidatorTest {
    private static final ConfigRevisionId REVISION = new ConfigRevisionId("command-action-test");
    private final CommandActionValidator validator = new CommandActionValidator();

    @Test
    @DisplayName("[A55] Command actions are disabled by default and dangerous normalized roots always lose precedence")
    void blocksDefaultsAndCriticalRoots() {
        assertTrue(validator.validateAndPlan(CommandActionPolicy.safeDefaults(), List.of(), 0).report().hasErrors());
        for (String root : List.of("stop", "restart", "op", "deop", "/minecraft:op")) {
            CommandActionPolicy policy = policy(root, root + " Player", Set.of());
            var result = validator.validateAndPlan(policy,
                    List.of(new CommandActionRequest("action", Map.of())), 0);
            assertTrue(result.report().hasErrors(), root);
            assertTrue(result.plans().isEmpty(), root);
        }
    }

    @Test
    @DisplayName("[A55] Only configured templates, roots, tokens, lengths, counts, and trigger depth can plan")
    void validatesAllowlistTemplatesAndBounds() {
        CommandActionPolicy allowed = policy("say", "say {player_name}", Set.of("player_name"));
        var result = validator.validateAndPlan(allowed,
                List.of(new CommandActionRequest("action", Map.of("player_name", "Player_1"))), 0);
        assertFalse(result.report().hasErrors(), result.report().toString());
        assertEquals("say Player_1", result.plans().getFirst().command());
        assertTrue(result.plans().getFirst().externalUncertaintyPossible());

        assertTrue(validator.validateAndPlan(allowed,
                List.of(new CommandActionRequest("missing", Map.of())), 0).report().hasErrors());

        CommandActionPolicy unknownRoot = new CommandActionPolicy(true, Set.of("give"), Set.of(), Set.of(),
                Map.of("action", new CommandTemplate("action", "say hello", Set.of())), 1, 64, 0);
        assertTrue(validator.validateConfiguration(unknownRoot).hasErrors());

        CommandActionPolicy maxOne = new CommandActionPolicy(true, Set.of("say"), Set.of(), Set.of(),
                Map.of("action", new CommandTemplate("action", "say hello", Set.of())), 1, 64, 0);
        assertTrue(validator.validateAndPlan(maxOne, List.of(
                new CommandActionRequest("action", Map.of()), new CommandActionRequest("action", Map.of())), 0)
                .report().hasErrors());
        assertTrue(validator.validateAndPlan(maxOne,
                List.of(new CommandActionRequest("action", Map.of())), 1).report().hasErrors());
    }

    @Test
    @DisplayName("[A55] Unresolved, newline, control, chaining, unsafe token, and oversized input fail closed")
    void rejectsInjectionShapes() {
        CommandActionPolicy allowed = policy("say", "say {player_name}", Set.of("player_name"));
        for (String unsafe : List.of("Player;stop", "Player name", "Player\nstop", "{nested}")) {
            assertTrue(validator.validateAndPlan(allowed,
                    List.of(new CommandActionRequest("action", Map.of("player_name", unsafe))), 0)
                    .report().hasErrors(), unsafe);
        }

        CommandActionPolicy unresolved = new CommandActionPolicy(true, Set.of("say"), Set.of(),
                Set.of("player_name"), Map.of("action", new CommandTemplate("action",
                        "say {player_name} {missing}", Set.of("player_name"))), 1, 64, 0);
        assertTrue(validator.validateConfiguration(unresolved).hasErrors());

        CommandActionPolicy newline = new CommandActionPolicy(true, Set.of("say"), Set.of(), Set.of(),
                Map.of("action", new CommandTemplate("action", "say hello\nstop", Set.of())), 1, 64, 0);
        assertTrue(validator.validateConfiguration(newline).hasErrors());
        CommandActionPolicy chained = new CommandActionPolicy(true, Set.of("say"), Set.of(), Set.of(),
                Map.of("action", new CommandTemplate("action", "say hello; stop", Set.of())), 1, 64, 0);
        assertTrue(validator.validateConfiguration(chained).hasErrors());

        CommandActionPolicy oversized = new CommandActionPolicy(true, Set.of("say"), Set.of(), Set.of(),
                Map.of("action", new CommandTemplate("action", "say " + "x".repeat(100), Set.of())),
                1, 16, 0);
        assertTrue(validator.validateConfiguration(oversized).hasErrors());
    }

    @Test
    @DisplayName("[A26][A55] Preview never dispatches and external uncertainty remains explicit at execution")
    void previewIsReadOnlyAndExecutionCanBeUncertain() {
        var validation = validator.validateAndPlan(policy("say", "say hello", Set.of()),
                List.of(new CommandActionRequest("action", Map.of())), 0);
        AtomicInteger dispatches = new AtomicInteger();
        CommandActionExecutor executor = new CommandActionExecutor(command -> {
            dispatches.incrementAndGet();
            return CompletableFuture.completedFuture(CommandDispatchResult.UNCERTAIN);
        });
        assertEquals(0, dispatches.get());
        var result = executor.execute(validation.plans().getFirst()).toCompletableFuture().join();
        assertEquals(net.maddkraft.maddprestige.api.action.ActionExecutionStatus.UNCERTAIN, result.status());
        assertEquals(1, dispatches.get());
    }

    @Test
    @DisplayName("[A25][A55] Production command reward provider preflights without dispatch and retains uncertainty")
    void commandRewardProviderUsesAuthorizedPlan() {
        ProviderId providerId = new ProviderId("command-rewards");
        CommandActionPolicy policy = policy("say", "say {player_uuid}", Set.of("player_uuid"));
        AtomicInteger dispatches = new AtomicInteger();
        CommandRewardProvider provider = new CommandRewardProvider(providerId, "maddprestige", policy, command -> {
            dispatches.incrementAndGet();
            return CompletableFuture.completedFuture(CommandDispatchResult.UNCERTAIN);
        }, () -> new ProviderHealth(ProviderHealthState.ACTIVE, "active", "active", Instant.EPOCH));
        RewardDefinition definition = new RewardDefinition(new RewardId("welcome"), providerId, "command",
                MetricValue.parse(MetricValueType.STRING, "action"), Map.of(), "Welcome command",
                RewardFailurePolicy.REQUIRED, RewardRepeatability.ONCE_PER_OPERATION);
        PlannedReward planned = new PlannedReward(OperationId.random(), "reward:welcome", UUID.randomUUID(),
                definition, REVISION, 1, new ActionCharacteristics(false, false, false, true), "console:say");

        assertFalse(provider.validate(definition).hasErrors());
        assertTrue(provider.preflight(planned).toCompletableFuture().join().plannedReward().isPresent());
        assertEquals(0, dispatches.get());
        assertEquals(ActionExecutionStatus.UNCERTAIN,
                provider.execute(planned).toCompletableFuture().join().status());
        assertEquals(1, dispatches.get());
    }

    @Test
    @DisplayName("[A25][A55] Command-count limit applies across all rewards in one operation batch")
    void commandRewardBatchEnforcesOperationLimit() {
        ProviderId providerId = new ProviderId("command-rewards");
        CommandActionPolicy policy = new CommandActionPolicy(true, Set.of("say"), Set.of(),
                Set.of("player_uuid"), Map.of("action", new CommandTemplate("action",
                        "say {player_uuid}", Set.of("player_uuid"))), 1, 256, 0);
        CommandRewardProvider provider = new CommandRewardProvider(providerId, "maddprestige", policy,
                command -> CompletableFuture.completedFuture(CommandDispatchResult.SUCCEEDED),
                () -> new ProviderHealth(ProviderHealthState.ACTIVE, "active", "active", Instant.EPOCH));
        RewardDefinition definition = new RewardDefinition(new RewardId("welcome"), providerId, "command",
                MetricValue.parse(MetricValueType.STRING, "action"), Map.of(), "Welcome command",
                RewardFailurePolicy.REQUIRED, RewardRepeatability.ONCE_PER_OPERATION);
        UUID playerId = UUID.randomUUID();
        OperationId operationId = OperationId.random();
        PlannedReward first = new PlannedReward(operationId, "reward:first", playerId, definition, REVISION, 1,
                provider.characteristics(definition), "console:say");
        PlannedReward second = new PlannedReward(operationId, "reward:second", playerId, definition, REVISION, 1,
                provider.characteristics(definition), "console:say");

        var preflights = provider.preflightBatch(List.of(first, second)).toCompletableFuture().join();
        assertTrue(preflights.stream().allMatch(preflight ->
                preflight.status() == net.maddkraft.maddprestige.api.action.PreflightStatus.INVALID));
    }

    @Test
    @DisplayName("[A55] Nested dispatch carries trusted depth/correlation and blocks before over-limit dispatch")
    void propagatesActualNestedExecutionDepth() {
        ProviderId providerId = new ProviderId("command-rewards");
        CommandActionPolicy policy = new CommandActionPolicy(true, Set.of("say"), Set.of(),
                Set.of(), Map.of("action", new CommandTemplate("action", "say hello", Set.of())),
                2, 256, 1);
        List<Integer> dispatchedDepths = new ArrayList<>();
        List<OperationId> correlations = new ArrayList<>();
        CommandRewardProvider[] provider = new CommandRewardProvider[1];
        PlannedReward[] reward = new PlannedReward[1];
        CommandDispatcher dispatcher = new CommandDispatcher() {
            @Override
            public java.util.concurrent.CompletionStage<CommandDispatchResult> dispatch(String command) {
                throw new AssertionError("Context-free dispatch must not be used");
            }

            @Override
            public java.util.concurrent.CompletionStage<CommandDispatchResult> dispatch(
                    String command, CommandExecutionContext context) {
                dispatchedDepths.add(context.triggerDepth());
                correlations.add(context.correlationId());
                return provider[0].execute(reward[0], context.nested()).thenApply(result ->
                        result.status() == ActionExecutionStatus.FAILED
                                ? CommandDispatchResult.FAILED : CommandDispatchResult.SUCCEEDED);
            }
        };
        provider[0] = new CommandRewardProvider(providerId, "maddprestige", policy, dispatcher,
                () -> new ProviderHealth(ProviderHealthState.ACTIVE, "active", "active", Instant.EPOCH));
        RewardDefinition definition = new RewardDefinition(new RewardId("nested"), providerId, "command",
                MetricValue.parse(MetricValueType.STRING, "action"), Map.of(), "Nested",
                RewardFailurePolicy.REQUIRED, RewardRepeatability.ONCE_PER_OPERATION);
        OperationId operationId = OperationId.random();
        reward[0] = new PlannedReward(operationId, "reward:nested", UUID.randomUUID(), definition, REVISION, 1,
                provider[0].characteristics(definition), "console:say");

        var result = provider[0].execute(reward[0]).toCompletableFuture().join();

        assertEquals(ActionExecutionStatus.FAILED, result.status());
        assertEquals(List.of(0, 1), dispatchedDepths, "depth 2 must fail before a third dispatch");
        assertTrue(correlations.stream().allMatch(operationId::equals));
    }

    private static CommandActionPolicy policy(String root, String command, Set<String> tokens) {
        return new CommandActionPolicy(true, Set.of(root.replace("/minecraft:", "")), Set.of(), tokens,
                Map.of("action", new CommandTemplate("action", command, tokens)), 2, 256, 0);
    }
}
