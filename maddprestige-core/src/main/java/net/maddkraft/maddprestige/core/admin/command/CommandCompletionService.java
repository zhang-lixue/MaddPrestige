package net.maddkraft.maddprestige.core.admin.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.core.admin.OperationConfirmationService;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.admin.setup.SetupWizardService;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.schema.SchemaRegistry;
import net.maddkraft.maddprestige.core.schema.SchemaValueType;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;

public final class CommandCompletionService {
    private static final int MAX_SUGGESTIONS = 50;
    private static final String SETUP = "setup";
    private static final String PRESTIGE = "prestige";
    private static final String DETAILS = "details";
    private static final String CONFIRM = "confirm";
    private static final String CANCEL = "cancel";
    private static final String APPLY = "apply";
    private static final String ACKNOWLEDGE = "acknowledge";
    private final AtomicReference<CompletionCatalog> catalog = new AtomicReference<>(CompletionCatalog.empty());
    private final SetupWizardService setup;
    private final OperationConfirmationService confirmations;

    public CommandCompletionService() {
        this.setup = null;
        this.confirmations = null;
    }

    public CommandCompletionService(SetupWizardService setup) {
        this.setup = Objects.requireNonNull(setup, SETUP);
        this.confirmations = null;
    }

    public CommandCompletionService(SetupWizardService setup, OperationConfirmationService confirmations) {
        this.setup = Objects.requireNonNull(setup, SETUP);
        this.confirmations = Objects.requireNonNull(confirmations, "confirmations");
    }

    public CommandCompletionService(OperationConfirmationService confirmations) {
        this.setup = null;
        this.confirmations = Objects.requireNonNull(confirmations, "confirmations");
    }

    public void refresh(ProviderRegistry providers, SchemaRegistry schema, StageConfiguration stages) {
        Objects.requireNonNull(providers, "providers");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(stages, "stages");
        ArrayList<String> providerIds = new ArrayList<>();
        LinkedHashMap<String, List<String>> metrics = new LinkedHashMap<>();
        providers.snapshots().stream().sorted(Comparator.comparing(value -> value.descriptor().id().value()))
                .forEach(snapshot -> {
                    String providerId = snapshot.descriptor().id().value();
                    providerIds.add(providerId);
                    providers.provider(snapshot.descriptor().id()).filter(MetricProvider.class::isInstance)
                            .map(MetricProvider.class::cast).ifPresent(metricProvider -> {
                                try {
                                    metrics.put(providerId, metricProvider.metrics().stream()
                                            .map(metric -> metric.metricId().value()).sorted().toList());
                                } catch (RuntimeException exception) {
                                    metrics.put(providerId, List.of());
                                }
                            });
                });
        LinkedHashMap<String, List<String>> values = new LinkedHashMap<>();
        schema.nodes().forEach(node -> values.put(node.canonicalPath(), node.allowedValues().staticValues()));
        catalog.set(new CompletionCatalog(stages.stages().keySet().stream().map(id -> id.value()).sorted().toList(),
                providerIds, metrics, schema.nodes().stream().map(node -> node.canonicalPath()).sorted().toList(),
                values, schema.nodes().stream()
                        .filter(node -> node.type() == SchemaValueType.LIST || node.type() == SchemaValueType.MAP)
                        .map(node -> node.canonicalPath()).sorted().toList(), schema.nodes().stream()
                        .filter(node -> node.type() == SchemaValueType.LIST)
                        .map(node -> node.canonicalPath()).sorted().toList(), List.of(), List.of()));
    }

    /** Refreshes server-owned draft/history identifiers outside the tab-completion hot path. */
    public void refreshAuthorities(Set<UUID> draftIds, Set<String> revisionIds) {
        catalog.updateAndGet(current -> current.withAuthorities(Set.copyOf(draftIds), Set.copyOf(revisionIds)));
    }

    public List<String> suggest(PermissionSubject subject, List<String> inputTokens) {
        Objects.requireNonNull(subject, "subject");
        List<String> tokens = List.copyOf(Objects.requireNonNull(inputTokens, "input tokens"));
        String partial = tokens.isEmpty() ? "" : tokens.getLast().toLowerCase(Locale.ROOT);
        List<String> candidates = candidates(subject, tokens);
        return candidates.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(partial))
                .distinct().sorted().limit(MAX_SUGGESTIONS).toList();
    }

    private List<String> candidates(PermissionSubject subject, List<String> tokens) {
        if (tokens.size() <= 1) {
            return rootCommands(subject);
        }
        String root = tokens.getFirst().toLowerCase(Locale.ROOT);
        if (root.equals("config")) {
            return configCandidates(subject, tokens);
        }
        if (root.equals("simulate") && subject.has(PhaseSixPermissions.SIMULATE)) {
            return prestigeDetailsCandidates(tokens);
        }
        if (root.equals("why") && (subject.has(PhaseSixPermissions.USE)
                || subject.has(PhaseSixPermissions.PLAYER_VIEW))) {
            return prestigeDetailsCandidates(tokens);
        }
        if (root.equals(CONFIRM) && tokens.size() == 2 && confirmations != null
                && subject.has(PhaseSixPermissions.PRESTIGE)) {
            return confirmations.validConfirmationIds(subject).stream().map(UUID::toString).toList();
        }
        if (root.equals("help")) {
            return List.of("overview", SETUP, "measurement", "requirements", "scaling", "providers");
        }
        if (root.equals("doctor") && subject.has(PhaseSixPermissions.DOCTOR)) {
            return tokens.size() == 2 ? List.of(DETAILS) : List.of();
        }
        if (root.equals(SETUP) && subject.has(PhaseSixPermissions.SETUP)) {
            return setupCandidates(tokens);
        }
        if (root.equals("staff") && subject.has(PhaseSixPermissions.PLAYER_PRESTIGE_EDIT)) {
            if (tokens.size() == 2) {
                return List.of(PRESTIGE);
            }
            if (tokens.size() == 3 && tokens.get(1).equalsIgnoreCase(PRESTIGE)) {
                return List.of("set");
            }
        }
        return List.of();
    }

    private static List<String> prestigeDetailsCandidates(List<String> tokens) {
        if (tokens.size() == 2) {
            return List.of(PRESTIGE);
        }
        if (tokens.size() == 3 || tokens.size() == 4) {
            return List.of(DETAILS);
        }
        return List.of();
    }

    private List<String> configCandidates(PermissionSubject subject, List<String> tokens) {
        if (tokens.size() == 2) {
            ArrayList<String> subcommands = new ArrayList<>();
            if (subject.has(PhaseSixPermissions.CONFIG_VIEW)) {
                subcommands.addAll(List.of("get", "list", "search", "explain", "history", "validate", "diff"));
            }
            if (subject.has(PhaseSixPermissions.CONFIG_EDIT)) {
                subcommands.addAll(List.of("draft", "set", "add", "remove", "segment-add", "segment-edit",
                        "segment-remove", CANCEL));
            }
            if (subject.has(PhaseSixPermissions.CONFIG_APPLY)) {
                subcommands.addAll(List.of(APPLY, ACKNOWLEDGE, CONFIRM));
            }
            if (subject.has(PhaseSixPermissions.CONFIG_ROLLBACK)) {
                subcommands.addAll(List.of("rollback", "rollback-apply", ACKNOWLEDGE, CONFIRM,
                        "validate", "diff", CANCEL));
            }
            return subcommands;
        }
        String operation = tokens.get(1).toLowerCase(Locale.ROOT);
        if (tokens.size() == 3 && Set.of("get", "explain").contains(operation)
                && subject.has(PhaseSixPermissions.CONFIG_VIEW)) {
            return catalog.get().schemaPaths();
        }
        if (operation.equals("list") && subject.has(PhaseSixPermissions.CONFIG_VIEW)) {
            return tokens.size() == 3 ? catalog.get().draftIds()
                    : tokens.size() == 4 ? catalog.get().structuralPaths() : List.of();
        }
        if (Set.of("set", "add", "remove").contains(operation)
                && subject.has(PhaseSixPermissions.CONFIG_EDIT)) {
            if (tokens.size() == 3) {
                return catalog.get().draftIds();
            }
            if (tokens.size() == 4) {
                if (operation.equals("set")) {
                    return catalog.get().schemaPaths().stream()
                            .filter(path -> !catalog.get().structuralPaths().contains(path)).toList();
                }
                return new ArrayList<>(catalog.get().listPaths());
            }
            if (tokens.size() == 5) {
                String path = tokens.get(3);
                return valuesForPath(path);
            }
        }
        if (Set.of("segment-add", "segment-edit", "segment-remove").contains(operation)
                && subject.has(PhaseSixPermissions.CONFIG_EDIT) && tokens.size() == 3) {
            return catalog.get().draftIds();
        }
        if (Set.of("validate", "diff", CANCEL, ACKNOWLEDGE).contains(operation)
                && tokens.size() == 3) {
            return catalog.get().draftIds();
        }
        if (Set.of(APPLY, "rollback-apply").contains(operation)) {
            if (tokens.size() == 3) {
                return catalog.get().draftIds();
            }
            if (tokens.size() == 4) {
                ArrayList<String> revisions = new ArrayList<>(catalog.get().revisionIds());
                revisions.add("none");
                return revisions;
            }
        }
        if (operation.equals("rollback") && tokens.size() == 3) {
            return catalog.get().revisionIds();
        }
        return List.of();
    }

    private List<String> setupCandidates(List<String> tokens) {
        if (tokens.size() == 2) {
            return List.of("discover", "start", "provider", "requirement", "cost",
                    "reward", PRESTIGE, "preview", ACKNOWLEDGE, CONFIRM, APPLY, CANCEL);
        }
        String operation = tokens.get(1).toLowerCase(Locale.ROOT);
        boolean explicit = tokens.size() > 2 && looksLikeUuid(tokens.get(2));
        int first = explicit ? 3 : 2;
        return switch (operation) {
            case "provider" -> setupProviderCandidates(tokens, first);
            case "requirement" -> setupRequirementCandidates(tokens, first);
            case "cost", "reward" -> setupCostOrRewardCandidates(tokens);
            case PRESTIGE -> setupPrestigeCandidates(tokens, first);
            default -> List.of();
        };
    }

    private List<String> setupProviderCandidates(List<String> tokens, int first) {
        if (tokens.size() != first + 1) {
            return List.of();
        }
        ArrayList<String> values = new ArrayList<>(catalog.get().providerIds());
        values.add("internal");
        return values;
    }

    private List<String> setupRequirementCandidates(List<String> tokens, int first) {
        int provider = first + 1;
        return switch (tokens.size() - provider) {
            case 1 -> catalog.get().providerIds();
            case 2 -> catalog.get().metricsByProvider().getOrDefault(tokens.get(provider), List.of());
            case 3 -> List.of("EQUAL", "GREATER_OR_EQUAL", "LESS_OR_EQUAL");
            case 4 -> playTimeRequirement(tokens, provider)
                    ? List.of("PT1M", "PT3M", "1m", "3m") : List.of();
            case 5 -> List.of("ABSOLUTE", "LIFETIME", "SINCE_PRESTIGE_START", "SINCE_SEASON_START");
            case 6 -> List.of("LIVE", "LATCHED");
            default -> List.of();
        };
    }

    private static boolean playTimeRequirement(List<String> tokens, int provider) {
        return tokens.get(provider).equalsIgnoreCase("paper_statistics")
                && tokens.get(provider + 1).equalsIgnoreCase("play_one_minute");
    }

    private List<String> setupCostOrRewardCandidates(List<String> tokens) {
        return tokens.size() == 5 ? catalog.get().providerIds() : List.of();
    }

    private static List<String> setupPrestigeCandidates(List<String> tokens, int first) {
        return tokens.size() == first + 1 ? List.of("disabled", "enabled") : List.of();
    }

    private static boolean looksLikeUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    private List<String> valuesForPath(String path) {
        CompletionCatalog current = catalog.get();
        if (path.endsWith(".provider")) {
            return current.providerIds();
        }
        if (path.endsWith(".metric")) {
            return current.metricsByProvider().values().stream().flatMap(List::stream).distinct().sorted().toList();
        }
        return current.valuesByPath().getOrDefault(path, List.of());
    }

    private static List<String> rootCommands(PermissionSubject subject) {
        ArrayList<String> commands = new ArrayList<>();
        boolean player = subject.actor().uuid().isPresent();
        if (subject.has(PhaseSixPermissions.USE)) {
            commands.addAll(List.of("help", "status", "why"));
        }
        if (player && subject.has(PhaseSixPermissions.PRESTIGE)) {
            commands.add(PRESTIGE);
            commands.add(CONFIRM);
        }
        if (subject.has(PhaseSixPermissions.CONFIG_VIEW) || subject.has(PhaseSixPermissions.CONFIG_EDIT)
                || subject.has(PhaseSixPermissions.CONFIG_APPLY) || subject.has(PhaseSixPermissions.CONFIG_ROLLBACK)) {
            commands.add("config");
        }
        if (subject.has(PhaseSixPermissions.DOCTOR)) {
            commands.add("doctor");
        }
        if (subject.has(PhaseSixPermissions.SIMULATE)) {
            commands.add("simulate");
        }
        if (subject.has(PhaseSixPermissions.SETUP)) {
            commands.add(SETUP);
        }
        if (player && (subject.has(PhaseSixPermissions.ADMIN_GUI) || subject.has(PhaseSixPermissions.USE))) {
            commands.add("gui");
        }
        if (subject.has(PhaseSixPermissions.PLAYER_VIEW)) {
            commands.add("player");
        }
        if (subject.has(PhaseSixPermissions.PLAYER_PRESTIGE_EDIT)) {
            commands.add("staff");
        }
        return commands;
    }
}
