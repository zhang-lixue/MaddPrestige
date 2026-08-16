package net.maddkraft.maddprestige.platform.paper;

import java.util.Set;

/** Runtime-supplied authoritative dimension classification for Bukkit statistics. */
public interface StatisticDimensionCatalog {
    Set<String> blockMaterials();

    Set<String> itemMaterials();

    Set<String> entityTypes();
}
