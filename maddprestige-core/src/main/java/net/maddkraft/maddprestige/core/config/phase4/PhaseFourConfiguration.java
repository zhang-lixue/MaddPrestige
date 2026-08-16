package net.maddkraft.maddprestige.core.config.phase4;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.CurrencyId;
import net.maddkraft.maddprestige.api.id.EntitlementId;
import net.maddkraft.maddprestige.api.id.MilestoneId;
import net.maddkraft.maddprestige.api.id.SeasonId;
import net.maddkraft.maddprestige.core.competition.CompetitionConfiguration;
import net.maddkraft.maddprestige.core.currency.CurrencyDefinition;
import net.maddkraft.maddprestige.core.entitlement.EntitlementDefinition;
import net.maddkraft.maddprestige.core.milestone.MilestoneDefinition;
import net.maddkraft.maddprestige.core.season.SeasonDefinition;

public record PhaseFourConfiguration(
        int schemaVersion,
        PrestigeConfiguration prestige,
        Map<CurrencyId, CurrencyDefinition> currencies,
        Map<EntitlementId, EntitlementDefinition> entitlements,
        Map<MilestoneId, MilestoneDefinition> milestones,
        Map<SeasonId, SeasonDefinition> seasons,
        CompetitionConfiguration competition) {
    public PhaseFourConfiguration {
        if (schemaVersion != 4) {
            throw new IllegalArgumentException("Phase 4 schema version must be 4");
        }
        prestige = Objects.requireNonNull(prestige, "Prestige configuration");
        currencies = Map.copyOf(Objects.requireNonNull(currencies, "currencies"));
        entitlements = Map.copyOf(Objects.requireNonNull(entitlements, "entitlements"));
        milestones = Map.copyOf(Objects.requireNonNull(milestones, "milestones"));
        seasons = Map.copyOf(Objects.requireNonNull(seasons, "seasons"));
        competition = Objects.requireNonNull(competition, "competition");
        currencies.forEach((id, value) -> requireMatching(id, value.id(), "currency"));
        entitlements.forEach((id, value) -> requireMatching(id, value.id(), "entitlement"));
        milestones.forEach((id, value) -> requireMatching(id, value.id(), "milestone"));
        seasons.forEach((id, value) -> requireMatching(id, value.id(), "season"));
        if (competition.enabled()) {
            throw new IllegalArgumentException("Competition is unsupported in Phase 4 and must remain disabled");
        }
    }

    private static void requireMatching(Object key, Object id, String type) {
        if (!key.equals(id)) {
            throw new IllegalArgumentException("Configured " + type + " map key differs from immutable ID");
        }
    }

    public static PhaseFourConfiguration empty() {
        return new PhaseFourConfiguration(4, PrestigeConfiguration.disabled(), Map.of(), Map.of(), Map.of(),
                Map.of(), CompetitionConfiguration.disabled());
    }
}
