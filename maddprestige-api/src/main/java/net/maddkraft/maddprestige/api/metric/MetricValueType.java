package net.maddkraft.maddprestige.api.metric;

public enum MetricValueType {
    INTEGER,
    EXACT_DECIMAL,
    DURATION,
    BOOLEAN,
    STRING,
    ENUM,
    COUNT,
    CURRENCY_AMOUNT;

    public boolean isNumeric() {
        return this == INTEGER || this == EXACT_DECIMAL || this == DURATION
                || this == COUNT || this == CURRENCY_AMOUNT;
    }

    public boolean isDiscrete() {
        return this == INTEGER || this == COUNT || this == DURATION;
    }
}
