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
        SchemaRegistry registry = PhaseOneSchema.create();
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
    @DisplayName("[Phase4-config] Canonical schema exposes lifecycle fields and safe feature defaults")
    void exposesPhaseFourLifecycleSchema() {
        SchemaRegistry registry = PhaseFourSchema.create();

        assertEquals("false", registry.find("prestige.enabled").orElseThrow().defaultValue().orElseThrow());
        assertEquals("unlimited", registry.find("prestige.maximum").orElseThrow()
                .defaultValue().orElseThrow());
        assertEquals("false", registry.find("competition.enabled").orElseThrow()
                .defaultValue().orElseThrow());
        assertEquals(RiskLevel.CRITICAL, registry.find("prestige.reset-policy").orElseThrow().risk());
        assertEquals(List.of("PRESERVE", "RESET"), registry.find("seasons.*.reset-policy.season-progress")
                .orElseThrow().allowedValues().staticValues().stream().sorted().toList());
        assertTrue(registry.find("seasons.*.reset-policy.progression-stage").isEmpty());
    }

    private static SchemaNode node(String id, String path) {
        return new SchemaNode(new FieldId(id), path, SchemaValueType.STRING, Optional.empty(), "description",
                List.of(), List.of(), AllowedValues.unrestricted(), false, RiskLevel.LOW,
                "edit", "apply", ReloadBehavior.HOT_RELOAD, List.of(), Optional.empty());
    }
}
