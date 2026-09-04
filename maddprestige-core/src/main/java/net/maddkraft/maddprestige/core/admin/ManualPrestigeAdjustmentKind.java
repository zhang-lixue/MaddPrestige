package net.maddkraft.maddprestige.core.admin;

/** Distinguishes administrative counter replacement from normal player Prestige execution. */
public enum ManualPrestigeAdjustmentKind {
    SET,
    RESET;

    public String permission() {
        return this == SET
                ? PhaseSixPermissions.PLAYER_PRESTIGE_SET
                : PhaseSixPermissions.PLAYER_PRESTIGE_RESET;
    }

    public String operationType() {
        return "ADMIN_PRESTIGE_" + name();
    }

    public String historyEvent() {
        return "ADMIN_" + name();
    }

    public String providerAction() {
        return "player.prestige.admin_" + name().toLowerCase(java.util.Locale.ROOT);
    }
}
