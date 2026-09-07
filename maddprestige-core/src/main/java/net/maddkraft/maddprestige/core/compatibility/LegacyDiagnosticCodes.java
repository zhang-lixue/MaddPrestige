package net.maddkraft.maddprestige.core.compatibility;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Exact pre-GA diagnostic aliases accepted by compatibility readers. */
public final class LegacyDiagnosticCodes {
    private static final List<Alias> ALIASES = List.of(
            alias("phase3.boolean.invalid", "progression.configuration.boolean.invalid"),
            alias("phase3.decimal.invalid", "progression.configuration.decimal.invalid"),
            alias("phase3.document.type", "progression.configuration.document.type"),
            alias("phase3.enum.invalid", "progression.configuration.enum.invalid"),
            alias("phase3.id.invalid", "progression.configuration.id.invalid"),
            alias("phase3.id.missing", "progression.configuration.id.missing"),
            alias("phase3.integer.invalid", "progression.configuration.integer.invalid"),
            alias("phase3.list.expected", "progression.configuration.list.expected"),
            alias("phase3.list.invalid", "progression.configuration.list.invalid"),
            alias("phase3.mapping.expected", "progression.configuration.mapping.expected"),
            alias("phase3.metric.unavailable", "progression.configuration.metric.unavailable"),
            alias("phase3.provider", "progression.configuration.provider"),
            alias("phase3.provider.", "progression.configuration.provider."),
            alias("phase3.provider.contract", "progression.configuration.provider.contract"),
            alias("phase3.provider.unavailable", "progression.configuration.provider.unavailable"),
            alias("phase3.provider.validation_exception",
                    "progression.configuration.provider.validation_exception"),
            alias("phase3.schema.version", "progression.configuration.schema.version"),
            alias("phase3.string_map", "progression.configuration.string_map"),
            alias("phase3.string.invalid", "progression.configuration.string.invalid"),
            alias("phase3.yaml.invalid", "progression.configuration.yaml.invalid"),
            alias("phase4.boolean.invalid", "lifecycle.configuration.boolean.invalid"),
            alias("phase4.competition.unsupported", "competition.configuration.unsupported"),
            alias("phase4.configuration.invalid", "lifecycle.configuration.invalid"),
            alias("phase4.currency.invalid", "currency.configuration.invalid"),
            alias("phase4.currency.type", "currency.configuration.type"),
            alias("phase4.decimal.invalid", "lifecycle.configuration.decimal.invalid"),
            alias("phase4.document.type", "lifecycle.configuration.document.type"),
            alias("phase4.duration.invalid", "lifecycle.configuration.duration.invalid"),
            alias("phase4.entitlement.invalid", "entitlement.configuration.invalid"),
            alias("phase4.entitlement.type", "entitlement.configuration.type"),
            alias("phase4.enum.invalid", "lifecycle.configuration.enum.invalid"),
            alias("phase4.id.invalid", "lifecycle.configuration.id.invalid"),
            alias("phase4.id.missing", "lifecycle.configuration.id.missing"),
            alias("phase4.instant.invalid", "lifecycle.configuration.instant.invalid"),
            alias("phase4.integer.invalid", "lifecycle.configuration.integer.invalid"),
            alias("phase4.list.duplicate", "lifecycle.configuration.list.duplicate"),
            alias("phase4.list.expected", "lifecycle.configuration.list.expected"),
            alias("phase4.long.invalid", "lifecycle.configuration.long.invalid"),
            alias("phase4.mapping.expected", "lifecycle.configuration.mapping.expected"),
            alias("phase4.milestone.invalid", "milestone.configuration.invalid"),
            alias("phase4.milestone.metric", "milestone.configuration.metric"),
            alias("phase4.milestone.reward", "milestone.configuration.reward"),
            alias("phase4.milestone.threshold", "milestone.configuration.threshold"),
            alias("phase4.milestone.trigger.unsupported", "milestone.configuration.trigger.unsupported"),
            alias("phase4.milestone.type", "milestone.configuration.type"),
            alias("phase4.prestige.cost", "prestige.configuration.cost"),
            alias("phase4.prestige.external_reset", "prestige.configuration.external_reset"),
            alias("phase4.prestige.invalid", "prestige.configuration.invalid"),
            alias("phase4.prestige.maximum", "prestige.configuration.maximum"),
            alias("phase4.prestige.profile.unsupported", "prestige.configuration.profile.unsupported"),
            alias("phase4.prestige.requirements", "prestige.configuration.requirements"),
            alias("phase4.prestige.reward", "prestige.configuration.reward"),
            alias("phase4.provider", "lifecycle.configuration.provider"),
            alias("phase4.provider.", "lifecycle.configuration.provider."),
            alias("phase4.provider.generation", "lifecycle.configuration.provider.generation"),
            alias("phase4.provider.unavailable", "lifecycle.configuration.provider.unavailable"),
            alias("phase4.reference.invalid", "lifecycle.configuration.reference.invalid"),
            alias("phase4.requirement.metric.discovery", "requirement.configuration.metric.discovery"),
            alias("phase4.reset_policy.scoped_state_conflict", "reset_policy.scoped_state_conflict"),
            alias("phase4.reset_policy.unsupported", "reset_policy.unsupported"),
            alias("phase4.scaling", "scaling"),
            alias("phase4.scaling.ambiguous_boundary", "scaling.ambiguous_boundary"),
            alias("phase4.scaling.gap", "scaling.gap"),
            alias("phase4.scaling.invalid", "scaling.invalid"),
            alias("phase4.scaling.invalid_bounds", "scaling.invalid_bounds"),
            alias("phase4.scaling.invalid_override", "scaling.invalid_override"),
            alias("phase4.scaling.invalid_range", "scaling.invalid_range"),
            alias("phase4.scaling.overlap", "scaling.overlap"),
            alias("phase4.schema.version", "lifecycle.configuration.schema.version"),
            alias("phase4.season.catch_up.unsupported", "season.configuration.catch_up.unsupported"),
            alias("phase4.season.invalid", "season.configuration.invalid"),
            alias("phase4.season.requirement_override.unsupported",
                    "season.configuration.requirement_override.unsupported"),
            alias("phase4.season.reset_policy.missing", "season.configuration.reset_policy.missing"),
            alias("phase4.season.reset_policy.unsupported_component",
                    "season.configuration.reset_policy.unsupported_component"),
            alias("phase4.season.type", "season.configuration.type"),
            alias("phase4.string.blank", "lifecycle.configuration.string.blank"),
            alias("phase4.string.invalid", "lifecycle.configuration.string.invalid"),
            alias("phase4.yaml.invalid", "lifecycle.configuration.yaml.invalid"),
            alias("phase5.integrations.invalid", "integration.configuration.invalid"),
            alias("phase5.integrations.missing", "integration.configuration.missing"),
            alias("phase9b.cost_scaling.non_numeric", "scaling.cost.non_numeric"),
            alias("phase9b.cost_scaling.unknown", "scaling.cost.unknown"),
            alias("phase9b.reward_scaling.non_numeric", "scaling.reward.non_numeric"),
            alias("phase9b.reward_scaling.unknown", "scaling.reward.unknown"),
            alias("phase9c.scaling.incomplete_finite_coverage", "scaling.coverage.incomplete_finite"),
            alias("phase9c.scaling.incomplete_unlimited_coverage", "scaling.coverage.incomplete_unlimited"),
            alias("stage.feature.unsupported_phase2", "stage.feature.unsupported"));
    private static final Map<String, String> CANONICAL_BY_LEGACY = indexedAliases();
    private static final Map<String, String> LEGACY_BY_CANONICAL = reversedAliases();

    private LegacyDiagnosticCodes() {
    }

    public static String canonicalize(String code) {
        String value = Objects.requireNonNull(code, "diagnostic code");
        return CANONICAL_BY_LEGACY.getOrDefault(value, value);
    }

    public static Optional<String> legacyAlias(String canonicalCode) {
        return Optional.ofNullable(LEGACY_BY_CANONICAL.get(
                Objects.requireNonNull(canonicalCode, "canonical diagnostic code")));
    }

    public static List<Alias> aliases() {
        return ALIASES;
    }

    private static Alias alias(String legacyCode, String canonicalCode) {
        return new Alias(legacyCode, canonicalCode);
    }

    private static Map<String, String> indexedAliases() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Alias alias : ALIASES) {
            if (result.put(alias.legacyCode(), alias.canonicalCode()) != null) {
                throw new IllegalStateException("Duplicate legacy diagnostic code: " + alias.legacyCode());
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, String> reversedAliases() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Alias alias : ALIASES) {
            if (result.put(alias.canonicalCode(), alias.legacyCode()) != null) {
                throw new IllegalStateException("Duplicate canonical diagnostic code: " + alias.canonicalCode());
            }
        }
        return Map.copyOf(result);
    }

    public record Alias(String legacyCode, String canonicalCode) {
        public Alias {
            legacyCode = Objects.requireNonNull(legacyCode, "legacy diagnostic code");
            canonicalCode = Objects.requireNonNull(canonicalCode, "canonical diagnostic code");
        }
    }
}
