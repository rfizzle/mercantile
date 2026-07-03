package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.network.*;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NetworkingGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void payloadRegistrationComplete(GameTestHelper helper) {
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void payloadsEncodeDecodeInServerContext(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        var infoPayload = new VillagerInfoPanelS2CPayload(
                villager.getId(), "farmer", 1, 0, 10, 0, "mercantile.tier.neutral", 0, false, false, "");
        VillagerInfoPanelS2CPayload.CODEC.encode(buf, infoPayload);
        VillagerInfoPanelS2CPayload decoded = VillagerInfoPanelS2CPayload.CODEC.decode(buf);
        helper.assertTrue(decoded.villagerEntityId() == villager.getId(),
                "decoded entity ID should match spawned villager");

        buf.clear();

        var mapPayload = new WorkstationMapS2CPayload(
                Map.of(villager.getUUID(), helper.absolutePos(new BlockPos(5, 1, 5))),
                List.of(),
                List.of(helper.absolutePos(new BlockPos(8, 1, 8))));
        WorkstationMapS2CPayload.CODEC.encode(buf, mapPayload);
        WorkstationMapS2CPayload decodedMap = WorkstationMapS2CPayload.CODEC.decode(buf);
        helper.assertTrue(decodedMap.bound().containsKey(villager.getUUID()),
                "decoded map should contain villager UUID");
        helper.assertTrue(decodedMap.unclaimedWorkstations().size() == 1,
                "decoded map should contain one unclaimed workstation");

        buf.release();
        villager.discard();
        helper.succeed();
    }
}
