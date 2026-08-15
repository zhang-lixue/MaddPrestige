package gg.maddkraft.prestige.model;

public enum ContestMetric {
    SERVER_EARNINGS,
    MCMMO_XP,
    RABBIT_HOLES,
    DECREE_OBJECTIVES,
    BOSSES,
    PRESTIGES,
    MADNESS_COMPOSITE;

    public static ContestMetric parse(String input) {
        return valueOf(input.trim().toUpperCase().replace('-', '_'));
    }
}
