package net.maddkraft.maddprestige.core.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.FieldId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SchemaRegistryTest {
    @Test
    @DisplayName("[A38][A40] Schema fields expose permissions, risk, defaults, values, and sensitivity")
    void exposesRepresentativeMetadata() {
        SchemaRegistry registry = FoundationSchema.create();
        SchemaNode quickShop = registry.find("integrations.quickshop.progression-income-weight").orElseThrow();
        assertEquals("0", quickShop.defaultValue().orElseThrow());
        assertEquals(RiskLevel.HIGH, quickShop.risk());
        assertFalse(quickShop.sensitive());
        assertTrue(registry.find("database.credentials.password").orElseThrow().sensitive());
        assertEquals(5, registry.nodes().size());
    }

    @Test
    @DisplayName("[A40] Duplicate immutable field IDs and paths are rejected")
    void rejectsDuplicateRegistration() {
        SchemaRegistry registry = new SchemaRegistry();
        SchemaNode node = node("one", "path.one");
        registry.register(node);
        assertThrows(IllegalArgumentException.class, () -> registry.register(node));
        assertThrows(IllegalArgumentException.class, () -> registry.register(node("two", "path.one")));
    }

    @Test
    @DisplayName("Canonical schema exposes lifecycle fields and safe feature defaults")
    void exposesPrestigeLifecycleSchema() {
        SchemaRegistry registry = PrestigeLifecycleSchema.create();

        assertEquals("false", registry.find("prestige.enabled").orElseThrow().defaultValue().orElseThrow());
        assertEquals("unlimited", registry.find("prestige.maximum").orElseThrow()
                .defaultValue().orElseThrow());
        assertEquals("false", registry.find("competition.enabled").orElseThrow()
                .defaultValue().orElseThrow());
        assertEquals(RiskLevel.CRITICAL, registry.find("prestige.reset-policy").orElseThrow().risk());
        assertEquals("RESET", registry.find("prestige.reset-policy.active-requirement-progress")
                .orElseThrow().defaultValue().orElseThrow());
        assertEquals("PRESERVE", registry.find("prestige.reset-policy.purchased-perks")
                .orElseThrow().defaultValue().orElseThrow());
        assertEquals(List.of("PRESERVE", "RESET"), registry.find("seasons.*.reset-policy.season-progress")
                .orElseThrow().allowedValues().staticValues().stream().sorted().toList());
        assertTrue(registry.find("seasons.*.reset-policy.progression-stage").isEmpty());
    }

    @Test
    @DisplayName("Numeric schema exposes compact scaling and inherited segment defaults")
    void exposesCompactScalingSchema() {
        SchemaRegistry registry = ActiveConfigurationSchema.create();

        assertTrue(registry.resolve("requirements.requirements.play.scope").isPresent());
        assertTrue(registry.resolve("requirements.requirements.play.completion").isPresent());
        assertTrue(registry.resolve("requirements.requirements.play.measurement-scope").isEmpty());
        assertTrue(registry.resolve("requirements.requirements.play.completion-mode").isEmpty());
        assertEquals("LINEAR", registry.resolve("requirements.requirements.play.scaling.mode")
                .orElseThrow().allowedValues().staticValues().stream()
                .filter("LINEAR"::equals).findFirst().orElseThrow());
        assertEquals("EXACT", registry.resolve("prestige.cost-scaling.payment.defaults.rounding")
                .orElseThrow().defaultValue().orElseThrow());
        assertEquals("1", registry.resolve("prestige.reward-scaling.grant.segments.0.base")
                .orElseThrow().defaultValue().orElseThrow());
        assertEquals(SchemaValueType.MAP, registry.resolve("prestige.cost-scaling.payment.segments.0")
                .orElseThrow().type());
        assertEquals(SchemaValueType.MAP, registry.resolve("requirements.trees.prestige.children.0")
                .orElseThrow().type());
    }

    private static SchemaNode node(String id, String path) {
        return new SchemaNode(new FieldId(id), path, SchemaValueType.STRING, Optional.empty(), "description",
                List.of(), List.of(), AllowedValues.unrestricted(), false, RiskLevel.LOW,
                "edit", "apply", ReloadBehavior.HOT_RELOAD, List.of(), Optional.empty());
    }
}
