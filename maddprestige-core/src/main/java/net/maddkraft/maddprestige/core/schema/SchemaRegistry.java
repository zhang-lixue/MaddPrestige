package net.maddkraft.maddprestige.core.schema;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.FieldId;

public final class SchemaRegistry {
    private final Map<FieldId, SchemaNode> byId = new LinkedHashMap<>();
    private final Map<String, SchemaNode> byPath = new LinkedHashMap<>();
    private final Map<String, SchemaNode> byAlias = new LinkedHashMap<>();

    public synchronized void register(SchemaNode node) {
        Objects.requireNonNull(node, "schema node");
        if (byId.containsKey(node.id())) {
            throw new IllegalArgumentException("Duplicate schema field ID: " + node.id().value());
        }
        if (byPath.containsKey(node.canonicalPath()) || byAlias.containsKey(node.canonicalPath())) {
            throw new IllegalArgumentException("Duplicate schema path: " + node.canonicalPath());
        }
        for (Deprecation deprecation : node.deprecations()) {
            String alias = deprecation.alias();
            if (byPath.containsKey(alias) || byAlias.containsKey(alias)) {
                throw new IllegalArgumentException("Duplicate schema alias: " + alias);
            }
        }
        byId.put(node.id(), node);
        byPath.put(node.canonicalPath(), node);
        node.deprecations().forEach(deprecation -> byAlias.put(deprecation.alias(), node));
    }

    public synchronized Optional<SchemaNode> find(String pathOrAlias) {
        SchemaNode direct = byPath.get(pathOrAlias);
        return Optional.ofNullable(direct != null ? direct : byAlias.get(pathOrAlias));
    }

    public synchronized Collection<SchemaNode> nodes() {
        return List.copyOf(byPath.values());
    }
}
