package net.maddkraft.maddprestige.core.legacy;

import java.util.Objects;
import java.util.Set;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.core.config.ContentHash;

public record LegacyStageDetection(
        ContentHash sourceHash,
        Set<String> legacyStageValues,
        ValidationReport findings) {
    public LegacyStageDetection {
        sourceHash = Objects.requireNonNull(sourceHash, "source hash");
        legacyStageValues = Set.copyOf(Objects.requireNonNull(legacyStageValues, "legacy stage values"));
        findings = Objects.requireNonNull(findings, "findings");
    }
}
