package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record VillageBoundsS2CPayload(
        BlockPos center,
        BlockPos boundsMin,
        BlockPos boundsMax,
        List<PoiEntry> pois
) implements CustomPacketPayload {

    public record PoiEntry(BlockPos pos, String type, Optional<BlockPos> villagerPos) {
    }

    public static final Type<VillageBoundsS2CPayload> TYPE =
            new Type<>(Mercantile.id("village_bounds_s2c"));

    public static final StreamCodec<FriendlyByteBuf, VillageBoundsS2CPayload> CODEC =
            StreamCodec.of(VillageBoundsS2CPayload::encode, VillageBoundsS2CPayload::decode);

    private static void encode(FriendlyByteBuf buf, VillageBoundsS2CPayload payload) {
        buf.writeBlockPos(payload.center);
        buf.writeBlockPos(payload.boundsMin);
        buf.writeBlockPos(payload.boundsMax);
        buf.writeVarInt(payload.pois.size());
        for (PoiEntry poi : payload.pois) {
            buf.writeBlockPos(poi.pos());
            buf.writeUtf(poi.type());
            buf.writeBoolean(poi.villagerPos().isPresent());
            poi.villagerPos().ifPresent(buf::writeBlockPos);
        }
    }

    private static VillageBoundsS2CPayload decode(FriendlyByteBuf buf) {
        BlockPos center = buf.readBlockPos();
        BlockPos min = buf.readBlockPos();
        BlockPos max = buf.readBlockPos();
        int poiCount = buf.readVarInt();
        List<PoiEntry> pois = new ArrayList<>(poiCount);
        for (int i = 0; i < poiCount; i++) {
            BlockPos pos = buf.readBlockPos();
            String type = buf.readUtf();
            Optional<BlockPos> villagerPos = buf.readBoolean()
                    ? Optional.of(buf.readBlockPos())
                    : Optional.empty();
            pois.add(new PoiEntry(pos, type, villagerPos));
        }
        return new VillageBoundsS2CPayload(center, min, max, pois);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
