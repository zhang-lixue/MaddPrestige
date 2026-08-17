package net.maddkraft.maddprestige.core.admin.config;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.config.ActiveConfiguration;
import net.maddkraft.maddprestige.core.schema.SchemaNode;
import net.maddkraft.maddprestige.core.schema.SchemaRegistry;
import net.maddkraft.maddprestige.core.yaml.LosslessYamlDocument;

public final class ConfigurationIntrospectionService {
    private static final int MAX_RESULTS = 100;
    private final SchemaRegistry schema;
    private final Supplier<Optional<ActiveConfiguration>> active;
    private final Supplier<ValidationReport> validation;
    private final ConfigurationPathResolver paths = new ConfigurationPathResolver();

    public ConfigurationIntrospectionService(
            SchemaRegistry schema,
            Supplier<Optional<ActiveConfiguration>> active,
            Supplier<ValidationReport> validation) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.active = Objects.requireNonNull(active, "active configuration");
        this.validation = Objects.requireNonNull(validation, "validation report");
    }

    public List<ConfigurationSearchResult> search(PermissionSubject subject, String query, int limit) {
        subject.require(PhaseSixPermissions.CONFIG_VIEW);
        Objects.requireNonNull(query, "query");
        if (limit < 1 || limit > MAX_RESULTS) {
            throw new IllegalArgumentException("Search limit must be between 1 and " + MAX_RESULTS);
        }
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        return schema.nodes().stream()
                .filter(node -> matches(node, normalized))
                .sorted(Comparator.comparing(SchemaNode::canonicalPath))
                .limit(limit)
                .map(this::searchResult)
                .toList();
    }

    public ConfigurationExplanation explain(PermissionSubject subject, String actualPath) {
        subject.require(PhaseSixPermissions.CONFIG_VIEW);
        SchemaNode node = schema.resolve(actualPath).orElseThrow(() -> new AdministrationException(
                "config.path.unknown", "Unknown canonical configuration path: " + actualPath,
                "Use config search to discover schema-owned paths."));
        Optional<String> value = currentValue(node, actualPath);
        return new ConfigurationExplanation(actualPath, node.type(), value, safeDefault(node), node.description(),
                node.allowedValues().staticValues(), node.examples(), node.risk(), node.reloadBehavior(),
                node.editPermission(), node.applyPermission());
    }

    private ConfigurationSearchResult searchResult(SchemaNode node) {
        String value = currentValue(node, node.canonicalPath()).orElse("<not set or structured>");
        List<String> codes = validation.get().findings().stream()
                .filter(finding -> finding.path().startsWith(node.canonicalPath().replace("*", "")))
                .map(finding -> finding.code())
                .distinct()
                .sorted()
                .toList();
        return new ConfigurationSearchResult(node.canonicalPath(), node.type(), value, node.description(),
                node.reloadBehavior(), codes);
    }

    private Optional<String> currentValue(SchemaNode node, String actualPath) {
        if (node.sensitive()) {
            return Optional.of("<redacted>");
        }
        Optional<ConfigurationPathResolver.ResolvedPath> resolved = paths.resolve(actualPath);
        Optional<ActiveConfiguration> current = active.get();
        if (resolved.isEmpty() || current.isEmpty()) {
            return Optional.empty();
        }
        String source = current.orElseThrow().compiled().documents()
                .get(resolved.orElseThrow().documentName());
        if (source == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LosslessYamlDocument.parse(source).scalar(resolved.orElseThrow().yamlPath()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Optional<String> safeDefault(SchemaNode node) {
        return node.sensitive() ? node.defaultValue().map(ignored -> "<redacted>") : node.defaultValue();
    }

    private static boolean matches(SchemaNode node, String query) {
        if (query.isEmpty()) {
            return true;
        }
        String text = String.join(" ", node.canonicalPath(), node.description(), node.type().name(),
                String.join(" ", node.examples()), String.join(" ", node.allowedValues().staticValues()))
                .toLowerCase(Locale.ROOT);
        return text.contains(query);
    }
}
