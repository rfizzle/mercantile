package com.rfizzle.mercantile.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rfizzle.mercantile.trade.OfferIdentityHash;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.trading.MerchantOffer;

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
                    Codec.BOOL.optionalFieldOf("tradesMigrated", false)
                            .forGetter(MercantileVillagerData::isTradesMigrated),
                    CompoundTag.CODEC.optionalFieldOf("wanderingTraderOfferTag")
                            .forGetter(data -> Optional.ofNullable(data.wanderingTraderOfferTag))
            ).apply(instance, (professionLocked, lockedTrades, nameAssigned, tradesMigrated, wanderingTraderOfferTag) ->
                    new MercantileVillagerData(professionLocked, lockedTrades, nameAssigned, tradesMigrated, wanderingTraderOfferTag.orElse(null)))
    );

    private boolean professionLocked;
    private final Set<String> lockedTrades;
    private boolean nameAssigned;
    private boolean tradesMigrated;
    private CompoundTag wanderingTraderOfferTag;

    public MercantileVillagerData() {
        this(false, Set.of(), false, false, null);
    }

    public MercantileVillagerData(boolean professionLocked, Set<String> lockedTrades, boolean nameAssigned, boolean tradesMigrated) {
        this(professionLocked, lockedTrades, nameAssigned, tradesMigrated, null);
    }

    public MercantileVillagerData(boolean professionLocked, Set<String> lockedTrades, boolean nameAssigned, boolean tradesMigrated, CompoundTag wanderingTraderOfferTag) {
        this.professionLocked = professionLocked;
        this.lockedTrades = new HashSet<>(lockedTrades);
        this.nameAssigned = nameAssigned;
        this.tradesMigrated = tradesMigrated;
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

    /**
     * One-time migration: replaces legacy (no-count) hash format with the current format
     * for any offers whose old hash is still stored. Should be called before reading
     * lockedTrades whenever the current offer list is available.
     */
    public void migrateLockedTrades(Collection<MerchantOffer> currentOffers) {
        if (tradesMigrated) return;
        // Don't burn the one-shot flag without a real chance to migrate (B-080)
        if (currentOffers.isEmpty()) return;
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

    public CompoundTag getWanderingTraderOfferTag() {
        return wanderingTraderOfferTag;
    }

    public void setWanderingTraderOfferTag(CompoundTag tag) {
        this.wanderingTraderOfferTag = tag;
    }
}
