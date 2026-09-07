package net.maddkraft.maddprestige.integrations.config;

import net.maddkraft.maddprestige.core.schema.ActiveConfigurationSchema;
import net.maddkraft.maddprestige.core.schema.SchemaRegistry;

/** Canonical administration schema with the accepted integration integration metadata. */
public final class ActiveIntegrationSchema {
    private ActiveIntegrationSchema() {
    }

    public static SchemaRegistry create() {
        SchemaRegistry registry = ActiveConfigurationSchema.create();
        IntegrationSchema.extend(registry);
        return registry;
    }
}
