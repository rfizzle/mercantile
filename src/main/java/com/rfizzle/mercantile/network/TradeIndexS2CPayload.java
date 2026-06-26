package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record TradeIndexS2CPayload(List<TradeIndexEntry> entries) implements CustomPacketPayload {

    public static final int MAX_ENTRIES = 4096;

    public static final Type<TradeIndexS2CPayload> TYPE =
            new Type<>(Mercantile.id("trade_index_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeIndexS2CPayload> CODEC =
            StreamCodec.of(TradeIndexS2CPayload::encode, TradeIndexS2CPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, TradeIndexS2CPayload payload) {
        if (payload.entries.size() > MAX_ENTRIES) {
            throw new EncoderException("TradeIndexS2CPayload exceeds entry cap: "
                    + payload.entries.size() + " > " + MAX_ENTRIES);
        }
        buf.writeVarInt(payload.entries.size());
        for (TradeIndexEntry entry : payload.entries) {
            TradeIndexEntry.STREAM_CODEC.encode(buf, entry);
        }
    }

    private static TradeIndexS2CPayload decode(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new DecoderException("TradeIndexS2CPayload entry count out of bounds: " + size);
        }
        List<TradeIndexEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(TradeIndexEntry.STREAM_CODEC.decode(buf));
        }
        return new TradeIndexS2CPayload(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
