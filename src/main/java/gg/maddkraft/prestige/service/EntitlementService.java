package gg.maddkraft.prestige.service;

import gg.maddkraft.prestige.config.PluginSettings;
import gg.maddkraft.prestige.integration.GriefPreventionBridge;
import gg.maddkraft.prestige.integration.PermissionBridge;
import gg.maddkraft.prestige.integration.IntegrationRelationshipService;
import gg.maddkraft.prestige.model.PlayerState;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Map;

public final class EntitlementService {
    private final PermissionBridge permissions;
    private final GriefPreventionBridge griefPrevention;
    private final ProfileService profiles;
    private final IntegrationRelationshipService relationships;
    private volatile PluginSettings settings;

    public EntitlementService(PermissionBridge permissions, GriefPreventionBridge griefPrevention,
                              ProfileService profiles, IntegrationRelationshipService relationships,
                              PluginSettings settings) {
        this.permissions = permissions;
        this.griefPrevention = griefPrevention;
        this.profiles = profiles;
        this.relationships = relationships;
        this.settings = settings;
    }

    public void reload(PluginSettings settings) {
        this.settings = settings;
    }

    public Entitlements calculate(Player player, PlayerState state) {
        int earnedHomes = settings.baseHomes() + state.homesPerkLevel() * settings.perks().get("homes").amountPerLevel();
        int earnedListings = settings.baseListings() + state.listingsPerkLevel() * settings.perks().get("auction-listings").amountPerLevel();
        int earnedClaims = settings.baseClaimBlocks() + state.claimPerkLevel() * settings.perks().get("claim-blocks").amountPerLevel();
        int patronHomes = highestPermissionValue(player, settings.patronEntitlements().getOrDefault("homes", Map.of()));
        int patronListings = highestPermissionValue(player, settings.patronEntitlements().getOrDefault("auction-listings", Map.of()));
        return new Entitlements(
                Math.max(earnedHomes, patronHomes),
                Math.max(earnedListings, patronListings),
                earnedClaims,
                earnedHomes,
                earnedListings
        );
    }

    public void apply(Player player, PlayerState state) {
        Entitlements value = calculate(player, state);
        permissions.updateEntitlements(player, value.homes(), value.auctionListings());
        relationships.applyEntitlements(player, Map.of(
                "homes", value.homes(),
                "auction-listings", value.auctionListings(),
                "claim-blocks", value.claimBlocks()
        ));
    }

    public ActionResult purchase(Player player, PlayerState state, String rawPerk) {
        String perk = normalize(rawPerk);
        PluginSettings.PerkDefinition definition = settings.perks().get(perk);
        if (definition == null) return ActionResult.fail("Unknown reward. Choose homes, auction-listings, or claim-blocks.");
        int level = switch (perk) {
            case "homes" -> state.homesPerkLevel();
            case "auction-listings" -> state.listingsPerkLevel();
            case "claim-blocks" -> state.claimPerkLevel();
            default -> 0;
        };
        int base = switch (perk) {
            case "homes" -> settings.baseHomes();
            case "auction-listings" -> settings.baseListings();
            case "claim-blocks" -> settings.baseClaimBlocks();
            default -> 0;
        };
        int nextValue = base + (level + 1) * definition.amountPerLevel();
        if (nextValue > definition.maximumValue()) return ActionResult.fail("That reward is already at its maximum.");
        if (state.teaLeaves() < definition.teaLeafCost()) {
            return ActionResult.fail("You need " + definition.teaLeafCost() + " Tea Leaves for that reward.");
        }

        PlayerState before = state.snapshot();
        state.spendTeaLeaves(definition.teaLeafCost());
        state.buyPerk(perk);
        boolean claimBlocksApplied = "claim-blocks".equals(perk)
                && griefPrevention.addBonusClaimBlocks(player.getUniqueId(), definition.amountPerLevel());
        if ("claim-blocks".equals(perk) && !claimBlocksApplied) {
            state.restoreFrom(before);
            return ActionResult.fail("GriefPrevention could not apply the claim blocks; no Tea Leaves were spent.");
        }
        try {
            profiles.saveNow(state);
        } catch (SQLException exception) {
            if (claimBlocksApplied) {
                griefPrevention.addBonusClaimBlocks(player.getUniqueId(), -definition.amountPerLevel());
            }
            state.restoreFrom(before);
            try { profiles.saveNow(state); } catch (SQLException ignored) {}
            return ActionResult.fail("The reward purchase was rolled back because saving failed.");
        }
        boolean followUpComplete = true;
        try {
            apply(player, state);
            relationships.trigger("perk-purchase", player, state, Map.of(
                    "perk", perk,
                    "amount", definition.amountPerLevel(),
                    "cost", definition.teaLeafCost()
            ));
        } catch (RuntimeException exception) {
            followUpComplete = false;
        }
        return ActionResult.ok("Purchased " + definition.amountPerLevel() + " " + perk + " for "
                + definition.teaLeafCost() + " Tea Leaves." + (followUpComplete ? ""
                : " Some optional permissions will be reconciled when you rejoin."));
    }

    private int highestPermissionValue(Player player, Map<String, Integer> mapping) {
        int result = 0;
        for (Map.Entry<String, Integer> entry : mapping.entrySet()) {
            if (player.hasPermission(entry.getKey())) result = Math.max(result, entry.getValue());
        }
        return result;
    }

    private String normalize(String value) {
        String normalized = value.toLowerCase().replace('_', '-');
        if (normalized.equals("listings")) return "auction-listings";
        if (normalized.equals("claims")) return "claim-blocks";
        return normalized;
    }

    public record Entitlements(int homes, int auctionListings, int claimBlocks, int earnedHomes, int earnedListings) {}
}
