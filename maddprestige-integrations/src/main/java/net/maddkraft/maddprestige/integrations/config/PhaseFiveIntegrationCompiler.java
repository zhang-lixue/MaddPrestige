package net.maddkraft.maddprestige.integrations.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/** Strict compiler for the plugin-specific {@code integrations.yml} boundary. */
public final class PhaseFiveIntegrationCompiler {
    private static final LoadSettings SETTINGS = LoadSettings.builder()
            .setLabel("Phase 5 integrations")
            .setAllowDuplicateKeys(false)
            .setAllowRecursiveKeys(false)
            .setAllowNonScalarKeys(false)
            .setMaxAliasesForCollections(20)
            .setCodePointLimit(256 * 1024)
            .build();

    public PhaseFiveIntegrationCompilation compile(String yaml) {
        ArrayList<ValidationFinding> findings = new ArrayList<>();
        try {
            Object loaded = new Load(SETTINGS).loadFromString(yaml == null ? "" : yaml);
            if (loaded != null && !(loaded instanceof Map<?, ?>)) {
                throw wrongType("integrations", "mapping", loaded);
            }
            Map<?, ?> root = loaded instanceof Map<?, ?> map ? map : Map.of();
            rejectUnknown(root, Set.of("schema-version", "vault", "mcmmo", "placeholderapi", "economyshopgui",
                    "quickshop"), "integrations");
            int schema = integer(root, "schema-version", "integrations.schema-version", 5);
            if (schema != 5) {
                throw invalid("integrations.schema-version", "Expected schema version 5 but was " + schema);
            }
            Map<?, ?> vault = mapping(root, "vault", "integrations.vault");
            Map<?, ?> mcMmo = mapping(root, "mcmmo", "integrations.mcmmo");
            Map<?, ?> papi = mapping(root, "placeholderapi", "integrations.placeholderapi");
            Map<?, ?> output = mapping(papi, "output", "integrations.placeholderapi.output");
            Map<?, ?> economyShopGui = mapping(root, "economyshopgui", "integrations.economyshopgui");
            Map<?, ?> economyShopProgression = mapping(economyShopGui, "progression-credit",
                    "integrations.economyshopgui.progression-credit");
            Map<?, ?> quickShop = mapping(root, "quickshop", "integrations.quickshop");
            Map<?, ?> quickShopProgression = mapping(quickShop, "progression-credit",
                    "integrations.quickshop.progression-credit");
            rejectUnknown(vault, Set.of("enabled"), "integrations.vault");
            rejectUnknown(mcMmo, Set.of("enabled"), "integrations.mcmmo");
            rejectUnknown(papi, Set.of("output", "inputs"), "integrations.placeholderapi");
            rejectUnknown(output, Set.of("enabled"), "integrations.placeholderapi.output");
            rejectUnknown(economyShopGui, Set.of("compatibility-enabled", "progression-credit"),
                    "integrations.economyshopgui");
            rejectUnknown(economyShopProgression, Set.of("enabled"),
                    "integrations.economyshopgui.progression-credit");
            rejectUnknown(quickShop, Set.of("compatibility-enabled", "progression-credit"),
                    "integrations.quickshop");
            rejectUnknown(quickShopProgression, Set.of("enabled"), "integrations.quickshop.progression-credit");
            Map<String, PhaseFiveIntegrationConfiguration.PlaceholderInput> inputs = inputs(papi);
            boolean economyShopProgressionEnabled = bool(economyShopProgression, "enabled",
                    "integrations.economyshopgui.progression-credit.enabled");
            if (economyShopProgressionEnabled) {
                throw invalid("integrations.economyshopgui.progression-credit.enabled",
                        "EconomyShopGUI progression credit is unsupported because economy units are not interchangeable");
            }
            boolean quickShopProgressionEnabled = bool(quickShopProgression, "enabled",
                    "integrations.quickshop.progression-credit.enabled");
            if (quickShopProgressionEnabled) {
                throw invalid("integrations.quickshop.progression-credit.enabled",
                        "QuickShop progression credit is unsupported; compatibility is zero-credit");
            }
            PhaseFiveIntegrationConfiguration result = new PhaseFiveIntegrationConfiguration(schema,
                    bool(vault, "enabled", "integrations.vault.enabled"),
                    bool(mcMmo, "enabled", "integrations.mcmmo.enabled"),
                    bool(output, "enabled", "integrations.placeholderapi.output.enabled"), inputs,
                    bool(economyShopGui, "compatibility-enabled",
                            "integrations.economyshopgui.compatibility-enabled"),
                    economyShopProgressionEnabled,
                    bool(quickShop, "compatibility-enabled", "integrations.quickshop.compatibility-enabled"),
                    quickShopProgressionEnabled);
            return new PhaseFiveIntegrationCompilation(result, ValidationReport.VALID);
        } catch (ConfigurationException exception) {
            findings.add(new ValidationFinding("phase5.integrations.invalid", ValidationSeverity.ERROR,
                    exception.path(), exception.getMessage(), "Integration configuration cannot be activated.",
                    "Use schema-version 5 and the exact documented YAML value types at the reported path."));
            return new PhaseFiveIntegrationCompilation(PhaseFiveIntegrationConfiguration.disabled(),
                    ValidationReport.of(findings));
        } catch (RuntimeException exception) {
            findings.add(new ValidationFinding("phase5.integrations.invalid", ValidationSeverity.ERROR,
                    "integrations.yml", exception.getMessage(), "Integration configuration cannot be activated.",
                    "Use schema-version 5, documented fields, and keep QuickShop progression credit disabled."));
            return new PhaseFiveIntegrationCompilation(PhaseFiveIntegrationConfiguration.disabled(),
                    ValidationReport.of(findings));
        }
    }

    private static Map<String, PhaseFiveIntegrationConfiguration.PlaceholderInput> inputs(Map<?, ?> papi) {
        Map<?, ?> configured = mapping(papi, "inputs", "integrations.placeholderapi.inputs");
        LinkedHashMap<String, PhaseFiveIntegrationConfiguration.PlaceholderInput> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : configured.entrySet()) {
            if (!(entry.getKey() instanceof String id)) {
                String path = "integrations.placeholderapi.inputs[" + display(entry.getKey()) + "]";
                throw wrongType(path, "string metric ID key", entry.getKey());
            }
            String path = "integrations.placeholderapi.inputs[\"" + id + "\"]";
            try {
                new MetricId(id);
            } catch (IllegalArgumentException exception) {
                throw invalid(path, exception.getMessage());
            }
            Map<?, ?> fields = requiredMapping(entry.getValue(), path);
            rejectUnknown(fields, Set.of("placeholder", "value-type", "maximum-age"),
                    path);
            try {
                String placeholder = requiredString(fields, "placeholder", path + ".placeholder");
                if (PhaseFiveIntegrationConfiguration.PlaceholderInput
                        .referencesMaddPrestigeOutput(placeholder)) {
                    throw invalid(path + ".placeholder",
                            "Placeholder input cannot recursively consume MaddPrestige output");
                }
                result.put(id, new PhaseFiveIntegrationConfiguration.PlaceholderInput(
                        placeholder,
                        valueType(fields, "value-type", path + ".value-type"),
                        duration(fields, "maximum-age", path + ".maximum-age")));
            } catch (ConfigurationException exception) {
                throw exception;
            } catch (IllegalArgumentException exception) {
                throw invalid(path, exception.getMessage());
            }
        }
        return Map.copyOf(result);
    }

    private static Map<?, ?> mapping(Map<?, ?> parent, String key, String path) {
        if (!parent.containsKey(key)) {
            return Map.of();
        }
        return requiredMapping(parent.get(key), path);
    }

    private static Map<?, ?> requiredMapping(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw wrongType(path, "mapping", value);
    }

    private static boolean bool(Map<?, ?> parent, String key, String path) {
        if (!parent.containsKey(key)) {
            return false;
        }
        Object value = parent.get(key);
        if (!(value instanceof Boolean bool)) {
            throw wrongType(path, "boolean", value);
        }
        return bool;
    }

    private static int integer(Map<?, ?> parent, String key, String path, int fallback) {
        if (!parent.containsKey(key)) {
            return fallback;
        }
        Object value = parent.get(key);
        if (!(value instanceof Integer integer)) {
            throw wrongType(path, "integer", value);
        }
        return integer;
    }

    private static String requiredString(Map<?, ?> parent, String key, String path) {
        if (!parent.containsKey(key)) {
            throw invalid(path, "Required string field is absent");
        }
        Object value = parent.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw wrongType(path, "nonblank string", value);
        }
        return text;
    }

    private static MetricValueType valueType(Map<?, ?> parent, String key, String path) {
        String text = requiredString(parent, key, path);
        try {
            return MetricValueType.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(path, "Unsupported metric value type: " + text);
        }
    }

    private static Duration duration(Map<?, ?> parent, String key, String path) {
        String text = requiredString(parent, key, path);
        try {
            Duration duration = Duration.parse(text);
            if (duration.isZero() || duration.isNegative()) {
                throw invalid(path, "Duration must be positive: " + text);
            }
            return duration;
        } catch (ConfigurationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw invalid(path, "Expected an ISO-8601 duration but was: " + text);
        }
    }

    private static void rejectUnknown(Map<?, ?> fields, Set<String> allowed, String path) {
        for (Object key : fields.keySet()) {
            if (!(key instanceof String text) || !allowed.contains(text)) {
                throw invalid(path + "[" + display(key) + "]", "Unsupported configuration field");
            }
        }
    }

    private static ConfigurationException wrongType(String path, String expected, Object actual) {
        return invalid(path, "Expected " + expected + " but was " + actualType(actual));
    }

    private static ConfigurationException invalid(String path, String detail) {
        return new ConfigurationException(path, path + ": " + detail);
    }

    private static String actualType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?>) {
            return "mapping";
        }
        if (value instanceof List<?>) {
            return "sequence (" + value.getClass().getSimpleName() + ")";
        }
        return value.getClass().getSimpleName() + " (" + display(value) + ")";
    }

    private static String display(Object value) {
        return value instanceof String text ? "\"" + text + "\"" : String.valueOf(value);
    }

    private static final class ConfigurationException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String path;

        private ConfigurationException(String path, String message) {
            super(message);
            this.path = path;
        }

        private String path() {
            return path;
        }
    }
}
