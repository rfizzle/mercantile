package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.memorial.MemorialManager;
import com.rfizzle.mercantile.memorial.MourningManager;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class MemorialGameTest implements FabricGameTest {

    private static final BlockPos DEATH_POS = new BlockPos(1, 1, 1);

    private static Villager spawnVillager(GameTestHelper helper) {
        return helper.spawn(EntityType.VILLAGER, 1, 1, 1);
    }

    private static ServerPlayer killWithPlayer(GameTestHelper helper, Villager villager) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(villager.position().add(1, 0, 0));
        villager.hurt(helper.getLevel().damageSources().playerAttack(player), 1_000.0f);
        return player;
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void namedVillagerDeathDropsMemorial(GameTestHelper helper) {
        Villager villager = spawnVillager(helper);
        villager.setCustomName(Component.literal("Elowen"));
        ServerPlayer player = killWithPlayer(helper, villager);

        helper.succeedWhen(() -> {
            helper.assertItemEntityPresent(MercantileRegistry.MEMORIAL, DEATH_POS, 4.0);
            player.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unnamedVillagerDeathDropsNothing(GameTestHelper helper) {
        Villager villager = spawnVillager(helper);
        // The naming system auto-names villagers on spawn; strip it to model the
        // unnamed edge case (naming disabled).
        villager.setCustomName(null);
        ServerPlayer player = killWithPlayer(helper, villager);

        helper.runAfterDelay(10, () -> {
            helper.assertItemEntityNotPresent(MercantileRegistry.MEMORIAL, DEATH_POS, 4.0);
            player.discard();
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void memorialsDisabledDropNothing(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        boolean saved = config.enableMemorials;
        config.enableMemorials = false;
        ServerPlayer player;
        try {
            Villager villager = spawnVillager(helper);
            villager.setCustomName(Component.literal("Elowen"));
            player = killWithPlayer(helper, villager);
        } finally {
            config.enableMemorials = saved;
        }

        ServerPlayer finalPlayer = player;
        helper.runAfterDelay(10, () -> {
            helper.assertItemEntityNotPresent(MercantileRegistry.MEMORIAL, DEATH_POS, 4.0);
            finalPlayer.discard();
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void memorialRecordsIdentityAndCause(GameTestHelper helper) {
        Villager villager = spawnVillager(helper);
        villager.setCustomName(Component.literal("Elowen"));
        villager.setVillagerData(villager.getVillagerData()
                .setProfession(VillagerProfession.FARMER).setLevel(3));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        ItemStack memorial = MemorialManager.createMemorialItem(
                villager, helper.getLevel().damageSources().playerAttack(player));

        CustomData data = memorial.get(DataComponents.CUSTOM_DATA);
        helper.assertTrue(data != null, "The memorial should carry custom data");
        CompoundTag nbt = data.copyTag();
        helper.assertTrue(nbt.getInt("MercantileDataVersion") == MemorialManager.CURRENT_DATA_VERSION,
                "The memorial data should be stamped with the current schema version");
        helper.assertTrue("Elowen".equals(nbt.getString("VillagerName")),
                "The memorial should record the villager's name, got " + nbt.getString("VillagerName"));
        helper.assertTrue("minecraft:farmer".equals(nbt.getString("Profession")),
                "The memorial should record the profession, got " + nbt.getString("Profession"));
        helper.assertTrue(nbt.getInt("Level") == 3,
                "The memorial should record the level, got " + nbt.getInt("Level"));
        helper.assertTrue("player".equals(nbt.getString("CauseOfDeath")),
                "The memorial should record the cause of death, got " + nbt.getString("CauseOfDeath"));
        helper.assertTrue(memorial.get(DataComponents.LORE) != null
                        && !memorial.get(DataComponents.LORE).lines().isEmpty(),
                "The memorial should carry tooltip lore lines");

        player.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void nearbyVillagerMournsADeath(GameTestHelper helper) {
        Villager victim = spawnVillager(helper);
        Villager witness = helper.spawn(EntityType.VILLAGER, 3, 1, 1);

        // The death event fires synchronously inside hurt(), so the witness is
        // enrolled before this call returns.
        ServerPlayer player = killWithPlayer(helper, victim);

        helper.assertTrue(MourningManager.isMourning(witness.getUUID()),
                "A villager within range should mourn the death");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void mourningDisabledNoReaction(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        boolean saved = config.enableMourning;
        config.enableMourning = false;
        try {
            Villager victim = spawnVillager(helper);
            helper.spawn(EntityType.VILLAGER, 3, 1, 1);

            // Assert on the session-count delta of this one synchronous death:
            // a global isMourning check can be contaminated by deaths in
            // concurrently running tests within witness range.
            int before = MourningManager.sessionCount();
            ServerPlayer player = killWithPlayer(helper, victim);
            int after = MourningManager.sessionCount();

            helper.assertTrue(after == before,
                    "A death with mourning disabled should not start a mourning session");
            player.discard();
        } finally {
            config.enableMourning = saved;
        }
        helper.succeed();
    }
}
