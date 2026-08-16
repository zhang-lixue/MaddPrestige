package net.maddkraft.maddprestige.core.command;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record CommandActionPolicy(
        boolean enabled,
        Set<String> allowedRoots,
        Set<String> blockedRoots,
        Set<String> allowedTokens,
        Map<String, CommandTemplate> templates,
        int maximumCommands,
        int maximumCommandLength,
        int maximumTriggerDepth) {
    public static final Set<String> MANDATORY_BLOCKED_ROOTS = Set.of(
            "stop", "restart", "op", "deop", "reload", "execute", "function",
            "maddprestige", "mprestige", "mp");

    public CommandActionPolicy {
        allowedRoots = normalizeRoots(allowedRoots);
        LinkedHashSet<String> blocked = new LinkedHashSet<>(normalizeRoots(blockedRoots));
        blocked.addAll(MANDATORY_BLOCKED_ROOTS);
        blockedRoots = Set.copyOf(blocked);
        allowedTokens = Set.copyOf(Objects.requireNonNull(allowedTokens, "allowed tokens"));
        templates = Map.copyOf(Objects.requireNonNull(templates, "templates"));
        if (maximumCommands < 1 || maximumCommands > 100
                || maximumCommandLength < 1 || maximumCommandLength > 4096
                || maximumTriggerDepth < 0 || maximumTriggerDepth > 16) {
            throw new IllegalArgumentException("Command action limits are outside the safe domain");
        }
        templates.forEach((id, template) -> {
            if (!id.equals(template.id())) {
                throw new IllegalArgumentException("Command template map key must match immutable template ID");
            }
        });
    }

    public static CommandActionPolicy safeDefaults() {
        return new CommandActionPolicy(false, Set.of(), MANDATORY_BLOCKED_ROOTS, Set.of(), Map.of(), 5, 256, 0);
    }

    private static Set<String> normalizeRoots(Set<String> roots) {
        Objects.requireNonNull(roots, "roots");
        return roots.stream().map(root -> {
            String normalized = Objects.requireNonNull(root, "command root").trim()
                    .toLowerCase(java.util.Locale.ROOT);
            if (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            int namespace = normalized.indexOf(':');
            if (namespace >= 0) {
                normalized = normalized.substring(namespace + 1);
            }
            if (!normalized.matches("[a-z0-9_-]{1,64}")) {
                throw new IllegalArgumentException("Configured command root is not a safe normalized identifier");
            }
            return normalized;
        }).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
