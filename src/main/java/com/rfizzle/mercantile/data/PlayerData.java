package com.rfizzle.mercantile.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.*;

public class PlayerData {
    public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("score", 0).forGetter(PlayerData::getScore),
                    Codec.INT.optionalFieldOf("proximityTicks", 0).forGetter(PlayerData::getProximityTicks),
                    UUIDUtil.CODEC_SET.optionalFieldOf("curedVillagers", Set.of()).forGetter(PlayerData::getCuredVillagers),
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT)
                            .optionalFieldOf("tradeStats", Map.of())
                            .forGetter(PlayerData::getTradeStats)
            ).apply(instance, PlayerData::new)
    );

    private int score;
    private int proximityTicks;
    private final Set<UUID> curedVillagers;
    private final Map<UUID, Integer> tradeStats;

    public PlayerData() {
        this(0, 0, Set.of(), Map.of());
    }

    public PlayerData(int score, int proximityTicks, Set<UUID> curedVillagers, Map<UUID, Integer> tradeStats) {
        this.score = score;
        this.proximityTicks = proximityTicks;
        this.curedVillagers = new HashSet<>(curedVillagers);
        this.tradeStats = new HashMap<>(tradeStats);
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getProximityTicks() {
        return proximityTicks;
    }

    public void setProximityTicks(int proximityTicks) {
        this.proximityTicks = proximityTicks;
    }

    public Set<UUID> getCuredVillagers() {
        return Collections.unmodifiableSet(curedVillagers);
    }

    public boolean addCuredVillager(UUID villagerUuid) {
        return curedVillagers.add(villagerUuid);
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
        tradeStats.merge(villagerUuid, 1, Integer::sum);
    }
}
