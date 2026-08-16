package net.maddkraft.maddprestige.core.command;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardPreflight;
import net.maddkraft.maddprestige.api.reward.RewardProvider;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;

/** A deliberately narrow bridge from an authorized reward definition to one reviewed console template. */
public final class CommandRewardProvider implements RewardProvider {
    private static final UUID VALIDATION_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ActionCharacteristics CHARACTERISTICS =
            new ActionCharacteristics(false, false, false, true);
    private final ProviderId providerId;
    private final String ownerIdentity;
    private final CommandActionPolicy policy;
    private final CommandActionValidator validator;
    private final CommandActionExecutor executor;
    private final Supplier<ProviderHealth> health;

    public CommandRewardProvider(
            ProviderId providerId,
            String ownerIdentity,
            CommandActionPolicy policy,
            CommandDispatcher dispatcher,
            Supplier<ProviderHealth> health) {
        this.providerId = Objects.requireNonNull(providerId, "provider ID");
        this.ownerIdentity = Objects.requireNonNull(ownerIdentity, "owner identity");
        this.policy = Objects.requireNonNull(policy, "command policy");
        this.validator = new CommandActionValidator();
        this.executor = new CommandActionExecutor(Objects.requireNonNull(dispatcher, "dispatcher"));
        this.health = Objects.requireNonNull(health, "health supplier");
    }

    @Override
    public ActionCharacteristics characteristics(RewardDefinition definition) {
        return CHARACTERISTICS;
    }

    @Override
    public ValidationReport validate(RewardDefinition definition) {
        ArrayList<ValidationFinding> findings = new ArrayList<>();
        if (!providerId.equals(definition.providerId())) {
            findings.add(error("command.reward.provider", "Reward provider ID does not match this provider."));
        }
        if (!"command".equals(definition.type()) || definition.value().type() != MetricValueType.STRING) {
            findings.add(error("command.reward.type",
                    "Command rewards require type 'command' and a STRING template ID value."));
        }
        for (String key : definition.metadata().keySet()) {
            if (!key.startsWith("token.")) {
                findings.add(error("command.reward.metadata", "Unsupported command reward metadata key: " + key));
            }
        }
        if (findings.isEmpty()) {
            findings.addAll(validatePlan(definition, VALIDATION_PLAYER, 0).report().findings());
        }
        return ValidationReport.of(findings);
    }

    @Override
    public CompletionStage<RewardPreflight> preflight(PlannedReward proposed) {
        if (!usable(health())) {
            return CompletableFuture.completedFuture(RewardPreflight.unavailable(
                    "Command reward provider is not available"));
        }
        ValidationReport definitionValidation = validate(proposed.definition());
        if (definitionValidation.hasErrors()) {
            return CompletableFuture.completedFuture(RewardPreflight.invalid(
                    "Command reward definition failed closed: " + firstError(definitionValidation)));
        }
        CommandActionValidation action = validatePlan(proposed.definition(), proposed.playerId(), 0);
        return CompletableFuture.completedFuture(action.report().hasErrors()
                ? RewardPreflight.invalid("Command reward plan failed closed: " + firstError(action.report()))
                : RewardPreflight.ready(proposed));
    }

    @Override
    public CompletionStage<List<RewardPreflight>> preflightBatch(List<PlannedReward> proposed) {
        List<PlannedReward> immutable = List.copyOf(proposed);
        if (!usable(health())) {
            return CompletableFuture.completedFuture(immutable.stream()
                    .map(ignored -> RewardPreflight.unavailable("Command reward provider is not available"))
                    .toList());
        }
        ArrayList<CommandActionRequest> requests = new ArrayList<>();
        for (PlannedReward reward : immutable) {
            ValidationReport report = validate(reward.definition());
            if (report.hasErrors()) {
                String detail = "Command reward definition failed closed: " + firstError(report);
                return CompletableFuture.completedFuture(immutable.stream()
                        .map(ignored -> RewardPreflight.invalid(detail)).toList());
            }
            requests.add(request(reward.definition(), reward.playerId()));
        }
        CommandActionValidation batch = validator.validateAndPlan(policy, requests, 0);
        if (batch.report().hasErrors() || batch.plans().size() != immutable.size()) {
            String detail = "Command reward batch failed closed: " + firstError(batch.report());
            return CompletableFuture.completedFuture(immutable.stream()
                    .map(ignored -> RewardPreflight.invalid(detail)).toList());
        }
        return CompletableFuture.completedFuture(immutable.stream().map(RewardPreflight::ready).toList());
    }

    @Override
    public CompletionStage<ActionExecutionResult> execute(PlannedReward plannedReward) {
        return execute(plannedReward, CommandExecutionContext.root(plannedReward.operationId()));
    }

    public CompletionStage<ActionExecutionResult> execute(
            PlannedReward plannedReward,
            CommandExecutionContext context) {
        Objects.requireNonNull(context, "command execution context");
        if (!usable(health())) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed(
                    "Command reward provider became unavailable before execution"));
        }
        CommandActionValidation action = validatePlan(plannedReward.definition(), plannedReward.playerId(),
                context.triggerDepth());
        if (action.report().hasErrors() || action.plans().size() != 1) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed(
                    "Pinned command reward no longer validates exactly: " + firstError(action.report())));
        }
        return executor.execute(action.plans().getFirst(), context);
    }

    @Override
    public ProviderDescriptor descriptor() {
        return new ProviderDescriptor(providerId, ownerIdentity, "phase3-foundation", "core-command-reward",
                List.of(), List.of(new CapabilityDescriptor("command", "reward",
                        "Reviewed allowlisted console-command reward", Map.of(
                                "default", "disabled", "idempotent", "false", "external-uncertainty", "true"))));
    }

    @Override
    public ProviderHealth health() {
        ProviderHealth current = health.get();
        if (!policy.enabled() && usable(current)) {
            return new ProviderHealth(ProviderHealthState.INACTIVE, "command-policy-disabled",
                    "Command rewards are disabled by policy", Instant.now());
        }
        return current;
    }

    private CommandActionValidation validatePlan(RewardDefinition definition, UUID playerId, int triggerDepth) {
        return validator.validateAndPlan(policy, List.of(request(definition, playerId)), triggerDepth);
    }

    private CommandActionRequest request(RewardDefinition definition, UUID playerId) {
        LinkedHashMap<String, String> tokens = new LinkedHashMap<>();
        definition.metadata().forEach((key, value) -> {
            if (key.startsWith("token.")) {
                tokens.put(key.substring("token.".length()), value);
            }
        });
        if (policy.allowedTokens().contains("player_uuid")) {
            tokens.put("player_uuid", playerId.toString());
        }
        return new CommandActionRequest(definition.value().canonical(), tokens);
    }

    private static boolean usable(ProviderHealth value) {
        return value.state() == ProviderHealthState.AVAILABLE || value.state() == ProviderHealthState.ACTIVE;
    }

    private static String firstError(ValidationReport report) {
        return report.findings().stream().filter(finding -> finding.severity() == ValidationSeverity.ERROR)
                .map(ValidationFinding::explanation).findFirst().orElse("unknown validation error");
    }

    private static ValidationFinding error(String code, String explanation) {
        return new ValidationFinding(code, ValidationSeverity.ERROR, "rewards.command",
                explanation, "The command reward cannot enter an operation plan.",
                "Use an enabled, allowlisted, fully token-validated command template.");
    }
}
