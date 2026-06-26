package com.rfizzle.mercantile.trade.index;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalInt;

public record TradeIndexEntry(
        ResourceLocation profession,
        int level,
        Source source,
        ItemStack inputA,
        ItemStack inputB,
        ItemStack output,
        ItemStack workstation,
        int maxUses,
        int xpGain,
        float priceMultiplier,
        OptionalInt minScore
) {
    public enum Source {
        VANILLA,
        EXCLUSIVE_PROFESSION,
        EXCLUSIVE_CROSS_PROFESSION
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeIndexEntry> STREAM_CODEC =
            StreamCodec.of(TradeIndexEntry::encode, TradeIndexEntry::decode);

    private static void encode(RegistryFriendlyByteBuf buf, TradeIndexEntry entry) {
        buf.writeResourceLocation(entry.profession);
        buf.writeVarInt(entry.level);
        buf.writeVarInt(entry.source.ordinal());
        ItemStack.STREAM_CODEC.encode(buf, entry.inputA);
        ItemStack.STREAM_CODEC.encode(buf, entry.inputB);
        ItemStack.STREAM_CODEC.encode(buf, entry.output);
        ItemStack.STREAM_CODEC.encode(buf, entry.workstation);
        buf.writeVarInt(entry.maxUses);
        buf.writeVarInt(entry.xpGain);
        buf.writeFloat(entry.priceMultiplier);
        buf.writeBoolean(entry.minScore.isPresent());
        if (entry.minScore.isPresent()) {
            buf.writeVarInt(entry.minScore.getAsInt());
        }
    }

    private static TradeIndexEntry decode(RegistryFriendlyByteBuf buf) {
        ResourceLocation profession = buf.readResourceLocation();
        int level = buf.readVarInt();
        Source[] sources = Source.values();
        int sourceOrdinal = buf.readVarInt();
        if (sourceOrdinal < 0 || sourceOrdinal >= sources.length) {
            throw new DecoderException("Unknown TradeIndexEntry.Source ordinal: " + sourceOrdinal);
        }
        Source source = sources[sourceOrdinal];
        ItemStack inputA = ItemStack.STREAM_CODEC.decode(buf);
        ItemStack inputB = ItemStack.STREAM_CODEC.decode(buf);
        ItemStack output = ItemStack.STREAM_CODEC.decode(buf);
        ItemStack workstation = ItemStack.STREAM_CODEC.decode(buf);
        int maxUses = buf.readVarInt();
        int xpGain = buf.readVarInt();
        float priceMultiplier = buf.readFloat();
        boolean hasMinScore = buf.readBoolean();
        OptionalInt minScore = hasMinScore ? OptionalInt.of(buf.readVarInt()) : OptionalInt.empty();
        return new TradeIndexEntry(profession, level, source, inputA, inputB, output, workstation,
                maxUses, xpGain, priceMultiplier, minScore);
    }
}
