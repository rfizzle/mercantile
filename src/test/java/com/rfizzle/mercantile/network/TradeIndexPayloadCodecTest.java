package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeIndexPayloadCodecTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private RegistryFriendlyByteBuf registryBuf() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private <T> T roundTrip(StreamCodec<? super RegistryFriendlyByteBuf, T> codec, T original) {
        RegistryFriendlyByteBuf buf = registryBuf();
        codec.encode(buf, original);
        T decoded = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "buffer should be fully consumed after decode");
        return decoded;
    }

    private static TradeIndexEntry sampleEntry(OptionalInt minScore) {
        return new TradeIndexEntry(
                ResourceLocation.parse("minecraft:farmer"),
                2,
                TradeIndexEntry.Source.VANILLA,
                new ItemStack(Items.EMERALD, 5),
                ItemStack.EMPTY,
                new ItemStack(Items.WHEAT, 3),
                new ItemStack(Items.COMPOSTER),
                16,
                5,
                0.05f,
                minScore
        );
    }

    // --- TradeIndexEntry.STREAM_CODEC ---

    @Test
    void tradeIndexEntryRoundTrip() {
        TradeIndexEntry original = sampleEntry(OptionalInt.empty());
        RegistryFriendlyByteBuf buf = registryBuf();
        TradeIndexEntry.STREAM_CODEC.encode(buf, original);
        TradeIndexEntry decoded = TradeIndexEntry.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "buffer should be fully consumed after decode");

        assertEquals(original.profession(), decoded.profession());
        assertEquals(original.level(), decoded.level());
        assertEquals(original.source(), decoded.source());
        assertEquals(original.inputA().getItem(), decoded.inputA().getItem());
        assertEquals(original.inputA().getCount(), decoded.inputA().getCount());
        assertTrue(decoded.inputB().isEmpty());
        assertEquals(original.output().getItem(), decoded.output().getItem());
        assertEquals(original.output().getCount(), decoded.output().getCount());
        assertEquals(original.maxUses(), decoded.maxUses());
        assertEquals(original.xpGain(), decoded.xpGain());
        assertEquals(original.priceMultiplier(), decoded.priceMultiplier(), 0.0001f);
        assertTrue(decoded.minScore().isEmpty());
    }

    @Test
    void tradeIndexEntryWithMinScore() {
        TradeIndexEntry original = new TradeIndexEntry(
                ResourceLocation.parse("minecraft:weaponsmith"),
                1,
                TradeIndexEntry.Source.EXCLUSIVE_PROFESSION,
                new ItemStack(Items.EMERALD, 10),
                ItemStack.EMPTY,
                new ItemStack(Items.DIAMOND_SWORD),
                new ItemStack(Items.GRINDSTONE),
                4,
                10,
                0.1f,
                OptionalInt.of(100)
        );
        RegistryFriendlyByteBuf buf = registryBuf();
        TradeIndexEntry.STREAM_CODEC.encode(buf, original);
        TradeIndexEntry decoded = TradeIndexEntry.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes());
        assertTrue(decoded.minScore().isPresent());
        assertEquals(100, decoded.minScore().getAsInt());
        assertEquals(TradeIndexEntry.Source.EXCLUSIVE_PROFESSION, decoded.source());
    }

    @Test
    void tradeIndexEntryAllSourceValues() {
        for (TradeIndexEntry.Source source : TradeIndexEntry.Source.values()) {
            TradeIndexEntry original = new TradeIndexEntry(
                    ResourceLocation.parse("minecraft:farmer"),
                    1,
                    source,
                    new ItemStack(Items.EMERALD),
                    ItemStack.EMPTY,
                    new ItemStack(Items.BREAD),
                    new ItemStack(Items.COMPOSTER),
                    12,
                    3,
                    0.05f,
                    source == TradeIndexEntry.Source.VANILLA ? OptionalInt.empty() : OptionalInt.of(50)
            );
            RegistryFriendlyByteBuf buf = registryBuf();
            TradeIndexEntry.STREAM_CODEC.encode(buf, original);
            TradeIndexEntry decoded = TradeIndexEntry.STREAM_CODEC.decode(buf);
            assertEquals(0, buf.readableBytes(), "Buffer not fully consumed for source " + source);
            assertEquals(source, decoded.source());
        }
    }

    // --- TradeIndexS2CPayload.CODEC ---

    @Test
    void emptyPayloadRoundTrip() {
        var original = new TradeIndexS2CPayload(List.of());
        TradeIndexS2CPayload decoded = roundTrip(TradeIndexS2CPayload.CODEC, original);
        assertTrue(decoded.entries().isEmpty());
    }

    @Test
    void singleEntryPayloadRoundTrip() {
        var original = new TradeIndexS2CPayload(List.of(sampleEntry(OptionalInt.empty())));
        TradeIndexS2CPayload decoded = roundTrip(TradeIndexS2CPayload.CODEC, original);
        assertEquals(1, decoded.entries().size());
        assertEquals(ResourceLocation.parse("minecraft:farmer"), decoded.entries().get(0).profession());
    }

    @Test
    void payloadAtMaxEntriesAccepted() {
        List<TradeIndexEntry> entries = new ArrayList<>(TradeIndexS2CPayload.MAX_ENTRIES);
        for (int i = 0; i < TradeIndexS2CPayload.MAX_ENTRIES; i++) {
            entries.add(sampleEntry(OptionalInt.empty()));
        }
        var original = new TradeIndexS2CPayload(entries);
        TradeIndexS2CPayload decoded = roundTrip(TradeIndexS2CPayload.CODEC, original);
        assertEquals(TradeIndexS2CPayload.MAX_ENTRIES, decoded.entries().size());
    }

    @Test
    void payloadExceedingMaxEntriesThrowsOnEncode() {
        List<TradeIndexEntry> entries = new ArrayList<>(TradeIndexS2CPayload.MAX_ENTRIES + 1);
        for (int i = 0; i < TradeIndexS2CPayload.MAX_ENTRIES + 1; i++) {
            entries.add(sampleEntry(OptionalInt.empty()));
        }
        var payload = new TradeIndexS2CPayload(entries);
        RegistryFriendlyByteBuf buf = registryBuf();
        assertThrows(EncoderException.class, () -> TradeIndexS2CPayload.CODEC.encode(buf, payload));
    }

    @Test
    void decodeRejectsOversizedCount() {
        RegistryFriendlyByteBuf buf = registryBuf();
        buf.writeVarInt(TradeIndexS2CPayload.MAX_ENTRIES + 1);
        assertThrows(DecoderException.class, () -> TradeIndexS2CPayload.CODEC.decode(buf));
    }

    @Test
    void decodeRejectsNegativeCount() {
        RegistryFriendlyByteBuf buf = registryBuf();
        buf.writeVarInt(-1);
        assertThrows(DecoderException.class, () -> TradeIndexS2CPayload.CODEC.decode(buf));
    }

    @Test
    void payloadTypeIsCorrect() {
        assertEquals(TradeIndexS2CPayload.TYPE, new TradeIndexS2CPayload(List.of()).type());
    }
}
