package net.maddkraft.maddprestige.core.admin.setup;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.rank.RankAdapter;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationAdministrationService;
import net.maddkraft.maddprestige.core.admin.config.PreparedConfigurationAcknowledgement;
import net.maddkraft.maddprestige.core.admin.config.StoredConfigurationRevision;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;

public final class SetupWizardService {
    private final ConfigurationAdministrationService configuration;
    private final Optional<ProviderRegistry> providers;
    private final Clock clock;
    private final Duration lifetime;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> acknowledgementSessions = new ConcurrentHashMap<>();

    public SetupWizardService(ConfigurationAdministrationService configuration) {
        this(configuration, Optional.empty(), Clock.systemUTC(), Duration.ofHours(2));
    }

    public SetupWizardService(
            ConfigurationAdministrationService configuration,
            ProviderRegistry providers) {
        this(configuration, Optional.of(Objects.requireNonNull(providers, "providers")), Clock.systemUTC(),
                Duration.ofHours(2));
    }

    public SetupWizardService(
            ConfigurationAdministrationService configuration,
            ProviderRegistry providers,
            Clock clock,
            Duration lifetime) {
        this(configuration, Optional.of(Objects.requireNonNull(providers, "providers")), clock, lifetime);
    }

    private SetupWizardService(
            ConfigurationAdministrationService configuration,
            Optional<ProviderRegistry> providers,
            Clock clock,
            Duration lifetime) {
        this.configuration = Objects.requireNonNull(configuration, "configuration service");
        this.providers = Objects.requireNonNull(providers, "providers");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofHours(8)) > 0) {
            throw new IllegalArgumentException("Setup session lifetime must be positive and at most eight hours");
        }
    }

    public SetupDiscovery discover(PermissionSubject subject) {
        subject.require(PhaseSixPermissions.SETUP);
        List<SetupProviderOption> options = providers.stream().flatMap(registry -> registry.snapshots().stream()
                .map(snapshot -> {
                    var provider = registry.provider(snapshot.descriptor().id());
                    List<String> metrics = provider.filter(MetricProvider.class::isInstance)
                            .map(MetricProvider.class::cast).map(metricProvider -> {
                                try {
                                    return metricProvider.metrics().stream().map(value -> value.metricId().value())
                                            .sorted().toList();
                                } catch (RuntimeException exception) {
                                    return List.<String>of();
                                }
                            }).orElse(List.of());
                    boolean healthy = snapshot.health().state() == ProviderHealthState.AVAILABLE
                            || snapshot.health().state() == ProviderHealthState.ACTIVE;
                    return new SetupProviderOption(snapshot.descriptor().id(),
                            snapshot.activation() == ActivationState.ACTIVE, healthy,
                            provider.filter(RankAdapter.class::isInstance).isPresent(), metrics,
                            snapshot.health().reason());
                })).sorted(java.util.Comparator.comparing(value -> value.providerId().value())).toList();
        return new SetupDiscovery(configuration.activeConfigurationPresentForSetup(subject), options,
                "Select existing external group names. Preview validates them; MaddPrestige never creates groups.");
    }

    public UUID start(PermissionSubject subject) {
        subject.require(PhaseSixPermissions.SETUP);
        pruneExpired();
        UUID id = UUID.randomUUID();
        sessions.put(id, new Session(subject, Optional.empty(), List.of(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), SetupPrestige.disabled(), Optional.empty(),
                Instant.now(clock).plus(lifetime)));
        return id;
    }

    public void selectRankProvider(PermissionSubject subject, UUID sessionId, Optional<ProviderId> providerId) {
        Session session = require(subject, sessionId);
        discardDraft(subject, session);
        sessions.put(sessionId, session.withProvider(providerId));
    }

    public void addStage(PermissionSubject subject, UUID sessionId, SetupStage stage) {
        Session session = require(subject, sessionId);
        rejectControlCharacters(stage.displayName(), "stage display name");
        stage.externalGroup().ifPresent(value -> rejectControlCharacters(value, "external group"));
        if (session.stages().stream().anyMatch(existing -> existing.id().equals(stage.id()))) {
            throw new AdministrationException("setup.stage.duplicate", "Stage ID already exists: " + stage.id().value(),
                    "Choose a unique immutable stage ID.");
        }
        if (session.providerId().isEmpty() && stage.externalGroup().isPresent()) {
            throw new AdministrationException("setup.provider.required",
                    "An external group cannot be selected without a rank provider.",
                    "Choose the provider first or configure this stage as internal-only.");
        }
        ArrayList<SetupStage> stages = new ArrayList<>(session.stages());
        stages.add(stage);
        Optional<net.maddkraft.maddprestige.api.id.StageId> baseline = session.baseline().isPresent()
                ? session.baseline() : Optional.of(stage.id());
        discardDraft(subject, session);
        sessions.put(sessionId, session.withStages(stages, baseline));
    }

    public void selectBaseline(
            PermissionSubject subject,
            UUID sessionId,
            net.maddkraft.maddprestige.api.id.StageId baseline) {
        Session session = require(subject, sessionId);
        if (session.stages().stream().noneMatch(stage -> stage.id().equals(baseline))) {
            throw new AdministrationException("setup.baseline.unknown", "Baseline stage is not in the wizard: "
                    + baseline.value(), "Select one of the configured stage IDs.");
        }
        discardDraft(subject, session);
        sessions.put(sessionId, session.withBaseline(baseline));
    }

    public void configureRequirement(PermissionSubject subject, UUID sessionId, SetupRequirement requirement) {
        Session session = require(subject, sessionId);
        discardDraft(subject, session);
        sessions.put(sessionId, session.withRequirement(Objects.requireNonNull(requirement, "requirement")));
    }

    public void configureCost(PermissionSubject subject, UUID sessionId, SetupCost cost) {
        Session session = require(subject, sessionId);
        discardDraft(subject, session);
        sessions.put(sessionId, session.withCost(Objects.requireNonNull(cost, "cost")));
    }

    public void configureReward(PermissionSubject subject, UUID sessionId, SetupReward reward) {
        Session session = require(subject, sessionId);
        discardDraft(subject, session);
        sessions.put(sessionId, session.withReward(Objects.requireNonNull(reward, "reward")));
    }

    public void configurePrestige(PermissionSubject subject, UUID sessionId, SetupPrestige prestige) {
        Session session = require(subject, sessionId);
        Objects.requireNonNull(prestige, "prestige");
        prestige.requiredStage().ifPresent(stage -> requireConfiguredStage(session, stage, "eligibility"));
        prestige.resetStage().ifPresent(stage -> requireConfiguredStage(session, stage, "reset"));
        discardDraft(subject, session);
        sessions.put(sessionId, session.withPrestige(prestige));
    }

    public CompletionStage<SetupPreview> preview(PermissionSubject subject, UUID sessionId) {
        Session session = require(subject, sessionId);
        if (session.stages().size() < 2 || session.baseline().isEmpty()) {
            throw new AdministrationException("setup.incomplete",
                    "A simple ladder needs at least two ordered stages and one baseline.",
                    "Add stages in progression order and select the baseline stage.");
        }
        if (session.providerId().isPresent()
                && session.stages().stream().skip(1).anyMatch(stage -> stage.externalGroup().isEmpty())) {
            throw new AdministrationException("setup.group.missing",
                    "Every projected stage after the baseline needs an existing external group.",
                    "Select existing groups; MaddPrestige never creates them.");
        }
        discardDraft(subject, session);
        UUID draftId = configuration.beginInitialDraft(subject, documents(session), "setup-wizard");
        sessions.put(sessionId, session.withDraft(draftId));
        return configuration.preview(subject, draftId).thenApply(preview -> new SetupPreview(
                sessionId, draftId, preview, experience(session)));
    }

    public CompletionStage<StoredConfigurationRevision> apply(
            PermissionSubject subject,
            UUID sessionId,
            Set<String> acknowledgements,
            String reason) {
        Session session = require(subject, sessionId);
        UUID draftId = session.draftId().orElseThrow(() -> new AdministrationException(
                "setup.preview.required", "Setup must be previewed before apply.",
                "Review the generated ladder and validation output first."));
        return configuration.applySetup(subject, draftId, acknowledgements, reason)
                .thenApply(revision -> {
                    sessions.remove(sessionId);
                    return revision;
                });
    }

    public PreparedConfigurationAcknowledgement prepareAcknowledgement(
            PermissionSubject subject,
            UUID sessionId) {
        Session session = require(subject, sessionId);
        UUID draftId = session.draftId().orElseThrow(() -> new AdministrationException(
                "setup.preview.required", "Setup must be previewed before acknowledgement.",
                "Preview and review the exact setup candidate first."));
        PreparedConfigurationAcknowledgement prepared = configuration.prepareAcknowledgement(
                subject, draftId);
        acknowledgementSessions.put(prepared.acknowledgementId(), sessionId);
        return prepared;
    }

    public CompletionStage<StoredConfigurationRevision> confirmAcknowledgement(
            PermissionSubject subject,
            UUID acknowledgementId,
            String reason) {
        UUID sessionId = acknowledgementSessions.get(Objects.requireNonNull(acknowledgementId,
                "acknowledgement ID"));
        if (sessionId == null) {
            throw new AdministrationException("setup.acknowledgement.unknown",
                    "Unknown, expired, or non-setup acknowledgement.",
                    "Preview setup and request a fresh acknowledgement.");
        }
        require(subject, sessionId);
        return configuration.confirmAcknowledgement(subject, acknowledgementId, reason).thenApply(revision -> {
            acknowledgementSessions.remove(acknowledgementId, sessionId);
            sessions.remove(sessionId);
            return revision;
        });
    }

    public void cancel(PermissionSubject subject, UUID sessionId) {
        Session session = require(subject, sessionId);
        discardDraft(subject, session);
        sessions.remove(sessionId);
        acknowledgementSessions.entrySet().removeIf(entry -> entry.getValue().equals(sessionId));
    }

    private void discardDraft(PermissionSubject subject, Session session) {
        session.draftId().ifPresent(draftId -> configuration.discardDraft(subject, draftId));
    }

    private Session require(PermissionSubject subject, UUID sessionId) {
        subject.require(PhaseSixPermissions.SETUP);
        pruneExpired();
        Session session = sessions.get(Objects.requireNonNull(sessionId, "session ID"));
        if (session == null) {
            throw new AdministrationException("setup.session.unknown", "Unknown or expired setup session.",
                    "Start or resume a current setup session.");
        }
        if (!session.subject().actor().equals(subject.actor())) {
            throw new AdministrationException("setup.session.owner_mismatch",
                    "Only the administrator who opened setup may resume it.",
                    "Start a separate setup session for this actor.");
        }
        return session;
    }

    private void pruneExpired() {
        Instant now = Instant.now(clock);
        sessions.forEach((id, session) -> {
            if (!now.isBefore(session.expiresAt()) && sessions.remove(id, session)) {
                discardDraft(session.subject(), session);
                acknowledgementSessions.entrySet().removeIf(entry -> entry.getValue().equals(id));
            }
        });
    }

    private static void requireConfiguredStage(
            Session session,
            net.maddkraft.maddprestige.api.id.StageId stage,
            String purpose) {
        if (session.stages().stream().noneMatch(candidate -> candidate.id().equals(stage))) {
            throw new AdministrationException("setup.prestige.stage_unknown",
                    "Prestige " + purpose + " stage is not in this setup session: " + stage.value(),
                    "Select one of the configured stage IDs.");
        }
    }

    private static Map<String, String> documents(Session session) {
        LinkedHashMap<String, String> documents = new LinkedHashMap<>();
        documents.put("progression.yml", progression(session));
        documents.put("requirements.yml", requirements(session));
        documents.put("rewards.yml", rewards(session));
        documents.put("lifecycle.yml", lifecycle(session));
        documents.put("integrations.yml", integrations());
        return Map.copyOf(documents);
    }

    private static String progression(Session session) {
        StringBuilder yaml = new StringBuilder("""
                # Created by the MaddPrestige setup wizard; review before apply.
                schema-version: 3
                active: true
                reconciliation-policy: warn-only
                """);
        yaml.append("baseline: ").append(session.baseline().orElseThrow().value()).append('\n');
        yaml.append("stages:\n");
        int baselineIndex = session.stages().indexOf(session.stages().stream()
                .filter(stage -> stage.id().equals(session.baseline().orElseThrow())).findFirst().orElseThrow());
        for (int index = 0; index < session.stages().size(); index++) {
            SetupStage stage = session.stages().get(index);
            yaml.append("  ").append(stage.id().value()).append(":\n")
                    .append("    enabled: true\n")
                    .append("    display-name: ").append(quote(stage.displayName())).append('\n');
            if (stage.externalGroup().isPresent()) {
                yaml.append("    projection:\n")
                        .append("      type: group\n")
                        .append("      provider: ").append(session.providerId().orElseThrow().value()).append('\n')
                        .append("      group: ").append(quote(stage.externalGroup().orElseThrow())).append('\n');
            } else {
                yaml.append("    projection: none\n");
            }
            if (index > baselineIndex) {
                session.requirement().ifPresent(value -> yaml.append("    requirements: setup_eligibility\n"));
                session.cost().ifPresent(value -> yaml.append("    costs: [")
                        .append(value.id().value()).append("]\n"));
                session.reward().ifPresent(value -> yaml.append("    rewards: [")
                        .append(value.id().value()).append("]\n"));
            }
        }
        yaml.append("order:\n");
        session.stages().forEach(stage -> yaml.append("  - ").append(stage.id().value()).append('\n'));
        return yaml.toString();
    }

    private static String requirements(Session session) {
        StringBuilder yaml = new StringBuilder("""
                # Created by the MaddPrestige setup wizard; review before apply.
                schema-version: 3
                maximum-depth: 16
                """);
        if (session.requirement().isEmpty()) {
            yaml.append("requirements: {}\ntrees: {}\n");
        } else {
            SetupRequirement value = session.requirement().orElseThrow();
            yaml.append("requirements:\n  ").append(value.id().value()).append(":\n")
                    .append("    provider: ").append(value.providerId().value()).append('\n')
                    .append("    metric: ").append(value.metricId().value()).append('\n')
                    .append("    operator: ").append(quote(value.operator())).append('\n')
                    .append("    target: ").append(quote(value.target())).append('\n')
                    .append("    scope: ").append(quote(value.scope())).append('\n')
                    .append("    completion: ").append(quote(value.completion())).append('\n')
                    .append("trees:\n")
                    .append("  setup_eligibility:\n")
                    .append("    id: setup_eligibility\n")
                    .append("    mode: all\n")
                    .append("    children:\n")
                    .append("      - requirement: ").append(value.id().value()).append('\n');
        }
        if (session.cost().isEmpty()) {
            yaml.append("costs: {}\n");
        } else {
            SetupCost value = session.cost().orElseThrow();
            yaml.append("costs:\n  ").append(value.id().value()).append(":\n")
                    .append("    provider: ").append(value.providerId().value()).append('\n')
                    .append("    type: ").append(quote(value.type())).append('\n')
                    .append("    value-type: ").append(quote(value.valueType())).append('\n')
                    .append("    amount: ").append(quote(value.amount())).append('\n')
                    .append("    display-name: ").append(quote(value.displayName())).append('\n');
        }
        return yaml.toString();
    }

    private static String rewards(Session session) {
        StringBuilder yaml = new StringBuilder("""
                # Created by the MaddPrestige setup wizard; command actions stay disabled.
                schema-version: 3
                """);
        if (session.reward().isEmpty()) {
            yaml.append("rewards: {}\n");
        } else {
            SetupReward value = session.reward().orElseThrow();
            yaml.append("rewards:\n  ").append(value.id().value()).append(":\n")
                    .append("    provider: ").append(value.providerId().value()).append('\n')
                    .append("    type: ").append(quote(value.type())).append('\n')
                    .append("    value-type: ").append(quote(value.valueType())).append('\n')
                    .append("    value: ").append(quote(value.value())).append('\n')
                    .append("    display-name: ").append(quote(value.displayName())).append('\n')
                    .append("    failure-policy: required\n")
                    .append("    repeatability: once-per-operation\n");
        }
        yaml.append("""
                command-actions:
                  enabled: false
                  allowed-roots: []
                  blocked-roots: [stop, restart, op, deop]
                  allowed-tokens: []
                  templates: {}
                  maximum-commands: 5
                  maximum-length: 256
                  maximum-depth: 0
                """);
        return yaml.toString();
    }

    private static String lifecycle(Session session) {
        SetupPrestige prestige = session.prestige();
        StringBuilder yaml = new StringBuilder("""
                # Created by the MaddPrestige setup wizard; reset consequences are explicit.
                schema-version: 4
                prestige:
                """);
        yaml.append("  enabled: ").append(prestige.enabled()).append('\n');
        if (prestige.enabled()) {
            yaml.append("  required-stages: [").append(prestige.requiredStage().orElseThrow().value()).append("]\n")
                    .append("  reset-stage: ").append(prestige.resetStage().orElseThrow().value()).append('\n');
        } else {
            yaml.append("  required-stages: []\n  reset-stage: disabled\n");
        }
        yaml.append("""
                  current-count-increment: 1
                  lifetime-count-increment: 1
                  maximum: unlimited
                  cooldown: PT0S
                  costs: []
                  rewards: []
                  external-resets: {enabled: false}
                  reset-policy:
                    progression-stage: RESET
                    active-requirement-progress: RESET
                    latched-completions: RESET
                    baselines: RESET
                    prestige-scoped-currency: RESET
                    purchased-perks: PRESERVE
                    milestone-history: PRESERVE
                    season-progress: PRESERVE
                    historical-statistics: PRESERVE
                currencies: {}
                entitlements: {}
                milestones: {}
                seasons: {}
                competition: {enabled: false}
                """);
        return yaml.toString();
    }

    private static String integrations() {
        return """
                schema-version: 5
                vault: {enabled: false}
                mcmmo: {enabled: false}
                placeholderapi:
                  output: {enabled: false}
                  inputs: {}
                economyshopgui:
                  compatibility-enabled: false
                  progression-credit: {enabled: false}
                quickshop:
                  compatibility-enabled: false
                  progression-credit: {enabled: false}
                """;
    }

    private static List<String> experience(Session session) {
        ArrayList<String> lines = new ArrayList<>();
        for (int index = 0; index < session.stages().size() - 1; index++) {
            lines.add(session.stages().get(index).displayName() + " → "
                    + session.stages().get(index + 1).displayName());
        }
        lines.add("Requirement: " + session.requirement().map(value -> value.id().value()).orElse("none"));
        lines.add("Cost: " + session.cost().map(value -> value.id().value()).orElse("none"));
        lines.add("Reward: " + session.reward().map(value -> value.id().value()).orElse("none"));
        lines.add("Prestige: " + (session.prestige().enabled()
                ? "eligible at " + session.prestige().requiredStage().orElseThrow().value()
                        + ", resets to " + session.prestige().resetStage().orElseThrow().value()
                : "disabled"));
        return List.copyOf(lines);
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        value.codePoints().forEach(character -> {
            if (character == '\\' || character == '"') {
                result.append('\\');
            }
            if (character == '\n') {
                result.append("\\n");
            } else if (character == '\r') {
                result.append("\\r");
            } else if (character == '\t') {
                result.append("\\t");
            } else {
                result.appendCodePoint(character);
            }
        });
        return result.append('"').toString();
    }

    private static void rejectControlCharacters(String value, String field) {
        if (value.codePoints().anyMatch(character -> Character.isISOControl(character)
                && character != '\n' && character != '\r' && character != '\t')) {
            throw new AdministrationException("setup.text.control_character",
                    "The " + field + " contains an unsupported control character.",
                    "Use printable Unicode text; line breaks and tabs will be escaped safely.");
        }
    }

    private record Session(
            PermissionSubject subject,
            Optional<ProviderId> providerId,
            List<SetupStage> stages,
            Optional<net.maddkraft.maddprestige.api.id.StageId> baseline,
            Optional<SetupRequirement> requirement,
            Optional<SetupCost> cost,
            Optional<SetupReward> reward,
            SetupPrestige prestige,
            Optional<UUID> draftId,
            Instant expiresAt) {
        private Session {
            subject = Objects.requireNonNull(subject, "subject");
            providerId = Objects.requireNonNull(providerId, "provider ID");
            stages = List.copyOf(Objects.requireNonNull(stages, "stages"));
            baseline = Objects.requireNonNull(baseline, "baseline");
            requirement = Objects.requireNonNull(requirement, "requirement");
            cost = Objects.requireNonNull(cost, "cost");
            reward = Objects.requireNonNull(reward, "reward");
            prestige = Objects.requireNonNull(prestige, "prestige");
            draftId = Objects.requireNonNull(draftId, "draft ID");
            expiresAt = Objects.requireNonNull(expiresAt, "expiry");
        }

        private Session withProvider(Optional<ProviderId> replacement) {
            return replace(replacement, stages, baseline, requirement, cost, reward, prestige, Optional.empty());
        }

        private Session withStages(
                List<SetupStage> replacement,
                Optional<net.maddkraft.maddprestige.api.id.StageId> replacementBaseline) {
            return replace(providerId, replacement, replacementBaseline, requirement, cost, reward, prestige,
                    Optional.empty());
        }

        private Session withBaseline(net.maddkraft.maddprestige.api.id.StageId replacement) {
            return replace(providerId, stages, Optional.of(replacement), requirement, cost, reward, prestige,
                    Optional.empty());
        }

        private Session withRequirement(SetupRequirement replacement) {
            return replace(providerId, stages, baseline, Optional.of(replacement), cost, reward, prestige,
                    Optional.empty());
        }

        private Session withCost(SetupCost replacement) {
            return replace(providerId, stages, baseline, requirement, Optional.of(replacement), reward, prestige,
                    Optional.empty());
        }

        private Session withReward(SetupReward replacement) {
            return replace(providerId, stages, baseline, requirement, cost, Optional.of(replacement), prestige,
                    Optional.empty());
        }

        private Session withPrestige(SetupPrestige replacement) {
            return replace(providerId, stages, baseline, requirement, cost, reward, replacement, Optional.empty());
        }

        private Session withDraft(UUID replacement) {
            return replace(providerId, stages, baseline, requirement, cost, reward, prestige,
                    Optional.of(replacement));
        }

        private Session replace(
                Optional<ProviderId> replacementProvider,
                List<SetupStage> replacementStages,
                Optional<net.maddkraft.maddprestige.api.id.StageId> replacementBaseline,
                Optional<SetupRequirement> replacementRequirement,
                Optional<SetupCost> replacementCost,
                Optional<SetupReward> replacementReward,
                SetupPrestige replacementPrestige,
                Optional<UUID> replacementDraft) {
            return new Session(subject, replacementProvider, replacementStages, replacementBaseline,
                    replacementRequirement, replacementCost, replacementReward, replacementPrestige,
                    replacementDraft, expiresAt);
        }
    }
}
