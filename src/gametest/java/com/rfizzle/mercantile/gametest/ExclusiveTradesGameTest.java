package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager.EnchantmentSpec;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager.ExclusiveTrade;
import com.rfizzle.mercantile.trade.OfferIdentityHash;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
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

    @GameTest(template = EMPTY_STRUCTURE)
    public void enchantedGearOutputRoundTrips(GameTestHelper helper) {
        // Sharpness V + Unbreaking III diamond sword as an honored toolsmith-style reward.
        ExclusiveTrade enchantedSword = new ExclusiveTrade(
                new ItemCost(Items.EMERALD, 64), null,
                new ItemStack(Items.DIAMOND_SWORD), 1, 1, 0.05f,
                ReputationTier.HONORED.minScore(),
                List.of(new EnchantmentSpec("minecraft:sharpness", 5),
                        new EnchantmentSpec("minecraft:unbreaking", 3)),
                List.of());

        ExclusiveTradesManager.setSnapshotsForTesting(
                Map.of("weaponsmith", List.of(enchantedSword)),
                java.util.Collections.emptyList());

        boolean savedEnableReputation = com.rfizzle.mercantile.config.MercantileConfig.get().enableReputation;
        com.rfizzle.mercantile.config.MercantileConfig.get().enableReputation = true;

        try {
            Villager weaponsmith = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
            weaponsmith.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.WEAPONSMITH, 1));

            ExclusiveTradesManager.injectOffers(weaponsmith, ReputationTier.HONORED.minScore());

            MerchantOffer injected = weaponsmith.getOffers().get(weaponsmith.getOffers().size() - 1);
            helper.assertTrue(injected.getResult().is(Items.DIAMOND_SWORD), "Expected diamond sword reward");

            ItemEnchantments enchantments = injected.getResult().get(DataComponents.ENCHANTMENTS);
            helper.assertTrue(enchantments != null && !enchantments.isEmpty(),
                    "Expected ENCHANTMENTS component on the result");

            RegistryAccess registries = weaponsmith.level().registryAccess();
            var lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> sharpness = lookup.getOrThrow(Enchantments.SHARPNESS);
            Holder<Enchantment> unbreaking = lookup.getOrThrow(Enchantments.UNBREAKING);
            helper.assertTrue(enchantments.getLevel(sharpness) == 5, "Expected Sharpness V");
            helper.assertTrue(enchantments.getLevel(unbreaking) == 3, "Expected Unbreaking III");

            helper.succeed();
        } finally {
            ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
            com.rfizzle.mercantile.config.MercantileConfig.get().enableReputation = savedEnableReputation;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void storedEnchantmentBookOutputRoundTrips(GameTestHelper helper) {
        // stored_enchantments writes onto the book's STORED_ENCHANTMENTS, not ENCHANTMENTS.
        ExclusiveTrade mendingBook = new ExclusiveTrade(
                new ItemCost(Items.EMERALD, 32), null,
                new ItemStack(Items.ENCHANTED_BOOK), 1, 1, 0.05f,
                ReputationTier.HONORED.minScore(),
                List.of(),
                List.of(new EnchantmentSpec("minecraft:mending", 1)));

        ExclusiveTradesManager.setSnapshotsForTesting(
                Map.of("librarian", List.of(mendingBook)),
                java.util.Collections.emptyList());

        boolean savedEnableReputation = com.rfizzle.mercantile.config.MercantileConfig.get().enableReputation;
        com.rfizzle.mercantile.config.MercantileConfig.get().enableReputation = true;

        try {
            Villager librarian = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
            librarian.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.LIBRARIAN, 1));

            ExclusiveTradesManager.injectOffers(librarian, ReputationTier.HONORED.minScore());

            MerchantOffer injected = librarian.getOffers().get(librarian.getOffers().size() - 1);
            helper.assertTrue(injected.getResult().is(Items.ENCHANTED_BOOK), "Expected enchanted book reward");

            ItemEnchantments stored = injected.getResult().get(DataComponents.STORED_ENCHANTMENTS);
            helper.assertTrue(stored != null && !stored.isEmpty(),
                    "Expected STORED_ENCHANTMENTS component on the book");
            helper.assertTrue(injected.getResult().get(DataComponents.ENCHANTMENTS) == null
                            || injected.getResult().get(DataComponents.ENCHANTMENTS).isEmpty(),
                    "stored_enchantments must not populate the gear ENCHANTMENTS component");

            var lookup = librarian.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> mending = lookup.getOrThrow(Enchantments.MENDING);
            helper.assertTrue(stored.getLevel(mending) == 1, "Expected Mending I stored on the book");

            helper.succeed();
        } finally {
            ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
            com.rfizzle.mercantile.config.MercantileConfig.get().enableReputation = savedEnableReputation;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void enchantmentLevelChangesOfferHash(GameTestHelper helper) {
        // Two trades identical except enchantment level must produce distinct lock hashes (no collision),
        // while a component-free offer hashes identically with or without registry resolution.
        RegistryAccess registries = helper.getLevel().registryAccess();

        ExclusiveTrade sharpness4 = new ExclusiveTrade(
                new ItemCost(Items.EMERALD, 64), null, new ItemStack(Items.DIAMOND_SWORD),
                1, 1, 0.05f, ReputationTier.HONORED.minScore(),
                List.of(new EnchantmentSpec("minecraft:sharpness", 4)), List.of());
        ExclusiveTrade sharpness5 = new ExclusiveTrade(
                new ItemCost(Items.EMERALD, 64), null, new ItemStack(Items.DIAMOND_SWORD),
                1, 1, 0.05f, ReputationTier.HONORED.minScore(),
                List.of(new EnchantmentSpec("minecraft:sharpness", 5)), List.of());

        String hash4 = OfferIdentityHash.compute(sharpness4.createOffer(registries));
        String hash5 = OfferIdentityHash.compute(sharpness5.createOffer(registries));
        helper.assertFalse(hash4.equals(hash5),
                "Different enchant levels must yield distinct hashes (got " + hash4 + " for both)");

        ExclusiveTrade plain = new ExclusiveTrade(
                new ItemCost(Items.EMERALD, 64), null, new ItemStack(Items.DIAMOND_SWORD),
                1, 1, 0.05f, ReputationTier.HONORED.minScore());
        helper.assertTrue(
                OfferIdentityHash.compute(plain.createOffer(registries))
                        .equals(OfferIdentityHash.compute(plain.createOffer())),
                "Component-free offer must hash identically with or without registry resolution");

        helper.succeed();
    }

    // ---- Fabric resource-condition gating (data/mercantile/exclusive_trades) ----

    /** Clearly fictional mod ID for the "condition fails" branch — must never be a loaded mod. */
    private static final String ABSENT_MOD_ID = "mercantile_test_absent_xxxxxx";

    @GameTest(template = EMPTY_STRUCTURE)
    public void fileConditionPresentLoadsTrades(GameTestHelper helper) {
        String json = """
                {
                  "fabric:load_conditions": [
                    { "condition": "fabric:all_mods_loaded", "values": ["minecraft"] }
                  ],
                  "min_tier": "trusted",
                  "trades": [
                    { "input_1": { "item": "minecraft:emerald" },
                      "output": { "item": "minecraft:calcite", "count": 16 } }
                  ]
                }
                """;
        try {
            ExclusiveTradesManager.loadTrades(stubManager(Map.of("mason", json)));
            List<ExclusiveTrade> mason = ExclusiveTradesManager.professionTradesSnapshot().get("mason");
            helper.assertTrue(mason != null && mason.size() == 1,
                    "Expected the mason trade to load when 'minecraft' is present");
            helper.succeed();
        } finally {
            ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fileConditionAbsentSkipsFile(GameTestHelper helper) {
        String json = """
                {
                  "fabric:load_conditions": [
                    { "condition": "fabric:all_mods_loaded", "values": ["%s"] }
                  ],
                  "min_tier": "trusted",
                  "trades": [
                    { "input_1": { "item": "minecraft:emerald" },
                      "output": { "item": "minecraft:calcite", "count": 16 } }
                  ]
                }
                """.formatted(ABSENT_MOD_ID);
        try {
            ExclusiveTradesManager.loadTrades(stubManager(Map.of("mason", json)));
            List<ExclusiveTrade> mason = ExclusiveTradesManager.professionTradesSnapshot().get("mason");
            helper.assertTrue(mason == null || mason.isEmpty(),
                    "Expected no mason trades when the gating mod is absent");
            helper.succeed();
        } finally {
            ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void perTradeConditionGatesSingleEntry(GameTestHelper helper) {
        // Two trades in one file: an unconditioned one and one gated on an absent mod.
        // Only the unconditioned trade should survive.
        String json = """
                {
                  "min_tier": "trusted",
                  "trades": [
                    { "input_1": { "item": "minecraft:emerald" },
                      "output": { "item": "minecraft:calcite", "count": 16 } },
                    { "fabric:load_conditions": [
                        { "condition": "fabric:all_mods_loaded", "values": ["%s"] } ],
                      "input_1": { "item": "minecraft:emerald" },
                      "output": { "item": "minecraft:tuff", "count": 16 } }
                  ]
                }
                """.formatted(ABSENT_MOD_ID);
        try {
            ExclusiveTradesManager.loadTrades(stubManager(Map.of("mason", json)));
            List<ExclusiveTrade> mason = ExclusiveTradesManager.professionTradesSnapshot().get("mason");
            helper.assertTrue(mason != null && mason.size() == 1,
                    "Expected only the unconditioned trade to load");
            helper.assertTrue(mason.get(0).output().is(Items.CALCITE),
                    "Expected the surviving trade to be the unconditioned calcite trade");
            helper.succeed();
        } finally {
            ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
        }
    }

    /**
     * Minimal in-memory {@link ResourceManager} that serves the given JSON strings as
     * {@code exclusive_trades/<profession>.json} resources. Only {@code listResourceStacks} is wired —
     * the seam {@code loadTrades} actually uses; every other method throws.
     */
    private static ResourceManager stubManager(Map<String, String> filesByProfession) {
        Map<ResourceLocation, List<Resource>> stacks = new java.util.HashMap<>();
        filesByProfession.forEach((profession, json) -> {
            ResourceLocation id = Mercantile.id("exclusive_trades/" + profession + ".json");
            Resource resource = new Resource(null,
                    () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            stacks.put(id, List.of(resource));
        });
        return new ResourceManager() {
            @Override
            public Map<ResourceLocation, List<Resource>> listResourceStacks(
                    String path, java.util.function.Predicate<ResourceLocation> filter) {
                Map<ResourceLocation, List<Resource>> result = new java.util.HashMap<>();
                stacks.forEach((id, list) -> {
                    if (id.getPath().startsWith(path + "/") && filter.test(id)) {
                        result.put(id, list);
                    }
                });
                return result;
            }

            @Override
            public java.util.Set<String> getNamespaces() {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Resource> getResourceStack(ResourceLocation id) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Map<ResourceLocation, Resource> listResources(
                    String path, java.util.function.Predicate<ResourceLocation> filter) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.stream.Stream<net.minecraft.server.packs.PackResources> listPacks() {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.Optional<Resource> getResource(ResourceLocation id) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
