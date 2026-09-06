package net.maddkraft.maddprestige.core.admin.ui;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Read-only projection of cached canonical runtime health for the Staff GUI. */
public interface StaffSystemStatusSource {
    Summary summary();

    CompletionStage<Snapshot> inspect();

    /** Canonical, read-only summary of the currently published Prestige configuration. */
    default ConfigurationSummary configuration() {
        Summary current = summary();
        return new ConfigurationSummary(current.configurationActive(), current.configurationActive(),
                current.configurationActive() ? "Configured" : "Unavailable",
                0, 0, 0, 0, List.of());
    }

    record Summary(Health health, boolean configurationActive, int availableProviders, int totalProviders) {
        public Summary {
            health = Objects.requireNonNull(health, "health");
            if (availableProviders < 0 || totalProviders < availableProviders) {
                throw new IllegalArgumentException("Provider health counts are invalid");
            }
        }
    }

    record Snapshot(Summary summary, List<Component> components) {
        public Snapshot {
            summary = Objects.requireNonNull(summary, "summary");
            components = List.copyOf(Objects.requireNonNull(components, "components"));
        }
    }

    record Component(String name, Health health, String status, String detail) {
        public Component {
            name = Objects.requireNonNull(name, "name");
            health = Objects.requireNonNull(health, "health");
            status = Objects.requireNonNull(status, "status");
            detail = Objects.requireNonNull(detail, "detail");
        }

        public Component(String name, Health health, String detail) {
            this(name, health, switch (health) {
                case HEALTHY -> "Available";
                case WARNING -> "Attention";
                case BLOCKED -> "Unavailable";
            }, detail);
        }
    }

    record ConfigurationSummary(
            boolean active,
            boolean usable,
            String prestigeRange,
            int requirements,
            int costs,
            int rewards,
            int scalingProfiles,
            List<String> providers) {
        public ConfigurationSummary {
            prestigeRange = Objects.requireNonNull(prestigeRange, "Prestige range");
            providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
            if (requirements < 0 || costs < 0 || rewards < 0 || scalingProfiles < 0) {
                throw new IllegalArgumentException("Configuration summary counts cannot be negative");
            }
            if (usable && !active) {
                throw new IllegalArgumentException("An inactive configuration cannot be usable");
            }
        }

        public static ConfigurationSummary inactive() {
            return new ConfigurationSummary(false, false, "Unavailable", 0, 0, 0, 0, List.of());
        }
    }

    enum Health {
        HEALTHY,
        WARNING,
        BLOCKED
    }
}
