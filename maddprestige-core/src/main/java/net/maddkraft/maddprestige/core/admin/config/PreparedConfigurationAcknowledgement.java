package net.maddkraft.maddprestige.core.admin.config;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.core.config.ContentHash;

public record PreparedConfigurationAcknowledgement(
        UUID acknowledgementId,
        UUID draftId,
        long draftVersion,
        ContentHash candidateHash,
        ConfigurationApplyKind kind,
        List<ValidationFinding> findings,
        Instant expiresAt) {
    public PreparedConfigurationAcknowledgement {
        acknowledgementId = Objects.requireNonNull(acknowledgementId, "acknowledgement ID");
        draftId = Objects.requireNonNull(draftId, "draft ID");
        candidateHash = Objects.requireNonNull(candidateHash, "candidate hash");
        kind = Objects.requireNonNull(kind, "apply kind");
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        expiresAt = Objects.requireNonNull(expiresAt, "expiry");
        if (draftVersion < 1 || findings.isEmpty()) {
            throw new IllegalArgumentException("Prepared acknowledgement requires a version and exact findings");
        }
    }
}
