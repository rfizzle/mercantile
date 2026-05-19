package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.VillagerData;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

public class AttachmentGameTest implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void villagerDataDefaultsOnFreshEntity(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        VillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);

        helper.assertFalse(data.isProfessionLocked(), "fresh villager should not be profession-locked");
        helper.assertTrue(data.getLockedTrades().isEmpty(), "fresh villager should have no locked trades");
        helper.assertFalse(data.isNameAssigned(), "fresh villager should not have name assigned");

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void villagerDataMutationsReadBack(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        VillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);

        data.setProfessionLocked(true);
        data.addLockedTrade("test_hash_1");
        data.addLockedTrade("test_hash_2");
        data.setNameAssigned(true);

        VillagerData readBack = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        helper.assertTrue(readBack.isProfessionLocked(), "profession lock should read back as true");
        helper.assertTrue(readBack.isTradeLocked("test_hash_1"), "locked trade should read back");
        helper.assertTrue(readBack.isTradeLocked("test_hash_2"), "locked trade should read back");
        helper.assertTrue(readBack.isNameAssigned(), "name assigned should read back as true");

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void villagerDataPersistsThroughNbt(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        VillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        data.setProfessionLocked(true);
        data.addLockedTrade("persist_hash");
        data.setNameAssigned(true);

        CompoundTag saved = new CompoundTag();
        villager.saveWithoutId(saved);
        villager.discard();

        Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(loaded != null, "villager entity should be created");
        loaded.load(saved);

        VillagerData loadedData = loaded.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        helper.assertTrue(loadedData.isProfessionLocked(), "profession lock should survive save/load");
        helper.assertTrue(loadedData.isTradeLocked("persist_hash"), "locked trade should survive save/load");
        helper.assertTrue(loadedData.isNameAssigned(), "name assigned should survive save/load");

        loaded.discard();
        helper.succeed();
    }
}
