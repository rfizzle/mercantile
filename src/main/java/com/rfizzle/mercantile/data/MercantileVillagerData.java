package com.rfizzle.mercantile.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class MercantileVillagerData {
    public static final Codec<MercantileVillagerData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.optionalFieldOf("professionLocked", false)
                            .forGetter(MercantileVillagerData::isProfessionLocked),
                    Codec.STRING.listOf()
                            .xmap(list -> (Set<String>) new HashSet<>(list),
                                  set -> set.stream().sorted().toList())
                            .optionalFieldOf("lockedTrades", Set.of())
                            .forGetter(MercantileVillagerData::getLockedTrades),
                    Codec.BOOL.optionalFieldOf("nameAssigned", false)
                            .forGetter(MercantileVillagerData::isNameAssigned),
                    CompoundTag.CODEC.optionalFieldOf("wanderingTraderOfferTag")
                            .forGetter(data -> Optional.ofNullable(data.wanderingTraderOfferTag)),
                    Codec.INT.optionalFieldOf("fedGrowthTicks", 0)
                            .forGetter(MercantileVillagerData::getFedGrowthTicks)
            ).apply(instance, (professionLocked, lockedTrades, nameAssigned, wanderingTraderOfferTag, fedGrowthTicks) -> {
                    MercantileVillagerData data = new MercantileVillagerData(
                            professionLocked, lockedTrades, nameAssigned, wanderingTraderOfferTag.orElse(null));
                    data.setFedGrowthTicks(fedGrowthTicks);
                    return data;
            })
    );

    private boolean professionLocked;
    private final Set<String> lockedTrades;
    private boolean nameAssigned;
    private CompoundTag wanderingTraderOfferTag;
    private int fedGrowthTicks;

    public MercantileVillagerData() {
        this(false, Set.of(), false, null);
    }

    public MercantileVillagerData(boolean professionLocked, Set<String> lockedTrades, boolean nameAssigned) {
        this(professionLocked, lockedTrades, nameAssigned, null);
    }

    public MercantileVillagerData(boolean professionLocked, Set<String> lockedTrades, boolean nameAssigned, CompoundTag wanderingTraderOfferTag) {
        this.professionLocked = professionLocked;
        this.lockedTrades = new HashSet<>(lockedTrades);
        this.nameAssigned = nameAssigned;
        this.wanderingTraderOfferTag = wanderingTraderOfferTag;
    }

    public boolean isProfessionLocked() {
        return professionLocked;
    }

    public void setProfessionLocked(boolean locked) {
        this.professionLocked = locked;
    }

    public Set<String> getLockedTrades() {
        return Collections.unmodifiableSet(lockedTrades);
    }

    public boolean addLockedTrade(String identityHash) {
        return lockedTrades.add(identityHash);
    }

    public boolean removeLockedTrade(String identityHash) {
        return lockedTrades.remove(identityHash);
    }

    public boolean isTradeLocked(String identityHash) {
        return lockedTrades.contains(identityHash);
    }

    public boolean isNameAssigned() {
        return nameAssigned;
    }

    public void setNameAssigned(boolean assigned) {
        this.nameAssigned = assigned;
    }

    public CompoundTag getWanderingTraderOfferTag() {
        return wanderingTraderOfferTag;
    }

    public void setWanderingTraderOfferTag(CompoundTag tag) {
        this.wanderingTraderOfferTag = tag;
    }

    /** Total growth-time ticks this baby has been accelerated by feeding (see BabyFeeding). */
    public int getFedGrowthTicks() {
        return fedGrowthTicks;
    }

    public void setFedGrowthTicks(int ticks) {
        this.fedGrowthTicks = Math.max(0, ticks);
    }
}
