package net.maddkraft.maddprestige.core.admin.setup;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationAdministrationService;
import net.maddkraft.maddprestige.core.admin.config.PreparedConfigurationAcknowledgement;
import net.maddkraft.maddprestige.core.admin.config.StoredConfigurationRevision;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.requirement.CompletionMode;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;

public final class SetupWizardService {
    private static final String ANY_METRIC = "*";
    private static final String VAULT = "vault";
    private static final String MCMO = "mcmmo";
    private static final String GRIEF_PREVENTION = "griefprevention";
    private static final String GRIEF_PREVENTION_CLAIMS = "griefprevention_claims";
    private static final String CRAFT_ENGINE = "craftengine";
    private static final Map<SetupSelection, BuiltInSetupRule> BUILT_IN_SETUP_RULES = Map.ofEntries(
            requirement("vault_balance", "balance", VAULT),
            requirement(MCMO, "total_level", MCMO),
            unsupportedRequirement(MCMO, "skill_level", MCMO, "skill filter"),
            requirement("phase5_events", "mcmmo_adjusted_xp_total", MCMO),
            requirement(GRIEF_PREVENTION_CLAIMS, "remaining_claim_blocks", GRIEF_PREVENTION),
            requirement(GRIEF_PREVENTION_CLAIMS, "accrued_claim_blocks", GRIEF_PREVENTION),
            requirement(GRIEF_PREVENTION_CLAIMS, "bonus_claim_blocks", GRIEF_PREVENTION),
            requirement(GRIEF_PREVENTION_CLAIMS, "owned_claim_count", GRIEF_PREVENTION),
            unsupportedRequirement("placeholder_input", ANY_METRIC, "placeholderapi",
                    "placeholder, value type, and maximum age"),
            unsupportedRequirement("worldguard_region", "inside_region", "worldguard", "region-id filter"),
            unsupportedRequirement("craftengine_item_count", "item_count", CRAFT_ENGINE, "item-id filter"),
            action(SetupSelectionKind.COST, "vault_economy_cost", VAULT),
            action(SetupSelectionKind.REWARD, "vault_economy_reward", VAULT),
            action(SetupSelectionKind.REWARD, "griefprevention_claim_blocks_reward", GRIEF_PREVENTION),
            unsupportedAction(SetupSelectionKind.REWARD, "craftengine_item_reward", CRAFT_ENGINE,
                    "item-id metadata"));

    private final ConfigurationAdministrationService configuration;
    private final Optional<ProviderRegistry> providers;
    private final Clock clock;
    private final Duration lifetime;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<Actor, UUID> currentSessions = new ConcurrentHashMap<>();
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
                            snapshot.activation() == ActivationState.ACTIVE, healthy, metrics,
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
                Map.of(), Optional.empty(), Optional.empty(), SetupPrestige.disabled(), Optional.empty(),
                Instant.now(clock).plus(lifetime)));
        currentSessions.put(subject.actor(), id);
        return id;
    }

    /** Returns the current unexpired setup session owned by the actor. */
    public UUID currentSession(PermissionSubject subject) {
        subject.require(PhaseSixPermissions.SETUP);
        pruneExpired();
        UUID sessionId = currentSessions.get(subject.actor());
        if (sessionId == null || !sessions.containsKey(sessionId)) {
            throw setupFailure("setup.session.unknown",
                    "This administrator has no current setup session.",
                    "Run /maddprestige setup start, then continue without pasting its UUID.");
        }
        require(subject, sessionId);
        return sessionId;
    }

    /** Returns an in-memory snapshot used only for contextual tab completion. */
    public Optional<SetupCompletion> currentCompletion(PermissionSubject subject) {
        if (!subject.has(PhaseSixPermissions.SETUP)) {
            return Optional.empty();
        }
        UUID sessionId = currentSessions.get(subject.actor());
        Session session = sessionId == null ? null : sessions.get(sessionId);
        return session == null || !Instant.now(clock).isBefore(session.expiresAt())
                || !session.subject().actor().equals(subject.actor()) ? Optional.empty()
                : Optional.of(new SetupCompletion(sessionId,
                        session.stages().stream().map(stage -> stage.id().value()).toList()));
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
                    "Choose a unique immutable stage ID.", "stage", stage.id().value());
        }
        if (session.providerId().isEmpty() && stage.externalGroup().isPresent()) {
            throw new AdministrationException("setup.provider.required",
                    "An external group cannot be selected without a rank provider.",
                    "Choose the provider first or configure this stage as internal-only.",
                    "stage", stage.id().value(), "group", stage.externalGroup().orElseThrow());
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
                    + baseline.value(), "Select one of the configured stage IDs.", "stage", baseline.value());
        }
        discardDraft(subject, session);
        sessions.put(sessionId, session.withBaseline(baseline));
    }

    public void configureRequirement(PermissionSubject subject, UUID sessionId, SetupRequirement requirement) {
        Session session = require(subject, sessionId);
        SetupRequirement replacement = canonicalRequirement(Objects.requireNonNull(requirement, "requirement"));
        rejectUnconfigurableRequirement(replacement);
        discardDraft(subject, session);
        sessions.put(sessionId, session.withRequirement(replacement));
    }

    public void configureRequirementForStage(
            PermissionSubject subject,
            UUID sessionId,
            net.maddkraft.maddprestige.api.id.StageId stageId,
            SetupRequirement requirement) {
        Session session = require(subject, sessionId);
        requireConfiguredStage(session, stageId, "requirement target");
        if (session.baseline().filter(stageId::equals).isPresent()) {
            throw new AdministrationException("setup.requirement.baseline",
                    "The baseline stage cannot require eligibility progress.",
                    "Choose a stage after the baseline.", "stage", stageId.value());
        }
        SetupRequirement replacement = canonicalRequirement(Objects.requireNonNull(requirement, "requirement"));
        rejectUnconfigurableRequirement(replacement);
        boolean duplicate = session.requirement().filter(value -> value.id().equals(replacement.id())).isPresent()
                || session.stageRequirements().entrySet().stream()
                        .anyMatch(entry -> !entry.getKey().equals(stageId)
                                && entry.getValue().id().equals(replacement.id()));
        if (duplicate) {
            throw new AdministrationException("setup.requirement.duplicate",
                    "Requirement ID is already assigned in this setup session: " + replacement.id().value(),
                    "Choose one unique immutable requirement ID per stage.",
                    "requirement", replacement.id().value(), "stage", stageId.value());
        }
        discardDraft(subject, session);
        sessions.put(sessionId, session.withStageRequirement(stageId, replacement));
    }

    /** Configures the normal owner-facing Paper playtime requirement with canonical safe defaults. */
    public void configurePlaytimeRequirement(
            PermissionSubject subject,
            UUID sessionId,
            net.maddkraft.maddprestige.api.id.StageId stageId,
            String target) {
        Objects.requireNonNull(stageId, "stage ID");
        String suffix = "_playtime";
        String candidate = stageId.value() + suffix;
        if (candidate.length() > 64) {
            String fingerprint = Integer.toUnsignedString(stageId.value().hashCode(), 36);
            candidate = stageId.value().substring(0, 64 - suffix.length() - fingerprint.length() - 1)
                    + "_" + fingerprint + suffix;
        }
        configureRequirementForStage(subject, sessionId, stageId, new SetupRequirement(
                new RequirementId(candidate), new ProviderId("paper_statistics"),
                new MetricId("play_one_minute"), "GREATER_OR_EQUAL", target,
                "SINCE_PRESTIGE_START", "LIVE"));
    }

    public void configureCost(PermissionSubject subject, UUID sessionId, SetupCost cost) {
        Session session = require(subject, sessionId);
        discardDraft(subject, session);
        sessions.put(sessionId, session.withCost(Objects.requireNonNull(cost, "cost")));
    }

    public void configureReward(PermissionSubject subject, UUID sessionId, SetupReward reward) {
        Session session = require(subject, sessionId);
        rejectUnconfigurableReward(Objects.requireNonNull(reward, "reward"));
        discardDraft(subject, session);
        sessions.put(sessionId, session.withReward(reward));
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
        if (!session.stages().isEmpty() && (session.stages().size() < 2 || session.baseline().isEmpty())) {
            throw new AdministrationException("setup.incomplete",
                    "A simple ladder needs at least two ordered stages and one baseline.",
                    "Add stages in progression order and select the baseline stage.");
        }
        Optional<SetupStage> missingGroup = session.providerId().isPresent()
                ? session.stages().stream().skip(1).filter(stage -> stage.externalGroup().isEmpty()).findFirst()
                : Optional.empty();
        if (missingGroup.isPresent()) {
            throw new AdministrationException("setup.group.missing",
                    "Every projected stage after the baseline needs an existing external group.",
                    "Select existing groups; MaddPrestige never creates them.",
                    "stage", missingGroup.orElseThrow().id().value(),
                    "provider", session.providerId().orElseThrow().value());
        }
        discardDraft(subject, session);
        UUID draftId = configuration.beginInitialDraft(subject, documents(session), "setup-wizard");
        sessions.put(sessionId, session.withDraft(draftId));
        return configuration.preview(subject, draftId).thenApply(preview -> new SetupPreview(
                sessionId, draftId, preview, experience(session)));
    }

    /** Returns the exact ordered candidate bytes that preview/apply will consume, without publishing them. */
    public Map<String, String> generatedDocuments(PermissionSubject subject, UUID sessionId) {
        return documents(require(subject, sessionId));
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
                    currentSessions.remove(subject.actor(), sessionId);
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
            currentSessions.remove(subject.actor(), sessionId);
            return revision;
        });
    }

    public void cancel(PermissionSubject subject, UUID sessionId) {
        Session session = require(subject, sessionId);
        discardDraft(subject, session);
        sessions.remove(sessionId);
        currentSessions.remove(subject.actor(), sessionId);
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
                currentSessions.remove(session.subject().actor(), id);
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
                    "Select one of the configured stage IDs.", "stage", stage.value(), "purpose", purpose);
        }
    }

    private Map<String, String> documents(Session session) {
        LinkedHashMap<String, String> documents = new LinkedHashMap<>();
        documents.put("progression.yml", progression(session));
        documents.put("requirements.yml", requirements(session));
        documents.put("rewards.yml", rewards(session));
        documents.put("lifecycle.yml", lifecycle(session));
        documents.put("integrations.yml", integrations(session));
        return Collections.unmodifiableMap(documents);
    }

    private static String progression(Session session) {
        if (session.stages().isEmpty()) {
            return """
                    schema-version: 3
                    """;
        }
        StringBuilder yaml = new StringBuilder("""
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
                Optional<SetupRequirement> requirement = Optional.ofNullable(
                        session.stageRequirements().get(stage.id())).or(() -> session.requirement());
                requirement.ifPresent(value -> yaml.append("    requirements: ")
                        .append(session.stageRequirements().containsKey(stage.id())
                                ? treeId(stage.id()) : "setup_eligibility")
                        .append('\n'));
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

    private String requirements(Session session) {
        if (session.stages().isEmpty()) {
            return numericRequirements(session);
        }
        StringBuilder yaml = new StringBuilder("""
                schema-version: 3
                maximum-depth: 16
                """);
        LinkedHashMap<net.maddkraft.maddprestige.api.id.RequirementId, SetupRequirement> definitions =
                new LinkedHashMap<>();
        session.requirement().ifPresent(value -> definitions.put(value.id(), value));
        session.stages().stream().map(SetupStage::id).map(session.stageRequirements()::get)
                .filter(Objects::nonNull).forEach(value -> definitions.put(value.id(), value));
        if (definitions.isEmpty()) {
            yaml.append("requirements: {}\ntrees: {}\n");
        } else {
            yaml.append("requirements:\n");
            definitions.values().forEach(value -> appendRequirement(yaml, value,
                    value.valueType().orElseGet(() -> requirementValueType(value)).name()));
            yaml.append("trees:\n");
            session.requirement().ifPresent(value -> appendTree(yaml, "setup_eligibility", value));
            session.stages().stream().map(SetupStage::id).forEach(stage -> Optional.ofNullable(
                    session.stageRequirements().get(stage)).ifPresent(value -> appendTree(yaml, treeId(stage), value)));
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

    private String numericRequirements(Session session) {
        StringBuilder yaml = new StringBuilder("schema-version: 3\n");
        session.requirement().ifPresent(value -> {
            yaml.append("requirements:\n");
            appendCompactRequirement(yaml, value, value.valueType()
                    .orElseGet(() -> requirementValueType(value)).name());
            yaml.append("trees:\n");
            appendCompactTree(yaml, "setup_eligibility", value);
        });
        session.cost().ifPresent(value -> {
            yaml.append("costs:\n  ").append(value.id().value()).append(":\n")
                    .append("    provider: ").append(value.providerId().value()).append('\n');
            appendUnless(yaml, "type", value.type(), "value");
            appendUnless(yaml, "value-type", value.valueType(), "EXACT_DECIMAL");
            yaml.append("    amount: ").append(quote(value.amount())).append('\n');
            appendUnless(yaml, "display-name", value.displayName(), value.id().value());
        });
        return yaml.toString();
    }

    private static void appendCompactRequirement(StringBuilder yaml, SetupRequirement value, String valueType) {
        yaml.append("  ").append(value.id().value()).append(":\n")
                .append("    provider: ").append(value.providerId().value()).append('\n')
                .append("    metric: ").append(value.metricId().value()).append('\n')
                .append("    value-type: ").append(valueType).append('\n');
        appendUnless(yaml, "operator", value.operator(), "GREATER_OR_EQUAL");
        yaml.append("    target: ").append(scalar(value.target())).append('\n');
        appendUnless(yaml, "scope", value.scope(), "ABSOLUTE");
        appendUnless(yaml, "completion", value.completion(), "LIVE");
    }

    private static void appendCompactTree(StringBuilder yaml, String treeId, SetupRequirement value) {
        yaml.append("  ").append(treeId).append(":\n")
                .append("    children:\n")
                .append("      - requirement: ").append(value.id().value()).append('\n');
    }

    private static void appendRequirement(StringBuilder yaml, SetupRequirement value, String valueType) {
        yaml.append("  ").append(value.id().value()).append(":\n")
                .append("    provider: ").append(value.providerId().value()).append('\n')
                .append("    metric: ").append(value.metricId().value()).append('\n')
                .append("    value-type: ").append(valueType).append('\n')
                .append("    operator: ").append(scalar(value.operator())).append('\n')
                .append("    target: ").append(scalar(value.target())).append('\n')
                .append("    scope: ").append(scalar(value.scope())).append('\n')
                .append("    completion: ").append(scalar(value.completion())).append('\n');
    }

    private static void appendTree(StringBuilder yaml, String treeId, SetupRequirement value) {
        yaml.append("  ").append(treeId).append(":\n")
                .append("    mode: all\n")
                .append("    children:\n")
                .append("      - requirement: ").append(value.id().value()).append('\n');
    }

    private static String treeId(net.maddkraft.maddprestige.api.id.StageId stageId) {
        return "setup_eligibility_" + stageId.value();
    }

    private static String rewards(Session session) {
        if (session.stages().isEmpty()) {
            return numericRewards(session);
        }
        StringBuilder yaml = new StringBuilder("""
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

    private static String numericRewards(Session session) {
        StringBuilder yaml = new StringBuilder("schema-version: 3\n");
        session.reward().ifPresent(value -> {
            yaml.append("rewards:\n  ").append(value.id().value()).append(":\n")
                    .append("    provider: ").append(value.providerId().value()).append('\n');
            appendUnless(yaml, "type", value.type(), "value");
            appendUnless(yaml, "value-type", value.valueType(), "EXACT_DECIMAL");
            yaml.append("    value: ").append(quote(value.value())).append('\n');
            appendUnless(yaml, "display-name", value.displayName(), value.id().value());
        });
        return yaml.toString();
    }

    private static String lifecycle(Session session) {
        if (session.stages().isEmpty()) {
            return numericLifecycle(session);
        }
        SetupPrestige prestige = session.prestige();
        StringBuilder yaml = new StringBuilder("""
                schema-version: 4
                prestige:
                """);
        yaml.append("  enabled: ").append(prestige.enabled()).append('\n');
        session.requirement().ifPresent(value -> yaml.append("  requirement-tree: setup_eligibility\n"));
        yaml.append("  costs: ");
        yaml.append(session.cost().map(value -> "[" + value.id().value() + "]").orElse("[]")).append('\n');
        yaml.append("  rewards: ");
        yaml.append(session.reward().map(value -> "[" + value.id().value() + "]").orElse("[]")).append('\n');
        if (!session.stages().isEmpty()) {
            yaml.append("  current-count-increment: 1\n")
                    .append("  lifetime-count-increment: 1\n");
        }
        yaml.append("""
                  maximum: unlimited
                  cooldown: PT0S
                  cost-scaling: {}
                  reward-scaling: {}
                  external-resets:
                    enabled: false
                  reset-policy:
                """);
        if (!session.stages().isEmpty()) {
            yaml.append("    progression-stage: PRESERVE\n");
        }
        yaml.append("""
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
                competition:
                  enabled: false
                """);
        return yaml.toString();
    }

    private static String numericLifecycle(Session session) {
        StringBuilder yaml = new StringBuilder("schema-version: 4\n");
        if (!session.prestige().enabled()) {
            return yaml.toString();
        }
        yaml.append("prestige:\n  enabled: true\n");
        session.requirement().ifPresent(value -> yaml.append("  requirement-tree: setup_eligibility\n"));
        session.cost().ifPresent(value -> yaml.append("  costs: [").append(value.id().value()).append("]\n"));
        session.reward().ifPresent(value -> yaml.append("  rewards: [").append(value.id().value()).append("]\n"));
        return yaml.toString();
    }

    private static String integrations(Session session) {
        if (session.stages().isEmpty()) {
            return numericIntegrations(session);
        }
        Set<String> selected = selectedIntegrationComponents(session);
        boolean vault = selected.contains("vault");
        boolean mcMmo = selected.contains("mcmmo");
        boolean griefPrevention = selected.contains("griefprevention");
        boolean worldGuard = selected.contains("worldguard");
        boolean craftEngine = selected.contains("craftengine");
        return """
                schema-version: 7
                vault:
                  enabled: %s
                mcmmo:
                  enabled: %s
                placeholderapi:
                  output:
                    enabled: false
                  inputs: {}
                economyshopgui:
                  compatibility-enabled: false
                  progression-credit:
                    enabled: false
                quickshop:
                  compatibility-enabled: false
                  progression-credit:
                    enabled: false
                griefprevention:
                  enabled: %s
                worldguard:
                  enabled: %s
                craftengine:
                  enabled: %s
                  reward-maximum-quantity: 2304
                """.formatted(vault, mcMmo, griefPrevention, worldGuard, craftEngine);
    }

    private static String numericIntegrations(Session session) {
        Set<String> selected = selectedIntegrationComponents(session);
        StringBuilder yaml = new StringBuilder("schema-version: 7\n");
        for (String integration : List.of("vault", "mcmmo", "griefprevention", "worldguard", "craftengine")) {
            if (selected.contains(integration)) {
                yaml.append(integration).append(":\n  enabled: true\n");
            }
        }
        return yaml.toString();
    }

    private static void appendUnless(StringBuilder yaml, String key, String value, String defaultValue) {
        String normalized = value.toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        if (!normalized.equals(defaultValue.toUpperCase(java.util.Locale.ROOT).replace('-', '_'))) {
            yaml.append("    ").append(key).append(": ").append(quote(value)).append('\n');
        }
    }

    private static Set<String> selectedIntegrationComponents(Session session) {
        java.util.HashSet<String> result = new java.util.HashSet<>();
        session.requirement().ifPresent(requirement -> addIntegration(result, rule(requirement),
                requirement.providerId().value()));
        session.stageRequirements().values().forEach(requirement ->
                addIntegration(result, rule(requirement), requirement.providerId().value()));
        session.cost().ifPresent(cost -> addIntegration(result,
                rule(new SetupSelection(SetupSelectionKind.COST, cost.providerId().value(), "")),
                cost.providerId().value()));
        session.reward().ifPresent(reward -> addIntegration(result, rule(reward), reward.providerId().value()));
        return Set.copyOf(result);
    }

    private static void rejectUnconfigurableRequirement(SetupRequirement requirement) {
        validateRule(rule(requirement), requirement.providerId().value());
    }

    private static void rejectUnconfigurableReward(SetupReward reward) {
        validateRule(rule(reward), reward.providerId().value());
    }

    private static Optional<BuiltInSetupRule> rule(SetupRequirement requirement) {
        String provider = requirement.providerId().value();
        BuiltInSetupRule exact = BUILT_IN_SETUP_RULES.get(new SetupSelection(SetupSelectionKind.REQUIREMENT,
                provider, requirement.metricId().value()));
        return Optional.ofNullable(exact == null ? BUILT_IN_SETUP_RULES.get(
                new SetupSelection(SetupSelectionKind.REQUIREMENT, provider, ANY_METRIC)) : exact);
    }

    private static Optional<BuiltInSetupRule> rule(SetupReward reward) {
        return rule(new SetupSelection(SetupSelectionKind.REWARD, reward.providerId().value(), ""));
    }

    private static Optional<BuiltInSetupRule> rule(SetupSelection selection) {
        return Optional.ofNullable(BUILT_IN_SETUP_RULES.get(selection));
    }

    private static void addIntegration(
            Set<String> integrations,
            Optional<BuiltInSetupRule> candidate,
            String provider) {
        validateRule(candidate, provider);
        candidate.map(BuiltInSetupRule::integration).ifPresent(integrations::add);
    }

    private static void validateRule(Optional<BuiltInSetupRule> candidate, String provider) {
        candidate.flatMap(BuiltInSetupRule::requiredParameters).ifPresent(required -> {
            BuiltInSetupRule rule = candidate.orElseThrow();
            throw unconfigurable(provider, rule.integration(), required);
        });
    }

    static Map<String, String> builtInSetupSelectionAudit() {
        return BUILT_IN_SETUP_RULES.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                entry -> entry.getKey().auditIdentity(),
                entry -> entry.getValue().auditIdentity()));
    }

    private static Map.Entry<SetupSelection, BuiltInSetupRule> requirement(
            String provider, String metric, String integration) {
        return Map.entry(new SetupSelection(SetupSelectionKind.REQUIREMENT, provider, metric),
                new BuiltInSetupRule(integration, Optional.empty()));
    }

    private static Map.Entry<SetupSelection, BuiltInSetupRule> unsupportedRequirement(
            String provider, String metric, String integration, String requiredParameters) {
        return Map.entry(new SetupSelection(SetupSelectionKind.REQUIREMENT, provider, metric),
                new BuiltInSetupRule(integration, Optional.of(requiredParameters)));
    }

    private static Map.Entry<SetupSelection, BuiltInSetupRule> action(
            SetupSelectionKind kind, String provider, String integration) {
        return Map.entry(new SetupSelection(kind, provider, ""),
                new BuiltInSetupRule(integration, Optional.empty()));
    }

    private static Map.Entry<SetupSelection, BuiltInSetupRule> unsupportedAction(
            SetupSelectionKind kind, String provider, String integration, String requiredParameters) {
        return Map.entry(new SetupSelection(kind, provider, ""),
                new BuiltInSetupRule(integration, Optional.of(requiredParameters)));
    }

    private enum SetupSelectionKind {
        REQUIREMENT,
        COST,
        REWARD
    }

    private record SetupSelection(SetupSelectionKind kind, String provider, String metric) {
        private String auditIdentity() {
            return kind.name().toLowerCase(java.util.Locale.ROOT) + ":" + provider
                    + (metric.isEmpty() ? "" : ":" + metric);
        }
    }

    private record BuiltInSetupRule(String integration, Optional<String> requiredParameters) {
        private String auditIdentity() {
            return requiredParameters.map(required -> "reject:" + integration + ":" + required)
                    .orElse("enable:" + integration);
        }
    }

    private static AdministrationException unconfigurable(
            String provider, String integration, String requiredParameters) {
        return new AdministrationException("setup.integration.unconfigurable",
                "The generic setup wizard cannot define the " + requiredParameters + " required by " + provider
                        + ".",
                "Create and validate a canonical configuration draft with those exact parameters instead.",
                "provider", provider, "component", integration, "requirement", requiredParameters);
    }

    private SetupRequirement canonicalRequirement(SetupRequirement requirement) {
        MetricValueType valueType = requirementValueType(requirement);
        MetricOperator operator = requirementOperator(requirement, valueType);
        if (!operator.supports(valueType) || operator == MetricOperator.IN_RANGE) {
            throw invalidRequirementOperator(requirement, valueType, operator.name());
        }
        MetricValue target;
        try {
            target = MetricValue.parse(valueType, requirement.target().strip());
        } catch (RuntimeException exception) {
            throw invalidRequirementTarget(requirement, valueType);
        }
        MeasurementScope scope = requirementScope(requirement);
        CompletionMode completion = requirementCompletion(requirement);
        return new SetupRequirement(requirement.id(), requirement.providerId(), requirement.metricId(),
                operator.name(), target.canonical(), scope.name(), completion.name(), Optional.of(valueType));
    }

    private static AdministrationException setupFailure(
            String code, String summary, String remediation, Object... facts) {
        return new AdministrationException(code, summary, remediation, facts);
    }

    private static MetricOperator requirementOperator(SetupRequirement requirement, MetricValueType valueType) {
        try {
            return enumValue(MetricOperator.class, requirement.operator());
        } catch (RuntimeException exception) {
            throw invalidRequirementOperator(requirement, valueType, requirement.operator());
        }
    }

    private static MeasurementScope requirementScope(SetupRequirement requirement) {
        try {
            return enumValue(MeasurementScope.class, requirement.scope());
        } catch (RuntimeException exception) {
            throw new AdministrationException("setup.requirement.scope.invalid",
                    "Unknown setup measurement scope: " + requirement.scope(),
                    "Use one of the documented measurement scopes.",
                    "scope", requirement.scope(), "allowed", enumNames(MeasurementScope.class),
                    "provider", requirement.providerId().value(), "metric", requirement.metricId().value());
        }
    }

    private static CompletionMode requirementCompletion(SetupRequirement requirement) {
        try {
            return enumValue(CompletionMode.class, requirement.completion());
        } catch (RuntimeException exception) {
            throw new AdministrationException("setup.requirement.completion.invalid",
                    "Unknown setup completion mode: " + requirement.completion(),
                    "Use one of the documented completion modes.",
                    "completion", requirement.completion(), "allowed", enumNames(CompletionMode.class),
                    "provider", requirement.providerId().value(), "metric", requirement.metricId().value());
        }
    }

    private static AdministrationException invalidRequirementTarget(
            SetupRequirement requirement, MetricValueType valueType) {
        return new AdministrationException("setup.requirement.target.invalid",
                "Target " + requirement.target() + " is not a valid " + valueType + " value.",
                valueType == MetricValueType.DURATION
                        ? "Use ISO-8601 such as PT1M or PT3M, or a supported short duration such as 1m or 3m."
                        : "Use a target accepted by the selected metric type.",
                "provider", requirement.providerId().value(), "metric", requirement.metricId().value(),
                "type", valueType, "target", requirement.target());
    }

    private static AdministrationException invalidRequirementOperator(
            SetupRequirement requirement, MetricValueType valueType, String operator) {
        return new AdministrationException("setup.requirement.operator.invalid",
                "Operator " + operator + " is not supported by this single-value " + valueType + " target.",
                "Use one compatible single-value operator.",
                "provider", requirement.providerId().value(), "metric", requirement.metricId().value(),
                "type", valueType, "operator", operator, "allowed", compatibleOperators(valueType));
    }

    private static String compatibleOperators(MetricValueType valueType) {
        return java.util.Arrays.stream(MetricOperator.values())
                .filter(operator -> operator != MetricOperator.IN_RANGE && operator.supports(valueType))
                .map(Enum::name).collect(java.util.stream.Collectors.joining(", "));
    }

    private static <T extends Enum<T>> String enumNames(Class<T> type) {
        return java.util.Arrays.stream(type.getEnumConstants()).map(Enum::name)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return Enum.valueOf(type, value.strip().replace('-', '_').toUpperCase(java.util.Locale.ROOT));
    }

    private MetricValueType requirementValueType(SetupRequirement requirement) {
        return providers.flatMap(registry -> registry.provider(requirement.providerId()))
                .filter(MetricProvider.class::isInstance).map(MetricProvider.class::cast)
                .flatMap(provider -> provider.metrics().stream()
                        .filter(metric -> metric.metricId().equals(requirement.metricId())).findFirst())
                .map(metric -> metric.valueType())
                .orElseThrow(() -> new AdministrationException("setup.requirement.metric_unknown",
                        "The selected setup metric has no authoritative value type: "
                                + requirement.providerId().value() + ":" + requirement.metricId().value(),
                        "Run setup discover and choose one metric advertised by an active provider.",
                        "provider", requirement.providerId().value(), "metric", requirement.metricId().value()));
    }

    private static List<MessageReference> experience(Session session) {
        ArrayList<MessageReference> lines = new ArrayList<>();
        for (int index = 0; index < session.stages().size() - 1; index++) {
            lines.add(MessageReference.of("command.setup.experience_transition",
                    "current", session.stages().get(index).displayName(),
                    "target", session.stages().get(index + 1).displayName()));
        }
        lines.add(MessageReference.of("command.setup.experience_requirement", "id",
                session.requirement().map(value -> value.id().value()).orElse("NONE")));
        session.stageRequirements().forEach((stage, requirement) ->
                lines.add(MessageReference.of("command.setup.experience_stage_requirement",
                        "stage", stage.value(), "id", requirement.id().value())));
        lines.add(MessageReference.of("command.setup.experience_cost", "id",
                session.cost().map(value -> value.id().value()).orElse("NONE")));
        lines.add(MessageReference.of("command.setup.experience_reward", "id",
                session.reward().map(value -> value.id().value()).orElse("NONE")));
        lines.add(session.prestige().enabled()
                ? MessageReference.of("command.setup.experience_prestige_enabled")
                : MessageReference.of("command.setup.experience_prestige_disabled"));
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

    private static String scalar(String value) {
        return value.matches("[A-Za-z0-9_.:-]+") ? value : quote(value);
    }

    private static void rejectControlCharacters(String value, String field) {
        if (value.codePoints().anyMatch(character -> Character.isISOControl(character)
                && character != '\n' && character != '\r' && character != '\t')) {
            throw new AdministrationException("setup.text.control_character",
                    "The " + field + " contains an unsupported control character.",
                    "Use printable Unicode text; line breaks and tabs will be escaped safely.",
                    "component", field);
        }
    }

    public record SetupCompletion(UUID sessionId, List<String> stageIds) {
        public SetupCompletion {
            sessionId = Objects.requireNonNull(sessionId, "session ID");
            stageIds = List.copyOf(Objects.requireNonNull(stageIds, "stage IDs"));
        }
    }

    private record Session(
            PermissionSubject subject,
            Optional<ProviderId> providerId,
            List<SetupStage> stages,
            Optional<net.maddkraft.maddprestige.api.id.StageId> baseline,
            Optional<SetupRequirement> requirement,
            Map<net.maddkraft.maddprestige.api.id.StageId, SetupRequirement> stageRequirements,
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
            stageRequirements = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(stageRequirements, "stage requirements")));
            cost = Objects.requireNonNull(cost, "cost");
            reward = Objects.requireNonNull(reward, "reward");
            prestige = Objects.requireNonNull(prestige, "prestige");
            draftId = Objects.requireNonNull(draftId, "draft ID");
            expiresAt = Objects.requireNonNull(expiresAt, "expiry");
        }

        private Session withProvider(Optional<ProviderId> replacement) {
            return replace(replacement, stages, baseline, requirement, stageRequirements, cost, reward, prestige,
                    Optional.empty());
        }

        private Session withStages(
                List<SetupStage> replacement,
                Optional<net.maddkraft.maddprestige.api.id.StageId> replacementBaseline) {
            return replace(providerId, replacement, replacementBaseline, requirement, stageRequirements, cost,
                    reward, prestige, Optional.empty());
        }

        private Session withBaseline(net.maddkraft.maddprestige.api.id.StageId replacement) {
            return replace(providerId, stages, Optional.of(replacement), requirement, stageRequirements, cost,
                    reward, prestige, Optional.empty());
        }

        private Session withRequirement(SetupRequirement replacement) {
            return replace(providerId, stages, baseline, Optional.of(replacement), stageRequirements, cost, reward,
                    prestige, Optional.empty());
        }

        private Session withStageRequirement(
                net.maddkraft.maddprestige.api.id.StageId stageId,
                SetupRequirement replacement) {
            LinkedHashMap<net.maddkraft.maddprestige.api.id.StageId, SetupRequirement> requirements =
                    new LinkedHashMap<>(stageRequirements);
            requirements.put(stageId, replacement);
            return replace(providerId, stages, baseline, requirement, requirements, cost, reward, prestige,
                    Optional.empty());
        }

        private Session withCost(SetupCost replacement) {
            return replace(providerId, stages, baseline, requirement, stageRequirements, Optional.of(replacement),
                    reward, prestige, Optional.empty());
        }

        private Session withReward(SetupReward replacement) {
            return replace(providerId, stages, baseline, requirement, stageRequirements, cost,
                    Optional.of(replacement), prestige, Optional.empty());
        }

        private Session withPrestige(SetupPrestige replacement) {
            return replace(providerId, stages, baseline, requirement, stageRequirements, cost, reward, replacement,
                    Optional.empty());
        }

        private Session withDraft(UUID replacement) {
            return replace(providerId, stages, baseline, requirement, stageRequirements, cost, reward, prestige,
                    Optional.of(replacement));
        }

        private Session replace(
                Optional<ProviderId> replacementProvider,
                List<SetupStage> replacementStages,
                Optional<net.maddkraft.maddprestige.api.id.StageId> replacementBaseline,
                Optional<SetupRequirement> replacementRequirement,
                Map<net.maddkraft.maddprestige.api.id.StageId, SetupRequirement> replacementStageRequirements,
                Optional<SetupCost> replacementCost,
                Optional<SetupReward> replacementReward,
                SetupPrestige replacementPrestige,
                Optional<UUID> replacementDraft) {
            return new Session(subject, replacementProvider, replacementStages, replacementBaseline,
                    replacementRequirement, replacementStageRequirements, replacementCost, replacementReward,
                    replacementPrestige,
                    replacementDraft, expiresAt);
        }
    }
}
