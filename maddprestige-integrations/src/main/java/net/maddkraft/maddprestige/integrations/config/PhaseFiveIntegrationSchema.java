package net.maddkraft.maddprestige.integrations.config;

import java.util.List;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.FieldId;
import net.maddkraft.maddprestige.core.schema.AllowedValues;
import net.maddkraft.maddprestige.core.schema.PhaseFourSchema;
import net.maddkraft.maddprestige.core.schema.ReloadBehavior;
import net.maddkraft.maddprestige.core.schema.RiskLevel;
import net.maddkraft.maddprestige.core.schema.SchemaNode;
import net.maddkraft.maddprestige.core.schema.SchemaRegistry;
import net.maddkraft.maddprestige.core.schema.SchemaValueType;

/** Extends the accepted generic schema from the plugin-specific integration boundary. */
public final class PhaseFiveIntegrationSchema {
    private PhaseFiveIntegrationSchema() {
    }

    public static SchemaRegistry create() {
        SchemaRegistry registry = PhaseFourSchema.create();
        registerMap(registry, "vault_integration", "integrations.vault", RiskLevel.CRITICAL,
                "Strict Vault integration mapping; scalar, sequence and null values are rejected.");
        register(registry, "vault_integration_enabled", "integrations.vault.enabled", SchemaValueType.BOOLEAN,
                Optional.of("false"), AllowedValues.fixed("true", "false"), RiskLevel.CRITICAL,
                "Discovers the Vault cost, reward, and balance providers; reachability controls activation.");
        registerMap(registry, "mcmmo_integration", "integrations.mcmmo", RiskLevel.HIGH,
                "Strict mcMMO integration mapping; scalar, sequence and null values are rejected.");
        register(registry, "mcmmo_integration_enabled", "integrations.mcmmo.enabled", SchemaValueType.BOOLEAN,
                Optional.of("false"), AllowedValues.fixed("true", "false"), RiskLevel.HIGH,
                "Discovers read-only mcMMO level metrics and the authenticated XP event source.");
        registerMap(registry, "placeholderapi_integration", "integrations.placeholderapi", RiskLevel.HIGH,
                "Strict PlaceholderAPI integration mapping.");
        registerMap(registry, "placeholderapi_output", "integrations.placeholderapi.output", RiskLevel.LOW,
                "Strict cache-only PlaceholderAPI output mapping.");
        register(registry, "placeholder_output_enabled", "integrations.placeholderapi.output.enabled",
                SchemaValueType.BOOLEAN, Optional.of("false"), AllowedValues.fixed("true", "false"), RiskLevel.LOW,
                "Registers the cache-only MaddPrestige PlaceholderAPI expansion.");
        register(registry, "placeholder_inputs", "integrations.placeholderapi.inputs", SchemaValueType.MAP,
                Optional.empty(), AllowedValues.unrestricted(), RiskLevel.HIGH,
                "Typed scheduled PlaceholderAPI input definitions; requirement reads use snapshots only.");
        registerMap(registry, "placeholder_input", "integrations.placeholderapi.inputs.*", RiskLevel.HIGH,
                "Strict input-definition mapping keyed by a canonical string MetricId.");
        register(registry, "placeholder_input_placeholder", "integrations.placeholderapi.inputs.*.placeholder",
                SchemaValueType.STRING, Optional.empty(), AllowedValues.unrestricted(), RiskLevel.HIGH,
                "External placeholder sampled on the controlled server-thread refresh path.");
        register(registry, "placeholder_input_type", "integrations.placeholderapi.inputs.*.value-type",
                SchemaValueType.STRING, Optional.empty(), AllowedValues.fixed("INTEGER", "EXACT_DECIMAL", "DURATION",
                        "COUNT", "CURRENCY_AMOUNT", "BOOLEAN", "STRING", "ENUM"), RiskLevel.HIGH,
                "Canonical metric value type required when parsing the sample.");
        register(registry, "placeholder_input_maximum_age", "integrations.placeholderapi.inputs.*.maximum-age",
                SchemaValueType.DURATION, Optional.empty(), AllowedValues.unrestricted(), RiskLevel.HIGH,
                "Positive freshness limit after which authorization sees unavailable.");
        registerMap(registry, "economyshopgui_integration", "integrations.economyshopgui", RiskLevel.HIGH,
                "Strict EconomyShopGUI compatibility mapping.");
        register(registry, "economyshopgui_compatibility_enabled",
                "integrations.economyshopgui.compatibility-enabled", SchemaValueType.BOOLEAN, Optional.of("false"),
                AllowedValues.fixed("true", "false"), RiskLevel.LOW,
                "Enables zero-credit EconomyShopGUI event compatibility diagnostics.");
        registerMap(registry, "economyshopgui_progression_credit",
                "integrations.economyshopgui.progression-credit", RiskLevel.CRITICAL,
                "Strict unsupported-progression mapping; non-mapping values are rejected.");
        register(registry, "economyshopgui_progression_credit_enabled",
                "integrations.economyshopgui.progression-credit.enabled", SchemaValueType.BOOLEAN,
                Optional.of("false"), AllowedValues.fixed("false"), RiskLevel.CRITICAL,
                "EconomyShopGUI progression is deferred because different economy units cannot be combined.");
        registerMap(registry, "quickshop_integration", "integrations.quickshop", RiskLevel.LOW,
                "Strict QuickShop compatibility mapping.");
        register(registry, "quickshop_compatibility_enabled", "integrations.quickshop.compatibility-enabled",
                SchemaValueType.BOOLEAN, Optional.of("false"), AllowedValues.fixed("true", "false"), RiskLevel.LOW,
                "Enables zero-credit QuickShop compatibility diagnostics.");
        registerMap(registry, "quickshop_progression_credit_mapping",
                "integrations.quickshop.progression-credit", RiskLevel.CRITICAL,
                "Strict unsupported-progression mapping; non-mapping values are rejected.");
        register(registry, "quickshop_progression_credit",
                "integrations.quickshop.progression-credit.enabled", SchemaValueType.BOOLEAN, Optional.of("false"),
                AllowedValues.fixed("false"), RiskLevel.CRITICAL,
                "Player-to-player QuickShop progression credit is unsupported and rejected.");
        return registry;
    }

    private static void registerMap(
            SchemaRegistry registry, String id, String path, RiskLevel risk, String description) {
        register(registry, id, path, SchemaValueType.MAP, Optional.empty(), AllowedValues.unrestricted(), risk,
                description);
    }

    private static void register(
            SchemaRegistry registry,
            String id,
            String path,
            SchemaValueType type,
            Optional<String> defaultValue,
            AllowedValues values,
            RiskLevel risk,
            String description) {
        registry.register(new SchemaNode(new FieldId(id), path, type, defaultValue, description, List.of(), List.of(),
                values, false, risk, "maddprestige.admin.config.edit", "maddprestige.admin.config.apply",
                ReloadBehavior.HOT_RELOAD, List.of(), Optional.empty()));
    }
}
