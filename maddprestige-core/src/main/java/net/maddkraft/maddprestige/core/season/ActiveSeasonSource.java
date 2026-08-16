package net.maddkraft.maddprestige.core.season;

@FunctionalInterface
public interface ActiveSeasonSource {
    ActiveSeasonContext active();
}
