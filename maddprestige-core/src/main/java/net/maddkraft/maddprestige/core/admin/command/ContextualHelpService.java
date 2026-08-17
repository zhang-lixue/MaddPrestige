package net.maddkraft.maddprestige.core.admin.command;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.schema.SchemaNode;
import net.maddkraft.maddprestige.core.schema.SchemaRegistry;

public final class ContextualHelpService {
    private final SchemaRegistry schema;

    public ContextualHelpService(SchemaRegistry schema) {
        this.schema = Objects.requireNonNull(schema, "schema");
    }

    public List<String> help(PermissionSubject subject, String topic) {
        subject.require(PhaseSixPermissions.USE);
        String normalized = Objects.requireNonNull(topic, "topic").toLowerCase(Locale.ROOT);
        if (normalized.equals("measurement")) {
            SchemaNode node = schema.resolve("requirements.requirements.example.measurement-scope")
                    .orElseThrow(() -> new IllegalStateException("Measurement schema metadata is absent"));
            return List.of("Measurement scope", node.description(),
                    "Valid values: " + String.join(", ", node.allowedValues().staticValues()),
                    "Simulation and why use the same saved baselines as real execution and never create one.");
        }
        return schema.nodes().stream()
                .filter(node -> node.canonicalPath().contains(normalized)
                        || node.description().toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(java.util.Comparator.comparing(SchemaNode::canonicalPath))
                .limit(20)
                .map(node -> node.canonicalPath() + " — " + node.description())
                .toList();
    }
}
