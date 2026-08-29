package net.maddkraft.maddprestige.platform.paper.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.rank.ManagedRankState;
import net.maddkraft.maddprestige.api.rank.RankAdapter;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.api.rank.RankProjectionResult;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.api.service.OperationStatus;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.command.CommandInvocation;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.persistence.VerifiedBackup;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteFoundation;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteMigrations;
import net.maddkraft.maddprestige.platform.paper.ExecutionThread;
import net.maddkraft.maddprestige.platform.paper.PaperTaskScheduler;
import net.maddkraft.maddprestige.platform.paper.placeholder.MaddPrestigePlaceholderCache;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductionRankUpCompatibilityBoundaryTest {
    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID PLAYER = UUID.fromString("95634fb9-a684-45d1-9153-a70f76e3f07b");

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("[Phase 9B] Production /rankup is blocked before confirmation, persistence, or projection")
    void productionRankUpCannotConfirmMutateOrProject() throws Exception {
        SqliteFoundation foundation = new SqliteFoundation(temporaryDirectory.resolve("rankup.sqlite"));
        new MigrationRunner(foundation, ignored -> new VerifiedBackup("fixture",
                Optional.of(foundation.databaseFile()), Optional.of(RevisionHasher.hashText("fixture")),
                NOW, true, "controlled production-boundary fixture"), CLOCK).migrate(SqliteMigrations.phaseNineB());
        CountingRankAdapter rankAdapter = new CountingRankAdapter();
        ProviderRegistry providers = new ProviderRegistry();
        providers.activate(providers.register("rank-compatibility-test", rankAdapter));
        Plugin plugin = mock(Plugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("phase9b-rankup-boundary"));
        PaperTaskScheduler scheduler = new PaperTaskScheduler() {
            @Override
            public <T> CompletionStage<T> submit(ExecutionThread thread, Supplier<T> task) {
                return CompletableFuture.completedFuture(task.get());
            }
        };

        try (ProductionRuntime runtime = new ProductionRuntime(plugin, providers, foundation, Optional.empty(),
                temporaryDirectory.resolve("data"), CLOCK, scheduler, new MaddPrestigePlaceholderCache(16),
                ignored -> { })) {
            PermissionSubject player = new PermissionSubject(
                    new Actor("player", Optional.of(PLAYER), "Player"), Set.of("maddprestige.*"));

            var response = runtime.commands().execute(new CommandInvocation(player, List.of("rankup")))
                    .toCompletableFuture().join();
            var apiResult = runtime.service().rankUp(PLAYER).toCompletableFuture().join().value().orElseThrow();

            assertEquals("rankup.compatibility_only", response.code());
            assertEquals(OperationStatus.BLOCKED, apiResult.status());
            assertEquals(0, scalar(foundation, "SELECT COUNT(*) FROM mp_operations"));
            assertEquals(0, scalar(foundation, "SELECT COUNT(*) FROM mp_player_stage_state"));
            assertEquals(0, scalar(foundation, "SELECT COUNT(*) FROM mp_player_prestige_state"));
            assertEquals(0, scalar(foundation, "SELECT COUNT(*) FROM mp_stage_history"));
            assertEquals(0, rankAdapter.calls.get());
        }
    }

    private static int scalar(SqliteFoundation foundation, String sql) throws Exception {
        try (var connection = foundation.open(); var statement = connection.createStatement();
                var row = statement.executeQuery(sql)) {
            return row.next() ? row.getInt(1) : -1;
        }
    }

    private static final class CountingRankAdapter implements RankAdapter {
        private static final ProviderId ID = new ProviderId("rank_compatibility_probe");
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public CompletionStage<Result<Set<String>>> validateTargets(Set<String> groupNames) {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(Result.success(Set.copyOf(groupNames)));
        }

        @Override
        public CompletionStage<Result<ManagedRankState>> readManagedState(UUID playerId, Set<String> managedGroups) {
            calls.incrementAndGet();
            throw new AssertionError("Unexpected rank read");
        }

        @Override
        public CompletionStage<Result<RankProjectionResult>> project(RankProjectionRequest request) {
            calls.incrementAndGet();
            throw new AssertionError("Unexpected rank projection");
        }

        @Override
        public ProviderDescriptor descriptor() {
            return new ProviderDescriptor(ID, "rank-compatibility-test", "compatibility-test", "1",
                    List.of(), List.of());
        }

        @Override
        public ProviderHealth health() {
            return new ProviderHealth(ProviderHealthState.AVAILABLE, "test.available", "available", NOW);
        }
    }
}
