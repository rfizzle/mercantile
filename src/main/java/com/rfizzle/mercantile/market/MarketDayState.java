package com.rfizzle.mercantile.market;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-world market-day bookkeeping: the last calendar day whose start-of-market
 * announcement already fired, so a server restart mid-market-day doesn't re-announce.
 */
public class MarketDayState extends SavedData {

    private static final String STORAGE_KEY = "mercantile_market_day";
    private static final String TAG_LAST_ANNOUNCED_DAY = "lastAnnouncedDay";

    public static final SavedData.Factory<MarketDayState> FACTORY =
            new SavedData.Factory<>(MarketDayState::new, MarketDayState::load, null);

    private long lastAnnouncedDay = -1;

    public static MarketDayState getOrCreate(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, STORAGE_KEY);
    }

    public long getLastAnnouncedDay() {
        return lastAnnouncedDay;
    }

    public void setLastAnnouncedDay(long day) {
        if (day != lastAnnouncedDay) {
            lastAnnouncedDay = day;
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(TAG_LAST_ANNOUNCED_DAY, lastAnnouncedDay);
        return tag;
    }

    public static MarketDayState load(CompoundTag tag, HolderLookup.Provider registries) {
        MarketDayState state = new MarketDayState();
        if (tag.contains(TAG_LAST_ANNOUNCED_DAY)) {
            state.lastAnnouncedDay = tag.getLong(TAG_LAST_ANNOUNCED_DAY);
        }
        return state;
    }
}
