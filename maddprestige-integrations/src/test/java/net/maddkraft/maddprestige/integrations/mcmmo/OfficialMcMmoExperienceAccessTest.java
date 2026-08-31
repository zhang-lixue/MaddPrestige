package net.maddkraft.maddprestige.integrations.mcmmo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.gmail.nossr50.api.ExperienceAPI;
import com.gmail.nossr50.api.exceptions.InvalidPlayerException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.metric.MetricSampleStatus;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.integrations.IntegrationTaskScheduler;
import net.maddkraft.maddprestige.integrations.MutableProviderHealth;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class OfficialMcMmoExperienceAccessTest {
    private static final UUID PLAYER_ID = UUID.fromString("d7551bf9-6358-3218-89c4-06c9c57dc879");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-30T10:03:26Z"), ZoneOffset.UTC);

    @Test
    void onlinePlayerTotalLevelResolvesFromLiveProfile() {
        Player player = mock(Player.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedStatic<ExperienceAPI> experience = mockStatic(ExperienceAPI.class)) {
            bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(player);
            experience.when(() -> ExperienceAPI.getPowerLevel(player)).thenReturn(23);
            experience.when(() -> ExperienceAPI.getPowerLevelOffline(PLAYER_ID)).thenReturn(99);

            assertEquals(23, new OfficialMcMmoExperienceAccess().powerLevel(PLAYER_ID));
            experience.verify(() -> ExperienceAPI.getPowerLevel(player));
            experience.verify(() -> ExperienceAPI.getPowerLevelOffline(PLAYER_ID), org.mockito.Mockito.never());
        }
    }

    @Test
    void aggregateLevelOneReturnsOne() {
        Player player = mock(Player.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedStatic<ExperienceAPI> experience = mockStatic(ExperienceAPI.class)) {
            bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(player);
            experience.when(() -> ExperienceAPI.getPowerLevel(player)).thenReturn(1);

            MetricSample sample = readTotal(new OfficialMcMmoExperienceAccess(), availableHealth());

            assertEquals(MetricSampleStatus.AVAILABLE, sample.status());
            assertEquals("1", sample.value().orElseThrow().canonical());
        }
    }

    @Test
    void zeroTotalLevelIsAValidAvailableValue() {
        Player player = mock(Player.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedStatic<ExperienceAPI> experience = mockStatic(ExperienceAPI.class)) {
            bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(player);
            experience.when(() -> ExperienceAPI.getPowerLevel(player)).thenReturn(0);

            MetricSample sample = readTotal(new OfficialMcMmoExperienceAccess(), availableHealth());

            assertEquals(MetricSampleStatus.AVAILABLE, sample.status());
            assertEquals("0", sample.value().orElseThrow().canonical());
        }
    }

    @Test
    void missingOfflineProfileFailsClearly() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedStatic<ExperienceAPI> experience = mockStatic(ExperienceAPI.class)) {
            bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(null);
            experience.when(() -> ExperienceAPI.getPowerLevelOffline(PLAYER_ID))
                    .thenThrow(new InvalidPlayerException());

            MetricSample sample = readTotal(new OfficialMcMmoExperienceAccess(), availableHealth());

            assertEquals(MetricSampleStatus.UNAVAILABLE, sample.status());
            assertTrue(sample.detail().orElseThrow().contains("InvalidPlayerException"));
        }
    }

    @Test
    void unavailableMcMmoFailsClosedWithoutReadingState() {
        MutableProviderHealth health = availableHealth();
        health.transition(ProviderHealthState.UNAVAILABLE, "test.unavailable", "mcMMO unavailable");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedStatic<ExperienceAPI> experience = mockStatic(ExperienceAPI.class)) {
            MetricSample sample = readTotal(new OfficialMcMmoExperienceAccess(), health);

            assertEquals(MetricSampleStatus.UNAVAILABLE, sample.status());
            bukkit.verifyNoInteractions();
            experience.verifyNoInteractions();
        }
    }

    @Test
    void individualSkillLevelIsNotSubstitutedForTotalLevel() {
        McMmoExperienceAccess access = new McMmoExperienceAccess() {
            @Override
            public boolean validSkill(String skill) {
                return true;
            }

            @Override
            public int level(UUID playerId, String skill) {
                return 99;
            }

            @Override
            public int powerLevel(UUID playerId) {
                return 1;
            }
        };

        assertEquals("1", readTotal(access, availableHealth()).value().orElseThrow().canonical());
    }

    @Test
    void totalLevelReadDoesNotMutateOrResetMcMmoState() {
        Player player = mock(Player.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedStatic<ExperienceAPI> experience = mockStatic(ExperienceAPI.class)) {
            bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(player);
            experience.when(() -> ExperienceAPI.getPowerLevel(player)).thenReturn(7);

            assertEquals(7, new OfficialMcMmoExperienceAccess().powerLevel(PLAYER_ID));

            experience.verify(() -> ExperienceAPI.getPowerLevel(player));
            experience.verifyNoMoreInteractions();
        }
    }

    private static MetricSample readTotal(McMmoExperienceAccess access, MutableProviderHealth health) {
        IntegrationTaskScheduler immediate = new IntegrationTaskScheduler() {
            @Override
            public <T> java.util.concurrent.CompletionStage<T> call(java.util.function.Supplier<T> action) {
                return java.util.concurrent.CompletableFuture.completedFuture(action.get());
            }
        };
        McMmoMetricProvider provider = new McMmoMetricProvider(access, immediate, health, CLOCK, "2.2.053");
        MetricQuery query = new MetricQuery(McMmoMetricProvider.TOTAL_LEVEL, MetricReadMode.CURRENT, Map.of());
        return provider.read(PLAYER_ID, List.of(query), 11).toCompletableFuture().join().get(query);
    }

    private static MutableProviderHealth availableHealth() {
        return new MutableProviderHealth(CLOCK, ProviderHealthState.AVAILABLE,
                "mcmmo.available", "mcMMO available");
    }
}
