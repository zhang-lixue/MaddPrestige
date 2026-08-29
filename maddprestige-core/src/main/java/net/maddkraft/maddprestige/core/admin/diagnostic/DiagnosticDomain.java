package net.maddkraft.maddprestige.core.admin.diagnostic;

/** Phase 6 Doctor coverage domains; absence is reported instead of implied healthy. */
public enum DiagnosticDomain {
    ACTIVE_CONFIGURATION("active-configuration"),
    SCHEMA_VERSION("schema-version"),
    PROVIDER_HEALTH("provider-health"),
    REQUIRED_METRICS("required-metrics"),
    COST_PROVIDERS("cost-providers"),
    REWARD_PROVIDERS("reward-providers"),
    PENDING_OPERATIONS("pending-operations"),
    OPERATION_RECONCILIATION("operation-reconciliation"),
    CONFIGURATION_HISTORY("configuration-history"),
    DATABASE_MIGRATIONS("database-migrations"),
    ORPHANED_PLAYER_STATE("orphaned-player-state"),
    IMMUTABLE_ID_INTEGRITY("immutable-id-integrity"),
    ENTITLEMENT_INTEGRITY("entitlement-integrity"),
    PLACEHOLDER_API("placeholder-api"),
    SCHEDULER_CACHE("scheduler-cache"),
    FLUSH_RECONCILIATION("flush-reconciliation"),
    INTEGRATION_CAPABILITY("integration-capability");

    private final String pathSegment;

    DiagnosticDomain(String pathSegment) {
        this.pathSegment = pathSegment;
    }

    public String pathSegment() {
        return pathSegment;
    }
}
