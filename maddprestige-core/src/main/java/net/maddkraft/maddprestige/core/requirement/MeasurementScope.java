package net.maddkraft.maddprestige.core.requirement;

import net.maddkraft.maddprestige.api.metric.MetricReadMode;

public enum MeasurementScope {
    ABSOLUTE,
    LIFETIME,
    SINCE_STAGE_START,
    SINCE_PRESTIGE_START,
    SINCE_SEASON_START;

    public boolean requiresBaseline() {
        return this == SINCE_STAGE_START || this == SINCE_PRESTIGE_START || this == SINCE_SEASON_START;
    }

    public MetricReadMode readMode() {
        return this == LIFETIME ? MetricReadMode.LIFETIME : MetricReadMode.CURRENT;
    }
}
