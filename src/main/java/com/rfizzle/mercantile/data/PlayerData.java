package com.rfizzle.mercantile.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.*;

public class PlayerData {
    public static final int MIN_SCORE = -200;
    public static final int MAX_SCORE = 1500;
    /** Max tracked cured villagers per player; ~32 KB serialized UUID footprint at this limit. */
    public static final int MAX_CURED_VILLAGERS = 1024;
    /** Max tracked trade-stat entries per player; bounded to keep serialized footprint predictable. */
    public static final int MAX_TRADE_STATS = 1024;
    /** Max tracked fear-markup villages per player; bounded to keep serialized footprint predictable. */
    public static final int MAX_FEAR_VILLAGES = 64;
    /**
     * Hard bound on persisted pins per player (the player-facing cap is the smaller, configurable
     * {@code maxPinnedTradesPerPlayer}); ~16 KB serialized footprint at this limit.
     */
    public static final int MAX_PINNED_TRADES = 64;

    // NOTE: pre-2026-07 saves stored the daily counters as flat fields; they now live in the
    // DailyCounters sub-record ("dailyCounters"), so upgrading resets at most one in-progress
    // day of cap tracking. RecordCodecBuilder.group ceiling is 16 fields; currently at 10.
    public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("score", 0).forGetter(PlayerData::getScore),
                    Codec.INT.optionalFieldOf("proximityTicks", 0).forGetter(PlayerData::getProximityTicks),
                    Codec.LONG.optionalFieldOf("lastProximityDay", -1L).forGetter(PlayerData::getLastProximityDay),
                    UUIDUtil.CODEC.listOf()
                            .<Set<UUID>>xmap(list -> new LinkedHashSet<>(list), set -> new ArrayList<>(set))
                            .optionalFieldOf("curedVillagers", Set.of())
                            .forGetter(PlayerData::getCuredVillagers),
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT)
                            .optionalFieldOf("tradeStats", Map.of())
                            .forGetter(PlayerData::getTradeStats),
                    DailyCounters.CODEC.optionalFieldOf("dailyCounters", new DailyCounters())
                            .forGetter(PlayerData::getDailyCounters),
                    Codec.LONG.optionalFieldOf("lastDecayDay", -1L).forGetter(PlayerData::getLastDecayDay),
                    Codec.BOOL.optionalFieldOf("reputationMigrated", false).forGetter(PlayerData::isReputationMigrated),
                    Codec.unboundedMap(Codec.STRING, FearEntry.CODEC)
                            .optionalFieldOf("fearByVillage", Map.of())
                            .forGetter(PlayerData::getFearByVillage),
                    PinnedTrade.CODEC.listOf()
                            .optionalFieldOf("pinnedTrades", List.of())
                            .forGetter(PlayerData::getPinnedTrades)
            ).apply(instance, PlayerData::new)
    );

    private int score;
    private int proximityTicks;
    private long lastProximityDay;
    private final LinkedHashSet<UUID> curedVillagers;
    private final LinkedHashMap<UUID, Integer> tradeStats;
    private final DailyCounters dailyCounters;
    private long lastDecayDay;
    private boolean reputationMigrated;
    private final LinkedHashMap<String, FearEntry> fearByVillage;
    private final List<PinnedTrade> pinnedTrades;

    public PlayerData() {
        this(0, 0, -1L, Set.of(), Map.of(), new DailyCounters(), -1L, false, Map.of(), List.of());
    }

    public PlayerData(int score, int proximityTicks, long lastProximityDay, Set<UUID> curedVillagers, Map<UUID, Integer> tradeStats,
                      DailyCounters dailyCounters, long lastDecayDay, boolean reputationMigrated,
                      Map<String, FearEntry> fearByVillage, List<PinnedTrade> pinnedTrades) {
        this.score = Math.clamp(score, MIN_SCORE, MAX_SCORE);
        this.proximityTicks = proximityTicks;
        this.lastProximityDay = lastProximityDay;
        LinkedHashSet<UUID> cv = new LinkedHashSet<>(curedVillagers);
        Iterator<UUID> it = cv.iterator();
        while (cv.size() > MAX_CURED_VILLAGERS && it.hasNext()) {
            it.next();
            it.remove();
        }
        this.curedVillagers = cv;
        LinkedHashMap<UUID, Integer> ts = new LinkedHashMap<>(tradeStats);
        Iterator<UUID> tsIt = ts.keySet().iterator();
        while (ts.size() > MAX_TRADE_STATS && tsIt.hasNext()) {
            tsIt.next();
            tsIt.remove();
        }
        this.tradeStats = ts;
        // Copy: the codec's optionalFieldOf default is a single shared instance.
        this.dailyCounters = dailyCounters.copy();
        this.lastDecayDay = lastDecayDay;
        this.reputationMigrated = reputationMigrated;
        LinkedHashMap<String, FearEntry> fear = new LinkedHashMap<>(fearByVillage);
        while (fear.size() > MAX_FEAR_VILLAGES) {
            evictLeastRecentlyActiveFearEntry(fear);
        }
        this.fearByVillage = fear;
        ArrayList<PinnedTrade> pins = new ArrayList<>(pinnedTrades);
        // Oldest pins evict first; list order is insertion order and survives the round-trip.
        while (pins.size() > MAX_PINNED_TRADES) {
            pins.removeFirst();
        }
        this.pinnedTrades = pins;
    }

    // Eviction goes by entry activity, not map order: Codec.unboundedMap round-trips through
    // a hash-ordered map, so insertion order does not survive a reload — and evicting the
    // least-recently-active entry keeps a live markup from being pushed out by fresh blanks.
    private static void evictLeastRecentlyActiveFearEntry(LinkedHashMap<String, FearEntry> map) {
        String victim = null;
        long oldest = Long.MAX_VALUE;
        for (Map.Entry<String, FearEntry> entry : map.entrySet()) {
            long activity = entry.getValue().lastActivityGameTime();
            if (activity < oldest) {
                oldest = activity;
                victim = entry.getKey();
            }
        }
        if (victim != null) {
            map.remove(victim);
        }
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = Math.clamp(score, MIN_SCORE, MAX_SCORE);
    }

    public void addScore(int amount) {
        setScore(this.score + amount);
    }

    public int getProximityTicks() {
        return proximityTicks;
    }

    public void setProximityTicks(int proximityTicks) {
        this.proximityTicks = proximityTicks;
    }

    public long getLastProximityDay() {
        return lastProximityDay;
    }

    public void setLastProximityDay(long lastProximityDay) {
        this.lastProximityDay = lastProximityDay;
    }

    public Set<UUID> getCuredVillagers() {
        return Collections.unmodifiableSet(curedVillagers);
    }

    public boolean addCuredVillager(UUID villagerUuid) {
        if (curedVillagers.contains(villagerUuid)) return false;
        if (curedVillagers.size() >= MAX_CURED_VILLAGERS) {
            Iterator<UUID> it = curedVillagers.iterator();
            it.next();
            it.remove();
        }
        curedVillagers.add(villagerUuid);
        return true;
    }

    public boolean hasCuredVillager(UUID villagerUuid) {
        return curedVillagers.contains(villagerUuid);
    }

    public Map<UUID, Integer> getTradeStats() {
        return Collections.unmodifiableMap(tradeStats);
    }

    public int getTradesWithVillager(UUID villagerUuid) {
        return tradeStats.getOrDefault(villagerUuid, 0);
    }

    public void incrementTradesWithVillager(UUID villagerUuid) {
        Integer existing = tradeStats.remove(villagerUuid);
        if (existing == null && tradeStats.size() >= MAX_TRADE_STATS) {
            Iterator<UUID> it = tradeStats.keySet().iterator();
            it.next();
            it.remove();
        }
        int newVal = (existing == null ? 0 : existing) + 1;
        tradeStats.put(villagerUuid, newVal);
    }

    public Map<String, FearEntry> getFearByVillage() {
        return Collections.unmodifiableMap(fearByVillage);
    }

    public boolean hasFearEntries() {
        return !fearByVillage.isEmpty();
    }

    public FearEntry getFearEntry(String villageKey) {
        return fearByVillage.get(villageKey);
    }

    public FearEntry getOrCreateFearEntry(String villageKey) {
        FearEntry existing = fearByVillage.get(villageKey);
        if (existing != null) return existing;
        if (fearByVillage.size() >= MAX_FEAR_VILLAGES) {
            evictLeastRecentlyActiveFearEntry(fearByVillage);
        }
        FearEntry created = new FearEntry();
        fearByVillage.put(villageKey, created);
        return created;
    }

    public void removeFearEntry(String villageKey) {
        fearByVillage.remove(villageKey);
    }

    public DailyCounters getDailyCounters() {
        return dailyCounters;
    }

    public int getDailyReputationEarned() {
        return dailyCounters.getReputationEarned();
    }

    public long getLastCapResetDay() {
        return dailyCounters.getLastCapResetDay();
    }

    public int getDailyTradeRep() {
        return dailyCounters.getTradeRep();
    }

    public int getDailyCycleRep() {
        return dailyCounters.getCycleRep();
    }

    public int getDailyGiftRep() {
        return dailyCounters.getGiftRep();
    }

    public long getLastDecayDay() {
        return lastDecayDay;
    }

    public void setLastDecayDay(long lastDecayDay) {
        this.lastDecayDay = lastDecayDay;
    }

    public int getTradesSinceLastRepGain() {
        return dailyCounters.getTradesSinceLastRepGain();
    }

    public void resetDailyCounters(long newDay) {
        dailyCounters.reset(newDay);
    }

    public int getDailyGratitudeGifts() {
        return dailyCounters.getGratitudeGifts();
    }

    public void incrementDailyGratitudeGifts() {
        dailyCounters.incrementGratitudeGifts();
    }

    public void addDailyTradeRep(int amount) {
        dailyCounters.addTradeRep(amount);
    }

    public void addDailyCycleRep(int amount) {
        dailyCounters.addCycleRep(amount);
    }

    public void addDailyGiftRep(int amount) {
        dailyCounters.addGiftRep(amount);
    }

    public void incrementTradesSinceLastRepGain() {
        dailyCounters.incrementTradesSinceLastRepGain();
    }

    public void resetTradesSinceLastRepGain() {
        dailyCounters.resetTradesSinceLastRepGain();
    }

    public List<PinnedTrade> getPinnedTrades() {
        return Collections.unmodifiableList(pinnedTrades);
    }

    public boolean isTradePinned(UUID villagerUuid, String offerHash) {
        for (PinnedTrade pin : pinnedTrades) {
            if (pin.matches(villagerUuid, offerHash)) return true;
        }
        return false;
    }

    /**
     * Adds a pin unless it already exists or the hard bound is reached. The configurable
     * player-facing cap is enforced by the caller before this; the bound here only guards
     * the serialized footprint.
     */
    public boolean addPinnedTrade(PinnedTrade pin) {
        if (isTradePinned(pin.villagerUuid(), pin.offerHash())) return false;
        if (pinnedTrades.size() >= MAX_PINNED_TRADES) return false;
        pinnedTrades.add(pin);
        return true;
    }

    public boolean removePinnedTrade(UUID villagerUuid, String offerHash) {
        return pinnedTrades.removeIf(pin -> pin.matches(villagerUuid, offerHash));
    }

    /** Removes all pins targeting the given villager (death/pruning). Returns the removed count. */
    public int removePinnedTradesFor(UUID villagerUuid) {
        int before = pinnedTrades.size();
        pinnedTrades.removeIf(pin -> pin.villagerUuid().equals(villagerUuid));
        return before - pinnedTrades.size();
    }

    public int clearPinnedTrades() {
        int cleared = pinnedTrades.size();
        pinnedTrades.clear();
        return cleared;
    }

    public boolean isReputationMigrated() {
        return reputationMigrated;
    }

    /**
     * Tests and the migration helper only. Flipping this in production code without performing
     * the 10× score scaling silently skips the one-shot S-040 migration on legacy saves —
     * production callers should go through
     * {@link com.rfizzle.mercantile.reputation.ReputationManager#migrateIfNeeded(PlayerData)}.
     */
    @VisibleForTesting
    public void setReputationMigrated(boolean reputationMigrated) {
        this.reputationMigrated = reputationMigrated;
    }

    public boolean isDailyCapNotified() {
        return dailyCounters.isCapNotified();
    }

    public void setDailyCapNotified(boolean dailyCapNotified) {
        dailyCounters.setCapNotified(dailyCapNotified);
    }

    /**
     * Test-only fluent builder. The positional constructor stays as the codec's
     * apply target, but tests should use this builder so an int swap (e.g. dailyTradeRep
     * vs dailyCycleRep) cannot pass the compiler silently.
     */
    @VisibleForTesting
    public static Builder builder() {
        return new Builder();
    }

    @VisibleForTesting
    public static final class Builder {
        private int score = 0;
        private int proximityTicks = 0;
        private long lastProximityDay = -1L;
        private Set<UUID> curedVillagers = Set.of();
        private Map<UUID, Integer> tradeStats = Map.of();
        private int dailyReputationEarned = 0;
        private long lastCapResetDay = -1L;
        private int dailyTradeRep = 0;
        private int dailyCycleRep = 0;
        private int dailyGiftRep = 0;
        private long lastDecayDay = -1L;
        private int tradesSinceLastRepGain = 0;
        private boolean reputationMigrated = false;
        private boolean dailyCapNotified = false;
        private int dailyGratitudeGifts = 0;
        private Map<String, FearEntry> fearByVillage = Map.of();
        private List<PinnedTrade> pinnedTrades = List.of();

        private Builder() {
        }

        public Builder score(int v) { this.score = v; return this; }
        public Builder proximityTicks(int v) { this.proximityTicks = v; return this; }
        public Builder lastProximityDay(long v) { this.lastProximityDay = v; return this; }
        public Builder curedVillagers(Set<UUID> v) { this.curedVillagers = v; return this; }
        public Builder tradeStats(Map<UUID, Integer> v) { this.tradeStats = v; return this; }
        public Builder dailyReputationEarned(int v) { this.dailyReputationEarned = v; return this; }
        public Builder lastCapResetDay(long v) { this.lastCapResetDay = v; return this; }
        public Builder dailyTradeRep(int v) { this.dailyTradeRep = v; return this; }
        public Builder dailyCycleRep(int v) { this.dailyCycleRep = v; return this; }
        public Builder dailyGiftRep(int v) { this.dailyGiftRep = v; return this; }
        public Builder lastDecayDay(long v) { this.lastDecayDay = v; return this; }
        public Builder tradesSinceLastRepGain(int v) { this.tradesSinceLastRepGain = v; return this; }
        public Builder reputationMigrated(boolean v) { this.reputationMigrated = v; return this; }
        public Builder dailyCapNotified(boolean v) { this.dailyCapNotified = v; return this; }
        public Builder dailyGratitudeGifts(int v) { this.dailyGratitudeGifts = v; return this; }
        public Builder fearByVillage(Map<String, FearEntry> v) { this.fearByVillage = v; return this; }
        public Builder pinnedTrades(List<PinnedTrade> v) { this.pinnedTrades = v; return this; }

        public PlayerData build() {
            DailyCounters daily = new DailyCounters(dailyReputationEarned, lastCapResetDay, dailyTradeRep,
                    dailyCycleRep, dailyGiftRep, tradesSinceLastRepGain, dailyCapNotified, dailyGratitudeGifts);
            return new PlayerData(score, proximityTicks, lastProximityDay, curedVillagers, tradeStats,
                    daily, lastDecayDay, reputationMigrated, fearByVillage, pinnedTrades);
        }
    }
}
