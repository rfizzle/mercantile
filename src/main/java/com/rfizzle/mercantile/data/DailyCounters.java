package com.rfizzle.mercantile.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The per-day reputation bookkeeping cluster, extracted from {@link PlayerData} to keep its
 * RecordCodecBuilder group under the 16-field ceiling. All fields reset together in
 * {@link PlayerData#resetDailyCounters(long)}. Saves written before the extraction decode to
 * defaults, which at worst resets one in-progress day of cap tracking.
 */
public class DailyCounters {

    public static final Codec<DailyCounters> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("reputationEarned", 0).forGetter(DailyCounters::getReputationEarned),
                    Codec.LONG.optionalFieldOf("lastCapResetDay", -1L).forGetter(DailyCounters::getLastCapResetDay),
                    Codec.INT.optionalFieldOf("tradeRep", 0).forGetter(DailyCounters::getTradeRep),
                    Codec.INT.optionalFieldOf("cycleRep", 0).forGetter(DailyCounters::getCycleRep),
                    Codec.INT.optionalFieldOf("giftRep", 0).forGetter(DailyCounters::getGiftRep),
                    Codec.INT.optionalFieldOf("tradesSinceLastRepGain", 0).forGetter(DailyCounters::getTradesSinceLastRepGain),
                    Codec.BOOL.optionalFieldOf("capNotified", false).forGetter(DailyCounters::isCapNotified),
                    Codec.INT.optionalFieldOf("gratitudeGifts", 0).forGetter(DailyCounters::getGratitudeGifts)
            ).apply(instance, DailyCounters::new)
    );

    private int reputationEarned;
    private long lastCapResetDay;
    private int tradeRep;
    private int cycleRep;
    private int giftRep;
    private int tradesSinceLastRepGain;
    private boolean capNotified;
    private int gratitudeGifts;

    public DailyCounters() {
        this(0, -1L, 0, 0, 0, 0, false, 0);
    }

    public DailyCounters(int reputationEarned, long lastCapResetDay, int tradeRep, int cycleRep, int giftRep,
                         int tradesSinceLastRepGain, boolean capNotified, int gratitudeGifts) {
        this.reputationEarned = Math.max(0, reputationEarned);
        this.lastCapResetDay = lastCapResetDay;
        this.tradeRep = Math.max(0, tradeRep);
        this.cycleRep = Math.max(0, cycleRep);
        this.giftRep = Math.max(0, giftRep);
        this.tradesSinceLastRepGain = Math.max(0, tradesSinceLastRepGain);
        this.capNotified = capNotified;
        this.gratitudeGifts = Math.max(0, gratitudeGifts);
    }

    public DailyCounters copy() {
        return new DailyCounters(reputationEarned, lastCapResetDay, tradeRep, cycleRep, giftRep,
                tradesSinceLastRepGain, capNotified, gratitudeGifts);
    }

    public void reset(long newDay) {
        this.lastCapResetDay = newDay;
        this.reputationEarned = 0;
        this.tradeRep = 0;
        this.cycleRep = 0;
        this.giftRep = 0;
        this.tradesSinceLastRepGain = 0;
        this.capNotified = false;
        this.gratitudeGifts = 0;
    }

    public int getReputationEarned() {
        return reputationEarned;
    }

    public long getLastCapResetDay() {
        return lastCapResetDay;
    }

    public int getTradeRep() {
        return tradeRep;
    }

    public int getCycleRep() {
        return cycleRep;
    }

    public int getGiftRep() {
        return giftRep;
    }

    public int getTradesSinceLastRepGain() {
        return tradesSinceLastRepGain;
    }

    public boolean isCapNotified() {
        return capNotified;
    }

    public void setCapNotified(boolean capNotified) {
        this.capNotified = capNotified;
    }

    public int getGratitudeGifts() {
        return gratitudeGifts;
    }

    public void incrementGratitudeGifts() {
        this.gratitudeGifts++;
    }

    public void addTradeRep(int amount) {
        this.tradeRep += amount;
        this.reputationEarned += amount;
    }

    public void addCycleRep(int amount) {
        this.cycleRep += amount;
        this.reputationEarned += amount;
    }

    public void addGiftRep(int amount) {
        this.giftRep += amount;
        this.reputationEarned += amount;
    }

    public void incrementTradesSinceLastRepGain() {
        this.tradesSinceLastRepGain++;
    }

    public void resetTradesSinceLastRepGain() {
        this.tradesSinceLastRepGain = 0;
    }
}
