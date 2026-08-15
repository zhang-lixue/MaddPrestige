import gg.maddkraft.prestige.config.PluginSettings;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/** Phase 0 helper: demonstrates how the current parser sees patron entitlement keys. */
public final class ConfigEntitlementProbe {
    public static void main(String[] args) {
        if (args.length == 0) throw new IllegalArgumentException("Expected one or more config.yml paths");
        for (String path : args) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(path));
            var homes = yaml.getConfigurationSection("rewards.patron-permissions.homes");
            System.out.println("config=" + path);
            System.out.println("raw-top-level-keys=" + (homes == null ? "missing" : homes.getKeys(false)));
            System.out.println("parsed-homes=" + PluginSettings.load(yaml).patronEntitlements().get("homes"));
        }
    }
}
