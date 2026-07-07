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
                    .setTitle(Component.translatable("config.mercantile.title"))
                    .setSavingRunnable(() -> {
                        config.clamp();
                        config.save();
                    });

            ConfigEntryBuilder entry = builder.entryBuilder();

            // --- Villager Pickup ---
            ConfigCategory pickup = builder.getOrCreateCategory(Component.translatable("config.mercantile.category.pickup"));
            pickup.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableVillagerPickup"), config.enableVillagerPickup)
                    .setDefaultValue(defaults.enableVillagerPickup)
                    .setTooltip(Component.translatable("config.mercantile.enableVillagerPickup.tooltip"))
                    .setSaveConsumer(v -> config.enableVillagerPickup = v)
                    .build());
            pickup.addEntry(entry.startIntField(Component.translatable("config.mercantile.pickupXpCost"), config.pickupXpCost)
                    .setDefaultValue(defaults.pickupXpCost)
                    .setMin(0)
                    .setTooltip(Component.translatable("config.mercantile.pickupXpCost.tooltip"))
                    .setSaveConsumer(v -> config.pickupXpCost = v)
                    .build());

            // --- Villager Names ---
            ConfigCategory names = builder.getOrCreateCategory(Component.translatable("config.mercantile.category.names"));
            names.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableNames"), config.enableNames)
                    .setDefaultValue(defaults.enableNames)
                    .setTooltip(Component.translatable("config.mercantile.enableNames.tooltip"))
                    .setSaveConsumer(v -> config.enableNames = v)
                    .build());

            // --- Trade Cycling ---
            ConfigCategory cycling = builder.getOrCreateCategory(Component.translatable("config.mercantile.category.cycling"));
            cycling.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableTradeCycling"), config.enableTradeCycling)
                    .setDefaultValue(defaults.enableTradeCycling)
                    .setTooltip(Component.translatable("config.mercantile.enableTradeCycling.tooltip"))
                    .setSaveConsumer(v -> config.enableTradeCycling = v)
                    .build());
            cycling.addEntry(entry.startIntField(Component.translatable("config.mercantile.tradeCycleEmeraldCost"), config.tradeCycleEmeraldCost)
                    .setDefaultValue(defaults.tradeCycleEmeraldCost)
                    .setMin(0)
                    .setTooltip(Component.translatable("config.mercantile.tradeCycleEmeraldCost.tooltip"))
                    .setSaveConsumer(v -> config.tradeCycleEmeraldCost = v)
                    .build());

            // --- Reputation ---
            ConfigCategory reputation = builder.getOrCreateCategory(Component.translatable("config.mercantile.category.reputation"));
            reputation.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableReputation"), config.enableReputation)
                    .setDefaultValue(defaults.enableReputation)
                    .setTooltip(Component.translatable("config.mercantile.enableReputation.tooltip"))
                    .setSaveConsumer(v -> config.enableReputation = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.reputationTradeGain"), config.reputationTradeGain)
                    .setDefaultValue(defaults.reputationTradeGain)
                    .setMin(0)
                    .setTooltip(Component.translatable("config.mercantile.reputationTradeGain.tooltip"))
                    .setSaveConsumer(v -> config.reputationTradeGain = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.reputationCureGain"), config.reputationCureGain)
                    .setDefaultValue(defaults.reputationCureGain)
                    .setMin(0)
                    .setTooltip(Component.translatable("config.mercantile.reputationCureGain.tooltip"))
                    .setSaveConsumer(v -> config.reputationCureGain = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.reputationAttackLoss"), config.reputationAttackLoss)
                    .setDefaultValue(defaults.reputationAttackLoss)
                    .setMin(0)
                    .setTooltip(Component.translatable("config.mercantile.reputationAttackLoss.tooltip"))
                    .setSaveConsumer(v -> config.reputationAttackLoss = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.reputationKillLoss"), config.reputationKillLoss)
                    .setDefaultValue(defaults.reputationKillLoss)
                    .setMin(0)
                    .setTooltip(Component.translatable("config.mercantile.reputationKillLoss.tooltip"))
                    .setSaveConsumer(v -> config.reputationKillLoss = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.reputationCycleGain"), config.reputationCycleGain)
                    .setDefaultValue(defaults.reputationCycleGain)
                    .setMin(0)
                    .setTooltip(Component.translatable("config.mercantile.reputationCycleGain.tooltip"))
                    .setSaveConsumer(v -> config.reputationCycleGain = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.reputationDailyCap"), config.reputationDailyCap)
                    .setDefaultValue(defaults.reputationDailyCap)
                    .setMin(1).setMax(50)
                    .setTooltip(Component.translatable("config.mercantile.reputationDailyCap.tooltip"))
                    .setSaveConsumer(v -> config.reputationDailyCap = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.reputationTradesPerGain"), config.reputationTradesPerGain)
                    .setDefaultValue(defaults.reputationTradesPerGain)
                    .setMin(1).setMax(20)
                    .setTooltip(Component.translatable("config.mercantile.reputationTradesPerGain.tooltip"))
                    .setSaveConsumer(v -> config.reputationTradesPerGain = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.reputationDailyMaxTradeRep"), config.reputationDailyMaxTradeRep)
                    .setDefaultValue(defaults.reputationDailyMaxTradeRep)
                    .setMin(1).setMax(10)
                    .setTooltip(Component.translatable("config.mercantile.reputationDailyMaxTradeRep.tooltip"))
                    .setSaveConsumer(v -> config.reputationDailyMaxTradeRep = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.reputationDailyMaxCycleRep"), config.reputationDailyMaxCycleRep)
                    .setDefaultValue(defaults.reputationDailyMaxCycleRep)
                    .setMin(1).setMax(10)
                    .setTooltip(Component.translatable("config.mercantile.reputationDailyMaxCycleRep.tooltip"))
                    .setSaveConsumer(v -> config.reputationDailyMaxCycleRep = v)
                    .build());
            reputation.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableRaidReputation"), config.enableRaidReputation)
                    .setDefaultValue(defaults.enableRaidReputation)
                    .setTooltip(Component.translatable("config.mercantile.enableRaidReputation.tooltip"))
                    .setSaveConsumer(v -> config.enableRaidReputation = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.reputationRaidWinGain"), config.reputationRaidWinGain)
                    .setDefaultValue(defaults.reputationRaidWinGain)
                    .setMin(0)
                    .setTooltip(Component.translatable("config.mercantile.reputationRaidWinGain.tooltip"))
                    .setSaveConsumer(v -> config.reputationRaidWinGain = v)
                    .build());
            reputation.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableWanderingTraderRep"), config.enableWanderingTraderRep)
                    .setDefaultValue(defaults.enableWanderingTraderRep)
                    .setTooltip(Component.translatable("config.mercantile.enableWanderingTraderRep.tooltip"))
                    .setSaveConsumer(v -> config.enableWanderingTraderRep = v)
                    .build());
            reputation.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableGifting"), config.enableGifting)
                    .setDefaultValue(defaults.enableGifting)
                    .setTooltip(Component.translatable("config.mercantile.enableGifting.tooltip"))
                    .setSaveConsumer(v -> config.enableGifting = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.reputationGiftGain"), config.reputationGiftGain)
                    .setDefaultValue(defaults.reputationGiftGain)
                    .setMin(0)
                    .setTooltip(Component.translatable("config.mercantile.reputationGiftGain.tooltip"))
                    .setSaveConsumer(v -> config.reputationGiftGain = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.reputationDailyMaxGiftRep"), config.reputationDailyMaxGiftRep)
                    .setDefaultValue(defaults.reputationDailyMaxGiftRep)
                    .setMin(1).setMax(10)
                    .setTooltip(Component.translatable("config.mercantile.reputationDailyMaxGiftRep.tooltip"))
                    .setSaveConsumer(v -> config.reputationDailyMaxGiftRep = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.reputationNegativeDecayPerDay"), config.reputationNegativeDecayPerDay)
                    .setDefaultValue(defaults.reputationNegativeDecayPerDay)
                    .setMin(0)
                    .setTooltip(Component.translatable("config.mercantile.reputationNegativeDecayPerDay.tooltip"))
                    .setSaveConsumer(v -> config.reputationNegativeDecayPerDay = v)
                    .build());
            reputation.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableGratitudeGifts"), config.enableGratitudeGifts)
                    .setDefaultValue(defaults.enableGratitudeGifts)
                    .setTooltip(Component.translatable("config.mercantile.enableGratitudeGifts.tooltip"))
                    .setSaveConsumer(v -> config.enableGratitudeGifts = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.gratitudeGiftsPerDay"), config.gratitudeGiftsPerDay)
                    .setDefaultValue(defaults.gratitudeGiftsPerDay)
                    .setMin(0).setMax(10)
                    .setTooltip(Component.translatable("config.mercantile.gratitudeGiftsPerDay.tooltip"))
                    .setSaveConsumer(v -> config.gratitudeGiftsPerDay = v)
                    .build());
            reputation.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableNitwitRehab"), config.enableNitwitRehab)
                    .setDefaultValue(defaults.enableNitwitRehab)
                    .setTooltip(Component.translatable("config.mercantile.enableNitwitRehab.tooltip"))
                    .setSaveConsumer(v -> config.enableNitwitRehab = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.nitwitRehabEmeraldCost"), config.nitwitRehabEmeraldCost)
                    .setDefaultValue(defaults.nitwitRehabEmeraldCost)
                    .setMin(0)
                    .setTooltip(Component.translatable("config.mercantile.nitwitRehabEmeraldCost.tooltip"))
                    .setSaveConsumer(v -> config.nitwitRehabEmeraldCost = v)
                    .build());
            reputation.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableContracts"), config.enableContracts)
                    .setDefaultValue(defaults.enableContracts)
                    .setTooltip(Component.translatable("config.mercantile.enableContracts.tooltip"))
                    .setSaveConsumer(v -> config.enableContracts = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.contractOfferChance"), config.contractOfferChance)
                    .setDefaultValue(defaults.contractOfferChance)
                    .setMin(0).setMax(100)
                    .setTooltip(Component.translatable("config.mercantile.contractOfferChance.tooltip"))
                    .setSaveConsumer(v -> config.contractOfferChance = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.contractPaymentScale"), config.contractPaymentScale)
                    .setDefaultValue(defaults.contractPaymentScale)
                    .setMin(0).setMax(1000)
                    .setTooltip(Component.translatable("config.mercantile.contractPaymentScale.tooltip"))
                    .setSaveConsumer(v -> config.contractPaymentScale = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.contractRepGain"), config.contractRepGain)
                    .setDefaultValue(defaults.contractRepGain)
                    .setMin(0)
                    .setTooltip(Component.translatable("config.mercantile.contractRepGain.tooltip"))
                    .setSaveConsumer(v -> config.contractRepGain = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.contractRepPerDay"), config.contractRepPerDay)
                    .setDefaultValue(defaults.contractRepPerDay)
                    .setMin(0).setMax(50)
                    .setTooltip(Component.translatable("config.mercantile.contractRepPerDay.tooltip"))
                    .setSaveConsumer(v -> config.contractRepPerDay = v)
                    .build());
            reputation.addEntry(entry.startIntField(Component.translatable("config.mercantile.contractDeadlineDays"), config.contractDeadlineDays)
                    .setDefaultValue(defaults.contractDeadlineDays)
                    .setMin(1).setMax(30)
                    .setTooltip(Component.translatable("config.mercantile.contractDeadlineDays.tooltip"))
                    .setSaveConsumer(v -> config.contractDeadlineDays = v)
                    .build());

            // --- Follow Mode ---
            ConfigCategory follow = builder.getOrCreateCategory(Component.translatable("config.mercantile.category.follow"));
            follow.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableFollowMode"), config.enableFollowMode)
                    .setDefaultValue(defaults.enableFollowMode)
                    .setTooltip(Component.translatable("config.mercantile.enableFollowMode.tooltip"))
                    .setSaveConsumer(v -> config.enableFollowMode = v)
                    .build());
            follow.addEntry(entry.startIntField(Component.translatable("config.mercantile.maxFollowingVillagers"), config.maxFollowingVillagers)
                    .setDefaultValue(defaults.maxFollowingVillagers)
                    .setMin(1)
                    .setTooltip(Component.translatable("config.mercantile.maxFollowingVillagers.tooltip"))
                    .setSaveConsumer(v -> config.maxFollowingVillagers = v)
                    .build());
            follow.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableSendHome"), config.enableSendHome)
                    .setDefaultValue(defaults.enableSendHome)
                    .setTooltip(Component.translatable("config.mercantile.enableSendHome.tooltip"))
                    .setSaveConsumer(v -> config.enableSendHome = v)
                    .build());

            // --- Pathfinding ---
            ConfigCategory pathfinding = builder.getOrCreateCategory(Component.translatable("config.mercantile.category.pathfinding"));
            pathfinding.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enablePathfindingFixes"), config.enablePathfindingFixes)
                    .setDefaultValue(defaults.enablePathfindingFixes)
                    .setTooltip(Component.translatable("config.mercantile.enablePathfindingFixes.tooltip"))
                    .setSaveConsumer(v -> config.enablePathfindingFixes = v)
                    .build());
            pathfinding.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enablePathfindingDoors"), config.enablePathfindingDoors)
                    .setDefaultValue(defaults.enablePathfindingDoors)
                    .setTooltip(Component.translatable("config.mercantile.enablePathfindingDoors.tooltip"))
                    .setSaveConsumer(v -> config.enablePathfindingDoors = v)
                    .build());
            pathfinding.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enablePathfindingStairs"), config.enablePathfindingStairs)
                    .setDefaultValue(defaults.enablePathfindingStairs)
                    .setTooltip(Component.translatable("config.mercantile.enablePathfindingStairs.tooltip"))
                    .setSaveConsumer(v -> config.enablePathfindingStairs = v)
                    .build());
            pathfinding.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enablePathfindingLadders"), config.enablePathfindingLadders)
                    .setDefaultValue(defaults.enablePathfindingLadders)
                    .setTooltip(Component.translatable("config.mercantile.enablePathfindingLadders.tooltip"))
                    .setSaveConsumer(v -> config.enablePathfindingLadders = v)
                    .build());
            pathfinding.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enablePathfindingWater"), config.enablePathfindingWater)
                    .setDefaultValue(defaults.enablePathfindingWater)
                    .setTooltip(Component.translatable("config.mercantile.enablePathfindingWater.tooltip"))
                    .setSaveConsumer(v -> config.enablePathfindingWater = v)
                    .build());

            // --- Trading ---
            ConfigCategory trading = builder.getOrCreateCategory(Component.translatable("config.mercantile.category.trading"));
            trading.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableBulkTrading"), config.enableBulkTrading)
                    .setDefaultValue(defaults.enableBulkTrading)
                    .setTooltip(Component.translatable("config.mercantile.enableBulkTrading.tooltip"))
                    .setSaveConsumer(v -> config.enableBulkTrading = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableProfessionLock"), config.enableProfessionLock)
                    .setDefaultValue(defaults.enableProfessionLock)
                    .setTooltip(Component.translatable("config.mercantile.enableProfessionLock.tooltip"))
                    .setSaveConsumer(v -> config.enableProfessionLock = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableWorkOrders"), config.enableWorkOrders)
                    .setDefaultValue(defaults.enableWorkOrders)
                    .setTooltip(Component.translatable("config.mercantile.enableWorkOrders.tooltip"))
                    .setSaveConsumer(v -> config.enableWorkOrders = v)
                    .build());
            trading.addEntry(entry.startIntField(Component.translatable("config.mercantile.workOrderEmeraldCost"), config.workOrderEmeraldCost)
                    .setDefaultValue(defaults.workOrderEmeraldCost)
                    .setMin(0)
                    .setTooltip(Component.translatable("config.mercantile.workOrderEmeraldCost.tooltip"))
                    .setSaveConsumer(v -> config.workOrderEmeraldCost = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableHealing"), config.enableHealing)
                    .setDefaultValue(defaults.enableHealing)
                    .setTooltip(Component.translatable("config.mercantile.enableHealing.tooltip"))
                    .setSaveConsumer(v -> config.enableHealing = v)
                    .build());
            trading.addEntry(entry.startFloatField(Component.translatable("config.mercantile.healingMultiplier"), config.healingMultiplier)
                    .setDefaultValue(defaults.healingMultiplier)
                    .setMin(1.0f).setMax(10.0f)
                    .setTooltip(Component.translatable("config.mercantile.healingMultiplier.tooltip"))
                    .setSaveConsumer(v -> config.healingMultiplier = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableRestockIndicator"), config.enableRestockIndicator)
                    .setDefaultValue(defaults.enableRestockIndicator)
                    .setTooltip(Component.translatable("config.mercantile.enableRestockIndicator.tooltip"))
                    .setSaveConsumer(v -> config.enableRestockIndicator = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableDemandTransparency"), config.enableDemandTransparency)
                    .setDefaultValue(defaults.enableDemandTransparency)
                    .setTooltip(Component.translatable("config.mercantile.enableDemandTransparency.tooltip"))
                    .setSaveConsumer(v -> config.enableDemandTransparency = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableTradePinning"), config.enableTradePinning)
                    .setDefaultValue(defaults.enableTradePinning)
                    .setTooltip(Component.translatable("config.mercantile.enableTradePinning.tooltip"))
                    .setSaveConsumer(v -> config.enableTradePinning = v)
                    .build());
            trading.addEntry(entry.startIntField(Component.translatable("config.mercantile.maxPinnedTradesPerPlayer"), config.maxPinnedTradesPerPlayer)
                    .setDefaultValue(defaults.maxPinnedTradesPerPlayer)
                    .setMin(1).setMax(64)
                    .setTooltip(Component.translatable("config.mercantile.maxPinnedTradesPerPlayer.tooltip"))
                    .setSaveConsumer(v -> config.maxPinnedTradesPerPlayer = v)
                    .build());
            trading.addEntry(entry.startIntField(Component.translatable("config.mercantile.pinRestockNotifyRange"), config.pinRestockNotifyRange)
                    .setDefaultValue(defaults.pinRestockNotifyRange)
                    .setMin(8).setMax(256)
                    .setTooltip(Component.translatable("config.mercantile.pinRestockNotifyRange.tooltip"))
                    .setSaveConsumer(v -> config.pinRestockNotifyRange = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableBreedingTooltip"), config.enableBreedingTooltip)
                    .setDefaultValue(defaults.enableBreedingTooltip)
                    .setTooltip(Component.translatable("config.mercantile.enableBreedingTooltip.tooltip"))
                    .setSaveConsumer(v -> config.enableBreedingTooltip = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableBabyFeeding"), config.enableBabyFeeding)
                    .setDefaultValue(defaults.enableBabyFeeding)
                    .setTooltip(Component.translatable("config.mercantile.enableBabyFeeding.tooltip"))
                    .setSaveConsumer(v -> config.enableBabyFeeding = v)
                    .build());
            trading.addEntry(entry.startIntField(Component.translatable("config.mercantile.babyFeedPercentPerFeed"), config.babyFeedPercentPerFeed)
                    .setDefaultValue(defaults.babyFeedPercentPerFeed)
                    .setMin(1).setMax(100)
                    .setTooltip(Component.translatable("config.mercantile.babyFeedPercentPerFeed.tooltip"))
                    .setSaveConsumer(v -> config.babyFeedPercentPerFeed = v)
                    .build());
            trading.addEntry(entry.startIntField(Component.translatable("config.mercantile.babyFeedMaxReductionPercent"), config.babyFeedMaxReductionPercent)
                    .setDefaultValue(defaults.babyFeedMaxReductionPercent)
                    .setMin(0).setMax(100)
                    .setTooltip(Component.translatable("config.mercantile.babyFeedMaxReductionPercent.tooltip"))
                    .setSaveConsumer(v -> config.babyFeedMaxReductionPercent = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableStateIndicators"), config.enableStateIndicators)
                    .setDefaultValue(defaults.enableStateIndicators)
                    .setTooltip(Component.translatable("config.mercantile.enableStateIndicators.tooltip"))
                    .setSaveConsumer(v -> config.enableStateIndicators = v)
                    .build());

            // --- Mood ---
            ConfigCategory mood = builder.getOrCreateCategory(Component.translatable("config.mercantile.category.mood"));
            mood.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableMood"), config.enableMood)
                    .setDefaultValue(defaults.enableMood)
                    .setTooltip(Component.translatable("config.mercantile.enableMood.tooltip"))
                    .setSaveConsumer(v -> config.enableMood = v)
                    .build());
            mood.addEntry(entry.startIntField(Component.translatable("config.mercantile.moodPriceModifierPercent"), config.moodPriceModifierPercent)
                    .setDefaultValue(defaults.moodPriceModifierPercent)
                    .setMin(0).setMax(50)
                    .setTooltip(Component.translatable("config.mercantile.moodPriceModifierPercent.tooltip"))
                    .setSaveConsumer(v -> config.moodPriceModifierPercent = v)
                    .build());
            mood.addEntry(entry.startIntField(Component.translatable("config.mercantile.moodRestockSpeedPercent"), config.moodRestockSpeedPercent)
                    .setDefaultValue(defaults.moodRestockSpeedPercent)
                    .setMin(0).setMax(80)
                    .setTooltip(Component.translatable("config.mercantile.moodRestockSpeedPercent.tooltip"))
                    .setSaveConsumer(v -> config.moodRestockSpeedPercent = v)
                    .build());
            mood.addEntry(entry.startIntField(Component.translatable("config.mercantile.moodRecalcIntervalTicks"), config.moodRecalcIntervalTicks)
                    .setDefaultValue(defaults.moodRecalcIntervalTicks)
                    .setMin(20).setMax(24_000)
                    .setTooltip(Component.translatable("config.mercantile.moodRecalcIntervalTicks.tooltip"))
                    .setSaveConsumer(v -> config.moodRecalcIntervalTicks = v)
                    .build());
            mood.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.moodAmbientParticles"), config.moodAmbientParticles)
                    .setDefaultValue(defaults.moodAmbientParticles)
                    .setTooltip(Component.translatable("config.mercantile.moodAmbientParticles.tooltip"))
                    .setSaveConsumer(v -> config.moodAmbientParticles = v)
                    .build());

            // --- Market Day ---
            ConfigCategory market = builder.getOrCreateCategory(Component.translatable("config.mercantile.category.market"));
            market.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableMarketDay"), config.enableMarketDay)
                    .setDefaultValue(defaults.enableMarketDay)
                    .setTooltip(Component.translatable("config.mercantile.enableMarketDay.tooltip"))
                    .setSaveConsumer(v -> config.enableMarketDay = v)
                    .build());
            market.addEntry(entry.startIntField(Component.translatable("config.mercantile.marketDayIntervalDays"), config.marketDayIntervalDays)
                    .setDefaultValue(defaults.marketDayIntervalDays)
                    .setMin(1).setMax(1_000)
                    .setTooltip(Component.translatable("config.mercantile.marketDayIntervalDays.tooltip"))
                    .setSaveConsumer(v -> config.marketDayIntervalDays = v)
                    .build());
            market.addEntry(entry.startIntField(Component.translatable("config.mercantile.marketDayDiscountPercent"), config.marketDayDiscountPercent)
                    .setDefaultValue(defaults.marketDayDiscountPercent)
                    .setMin(0).setMax(100)
                    .setTooltip(Component.translatable("config.mercantile.marketDayDiscountPercent.tooltip"))
                    .setSaveConsumer(v -> config.marketDayDiscountPercent = v)
                    .build());

            // --- Memorials & Fear ---
            ConfigCategory memorial = builder.getOrCreateCategory(Component.translatable("config.mercantile.category.memorial"));
            memorial.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableMemorials"), config.enableMemorials)
                    .setDefaultValue(defaults.enableMemorials)
                    .setTooltip(Component.translatable("config.mercantile.enableMemorials.tooltip"))
                    .setSaveConsumer(v -> config.enableMemorials = v)
                    .build());
            memorial.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableMourning"), config.enableMourning)
                    .setDefaultValue(defaults.enableMourning)
                    .setTooltip(Component.translatable("config.mercantile.enableMourning.tooltip"))
                    .setSaveConsumer(v -> config.enableMourning = v)
                    .build());
            memorial.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableFearMarkup"), config.enableFearMarkup)
                    .setDefaultValue(defaults.enableFearMarkup)
                    .setTooltip(Component.translatable("config.mercantile.enableFearMarkup.tooltip"))
                    .setSaveConsumer(v -> config.enableFearMarkup = v)
                    .build());
            memorial.addEntry(entry.startIntField(Component.translatable("config.mercantile.fearKillThreshold"), config.fearKillThreshold)
                    .setDefaultValue(defaults.fearKillThreshold)
                    .setMin(1).setMax(20)
                    .setTooltip(Component.translatable("config.mercantile.fearKillThreshold.tooltip"))
                    .setSaveConsumer(v -> config.fearKillThreshold = v)
                    .build());
            memorial.addEntry(entry.startIntField(Component.translatable("config.mercantile.fearKillWindowMinutes"), config.fearKillWindowMinutes)
                    .setDefaultValue(defaults.fearKillWindowMinutes)
                    .setMin(1).setMax(120)
                    .setTooltip(Component.translatable("config.mercantile.fearKillWindowMinutes.tooltip"))
                    .setSaveConsumer(v -> config.fearKillWindowMinutes = v)
                    .build());
            memorial.addEntry(entry.startIntField(Component.translatable("config.mercantile.fearMarkupPercent"), config.fearMarkupPercent)
                    .setDefaultValue(defaults.fearMarkupPercent)
                    .setMin(0).setMax(200)
                    .setTooltip(Component.translatable("config.mercantile.fearMarkupPercent.tooltip"))
                    .setSaveConsumer(v -> config.fearMarkupPercent = v)
                    .build());
            memorial.addEntry(entry.startIntField(Component.translatable("config.mercantile.fearMarkupDurationDays"), config.fearMarkupDurationDays)
                    .setDefaultValue(defaults.fearMarkupDurationDays)
                    .setMin(1).setMax(30)
                    .setTooltip(Component.translatable("config.mercantile.fearMarkupDurationDays.tooltip"))
                    .setSaveConsumer(v -> config.fearMarkupDurationDays = v)
                    .build());

            // --- Sentry Pylon ---
            ConfigCategory pylon = builder.getOrCreateCategory(Component.translatable("config.mercantile.category.pylon"));
            pylon.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableSentryPylon"), config.enableSentryPylon)
                    .setDefaultValue(defaults.enableSentryPylon)
                    .setTooltip(Component.translatable("config.mercantile.enableSentryPylon.tooltip"))
                    .setSaveConsumer(v -> config.enableSentryPylon = v)
                    .build());
            pylon.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enablePylonBellAlarm"), config.enablePylonBellAlarm)
                    .setDefaultValue(defaults.enablePylonBellAlarm)
                    .setTooltip(Component.translatable("config.mercantile.enablePylonBellAlarm.tooltip"))
                    .setSaveConsumer(v -> config.enablePylonBellAlarm = v)
                    .build());
            pylon.addEntry(entry.startIntField(Component.translatable("config.mercantile.pylonDetectionRadius"), config.pylonDetectionRadius)
                    .setDefaultValue(defaults.pylonDetectionRadius)
                    .setMin(8).setMax(128)
                    .setTooltip(Component.translatable("config.mercantile.pylonDetectionRadius.tooltip"))
                    .setSaveConsumer(v -> config.pylonDetectionRadius = v)
                    .build());
            pylon.addEntry(entry.startIntField(Component.translatable("config.mercantile.pylonMaxFuel"), config.pylonMaxFuel)
                    .setDefaultValue(defaults.pylonMaxFuel)
                    .setMin(1)
                    .setTooltip(Component.translatable("config.mercantile.pylonMaxFuel.tooltip"))
                    .setSaveConsumer(v -> config.pylonMaxFuel = v)
                    .build());
            pylon.addEntry(entry.startIntField(Component.translatable("config.mercantile.pylonMaxGolems"), config.pylonMaxGolems)
                    .setDefaultValue(defaults.pylonMaxGolems)
                    .setMin(1)
                    .setTooltip(Component.translatable("config.mercantile.pylonMaxGolems.tooltip"))
                    .setSaveConsumer(v -> config.pylonMaxGolems = v)
                    .build());
            pylon.addEntry(entry.startIntField(Component.translatable("config.mercantile.sentryDespawnSeconds"), config.sentryDespawnSeconds)
                    .setDefaultValue(defaults.sentryDespawnSeconds)
                    .setMin(5)
                    .setTooltip(Component.translatable("config.mercantile.sentryDespawnSeconds.tooltip"))
                    .setSaveConsumer(v -> config.sentryDespawnSeconds = v)
                    .build());
            pylon.addEntry(entry.startIntField(Component.translatable("config.mercantile.pylonTribulationGolemBonusPerTier"), config.pylonTribulationGolemBonusPerTier)
                    .setDefaultValue(defaults.pylonTribulationGolemBonusPerTier)
                    .setMin(0)
                    .setTooltip(Component.translatable("config.mercantile.pylonTribulationGolemBonusPerTier.tooltip"))
                    .setSaveConsumer(v -> config.pylonTribulationGolemBonusPerTier = v)
                    .build());
            pylon.addEntry(entry.startIntField(Component.translatable("config.mercantile.pylonTribulationRadiusBonusPerTier"), config.pylonTribulationRadiusBonusPerTier)
                    .setDefaultValue(defaults.pylonTribulationRadiusBonusPerTier)
                    .setMin(0)
                    .setTooltip(Component.translatable("config.mercantile.pylonTribulationRadiusBonusPerTier.tooltip"))
                    .setSaveConsumer(v -> config.pylonTribulationRadiusBonusPerTier = v)
                    .build());
            pylon.addEntry(entry.startIntField(Component.translatable("config.mercantile.pylonTribulationMaxGolems"), config.pylonTribulationMaxGolems)
                    .setDefaultValue(defaults.pylonTribulationMaxGolems)
                    .setMin(1)
                    .setTooltip(Component.translatable("config.mercantile.pylonTribulationMaxGolems.tooltip"))
                    .setSaveConsumer(v -> config.pylonTribulationMaxGolems = v)
                    .build());

            // --- Client ---
            ConfigCategory client = builder.getOrCreateCategory(Component.translatable("config.mercantile.category.client"));
            client.addEntry(entry.startFloatField(Component.translatable("config.mercantile.villagerSoundVolume"), config.villagerSoundVolume)
                    .setDefaultValue(defaults.villagerSoundVolume)
                    .setMin(0.0f).setMax(1.0f)
                    .setTooltip(Component.translatable("config.mercantile.villagerSoundVolume.tooltip"))
                    .setSaveConsumer(v -> config.villagerSoundVolume = v)
                    .build());
            client.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableWorkstationVis"), config.enableWorkstationVis)
                    .setDefaultValue(defaults.enableWorkstationVis)
                    .setTooltip(Component.translatable("config.mercantile.enableWorkstationVis.tooltip"))
                    .setSaveConsumer(v -> config.enableWorkstationVis = v)
                    .build());
            client.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableBellRadiusVis"), config.enableBellRadiusVis)
                    .setDefaultValue(defaults.enableBellRadiusVis)
                    .setTooltip(Component.translatable("config.mercantile.enableBellRadiusVis.tooltip"))
                    .setSaveConsumer(v -> config.enableBellRadiusVis = v)
                    .build());
            client.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableInfoPanel"), config.enableInfoPanel)
                    .setDefaultValue(defaults.enableInfoPanel)
                    .setTooltip(Component.translatable("config.mercantile.enableInfoPanel.tooltip"))
                    .setSaveConsumer(v -> config.enableInfoPanel = v)
                    .build());
            client.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableReputationHud"), config.enableReputationHud)
                    .setDefaultValue(defaults.enableReputationHud)
                    .setTooltip(Component.translatable("config.mercantile.enableReputationHud.tooltip"))
                    .setSaveConsumer(v -> config.enableReputationHud = v)
                    .build());
            client.addEntry(entry.startBooleanToggle(Component.translatable("config.mercantile.enableTierChangeMessages"), config.enableTierChangeMessages)
                    .setDefaultValue(defaults.enableTierChangeMessages)
                    .setTooltip(Component.translatable("config.mercantile.enableTierChangeMessages.tooltip"))
                    .setSaveConsumer(v -> config.enableTierChangeMessages = v)
                    .build());
            client.addEntry(entry.startEnumSelector(Component.translatable("config.mercantile.hudAnchor"),
                            MercantileConfig.Anchor.class, config.hudAnchor)
                    .setDefaultValue(defaults.hudAnchor)
                    .setEnumNameProvider(v -> Component.translatable(
                            "config.mercantile.hudAnchor." + v.name().toLowerCase(Locale.ROOT)))
                    .setTooltip(Component.translatable("config.mercantile.hudAnchor.tooltip"))
                    .setSaveConsumer(v -> config.hudAnchor = v)
                    .build());
            client.addEntry(entry.startIntField(Component.translatable("config.mercantile.hudOffsetX"), config.hudOffsetX)
                    .setDefaultValue(defaults.hudOffsetX)
                    .setMin(0).setMax(10_000)
                    .setTooltip(Component.translatable("config.mercantile.hudOffsetX.tooltip"))
                    .setSaveConsumer(v -> config.hudOffsetX = v)
                    .build());
            client.addEntry(entry.startIntField(Component.translatable("config.mercantile.hudOffsetY"), config.hudOffsetY)
                    .setDefaultValue(defaults.hudOffsetY)
                    .setMin(0).setMax(10_000)
                    .setTooltip(Component.translatable("config.mercantile.hudOffsetY.tooltip"))
                    .setSaveConsumer(v -> config.hudOffsetY = v)
                    .build());

            return builder.build();
        };
    }
}
