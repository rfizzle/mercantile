package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerInfoPanelS2CPayload(
        int villagerEntityId,
        String profession,
        int level,
        int xp,
        int xpToNextLevel,
        int reputation,
        String reputationTier,
        int totalTrades,
        boolean hasWorkstation,
        boolean professionLocked
) implements CustomPacketPayload {

    public static final Type<VillagerInfoPanelS2CPayload> TYPE =
            new Type<>(Mercantile.id("villager_info_panel_s2c"));

    public static final StreamCodec<FriendlyByteBuf, VillagerInfoPanelS2CPayload> CODEC =
            StreamCodec.of(VillagerInfoPanelS2CPayload::encode, VillagerInfoPanelS2CPayload::decode);

    private static void encode(FriendlyByteBuf buf, VillagerInfoPanelS2CPayload payload) {
        buf.writeVarInt(payload.villagerEntityId);
        buf.writeUtf(payload.profession);
        buf.writeVarInt(payload.level);
        buf.writeVarInt(payload.xp);
        buf.writeVarInt(payload.xpToNextLevel);
        buf.writeVarInt(payload.reputation);
        buf.writeUtf(payload.reputationTier);
        buf.writeVarInt(payload.totalTrades);
        buf.writeBoolean(payload.hasWorkstation);
        buf.writeBoolean(payload.professionLocked);
    }

    private static VillagerInfoPanelS2CPayload decode(FriendlyByteBuf buf) {
        return new VillagerInfoPanelS2CPayload(
                buf.readVarInt(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
