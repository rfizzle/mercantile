package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.market.MarketDayManager;
import com.rfizzle.mercantile.market.MarketDayMath;
import com.rfizzle.mercantile.market.MarketDayState;
import com.rfizzle.mercantile.network.DemandPriceS2CPayload;
import com.rfizzle.mercantile.trade.PriceBreakdownBuilder;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.UUID;

public class MarketDayGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void discountAppearsAsOwnBreakdownLineOnMarketDay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MercantileConfig config = MercantileConfig.get();
        long savedDayTime = level.getDayTime();
        boolean savedEnable = config.enableMarketDay;
        int savedInterval = config.marketDayIntervalDays;
        int savedDiscount = config.marketDayDiscountPercent;

        Villager villager = spawnTrader(helper, new MerchantOffer(
                new ItemCost(Items.EMERALD, 64), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f));
        ServerPlayer player = directPlayer(helper);

        try {
            config.enableMarketDay = true;
            config.marketDayIntervalDays = 7;
            config.marketDayDiscountPercent = 5;
            level.setDayTime(marketDayMorning(level, config.marketDayIntervalDays));

            DemandPriceS2CPayload.PriceComponent c =
                    PriceBreakdownBuilder.buildFor(villager, player).get(0);
            helper.assertTrue(c.marketDayModifier() == -3,
                    "market-day modifier should be -3 (5% of 64, floored); got " + c.marketDayModifier());
        } finally {
            level.setDayTime(savedDayTime);
            config.enableMarketDay = savedEnable;
            config.marketDayIntervalDays = savedInterval;
            config.marketDayDiscountPercent = savedDiscount;
            player.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void noDiscountOffMarketDay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MercantileConfig config = MercantileConfig.get();
        long savedDayTime = level.getDayTime();
        boolean savedEnable = config.enableMarketDay;
        int savedInterval = config.marketDayIntervalDays;

        Villager villager = spawnTrader(helper, new MerchantOffer(
                new ItemCost(Items.EMERALD, 64), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f));
        ServerPlayer player = directPlayer(helper);

        try {
            config.enableMarketDay = true;
            config.marketDayIntervalDays = 7;
            // The morning after a market day is never itself a market day (interval >= 2).
            level.setDayTime(marketDayMorning(level, config.marketDayIntervalDays) + MarketDayMath.TICKS_PER_DAY);

            DemandPriceS2CPayload.PriceComponent c =
                    PriceBreakdownBuilder.buildFor(villager, player).get(0);
            helper.assertTrue(c.marketDayModifier() == 0,
                    "no market-day modifier expected off market day; got " + c.marketDayModifier());
        } finally {
            level.setDayTime(savedDayTime);
            config.enableMarketDay = savedEnable;
            config.marketDayIntervalDays = savedInterval;
            player.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void disabledFeatureHasNoEffect(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MercantileConfig config = MercantileConfig.get();
        long savedDayTime = level.getDayTime();
        boolean savedEnable = config.enableMarketDay;
        int savedInterval = config.marketDayIntervalDays;

        Villager villager = spawnTrader(helper, new MerchantOffer(
                new ItemCost(Items.EMERALD, 64), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f));
        ServerPlayer player = directPlayer(helper);

        try {
            config.enableMarketDay = false;
            config.marketDayIntervalDays = 7;
            level.setDayTime(marketDayMorning(level, config.marketDayIntervalDays));

            DemandPriceS2CPayload.PriceComponent c =
                    PriceBreakdownBuilder.buildFor(villager, player).get(0);
            helper.assertTrue(c.marketDayModifier() == 0,
                    "disabled market day must not discount; got " + c.marketDayModifier());
            helper.assertTrue(MarketDayManager.maxRestocksToday(villager) == 2,
                    "disabled market day must keep the vanilla restock cap");
        } finally {
            level.setDayTime(savedDayTime);
            config.enableMarketDay = savedEnable;
            config.marketDayIntervalDays = savedInterval;
            player.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void restockCapRaisedOnMarketDay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MercantileConfig config = MercantileConfig.get();
        long savedDayTime = level.getDayTime();
        boolean savedEnable = config.enableMarketDay;
        int savedInterval = config.marketDayIntervalDays;

        Villager villager = spawnTrader(helper, new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f));

        try {
            config.enableMarketDay = true;
            config.marketDayIntervalDays = 7;
            level.setDayTime(marketDayMorning(level, config.marketDayIntervalDays));
            helper.assertTrue(MarketDayManager.maxRestocksToday(villager) == 3,
                    "market day should raise the restock cap to 3");

            level.setDayTime(marketDayMorning(level, config.marketDayIntervalDays) + MarketDayMath.TICKS_PER_DAY);
            helper.assertTrue(MarketDayManager.maxRestocksToday(villager) == 2,
                    "off market day the vanilla cap of 2 applies");
        } finally {
            level.setDayTime(savedDayTime);
            config.enableMarketDay = savedEnable;
            config.marketDayIntervalDays = savedInterval;
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void announcementStatePersistsLastAnnouncedDay(GameTestHelper helper) {
        MarketDayState state = MarketDayState.getOrCreate(helper.getLevel().getServer());
        long saved = state.getLastAnnouncedDay();
        try {
            state.setLastAnnouncedDay(1234L);
            helper.assertTrue(
                    MarketDayState.getOrCreate(helper.getLevel().getServer()).getLastAnnouncedDay() == 1234L,
                    "lastAnnouncedDay should read back through the world's data storage");
        } finally {
            state.setLastAnnouncedDay(saved);
        }
        helper.succeed();
    }

    // The next market-day morning strictly after the level's current day, mid-morning
    // (+1000 ticks). Always at least day intervalDays, since day 0 is never a market day.
    private static long marketDayMorning(ServerLevel level, int intervalDays) {
        long day = MarketDayMath.dayOf(level.getDayTime());
        long nextMarketDay = (day / intervalDays + 1) * intervalDays;
        return nextMarketDay * MarketDayMath.TICKS_PER_DAY + 1_000L;
    }

    private static Villager spawnTrader(GameTestHelper helper, MerchantOffer offer) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        MerchantOffers offers = new MerchantOffers();
        offers.add(offer);
        villager.setOffers(offers);
        return villager;
    }

    // Direct ServerPlayer construction avoids PlayerList.placeNewPlayer, which schedules
    // server work that ticks the villager's brain and wipes overridden offers.
    private static ServerPlayer directPlayer(GameTestHelper helper) {
        return new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "TestPlayer"),
                ClientInformation.createDefault());
    }
}
