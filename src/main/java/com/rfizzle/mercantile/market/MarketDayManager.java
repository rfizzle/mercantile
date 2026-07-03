package com.rfizzle.mercantile.market;

import com.rfizzle.mercantile.config.MercantileConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

/**
 * Recurring village market day: every {@code marketDayIntervalDays} calendar days, from dawn
 * to dusk, all villagers grant a global trade discount and one extra restock cycle. The
 * schedule is shared world-wide and derived purely from the overworld day time, so it costs
 * one arithmetic check wherever it's read; only the start-of-day announcement carries state
 * (see {@link MarketDayState}).
 */
public final class MarketDayManager {

    /** Villagers within this range of a player receive announcement particles. */
    private static final double CELEBRATION_RANGE = 48.0;
    private static final float BELL_VOLUME = 2.0f;

    private MarketDayManager() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(MarketDayManager::tick);
    }

    /** Whether market day is currently in effect in this level. */
    public static boolean isActive(ServerLevel level) {
        MercantileConfig config = MercantileConfig.get();
        return config.enableMarketDay
                && MarketDayMath.isMarketDay(level.getDayTime(), config.marketDayIntervalDays);
    }

    /** Market-day price adjustment for one offer of this villager; 0 outside market day. */
    public static int priceModifier(Villager villager, int basePrice, MercantileConfig config) {
        if (!config.enableMarketDay) return 0;
        if (!(villager.level() instanceof ServerLevel level)) return 0;
        if (!MarketDayMath.isMarketDay(level.getDayTime(), config.marketDayIntervalDays)) return 0;
        return MarketDayMath.discount(basePrice, config.marketDayDiscountPercent);
    }

    /** The daily restock cap for this villager — one extra cycle during market day. */
    public static int maxRestocksToday(Villager villager) {
        if (villager.level() instanceof ServerLevel level && isActive(level)) {
            return MarketDayMath.MARKET_DAY_MAX_RESTOCKS_PER_DAY;
        }
        return MarketDayMath.VANILLA_MAX_RESTOCKS_PER_DAY;
    }

    private static void tick(MinecraftServer server) {
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableMarketDay) return;

        long dayTime = server.overworld().getDayTime();
        if (!MarketDayMath.isMarketDay(dayTime, config.marketDayIntervalDays)) return;

        // Don't consume the announcement on an empty server — the first player to be
        // online during the market-day window still gets it.
        if (server.getPlayerList().getPlayers().isEmpty()) return;

        long day = MarketDayMath.dayOf(dayTime);
        MarketDayState state = MarketDayState.getOrCreate(server);
        if (state.getLastAnnouncedDay() == day) return;
        state.setLastAnnouncedDay(day);

        announce(server);
    }

    // Dawn of a market day: action-bar note and a bell ring for every player, plus
    // happy-villager particles over the villagers around them.
    private static void announce(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.displayClientMessage(
                    Component.translatable("mercantile.message.market_day")
                            .withStyle(ChatFormatting.GOLD), true);

            ServerLevel level = player.serverLevel();
            level.playSound(null, player.blockPosition(),
                    SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, BELL_VOLUME, 1.0f);

            AABB range = player.getBoundingBox().inflate(CELEBRATION_RANGE);
            for (Villager villager : level.getEntitiesOfClass(Villager.class, range, LivingEntity::isAlive)) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        villager.getX(), villager.getEyeY() + 0.5, villager.getZ(),
                        5, 0.4, 0.4, 0.4, 0.0);
            }
        }
    }
}
