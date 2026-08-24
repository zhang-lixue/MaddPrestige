package net.maddkraft.maddprestige.core.admin.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SetupWizardBuiltInMappingTest {
    @Test
    void everyIntegrationBackedSetupSelectionHasOneExactIntegrationRule() {
        assertEquals(Map.ofEntries(
                Map.entry("requirement:vault_balance:balance", "enable:vault"),
                Map.entry("requirement:mcmmo:power_level", "enable:mcmmo"),
                Map.entry("requirement:mcmmo:skill_level", "reject:mcmmo:skill filter"),
                Map.entry("requirement:phase5_events:mcmmo_adjusted_xp_total", "enable:mcmmo"),
                Map.entry("requirement:griefprevention_claims:remaining_claim_blocks", "enable:griefprevention"),
                Map.entry("requirement:griefprevention_claims:accrued_claim_blocks", "enable:griefprevention"),
                Map.entry("requirement:griefprevention_claims:bonus_claim_blocks", "enable:griefprevention"),
                Map.entry("requirement:griefprevention_claims:owned_claim_count", "enable:griefprevention"),
                Map.entry("requirement:placeholder_input:*",
                        "reject:placeholderapi:placeholder, value type, and maximum age"),
                Map.entry("requirement:worldguard_region:inside_region", "reject:worldguard:region-id filter"),
                Map.entry("requirement:craftengine_item_count:item_count", "reject:craftengine:item-id filter"),
                Map.entry("cost:vault_economy_cost", "enable:vault"),
                Map.entry("reward:vault_economy_reward", "enable:vault"),
                Map.entry("reward:griefprevention_claim_blocks_reward", "enable:griefprevention"),
                Map.entry("reward:craftengine_item_reward", "reject:craftengine:item-id metadata")),
                SetupWizardService.builtInSetupSelectionAudit());
    }
}
