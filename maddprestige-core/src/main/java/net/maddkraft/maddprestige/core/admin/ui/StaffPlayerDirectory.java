package net.maddkraft.maddprestige.core.admin.ui;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Authoritative platform/persistence identity projection; it owns no parallel player state. */
@FunctionalInterface
public interface StaffPlayerDirectory {
    Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    List<StaffPlayerIdentity> onlinePlayers();

    default List<StaffPlayerIdentity> knownPlayers() {
        return onlinePlayers();
    }

    default Optional<StaffPlayerIdentity> onlinePlayer(UUID playerId) {
        return onlinePlayers().stream().filter(player -> player.playerId().equals(playerId)).findFirst();
    }

    default Optional<StaffPlayerIdentity> player(UUID playerId) {
        Objects.requireNonNull(playerId, "player ID");
        return knownPlayers().stream().filter(player -> player.playerId().equals(playerId)).findFirst();
    }

    /** Exact-only command resolution; ambiguous names never select a player silently. */
    default Optional<StaffPlayerIdentity> findPlayer(String selector) {
        Objects.requireNonNull(selector, "player selector");
        String normalized = selector.trim();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        try {
            Optional<StaffPlayerIdentity> byId = player(UUID.fromString(normalized));
            if (byId.isPresent()) {
                return byId;
            }
        } catch (IllegalArgumentException ignored) {
            // Player names are the normal staff-facing selector.
        }
        if (!PLAYER_NAME.matcher(normalized).matches()) {
            return Optional.empty();
        }
        List<StaffPlayerIdentity> exact = knownPlayers().stream()
                .filter(player -> player.name().equalsIgnoreCase(normalized))
                .toList();
        return exact.size() == 1 ? Optional.of(exact.getFirst()) : Optional.empty();
    }

    /** Exact matches win; otherwise partial names remain explicit selectable candidates. */
    default List<StaffPlayerIdentity> searchPlayers(String selector) {
        Objects.requireNonNull(selector, "player selector");
        String normalized = selector.trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        try {
            return player(UUID.fromString(normalized)).map(List::of).orElseGet(List::of);
        } catch (IllegalArgumentException ignored) {
            // Partial player names are accepted only through the bounded GUI search route.
        }
        if (!PLAYER_NAME.matcher(normalized).matches()) {
            return List.of();
        }
        Comparator<StaffPlayerIdentity> order = Comparator
                .comparing(StaffPlayerIdentity::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(StaffPlayerIdentity::playerId);
        List<StaffPlayerIdentity> exact = knownPlayers().stream()
                .filter(player -> player.name().equalsIgnoreCase(normalized))
                .sorted(order).toList();
        if (!exact.isEmpty()) {
            return exact;
        }
        String needle = normalized.toLowerCase(java.util.Locale.ROOT);
        return knownPlayers().stream()
                .filter(player -> player.name().toLowerCase(java.util.Locale.ROOT).contains(needle))
                .sorted(order).toList();
    }
}
