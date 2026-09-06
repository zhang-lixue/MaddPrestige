package net.maddkraft.maddprestige.core.admin.config;

/** Bounded, schema-owned scaling parameters supported by the guided Staff editor. */
public enum GuidedScalingParameter {
    LINEAR_BASE("base"),
    LINEAR_INCREMENT("rate");

    private final String yamlField;

    GuidedScalingParameter(String yamlField) {
        this.yamlField = yamlField;
    }

    String yamlField() {
        return yamlField;
    }
}
