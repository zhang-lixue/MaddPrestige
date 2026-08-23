package net.maddkraft.maddprestige.api.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ProviderId;

/**
 * Immutable machine-readable summary of the effects sealed into an eligible or blocked canonical operation plan.
 * IDs are configuration identities, not rendered labels; no cost, reward, or projection is executed by this view.
 *
 * @param costIds ordered configured cost identities, at most 128
 * @param rewardIds ordered configured reward identities, at most 128
 * @param rankProjectionProvider provider selected for an external projection, or empty for no projection
 */
public record OperationSimulationView(
        List<String> costIds,
        List<String> rewardIds,
        Optional<ProviderId> rankProjectionProvider) {
    public OperationSimulationView {
        costIds = boundedIds(costIds, "cost IDs");
        rewardIds = boundedIds(rewardIds, "reward IDs");
        rankProjectionProvider = Objects.requireNonNull(rankProjectionProvider, "rank projection provider");
    }

    private static List<String> boundedIds(List<String> values, String name) {
        values = List.copyOf(Objects.requireNonNull(values, name));
        if (values.size() > 128 || values.stream().anyMatch(value -> value == null
                || !value.matches("[a-z0-9][a-z0-9._-]{0,63}"))) {
            throw new IllegalArgumentException(name + " are outside stable bounds");
        }
        return values;
    }
}
