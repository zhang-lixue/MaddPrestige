package net.maddkraft.maddprestige.core.admin.config;

import java.util.Objects;

/** Exact requirement/cost ownership used by the lossless guided Money editor. */
public record GuidedMoneyScalingPair(
        String requirementId,
        String costId,
        String requirementScalingPath,
        String costAmountPath,
        String costBaseAmount,
        StructuredConfigurationValue costScalingProfile) {
    public GuidedMoneyScalingPair {
        requirementId = safeIdentifier(requirementId, "requirement ID");
        costId = safeIdentifier(costId, "cost ID");
        requirementScalingPath = nonBlank(requirementScalingPath, "requirement scaling path");
        costAmountPath = nonBlank(costAmountPath, "cost amount path");
        costBaseAmount = nonBlank(costBaseAmount, "cost base amount");
        costScalingProfile = Objects.requireNonNull(costScalingProfile, "cost scaling profile");
    }

    public String costScalingPath() {
        return "prestige.cost-scaling." + costId;
    }

    private static String safeIdentifier(String value, String name) {
        value = nonBlank(value, name);
        if (!value.matches("[a-z0-9][a-z0-9_-]{0,127}")) {
            throw new IllegalArgumentException(name + " is not a safe canonical identifier");
        }
        return value;
    }

    private static String nonBlank(String value, String name) {
        value = Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
