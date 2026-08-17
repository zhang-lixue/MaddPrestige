package net.maddkraft.maddprestige.integrations.craftengine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardPreflight;
import net.maddkraft.maddprestige.api.reward.RewardProvider;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.integrations.IntegrationTaskScheduler;
import net.maddkraft.maddprestige.integrations.MutableProviderHealth;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Capacity-preflighted, identity-verified CraftEngine custom-item reward. */
public final class CraftEngineItemRewardProvider implements RewardProvider {
    public static final String TYPE = "custom_item";
    public static final int DEFAULT_MAXIMUM_QUANTITY = 2304;
    private static final ActionCharacteristics CHARACTERISTICS =
            new ActionCharacteristics(false, false, false, true);

    private final Server server;
    private final CraftEngineItemAccess access;
    private final IntegrationTaskScheduler scheduler;
    private final MutableProviderHealth health;
    private final int maximumQuantity;
    private final ProviderDescriptor descriptor;

    public CraftEngineItemRewardProvider(Server server, CraftEngineItemAccess access,
            IntegrationTaskScheduler scheduler, MutableProviderHealth health, int maximumQuantity,
            String detectedVersion) {
        this.server = java.util.Objects.requireNonNull(server, "server");
        this.access = java.util.Objects.requireNonNull(access, "access");
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.health = java.util.Objects.requireNonNull(health, "health");
        if (maximumQuantity < 1) {
            throw new IllegalArgumentException("CraftEngine reward maximum must be positive");
        }
        this.maximumQuantity = maximumQuantity;
        descriptor = CraftEngineProviderDescriptors.descriptor(
                CraftEngineProviderDescriptors.REWARD, "reward", detectedVersion);
    }

    @Override
    public ActionCharacteristics characteristics(RewardDefinition definition) {
        return CHARACTERISTICS;
    }

    @Override
    public ValidationReport validate(RewardDefinition definition) {
        try {
            specification(definition);
            return ValidationReport.VALID;
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return ValidationReport.of(List.of(new ValidationFinding("craftengine.reward.invalid",
                    ValidationSeverity.ERROR, "rewards." + definition.id().value(), exception.getMessage(),
                    "The CraftEngine reward cannot be planned.",
                    "Use custom_item, a fully namespaced item-id, and a positive integral COUNT within the safety maximum.")));
        }
    }

    @Override
    public CompletionStage<RewardPreflight> preflight(PlannedReward proposed) {
        return scheduler.call(() -> {
            if (!health.isUsable()) {
                return RewardPreflight.unavailable("CraftEngine binding is unavailable");
            }
            Player player = server.getPlayer(proposed.playerId());
            if (player == null || !player.isOnline()) {
                return RewardPreflight.unavailable("CraftEngine reward requires an online player");
            }
            try {
                Specification specification = specification(proposed.definition());
                List<ItemStack> stacks = buildVerified(player, specification);
                return hasCapacity(access, specification.itemId(), player.getInventory(), stacks)
                        ? RewardPreflight.ready(proposed)
                        : RewardPreflight.invalid("Player storage inventory has insufficient exact capacity");
            } catch (IllegalArgumentException exception) {
                return RewardPreflight.invalid(exception.getMessage());
            } catch (RuntimeException exception) {
                return RewardPreflight.unavailable("CraftEngine reward preflight failed");
            }
        });
    }

    @Override
    public CompletionStage<ActionExecutionResult> execute(PlannedReward plannedReward) {
        return scheduler.call(() -> executeScheduled(plannedReward));
    }

    private ActionExecutionResult executeScheduled(PlannedReward plannedReward) {
        if (!health.isUsable()) {
            return ActionExecutionResult.failed("CraftEngine binding disappeared before execution");
        }
        Player player = server.getPlayer(plannedReward.playerId());
        if (player == null || !player.isOnline()) {
            return ActionExecutionResult.failed("CraftEngine reward player is offline before execution");
        }
        Specification specification;
        List<ItemStack> stacks;
        long before;
        try {
            specification = specification(plannedReward.definition());
            stacks = buildVerified(player, specification);
            PlayerInventory inventory = player.getInventory();
            if (!hasCapacity(access, specification.itemId(), inventory, stacks)) {
                return ActionExecutionResult.failed("Player storage inventory capacity changed before execution");
            }
            before = CraftEngineItemMetricProvider.count(access, inventory.getStorageContents(),
                    specification.itemId());
        } catch (RuntimeException exception) {
            return ActionExecutionResult.failed("CraftEngine failed before inventory mutation");
        }
        try {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stacks.toArray(ItemStack[]::new));
            if (!leftovers.isEmpty()) {
                return ActionExecutionResult.uncertain(
                        "CraftEngine inventory insertion returned leftovers after possible partial mutation; no items were dropped");
            }
            long after = CraftEngineItemMetricProvider.count(access,
                    player.getInventory().getStorageContents(), specification.itemId());
            long expected = Math.addExact(before, specification.quantity());
            return after == expected ? ActionExecutionResult.applied()
                    : ActionExecutionResult.uncertain(
                            "CraftEngine post-insert identity/count verification did not match; automatic replay is forbidden");
        } catch (RuntimeException exception) {
            return ActionExecutionResult.uncertain(
                    "CraftEngine inventory may have changed; automatic replay and ground drops are forbidden");
        }
    }

    private Specification specification(RewardDefinition definition) {
        if (!CraftEngineProviderDescriptors.REWARD.equals(definition.providerId())
                || !TYPE.equals(definition.type()) || definition.value().type() != MetricValueType.COUNT
                || !definition.metadata().keySet().equals(java.util.Set.of(CraftEngineProviderDescriptors.ITEM_ID))) {
            throw new IllegalArgumentException(
                    "Reward must target craftengine_item_reward/custom_item with COUNT and exactly one item-id");
        }
        String itemId = CraftEngineProviderDescriptors.requireItemId(
                definition.metadata().get(CraftEngineProviderDescriptors.ITEM_ID));
        BigDecimal value = definition.value().asNumber();
        int quantity = value.intValueExact();
        if (quantity < 1 || quantity > maximumQuantity) {
            throw new IllegalArgumentException(
                    "CraftEngine reward quantity must be between 1 and " + maximumQuantity);
        }
        if (!access.exists(itemId)) {
            throw new IllegalArgumentException("CraftEngine item is not currently loaded: " + itemId);
        }
        return new Specification(itemId, quantity);
    }

    private List<ItemStack> buildVerified(Player player, Specification specification) {
        if (!access.exists(specification.itemId())) {
            throw new IllegalArgumentException("CraftEngine item is not currently loaded");
        }
        ItemStack probe = access.build(specification.itemId(), player, 1);
        verify(probe, specification.itemId(), 1);
        int maximumStack = probe.getMaxStackSize();
        if (maximumStack < 1) {
            throw new IllegalArgumentException("CraftEngine item has an invalid maximum stack size");
        }
        ArrayList<ItemStack> result = new ArrayList<>();
        int remaining = specification.quantity();
        while (remaining > 0) {
            int amount = Math.min(remaining, maximumStack);
            ItemStack built = access.build(specification.itemId(), player, amount);
            verify(built, specification.itemId(), amount);
            result.add(built);
            remaining -= amount;
        }
        return List.copyOf(result);
    }

    private void verify(ItemStack stack, String itemId, int amount) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() != amount
                || amount > stack.getMaxStackSize()
                || access.identify(stack).filter(itemId::equals).isEmpty()) {
            throw new IllegalArgumentException("CraftEngine built an item with mismatched identity or stack amount");
        }
    }

    static boolean hasCapacity(CraftEngineItemAccess access, String itemId, PlayerInventory inventory,
            List<ItemStack> additions) {
        ItemStack[] slots = inventory.getStorageContents().clone();
        Map<Integer, Integer> simulated = new HashMap<>();
        for (int index = 0; index < slots.length; index++) {
            simulated.put(index, slots[index] == null || slots[index].getType() == Material.AIR
                    ? 0 : slots[index].getAmount());
        }
        for (ItemStack addition : additions) {
            int remaining = addition.getAmount();
            for (int index = 0; index < slots.length && remaining > 0; index++) {
                ItemStack current = slots[index];
                int used = simulated.get(index);
                if (current != null && current.getType() != Material.AIR && current.isSimilar(addition)
                        && access.identify(current).filter(itemId::equals).isPresent()) {
                    int accepted = Math.min(remaining, Math.max(0, current.getMaxStackSize() - used));
                    simulated.put(index, used + accepted);
                    remaining -= accepted;
                }
            }
            for (int index = 0; index < slots.length && remaining > 0; index++) {
                ItemStack current = slots[index];
                if ((current == null || current.getType() == Material.AIR) && simulated.get(index) == 0) {
                    int accepted = Math.min(remaining, addition.getMaxStackSize());
                    simulated.put(index, accepted);
                    slots[index] = addition;
                    remaining -= accepted;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public ProviderHealth health() {
        return health.get();
    }

    private record Specification(String itemId, int quantity) {
    }
}
