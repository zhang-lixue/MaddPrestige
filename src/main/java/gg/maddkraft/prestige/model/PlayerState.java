package gg.maddkraft.prestige.model;

import java.time.Instant;
import java.util.UUID;

public final class PlayerState {
    private final UUID playerId;
    private String seasonId;
    private ProgressionRank rank;
    private int prestigeLevel;
    private int teaLeaves;
    private int legacyStars;
    private int lifetimePrestiges;
    private int homesPerkLevel;
    private int listingsPerkLevel;
    private int claimPerkLevel;
    private String selectedTitle;
    private Instant lastPrestigeAt;
    private Instant lastSeenAt;
    private Instant seasonJoinedAt;
    private final RunLedger ledger;
    private boolean dirty;

    public PlayerState(UUID playerId, String seasonId) {
        this(playerId, seasonId, ProgressionRank.CURIOUS, 0, 0, 0, 0, 0, 0, 0,
                "", Instant.EPOCH, Instant.now(), Instant.now(), new RunLedger());
    }

    public PlayerState(
            UUID playerId,
            String seasonId,
            ProgressionRank rank,
            int prestigeLevel,
            int teaLeaves,
            int legacyStars,
            int lifetimePrestiges,
            int homesPerkLevel,
            int listingsPerkLevel,
            int claimPerkLevel,
            String selectedTitle,
            Instant lastPrestigeAt,
            Instant lastSeenAt,
            Instant seasonJoinedAt,
            RunLedger ledger
    ) {
        this.playerId = playerId;
        this.seasonId = seasonId;
        this.rank = rank;
        this.prestigeLevel = Math.max(0, prestigeLevel);
        this.teaLeaves = Math.max(0, teaLeaves);
        this.legacyStars = Math.max(0, legacyStars);
        this.lifetimePrestiges = Math.max(0, lifetimePrestiges);
        this.homesPerkLevel = Math.max(0, homesPerkLevel);
        this.listingsPerkLevel = Math.max(0, listingsPerkLevel);
        this.claimPerkLevel = Math.max(0, claimPerkLevel);
        this.selectedTitle = selectedTitle == null ? "" : selectedTitle;
        this.lastPrestigeAt = lastPrestigeAt == null ? Instant.EPOCH : lastPrestigeAt;
        this.lastSeenAt = lastSeenAt == null ? Instant.now() : lastSeenAt;
        this.seasonJoinedAt = seasonJoinedAt == null ? Instant.now() : seasonJoinedAt;
        this.ledger = ledger == null ? new RunLedger() : ledger;
    }

    public synchronized PlayerState snapshot() {
        PlayerState copy = new PlayerState(playerId, seasonId, rank, prestigeLevel, teaLeaves, legacyStars,
                lifetimePrestiges, homesPerkLevel, listingsPerkLevel, claimPerkLevel, selectedTitle,
                lastPrestigeAt, lastSeenAt, seasonJoinedAt, ledger.snapshot());
        copy.dirty = dirty;
        return copy;
    }

    public synchronized void restoreFrom(PlayerState snapshot) {
        if (!playerId.equals(snapshot.playerId())) throw new IllegalArgumentException("snapshot belongs to another player");
        seasonId = snapshot.seasonId();
        rank = snapshot.rank();
        prestigeLevel = snapshot.prestigeLevel();
        teaLeaves = snapshot.teaLeaves();
        legacyStars = snapshot.legacyStars();
        lifetimePrestiges = snapshot.lifetimePrestiges();
        homesPerkLevel = snapshot.homesPerkLevel();
        listingsPerkLevel = snapshot.listingsPerkLevel();
        claimPerkLevel = snapshot.claimPerkLevel();
        selectedTitle = snapshot.selectedTitle();
        lastPrestigeAt = snapshot.lastPrestigeAt();
        lastSeenAt = snapshot.lastSeenAt();
        seasonJoinedAt = snapshot.seasonJoinedAt();
        RunLedger source = snapshot.ledger().snapshot();
        ledger.reset();
        ledger.addServerEarnings(source.serverEarnings());
        ledger.addMcMmoXp(source.mcMmoXp());
        ledger.addRabbitHoles(source.rabbitHoles());
        ledger.addDecreeObjectives(source.decreeObjectives());
        ledger.addBosses(source.bosses());
        dirty = true;
    }

    public synchronized void advanceRank(ProgressionRank newRank) {
        if (newRank.index() != rank.index() + 1) throw new IllegalArgumentException("rank must advance by one");
        rank = newRank;
        dirty = true;
    }

    public synchronized void setRank(ProgressionRank newRank) {
        rank = newRank;
        dirty = true;
    }

    public synchronized int completePrestige(int teaLeafReward, Instant when) {
        prestigeLevel++;
        lifetimePrestiges++;
        teaLeaves += Math.max(0, teaLeafReward);
        rank = ProgressionRank.CURIOUS;
        lastPrestigeAt = when;
        ledger.reset();
        dirty = true;
        return prestigeLevel;
    }

    public synchronized void spendTeaLeaves(int cost) {
        if (cost < 0 || teaLeaves < cost) throw new IllegalArgumentException("not enough Tea Leaves");
        teaLeaves -= cost;
        dirty = true;
    }

    public synchronized void addTeaLeaves(int amount) {
        teaLeaves = Math.max(0, teaLeaves + amount);
        dirty = true;
    }

    public synchronized void setPrestigeLevel(int level) {
        prestigeLevel = Math.max(0, level);
        dirty = true;
    }

    public synchronized void addLegacyStars(int amount) {
        legacyStars = Math.max(0, legacyStars + amount);
        dirty = true;
    }

    public synchronized void buyPerk(String perk) {
        switch (perk.toLowerCase()) {
            case "homes" -> homesPerkLevel++;
            case "auction-listings", "listings" -> listingsPerkLevel++;
            case "claim-blocks", "claims" -> claimPerkLevel++;
            default -> throw new IllegalArgumentException("unknown perk " + perk);
        }
        dirty = true;
    }

    public synchronized void beginSeason(String newSeasonId) {
        seasonId = newSeasonId;
        rank = ProgressionRank.CURIOUS;
        prestigeLevel = 0;
        teaLeaves = 0;
        homesPerkLevel = 0;
        listingsPerkLevel = 0;
        // Claim-block purchases are lifetime rewards because GriefPrevention stores bonus blocks permanently.
        lastPrestigeAt = Instant.EPOCH;
        seasonJoinedAt = Instant.now();
        ledger.reset();
        dirty = true;
    }

    public synchronized void touch(Instant when) {
        lastSeenAt = when;
        dirty = true;
    }

    public synchronized void markDirty() {
        dirty = true;
    }

    public synchronized boolean consumeDirty() {
        boolean wasDirty = dirty;
        dirty = false;
        return wasDirty;
    }

    public UUID playerId() { return playerId; }
    public synchronized String seasonId() { return seasonId; }
    public synchronized ProgressionRank rank() { return rank; }
    public synchronized int prestigeLevel() { return prestigeLevel; }
    public synchronized int teaLeaves() { return teaLeaves; }
    public synchronized int legacyStars() { return legacyStars; }
    public synchronized int lifetimePrestiges() { return lifetimePrestiges; }
    public synchronized int homesPerkLevel() { return homesPerkLevel; }
    public synchronized int listingsPerkLevel() { return listingsPerkLevel; }
    public synchronized int claimPerkLevel() { return claimPerkLevel; }
    public synchronized String selectedTitle() { return selectedTitle; }
    public synchronized Instant lastPrestigeAt() { return lastPrestigeAt; }
    public synchronized Instant lastSeenAt() { return lastSeenAt; }
    public synchronized Instant seasonJoinedAt() { return seasonJoinedAt; }
    public RunLedger ledger() { return ledger; }
}
