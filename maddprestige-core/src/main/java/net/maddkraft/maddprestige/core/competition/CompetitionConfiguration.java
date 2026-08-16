package net.maddkraft.maddprestige.core.competition;

public record CompetitionConfiguration(boolean enabled) {
    public static CompetitionConfiguration disabled() {
        return new CompetitionConfiguration(false);
    }
}
