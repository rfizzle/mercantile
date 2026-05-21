package com.rfizzle.mercantile.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rfizzle.mercantile.trade.OfferIdentityHash;
import net.minecraft.world.item.trading.MerchantOffer;

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
                            .forGetter(VillagerData::isHealBoosted),
                    Codec.BOOL.optionalFieldOf("tradesMigrated", false)
                            .forGetter(VillagerData::isTradesMigrated)
            ).apply(instance, VillagerData::new)
    );

    private boolean professionLocked;
    private final Set<String> lockedTrades;
    private boolean nameAssigned;
    private boolean healBoosted;
    private boolean tradesMigrated;

    public VillagerData() {
        this(false, Set.of(), false, false, false);
    }

    public VillagerData(boolean professionLocked, Set<String> lockedTrades, boolean nameAssigned, boolean healBoosted, boolean tradesMigrated) {
        this.professionLocked = professionLocked;
        this.lockedTrades = new HashSet<>(lockedTrades);
        this.nameAssigned = nameAssigned;
        this.healBoosted = healBoosted;
        this.tradesMigrated = tradesMigrated;
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

    /**
     * One-time migration: replaces legacy (no-count) hash format with the current format
     * for any offers whose old hash is still stored. Should be called before reading
     * lockedTrades whenever the current offer list is available.
     */
    public void migrateLockedTrades(Collection<MerchantOffer> currentOffers) {
        if (tradesMigrated) return;
        Set<String> toRemove = null;
        Set<String> toAdd = null;
        for (MerchantOffer offer : currentOffers) {
            String legacy = OfferIdentityHash.computeLegacy(offer);
            if (lockedTrades.contains(legacy)) {
                if (toRemove == null) {
                    toRemove = new HashSet<>();
                    toAdd = new HashSet<>();
                }
                toRemove.add(legacy);
                toAdd.add(OfferIdentityHash.compute(offer));
            }
        }
        if (toRemove != null) {
            lockedTrades.removeAll(toRemove);
            lockedTrades.addAll(toAdd);
        }
        tradesMigrated = true;
    }

    public boolean isTradesMigrated() {
        return tradesMigrated;
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
