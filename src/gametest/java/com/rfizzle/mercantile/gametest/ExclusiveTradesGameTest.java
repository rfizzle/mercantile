package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;

import java.util.List;
import java.util.Map;

public class ExclusiveTradesGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void masonTierGating(GameTestHelper helper) {
        // Setup controlled trades for Mason
        // 1. TRUSTED trade: 1 Emerald -> 16 Calcite
        // 2. HONORED trade: 1 Emerald -> 16 Tuff
        ExclusiveTradesManager.ExclusiveTrade trustedTrade = new ExclusiveTradesManager.ExclusiveTrade(
                new ItemCost(Items.EMERALD, 1),
                null,
                new ItemStack(Items.CALCITE, 16),
                12, 1, 0.05f,
                ReputationTier.TRUSTED.minScore()
        );

        ExclusiveTradesManager.ExclusiveTrade honoredTrade = new ExclusiveTradesManager.ExclusiveTrade(
                new ItemCost(Items.EMERALD, 1),
                null,
                new ItemStack(Items.TUFF, 16),
                12, 1, 0.05f,
                ReputationTier.HONORED.minScore()
        );

        // Explicitly use EMPTY list for cross-profession to avoid picking up _mercantile.json
        ExclusiveTradesManager.setSnapshotsForTesting(
                Map.of("mason", List.of(trustedTrade, honoredTrade)),
                java.util.Collections.emptyList()
        );

        // Ensure reputation is enabled for the test
        boolean savedEnableReputation = com.rfizzle.mercantile.config.MercantileConfig.get().enableReputation;
        com.rfizzle.mercantile.config.MercantileConfig.get().enableReputation = true;

        try {
            Villager mason = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
            mason.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.MASON, 1));

            // 1. Score below TRUSTED (e.g., LIKED max score 299 or just below TRUSTED min score 300)
            int scoreBelowTrusted = ReputationTier.TRUSTED.minScore() - 1;
            int baseOfferCount = mason.getOffers().size();
            ExclusiveTradesManager.injectOffers(mason, scoreBelowTrusted);
            helper.assertTrue(mason.getOffers().size() == baseOfferCount,
                    "Expected " + baseOfferCount + " trades at score " + scoreBelowTrusted + ", got " + mason.getOffers().size());

            // 2. Score at TRUSTED (300)
            ExclusiveTradesManager.stripInjectedOffers(mason);
            ExclusiveTradesManager.injectOffers(mason, ReputationTier.TRUSTED.minScore());
            helper.assertTrue(mason.getOffers().size() == baseOfferCount + 1,
                    "Expected " + (baseOfferCount + 1) + " trades at TRUSTED score, got " + mason.getOffers().size());
            // Injected trades are added to the END of the list
            helper.assertTrue(mason.getOffers().get(mason.getOffers().size() - 1).getResult().getItem() == Items.CALCITE,
                    "Expected CALCITE trade at TRUSTED score");

            // 3. Score at HONORED (1000)
            ExclusiveTradesManager.stripInjectedOffers(mason);
            ExclusiveTradesManager.injectOffers(mason, ReputationTier.HONORED.minScore());
            helper.assertTrue(mason.getOffers().size() == baseOfferCount + 2,
                    "Expected " + (baseOfferCount + 2) + " trades at HONORED score, got " + mason.getOffers().size());

            helper.succeed();
        } finally {
            // Clean up to avoid poisoning other tests
            ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
            com.rfizzle.mercantile.config.MercantileConfig.get().enableReputation = savedEnableReputation;
        }
    }
}
