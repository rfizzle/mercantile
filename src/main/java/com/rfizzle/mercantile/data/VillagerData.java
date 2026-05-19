package com.rfizzle.mercantile.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;

public class VillagerData {
    public static final Codec<VillagerData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.optionalFieldOf("professionLocked", false)
                            .forGetter(VillagerData::isProfessionLocked),
                    Codec.STRING.listOf()
                            .xmap(list -> (Set<String>) new HashSet<>(list), List::copyOf)
                            .optionalFieldOf("lockedTrades", Set.of())
                            .forGetter(VillagerData::getLockedTrades),
                    Codec.BOOL.optionalFieldOf("nameAssigned", false)
                            .forGetter(VillagerData::isNameAssigned),
                    Codec.BOOL.optionalFieldOf("healBoosted", false)
                            .forGetter(VillagerData::isHealBoosted)
            ).apply(instance, VillagerData::new)
    );

    private boolean professionLocked;
    private final Set<String> lockedTrades;
    private boolean nameAssigned;
    private boolean healBoosted;

    public VillagerData() {
        this(false, Set.of(), false, false);
    }

    public VillagerData(boolean professionLocked, Set<String> lockedTrades, boolean nameAssigned, boolean healBoosted) {
        this.professionLocked = professionLocked;
        this.lockedTrades = new HashSet<>(lockedTrades);
        this.nameAssigned = nameAssigned;
        this.healBoosted = healBoosted;
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

    public boolean isTradeLocked(String identityHash) {
        return lockedTrades.contains(identityHash);
    }

    public boolean isNameAssigned() {
        return nameAssigned;
    }

    public void setNameAssigned(boolean assigned) {
        this.nameAssigned = assigned;
    }

    public boolean isHealBoosted() {
        return healBoosted;
    }

    public void setHealBoosted(boolean boosted) {
        this.healBoosted = boosted;
    }
}
