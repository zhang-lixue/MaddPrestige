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
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.schema.SchemaRegistry;
import net.maddkraft.maddprestige.core.schema.SchemaValueType;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;

public final class CommandCompletionService {
    private static final int MAX_SUGGESTIONS = 50;
    private final AtomicReference<CompletionCatalog> catalog = new AtomicReference<>(CompletionCatalog.empty());

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
            return tokens.size() == 2 ? List.of("rankup", "prestige") : List.of();
        }
        if (root.equals("why") && (subject.has(PhaseSixPermissions.USE)
                || subject.has(PhaseSixPermissions.PLAYER_VIEW))) {
            return tokens.size() == 2 ? List.of("rankup", "prestige") : List.of();
        }
        if (root.equals("help")) {
            return List.of("measurement", "requirements", "scaling", "stages", "providers");
        }
        if (root.equals("setup") && subject.has(PhaseSixPermissions.SETUP)) {
            return setupCandidates(tokens);
        }
        if (root.equals("staff") && subject.has(PhaseSixPermissions.PLAYER_PRESTIGE_EDIT)) {
            if (tokens.size() == 2) {
                return List.of("prestige");
            }
            if (tokens.size() == 3 && tokens.get(1).equalsIgnoreCase("prestige")) {
                return List.of("set");
            }
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
                subcommands.addAll(List.of("draft", "set", "add", "remove", "cancel"));
            }
            if (subject.has(PhaseSixPermissions.CONFIG_APPLY)) {
                subcommands.addAll(List.of("apply", "acknowledge", "confirm", "remap", "unmap"));
            }
            if (subject.has(PhaseSixPermissions.CONFIG_ROLLBACK)) {
                subcommands.addAll(List.of("rollback", "rollback-apply", "acknowledge", "confirm", "remap", "unmap",
                        "validate", "diff", "cancel"));
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
                if (operation.equals("remove")) {
                    ArrayList<String> paths = new ArrayList<>(catalog.get().listPaths());
                    catalog.get().stageIds().forEach(stage -> paths.add("progression.stages." + stage));
                    return paths;
                }
                ArrayList<String> paths = new ArrayList<>(catalog.get().listPaths());
                paths.add("progression.stages");
                return paths;
            }
            if (tokens.size() == 5) {
                String path = tokens.get(3);
                if (operation.equals("remove") && path.startsWith("progression.stages.")) {
                    String removed = path.substring("progression.stages.".length());
                    return catalog.get().stageIds().stream().filter(stage -> !stage.equals(removed)).toList();
                }
                return valuesForPath(path);
            }
            if (operation.equals("add") && tokens.size() == 7
                    && tokens.get(3).equals("progression.stages")) {
                return catalog.get().providerIds();
            }
        }
        if (Set.of("validate", "diff", "cancel", "acknowledge").contains(operation)
                && tokens.size() == 3) {
            return catalog.get().draftIds();
        }
        if (operation.equals("remap")
                && (subject.has(PhaseSixPermissions.CONFIG_APPLY)
                        || subject.has(PhaseSixPermissions.CONFIG_ROLLBACK))) {
            if (tokens.size() == 3) {
                return catalog.get().draftIds();
            }
            if (tokens.size() == 4) {
                return catalog.get().stageIds();
            }
            if (tokens.size() == 5) {
                return catalog.get().stageIds().stream().filter(stage -> !stage.equals(tokens.get(3))).toList();
            }
        }
        if (operation.equals("unmap")
                && (subject.has(PhaseSixPermissions.CONFIG_APPLY)
                        || subject.has(PhaseSixPermissions.CONFIG_ROLLBACK))) {
            if (tokens.size() == 3) {
                return catalog.get().draftIds();
            }
            if (tokens.size() == 4) {
                return catalog.get().stageIds();
            }
        }
        if (Set.of("apply", "rollback-apply").contains(operation)) {
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
            return List.of("discover", "start", "provider", "stage", "baseline", "requirement", "cost", "reward",
                    "prestige", "preview", "acknowledge", "confirm", "apply", "cancel");
        }
        String operation = tokens.get(1).toLowerCase(Locale.ROOT);
        if (operation.equals("provider") && tokens.size() == 4) {
            ArrayList<String> values = new ArrayList<>(catalog.get().providerIds());
            values.add("internal");
            return values;
        }
        if (operation.equals("baseline") && tokens.size() == 4) {
            return catalog.get().stageIds();
        }
        if (operation.equals("requirement")) {
            if (tokens.size() == 5) {
                return catalog.get().providerIds();
            }
            if (tokens.size() == 6) {
                return catalog.get().metricsByProvider().getOrDefault(tokens.get(4), List.of());
            }
            if (tokens.size() == 7) {
                return List.of("equal", "greater-or-equal", "less-or-equal");
            }
            if (tokens.size() == 9) {
                return List.of("absolute", "lifetime", "since-stage-start", "since-prestige-start",
                        "since-season-start");
            }
            if (tokens.size() == 10) {
                return List.of("live", "latched");
            }
        }
        if (Set.of("cost", "reward").contains(operation) && tokens.size() == 5) {
            return catalog.get().providerIds();
        }
        if (operation.equals("prestige")) {
            if (tokens.size() == 4) {
                return List.of("disabled", "enabled");
            }
            if (tokens.size() == 5 || tokens.size() == 6) {
                return catalog.get().stageIds();
            }
        }
        return List.of();
    }

    private List<String> valuesForPath(String path) {
        CompletionCatalog current = catalog.get();
        if (path.endsWith(".provider")) {
            return current.providerIds();
        }
        if (path.endsWith(".metric")) {
            return current.metricsByProvider().values().stream().flatMap(List::stream).distinct().sorted().toList();
        }
        if (path.equals("progression.baseline") || path.equals("prestige.reset-stage")
                || path.equals("prestige.required-stages") || path.equals("progression.order")) {
            return current.stageIds();
        }
        return current.valuesByPath().getOrDefault(path, List.of());
    }

    private static List<String> rootCommands(PermissionSubject subject) {
        ArrayList<String> commands = new ArrayList<>();
        boolean player = subject.actor().uuid().isPresent();
        if (subject.has(PhaseSixPermissions.USE)) {
            commands.addAll(List.of("help", "status", "why"));
        }
        if (player && subject.has(PhaseSixPermissions.RANK_UP)) {
            commands.add("rankup");
        }
        if (player && subject.has(PhaseSixPermissions.PRESTIGE)) {
            commands.add("prestige");
        }
        if (player && (subject.has(PhaseSixPermissions.RANK_UP) || subject.has(PhaseSixPermissions.PRESTIGE))) {
            commands.add("confirm");
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
            commands.add("setup");
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
