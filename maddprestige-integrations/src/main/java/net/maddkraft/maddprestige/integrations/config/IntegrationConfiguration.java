package net.maddkraft.maddprestige.integrations.config;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.metric.MetricValueType;

/** Canonical, dormant-by-default integration integration configuration. */
public record IntegrationConfiguration(
        int schemaVersion,
        boolean vaultEnabled,
        boolean mcMmoEnabled,
        boolean placeholderOutputEnabled,
        Map<String, PlaceholderInput> placeholderInputs,
        boolean economyShopGuiCompatibilityEnabled,
        boolean economyShopGuiProgressionCreditEnabled,
        boolean quickShopCompatibilityEnabled,
        boolean quickShopProgressionCreditEnabled,
        boolean griefPreventionEnabled,
        boolean worldGuardEnabled,
        boolean craftEngineEnabled,
        int craftEngineRewardMaximumQuantity) {
    public IntegrationConfiguration {
        if (schemaVersion != 5 && schemaVersion != 7) {
            throw new IllegalArgumentException("Integration schema version must be 5 or 7");
        }
        placeholderInputs = Map.copyOf(Objects.requireNonNull(placeholderInputs, "placeholder inputs"));
        if (economyShopGuiProgressionCreditEnabled) {
            throw new IllegalArgumentException(
                    "EconomyShopGUI progression credit is unsupported because economy units are not interchangeable");
        }
        if (quickShopProgressionCreditEnabled) {
            throw new IllegalArgumentException("QuickShop progression credit is unsupported; compatibility is zero-credit");
        }
        if (craftEngineRewardMaximumQuantity < 1 || craftEngineRewardMaximumQuantity > 2304) {
            throw new IllegalArgumentException("CraftEngine reward maximum quantity must be between 1 and 2304");
        }
    }

    public static IntegrationConfiguration disabled() {
        return new IntegrationConfiguration(5, false, false, false, Map.of(), false, false, false, false,
                false, false, false, 2304);
    }

    public record PlaceholderInput(String placeholder, MetricValueType valueType, Duration maximumAge) {
        public PlaceholderInput {
            placeholder = Objects.requireNonNull(placeholder, "placeholder");
            valueType = Objects.requireNonNull(valueType, "value type");
            maximumAge = Objects.requireNonNull(maximumAge, "maximum age");
            if (placeholder.isBlank() || maximumAge.isZero() || maximumAge.isNegative()) {
                throw new IllegalArgumentException("Placeholder and positive maximum age are required");
            }
            if (referencesMaddPrestigeOutput(placeholder)) {
                throw new IllegalArgumentException("Placeholder input cannot recursively consume MaddPrestige output");
            }
        }

        static boolean referencesMaddPrestigeOutput(String value) {
            int searchFrom = 0;
            while (searchFrom < value.length()) {
                int open = value.indexOf('%', searchFrom);
                if (open < 0) {
                    return false;
                }
                int close = value.indexOf('%', open + 1);
                if (close < 0) {
                    return false;
                }
                String token = value.substring(open + 1, close);
                int parameterSeparator = token.indexOf('_');
                String identifier = parameterSeparator < 0 ? token : token.substring(0, parameterSeparator);
                if ("maddprestige".equalsIgnoreCase(identifier)) {
                    return true;
                }
                searchFrom = close + 1;
            }
            return false;
        }
    }
}
