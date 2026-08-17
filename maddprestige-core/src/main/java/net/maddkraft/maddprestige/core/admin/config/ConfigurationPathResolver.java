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
        List<String> segments = parseSegments(canonicalPath);
        if (segments.isEmpty()) {
            return Optional.empty();
        }
        DocumentRoot root = ROOTS.get(segments.getFirst());
        if (root == null) {
            return Optional.empty();
        }
        int firstYamlSegment = root.stripFirstSegment() ? 1 : 0;
        ArrayList<YamlPath.Segment> yamlSegments = new ArrayList<>(segments.size() - firstYamlSegment);
        for (int index = firstYamlSegment; index < segments.size(); index++) {
            yamlSegments.add(new YamlPath.Key(segments.get(index)));
        }
        return Optional.of(new ResolvedPath(root.documentName(), new YamlPath(0, yamlSegments)));
    }

    private static List<String> parseSegments(String canonicalPath) {
        if (canonicalPath.isEmpty()) {
            return List.of();
        }
        ArrayList<String> segments = new ArrayList<>();
        int segmentStart = 0;
        for (int index = 0; index < canonicalPath.length(); index++) {
            char current = canonicalPath.charAt(index);
            if (current == '.') {
                if (index == segmentStart) {
                    return List.of();
                }
                segments.add(canonicalPath.substring(segmentStart, index));
                segmentStart = index + 1;
            } else if (!isSegmentCharacter(current)) {
                return List.of();
            }
        }
        if (segmentStart == canonicalPath.length()) {
            return List.of();
        }
        segments.add(canonicalPath.substring(segmentStart));
        return List.copyOf(segments);
    }

    private static boolean isSegmentCharacter(char value) {
        return value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '_'
                || value == '-';
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
