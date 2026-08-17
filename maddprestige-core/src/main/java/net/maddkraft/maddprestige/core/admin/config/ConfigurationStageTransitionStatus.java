package net.maddkraft.maddprestige.core.admin.config;

/** Durable lifecycle of one configuration-owned unsafe-stage reservation. */
public enum ConfigurationStageTransitionStatus {
    RESERVED,
    NEEDS_RECONCILIATION,
    CONFIG_APPLIED,
    CONFIG_FAILED_SAFE;

    public boolean active() {
        return this == RESERVED || this == NEEDS_RECONCILIATION;
    }
}
