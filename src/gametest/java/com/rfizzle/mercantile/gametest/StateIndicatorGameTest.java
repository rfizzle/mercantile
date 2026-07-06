package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.compat.shared.StateIndicatorData;
import com.rfizzle.mercantile.contract.DeliveryContract;
import com.rfizzle.mercantile.data.MercantileAttachments;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class StateIndicatorGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void unemployedFreshVillagerHasNoBedState(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.getBrain().eraseMemory(MemoryModuleType.HOME);
        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);

        CompoundTag tag = new CompoundTag();
        StateIndicatorData.write(tag, villager);

        Set<String> states = readStates(tag);
        helper.assertFalse(states.contains("needs_bed"), "needs_bed state must not appear");
        helper.assertFalse(states.contains(StateIndicatorData.STATE_NEEDS_WORKSTATION),
                "unemployed should not need workstation");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void farmerWithoutJobSiteNeedsWorkstation(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);

        CompoundTag tag = new CompoundTag();
        StateIndicatorData.write(tag, villager);

        Set<String> states = readStates(tag);
        helper.assertTrue(states.contains(StateIndicatorData.STATE_NEEDS_WORKSTATION),
                "farmer without job site should need workstation");
        helper.assertValueEqual(tag.getString(StateIndicatorData.KEY_WORKSTATION_ITEM),
                "minecraft:composter", "farmer workstation is composter");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tradingPlayerStateAppearsWithName(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        villager.setTradingPlayer(player);

        CompoundTag tag = new CompoundTag();
        StateIndicatorData.write(tag, villager);

        Set<String> states = readStates(tag);
        helper.assertTrue(states.contains(StateIndicatorData.STATE_TRADING),
                "trading state present");
        helper.assertValueEqual(tag.getString(StateIndicatorData.KEY_TRADING_PLAYER),
                player.getName().getString(),
                "trading player name stored");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void panickingStateOnHostile(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 0);
        villager.getBrain().setMemory(MemoryModuleType.NEAREST_HOSTILE, (LivingEntity) zombie);

        CompoundTag tag = new CompoundTag();
        StateIndicatorData.write(tag, villager);

        Set<String> states = readStates(tag);
        helper.assertTrue(states.contains(StateIndicatorData.STATE_PANICKING),
                "panicking when NEAREST_HOSTILE present");

        zombie.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void multipleStatesPresentSimultaneously(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        villager.setTradingPlayer(player);

        CompoundTag tag = new CompoundTag();
        StateIndicatorData.write(tag, villager);

        ListTag list = tag.getList(StateIndicatorData.KEY_STATES, Tag.TAG_STRING);
        helper.assertTrue(list.size() >= 2, "expected at least 2 states, got " + list.size());

        Set<String> states = readStates(tag);
        helper.assertTrue(states.contains(StateIndicatorData.STATE_TRADING), "trading present");
        helper.assertTrue(states.contains(StateIndicatorData.STATE_NEEDS_WORKSTATION),
                "needs_workstation present");

        helper.assertValueEqual(list.getString(0), StateIndicatorData.STATE_TRADING,
                "trading is first by priority");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unemployedAdultHasUnemployedState(GameTestHelper helper) {
        // Fresh villagers spawn with profession NONE; they can take a work order.
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);

        CompoundTag tag = new CompoundTag();
        StateIndicatorData.write(tag, villager);

        Set<String> states = readStates(tag);
        helper.assertTrue(states.contains(StateIndicatorData.STATE_UNEMPLOYED),
                "profession-NONE adult should be unemployed");
        helper.assertFalse(states.contains(StateIndicatorData.STATE_NEEDS_WORKSTATION),
                "unemployed villager does not report needs_workstation");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void liveContractOfferHasContractState(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        DeliveryContract offer = new DeliveryContract(UUID.randomUUID(),
                BuiltInRegistries.ITEM.getKey(Items.WHEAT),
                8, 4, false, helper.getLevel().getGameTime() + 12_000L);
        villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA).setContract(offer);

        CompoundTag tag = new CompoundTag();
        StateIndicatorData.write(tag, villager);

        Set<String> states = readStates(tag);
        helper.assertTrue(states.contains(StateIndicatorData.STATE_HAS_CONTRACT_OFFER),
                "live unaccepted offer should surface the contract state");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void acceptedContractHasNoOfferState(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        DeliveryContract accepted = new DeliveryContract(UUID.randomUUID(),
                BuiltInRegistries.ITEM.getKey(Items.WHEAT),
                8, 4, true, helper.getLevel().getGameTime() + 12_000L);
        villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA).setContract(accepted);

        CompoundTag tag = new CompoundTag();
        StateIndicatorData.write(tag, villager);

        Set<String> states = readStates(tag);
        helper.assertFalse(states.contains(StateIndicatorData.STATE_HAS_CONTRACT_OFFER),
                "an accepted contract is no longer an open offer");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void babyVillagerHasNoStates(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setAge(-12000);

        CompoundTag tag = new CompoundTag();
        StateIndicatorData.write(tag, villager);

        helper.assertTrue(tag.getBoolean(StateIndicatorData.KEY_PRESENT), "data present");
        ListTag list = tag.getList(StateIndicatorData.KEY_STATES, Tag.TAG_STRING);
        helper.assertTrue(list.isEmpty(), "baby villager has no states, got " + list.size());
        helper.succeed();
    }

    private static Set<String> readStates(CompoundTag tag) {
        ListTag list = tag.getList(StateIndicatorData.KEY_STATES, Tag.TAG_STRING);
        Set<String> result = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getString(i));
        }
        return result;
    }
}
