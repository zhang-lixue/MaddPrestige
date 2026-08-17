package net.maddkraft.maddprestige.core.admin.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CompletionCatalog(
        List<String> stageIds,
        List<String> providerIds,
        Map<String, List<String>> metricsByProvider,
        List<String> schemaPaths,
        Map<String, List<String>> valuesByPath,
        List<String> structuralPaths,
        List<String> listPaths,
        List<String> draftIds,
        List<String> revisionIds) {
    public CompletionCatalog {
        stageIds = List.copyOf(Objects.requireNonNull(stageIds, "stage IDs"));
        providerIds = List.copyOf(Objects.requireNonNull(providerIds, "provider IDs"));
        metricsByProvider = Map.copyOf(Objects.requireNonNull(metricsByProvider, "metrics"));
        schemaPaths = List.copyOf(Objects.requireNonNull(schemaPaths, "schema paths"));
        valuesByPath = Map.copyOf(Objects.requireNonNull(valuesByPath, "path values"));
        structuralPaths = List.copyOf(Objects.requireNonNull(structuralPaths, "structural paths"));
        listPaths = List.copyOf(Objects.requireNonNull(listPaths, "list paths"));
        draftIds = List.copyOf(Objects.requireNonNull(draftIds, "draft IDs"));
        revisionIds = List.copyOf(Objects.requireNonNull(revisionIds, "revision IDs"));
    }

    public static CompletionCatalog empty() {
        return new CompletionCatalog(List.of(), List.of(), Map.of(), List.of(), Map.of(), List.of(), List.of(),
                List.of(), List.of());
    }

    public CompletionCatalog withAuthorities(Set<UUID> drafts, Set<String> revisions) {
        return new CompletionCatalog(stageIds, providerIds, metricsByProvider, schemaPaths, valuesByPath,
                structuralPaths, listPaths, drafts.stream().map(UUID::toString).sorted().toList(),
                revisions.stream().sorted().toList());
    }
}
