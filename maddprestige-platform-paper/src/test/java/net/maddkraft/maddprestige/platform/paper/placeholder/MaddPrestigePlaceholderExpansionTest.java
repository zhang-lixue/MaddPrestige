package net.maddkraft.maddprestige.platform.paper.placeholder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MaddPrestigePlaceholderExpansionTest {
    @Test
    @DisplayName("[A52] Repeated PlaceholderAPI renders read one immutable snapshot without recomputation")
    void repeatedRendersAreCacheOnly() {
        UUID playerId = UUID.randomUUID();
        MaddPrestigePlaceholderCache cache = new MaddPrestigePlaceholderCache(10);
        cache.publish(playerId, new MaddPrestigePlaceholderSnapshot("7", "12", "READY",
                Map.of("season", "summer")));
        MaddPrestigePlaceholderExpansion expansion = new MaddPrestigePlaceholderExpansion(cache, "2.0.0");
        OfflinePlayer player = player(playerId);
        for (int index = 0; index < 10_000; index++) {
            assertNull(expansion.onRequest(player, "stage"));
            assertEquals("7", expansion.onRequest(player, "current_prestige"));
        }
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("[A52] Null/offline/missing placeholder behavior is safe and predictable")
    void safeMissingBehavior() {
        MaddPrestigePlaceholderCache cache = new MaddPrestigePlaceholderCache(1);
        MaddPrestigePlaceholderExpansion expansion = new MaddPrestigePlaceholderExpansion(cache, "2.0.0");
        assertEquals("", expansion.onRequest(null, "stage"));
        assertNull(expansion.onRequest(player(UUID.randomUUID()), "stage"));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        cache.publish(first, snapshot("first"));
        cache.publish(second, snapshot("second"));
        assertEquals(1, cache.size());
        assertNull(expansion.onRequest(player(first), "current_prestige"));
        assertEquals("0", expansion.onRequest(player(second), "current_prestige"));
        assertNull(expansion.onRequest(player(second), "unknown"));
    }

    @Test
    @DisplayName("[A52] Expansion uses the official persistent PlaceholderAPI contract")
    void officialExpansionContract() {
        PlaceholderExpansion expansion = new MaddPrestigePlaceholderExpansion(
                new MaddPrestigePlaceholderCache(1), "2.0.0");
        assertEquals("maddprestige", expansion.getIdentifier());
        assertEquals(true, expansion.persist());
        assertEquals(true, expansion.canRegister());
    }

    private static MaddPrestigePlaceholderSnapshot snapshot(String ignored) {
        return new MaddPrestigePlaceholderSnapshot("0", "0", "UNAVAILABLE", Map.of());
    }

    private static OfflinePlayer player(UUID id) {
        return (OfflinePlayer) Proxy.newProxyInstance(OfflinePlayer.class.getClassLoader(),
                new Class<?>[]{OfflinePlayer.class}, (proxy, method, arguments) -> {
                    if ("getUniqueId".equals(method.getName())) {
                        return id;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == long.class) {
                        return 0L;
                    }
                    return null;
                });
    }
}
