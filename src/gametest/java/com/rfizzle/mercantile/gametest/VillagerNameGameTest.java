package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.MercantileVillagerData;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

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
