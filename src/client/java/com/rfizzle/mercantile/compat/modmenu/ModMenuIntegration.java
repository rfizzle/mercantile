package com.rfizzle.mercantile.compat.modmenu;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            MercantileConfig config = MercantileConfig.get();
            MercantileConfig defaults = new MercantileConfig();

            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Component.translatable("mercantile.config.title"))
                    .setSavingRunnable(() -> {
                        config.clamp();
                        config.save();
                    });

            ConfigEntryBuilder entry = builder.entryBuilder();

            // --- Villager Pickup ---
            ConfigCategory pickup = builder.getOrCreateCategory(Component.translatable("mercantile.config.category.pickup"));
            pickup.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableVillagerPickup"), config.enableVillagerPickup)
                    .setDefaultValue(defaults.enableVillagerPickup)
                    .setTooltip(Component.translatable("mercantile.config.enableVillagerPickup.tooltip"))
                    .setSaveConsumer(v -> config.enableVillagerPickup = v)
                    .build());
            pickup.addEntry(entry.startIntField(Component.translatable("mercantile.config.pickupXpCost"), config.pickupXpCost)
                    .setDefaultValue(defaults.pickupXpCost)
                    .setMin(0)
                    .setTooltip(Component.translatable("mercantile.config.pickupXpCost.tooltip"))
                    .setSaveConsumer(v -> config.pickupXpCost = v)
                    .build());

            // --- Villager Names ---
            ConfigCategory names = builder.getOrCreateCategory(Component.translatable("mercantile.config.category.names"));
            names.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableNames"), config.enableNames)
                    .setDefaultValue(defaults.enableNames)
                    .setTooltip(Component.translatable("mercantile.config.enableNames.tooltip"))
                    .setSaveConsumer(v -> config.enableNames = v)
                    .build());

            // --- Trade Cycling ---
            ConfigCategory cycling = builder.getOrCreateCategory(Component.translatable("mercantile.config.category.cycling"));
            cycling.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableTradeCycling"), config.enableTradeCycling)
                    .setDefaultValue(defaults.enableTradeCycling)
                    .setTooltip(Component.translatable("mercantile.config.enableTradeCycling.tooltip"))
                    .setSaveConsumer(v -> config.enableTradeCycling = v)
                    .build());
            cycling.addEntry(entry.startIntField(Component.translatable("mercantile.config.tradeCycleEmeraldCost"), config.tradeCycleEmeraldCost)
                    .setDefaultValue(defaults.tradeCycleEmeraldCost)
                    .setMin(0)
                    .setTooltip(Component.translatable("mercantile.config.tradeCycleEmeraldCost.tooltip"))
                    .setSaveConsumer(v -> config.tradeCycleEmeraldCost = v)
                    .build());

            // --- Reputation ---
            ConfigCategory reputation = builder.getOrCreateCategory(Component.translatable("mercantile.config.category.reputation"));
            reputation.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableReputation"), config.enableReputation)
                    .setDefaultValue(defaults.enableReputation)
                    .setTooltip(Component.translatable("mercantile.config.enableReputation.tooltip"))
                    .setSaveConsumer(v -> config.enableReputation = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("mercantile.config.reputationTradeGain"), config.reputationTradeGain)
                    .setDefaultValue(defaults.reputationTradeGain)
                    .setMin(0)
                    .setTooltip(Component.translatable("mercantile.config.reputationTradeGain.tooltip"))
                    .setSaveConsumer(v -> config.reputationTradeGain = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("mercantile.config.reputationCureGain"), config.reputationCureGain)
                    .setDefaultValue(defaults.reputationCureGain)
                    .setMin(0)
                    .setTooltip(Component.translatable("mercantile.config.reputationCureGain.tooltip"))
                    .setSaveConsumer(v -> config.reputationCureGain = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("mercantile.config.reputationAttackLoss"), config.reputationAttackLoss)
                    .setDefaultValue(defaults.reputationAttackLoss)
                    .setMin(0)
                    .setTooltip(Component.translatable("mercantile.config.reputationAttackLoss.tooltip"))
                    .setSaveConsumer(v -> config.reputationAttackLoss = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("mercantile.config.reputationKillLoss"), config.reputationKillLoss)
                    .setDefaultValue(defaults.reputationKillLoss)
                    .setMin(0)
                    .setTooltip(Component.translatable("mercantile.config.reputationKillLoss.tooltip"))
                    .setSaveConsumer(v -> config.reputationKillLoss = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("mercantile.config.reputationCycleGain"), config.reputationCycleGain)
                    .setDefaultValue(defaults.reputationCycleGain)
                    .setMin(0)
                    .setTooltip(Component.translatable("mercantile.config.reputationCycleGain.tooltip"))
                    .setSaveConsumer(v -> config.reputationCycleGain = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("mercantile.config.reputationDailyCap"), config.reputationDailyCap)
                    .setDefaultValue(defaults.reputationDailyCap)
                    .setMin(1).setMax(50)
                    .setTooltip(Component.translatable("mercantile.config.reputationDailyCap.tooltip"))
                    .setSaveConsumer(v -> config.reputationDailyCap = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("mercantile.config.reputationTradesPerGain"), config.reputationTradesPerGain)
                    .setDefaultValue(defaults.reputationTradesPerGain)
                    .setMin(1).setMax(20)
                    .setTooltip(Component.translatable("mercantile.config.reputationTradesPerGain.tooltip"))
                    .setSaveConsumer(v -> config.reputationTradesPerGain = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("mercantile.config.reputationDailyMaxTradeRep"), config.reputationDailyMaxTradeRep)
                    .setDefaultValue(defaults.reputationDailyMaxTradeRep)
                    .setMin(1).setMax(10)
                    .setTooltip(Component.translatable("mercantile.config.reputationDailyMaxTradeRep.tooltip"))
                    .setSaveConsumer(v -> config.reputationDailyMaxTradeRep = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("mercantile.config.reputationDailyMaxCycleRep"), config.reputationDailyMaxCycleRep)
                    .setDefaultValue(defaults.reputationDailyMaxCycleRep)
                    .setMin(1).setMax(10)
                    .setTooltip(Component.translatable("mercantile.config.reputationDailyMaxCycleRep.tooltip"))
                    .setSaveConsumer(v -> config.reputationDailyMaxCycleRep = v)
                    .build());
            reputation.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableRaidReputation"), config.enableRaidReputation)
                    .setDefaultValue(defaults.enableRaidReputation)
                    .setTooltip(Component.translatable("mercantile.config.enableRaidReputation.tooltip"))
                    .setSaveConsumer(v -> config.enableRaidReputation = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("mercantile.config.reputationRaidWinGain"), config.reputationRaidWinGain)
                    .setDefaultValue(defaults.reputationRaidWinGain)
                    .setMin(0)
                    .setTooltip(Component.translatable("mercantile.config.reputationRaidWinGain.tooltip"))
                    .setSaveConsumer(v -> config.reputationRaidWinGain = v)
                    .build());
            reputation.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableWanderingTraderRep"), config.enableWanderingTraderRep)
                    .setDefaultValue(defaults.enableWanderingTraderRep)
                    .setTooltip(Component.translatable("mercantile.config.enableWanderingTraderRep.tooltip"))
                    .setSaveConsumer(v -> config.enableWanderingTraderRep = v)
                    .build());
            reputation.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableGifting"), config.enableGifting)
                    .setDefaultValue(defaults.enableGifting)
                    .setTooltip(Component.translatable("mercantile.config.enableGifting.tooltip"))
                    .setSaveConsumer(v -> config.enableGifting = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("mercantile.config.reputationGiftGain"), config.reputationGiftGain)
                    .setDefaultValue(defaults.reputationGiftGain)
                    .setMin(0)
                    .setTooltip(Component.translatable("mercantile.config.reputationGiftGain.tooltip"))
                    .setSaveConsumer(v -> config.reputationGiftGain = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("mercantile.config.reputationDailyMaxGiftRep"), config.reputationDailyMaxGiftRep)
                    .setDefaultValue(defaults.reputationDailyMaxGiftRep)
                    .setMin(1).setMax(10)
                    .setTooltip(Component.translatable("mercantile.config.reputationDailyMaxGiftRep.tooltip"))
                    .setSaveConsumer(v -> config.reputationDailyMaxGiftRep = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("mercantile.config.reputationNegativeDecayPerDay"), config.reputationNegativeDecayPerDay)
                    .setDefaultValue(defaults.reputationNegativeDecayPerDay)
                    .setMin(0)
                    .setTooltip(Component.translatable("mercantile.config.reputationNegativeDecayPerDay.tooltip"))
                    .setSaveConsumer(v -> config.reputationNegativeDecayPerDay = v)
                    .build());
            reputation.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableGratitudeGifts"), config.enableGratitudeGifts)
                    .setDefaultValue(defaults.enableGratitudeGifts)
                    .setTooltip(Component.translatable("mercantile.config.enableGratitudeGifts.tooltip"))
                    .setSaveConsumer(v -> config.enableGratitudeGifts = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("mercantile.config.gratitudeGiftsPerDay"), config.gratitudeGiftsPerDay)
                    .setDefaultValue(defaults.gratitudeGiftsPerDay)
                    .setMin(0).setMax(10)
                    .setTooltip(Component.translatable("mercantile.config.gratitudeGiftsPerDay.tooltip"))
                    .setSaveConsumer(v -> config.gratitudeGiftsPerDay = v)
                    .build());
            reputation.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableNitwitRehab"), config.enableNitwitRehab)
                    .setDefaultValue(defaults.enableNitwitRehab)
                    .setTooltip(Component.translatable("mercantile.config.enableNitwitRehab.tooltip"))
                    .setSaveConsumer(v -> config.enableNitwitRehab = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("mercantile.config.nitwitRehabEmeraldCost"), config.nitwitRehabEmeraldCost)
                    .setDefaultValue(defaults.nitwitRehabEmeraldCost)
                    .setMin(0)
                    .setTooltip(Component.translatable("mercantile.config.nitwitRehabEmeraldCost.tooltip"))
                    .setSaveConsumer(v -> config.nitwitRehabEmeraldCost = v)
                    .build());

            // --- Follow Mode ---
            ConfigCategory follow = builder.getOrCreateCategory(Component.translatable("mercantile.config.category.follow"));
            follow.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableFollowMode"), config.enableFollowMode)
                    .setDefaultValue(defaults.enableFollowMode)
                    .setTooltip(Component.translatable("mercantile.config.enableFollowMode.tooltip"))
                    .setSaveConsumer(v -> config.enableFollowMode = v)
                    .build());
            follow.addEntry(entry.startIntField(Component.translatable("mercantile.config.maxFollowingVillagers"), config.maxFollowingVillagers)
                    .setDefaultValue(defaults.maxFollowingVillagers)
                    .setMin(1)
                    .setTooltip(Component.translatable("mercantile.config.maxFollowingVillagers.tooltip"))
                    .setSaveConsumer(v -> config.maxFollowingVillagers = v)
                    .build());
            follow.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableSendHome"), config.enableSendHome)
                    .setDefaultValue(defaults.enableSendHome)
                    .setTooltip(Component.translatable("mercantile.config.enableSendHome.tooltip"))
                    .setSaveConsumer(v -> config.enableSendHome = v)
                    .build());

            // --- Pathfinding ---
            ConfigCategory pathfinding = builder.getOrCreateCategory(Component.translatable("mercantile.config.category.pathfinding"));
            pathfinding.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enablePathfindingFixes"), config.enablePathfindingFixes)
                    .setDefaultValue(defaults.enablePathfindingFixes)
                    .setTooltip(Component.translatable("mercantile.config.enablePathfindingFixes.tooltip"))
                    .setSaveConsumer(v -> config.enablePathfindingFixes = v)
                    .build());
            pathfinding.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enablePathfindingDoors"), config.enablePathfindingDoors)
                    .setDefaultValue(defaults.enablePathfindingDoors)
                    .setTooltip(Component.translatable("mercantile.config.enablePathfindingDoors.tooltip"))
                    .setSaveConsumer(v -> config.enablePathfindingDoors = v)
                    .build());
            pathfinding.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enablePathfindingStairs"), config.enablePathfindingStairs)
                    .setDefaultValue(defaults.enablePathfindingStairs)
                    .setTooltip(Component.translatable("mercantile.config.enablePathfindingStairs.tooltip"))
                    .setSaveConsumer(v -> config.enablePathfindingStairs = v)
                    .build());
            pathfinding.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enablePathfindingLadders"), config.enablePathfindingLadders)
                    .setDefaultValue(defaults.enablePathfindingLadders)
                    .setTooltip(Component.translatable("mercantile.config.enablePathfindingLadders.tooltip"))
                    .setSaveConsumer(v -> config.enablePathfindingLadders = v)
                    .build());
            pathfinding.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enablePathfindingWater"), config.enablePathfindingWater)
                    .setDefaultValue(defaults.enablePathfindingWater)
                    .setTooltip(Component.translatable("mercantile.config.enablePathfindingWater.tooltip"))
                    .setSaveConsumer(v -> config.enablePathfindingWater = v)
                    .build());

            // --- Trading ---
            ConfigCategory trading = builder.getOrCreateCategory(Component.translatable("mercantile.config.category.trading"));
            trading.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableBulkTrading"), config.enableBulkTrading)
                    .setDefaultValue(defaults.enableBulkTrading)
                    .setTooltip(Component.translatable("mercantile.config.enableBulkTrading.tooltip"))
                    .setSaveConsumer(v -> config.enableBulkTrading = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableProfessionLock"), config.enableProfessionLock)
                    .setDefaultValue(defaults.enableProfessionLock)
                    .setTooltip(Component.translatable("mercantile.config.enableProfessionLock.tooltip"))
                    .setSaveConsumer(v -> config.enableProfessionLock = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableWorkOrders"), config.enableWorkOrders)
                    .setDefaultValue(defaults.enableWorkOrders)
                    .setTooltip(Component.translatable("mercantile.config.enableWorkOrders.tooltip"))
                    .setSaveConsumer(v -> config.enableWorkOrders = v)
                    .build());
            trading.addEntry(entry.startIntField(Component.translatable("mercantile.config.workOrderEmeraldCost"), config.workOrderEmeraldCost)
                    .setDefaultValue(defaults.workOrderEmeraldCost)
                    .setMin(0)
                    .setTooltip(Component.translatable("mercantile.config.workOrderEmeraldCost.tooltip"))
                    .setSaveConsumer(v -> config.workOrderEmeraldCost = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableHealing"), config.enableHealing)
                    .setDefaultValue(defaults.enableHealing)
                    .setTooltip(Component.translatable("mercantile.config.enableHealing.tooltip"))
                    .setSaveConsumer(v -> config.enableHealing = v)
                    .build());
            trading.addEntry(entry.startFloatField(Component.translatable("mercantile.config.healingMultiplier"), config.healingMultiplier)
                    .setDefaultValue(defaults.healingMultiplier)
                    .setMin(1.0f).setMax(10.0f)
                    .setTooltip(Component.translatable("mercantile.config.healingMultiplier.tooltip"))
                    .setSaveConsumer(v -> config.healingMultiplier = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableRestockIndicator"), config.enableRestockIndicator)
                    .setDefaultValue(defaults.enableRestockIndicator)
                    .setTooltip(Component.translatable("mercantile.config.enableRestockIndicator.tooltip"))
                    .setSaveConsumer(v -> config.enableRestockIndicator = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableDemandTransparency"), config.enableDemandTransparency)
                    .setDefaultValue(defaults.enableDemandTransparency)
                    .setTooltip(Component.translatable("mercantile.config.enableDemandTransparency.tooltip"))
                    .setSaveConsumer(v -> config.enableDemandTransparency = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableTradePinning"), config.enableTradePinning)
                    .setDefaultValue(defaults.enableTradePinning)
                    .setTooltip(Component.translatable("mercantile.config.enableTradePinning.tooltip"))
                    .setSaveConsumer(v -> config.enableTradePinning = v)
                    .build());
            trading.addEntry(entry.startIntField(Component.translatable("mercantile.config.maxPinnedTradesPerPlayer"), config.maxPinnedTradesPerPlayer)
                    .setDefaultValue(defaults.maxPinnedTradesPerPlayer)
                    .setMin(1).setMax(64)
                    .setTooltip(Component.translatable("mercantile.config.maxPinnedTradesPerPlayer.tooltip"))
                    .setSaveConsumer(v -> config.maxPinnedTradesPerPlayer = v)
                    .build());
            trading.addEntry(entry.startIntField(Component.translatable("mercantile.config.pinRestockNotifyRange"), config.pinRestockNotifyRange)
                    .setDefaultValue(defaults.pinRestockNotifyRange)
                    .setMin(8).setMax(256)
                    .setTooltip(Component.translatable("mercantile.config.pinRestockNotifyRange.tooltip"))
                    .setSaveConsumer(v -> config.pinRestockNotifyRange = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableBreedingTooltip"), config.enableBreedingTooltip)
                    .setDefaultValue(defaults.enableBreedingTooltip)
                    .setTooltip(Component.translatable("mercantile.config.enableBreedingTooltip.tooltip"))
                    .setSaveConsumer(v -> config.enableBreedingTooltip = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableBabyFeeding"), config.enableBabyFeeding)
                    .setDefaultValue(defaults.enableBabyFeeding)
                    .setTooltip(Component.translatable("mercantile.config.enableBabyFeeding.tooltip"))
                    .setSaveConsumer(v -> config.enableBabyFeeding = v)
                    .build());
            trading.addEntry(entry.startIntField(Component.translatable("mercantile.config.babyFeedPercentPerFeed"), config.babyFeedPercentPerFeed)
                    .setDefaultValue(defaults.babyFeedPercentPerFeed)
                    .setMin(1).setMax(100)
                    .setTooltip(Component.translatable("mercantile.config.babyFeedPercentPerFeed.tooltip"))
                    .setSaveConsumer(v -> config.babyFeedPercentPerFeed = v)
                    .build());
            trading.addEntry(entry.startIntField(Component.translatable("mercantile.config.babyFeedMaxReductionPercent"), config.babyFeedMaxReductionPercent)
                    .setDefaultValue(defaults.babyFeedMaxReductionPercent)
                    .setMin(0).setMax(100)
                    .setTooltip(Component.translatable("mercantile.config.babyFeedMaxReductionPercent.tooltip"))
                    .setSaveConsumer(v -> config.babyFeedMaxReductionPercent = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableStateIndicators"), config.enableStateIndicators)
                    .setDefaultValue(defaults.enableStateIndicators)
                    .setTooltip(Component.translatable("mercantile.config.enableStateIndicators.tooltip"))
                    .setSaveConsumer(v -> config.enableStateIndicators = v)
                    .build());

            // --- Mood ---
            ConfigCategory mood = builder.getOrCreateCategory(Component.translatable("mercantile.config.category.mood"));
            mood.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableMood"), config.enableMood)
                    .setDefaultValue(defaults.enableMood)
                    .setTooltip(Component.translatable("mercantile.config.enableMood.tooltip"))
                    .setSaveConsumer(v -> config.enableMood = v)
                    .build());
            mood.addEntry(entry.startIntField(Component.translatable("mercantile.config.moodPriceModifierPercent"), config.moodPriceModifierPercent)
                    .setDefaultValue(defaults.moodPriceModifierPercent)
                    .setMin(0).setMax(50)
                    .setTooltip(Component.translatable("mercantile.config.moodPriceModifierPercent.tooltip"))
                    .setSaveConsumer(v -> config.moodPriceModifierPercent = v)
                    .build());
            mood.addEntry(entry.startIntField(Component.translatable("mercantile.config.moodRestockSpeedPercent"), config.moodRestockSpeedPercent)
                    .setDefaultValue(defaults.moodRestockSpeedPercent)
                    .setMin(0).setMax(80)
                    .setTooltip(Component.translatable("mercantile.config.moodRestockSpeedPercent.tooltip"))
                    .setSaveConsumer(v -> config.moodRestockSpeedPercent = v)
                    .build());
            mood.addEntry(entry.startIntField(Component.translatable("mercantile.config.moodRecalcIntervalTicks"), config.moodRecalcIntervalTicks)
                    .setDefaultValue(defaults.moodRecalcIntervalTicks)
                    .setMin(20).setMax(24_000)
                    .setTooltip(Component.translatable("mercantile.config.moodRecalcIntervalTicks.tooltip"))
                    .setSaveConsumer(v -> config.moodRecalcIntervalTicks = v)
                    .build());
            mood.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.moodAmbientParticles"), config.moodAmbientParticles)
                    .setDefaultValue(defaults.moodAmbientParticles)
                    .setTooltip(Component.translatable("mercantile.config.moodAmbientParticles.tooltip"))
                    .setSaveConsumer(v -> config.moodAmbientParticles = v)
                    .build());

            // --- Market Day ---
            ConfigCategory market = builder.getOrCreateCategory(Component.translatable("mercantile.config.category.market"));
            market.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableMarketDay"), config.enableMarketDay)
                    .setDefaultValue(defaults.enableMarketDay)
                    .setTooltip(Component.translatable("mercantile.config.enableMarketDay.tooltip"))
                    .setSaveConsumer(v -> config.enableMarketDay = v)
                    .build());
            market.addEntry(entry.startIntField(Component.translatable("mercantile.config.marketDayIntervalDays"), config.marketDayIntervalDays)
                    .setDefaultValue(defaults.marketDayIntervalDays)
                    .setMin(1).setMax(1_000)
                    .setTooltip(Component.translatable("mercantile.config.marketDayIntervalDays.tooltip"))
                    .setSaveConsumer(v -> config.marketDayIntervalDays = v)
                    .build());
            market.addEntry(entry.startIntField(Component.translatable("mercantile.config.marketDayDiscountPercent"), config.marketDayDiscountPercent)
                    .setDefaultValue(defaults.marketDayDiscountPercent)
                    .setMin(0).setMax(100)
                    .setTooltip(Component.translatable("mercantile.config.marketDayDiscountPercent.tooltip"))
                    .setSaveConsumer(v -> config.marketDayDiscountPercent = v)
                    .build());

            // --- Memorials & Fear ---
            ConfigCategory memorial = builder.getOrCreateCategory(Component.translatable("mercantile.config.category.memorial"));
            memorial.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableMemorials"), config.enableMemorials)
                    .setDefaultValue(defaults.enableMemorials)
                    .setTooltip(Component.translatable("mercantile.config.enableMemorials.tooltip"))
                    .setSaveConsumer(v -> config.enableMemorials = v)
                    .build());
            memorial.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableMourning"), config.enableMourning)
                    .setDefaultValue(defaults.enableMourning)
                    .setTooltip(Component.translatable("mercantile.config.enableMourning.tooltip"))
                    .setSaveConsumer(v -> config.enableMourning = v)
                    .build());
            memorial.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableFearMarkup"), config.enableFearMarkup)
                    .setDefaultValue(defaults.enableFearMarkup)
                    .setTooltip(Component.translatable("mercantile.config.enableFearMarkup.tooltip"))
                    .setSaveConsumer(v -> config.enableFearMarkup = v)
                    .build());
            memorial.addEntry(entry.startIntField(Component.translatable("mercantile.config.fearKillThreshold"), config.fearKillThreshold)
                    .setDefaultValue(defaults.fearKillThreshold)
                    .setMin(1).setMax(20)
                    .setTooltip(Component.translatable("mercantile.config.fearKillThreshold.tooltip"))
                    .setSaveConsumer(v -> config.fearKillThreshold = v)
                    .build());
            memorial.addEntry(entry.startIntField(Component.translatable("mercantile.config.fearKillWindowMinutes"), config.fearKillWindowMinutes)
                    .setDefaultValue(defaults.fearKillWindowMinutes)
                    .setMin(1).setMax(120)
                    .setTooltip(Component.translatable("mercantile.config.fearKillWindowMinutes.tooltip"))
                    .setSaveConsumer(v -> config.fearKillWindowMinutes = v)
                    .build());
            memorial.addEntry(entry.startIntField(Component.translatable("mercantile.config.fearMarkupPercent"), config.fearMarkupPercent)
                    .setDefaultValue(defaults.fearMarkupPercent)
                    .setMin(0).setMax(200)
                    .setTooltip(Component.translatable("mercantile.config.fearMarkupPercent.tooltip"))
                    .setSaveConsumer(v -> config.fearMarkupPercent = v)
                    .build());
            memorial.addEntry(entry.startIntField(Component.translatable("mercantile.config.fearMarkupDurationDays"), config.fearMarkupDurationDays)
                    .setDefaultValue(defaults.fearMarkupDurationDays)
                    .setMin(1).setMax(30)
                    .setTooltip(Component.translatable("mercantile.config.fearMarkupDurationDays.tooltip"))
                    .setSaveConsumer(v -> config.fearMarkupDurationDays = v)
                    .build());

            // --- Sentry Pylon ---
            ConfigCategory pylon = builder.getOrCreateCategory(Component.translatable("mercantile.config.category.pylon"));
            pylon.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableSentryPylon"), config.enableSentryPylon)
                    .setDefaultValue(defaults.enableSentryPylon)
                    .setTooltip(Component.translatable("mercantile.config.enableSentryPylon.tooltip"))
                    .setSaveConsumer(v -> config.enableSentryPylon = v)
                    .build());
            pylon.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enablePylonBellAlarm"), config.enablePylonBellAlarm)
                    .setDefaultValue(defaults.enablePylonBellAlarm)
                    .setTooltip(Component.translatable("mercantile.config.enablePylonBellAlarm.tooltip"))
                    .setSaveConsumer(v -> config.enablePylonBellAlarm = v)
                    .build());
            pylon.addEntry(entry.startIntField(Component.translatable("mercantile.config.pylonDetectionRadius"), config.pylonDetectionRadius)
                    .setDefaultValue(defaults.pylonDetectionRadius)
                    .setMin(8).setMax(128)
                    .setTooltip(Component.translatable("mercantile.config.pylonDetectionRadius.tooltip"))
                    .setSaveConsumer(v -> config.pylonDetectionRadius = v)
                    .build());
            pylon.addEntry(entry.startIntField(Component.translatable("mercantile.config.pylonMaxFuel"), config.pylonMaxFuel)
                    .setDefaultValue(defaults.pylonMaxFuel)
                    .setMin(1)
                    .setTooltip(Component.translatable("mercantile.config.pylonMaxFuel.tooltip"))
                    .setSaveConsumer(v -> config.pylonMaxFuel = v)
                    .build());
            pylon.addEntry(entry.startIntField(Component.translatable("mercantile.config.pylonMaxGolems"), config.pylonMaxGolems)
                    .setDefaultValue(defaults.pylonMaxGolems)
                    .setMin(1)
                    .setTooltip(Component.translatable("mercantile.config.pylonMaxGolems.tooltip"))
                    .setSaveConsumer(v -> config.pylonMaxGolems = v)
                    .build());
            pylon.addEntry(entry.startIntField(Component.translatable("mercantile.config.sentryDespawnSeconds"), config.sentryDespawnSeconds)
                    .setDefaultValue(defaults.sentryDespawnSeconds)
                    .setMin(5)
                    .setTooltip(Component.translatable("mercantile.config.sentryDespawnSeconds.tooltip"))
                    .setSaveConsumer(v -> config.sentryDespawnSeconds = v)
                    .build());
            pylon.addEntry(entry.startIntField(Component.translatable("mercantile.config.pylonTribulationGolemBonusPerTier"), config.pylonTribulationGolemBonusPerTier)
                    .setDefaultValue(defaults.pylonTribulationGolemBonusPerTier)
                    .setMin(0)
                    .setTooltip(Component.translatable("mercantile.config.pylonTribulationGolemBonusPerTier.tooltip"))
                    .setSaveConsumer(v -> config.pylonTribulationGolemBonusPerTier = v)
                    .build());
            pylon.addEntry(entry.startIntField(Component.translatable("mercantile.config.pylonTribulationRadiusBonusPerTier"), config.pylonTribulationRadiusBonusPerTier)
                    .setDefaultValue(defaults.pylonTribulationRadiusBonusPerTier)
                    .setMin(0)
                    .setTooltip(Component.translatable("mercantile.config.pylonTribulationRadiusBonusPerTier.tooltip"))
                    .setSaveConsumer(v -> config.pylonTribulationRadiusBonusPerTier = v)
                    .build());
            pylon.addEntry(entry.startIntField(Component.translatable("mercantile.config.pylonTribulationMaxGolems"), config.pylonTribulationMaxGolems)
                    .setDefaultValue(defaults.pylonTribulationMaxGolems)
                    .setMin(1)
                    .setTooltip(Component.translatable("mercantile.config.pylonTribulationMaxGolems.tooltip"))
                    .setSaveConsumer(v -> config.pylonTribulationMaxGolems = v)
                    .build());

            // --- Client ---
            ConfigCategory client = builder.getOrCreateCategory(Component.translatable("mercantile.config.category.client"));
            client.addEntry(entry.startFloatField(Component.translatable("mercantile.config.villagerSoundVolume"), config.villagerSoundVolume)
                    .setDefaultValue(defaults.villagerSoundVolume)
                    .setMin(0.0f).setMax(1.0f)
                    .setTooltip(Component.translatable("mercantile.config.villagerSoundVolume.tooltip"))
                    .setSaveConsumer(v -> config.villagerSoundVolume = v)
                    .build());
            client.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableWorkstationVis"), config.enableWorkstationVis)
                    .setDefaultValue(defaults.enableWorkstationVis)
                    .setTooltip(Component.translatable("mercantile.config.enableWorkstationVis.tooltip"))
                    .setSaveConsumer(v -> config.enableWorkstationVis = v)
                    .build());
            client.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableBellRadiusVis"), config.enableBellRadiusVis)
                    .setDefaultValue(defaults.enableBellRadiusVis)
                    .setTooltip(Component.translatable("mercantile.config.enableBellRadiusVis.tooltip"))
                    .setSaveConsumer(v -> config.enableBellRadiusVis = v)
                    .build());
            client.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableInfoPanel"), config.enableInfoPanel)
                    .setDefaultValue(defaults.enableInfoPanel)
                    .setTooltip(Component.translatable("mercantile.config.enableInfoPanel.tooltip"))
                    .setSaveConsumer(v -> config.enableInfoPanel = v)
                    .build());
            client.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableReputationHud"), config.enableReputationHud)
                    .setDefaultValue(defaults.enableReputationHud)
                    .setTooltip(Component.translatable("mercantile.config.enableReputationHud.tooltip"))
                    .setSaveConsumer(v -> config.enableReputationHud = v)
                    .build());
            client.addEntry(entry.startEnumSelector(Component.translatable("mercantile.config.hudAnchor"),
                            MercantileConfig.Anchor.class, config.hudAnchor)
                    .setDefaultValue(defaults.hudAnchor)
                    .setEnumNameProvider(v -> Component.translatable(
                            "mercantile.config.hudAnchor." + v.name().toLowerCase(Locale.ROOT)))
                    .setTooltip(Component.translatable("mercantile.config.hudAnchor.tooltip"))
                    .setSaveConsumer(v -> config.hudAnchor = v)
                    .build());
            client.addEntry(entry.startIntField(Component.translatable("mercantile.config.hudOffsetX"), config.hudOffsetX)
                    .setDefaultValue(defaults.hudOffsetX)
                    .setMin(0).setMax(10_000)
                    .setTooltip(Component.translatable("mercantile.config.hudOffsetX.tooltip"))
                    .setSaveConsumer(v -> config.hudOffsetX = v)
                    .build());
            client.addEntry(entry.startIntField(Component.translatable("mercantile.config.hudOffsetY"), config.hudOffsetY)
                    .setDefaultValue(defaults.hudOffsetY)
                    .setMin(0).setMax(10_000)
                    .setTooltip(Component.translatable("mercantile.config.hudOffsetY.tooltip"))
                    .setSaveConsumer(v -> config.hudOffsetY = v)
                    .build());

            return builder.build();
        };
    }
}
