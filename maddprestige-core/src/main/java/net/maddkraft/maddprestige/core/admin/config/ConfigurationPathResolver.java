package net.maddkraft.maddprestige.core.admin.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.core.yaml.YamlPath;

public final class ConfigurationPathResolver {
    private static final Map<String, DocumentRoot> ROOTS = Map.ofEntries(
            Map.entry("progression", new DocumentRoot("progression.yml", true)),
            Map.entry("requirements", new DocumentRoot("requirements.yml", true)),
            Map.entry("rewards", new DocumentRoot("rewards.yml", true)),
            Map.entry("integrations", new DocumentRoot("integrations.yml", true)),
            Map.entry("prestige", new DocumentRoot("lifecycle.yml", false)),
            Map.entry("currencies", new DocumentRoot("lifecycle.yml", false)),
            Map.entry("entitlements", new DocumentRoot("lifecycle.yml", false)),
            Map.entry("milestones", new DocumentRoot("lifecycle.yml", false)),
            Map.entry("seasons", new DocumentRoot("lifecycle.yml", false)),
            Map.entry("competition", new DocumentRoot("lifecycle.yml", false)));

    public Optional<ResolvedPath> resolve(String canonicalPath) {
        Objects.requireNonNull(canonicalPath, "canonical path");
        if (!canonicalPath.matches("[a-z0-9_-]+(?:\\.[a-z0-9_-]+)*")) {
            return Optional.empty();
        }
        List<String> segments = List.of(canonicalPath.split("\\."));
        DocumentRoot root = ROOTS.get(segments.getFirst());
        if (root == null) {
            return Optional.empty();
        }
        ArrayList<String> yamlSegments = new ArrayList<>(segments);
        if (root.stripFirstSegment()) {
            yamlSegments.removeFirst();
        }
        YamlPath yamlPath = YamlPath.document(0);
        for (String segment : yamlSegments) {
            yamlPath = yamlPath.key(segment);
        }
        return Optional.of(new ResolvedPath(root.documentName(), yamlPath));
    }

    public record ResolvedPath(String documentName, YamlPath yamlPath) {
        public ResolvedPath {
            documentName = Objects.requireNonNull(documentName, "document name");
            yamlPath = Objects.requireNonNull(yamlPath, "YAML path");
        }
    }

    private record DocumentRoot(String documentName, boolean stripFirstSegment) {
    }
}
