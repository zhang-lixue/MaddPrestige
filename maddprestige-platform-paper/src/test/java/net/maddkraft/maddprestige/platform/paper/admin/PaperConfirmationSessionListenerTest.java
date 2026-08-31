package net.maddkraft.maddprestige.platform.paper.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class PaperConfirmationSessionListenerTest {
    @Test
    @DisplayName("[Phase 9D UX] Paper join, quit, and kick route exact confirmation-session lifecycle")
    void routesPlayerSessionLifecycle() {
        UUID playerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        PlayerJoinEvent join = mock(PlayerJoinEvent.class);
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        PlayerKickEvent kick = mock(PlayerKickEvent.class);
        when(join.getPlayer()).thenReturn(player);
        when(quit.getPlayer()).thenReturn(player);
        when(kick.getPlayer()).thenReturn(player);
        ArrayList<UUID> started = new ArrayList<>();
        ArrayList<UUID> ended = new ArrayList<>();
        PaperConfirmationSessionListener listener = new PaperConfirmationSessionListener(
                started::add, ended::add);

        listener.onJoin(join);
        listener.onQuit(quit);
        listener.onKick(kick);

        assertEquals(List.of(playerId), started);
        assertEquals(List.of(playerId, playerId), ended);
    }
}
