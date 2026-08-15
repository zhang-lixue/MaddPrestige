package gg.maddkraft.prestige.config;

import gg.maddkraft.prestige.model.ProgressionRank;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PluginSettingsTest {
    @Test
    void bundledConfigurationLoadsEveryRequiredSystem() {
        var stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        PluginSettings settings = PluginSettings.load(yaml);

        assertEquals("curious", settings.progressionGroups().get(ProgressionRank.CURIOUS));
        assertEquals(4, settings.ranks().size());
        assertEquals(25_000_000.0, settings.prestige().baseRequirements().cost());
        assertEquals(5, settings.patronTiers().size());
        assertEquals("whitequeen", settings.patronTiers().get("WHITE_QUEEN").luckPermsGroup());
        assertFalse(settings.patronTiers().containsKey("MADDHATTER"));
        assertEquals(54, settings.gui().size());
        assertEquals(53, settings.gui().slot("nav-staff", -1));
        assertEquals(Material.COMMAND_BLOCK, settings.gui().material("nav-staff", Material.AIR));
        assertEquals(38, settings.relationships().providers().size());
        assertFalse(settings.relationships().emitProgressActions());
        var essentials = settings.relationships().providers().get("essentials");
        assertEquals(2, essentials.entitlementPermissions().get("homes").minimumValue());
        assertEquals("essentials.sethome.multiple.maddkraft_{value}",
                essentials.entitlementPermissions().get("homes").nodes().getLast());
        assertEquals("quickshop.maddkraft.limit.{value}", settings.relationships().providers()
                .get("quickshop-hikari").entitlementPermissions().get("auction-listings").nodes().getFirst());
    }

    @Test
    void parsesCustomCommandsAndGenericEventHooks() {
        var stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String root = "relationships.providers.maxcrates";
        yaml.set(root + ".commands.prestige", java.util.List.of("crate give {player} prestige 1"));
        yaml.set(root + ".event-hooks.crate-open.event-class", "com.example.CrateOpenEvent");
        yaml.set(root + ".event-hooks.crate-open.player-path", "getPlayer");
        yaml.set(root + ".event-hooks.crate-open.amount-path", "getReward.getAmount");
        yaml.set(root + ".event-hooks.crate-open.progress-type", "DECREE_OBJECTIVE");
        yaml.set(root + ".event-hooks.crate-open.equals", java.util.List.of("getCrate.getName=PrestigeCrate"));
        yaml.set("relationships.providers.my_plugin.plugin", "MyPlugin");

        PluginSettings settings = PluginSettings.load(yaml);
        var maxCrates = settings.relationships().providers().get("maxcrates");
        assertEquals("crate give {player} prestige 1", maxCrates.commands().get("prestige").getFirst());
        assertEquals("getReward.getAmount", maxCrates.eventHooks().getFirst().amountPath());
        assertEquals("PrestigeCrate", maxCrates.eventHooks().getFirst().equalsConditions().get("getCrate.getName"));
        assertEquals("MyPlugin", settings.relationships().providers().get("my_plugin").pluginNames().getFirst());
    }
}
