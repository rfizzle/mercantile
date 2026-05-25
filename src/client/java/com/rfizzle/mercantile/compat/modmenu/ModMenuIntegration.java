package com.rfizzle.mercantile.compat.modmenu;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

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
            trading.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableBreedingTooltip"), config.enableBreedingTooltip)
                    .setDefaultValue(defaults.enableBreedingTooltip)
                    .setTooltip(Component.translatable("mercantile.config.enableBreedingTooltip.tooltip"))
                    .setSaveConsumer(v -> config.enableBreedingTooltip = v)
                    .build());
            trading.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableStateIndicators"), config.enableStateIndicators)
                    .setDefaultValue(defaults.enableStateIndicators)
                    .setTooltip(Component.translatable("mercantile.config.enableStateIndicators.tooltip"))
                    .setSaveConsumer(v -> config.enableStateIndicators = v)
                    .build());

            // --- Sentry Pylon ---
            ConfigCategory pylon = builder.getOrCreateCategory(Component.translatable("mercantile.config.category.pylon"));
            pylon.addEntry(entry.startBooleanToggle(Component.translatable("mercantile.config.enableSentryPylon"), config.enableSentryPylon)
                    .setDefaultValue(defaults.enableSentryPylon)
                    .setTooltip(Component.translatable("mercantile.config.enableSentryPylon.tooltip"))
                    .setSaveConsumer(v -> config.enableSentryPylon = v)
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

            return builder.build();
        };
    }
}
