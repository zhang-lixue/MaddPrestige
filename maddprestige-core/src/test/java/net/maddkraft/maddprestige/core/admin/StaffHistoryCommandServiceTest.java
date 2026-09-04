package net.maddkraft.maddprestige.core.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.core.admin.command.CommandCompletionService;
import net.maddkraft.maddprestige.core.admin.command.CommandResponse;
import net.maddkraft.maddprestige.core.admin.command.StaffHistoryCommandService;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.core.admin.ui.StaffHistoryPresentation;
import net.maddkraft.maddprestige.core.admin.ui.StaffHistorySource;
import net.maddkraft.maddprestige.core.admin.ui.StaffPlayerDirectory;
import net.maddkraft.maddprestige.core.admin.ui.StaffPlayerIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StaffHistoryCommandServiceTest {
    private static final UUID STAFF = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PLAYER = UUID.fromString("d7551bf9-6358-3218-89c4-06c9c57dc879");
    private static final Instant NOW = Instant.parse("2026-09-02T20:24:00Z");

    @Test
    @DisplayName("[Phase 9F-B] Player history summary is selected-player filtered, newest-first, concise, and paged")
    void summaryUsesCanonicalSelectedPlayerPageAndOutcomeLanguage() {
        List<StaffHistorySource.Entry> entries = List.of(
                entry(1, 5, 6, StaffHistorySource.Outcome.COMPLETED),
                entry(2, 4, 5, StaffHistorySource.Outcome.RECOVERED),
                entry(3, 3, 4, StaffHistorySource.Outcome.FAILED));
        HistorySource source = new HistorySource(entries, 8);
        StaffHistoryCommandService service = service(source);

        CommandResponse response = service.execute(staff(), List.of("history", "tmydwc", "1"))
                .toCompletableFuture().join();

        assertTrue(response.successful());
        assertEquals(PLAYER, source.requestedPlayer.get());
        assertEquals(0, source.requestedOffset.get());
        assertEquals(5, source.requestedLimit.get());
        assertEquals(List.of("command.history.header", "command.history.entry.completed",
                "command.history.entry.recovered", "command.history.entry.failed", "command.history.page"),
                response.messages().stream().map(MessageReference::key).toList());
        assertEquals("1", response.messages().getLast().argument("current").orElseThrow());
        assertEquals("2", response.messages().getLast().argument("total").orElseThrow());
        assertEquals("", response.messages().getLast().argument("previous").orElseThrow());
        assertEquals("2", response.messages().getLast().argument("next").orElseThrow());
        assertEquals(StaffHistoryPresentation.timestamp(NOW),
                response.messages().get(1).argument("value").orElseThrow());
        assertTrue(response.messages().subList(1, 4).stream().allMatch(line ->
                line.argument("id").isPresent() && line.argument("player").filter("tmydwc"::equals).isPresent()));
    }

    @Test
    @DisplayName("[Phase 9F-B UX] History pagination exposes truthful Previous and Next command targets")
    void paginationCarriesOnlyAvailableDirectPageTargets() {
        List<StaffHistorySource.Entry> entries = java.util.stream.IntStream.rangeClosed(1, 8)
                .mapToObj(index -> entry(index, index - 1L, index,
                        StaffHistorySource.Outcome.COMPLETED))
                .toList();
        HistorySource source = new HistorySource(entries, entries.size());
        StaffHistoryCommandService service = service(source);

        MessageReference first = service.execute(staff(), List.of("history", "tmydwc", "1"))
                .toCompletableFuture().join().messages().getLast();
        MessageReference last = service.execute(staff(), List.of("history", "tmydwc", "2"))
                .toCompletableFuture().join().messages().getLast();

        assertEquals("", first.argument("previous").orElseThrow());
        assertEquals("2", first.argument("next").orElseThrow());
        assertEquals("1", last.argument("previous").orElseThrow());
        assertEquals("", last.argument("next").orElseThrow());
        assertEquals("tmydwc", last.argument("player").orElseThrow());
        assertEquals(5, source.requestedOffset.get(), "direct page argument must remain authoritative");
    }

    @Test
    @DisplayName("[Phase 9F-B] Page selection and compact empty history never read another player")
    void selectsPageAndRendersCompactEmptyState() {
        HistorySource source = new HistorySource(List.of(), 0);
        StaffHistoryCommandService service = service(source);

        CommandResponse empty = service.execute(staff(), List.of("history", PLAYER.toString()))
                .toCompletableFuture().join();
        CommandResponse invalidPage = service.execute(staff(), List.of("history", "tmydwc", "2"))
                .toCompletableFuture().join();

        assertEquals(List.of("command.history.header", "command.history.empty"),
                empty.messages().stream().map(MessageReference::key).toList());
        assertFalse(invalidPage.successful());
        assertEquals("command.history.page_invalid", invalidPage.messages().getFirst().key());
        assertEquals(5, source.requestedOffset.get());
        assertEquals(PLAYER, source.requestedPlayer.get());
    }

    @Test
    @DisplayName("[Phase 9F-B] Stable detail lookup presents canonical transition, balance, reward, outcome, and time")
    void detailUsesStableOperationIdAndCanonicalFinancialProjection() {
        UUID entryId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        StaffHistorySource.Entry entry = new StaffHistorySource.Entry(entryId, "tmydwc", 5, 6,
                StaffHistorySource.Outcome.RECOVERED, true, true,
                Optional.of("$7"), Optional.of("$1"), Optional.of("$5"), Optional.of("$1"), NOW);
        HistorySource source = new HistorySource(List.of(entry), 1);
        StaffHistoryCommandService service = service(source);

        CommandResponse response = service.execute(staff(), List.of(
                "history", "details", "tmydwc", entryId.toString())).toCompletableFuture().join();

        assertTrue(response.successful());
        assertEquals(entryId, source.requestedEntry.get());
        assertEquals(List.of("command.history.detail.header", "command.history.outcome.recovered",
                "command.history.detail.balance", "command.history.detail.reward",
                "command.history.detail.time"),
                response.messages().stream().map(MessageReference::key).toList());
        assertEquals("$7", response.messages().get(2).argument("before").orElseThrow());
        assertEquals("$1", response.messages().get(2).argument("after").orElseThrow());
        assertEquals("$1", response.messages().get(3).argument("value").orElseThrow());
        assertEquals(StaffHistoryPresentation.timestamp(NOW),
                response.messages().getLast().argument("value").orElseThrow());
    }

    @Test
    @DisplayName("[Phase 9F-B UX] History detail truthfully falls back to cost when balance snapshots are absent")
    void detailFallsBackToCanonicalCostWithoutFabricatingBalance() {
        UUID entryId = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        StaffHistorySource.Entry entry = new StaffHistorySource.Entry(entryId, "tmydwc", 4, 5,
                StaffHistorySource.Outcome.COMPLETED, true, true,
                Optional.empty(), Optional.empty(), Optional.of("$7"), Optional.of("$1"), NOW);
        StaffHistoryCommandService service = service(new HistorySource(List.of(entry), 1));

        CommandResponse response = service.execute(staff(), List.of(
                "history", "details", "tmydwc", entryId.toString())).toCompletableFuture().join();

        assertEquals(List.of("command.history.detail.header", "command.history.outcome.completed",
                "command.history.detail.cost", "command.history.detail.reward",
                "command.history.detail.time"),
                response.messages().stream().map(MessageReference::key).toList());
        assertTrue(response.messages().stream().noneMatch(line ->
                line.key().equals("command.history.detail.balance")));
    }

    @Test
    @DisplayName("[Phase 9F-C1] Admin Set/Reset command history identifies the actor without fake finances")
    void administrativeHistoryIsDistinctAndNeverPresentsNormalPrestigeFinances() {
        UUID entryId = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
        StaffHistorySource.Entry entry = new StaffHistorySource.Entry(entryId, "tmydwc",
                StaffHistorySource.Kind.ADMIN_RESET, Optional.of(STAFF), Optional.of("Owner"),
                8, 0, StaffHistorySource.Outcome.COMPLETED, true, true,
                Optional.of("$8"), Optional.of("$1"), Optional.of("$7"), Optional.of("$1"), NOW);
        StaffHistoryCommandService service = service(new HistorySource(List.of(entry), 1));

        CommandResponse summary = service.execute(staff(), List.of("history", "tmydwc"))
                .toCompletableFuture().join();
        CommandResponse detail = service.execute(staff(), List.of(
                "history", "details", "tmydwc", entryId.toString())).toCompletableFuture().join();

        assertEquals("command.history.kind.admin_reset", summary.messages().get(1).key());
        assertEquals(List.of("command.history.detail.admin_header", "command.history.detail.actor",
                "command.history.outcome.completed", "command.history.detail.time"),
                detail.messages().stream().map(MessageReference::key).toList());
        assertEquals("Admin Reset", detail.messages().getFirst().argument("type").orElseThrow());
        assertEquals("Owner", detail.messages().get(1).argument("player").orElseThrow());
        assertTrue(detail.messages().stream().noneMatch(line -> Set.of(
                "command.history.detail.balance", "command.history.detail.cost",
                "command.history.detail.reward").contains(line.key())));
    }

    @Test
    @DisplayName("[Phase 9F-B] Missing, invalid, and cross-player detail selectors fail closed")
    void invalidAndMissingDetailsFailClosed() {
        HistorySource source = new HistorySource(List.of(), 0);
        StaffHistoryCommandService service = service(source);

        CommandResponse invalid = service.execute(staff(), List.of(
                "history", "details", "tmydwc", "not-an-id")).toCompletableFuture().join();
        CommandResponse missing = service.execute(staff(), List.of(
                "history", "details", "tmydwc", UUID.randomUUID().toString()))
                .toCompletableFuture().join();
        CommandResponse unknownPlayer = service.execute(staff(), List.of("history", "unknown"))
                .toCompletableFuture().join();

        assertFalse(invalid.successful());
        assertEquals("command.history.entry_invalid", invalid.messages().getFirst().key());
        assertFalse(missing.successful());
        assertEquals("command.history.entry_unknown", missing.messages().getFirst().key());
        assertFalse(unknownPlayer.successful());
        assertEquals("command.history.player_unknown", unknownPlayer.messages().getFirst().key());
    }

    @Test
    @DisplayName("[Phase 9F-B] History permission and completion remain isolated and read-only")
    void permissionAwareCompletionCachesOnlyDisplayedCanonicalIdsWithoutMutation() {
        StaffHistorySource.Entry entry = entry(4, 2, 3, StaffHistorySource.Outcome.COMPLETED);
        HistorySource source = new HistorySource(List.of(entry), 1);
        StaffHistoryCommandService service = service(source);
        CommandCompletionService completion = new CommandCompletionService(service);

        assertThrows(AdministrationException.class, () -> service.execute(ordinary(),
                List.of("history", "tmydwc")));
        assertFalse(completion.suggest(ordinary(), List.of("")).contains("history"));
        assertTrue(completion.suggest(staff(), List.of("")).contains("history"));
        assertEquals(List.of("tmydwc"), completion.suggest(staff(), List.of("history", "t")));
        assertEquals(List.of("tmydwc"), completion.suggest(staff(), List.of("history", "details", "t")));

        service.execute(staff(), List.of("history", "tmydwc")).toCompletableFuture().join();

        assertEquals(List.of(entry.entryId().toString()),
                completion.suggest(staff(), List.of("history", "details", "tmydwc", "")));
        assertEquals(1, source.reads.get());
        assertEquals(0, source.mutations.get());
    }

    @Test
    @DisplayName("[Phase 9F-B] Offline known players remain valid history and admin-find selectors")
    void offlineKnownPlayerRemainsAvailableToHistoryAndCompletion() {
        UUID offlineId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        StaffPlayerDirectory directory = new StaffPlayerDirectory() {
            @Override
            public List<StaffPlayerIdentity> onlinePlayers() {
                return List.of();
            }

            @Override
            public List<StaffPlayerIdentity> knownPlayers() {
                return List.of(new StaffPlayerIdentity(offlineId, "KnownPlayer", false));
            }
        };
        HistorySource source = new HistorySource(List.of(), 0);
        StaffHistoryCommandService service = new StaffHistoryCommandService(source, directory);
        PermissionSubject authorized = new PermissionSubject(
                new Actor("player", Optional.of(STAFF), "Staff"),
                Set.of(PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.PLAYER_VIEW));

        CommandResponse response = service.execute(authorized, List.of("history", "KnownPlayer"))
                .toCompletableFuture().join();
        CommandCompletionService completion = new CommandCompletionService(service);

        assertTrue(response.successful());
        assertEquals(offlineId, source.requestedPlayer.get());
        assertEquals(List.of("KnownPlayer"), completion.suggest(authorized,
                List.of("admin", "find", "K")));
        assertEquals(List.of("KnownPlayer"), completion.suggest(authorized,
                List.of("history", "K")));
    }

    private static StaffHistoryCommandService service(HistorySource source) {
        return new StaffHistoryCommandService(source,
                () -> List.of(new StaffPlayerIdentity(PLAYER, "tmydwc")));
    }

    private static StaffHistorySource.Entry entry(
            int suffix,
            long before,
            long after,
            StaffHistorySource.Outcome outcome) {
        UUID id = UUID.fromString("00000000-0000-4000-8000-00000000000" + suffix);
        return new StaffHistorySource.Entry(id, "tmydwc", before, after, outcome, true, true,
                Optional.empty(), Optional.empty(), Optional.of("$5"), Optional.of("$1"), NOW);
    }

    private static PermissionSubject staff() {
        return new PermissionSubject(new Actor("player", Optional.of(STAFF), "Staff"),
                Set.of(PhaseSixPermissions.PLAYER_VIEW));
    }

    private static PermissionSubject ordinary() {
        return new PermissionSubject(new Actor("player", Optional.of(STAFF), "Player"), Set.of());
    }

    private static final class HistorySource implements StaffHistorySource {
        private final List<Entry> entries;
        private final long total;
        private final AtomicReference<UUID> requestedPlayer = new AtomicReference<>();
        private final AtomicReference<UUID> requestedEntry = new AtomicReference<>();
        private final AtomicInteger requestedOffset = new AtomicInteger();
        private final AtomicInteger requestedLimit = new AtomicInteger();
        private final AtomicInteger reads = new AtomicInteger();
        private final AtomicInteger mutations = new AtomicInteger();

        private HistorySource(List<Entry> entries, long total) {
            this.entries = List.copyOf(entries);
            this.total = total;
        }

        @Override
        public CompletionStage<Page> recent(int offset, int limit) {
            throw new AssertionError("Command history must use the selected-player source");
        }

        @Override
        public CompletionStage<Page> forPlayer(UUID playerId, int offset, int limit) {
            requestedPlayer.set(playerId);
            requestedOffset.set(offset);
            requestedLimit.set(limit);
            reads.incrementAndGet();
            int last = Math.min(entries.size(), Math.max(0, offset) + limit);
            List<Entry> page = offset >= entries.size() ? List.of() : entries.subList(offset, last);
            return CompletableFuture.completedFuture(new Page(page, offset > 0, last < total, total));
        }

        @Override
        public CompletionStage<Optional<Entry>> entry(UUID playerId, UUID entryId) {
            requestedPlayer.set(playerId);
            requestedEntry.set(entryId);
            reads.incrementAndGet();
            return CompletableFuture.completedFuture(entries.stream()
                    .filter(entry -> entry.entryId().equals(entryId)).findFirst());
        }
    }
}
