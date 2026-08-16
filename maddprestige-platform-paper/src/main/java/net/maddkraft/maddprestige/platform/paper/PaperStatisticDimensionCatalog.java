package net.maddkraft.maddprestige.platform.paper;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/** Must be created at the live Paper bootstrap boundary, where registries are initialized. */
public final class PaperStatisticDimensionCatalog implements StatisticDimensionCatalog {
    private final Set<String> blocks;
    private final Set<String> items;
    private final Set<String> entities;

    public PaperStatisticDimensionCatalog() {
        blocks = Arrays.stream(Material.values()).filter(Material::isBlock).map(PaperStatisticDimensionCatalog::id)
                .collect(Collectors.toUnmodifiableSet());
        items = Arrays.stream(Material.values()).filter(Material::isItem).map(PaperStatisticDimensionCatalog::id)
                .collect(Collectors.toUnmodifiableSet());
        entities = Arrays.stream(EntityType.values()).map(PaperStatisticDimensionCatalog::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<String> blockMaterials() {
        return blocks;
    }

    @Override
    public Set<String> itemMaterials() {
        return items;
    }

    @Override
    public Set<String> entityTypes() {
        return entities;
    }

    private static String id(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
