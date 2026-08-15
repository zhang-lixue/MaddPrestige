package gg.maddkraft.prestige.storage;

import gg.maddkraft.prestige.model.ContestMetric;
import gg.maddkraft.prestige.model.HatterContest;
import gg.maddkraft.prestige.model.HatterHolder;
import gg.maddkraft.prestige.model.PlayerState;
import gg.maddkraft.prestige.model.ProgressionRank;
import gg.maddkraft.prestige.model.RunLedger;
import gg.maddkraft.prestige.model.Season;
import gg.maddkraft.prestige.model.SeasonStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class Database implements AutoCloseable {
    private static final int SCHEMA_VERSION = 4;

    private final Path databasePath;
    private final Logger logger;
    private Connection connection;

    public Database(Path databasePath, Logger logger) {
        this.databasePath = databasePath;
        this.logger = logger;
    }

    public synchronized void initialize() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("SQLite driver was not bundled", exception);
        }
        try {
            Files.createDirectories(databasePath.getParent());
        } catch (Exception exception) {
            throw new SQLException("Could not create plugin data directory", exception);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA synchronous=NORMAL");
        }
        createSchema();
    }

    private void createSchema() throws SQLException {
        String[] ddl = {
                """
                CREATE TABLE IF NOT EXISTS schema_info (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    version INTEGER NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS seasons (
                    id TEXT PRIMARY KEY,
                    number INTEGER NOT NULL UNIQUE,
                    display_name TEXT NOT NULL,
                    status TEXT NOT NULL,
                    starts_at INTEGER NOT NULL,
                    ends_at INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_lifetime (
                    uuid TEXT PRIMARY KEY,
                    legacy_stars INTEGER NOT NULL DEFAULT 0,
                    lifetime_prestiges INTEGER NOT NULL DEFAULT 0,
                    claim_perk_level INTEGER NOT NULL DEFAULT 0,
                    selected_title TEXT NOT NULL DEFAULT '',
                    last_seen_at INTEGER NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_season (
                    uuid TEXT NOT NULL,
                    season_id TEXT NOT NULL,
                    progression_rank TEXT NOT NULL,
                    prestige_level INTEGER NOT NULL DEFAULT 0,
                    tea_leaves INTEGER NOT NULL DEFAULT 0,
                    homes_perk_level INTEGER NOT NULL DEFAULT 0,
                    listings_perk_level INTEGER NOT NULL DEFAULT 0,
                    claim_perk_level INTEGER NOT NULL DEFAULT 0,
                    last_prestige_at INTEGER NOT NULL DEFAULT 0,
                    joined_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (uuid, season_id),
                    FOREIGN KEY (season_id) REFERENCES seasons(id)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS run_ledger (
                    uuid TEXT NOT NULL,
                    season_id TEXT NOT NULL,
                    server_earnings REAL NOT NULL DEFAULT 0,
                    mcmmo_xp INTEGER NOT NULL DEFAULT 0,
                    rabbit_holes INTEGER NOT NULL DEFAULT 0,
                    decree_objectives INTEGER NOT NULL DEFAULT 0,
                    bosses INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (uuid, season_id),
                    FOREIGN KEY (season_id) REFERENCES seasons(id)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS prestige_transactions (
                    id TEXT PRIMARY KEY,
                    uuid TEXT NOT NULL,
                    season_id TEXT NOT NULL,
                    target_prestige INTEGER NOT NULL,
                    state TEXT NOT NULL,
                    fee REAL NOT NULL,
                    snapshot TEXT NOT NULL,
                    error TEXT,
                    created_at INTEGER NOT NULL,
                    completed_at INTEGER
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS hatter_contests (
                    id TEXT PRIMARY KEY,
                    season_id TEXT NOT NULL,
                    metric TEXT NOT NULL,
                    starts_at INTEGER NOT NULL,
                    ends_at INTEGER NOT NULL,
                    minimum_prestige INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    winner_uuid TEXT,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY (season_id) REFERENCES seasons(id)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS hatter_scores (
                    contest_id TEXT NOT NULL,
                    uuid TEXT NOT NULL,
                    score REAL NOT NULL DEFAULT 0,
                    reached_at INTEGER NOT NULL,
                    PRIMARY KEY (contest_id, uuid),
                    FOREIGN KEY (contest_id) REFERENCES hatter_contests(id)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS hatter_holder (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    uuid TEXT,
                    contest_id TEXT,
                    hat_item_id TEXT,
                    since_at INTEGER
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS hatter_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL,
                    season_id TEXT NOT NULL,
                    contest_id TEXT,
                    started_at INTEGER NOT NULL,
                    ended_at INTEGER NOT NULL,
                    reason TEXT NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS pending_patron_grants (
                    username_key TEXT PRIMARY KEY,
                    supplied_name TEXT NOT NULL,
                    tier TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS admin_audit (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    actor TEXT NOT NULL,
                    action TEXT NOT NULL,
                    target TEXT,
                    details TEXT,
                    created_at INTEGER NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_preferences (
                    uuid TEXT PRIMARY KEY,
                    menu_sounds INTEGER NOT NULL DEFAULT 1,
                    compact_numbers INTEGER NOT NULL DEFAULT 0,
                    show_integration_status INTEGER NOT NULL DEFAULT 1,
                    default_page TEXT NOT NULL DEFAULT 'PRESTIGE',
                    updated_at INTEGER NOT NULL
                )
                """,
                "CREATE INDEX IF NOT EXISTS idx_player_season_prestige ON player_season(season_id, prestige_level DESC)",
                "CREATE INDEX IF NOT EXISTS idx_hatter_score ON hatter_scores(contest_id, score DESC, reached_at ASC)"
        };
        try (Statement statement = connection.createStatement()) {
            for (String sql : ddl) statement.execute(sql);
            statement.executeUpdate("INSERT INTO schema_info(id, version) VALUES(1, " + SCHEMA_VERSION + ") ON CONFLICT(id) DO UPDATE SET version=excluded.version");
            statement.executeUpdate("INSERT OR IGNORE INTO hatter_holder(id, uuid, contest_id, hat_item_id, since_at) VALUES(1, NULL, NULL, NULL, NULL)");
        }
        ensureColumn("player_lifetime", "claim_perk_level", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("player_season", "joined_at", "INTEGER NOT NULL DEFAULT 0");
    }

    private void ensureColumn(String table, String column, String definition) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, table, column)) {
            if (columns.next()) return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    public synchronized Season ensureActiveSeason(String id, String displayName, int lengthDays) throws SQLException {
        Optional<Season> existing = activeSeason();
        if (existing.isPresent()) return existing.get();
        Instant now = Instant.now();
        int nextNumber = 1;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(number), 0) + 1 FROM seasons")) {
            if (result.next()) nextNumber = result.getInt(1);
        }
        Season season = new Season(id, nextNumber, displayName, SeasonStatus.ACTIVE, now, now.plus(lengthDays, ChronoUnit.DAYS));
        insertSeason(season);
        return season;
    }

    public synchronized Optional<Season> activeSeason() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, number, display_name, status, starts_at, ends_at FROM seasons WHERE status != 'CLOSED' ORDER BY number DESC LIMIT 1");
             ResultSet result = statement.executeQuery()) {
            return result.next() ? Optional.of(readSeason(result)) : Optional.empty();
        }
    }

    public synchronized Optional<Season> season(String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, number, display_name, status, starts_at, ends_at FROM seasons WHERE id=?")) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readSeason(result)) : Optional.empty();
            }
        }
    }

    public synchronized void updateSeasonStatus(String id, SeasonStatus status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE seasons SET status=? WHERE id=?")) {
            statement.setString(1, status.name());
            statement.setString(2, id);
            if (statement.executeUpdate() != 1) throw new SQLException("Unknown season " + id);
        }
    }

    public synchronized Season createNewSeason(String id, String displayName, int lengthDays) throws SQLException {
        if (season(id).isPresent()) throw new SQLException("Season id already exists: " + id);
        int number;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(number), 0) + 1 FROM seasons")) {
            result.next();
            number = result.getInt(1);
        }
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement close = connection.prepareStatement("UPDATE seasons SET status='CLOSED' WHERE status != 'CLOSED'")) {
                close.executeUpdate();
            }
            Instant now = Instant.now();
            Season season = new Season(id, number, displayName, SeasonStatus.ACTIVE, now, now.plus(lengthDays, ChronoUnit.DAYS));
            insertSeason(season);
            connection.commit();
            return season;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void insertSeason(Season season) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO seasons(id, number, display_name, status, starts_at, ends_at, created_at) VALUES(?,?,?,?,?,?,?)")) {
            statement.setString(1, season.id());
            statement.setInt(2, season.number());
            statement.setString(3, season.displayName());
            statement.setString(4, season.status().name());
            statement.setLong(5, season.startsAt().toEpochMilli());
            statement.setLong(6, season.endsAt().toEpochMilli());
            statement.setLong(7, Instant.now().toEpochMilli());
            statement.executeUpdate();
        }
    }

    public synchronized PlayerState loadPlayer(UUID playerId, String seasonId) throws SQLException {
        ensurePlayerRows(playerId, seasonId);
        String sql = """
                SELECT ps.progression_rank, ps.prestige_level, ps.tea_leaves,
                       ps.homes_perk_level, ps.listings_perk_level, ps.claim_perk_level,
                       ps.last_prestige_at, ps.joined_at, pl.legacy_stars, pl.lifetime_prestiges,
                       pl.claim_perk_level AS lifetime_claim_perk_level,
                       pl.selected_title, pl.last_seen_at,
                       rl.server_earnings, rl.mcmmo_xp, rl.rabbit_holes,
                       rl.decree_objectives, rl.bosses
                FROM player_season ps
                JOIN player_lifetime pl ON pl.uuid = ps.uuid
                JOIN run_ledger rl ON rl.uuid = ps.uuid AND rl.season_id = ps.season_id
                WHERE ps.uuid=? AND ps.season_id=?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, seasonId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Player row missing after ensure");
                return new PlayerState(
                        playerId,
                        seasonId,
                        ProgressionRank.parse(result.getString("progression_rank")),
                        result.getInt("prestige_level"),
                        result.getInt("tea_leaves"),
                        result.getInt("legacy_stars"),
                        result.getInt("lifetime_prestiges"),
                        result.getInt("homes_perk_level"),
                        result.getInt("listings_perk_level"),
                        result.getInt("lifetime_claim_perk_level"),
                        result.getString("selected_title"),
                        Instant.ofEpochMilli(result.getLong("last_prestige_at")),
                        Instant.ofEpochMilli(result.getLong("last_seen_at")),
                        Instant.ofEpochMilli(result.getLong("joined_at")),
                        new RunLedger(
                                result.getDouble("server_earnings"),
                                result.getLong("mcmmo_xp"),
                                result.getInt("rabbit_holes"),
                                result.getInt("decree_objectives"),
                                result.getInt("bosses")
                        )
                );
            }
        }
    }

    private void ensurePlayerRows(UUID playerId, String seasonId) throws SQLException {
        long now = Instant.now().toEpochMilli();
        try (PreparedStatement lifetime = connection.prepareStatement(
                "INSERT OR IGNORE INTO player_lifetime(uuid, last_seen_at) VALUES(?,?)");
             PreparedStatement seasonal = connection.prepareStatement(
                     "INSERT OR IGNORE INTO player_season(uuid, season_id, progression_rank, joined_at, updated_at) VALUES(?,?,?,?,?)");
             PreparedStatement ledger = connection.prepareStatement(
                     "INSERT OR IGNORE INTO run_ledger(uuid, season_id, updated_at) VALUES(?,?,?)")) {
            lifetime.setString(1, playerId.toString());
            lifetime.setLong(2, now);
            lifetime.executeUpdate();
            seasonal.setString(1, playerId.toString());
            seasonal.setString(2, seasonId);
            seasonal.setString(3, ProgressionRank.CURIOUS.name());
            seasonal.setLong(4, now);
            seasonal.setLong(5, now);
            seasonal.executeUpdate();
            ledger.setString(1, playerId.toString());
            ledger.setString(2, seasonId);
            ledger.setLong(3, now);
            ledger.executeUpdate();
        }
    }

    public synchronized void savePlayer(PlayerState original) throws SQLException {
        PlayerState state = original.snapshot();
        RunLedger ledger = state.ledger().snapshot();
        long now = Instant.now().toEpochMilli();
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement lifetime = connection.prepareStatement("""
                    INSERT INTO player_lifetime(uuid, legacy_stars, lifetime_prestiges, claim_perk_level, selected_title, last_seen_at)
                    VALUES(?,?,?,?,?,?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        legacy_stars=excluded.legacy_stars,
                        lifetime_prestiges=excluded.lifetime_prestiges,
                        claim_perk_level=excluded.claim_perk_level,
                        selected_title=excluded.selected_title,
                        last_seen_at=excluded.last_seen_at
                    """)) {
                lifetime.setString(1, state.playerId().toString());
                lifetime.setInt(2, state.legacyStars());
                lifetime.setInt(3, state.lifetimePrestiges());
                lifetime.setInt(4, state.claimPerkLevel());
                lifetime.setString(5, state.selectedTitle());
                lifetime.setLong(6, state.lastSeenAt().toEpochMilli());
                lifetime.executeUpdate();
            }
            try (PreparedStatement seasonal = connection.prepareStatement("""
                    INSERT INTO player_season(uuid, season_id, progression_rank, prestige_level, tea_leaves,
                        homes_perk_level, listings_perk_level, claim_perk_level, last_prestige_at, joined_at, updated_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(uuid, season_id) DO UPDATE SET
                        progression_rank=excluded.progression_rank,
                        prestige_level=excluded.prestige_level,
                        tea_leaves=excluded.tea_leaves,
                        homes_perk_level=excluded.homes_perk_level,
                        listings_perk_level=excluded.listings_perk_level,
                        claim_perk_level=excluded.claim_perk_level,
                        last_prestige_at=excluded.last_prestige_at,
                        joined_at=excluded.joined_at,
                        updated_at=excluded.updated_at
                    """)) {
                seasonal.setString(1, state.playerId().toString());
                seasonal.setString(2, state.seasonId());
                seasonal.setString(3, state.rank().name());
                seasonal.setInt(4, state.prestigeLevel());
                seasonal.setInt(5, state.teaLeaves());
                seasonal.setInt(6, state.homesPerkLevel());
                seasonal.setInt(7, state.listingsPerkLevel());
                seasonal.setInt(8, state.claimPerkLevel());
                seasonal.setLong(9, state.lastPrestigeAt().toEpochMilli());
                seasonal.setLong(10, state.seasonJoinedAt().toEpochMilli());
                seasonal.setLong(11, now);
                seasonal.executeUpdate();
            }
            try (PreparedStatement run = connection.prepareStatement("""
                    INSERT INTO run_ledger(uuid, season_id, server_earnings, mcmmo_xp, rabbit_holes,
                        decree_objectives, bosses, updated_at)
                    VALUES(?,?,?,?,?,?,?,?)
                    ON CONFLICT(uuid, season_id) DO UPDATE SET
                        server_earnings=excluded.server_earnings,
                        mcmmo_xp=excluded.mcmmo_xp,
                        rabbit_holes=excluded.rabbit_holes,
                        decree_objectives=excluded.decree_objectives,
                        bosses=excluded.bosses,
                        updated_at=excluded.updated_at
                    """)) {
                run.setString(1, state.playerId().toString());
                run.setString(2, state.seasonId());
                run.setDouble(3, ledger.serverEarnings());
                run.setLong(4, ledger.mcMmoXp());
                run.setInt(5, ledger.rabbitHoles());
                run.setInt(6, ledger.decreeObjectives());
                run.setInt(7, ledger.bosses());
                run.setLong(8, now);
                run.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized void savePlayers(Collection<PlayerState> states) throws SQLException {
        for (PlayerState state : states) savePlayer(state);
    }

    public synchronized void convertLegacyStars(String seasonId, int every) throws SQLException {
        if (every <= 0) throw new IllegalArgumentException("every must be positive");
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT uuid, prestige_level FROM player_season WHERE season_id=?");
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE player_lifetime SET legacy_stars=legacy_stars+? WHERE uuid=?")) {
            query.setString(1, seasonId);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    int stars = result.getInt("prestige_level") / every;
                    if (stars <= 0) continue;
                    update.setInt(1, stars);
                    update.setString(2, result.getString("uuid"));
                    update.addBatch();
                }
            }
            update.executeBatch();
        }
    }

    public synchronized String beginPrestigeTransaction(PlayerState state, int targetPrestige, double fee) throws SQLException {
        String id = UUID.randomUUID().toString();
        String snapshot = "rank=" + state.rank() + ";prestige=" + state.prestigeLevel() + ";tea=" + state.teaLeaves();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO prestige_transactions(id, uuid, season_id, target_prestige, state, fee, snapshot, created_at)
                VALUES(?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, id);
            statement.setString(2, state.playerId().toString());
            statement.setString(3, state.seasonId());
            statement.setInt(4, targetPrestige);
            statement.setString(5, "PENDING");
            statement.setDouble(6, fee);
            statement.setString(7, snapshot);
            statement.setLong(8, Instant.now().toEpochMilli());
            statement.executeUpdate();
        }
        return id;
    }

    public synchronized void finishPrestigeTransaction(String id) throws SQLException {
        updatePrestigeTransaction(id, "COMPLETED", null);
    }

    public synchronized void failPrestigeTransaction(String id, String error) throws SQLException {
        updatePrestigeTransaction(id, "FAILED", error == null ? "unknown" : error.substring(0, Math.min(error.length(), 500)));
    }

    public synchronized List<PendingPrestigeTransaction> pendingPrestigeTransactions() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT id, uuid, season_id, target_prestige, fee, snapshot, created_at
                     FROM prestige_transactions WHERE state='PENDING' ORDER BY created_at ASC
                     """)) {
            List<PendingPrestigeTransaction> entries = new ArrayList<>();
            while (result.next()) {
                entries.add(new PendingPrestigeTransaction(
                        result.getString("id"),
                        UUID.fromString(result.getString("uuid")),
                        result.getString("season_id"),
                        result.getInt("target_prestige"),
                        result.getDouble("fee"),
                        result.getString("snapshot"),
                        Instant.ofEpochMilli(result.getLong("created_at"))
                ));
            }
            return entries;
        }
    }

    public synchronized void queuePatronGrant(String suppliedName, String tier) throws SQLException {
        String key = suppliedName.trim().toLowerCase();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO pending_patron_grants(username_key, supplied_name, tier, created_at) VALUES(?,?,?,?)
                ON CONFLICT(username_key) DO UPDATE SET supplied_name=excluded.supplied_name,
                    tier=excluded.tier, created_at=excluded.created_at
                """)) {
            statement.setString(1, key);
            statement.setString(2, suppliedName.trim());
            statement.setString(3, tier);
            statement.setLong(4, Instant.now().toEpochMilli());
            statement.executeUpdate();
        }
    }

    public synchronized Optional<PendingPatronGrant> pendingPatronGrant(String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT supplied_name, tier, created_at FROM pending_patron_grants WHERE username_key=?")) {
            statement.setString(1, username.trim().toLowerCase());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(new PendingPatronGrant(
                        result.getString("supplied_name"),
                        result.getString("tier"),
                        Instant.ofEpochMilli(result.getLong("created_at"))
                )) : Optional.empty();
            }
        }
    }

    public synchronized void deletePendingPatronGrant(String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM pending_patron_grants WHERE username_key=?")) {
            statement.setString(1, username.trim().toLowerCase());
            statement.executeUpdate();
        }
    }

    public synchronized void recordAdminAction(String actor, String action, String target, String details) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO admin_audit(actor, action, target, details, created_at) VALUES(?,?,?,?,?)")) {
            statement.setString(1, truncate(actor, 100));
            statement.setString(2, truncate(action, 100));
            statement.setString(3, truncate(target, 100));
            statement.setString(4, truncate(details, 500));
            statement.setLong(5, Instant.now().toEpochMilli());
            statement.executeUpdate();
        }
    }

    public synchronized PlayerPreferences loadPlayerPreferences(UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT menu_sounds, compact_numbers, show_integration_status, default_page
                FROM player_preferences WHERE uuid=?
                """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return PlayerPreferences.defaults();
                return new PlayerPreferences(
                        result.getInt("menu_sounds") != 0,
                        result.getInt("compact_numbers") != 0,
                        result.getInt("show_integration_status") != 0,
                        result.getString("default_page")
                );
            }
        }
    }

    public synchronized void savePlayerPreferences(UUID playerId, PlayerPreferences preferences) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_preferences(uuid, menu_sounds, compact_numbers, show_integration_status, default_page, updated_at)
                VALUES(?,?,?,?,?,?)
                ON CONFLICT(uuid) DO UPDATE SET
                    menu_sounds=excluded.menu_sounds,
                    compact_numbers=excluded.compact_numbers,
                    show_integration_status=excluded.show_integration_status,
                    default_page=excluded.default_page,
                    updated_at=excluded.updated_at
                """)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, preferences.menuSounds() ? 1 : 0);
            statement.setInt(3, preferences.compactNumbers() ? 1 : 0);
            statement.setInt(4, preferences.showIntegrationStatus() ? 1 : 0);
            statement.setString(5, truncate(preferences.defaultPage(), 32));
            statement.setLong(6, Instant.now().toEpochMilli());
            statement.executeUpdate();
        }
    }

    private String truncate(String value, int maximum) {
        if (value == null) return null;
        return value.substring(0, Math.min(value.length(), maximum));
    }

    private void updatePrestigeTransaction(String id, String state, String error) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE prestige_transactions SET state=?, error=?, completed_at=? WHERE id=?")) {
            statement.setString(1, state);
            statement.setString(2, error);
            statement.setLong(3, Instant.now().toEpochMilli());
            statement.setString(4, id);
            statement.executeUpdate();
        }
    }

    public synchronized List<PrestigeEntry> prestigeLeaderboard(String seasonId, int limit) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT uuid, prestige_level FROM player_season
                WHERE season_id=? ORDER BY prestige_level DESC, updated_at ASC LIMIT ?
                """)) {
            statement.setString(1, seasonId);
            statement.setInt(2, Math.max(1, Math.min(limit, 100)));
            try (ResultSet result = statement.executeQuery()) {
                List<PrestigeEntry> entries = new ArrayList<>();
                while (result.next()) entries.add(new PrestigeEntry(UUID.fromString(result.getString(1)), result.getInt(2)));
                return entries;
            }
        }
    }

    public synchronized HatterContest startContest(String seasonId, ContestMetric metric, Instant startsAt, Instant endsAt, int minimumPrestige) throws SQLException {
        if (!endsAt.isAfter(startsAt)) throw new IllegalArgumentException("contest end must be after start");
        if (activeContest(seasonId).isPresent()) throw new SQLException("An active contest already exists");
        HatterContest contest = new HatterContest(
                UUID.randomUUID().toString(), seasonId, metric, startsAt, endsAt,
                Math.max(0, minimumPrestige), "ACTIVE", null
        );
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO hatter_contests(id, season_id, metric, starts_at, ends_at, minimum_prestige, status, created_at)
                VALUES(?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, contest.id());
            statement.setString(2, seasonId);
            statement.setString(3, metric.name());
            statement.setLong(4, startsAt.toEpochMilli());
            statement.setLong(5, endsAt.toEpochMilli());
            statement.setInt(6, contest.minimumPrestige());
            statement.setString(7, contest.status());
            statement.setLong(8, Instant.now().toEpochMilli());
            statement.executeUpdate();
        }
        return contest;
    }

    public synchronized Optional<HatterContest> activeContest(String seasonId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, season_id, metric, starts_at, ends_at, minimum_prestige, status, winner_uuid
                FROM hatter_contests WHERE season_id=? AND status='ACTIVE' ORDER BY created_at DESC LIMIT 1
                """)) {
            statement.setString(1, seasonId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readContest(result)) : Optional.empty();
            }
        }
    }

    public synchronized void addContestScore(String contestId, UUID playerId, double amount) throws SQLException {
        if (!Double.isFinite(amount) || amount <= 0.0) return;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO hatter_scores(contest_id, uuid, score, reached_at) VALUES(?,?,?,?)
                ON CONFLICT(contest_id, uuid) DO UPDATE SET
                    score=hatter_scores.score+excluded.score,
                    reached_at=excluded.reached_at
                """)) {
            statement.setString(1, contestId);
            statement.setString(2, playerId.toString());
            statement.setDouble(3, amount);
            statement.setLong(4, Instant.now().toEpochMilli());
            statement.executeUpdate();
        }
    }

    public synchronized List<ScoreEntry> contestLeaderboard(String contestId, int limit) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT uuid, score, reached_at FROM hatter_scores
                WHERE contest_id=? ORDER BY score DESC, reached_at ASC LIMIT ?
                """)) {
            statement.setString(1, contestId);
            statement.setInt(2, Math.max(1, Math.min(limit, 100_000)));
            try (ResultSet result = statement.executeQuery()) {
                List<ScoreEntry> entries = new ArrayList<>();
                while (result.next()) {
                    entries.add(new ScoreEntry(
                            UUID.fromString(result.getString("uuid")),
                            result.getDouble("score"),
                            Instant.ofEpochMilli(result.getLong("reached_at"))
                    ));
                }
                return entries;
            }
        }
    }

    public synchronized void finishContest(String contestId, UUID winnerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE hatter_contests SET status='COMPLETED', winner_uuid=? WHERE id=? AND status='ACTIVE'")) {
            statement.setString(1, winnerId == null ? null : winnerId.toString());
            statement.setString(2, contestId);
            if (statement.executeUpdate() != 1) throw new SQLException("Contest is not active: " + contestId);
        }
    }

    public synchronized Optional<HatterHolder> hatterHolder() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT uuid, contest_id, hat_item_id, since_at FROM hatter_holder WHERE id=1")) {
            if (!result.next() || result.getString("uuid") == null) return Optional.empty();
            return Optional.of(new HatterHolder(
                    UUID.fromString(result.getString("uuid")),
                    result.getString("contest_id"),
                    result.getString("hat_item_id"),
                    Instant.ofEpochMilli(result.getLong("since_at"))
            ));
        }
    }

    public synchronized HatterHolder transferHatter(UUID playerId, String seasonId, String contestId, String reason) throws SQLException {
        Instant now = Instant.now();
        String itemId = UUID.randomUUID().toString();
        Optional<HatterHolder> previous = hatterHolder();
        try {
            connection.setAutoCommit(false);
            if (previous.isPresent()) {
                HatterHolder old = previous.get();
                try (PreparedStatement history = connection.prepareStatement("""
                        INSERT INTO hatter_history(uuid, season_id, contest_id, started_at, ended_at, reason)
                        VALUES(?,?,?,?,?,?)
                        """)) {
                    history.setString(1, old.playerId().toString());
                    history.setString(2, seasonId);
                    history.setString(3, old.contestId());
                    history.setLong(4, old.since().toEpochMilli());
                    history.setLong(5, now.toEpochMilli());
                    history.setString(6, reason);
                    history.executeUpdate();
                }
            }
            try (PreparedStatement holder = connection.prepareStatement("""
                    UPDATE hatter_holder SET uuid=?, contest_id=?, hat_item_id=?, since_at=? WHERE id=1
                    """)) {
                holder.setString(1, playerId.toString());
                holder.setString(2, contestId);
                holder.setString(3, itemId);
                holder.setLong(4, now.toEpochMilli());
                holder.executeUpdate();
            }
            connection.commit();
            return new HatterHolder(playerId, contestId, itemId, now);
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized void revokeHatter(String seasonId, String reason) throws SQLException {
        Optional<HatterHolder> previous = hatterHolder();
        if (previous.isEmpty()) return;
        HatterHolder old = previous.get();
        Instant now = Instant.now();
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement history = connection.prepareStatement("""
                    INSERT INTO hatter_history(uuid, season_id, contest_id, started_at, ended_at, reason)
                    VALUES(?,?,?,?,?,?)
                    """)) {
                history.setString(1, old.playerId().toString());
                history.setString(2, seasonId);
                history.setString(3, old.contestId());
                history.setLong(4, old.since().toEpochMilli());
                history.setLong(5, now.toEpochMilli());
                history.setString(6, reason);
                history.executeUpdate();
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE hatter_holder SET uuid=NULL, contest_id=NULL, hat_item_id=NULL, since_at=NULL WHERE id=1");
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized List<HatterHistoryEntry> hatterHistory(int limit) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT uuid, season_id, contest_id, started_at, ended_at, reason
                FROM hatter_history ORDER BY ended_at DESC LIMIT ?
                """)) {
            statement.setInt(1, Math.max(1, Math.min(limit, 100)));
            try (ResultSet result = statement.executeQuery()) {
                List<HatterHistoryEntry> entries = new ArrayList<>();
                while (result.next()) {
                    entries.add(new HatterHistoryEntry(
                            UUID.fromString(result.getString("uuid")),
                            result.getString("season_id"),
                            result.getString("contest_id"),
                            Instant.ofEpochMilli(result.getLong("started_at")),
                            Instant.ofEpochMilli(result.getLong("ended_at")),
                            result.getString("reason")
                    ));
                }
                return entries;
            }
        }
    }

    public synchronized Optional<Instant> lastSeen(UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT last_seen_at FROM player_lifetime WHERE uuid=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(Instant.ofEpochMilli(result.getLong(1))) : Optional.empty();
            }
        }
    }

    private Season readSeason(ResultSet result) throws SQLException {
        return new Season(
                result.getString("id"),
                result.getInt("number"),
                result.getString("display_name"),
                SeasonStatus.valueOf(result.getString("status")),
                Instant.ofEpochMilli(result.getLong("starts_at")),
                Instant.ofEpochMilli(result.getLong("ends_at"))
        );
    }

    private HatterContest readContest(ResultSet result) throws SQLException {
        String rawWinner = result.getString("winner_uuid");
        return new HatterContest(
                result.getString("id"),
                result.getString("season_id"),
                ContestMetric.valueOf(result.getString("metric")),
                Instant.ofEpochMilli(result.getLong("starts_at")),
                Instant.ofEpochMilli(result.getLong("ends_at")),
                result.getInt("minimum_prestige"),
                result.getString("status"),
                rawWinner == null ? null : UUID.fromString(rawWinner)
        );
    }

    @Override
    public synchronized void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            } catch (SQLException exception) {
                logger.warning("Could not checkpoint MaddPrestige database: " + exception.getMessage());
            }
            connection.close();
        }
    }

    public record PrestigeEntry(UUID playerId, int prestigeLevel) {}
    public record ScoreEntry(UUID playerId, double score, Instant reachedAt) {}
    public record HatterHistoryEntry(UUID playerId, String seasonId, String contestId, Instant startedAt, Instant endedAt, String reason) {}
    public record PendingPrestigeTransaction(String id, UUID playerId, String seasonId, int targetPrestige,
                                             double fee, String snapshot, Instant createdAt) {}
    public record PendingPatronGrant(String suppliedName, String tier, Instant createdAt) {}
    public record PlayerPreferences(boolean menuSounds, boolean compactNumbers,
                                    boolean showIntegrationStatus, String defaultPage) {
        public PlayerPreferences {
            defaultPage = defaultPage == null || defaultPage.isBlank() ? "PRESTIGE" : defaultPage;
        }

        public static PlayerPreferences defaults() {
            return new PlayerPreferences(true, false, true, "PRESTIGE");
        }
    }
}
