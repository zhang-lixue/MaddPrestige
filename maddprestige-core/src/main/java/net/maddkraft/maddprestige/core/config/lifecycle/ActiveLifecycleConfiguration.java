package net.maddkraft.maddprestige.core.config.lifecycle;

import java.util.Objects;
import net.maddkraft.maddprestige.core.stage.ActiveStageConfiguration;

public record ActiveLifecycleConfiguration(
        ActiveStageConfiguration prerequisites,
        LifecycleConfigurationSnapshot lifecycle) {
    public ActiveLifecycleConfiguration {
        prerequisites = Objects.requireNonNull(prerequisites, "prior configuration");
        lifecycle = Objects.requireNonNull(lifecycle, "lifecycle configuration");
        if (!prerequisites.stages().revisionId().equals(lifecycle.revisionId())) {
            throw new IllegalArgumentException("All active configuration snapshots must share one revision");
        }
        var retainedPins = lifecycle.providerGenerations();
        prerequisites.progression().providerGenerations().forEach((provider, generation) -> {
            if (!generation.equals(retainedPins.get(provider))) {
                throw new IllegalArgumentException("Lifecycle snapshot must retain every prior provider pin exactly");
            }
        });
    }
}
