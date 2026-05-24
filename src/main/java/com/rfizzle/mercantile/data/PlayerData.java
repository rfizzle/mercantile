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
                    Codec.INT.optionalFieldOf("dailyReputationEarned", 0).forGetter(PlayerData::getDailyReputationEarned),
                    Codec.LONG.optionalFieldOf("lastCapResetDay", -1L).forGetter(PlayerData::getLastCapResetDay),
                    Codec.INT.optionalFieldOf("dailyTradeRep", 0).forGetter(PlayerData::getDailyTradeRep),
                    Codec.INT.optionalFieldOf("dailyCycleRep", 0).forGetter(PlayerData::getDailyCycleRep),
                    Codec.INT.optionalFieldOf("tradesSinceLastRepGain", 0).forGetter(PlayerData::getTradesSinceLastRepGain),
                    Codec.BOOL.optionalFieldOf("reputationMigrated", false).forGetter(PlayerData::isReputationMigrated),
                    Codec.BOOL.optionalFieldOf("dailyCapNotified", false).forGetter(PlayerData::isDailyCapNotified)
            ).apply(instance, PlayerData::new)
    );

    private int score;
    private int proximityTicks;
    private long lastProximityDay;
    private final LinkedHashSet<UUID> curedVillagers;
    private final LinkedHashMap<UUID, Integer> tradeStats;
    private int dailyReputationEarned;
    private long lastCapResetDay;
    private int dailyTradeRep;
    private int dailyCycleRep;
    private int tradesSinceLastRepGain;
    private boolean reputationMigrated;
    private boolean dailyCapNotified;

    public PlayerData() {
        this(0, 0, -1L, Set.of(), Map.of(), 0, -1L, 0, 0, 0, false, false);
    }

    public PlayerData(int score, int proximityTicks, long lastProximityDay, Set<UUID> curedVillagers, Map<UUID, Integer> tradeStats,
                      int dailyReputationEarned, long lastCapResetDay, int dailyTradeRep, int dailyCycleRep, int tradesSinceLastRepGain,
                      boolean reputationMigrated, boolean dailyCapNotified) {
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
        this.dailyReputationEarned = Math.max(0, dailyReputationEarned);
        this.lastCapResetDay = lastCapResetDay;
        this.dailyTradeRep = Math.max(0, dailyTradeRep);
        this.dailyCycleRep = Math.max(0, dailyCycleRep);
        this.tradesSinceLastRepGain = Math.max(0, tradesSinceLastRepGain);
        this.reputationMigrated = reputationMigrated;
        this.dailyCapNotified = dailyCapNotified;
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

    public int getDailyReputationEarned() {
        return dailyReputationEarned;
    }

    public long getLastCapResetDay() {
        return lastCapResetDay;
    }

    public int getDailyTradeRep() {
        return dailyTradeRep;
    }

    public int getDailyCycleRep() {
        return dailyCycleRep;
    }

    public int getTradesSinceLastRepGain() {
        return tradesSinceLastRepGain;
    }

    public void resetDailyCounters(long newDay) {
        this.lastCapResetDay = newDay;
        this.dailyReputationEarned = 0;
        this.dailyTradeRep = 0;
        this.dailyCycleRep = 0;
        this.tradesSinceLastRepGain = 0;
        this.dailyCapNotified = false;
    }

    public void addDailyTradeRep(int amount) {
        this.dailyTradeRep += amount;
        this.dailyReputationEarned += amount;
    }

    public void addDailyCycleRep(int amount) {
        this.dailyCycleRep += amount;
        this.dailyReputationEarned += amount;
    }

    public void incrementTradesSinceLastRepGain() {
        this.tradesSinceLastRepGain++;
    }

    public void resetTradesSinceLastRepGain() {
        this.tradesSinceLastRepGain = 0;
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
        return dailyCapNotified;
    }

    public void setDailyCapNotified(boolean dailyCapNotified) {
        this.dailyCapNotified = dailyCapNotified;
    }

    /**
     * Test-only fluent builder. The 12-arg positional constructor stays as the codec's
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
        private int tradesSinceLastRepGain = 0;
        private boolean reputationMigrated = false;
        private boolean dailyCapNotified = false;

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
        public Builder tradesSinceLastRepGain(int v) { this.tradesSinceLastRepGain = v; return this; }
        public Builder reputationMigrated(boolean v) { this.reputationMigrated = v; return this; }
        public Builder dailyCapNotified(boolean v) { this.dailyCapNotified = v; return this; }

        public PlayerData build() {
            return new PlayerData(score, proximityTicks, lastProximityDay, curedVillagers, tradeStats,
                    dailyReputationEarned, lastCapResetDay, dailyTradeRep, dailyCycleRep, tradesSinceLastRepGain,
                    reputationMigrated, dailyCapNotified);
        }
    }
}
