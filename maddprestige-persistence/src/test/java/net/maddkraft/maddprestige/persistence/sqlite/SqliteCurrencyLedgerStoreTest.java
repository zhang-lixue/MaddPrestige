package net.maddkraft.maddprestige.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.CurrencyId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.action.ActionExecutionStatus;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.cost.PlannedCost;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardFailurePolicy;
import net.maddkraft.maddprestige.api.reward.RewardRepeatability;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.currency.CurrencyDefinition;
import net.maddkraft.maddprestige.core.currency.CurrencyMutationKind;
import net.maddkraft.maddprestige.core.currency.InternalCurrencyService;
import net.maddkraft.maddprestige.core.currency.InternalCurrencyCostProvider;
import net.maddkraft.maddprestige.core.currency.InternalCurrencyRewardProvider;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConnection;
import org.sqlite.SQLiteDataSource;

class SqliteCurrencyLedgerStoreTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-15T12:00:00Z"), ZoneOffset.UTC);
    private static final ConfigRevisionId REVISION = new ConfigRevisionId("prestige-lifecycle-currency");
    private static final CurrencyId CURRENCY = new CurrencyId("generic_credit");
    @TempDir
    Path temporaryDirectory;
    private SqliteCurrencyLedgerStore store;
    private SqliteFoundation sqlite;
    private AtomicReference<Map<CurrencyId, CurrencyDefinition>> definitions;
    private InternalCurrencyService service;
    private List<Integer> retryAttempts;

    @BeforeEach
    void migrate() {
        Path database = temporaryDirectory.resolve("currency.db");
        sqlite = new SqliteFoundation(database);
        new MigrationRunner(sqlite, new FileBackupService(database, temporaryDirectory.resolve("backups"), CLOCK),
                CLOCK).migrate(SqliteMigrations.throughVersionFive());
        new SqliteConfigRevisionRepository(sqlite).insert(REVISION, RevisionHasher.hashText("Prestige lifecycle currency"));
        retryAttempts = java.util.Collections.synchronizedList(new ArrayList<>());
        store = new SqliteCurrencyLedgerStore(sqlite, retryAttempts::add);
        definitions = new AtomicReference<>(Map.of(CURRENCY, definition("Credits", "¤")));
        service = new InternalCurrencyService(definitions::get, store, CLOCK);
    }

    @Test
    @DisplayName("[A30] Stable currency ID preserves exact balance/history across display rename")
    void exactIdempotentLedgerSurvivesDisplayRename() {
        UUID player = UUID.randomUUID();
        Actor actor = new Actor("system", Optional.empty(), "System");
        OperationId first = OperationId.random();
        var applied = service.earn(first, "reward", player, CURRENCY, ExactDecimal.parse("0.10"), actor,
                "milestone", "first exact reward", REVISION);
        var replay = service.earn(first, "reward", player, CURRENCY, ExactDecimal.parse("0.10"), actor,
                "milestone", "first exact reward", REVISION);
        service.earn(OperationId.random(), "reward", player, CURRENCY, ExactDecimal.parse("0.20"), actor,
                "prestige", "second exact reward", REVISION);
        definitions.set(Map.of(CURRENCY, definition("Renamed Unit", "R")));

        assertFalse(applied.replay());
        assertTrue(replay.replay());
        assertEquals(ExactDecimal.parse("0.30"), store.balance(player, CURRENCY));
        assertEquals(2, store.history(player, CURRENCY, 10).size());
        assertEquals(CurrencyMutationKind.EARN, store.history(player, CURRENCY, 10).getFirst().kind());
    }

    @Test
    @DisplayName("[A30] Replay identity binds complete audit provenance but not retry timing")
    void idempotencyBindsSemanticAuditProvenance() {
        UUID player = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Actor actor = new Actor("staff", Optional.of(actorId), "Auditor");
        OperationId operation = OperationId.random();
        service.earn(operation, "semantic-replay", player, CURRENCY, ExactDecimal.parse("1"), actor,
                "admin", "case 42", REVISION);
        InternalCurrencyService laterRetry = new InternalCurrencyService(definitions::get, store,
                Clock.offset(CLOCK, Duration.ofSeconds(30)));

        assertTrue(laterRetry.earn(operation, "semantic-replay", player, CURRENCY, ExactDecimal.parse("1"), actor,
                "admin", "case 42", REVISION).replay(), "occurredAt is retry metadata, not semantic identity");
        assertThrows(SecurityException.class, () -> laterRetry.earn(operation, "semantic-replay", player,
                CURRENCY, ExactDecimal.parse("1"), new Actor("console", Optional.of(actorId), "Auditor"),
                "admin", "case 42", REVISION));
        assertThrows(SecurityException.class, () -> laterRetry.earn(operation, "semantic-replay", player,
                CURRENCY, ExactDecimal.parse("1"), new Actor("staff", Optional.of(UUID.randomUUID()), "Auditor"),
                "admin", "case 42", REVISION));
        assertThrows(SecurityException.class, () -> laterRetry.earn(operation, "semantic-replay", player,
                CURRENCY, ExactDecimal.parse("1"), new Actor("staff", Optional.of(actorId), "Renamed Auditor"),
                "admin", "case 42", REVISION));
        assertThrows(SecurityException.class, () -> laterRetry.earn(operation, "semantic-replay", player,
                CURRENCY, ExactDecimal.parse("1"), actor, "migration", "case 42", REVISION));
        assertThrows(SecurityException.class, () -> laterRetry.earn(operation, "semantic-replay", player,
                CURRENCY, ExactDecimal.parse("1"), actor, "admin", "case 43", REVISION));

        assertEquals(ExactDecimal.parse("1"), store.balance(player, CURRENCY));
        assertEquals(1, store.history(player, CURRENCY, 10).size());
    }

    @Test
    @DisplayName("[A30] Insufficient spend and invalid scale fail without partial mutation")
    void insufficientBalanceHasNoPartialMutation() {
        UUID player = UUID.randomUUID();
        Actor actor = new Actor("system", Optional.empty(), "System");
        service.earn(OperationId.random(), "earn", player, CURRENCY, ExactDecimal.parse("1.00"), actor,
                "test", "seed", REVISION);

        assertThrows(IllegalArgumentException.class, () -> service.spend(OperationId.random(), "spend", player,
                CURRENCY, ExactDecimal.parse("1.01"), actor, "test", "too much", REVISION));
        assertThrows(IllegalArgumentException.class, () -> service.earn(OperationId.random(), "scale", player,
                CURRENCY, ExactDecimal.parse("0.001"), actor, "test", "bad scale", REVISION));
        assertEquals(ExactDecimal.parse("1"), store.balance(player, CURRENCY));
        assertEquals(1, store.history(player, CURRENCY, 10).size());
    }

    @Test
    @DisplayName("[A30] Administrative adjustments are actor/reason bound and concurrent-safe")
    void auditedConcurrentAdjustment() {
        Actor staff = new Actor("staff", Optional.of(UUID.randomUUID()), "Auditor");
        UUID unauthorizedPlayer = UUID.randomUUID();
        assertThrows(SecurityException.class, () -> service.adjust(OperationId.random(), "adjust", unauthorizedPlayer,
                CURRENCY, ExactDecimal.parse("1"), new Actor("player", Optional.of(unauthorizedPlayer), "Player"),
                "not authorized", REVISION));
        int repetitions = Integer.getInteger("maddprestige.sqlite.currency.stress-repetitions", 1);
        long started = System.nanoTime();
        for (int repetition = 0; repetition < repetitions; repetition++) {
            UUID player = UUID.randomUUID();
            String suffix = Integer.toString(repetition);
            CompletableFuture.allOf(
                    CompletableFuture.runAsync(() -> service.adjust(OperationId.random(), "adjust-a-" + suffix,
                            player, CURRENCY, ExactDecimal.parse("1.25"), staff, "case A " + suffix, REVISION)),
                    CompletableFuture.runAsync(() -> service.adjust(OperationId.random(), "adjust-b-" + suffix,
                            player, CURRENCY, ExactDecimal.parse("2.75"), staff, "case B " + suffix, REVISION))).join();

            assertEquals(ExactDecimal.parse("4"), store.balance(player, CURRENCY));
            assertEquals(2, store.history(player, CURRENCY, 10).size());
            assertTrue(store.history(player, CURRENCY, 10).stream()
                    .allMatch(entry -> entry.kind() == CurrencyMutationKind.ADJUSTMENT
                            && entry.actorName().equals("Auditor") && entry.reason().startsWith("case")));
        }
        if (repetitions > 1) {
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            Map<Integer, Long> distribution = retryAttempts.stream().collect(java.util.stream.Collectors.groupingBy(
                    Integer::intValue, java.util.TreeMap::new, java.util.stream.Collectors.counting()));
            System.out.printf("SQLite currency stress: repetitions=%d, retriesByFailedAttempt=%s, elapsedMs=%d%n",
                    repetitions, distribution, elapsedMillis);
        }
    }

    @Test
    @DisplayName("[A30] Multiple snapshot conflicts back off before one successful mutation")
    void retriesSeveralSnapshotConflictsBeforeSuccess() {
        AtomicInteger opens = new AtomicInteger();
        ArrayList<Integer> retries = new ArrayList<>();
        ConnectionProvider transientContention = () -> {
            if (opens.incrementAndGet() <= 3) {
                throw busySnapshot();
            }
            return sqlite.open();
        };
        SqliteCurrencyLedgerStore retryingStore = new SqliteCurrencyLedgerStore(transientContention, retries::add);
        InternalCurrencyService retryingService = new InternalCurrencyService(definitions::get, retryingStore, CLOCK);
        UUID player = UUID.randomUUID();

        var result = retryingService.earn(OperationId.random(), "eventual", player, CURRENCY,
                ExactDecimal.parse("2"), new Actor("system", Optional.empty(), "Retry test"),
                "test", "eventual success", REVISION);

        assertFalse(result.replay());
        assertEquals(List.of(1, 2, 3), retries);
        assertEquals(4, opens.get());
        assertEquals(ExactDecimal.parse("2"), retryingStore.balance(player, CURRENCY));
        assertEquals(1, retryingStore.history(player, CURRENCY, 10).size());
    }

    @Test
    @DisplayName("[A30] A temporary writer conflict waits within SQLite's bounded busy timeout")
    void temporaryWriterConflictWaitsThenSucceeds() throws Exception {
        CountDownLatch contenderOpened = new CountDownLatch(1);
        ArrayList<Integer> retries = new ArrayList<>();
        ConnectionProvider observedConnections = () -> {
            contenderOpened.countDown();
            return sqlite.open();
        };
        SqliteCurrencyLedgerStore waitingStore = new SqliteCurrencyLedgerStore(observedConnections, retries::add);
        InternalCurrencyService waitingService = new InternalCurrencyService(definitions::get, waitingStore, CLOCK);
        UUID player = UUID.randomUUID();
        CompletableFuture<net.maddkraft.maddprestige.core.currency.CurrencyMutationResult> pending;

        try (Connection blocker = sqlite.open()) {
            SQLiteConnection nativeConnection = blocker.unwrap(SQLiteConnection.class);
            nativeConnection.getConnectionConfig().setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
            blocker.setAutoCommit(false);
            pending = CompletableFuture.supplyAsync(() -> waitingService.earn(OperationId.random(), "waited", player,
                    CURRENCY, ExactDecimal.parse("1"), new Actor("system", Optional.empty(), "Retry test"),
                    "test", "temporary writer", REVISION));
            assertTrue(contenderOpened.await(1, java.util.concurrent.TimeUnit.SECONDS));
            Thread.sleep(50);
            assertFalse(pending.isDone(), "the competing writer should still be waiting for the reservation");
        }

        var result = pending.join();
        assertFalse(result.replay());
        assertTrue(retries.isEmpty(), "SQLite busy_timeout should absorb ordinary writer contention");
        assertEquals(ExactDecimal.parse("1"), waitingStore.balance(player, CURRENCY));
        assertEquals(1, waitingStore.history(player, CURRENCY, 10).size());
    }

    @Test
    @DisplayName("[A30] Retry after an ambiguous close replays instead of duplicating the committed mutation")
    void ambiguousPostCommitFailureReplaysIdempotently() {
        AtomicBoolean injectCloseFailure = new AtomicBoolean(true);
        ArrayList<Integer> retries = new ArrayList<>();
        ConnectionProvider ambiguousCommit = () -> closeFailsOnce(sqlite.open(), injectCloseFailure);
        SqliteCurrencyLedgerStore retryingStore = new SqliteCurrencyLedgerStore(ambiguousCommit, retries::add);
        InternalCurrencyService retryingService = new InternalCurrencyService(definitions::get, retryingStore, CLOCK);
        UUID player = UUID.randomUUID();
        OperationId operation = OperationId.random();

        var result = retryingService.earn(operation, "ambiguous", player, CURRENCY, ExactDecimal.parse("1"),
                new Actor("system", Optional.empty(), "Retry test"), "test", "ambiguous commit", REVISION);

        assertTrue(result.replay(), "the retry must observe the already committed idempotency key");
        assertEquals(List.of(1), retries);
        assertEquals(ExactDecimal.parse("1"), retryingStore.balance(player, CURRENCY));
        assertEquals(1, retryingStore.history(player, CURRENCY, 10).size());
    }

    @Test
    @DisplayName("[A30] Persistent snapshot contention exhausts the bounded retry policy cleanly")
    void persistentSnapshotContentionIsBounded() {
        AtomicInteger opens = new AtomicInteger();
        ArrayList<Integer> retries = new ArrayList<>();
        ConnectionProvider persistentContention = () -> {
            opens.incrementAndGet();
            throw busySnapshot();
        };
        SqliteCurrencyLedgerStore retryingStore = new SqliteCurrencyLedgerStore(persistentContention, retries::add);
        InternalCurrencyService retryingService = new InternalCurrencyService(definitions::get, retryingStore, CLOCK);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThrows(PersistenceException.class,
                () -> retryingService.earn(OperationId.random(), "exhausted", UUID.randomUUID(), CURRENCY,
                        ExactDecimal.parse("1"), new Actor("system", Optional.empty(), "Retry test"),
                        "test", "persistent contention", REVISION)));
        assertEquals(8, opens.get());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), retries);
    }

    @Test
    @DisplayName("[A30] A persistent writer lock fails after one bounded SQLite busy window")
    void persistentWriterLockIsBounded() throws Exception {
        SQLiteConfig impatient = new SQLiteConfig();
        impatient.setBusyTimeout(75);
        SQLiteDataSource dataSource = new SQLiteDataSource(impatient);
        dataSource.setUrl("jdbc:sqlite:" + sqlite.databaseFile());
        AtomicInteger opens = new AtomicInteger();
        ArrayList<Integer> retries = new ArrayList<>();
        ConnectionProvider shortBusyWindow = () -> {
            opens.incrementAndGet();
            return dataSource.getConnection();
        };
        SqliteCurrencyLedgerStore waitingStore = new SqliteCurrencyLedgerStore(shortBusyWindow, retries::add);
        InternalCurrencyService waitingService = new InternalCurrencyService(definitions::get, waitingStore, CLOCK);

        try (Connection blocker = sqlite.open()) {
            SQLiteConnection nativeConnection = blocker.unwrap(SQLiteConnection.class);
            nativeConnection.getConnectionConfig().setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
            blocker.setAutoCommit(false);
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertThrows(PersistenceException.class,
                    () -> waitingService.earn(OperationId.random(), "locked", UUID.randomUUID(), CURRENCY,
                            ExactDecimal.parse("1"), new Actor("system", Optional.empty(), "Retry test"),
                            "test", "persistent writer", REVISION)));
        }
        assertEquals(1, opens.get(), "ordinary SQLITE_BUSY already exhausted its configured wait window");
        assertTrue(retries.isEmpty(), "an exhausted busy_timeout must not be multiplied by application retries");
    }

    @Test
    @DisplayName("[A30] Non-contention SQL failures are never retried")
    void nonContentionFailureIsNotRetried() {
        AtomicInteger opens = new AtomicInteger();
        ArrayList<Integer> retries = new ArrayList<>();
        ConnectionProvider broken = () -> {
            opens.incrementAndGet();
            throw new SQLException("disk I/O error", "", 10);
        };
        SqliteCurrencyLedgerStore retryingStore = new SqliteCurrencyLedgerStore(broken, retries::add);
        InternalCurrencyService retryingService = new InternalCurrencyService(definitions::get, retryingStore, CLOCK);

        assertThrows(PersistenceException.class,
                () -> retryingService.earn(OperationId.random(), "broken", UUID.randomUUID(), CURRENCY,
                        ExactDecimal.parse("1"), new Actor("system", Optional.empty(), "Retry test"),
                        "test", "non-contention", REVISION));
        assertEquals(1, opens.get());
        assertTrue(retries.isEmpty());
    }

    @Test
    @DisplayName("[A30] Independent concurrent accounts and currencies preserve every adjustment")
    void independentConcurrentAdjustmentsHaveNoLostUpdates() {
        CurrencyId secondary = new CurrencyId("secondary_credit");
        definitions.set(Map.of(CURRENCY, definition("Credits", "¤"),
                secondary, definition(secondary, "Secondary", "S")));
        SqliteCurrencyLedgerStore firstStore = new SqliteCurrencyLedgerStore(sqlite);
        SqliteCurrencyLedgerStore secondStore = new SqliteCurrencyLedgerStore(sqlite);
        InternalCurrencyService first = new InternalCurrencyService(definitions::get, firstStore, CLOCK);
        InternalCurrencyService second = new InternalCurrencyService(definitions::get, secondStore, CLOCK);
        Actor staff = new Actor("staff", Optional.of(UUID.randomUUID()), "Concurrency test");
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        ArrayList<CompletableFuture<Void>> writes = new ArrayList<>();

        for (int index = 0; index < 24; index++) {
            int mutation = index;
            writes.add(CompletableFuture.runAsync(() -> {
                await(start);
                UUID player = mutation % 2 == 0 ? firstPlayer : secondPlayer;
                CurrencyId currency = mutation % 4 < 2 ? CURRENCY : secondary;
                InternalCurrencyService selected = mutation % 3 == 0 ? first : second;
                selected.adjust(OperationId.random(), "independent-" + mutation, player, currency,
                        ExactDecimal.parse("1"), staff, "case " + mutation, REVISION);
            }));
        }
        start.countDown();
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();

        assertEquals(ExactDecimal.parse("6"), firstStore.balance(firstPlayer, CURRENCY));
        assertEquals(ExactDecimal.parse("6"), firstStore.balance(firstPlayer, secondary));
        assertEquals(ExactDecimal.parse("6"), firstStore.balance(secondPlayer, CURRENCY));
        assertEquals(ExactDecimal.parse("6"), firstStore.balance(secondPlayer, secondary));
        assertEquals(6, firstStore.history(firstPlayer, CURRENCY, 10).size());
        assertEquals(6, firstStore.history(firstPlayer, secondary, 10).size());
        assertEquals(6, firstStore.history(secondPlayer, CURRENCY, 10).size());
        assertEquals(6, firstStore.history(secondPlayer, secondary, 10).size());
    }

    @Test
    @DisplayName("[A30] Native cost/reward/compensation retain sealed R1 provenance after active R2")
    void costAndRewardProvidersUseLedger() {
        UUID player = UUID.randomUUID();
        service.earn(OperationId.random(), "seed", player, CURRENCY, ExactDecimal.parse("5"),
                new Actor("system", Optional.empty(), "System"), "test", "seed", REVISION);
        InternalCurrencyCostProvider costs = new InternalCurrencyCostProvider(new ProviderId("internal_cost"),
                "test", definitions::get, store, CLOCK);
        InternalCurrencyRewardProvider rewards = new InternalCurrencyRewardProvider(
                new ProviderId("internal_reward"), "test", definitions::get, store, CLOCK);
        CostDefinition costDefinition = new CostDefinition(new CostId("entry"), costs.descriptor().id(),
                InternalCurrencyCostProvider.TYPE, MetricValue.decimal("1"),
                Map.of(InternalCurrencyCostProvider.CURRENCY_ID, CURRENCY.value()), "Entry");
        RewardDefinition rewardDefinition = new RewardDefinition(new RewardId("bonus"), rewards.descriptor().id(),
                InternalCurrencyCostProvider.TYPE, MetricValue.decimal("2"),
                Map.of(InternalCurrencyCostProvider.CURRENCY_ID, CURRENCY.value()), "Bonus",
                RewardFailurePolicy.REQUIRED, RewardRepeatability.ONCE_PER_OPERATION);
        OperationId operation = OperationId.random();
        PlannedCost cost = new PlannedCost(operation, "cost-0", player, costDefinition, REVISION, 1,
                costs.characteristics(costDefinition), "entry");
        PlannedReward reward = new PlannedReward(operation, "reward-0", player, rewardDefinition, REVISION, 1,
                rewards.characteristics(rewardDefinition), "bonus");
        ConfigRevisionId activeR2 = new ConfigRevisionId("prestige-lifecycle-currency-r2");
        new SqliteConfigRevisionRepository(sqlite).insert(activeR2, RevisionHasher.hashText("Prestige lifecycle R2"));
        AtomicReference<ConfigRevisionId> activeRevision = new AtomicReference<>(REVISION);
        activeRevision.set(activeR2);

        assertEquals(activeR2, activeRevision.get(), "the mutable active configuration has advanced to R2");
        assertEquals(ActionExecutionStatus.APPLIED, costs.execute(cost).toCompletableFuture().join().status());
        assertEquals(ActionExecutionStatus.UNCHANGED, costs.execute(cost).toCompletableFuture().join().status());
        assertEquals(ActionExecutionStatus.APPLIED, rewards.execute(reward).toCompletableFuture().join().status());
        assertEquals(ActionExecutionStatus.APPLIED, costs.compensate(cost).toCompletableFuture().join().status());
        assertEquals(ExactDecimal.parse("7"), store.balance(player, CURRENCY));
        assertEquals(4, store.history(player, CURRENCY, 10).size());
        assertTrue(store.history(player, CURRENCY, 10).stream()
                .allMatch(entry -> entry.configRevision().equals(REVISION)),
                "native cost/reward/compensation provenance must come from each sealed planned action");
    }

    @Test
    @DisplayName("[A30] Two independent stores serialize credits/debits/idempotency at the SQLite boundary")
    void crossInstanceConcurrencyIsDatabaseAuthoritative() {
        SqliteCurrencyLedgerStore firstStore = new SqliteCurrencyLedgerStore(sqlite);
        SqliteCurrencyLedgerStore secondStore = new SqliteCurrencyLedgerStore(sqlite);
        InternalCurrencyService first = new InternalCurrencyService(definitions::get, firstStore, CLOCK);
        InternalCurrencyService second = new InternalCurrencyService(definitions::get, secondStore, CLOCK);
        Actor actor = new Actor("system", Optional.empty(), "Concurrency test");

        UUID credits = UUID.randomUUID();
        runTogether(() -> first.earn(OperationId.random(), "credit-a", credits, CURRENCY,
                        ExactDecimal.parse("1.25"), actor, "test", "credit A", REVISION),
                () -> second.earn(OperationId.random(), "credit-b", credits, CURRENCY,
                        ExactDecimal.parse("2.75"), actor, "test", "credit B", REVISION));
        assertEquals(ExactDecimal.parse("4"), firstStore.balance(credits, CURRENCY));
        assertEquals(2, secondStore.history(credits, CURRENCY, 10).size());

        UUID debits = UUID.randomUUID();
        first.earn(OperationId.random(), "seed", debits, CURRENCY, ExactDecimal.parse("1"), actor,
                "test", "seed", REVISION);
        CountDownLatch debitStart = new CountDownLatch(1);
        CompletableFuture<Boolean> debitA = CompletableFuture.supplyAsync(() -> spendAfter(debitStart, first,
                debits, actor, "debit-a"));
        CompletableFuture<Boolean> debitB = CompletableFuture.supplyAsync(() -> spendAfter(debitStart, second,
                debits, actor, "debit-b"));
        debitStart.countDown();
        assertEquals(1, java.util.stream.Stream.of(debitA.join(), debitB.join()).filter(Boolean::booleanValue).count());
        assertEquals(ExactDecimal.ZERO, firstStore.balance(debits, CURRENCY));
        assertEquals(2, firstStore.history(debits, CURRENCY, 10).size());

        UUID replayPlayer = UUID.randomUUID();
        OperationId replayOperation = OperationId.random();
        var results = runTogether(() -> first.earn(replayOperation, "same-action", replayPlayer, CURRENCY,
                        ExactDecimal.parse("3"), actor, "test", "same", REVISION),
                () -> second.earn(replayOperation, "same-action", replayPlayer, CURRENCY,
                        ExactDecimal.parse("3"), actor, "test", "same", REVISION));
        assertEquals(1, results.stream().filter(value -> !value.replay()).count());
        assertEquals(1, results.stream().filter(net.maddkraft.maddprestige.core.currency.CurrencyMutationResult::replay)
                .count());
        assertEquals(ExactDecimal.parse("3"), firstStore.balance(replayPlayer, CURRENCY));
        assertEquals(1, firstStore.history(replayPlayer, CURRENCY, 10).size());
    }

    private static java.util.List<net.maddkraft.maddprestige.core.currency.CurrencyMutationResult> runTogether(
            java.util.function.Supplier<net.maddkraft.maddprestige.core.currency.CurrencyMutationResult> first,
            java.util.function.Supplier<net.maddkraft.maddprestige.core.currency.CurrencyMutationResult> second) {
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<net.maddkraft.maddprestige.core.currency.CurrencyMutationResult> left =
                CompletableFuture.supplyAsync(() -> after(start, first));
        CompletableFuture<net.maddkraft.maddprestige.core.currency.CurrencyMutationResult> right =
                CompletableFuture.supplyAsync(() -> after(start, second));
        start.countDown();
        return java.util.List.of(left.join(), right.join());
    }

    private static net.maddkraft.maddprestige.core.currency.CurrencyMutationResult after(
            CountDownLatch start,
            java.util.function.Supplier<net.maddkraft.maddprestige.core.currency.CurrencyMutationResult> action) {
        await(start);
        return action.get();
    }

    private static boolean spendAfter(
            CountDownLatch start,
            InternalCurrencyService service,
            UUID player,
            Actor actor,
            String actionId) {
        await(start);
        try {
            service.spend(OperationId.random(), actionId, player, CURRENCY, ExactDecimal.parse("1"), actor,
                    "test", actionId, REVISION);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static SQLException busySnapshot() {
        return new SQLException("[SQLITE_BUSY_SNAPSHOT] database is locked", "", 517);
    }

    private static Connection closeFailsOnce(Connection delegate, AtomicBoolean injectFailure) {
        return (Connection) Proxy.newProxyInstance(SqliteCurrencyLedgerStoreTest.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("close") && injectFailure.compareAndSet(true, false)) {
                        invoke(delegate, method, arguments);
                        throw busySnapshot();
                    }
                    return invoke(delegate, method, arguments);
                });
    }

    private static Object invoke(Connection delegate, java.lang.reflect.Method method, Object[] arguments)
            throws Throwable {
        try {
            return method.invoke(delegate, arguments);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static CurrencyDefinition definition(String name, String symbol) {
        return definition(CURRENCY, name, symbol);
    }

    private static CurrencyDefinition definition(CurrencyId currencyId, String name, String symbol) {
        return new CurrencyDefinition(currencyId, name, Optional.of(symbol), 2, RoundingMode.UNNECESSARY, 12,
                ExactDecimal.parse("9999999999.99"), true);
    }
}
