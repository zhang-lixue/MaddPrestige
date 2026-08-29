package net.maddkraft.maddprestige.core.admin.command;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.schema.SchemaNode;
import net.maddkraft.maddprestige.core.schema.SchemaRegistry;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;

public final class ContextualHelpService {
    private final SchemaRegistry schema;

    public ContextualHelpService(SchemaRegistry schema) {
        this.schema = Objects.requireNonNull(schema, "schema");
    }

    public List<MessageReference> help(PermissionSubject subject, String topic) {
        subject.require(PhaseSixPermissions.USE);
        String normalized = Objects.requireNonNull(topic, "topic").toLowerCase(Locale.ROOT);
        if (normalized.equals("overview")) {
            return List.of(MessageReference.of("command.help.overview.title"),
                    MessageReference.of("command.help.overview.player"),
                    MessageReference.of("command.help.overview.setup"),
                    MessageReference.of("command.help.overview.health"),
                    MessageReference.of("command.help.overview.more"));
        }
        if (normalized.equals("setup")) {
            return List.of(MessageReference.of("command.help.setup.title"),
                    MessageReference.of("command.help.setup.start"),
                    MessageReference.of("command.help.setup.stages"),
                    MessageReference.of("command.help.setup.playtime"),
                    MessageReference.of("command.help.setup.prestige"),
                    MessageReference.of("command.help.setup.safety"),
                    MessageReference.of("command.help.setup.advanced"));
        }
        if (normalized.equals("measurement")) {
            SchemaNode node = schema.resolve("requirements.requirements.example.measurement-scope")
                    .orElseThrow(() -> new IllegalStateException("Measurement schema metadata is absent"));
            return List.of(MessageReference.of("command.help.measurement.title"),
                    MessageReference.of("command.help.measurement.description"),
                    MessageReference.of("command.help.valid_values", "value",
                            String.join(", ", node.allowedValues().staticValues())),
                    MessageReference.of("command.help.measurement.duration_targets"),
                    MessageReference.of("command.help.measurement.playtime_example"),
                    MessageReference.of("command.help.measurement.baseline_safety"));
        }
        return schema.nodes().stream()
                .filter(node -> node.canonicalPath().contains(normalized)
                        || node.description().toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(java.util.Comparator.comparing(SchemaNode::canonicalPath))
                .limit(20)
                .map(node -> MessageReference.of("command.help.schema_entry",
                        "path", node.canonicalPath(), "type", node.type()))
                .toList();
    }
}
