package net.maddkraft.maddprestige.core.admin.ui;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.core.admin.config.StructuredConfigurationValue;

/** Exact server-owned inputs for a canonical GUI mutation. */
public record GuiMutationContext(
        Optional<UUID> draftId,
        Optional<String> configPath,
        Optional<String> value,
        Optional<String> objectSelector,
        Optional<StructuredConfigurationValue> structuredValue,
        Optional<ProviderId> providerId,
        Optional<String> externalGroup,
        Optional<ConfigRevisionId> rollbackRevision,
        Optional<UUID> acknowledgementId,
        Optional<String> reason) {
    public GuiMutationContext {
        draftId = Objects.requireNonNull(draftId, "draft ID");
        configPath = Objects.requireNonNull(configPath, "configuration path");
        value = Objects.requireNonNull(value, "value");
        objectSelector = Objects.requireNonNull(objectSelector, "object selector");
        structuredValue = Objects.requireNonNull(structuredValue, "structured value");
        providerId = Objects.requireNonNull(providerId, "provider ID");
        externalGroup = Objects.requireNonNull(externalGroup, "external group");
        rollbackRevision = Objects.requireNonNull(rollbackRevision, "rollback revision");
        acknowledgementId = Objects.requireNonNull(acknowledgementId, "acknowledgement ID");
        reason = Objects.requireNonNull(reason, "reason");
        configPath.ifPresent(candidate -> bounded(candidate, "configuration path"));
        value.ifPresent(candidate -> bounded(candidate, "value"));
        objectSelector.ifPresent(candidate -> bounded(candidate, "object selector"));
        externalGroup.ifPresent(candidate -> bounded(candidate, "external group"));
        reason.ifPresent(candidate -> bounded(candidate, "reason"));
    }

    public static GuiMutationContext draft(UUID draftId) {
        return new GuiMutationContext(Optional.of(draftId), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty());
    }

    public GuiMutationContext(
            Optional<UUID> draftId,
            Optional<String> configPath,
            Optional<String> value,
            Optional<ProviderId> providerId,
            Optional<String> externalGroup,
            Optional<ConfigRevisionId> rollbackRevision,
            Optional<UUID> acknowledgementId,
            Optional<String> reason) {
        this(draftId, configPath, value, Optional.empty(), Optional.empty(), providerId, externalGroup,
                rollbackRevision, acknowledgementId, reason);
    }

    public static GuiMutationContext structured(
            UUID draftId,
            String path,
            Optional<String> selector,
            Optional<StructuredConfigurationValue> value) {
        return new GuiMutationContext(Optional.of(draftId), Optional.of(path), Optional.empty(), selector, value,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static void bounded(String value, String label) {
        if (value.isBlank() || value.length() > 512 || value.codePoints().anyMatch(character ->
                Character.isISOControl(character) && character != '\t')) {
            throw new IllegalArgumentException("GUI " + label + " must contain 1-512 safe characters");
        }
    }
}
