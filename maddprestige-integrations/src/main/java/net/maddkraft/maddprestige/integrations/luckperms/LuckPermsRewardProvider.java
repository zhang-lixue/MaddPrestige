package net.maddkraft.maddprestige.integrations.luckperms;

import java.time.Clock;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardPreflight;
import net.maddkraft.maddprestige.api.reward.RewardProvider;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

/** Active LuckPerms boundary: additive configured rewards only, never Prestige progression. */
public final class LuckPermsRewardProvider implements RewardProvider {
    public static final ProviderId PROVIDER_ID = new ProviderId("luckperms");
    private final LuckPermsIntegrationSupport support;

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
        this.support = new LuckPermsIntegrationSupport(PROVIDER_ID, luckPerms, implementationVersion,
                available, clock, inheritanceNodes, permissionNodes);
    }

    @Override
    public ProviderDescriptor descriptor() {
        return new ProviderDescriptor(PROVIDER_ID, "maddprestige", "1", support.implementationVersion(),
                support.dependencies(), support.rewardCapabilities());
    }

    @Override
    public ProviderHealth health() {
        return support.health();
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
}
