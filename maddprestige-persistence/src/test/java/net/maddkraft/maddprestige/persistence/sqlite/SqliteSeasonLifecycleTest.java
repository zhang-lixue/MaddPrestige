package net.maddkraft.maddprestige.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.SeasonId;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.config.phase4.ResetDisposition;
import net.maddkraft.maddprestige.core.requirement.BaselineInitializationService;
import net.maddkraft.maddprestige.core.requirement.BaselineKey;
import net.maddkraft.maddprestige.core.requirement.CatchUpProfile;
import net.maddkraft.maddprestige.core.requirement.CompletionMode;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;
import net.maddkraft.maddprestige.core.requirement.RequirementDefinition;
import net.maddkraft.maddprestige.core.requirement.RequirementTarget;
import net.maddkraft.maddprestige.core.requirement.ScalingProfile;
import net.maddkraft.maddprestige.core.season.SeasonDefinition;
import net.maddkraft.maddprestige.core.season.SeasonLifecycleService;
import net.maddkraft.maddprestige.core.season.SeasonLifecycleState;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteSeasonLifecycleTest {
    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ConfigRevisionId REVISION = new ConfigRevisionId("phase4-season");
    private static final ProviderId PROVIDER = new ProviderId("progress_source");
    private static final RequirementId REQUIREMENT = new RequirementId("season_activity");
    @TempDir
    Path temporaryDirectory;
    private SqliteFoundation sqlite;
    private SqliteRequirementStateRepository requirementStates;

    @BeforeEach
    void migrate() {
        Path database = temporaryDirectory.resolve("season.db");
        sqlite = new SqliteFoundation(database);
        new MigrationRunner(sqlite, new FileBackupService(database, temporaryDirectory.resolve("backups"), CLOCK),
                CLOCK).migrate(SqliteMigrations.phaseFour());
        new SqliteConfigRevisionRepository(sqlite).insert(REVISION, RevisionHasher.hashText("phase four season"));
        requirementStates = new SqliteRequirementStateRepository(sqlite);
    }

    @Test
    @DisplayName("[A33][A17] Archive/restart preserves history and creates a fresh season baseline/scope")
    void archiveRestartAndFreshBaseline() {
        SqliteSeasonStore store = new SqliteSeasonStore(sqlite);
        SeasonLifecycleService service = new SeasonLifecycleService(store,
                new BaselineInitializationService(requirementStates, CLOCK), CLOCK);
        UUID player = UUID.randomUUID();
        RequirementDefinition requirement = requirement();
        var first = service.start(season("chapter_one", ResetDisposition.RESET), REVISION);
        service.enterPlayer(player, first, List.of(requirement), samples(100), Map.of(PROVIDER, 1L));
        store.setPlayerProgress(player, first.id(), ExactDecimal.parse("42"), NOW.plusSeconds(1));
        assertThrows(IllegalStateException.class,
                () -> service.start(season("overlap", ResetDisposition.RESET), REVISION));
        var archived = service.endAndArchive(first.id());
        assertEquals(SeasonLifecycleState.ARCHIVED, archived.state());
        assertThrows(IllegalStateException.class,
                () -> store.setPlayerProgress(player, first.id(), ExactDecimal.parse("99"), NOW.plusSeconds(2)));
        assertEquals(ExactDecimal.parse("42"), store.playerProgress(player, first.id()));

        SqliteSeasonStore restarted = new SqliteSeasonStore(sqlite);
        assertFalse(restarted.active().seasonId().isPresent());
        assertEquals(ExactDecimal.parse("42"), restarted.playerProgress(player, first.id()));
        assertEquals(SeasonLifecycleState.ARCHIVED, restarted.find(first.id()).orElseThrow().state());
        assertThrows(IllegalStateException.class,
                () -> restarted.setPlayerProgress(player, first.id(), ExactDecimal.parse("99"), NOW.plusSeconds(3)));
        assertEquals(ExactDecimal.parse("42"), restarted.playerProgress(player, first.id()));
        assertThrows(IllegalStateException.class, () -> service.endAndArchive(first.id()));

        SeasonLifecycleService restartedService = new SeasonLifecycleService(restarted,
                new BaselineInitializationService(requirementStates, CLOCK), CLOCK);
        var second = restartedService.start(season("chapter_two", ResetDisposition.RESET), REVISION);
        restartedService.enterPlayer(player, second, List.of(requirement), samples(150), Map.of(PROVIDER, 1L));

        assertNotEquals(first.scopeId(), second.scopeId());
        assertEquals(ExactDecimal.ZERO, restarted.playerProgress(player, second.id()));
        BaselineKey firstKey = new BaselineKey(player, REQUIREMENT, MeasurementScope.SINCE_SEASON_START,
                first.scopeId(), requirement.semanticFingerprint());
        BaselineKey secondKey = new BaselineKey(player, REQUIREMENT, MeasurementScope.SINCE_SEASON_START,
                second.scopeId(), requirement.semanticFingerprint());
        assertEquals(MetricValue.count(100), requirementStates.findBaseline(firstKey).orElseThrow().value());
        assertEquals(MetricValue.count(150), requirementStates.findBaseline(secondKey).orElseThrow().value());
        assertEquals(MetricValue.count(25), MetricValue.count(175).subtract(
                requirementStates.findBaseline(secondKey).orElseThrow().value()));
        assertEquals(2, restarted.history(10).size());
        assertTrue(restarted.history(10).stream().anyMatch(value -> value.id().equals(first.id())
                && value.state() == SeasonLifecycleState.ARCHIVED));
    }

    @Test
    @DisplayName("[A33] PRESERVE carries the latest immutable archived season progress")
    void preservePolicyCarriesArchivedProgress() {
        SqliteSeasonStore store = new SqliteSeasonStore(sqlite);
        SeasonLifecycleService service = new SeasonLifecycleService(store,
                new BaselineInitializationService(requirementStates, CLOCK), CLOCK);
        UUID player = UUID.randomUUID();
        var first = service.start(season("preserve_source", ResetDisposition.RESET), REVISION);
        service.enterPlayer(player, first, List.of(), Map.of(), Map.of());
        store.setPlayerProgress(player, first.id(), ExactDecimal.parse("42"), NOW.plusSeconds(1));
        service.endAndArchive(first.id());

        var second = service.start(season("preserve_target", ResetDisposition.PRESERVE), REVISION);
        service.enterPlayer(player, second, List.of(), Map.of(), Map.of());

        assertEquals(ExactDecimal.parse("42"), store.playerProgress(player, second.id()));
    }

    @Test
    @DisplayName("[A33] Archive wins a stale player-entry race without leaving entry or baselines")
    void archiveRaceCannotInsertPlayerFromStaleActiveRead() throws Exception {
        SqliteSeasonStore normalStore = new SqliteSeasonStore(sqlite);
        SeasonLifecycleService normalService = new SeasonLifecycleService(normalStore,
                new BaselineInitializationService(requirementStates, CLOCK), CLOCK);
        var season = normalService.start(season("archive_race", ResetDisposition.RESET), REVISION);
        UUID player = UUID.randomUUID();
        RequirementDefinition requirement = requirement();
        AtomicBoolean archived = new AtomicBoolean();
        ConnectionProvider racing = () -> archiveBeforePlayerInsert(sqlite.open(), () -> {
            if (archived.compareAndSet(false, true)) {
                normalService.endAndArchive(season.id());
            }
        });
        SeasonLifecycleService racingService = new SeasonLifecycleService(new SqliteSeasonStore(racing),
                new BaselineInitializationService(requirementStates, CLOCK), CLOCK);

        assertThrows(net.maddkraft.maddprestige.persistence.PersistenceException.class,
                () -> racingService.enterPlayer(player, season, List.of(requirement), samples(100),
                        Map.of(PROVIDER, 1L)));
        assertTrue(archived.get());
        assertEquals(SeasonLifecycleState.ARCHIVED, normalStore.find(season.id()).orElseThrow().state());
        assertEquals(0, playerEntryCount(player, season.id()));
        BaselineKey key = new BaselineKey(player, REQUIREMENT, MeasurementScope.SINCE_SEASON_START,
                season.scopeId(), requirement.semanticFingerprint());
        assertTrue(requirementStates.findBaseline(key).isEmpty());
    }

    @Test
    @DisplayName("[A17][A33] Season entry and required baseline roll back together and retry idempotently")
    void seasonEntryBaselineBoundaryIsAtomicAndRetryable() throws Exception {
        SqliteSeasonStore normalStore = new SqliteSeasonStore(sqlite);
        SeasonRecordHolder holder = new SeasonRecordHolder(new SeasonLifecycleService(normalStore,
                new BaselineInitializationService(requirementStates, CLOCK), CLOCK)
                .start(season("atomic_chapter", ResetDisposition.RESET), REVISION));
        UUID player = UUID.randomUUID();
        RequirementDefinition requirement = requirement();
        ConnectionProvider failing = () -> failBaselinePrepare(sqlite.open());
        SeasonLifecycleService faulting = new SeasonLifecycleService(new SqliteSeasonStore(failing),
                new BaselineInitializationService(requirementStates, CLOCK), CLOCK);

        assertThrows(net.maddkraft.maddprestige.persistence.PersistenceException.class,
                () -> faulting.enterPlayer(player, holder.season(), List.of(requirement), samples(100),
                        Map.of(PROVIDER, 1L)));
        assertEquals(0, playerEntryCount(player, holder.season().id()));
        BaselineKey key = new BaselineKey(player, REQUIREMENT, MeasurementScope.SINCE_SEASON_START,
                holder.season().scopeId(), requirement.semanticFingerprint());
        assertTrue(requirementStates.findBaseline(key).isEmpty());

        SeasonLifecycleService restarted = new SeasonLifecycleService(new SqliteSeasonStore(sqlite),
                new BaselineInitializationService(requirementStates, CLOCK), CLOCK);
        restarted.enterPlayer(player, holder.season(), List.of(requirement), samples(100), Map.of(PROVIDER, 1L));
        restarted.enterPlayer(player, holder.season(), List.of(requirement), samples(100), Map.of(PROVIDER, 1L));
        assertEquals(1, playerEntryCount(player, holder.season().id()));
        assertEquals(MetricValue.count(100), requirementStates.findBaseline(key).orElseThrow().value());
        assertEquals(1, baselineCount(key));
    }

    private Connection failBaselinePrepare(Connection delegate) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> {
                    if ("prepareStatement".equals(method.getName()) && arguments != null
                            && arguments.length > 0 && arguments[0] instanceof String sql
                            && sql.contains("INSERT OR IGNORE INTO mp_requirement_baselines")) {
                        throw new SQLException("fault injected before season baseline persistence");
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    private Connection archiveBeforePlayerInsert(Connection delegate, Runnable archive) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> {
                    if ("prepareStatement".equals(method.getName()) && arguments != null
                            && arguments.length > 0 && arguments[0] instanceof String sql
                            && sql.contains("INSERT INTO mp_player_season_state")) {
                        archive.run();
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    private int playerEntryCount(UUID player, SeasonId season) throws SQLException {
        try (Connection connection = sqlite.open(); var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM mp_player_season_state WHERE player_uuid = ? AND season_id = ?")) {
            statement.setString(1, player.toString());
            statement.setString(2, season.value());
            try (var row = statement.executeQuery()) {
                return row.next() ? row.getInt(1) : 0;
            }
        }
    }

    private int baselineCount(BaselineKey key) throws SQLException {
        try (Connection connection = sqlite.open(); var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM mp_requirement_baselines WHERE player_uuid = ? AND requirement_id = ? "
                        + "AND measurement_scope = ? AND scope_instance = ? AND semantic_fingerprint = ?")) {
            statement.setString(1, key.playerId().toString());
            statement.setString(2, key.requirementId().value());
            statement.setString(3, key.scope().name());
            statement.setString(4, key.scopeInstance().value());
            statement.setString(5, key.semanticFingerprint());
            try (var row = statement.executeQuery()) {
                return row.next() ? row.getInt(1) : 0;
            }
        }
    }

    private record SeasonRecordHolder(net.maddkraft.maddprestige.core.season.SeasonRecord season) {
    }

    private static SeasonDefinition season(String id, ResetDisposition progressPolicy) {
        return new SeasonDefinition(new SeasonId(id), id.replace('_', ' '), Optional.empty(), Optional.empty(),
                progressPolicy, Map.of(), Map.of());
    }

    private static RequirementDefinition requirement() {
        return RequirementDefinition.create(REQUIREMENT, PROVIDER, new MetricId("activity"),
                MetricOperator.GREATER_OR_EQUAL, RequirementTarget.single(MetricValue.count(10)),
                MeasurementScope.SINCE_SEASON_START, CompletionMode.LIVE, ScalingProfile.none(),
                CatchUpProfile.disabled(), Map.of(), Map.of(), false);
    }

    private static Map<RequirementId, MetricSample> samples(long value) {
        return Map.of(REQUIREMENT, MetricSample.available(MetricValue.count(value), 1, NOW, "season-test"));
    }
}
