package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager.EnchantRandomlySpec;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager.EnchantmentSpec;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager.ExclusiveTrade;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager.LevelPolicy;
import com.rfizzle.mercantile.trade.OfferIdentityHash;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
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
import java.util.Optional;

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

    @GameTest(template = EMPTY_STRUCTURE)
    public void generativeBookResolvesEnchantment(GameTestHelper helper) {
        // A generative enchant_randomly output rolls one enchantment from the tag onto the book at the
        // policy level. IN_ENCHANTING_TABLE is a populated vanilla tag, so the draw always resolves.
        RegistryAccess registries = helper.getLevel().registryAccess();
        RandomSource random = helper.getLevel().getRandom();

        ExclusiveTrade book = new ExclusiveTrade(
                new ItemCost(Items.EMERALD, 30), null, new ItemStack(Items.ENCHANTED_BOOK),
                3, 5, 0.05f, ReputationTier.NEUTRAL.minScore(),
                List.of(), List.of(), Optional.empty(),
                new EnchantRandomlySpec(EnchantmentTags.IN_ENCHANTING_TABLE, LevelPolicy.MAX));

        MerchantOffer offer = book.createOffer(registries, random);
        ItemStack result = offer.getResult();
        helper.assertTrue(result.is(Items.ENCHANTED_BOOK), "Expected an enchanted book result");

        ItemEnchantments stored = result.get(DataComponents.STORED_ENCHANTMENTS);
        helper.assertTrue(stored != null && stored.size() == 1,
                "Expected exactly one rolled stored enchantment");
        helper.assertTrue(result.get(DataComponents.ENCHANTMENTS) == null
                        || result.get(DataComponents.ENCHANTMENTS).isEmpty(),
                "A rolled book must not populate the gear ENCHANTMENTS component");

        // The rolled enchantment belongs to the tag and sits at MAX level per the policy.
        var lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
        HolderSet.Named<Enchantment> pool = lookup.getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE);
        Holder<Enchantment> rolled = stored.keySet().iterator().next();
        helper.assertTrue(pool.contains(rolled), "Rolled enchantment must come from the declared tag");
        helper.assertTrue(stored.getLevel(rolled) == rolled.value().getMaxLevel(),
                "MAX policy must store the enchantment at its maximum level");

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void generativeBookEmptyTagYieldsPlainBook(GameTestHelper helper) {
        // An unresolved/empty enchantment tag warns and leaves a plain book rather than failing the offer.
        RegistryAccess registries = helper.getLevel().registryAccess();
        RandomSource random = helper.getLevel().getRandom();

        TagKey<Enchantment> missing = TagKey.create(Registries.ENCHANTMENT,
                Mercantile.id("nonexistent_test_tag"));
        ExclusiveTrade book = new ExclusiveTrade(
                new ItemCost(Items.EMERALD, 30), null, new ItemStack(Items.ENCHANTED_BOOK),
                3, 5, 0.05f, ReputationTier.NEUTRAL.minScore(),
                List.of(), List.of(), Optional.empty(),
                new EnchantRandomlySpec(missing, LevelPolicy.MID));

        MerchantOffer offer = book.createOffer(registries, random);
        ItemStack result = offer.getResult();
        helper.assertTrue(result.is(Items.ENCHANTED_BOOK), "Expected a plain enchanted book");
        ItemEnchantments stored = result.get(DataComponents.STORED_ENCHANTMENTS);
        helper.assertTrue(stored == null || stored.isEmpty(),
                "An empty tag must leave the book without stored enchantments");

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void generativeBookRollIsStableAcrossReopens(GameTestHelper helper) {
        // The rolled book is persisted on the villager, so re-injecting (as happens on every trade-screen
        // open) restores the same enchantment and the same identity hash rather than re-rolling.
        ExclusiveTrade book = new ExclusiveTrade(
                new ItemCost(Items.EMERALD, 30), null, new ItemStack(Items.ENCHANTED_BOOK),
                3, 5, 0.05f, ReputationTier.NEUTRAL.minScore(),
                List.of(), List.of(), Optional.empty(),
                new EnchantRandomlySpec(EnchantmentTags.IN_ENCHANTING_TABLE, LevelPolicy.MID));

        ExclusiveTradesManager.setSnapshotsForTesting(
                Map.of("librarian", List.of(book)), List.of());

        boolean savedEnableReputation = com.rfizzle.mercantile.config.MercantileConfig.get().enableReputation;
        com.rfizzle.mercantile.config.MercantileConfig.get().enableReputation = true;
        try {
            Villager librarian = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
            librarian.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.LIBRARIAN, 1));

            ExclusiveTradesManager.injectOffers(librarian, ReputationTier.HONORED.minScore());
            MerchantOffer first = librarian.getOffers().get(librarian.getOffers().size() - 1);
            ItemEnchantments firstStored = first.getResult().get(DataComponents.STORED_ENCHANTMENTS);
            helper.assertTrue(firstStored != null && !firstStored.isEmpty(), "Expected a rolled enchantment");
            String firstHash = OfferIdentityHash.compute(first);

            // Simulate a second trade-screen open: strip then re-inject.
            ExclusiveTradesManager.stripInjectedOffers(librarian);
            ExclusiveTradesManager.injectOffers(librarian, ReputationTier.HONORED.minScore());
            MerchantOffer second = librarian.getOffers().get(librarian.getOffers().size() - 1);
            String secondHash = OfferIdentityHash.compute(second);

            helper.assertTrue(firstHash.equals(secondHash),
                    "Persisted generative roll must yield a stable identity hash across re-opens (got "
                            + firstHash + " then " + secondHash + ")");

            helper.succeed();
        } finally {
            ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
            com.rfizzle.mercantile.config.MercantileConfig.get().enableReputation = savedEnableReputation;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void advancementGatedTradeHiddenWithoutPlayer(GameTestHelper helper) {
        // The player-less injectOffers overload cannot verify a requires_advancement gate, so an
        // advancement-gated trade is withheld while an ungated sibling is still injected.
        ExclusiveTrade gated = new ExclusiveTrade(
                new ItemCost(Items.EMERALD, 20), null, new ItemStack(Items.DRAGON_BREATH),
                4, 1, 0.05f, ReputationTier.TRUSTED.minScore(),
                List.of(), List.of(),
                Optional.of(net.minecraft.resources.ResourceLocation.parse("minecraft:end/kill_dragon")),
                null);
        ExclusiveTrade ungated = new ExclusiveTrade(
                new ItemCost(Items.EMERALD, 24), null, new ItemStack(Items.PAPER),
                4, 1, 0.05f, ReputationTier.TRUSTED.minScore());

        ExclusiveTradesManager.setSnapshotsForTesting(
                Map.of("librarian", List.of(gated, ungated)), List.of());

        boolean savedEnableReputation = com.rfizzle.mercantile.config.MercantileConfig.get().enableReputation;
        com.rfizzle.mercantile.config.MercantileConfig.get().enableReputation = true;
        try {
            Villager librarian = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
            librarian.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.LIBRARIAN, 1));

            ExclusiveTradesManager.injectOffers(librarian, ReputationTier.HONORED.minScore());

            boolean hasDragonBreath = librarian.getOffers().stream()
                    .anyMatch(o -> o.getResult().is(Items.DRAGON_BREATH));
            boolean hasPaper = librarian.getOffers().stream()
                    .anyMatch(o -> o.getResult().is(Items.PAPER));
            helper.assertFalse(hasDragonBreath,
                    "Advancement-gated trade must be withheld when no player context is available");
            helper.assertTrue(hasPaper, "Ungated sibling trade must still be injected");

            helper.succeed();
        } finally {
            ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
            com.rfizzle.mercantile.config.MercantileConfig.get().enableReputation = savedEnableReputation;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void advancementGatedTradeGrantedWithPlayer(GameTestHelper helper) {
        // The player-aware injectOffers overload can verify a requires_advancement gate: with the
        // advancement granted, the gated trade is injected alongside its ungated sibling.
        ExclusiveTrade gated = new ExclusiveTrade(
                new ItemCost(Items.EMERALD, 20), null, new ItemStack(Items.DRAGON_BREATH),
                4, 1, 0.05f, ReputationTier.TRUSTED.minScore(),
                List.of(), List.of(),
                Optional.of(net.minecraft.resources.ResourceLocation.parse("minecraft:end/kill_dragon")),
                null);
        ExclusiveTrade ungated = new ExclusiveTrade(
                new ItemCost(Items.EMERALD, 24), null, new ItemStack(Items.PAPER),
                4, 1, 0.05f, ReputationTier.TRUSTED.minScore());

        ExclusiveTradesManager.setSnapshotsForTesting(
                Map.of("librarian", List.of(gated, ungated)), List.of());

        boolean savedEnableReputation = com.rfizzle.mercantile.config.MercantileConfig.get().enableReputation;
        com.rfizzle.mercantile.config.MercantileConfig.get().enableReputation = true;
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            net.minecraft.advancements.AdvancementHolder dragon = player.server.getAdvancements()
                    .get(net.minecraft.resources.ResourceLocation.parse("minecraft:end/kill_dragon"));
            helper.assertTrue(dragon != null, "Vanilla end/kill_dragon advancement should be loaded");
            player.getAdvancements().award(dragon, "killed_dragon");
            helper.assertTrue(player.getAdvancements().getOrStartProgress(dragon).isDone(),
                    "kill_dragon advancement should be complete after awarding its criterion");

            Villager librarian = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
            librarian.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.LIBRARIAN, 1));

            ExclusiveTradesManager.injectOffers(librarian, player, ReputationTier.HONORED.minScore());

            boolean hasDragonBreath = librarian.getOffers().stream()
                    .anyMatch(o -> o.getResult().is(Items.DRAGON_BREATH));
            boolean hasPaper = librarian.getOffers().stream()
                    .anyMatch(o -> o.getResult().is(Items.PAPER));
            helper.assertTrue(hasDragonBreath,
                    "Advancement-gated trade must be injected when the player holds the advancement");
            helper.assertTrue(hasPaper, "Ungated sibling trade must also be injected");

            librarian.discard();
            helper.succeed();
        } finally {
            player.discard();
            ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
            com.rfizzle.mercantile.config.MercantileConfig.get().enableReputation = savedEnableReputation;
        }
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
