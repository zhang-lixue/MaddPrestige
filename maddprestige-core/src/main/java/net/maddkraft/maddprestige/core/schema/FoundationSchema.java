package net.maddkraft.maddprestige.core.schema;

import java.util.List;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.FieldId;

public final class FoundationSchema {
    private FoundationSchema() {
    }

    public static SchemaRegistry create() {
        SchemaRegistry registry = new SchemaRegistry();
        registry.register(node("lp_reconciliation", "integrations.rank.reconciliation-policy", SchemaValueType.ENUM,
                "warn-only", "Controls mismatch reporting or repair for managed progression projection.",
                AllowedValues.fixed("warn-only", "maddprestige-authoritative", "import-once"), RiskLevel.HIGH));
        registry.register(node("external_commands_enabled", "safety.external-commands.enabled", SchemaValueType.BOOLEAN,
                "false", "Enables explicitly allowlisted external command actions.",
                AllowedValues.fixed("true", "false"), RiskLevel.CRITICAL));
        registry.register(node("competitions_enabled", "competitions.enabled", SchemaValueType.BOOLEAN,
                "false", "Enables the optional generic competition module.",
                AllowedValues.fixed("true", "false"), RiskLevel.MEDIUM));
        registry.register(node("quickshop_income_weight", "integrations.quickshop.progression-income-weight",
                SchemaValueType.DECIMAL, "0", "Progression credit for player-to-player sales volume.",
                AllowedValues.unrestricted(), RiskLevel.HIGH));
        SchemaNode databasePassword = new SchemaNode(
                new FieldId("database_password"), "database.credentials.password", SchemaValueType.SECRET,
                Optional.empty(), "External database credential.", List.of("${MADD_PRESTIGE_DB_PASSWORD}"),
                List.of(), AllowedValues.unrestricted(), true, RiskLevel.CRITICAL,
                "maddprestige.admin.config.edit.secret", "maddprestige.admin.config.apply.secret",
                ReloadBehavior.SERVICE_RESTART, List.of(), Optional.of("redact-always"));
        registry.register(databasePassword);
        return registry;
    }

    private static SchemaNode node(
            String id,
            String path,
            SchemaValueType type,
            String defaultValue,
            String description,
            AllowedValues allowedValues,
            RiskLevel risk) {
        return new SchemaNode(new FieldId(id), path, type, Optional.of(defaultValue), description,
                List.of(defaultValue), List.of(), allowedValues, false, risk,
                "maddprestige.admin.config.edit", "maddprestige.admin.config.apply",
                ReloadBehavior.HOT_RELOAD, List.of(), Optional.empty());
    }
}
