package net.maddkraft.maddprestige.core.admin.setup;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationPreview;

public record SetupPreview(
        UUID sessionId,
        UUID draftId,
        ConfigurationPreview configuration,
        List<String> playerExperience) {
    public SetupPreview {
        sessionId = Objects.requireNonNull(sessionId, "session ID");
        draftId = Objects.requireNonNull(draftId, "draft ID");
        configuration = Objects.requireNonNull(configuration, "configuration preview");
        playerExperience = List.copyOf(Objects.requireNonNull(playerExperience, "player experience"));
    }
}
