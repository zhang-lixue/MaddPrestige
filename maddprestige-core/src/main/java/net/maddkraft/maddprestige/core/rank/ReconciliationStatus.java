package net.maddkraft.maddprestige.core.rank;

public enum ReconciliationStatus {
    MATCHED,
    WARNED,
    REPAIR_REQUIRED,
    IMPORT_READY,
    AMBIGUOUS,
    MISSING_INTERNAL_STATE,
    RATE_LIMITED,
    STALE_GENERATION,
    PROVIDER_UNAVAILABLE,
    REPAIRED,
    IMPORTED,
    UNCERTAIN,
    FAILED
}
