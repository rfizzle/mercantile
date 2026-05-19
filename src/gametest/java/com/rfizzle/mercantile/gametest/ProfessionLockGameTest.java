package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.VillagerData;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

public class ProfessionLockGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void lockOnFirstTrade(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));

        VillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        helper.assertFalse(data.isProfessionLocked(), "Should not be locked before any trade");

        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.WHEAT, 20),
                new ItemStack(Items.EMERALD, 1),
                16, 1, 0.05f);
        villager.notifyTrade(offer);

        data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        helper.assertTrue(data.isProfessionLocked(), "Profession should be locked after first trade");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void professionRetainedWhenLocked(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));

        VillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        data.setProfessionLocked(true);

        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));

        helper.assertTrue(
                villager.getVillagerData().getProfession() == VillagerProfession.FARMER,
                "Locked villager should retain profession after workstation break");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unlockedVillagerCanLoseProfession(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));

        VillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        helper.assertFalse(data.isProfessionLocked(), "Should not be locked");

        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));

        helper.assertTrue(
                villager.getVillagerData().getProfession() == VillagerProfession.NONE,
                "Unlocked villager should lose profession");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void workstationReclaimPreservesProfession(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));

        VillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        data.setProfessionLocked(true);

        GlobalPos jobSite = GlobalPos.of(helper.getLevel().dimension(),
                helper.absolutePos(new BlockPos(2, 1, 2)));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, jobSite);

        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));

        helper.assertTrue(
                villager.getVillagerData().getProfession() == VillagerProfession.FARMER,
                "Profession should persist after job site removal");

        GlobalPos newJobSite = GlobalPos.of(helper.getLevel().dimension(),
                helper.absolutePos(new BlockPos(3, 1, 3)));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, newJobSite);

        helper.assertTrue(
                villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent(),
                "Should be able to claim new workstation");
        helper.assertTrue(
                villager.getVillagerData().getProfession() == VillagerProfession.FARMER,
                "Profession should still be FARMER after reclaim");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void professionLockPersistsThroughSaveLoad(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(villager.getVillagerData()
                .setProfession(VillagerProfession.LIBRARIAN)
                .setLevel(3));

        VillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        data.setProfessionLocked(true);

        CompoundTag saved = new CompoundTag();
        villager.saveWithoutId(saved);
        villager.discard();

        Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(loaded != null, "Loaded villager should be created");
        loaded.load(saved);

        helper.assertTrue(
                loaded.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN,
                "Profession should persist through save/load");
        helper.assertTrue(
                loaded.getVillagerData().getLevel() == 3,
                "Level should persist through save/load");

        VillagerData loadedData = loaded.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        helper.assertTrue(loadedData.isProfessionLocked(),
                "Profession lock should persist through save/load");

        loaded.setVillagerData(loaded.getVillagerData().setProfession(VillagerProfession.NONE));
        helper.assertTrue(
                loaded.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN,
                "Lock should still protect profession after save/load");

        loaded.discard();
        helper.succeed();
    }
}
