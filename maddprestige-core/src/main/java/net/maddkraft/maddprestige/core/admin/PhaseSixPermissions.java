package net.maddkraft.maddprestige.core.admin;

import java.util.Set;

public final class PhaseSixPermissions {
    public static final String USE = "maddprestige.use";
    public static final String RANK_UP = "maddprestige.rankup";
    public static final String PRESTIGE = "maddprestige.prestige";
    public static final String ADMIN_GUI = "maddprestige.admin.gui";
    public static final String CONFIG_VIEW = "maddprestige.admin.config.view";
    public static final String CONFIG_EDIT = "maddprestige.admin.config.edit";
    public static final String CONFIG_APPLY = "maddprestige.admin.config.apply";
    public static final String CONFIG_ROLLBACK = "maddprestige.admin.config.rollback";
    public static final String PLAYER_VIEW = "maddprestige.admin.players.view";
    public static final String PLAYER_PRESTIGE_EDIT = "maddprestige.admin.players.prestige";
    public static final String PLAYER_PRESTIGE_SET = "maddprestige.admin.players.prestige.set";
    public static final String PLAYER_PRESTIGE_RESET = "maddprestige.admin.players.prestige.reset";
    public static final String SIMULATE = "maddprestige.admin.simulate";
    public static final String DOCTOR = "maddprestige.admin.doctor";
    public static final String SETUP = "maddprestige.admin.setup";
    public static final String EXECUTE = "maddprestige.admin.execute";

    public static Set<String> all() {
        return Set.of(USE, RANK_UP, PRESTIGE, ADMIN_GUI, CONFIG_VIEW, CONFIG_EDIT, CONFIG_APPLY,
                CONFIG_ROLLBACK, PLAYER_VIEW, PLAYER_PRESTIGE_EDIT, PLAYER_PRESTIGE_SET,
                PLAYER_PRESTIGE_RESET, SIMULATE, DOCTOR, SETUP, EXECUTE);
    }

    private PhaseSixPermissions() {
    }
}
