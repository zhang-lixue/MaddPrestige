package net.maddkraft.maddprestige.core.legacy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

public final class LegacyStageDetector {
    private static final LoadSettings SETTINGS = LoadSettings.builder()
            .setLabel("legacy stage detection")
            .setAllowDuplicateKeys(false)
            .setAllowRecursiveKeys(false)
            .setAllowNonScalarKeys(false)
            .setMaxAliasesForCollections(50)
            .setCodePointLimit(4 * 1024 * 1024)
            .build();

    public LegacyStageDetection detect(String source, Collection<String> storedRankValues) {
        LinkedHashSet<String> values = new LinkedHashSet<>(storedRankValues);
        ArrayList<ValidationFinding> findings = new ArrayList<>();
        try {
            Object loaded = new Load(SETTINGS).loadFromString(source);
            if (loaded instanceof Map<?, ?> root) {
                Object fixedRanks = root.get("ranks");
                if (fixedRanks instanceof Map<?, ?> rankMap) {
                    rankMap.keySet().stream().filter(String.class::isInstance).map(String.class::cast)
                            .forEach(values::add);
                    findings.add(finding("legacy.stage.fixed_rank_config", "legacy.ranks",
                            "A fixed-rank configuration shape was detected.",
                            "Use an explicit mapping manifest to immutable V2 stage IDs."));
                }
                if (root.containsKey("patrons") || root.containsKey("patron-tiers")) {
                    findings.add(finding("legacy.stage.parallel_identity", "legacy",
                            "Legacy parallel identity configuration was detected and is not progression state.",
                            "Keep supporter/staff identities outside the progression mapping manifest."));
                }
            } else {
                findings.add(finding("legacy.config.unrecognized", "legacy",
                        "Legacy input is not a top-level YAML mapping.",
                        "Quarantine the input and supply a recognized read-only fixture."));
            }
        } catch (RuntimeException exception) {
            findings.add(finding("legacy.config.invalid", "legacy",
                    "Legacy YAML could not be parsed read-only: " + exception.getMessage(),
                    "Preserve the source and correct or quarantine it; do not guess mappings."));
        }
        if (!storedRankValues.isEmpty()) {
            findings.add(finding("legacy.stage.stored_values", "legacy.player-state",
                    "Legacy stored rank values require explicit mapping: " + storedRankValues.size() + " distinct input(s).",
                    "Map every distinct opaque value to one enabled immutable V2 stage ID."));
        }
        return new LegacyStageDetection(RevisionHasher.hashText(source), Set.copyOf(values),
                ValidationReport.of(findings));
    }

    private static ValidationFinding finding(String code, String path, String explanation, String remediation) {
        return new ValidationFinding(code, ValidationSeverity.ACKNOWLEDGEMENT_REQUIRED, path, explanation,
                "Detection is read-only; no configuration, group, database, or player state is changed.", remediation);
    }
}
