package net.maddkraft.maddprestige.platform.paper.bootstrap;

import net.maddkraft.maddprestige.integrations.config.IntegrationConfiguration;

/** Reconciles Paper-owned configuration-dependent capabilities after durable canonical apply. */
@FunctionalInterface
interface RuntimeConfigurationReconciler {
    void reconcile(IntegrationConfiguration configuration);
}
