package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record VillageBoundsS2CPayload(
        BlockPos center,
        BlockPos boundsMin,
        BlockPos boundsMax,
        List<PoiEntry> pois
) implements CustomPacketPayload {

    public record PoiEntry(BlockPos pos, String type, Optional<BlockPos> villagerPos) {
    }

    public static final int MAX_POIS = 1024;
    public static final int MAX_POI_TYPE_LEN = 64;

    public static final Type<VillageBoundsS2CPayload> TYPE =
            new Type<>(Mercantile.id("village_bounds_s2c"));

    public static final StreamCodec<FriendlyByteBuf, VillageBoundsS2CPayload> CODEC =
            StreamCodec.of(VillageBoundsS2CPayload::encode, VillageBoundsS2CPayload::decode);

    private static void encode(FriendlyByteBuf buf, VillageBoundsS2CPayload payload) {
        if (payload.pois.size() > MAX_POIS) {
            throw new EncoderException("VillageBoundsS2CPayload exceeds POI cap: " + payload.pois.size() + " > " + MAX_POIS);
        }
        buf.writeBlockPos(payload.center);
        buf.writeBlockPos(payload.boundsMin);
        buf.writeBlockPos(payload.boundsMax);
        buf.writeVarInt(payload.pois.size());
        for (PoiEntry poi : payload.pois) {
            buf.writeBlockPos(poi.pos());
            buf.writeUtf(poi.type(), MAX_POI_TYPE_LEN);
            buf.writeBoolean(poi.villagerPos().isPresent());
            poi.villagerPos().ifPresent(buf::writeBlockPos);
        }
    }

    private static VillageBoundsS2CPayload decode(FriendlyByteBuf buf) {
        BlockPos center = buf.readBlockPos();
        BlockPos min = buf.readBlockPos();
        BlockPos max = buf.readBlockPos();
        int poiCount = buf.readVarInt();
        if (poiCount < 0 || poiCount > MAX_POIS) {
            throw new DecoderException("VillageBoundsS2CPayload POI count out of bounds: " + poiCount);
        }
        List<PoiEntry> pois = new ArrayList<>(poiCount);
        for (int i = 0; i < poiCount; i++) {
            BlockPos pos = buf.readBlockPos();
            String type = buf.readUtf(MAX_POI_TYPE_LEN);
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
