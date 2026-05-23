package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.MercantileVillagerData;
import com.rfizzle.mercantile.data.VillagerNameManager;
import com.rfizzle.mercantile.data.VillagerPickupHelper;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VillagerNameGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void villagerGetsNameOnSpawn(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);

        helper.assertTrue(villager.hasCustomName(), "Villager should have a name after spawn");
        helper.assertTrue(villager.isCustomNameVisible(), "Villager name should be visible");

        String name = villager.getCustomName().getString();
        helper.assertFalse(name.isEmpty(), "Villager name should not be empty");

        MercantileVillagerData data =villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        helper.assertTrue(data.isNameAssigned(), "nameAssigned should be true after spawn");

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void nametagNameNotOverwritten(GameTestHelper helper) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(villager != null, "Villager should be created");
        villager.setCustomName(Component.literal("PlayerName"));
        villager.moveTo(helper.absolutePos(new BlockPos(0, 1, 0)), 0, 0);
        helper.getLevel().addFreshEntity(villager);

        helper.assertTrue("PlayerName".equals(villager.getCustomName().getString()),
                "Nametag name should not be overwritten");

        MercantileVillagerData data =villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        helper.assertTrue(data.isNameAssigned(),
                "nameAssigned should be set even for nametag-named villagers");

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void namePersistsThroughNbt(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        String originalName = villager.getCustomName().getString();

        CompoundTag saved = new CompoundTag();
        villager.saveWithoutId(saved);
        villager.discard();

        Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(loaded != null, "Loaded villager should be created");
        loaded.load(saved);

        helper.assertTrue(loaded.hasCustomName(), "Name should persist through NBT save/load");
        helper.assertTrue(originalName.equals(loaded.getCustomName().getString()),
                "Name should be identical after save/load");
        helper.assertTrue(loaded.isCustomNameVisible(), "Name visibility should persist");

        MercantileVillagerData data =loaded.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        helper.assertTrue(data.isNameAssigned(), "nameAssigned should persist through NBT");

        loaded.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void customNamedPickupShowsProfession(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(villager.getVillagerData()
                .setProfession(VillagerProfession.LIBRARIAN)
                .setLevel(2));
        villager.setCustomName(Component.literal("Bob"));

        ItemStack head = VillagerPickupHelper.createHeadItem(villager);
        Component name = head.get(DataComponents.CUSTOM_NAME);
        helper.assertTrue(name != null, "Head item should have a display name");

        String rendered = name.getString();
        helper.assertTrue(rendered.contains("Bob"),
                "Display name should contain custom name; got: " + rendered);
        helper.assertTrue(rendered.contains("Librarian"),
                "Display name should contain profession; got: " + rendered);

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void nearbyVillagersGetUniqueNames(GameTestHelper helper) {
        int count = 6;

        // Look up the pool the dedup logic will draw from so the assertion exercises
        // dedup deterministically rather than depending on which biome the test region
        // happens to roll. If the pool can't supply `count` unique names, the dedup
        // logic itself is allowed to repeat — skip the determinism assertion.
        BlockPos spawnAbs = helper.absolutePos(new BlockPos(0, 1, 0));
        Holder<Biome> biomeHolder = helper.getLevel().getBiome(spawnAbs);
        ResourceKey<Biome> biomeKey = biomeHolder.unwrapKey().orElse(null);
        String category = biomeKey != null ? VillagerNameManager.getCategory(biomeKey) : "fallback";
        List<String> pool = VillagerNameManager.getNamePool(category);
        if (pool.size() < count) {
            pool = VillagerNameManager.getNamePool("fallback");
        }
        helper.assertTrue(pool.size() >= count,
                "Test prerequisite: a name pool with >= " + count + " names is needed "
                        + "(category=" + category + ", size=" + pool.size() + ")");
        Set<String> poolNames = new HashSet<>(pool);

        Villager[] villagers = new Villager[count];
        for (int i = 0; i < count; i++) {
            villagers[i] = helper.spawn(EntityType.VILLAGER, i % 3, 1, i / 3);
        }

        Set<String> names = new HashSet<>();
        for (Villager v : villagers) {
            helper.assertTrue(v.hasCustomName(), "Each villager should have a name");
            names.add(v.getCustomName().getString());
        }
        // All assigned names must come from a known pool — if they do, and the pool
        // has >= count entries, dedup MUST produce `count` unique names.
        boolean allFromKnownPool = names.stream().allMatch(poolNames::contains)
                || names.stream().allMatch(new HashSet<>(VillagerNameManager.getNamePool("fallback"))::contains);
        if (allFromKnownPool) {
            helper.assertTrue(names.size() == count,
                    "Nearby villagers should get distinct names from a pool of size "
                            + pool.size() + "; got " + names.size() + " unique of " + count);
        } else {
            helper.assertTrue(names.size() >= count - 1,
                    "Nearby villagers should get distinct names; got "
                            + names.size() + " unique of " + count);
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void nameNotReassignedOnSubsequentLoad(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        String firstName = villager.getCustomName().getString();

        CompoundTag saved = new CompoundTag();
        villager.saveWithoutId(saved);
        villager.discard();

        Villager reloaded = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(reloaded != null, "Reloaded villager should be created");
        reloaded.load(saved);
        reloaded.moveTo(helper.absolutePos(new BlockPos(0, 1, 0)), 0, 0);
        helper.getLevel().addFreshEntity(reloaded);

        helper.assertTrue(firstName.equals(reloaded.getCustomName().getString()),
                "Name should not change on re-load into world");

        reloaded.discard();
        helper.succeed();
    }
}
