package net.maddkraft.maddprestige.core.admin.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.core.config.ContentHash;

/** Exact durable authority acquired for one destructive configuration transition. */
public record ConfigurationStageTransitionExecution(
        ConfigRevisionId configurationRevision,
        Optional<ConfigRevisionId> priorRevision,
        ContentHash candidateHash,
        Map<StageId, ConfigurationStageReservationKind> reservedStages,
        Optional<StageRemapExecution> remapExecution) {
    public ConfigurationStageTransitionExecution {
        configurationRevision = Objects.requireNonNull(configurationRevision, "configuration revision");
        priorRevision = Objects.requireNonNull(priorRevision, "prior revision");
        candidateHash = Objects.requireNonNull(candidateHash, "candidate hash");
        reservedStages = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(
                reservedStages, "reserved stages")));
        remapExecution = Objects.requireNonNull(remapExecution, "remap execution");
        if (reservedStages.isEmpty()) {
            throw new IllegalArgumentException("A configuration stage transition must reserve at least one stage");
        }
    }
}
