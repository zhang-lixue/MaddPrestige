package net.maddkraft.maddprestige.integrations.config;

import net.maddkraft.maddprestige.core.schema.PhaseSixSchema;
import net.maddkraft.maddprestige.core.schema.SchemaRegistry;

/** Canonical Phase 6 schema with the accepted Phase 5 integration metadata. */
public final class PhaseSixIntegrationSchema {
    private PhaseSixIntegrationSchema() {
    }

    public static SchemaRegistry create() {
        SchemaRegistry registry = PhaseSixSchema.create();
        PhaseFiveIntegrationSchema.extend(registry);
        return registry;
    }
}
