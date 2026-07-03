package com.rfizzle.mercantile.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PayloadCodecTest {

    private FriendlyByteBuf buf() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    private <T> T roundTrip(StreamCodec<? super FriendlyByteBuf, T> codec, T original) {
        FriendlyByteBuf buf = buf();
        codec.encode(buf, original);
        T decoded = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "buffer should be fully consumed after decode");
        return decoded;
    }

    // --- C2S payloads ---

    @Test
    void cycleTradesC2S() {
        var original = new CycleTradesC2SPayload(42);
        assertEquals(original, roundTrip(CycleTradesC2SPayload.CODEC, original));
    }

    @Test
    void requestWorkstationMapC2SEmpty() {
        var original = new RequestWorkstationMapC2SPayload();
        assertEquals(original, roundTrip(RequestWorkstationMapC2SPayload.CODEC, original));
    }

    // --- S2C payloads ---

    @Test
    void syncReputationS2C() {
        var original = new SyncReputationS2CPayload(75, "mercantile.tier.trusted", 3, 5);
        assertEquals(original, roundTrip(SyncReputationS2CPayload.CODEC, original));
    }

    @Test
    void syncReputationS2CNegativeScore() {
        var original = new SyncReputationS2CPayload(-80, "mercantile.tier.reviled", 0, 5);
        assertEquals(original, roundTrip(SyncReputationS2CPayload.CODEC, original));
    }

    @Test
    void syncReputationS2CCarriesDailyFields() {
        var original = new SyncReputationS2CPayload(305, "mercantile.tier.trusted", 4, 5);
        SyncReputationS2CPayload decoded = roundTrip(SyncReputationS2CPayload.CODEC, original);
        assertEquals(305, decoded.score());
        assertEquals("mercantile.tier.trusted", decoded.tierKey());
        assertEquals(4, decoded.dailyEarned());
        assertEquals(5, decoded.dailyCap());
    }

    @Test
    void followStateS2CFollowing() {
        var original = new FollowStateS2CPayload(123, true);
        assertEquals(original, roundTrip(FollowStateS2CPayload.CODEC, original));
    }

    @Test
    void followStateS2CNotFollowing() {
        var original = new FollowStateS2CPayload(456, false);
        assertEquals(original, roundTrip(FollowStateS2CPayload.CODEC, original));
    }

    @Test
    void restockTimerS2C() {
        var original = new RestockTimerS2CPayload(10, 6000, 1, true, 2400, 2);
        assertEquals(original, roundTrip(RestockTimerS2CPayload.CODEC, original));
    }

    @Test
    void restockTimerS2CNoWorkstation() {
        var original = new RestockTimerS2CPayload(10, 0, 0, false, 2400, 2);
        assertEquals(original, roundTrip(RestockTimerS2CPayload.CODEC, original));
    }

    @Test
    void demandPriceS2CWithComponents() {
        var components = List.of(
                new DemandPriceS2CPayload.PriceComponent(10, 2, -1, 0, -3, 0, 0, 8),
                new DemandPriceS2CPayload.PriceComponent(32, 0, -5, 0, 0, 0, 0, 27)
        );
        var original = new DemandPriceS2CPayload(42, components);
        assertEquals(original, roundTrip(DemandPriceS2CPayload.CODEC, original));
    }

    @Test
    void demandPriceS2CEmptyComponents() {
        var original = new DemandPriceS2CPayload(42, List.of());
        assertEquals(original, roundTrip(DemandPriceS2CPayload.CODEC, original));
    }

    @Test
    void priceComponentDirect() {
        var original = new DemandPriceS2CPayload.PriceComponent(64, 5, -10, 0, -2, 0, 0, 57);
        assertEquals(original, roundTrip(DemandPriceS2CPayload.PriceComponent.STREAM_CODEC, original));
    }

    @Test
    void priceComponentWithMoodModifier() {
        var discount = new DemandPriceS2CPayload.PriceComponent(20, 0, 0, -1, 0, 0, 0, 19);
        assertEquals(discount, roundTrip(DemandPriceS2CPayload.PriceComponent.STREAM_CODEC, discount));
        var markup = new DemandPriceS2CPayload.PriceComponent(20, 0, 0, 1, 0, 0, 0, 21);
        assertEquals(markup, roundTrip(DemandPriceS2CPayload.PriceComponent.STREAM_CODEC, markup));
    }

    @Test
    void priceComponentWithOtherAdjust() {
        // Simulates Hero of the Village discount: finalPrice < basePrice with no other modifiers.
        var original = new DemandPriceS2CPayload.PriceComponent(10, 0, 0, 0, 0, 0, -3, 7);
        assertEquals(original, roundTrip(DemandPriceS2CPayload.PriceComponent.STREAM_CODEC, original));
    }

    @Test
    void demandPriceS2CDecodeRejectsBogusSize() {
        FriendlyByteBuf buf = buf();
        buf.writeVarInt(42); // villagerEntityId
        buf.writeVarInt(DemandPriceS2CPayload.MAX_OFFERS + 1);
        assertThrows(DecoderException.class, () -> DemandPriceS2CPayload.CODEC.decode(buf));
    }

    @Test
    void villagerInfoPanelS2C() {
        var original = new VillagerInfoPanelS2CPayload(
                42, "farmer", 3, 150, 250, 75, "mercantile.tier.trusted", 28, true, true, "mercantile.mood.content");
        assertEquals(original, roundTrip(VillagerInfoPanelS2CPayload.CODEC, original));
    }

    @Test
    void villagerInfoPanelS2CDefaults() {
        var original = new VillagerInfoPanelS2CPayload(
                1, "none", 1, 0, 10, 0, "mercantile.tier.neutral", 0, false, false, "");
        assertEquals(original, roundTrip(VillagerInfoPanelS2CPayload.CODEC, original));
    }

    @Test
    void workstationMapS2C() {
        var entries = Map.of(
                UUID.fromString("12345678-1234-1234-1234-123456789abc"), new BlockPos(100, 64, -200),
                UUID.fromString("abcdefab-abcd-abcd-abcd-abcdefabcdef"), new BlockPos(-50, 70, 300)
        );
        var original = new WorkstationMapS2CPayload(entries, List.of(), List.of());
        assertEquals(original, roundTrip(WorkstationMapS2CPayload.CODEC, original));
    }

    @Test
    void workstationMapS2CEmptyMap() {
        var original = new WorkstationMapS2CPayload(Map.of(), List.of(), List.of());
        assertEquals(original, roundTrip(WorkstationMapS2CPayload.CODEC, original));
    }

    @Test
    void workstationMapS2CWithUnboundAndUnclaimed() {
        var bound = Map.of(
                UUID.fromString("11111111-1111-1111-1111-111111111111"), new BlockPos(1, 64, 1)
        );
        var unbound = List.of(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333")
        );
        var unclaimed = List.of(
                new BlockPos(10, 64, 10),
                new BlockPos(-5, 64, 5),
                new BlockPos(0, 80, 0)
        );
        var original = new WorkstationMapS2CPayload(bound, unbound, unclaimed);
        WorkstationMapS2CPayload decoded = roundTrip(WorkstationMapS2CPayload.CODEC, original);
        assertEquals(bound, decoded.bound());
        assertEquals(unbound, decoded.unboundVillagers());
        assertEquals(unclaimed, decoded.unclaimedWorkstations());
    }

    @Test
    void workstationMapS2CEmptyAllFields() {
        var original = new WorkstationMapS2CPayload(Map.of(), List.of(), List.of());
        WorkstationMapS2CPayload decoded = roundTrip(WorkstationMapS2CPayload.CODEC, original);
        assertTrue(decoded.bound().isEmpty());
        assertTrue(decoded.unboundVillagers().isEmpty());
        assertTrue(decoded.unclaimedWorkstations().isEmpty());
    }

    @Test
    void configSyncS2C() {
        var original = new ConfigSyncS2CPayload(
                "{\"enableTradeCycling\":true,\"tradeCycleEmeraldCost\":6}");
        assertEquals(original, roundTrip(ConfigSyncS2CPayload.CODEC, original));
    }

    // --- Payload type identity ---

    // --- Size guards ---

    @Test
    void configSyncS2CAcceptsAtBoundary() {
        String exact = "a".repeat(ConfigSyncS2CPayload.MAX_CONFIG_JSON_CHARS);
        var original = new ConfigSyncS2CPayload(exact);
        assertEquals(original, roundTrip(ConfigSyncS2CPayload.CODEC, original));
    }

    @Test
    void defaultConfigJsonLeavesSyncHeadroom() {
        // The join sync sends the whole config as JSON. Keep at least half the cap free so
        // config growth and non-default values can't push a real payload past the limit.
        int length = new com.rfizzle.mercantile.config.MercantileConfig().toJson().length();
        assertTrue(length <= ConfigSyncS2CPayload.MAX_CONFIG_JSON_CHARS / 2,
                "default config JSON is " + length + " chars; raise MAX_CONFIG_JSON_CHARS before it outgrows the sync payload");
    }

    @Test
    void configSyncS2CRejectsOversizedString() {
        String oversized = "a".repeat(ConfigSyncS2CPayload.MAX_CONFIG_JSON_CHARS + 1);
        var payload = new ConfigSyncS2CPayload(oversized);
        FriendlyByteBuf buf = buf();
        assertThrows(EncoderException.class, () -> ConfigSyncS2CPayload.CODEC.encode(buf, payload));
    }

    @Test
    void workstationMapS2CRejectsTooManyEntries() {
        Map<UUID, BlockPos> entries = new HashMap<>(WorkstationMapS2CPayload.MAX_ENTRIES + 1);
        for (int i = 0; i < WorkstationMapS2CPayload.MAX_ENTRIES + 1; i++) {
            entries.put(new UUID(0, i), new BlockPos(i, 64, i));
        }
        var payload = new WorkstationMapS2CPayload(entries, List.of(), List.of());
        FriendlyByteBuf buf = buf();
        assertThrows(EncoderException.class, () -> WorkstationMapS2CPayload.CODEC.encode(buf, payload));
    }

    @Test
    void workstationMapS2CRejectsTooManyUnbound() {
        List<UUID> unbound = new ArrayList<>(WorkstationMapS2CPayload.MAX_UNBOUND + 1);
        for (int i = 0; i < WorkstationMapS2CPayload.MAX_UNBOUND + 1; i++) {
            unbound.add(new UUID(1, i));
        }
        var payload = new WorkstationMapS2CPayload(Map.of(), unbound, List.of());
        FriendlyByteBuf buf = buf();
        assertThrows(EncoderException.class, () -> WorkstationMapS2CPayload.CODEC.encode(buf, payload));
    }

    @Test
    void workstationMapS2CRejectsTooManyUnclaimed() {
        List<BlockPos> unclaimed = new ArrayList<>(WorkstationMapS2CPayload.MAX_UNCLAIMED + 1);
        for (int i = 0; i < WorkstationMapS2CPayload.MAX_UNCLAIMED + 1; i++) {
            unclaimed.add(new BlockPos(i, 64, i));
        }
        var payload = new WorkstationMapS2CPayload(Map.of(), List.of(), unclaimed);
        FriendlyByteBuf buf = buf();
        assertThrows(EncoderException.class, () -> WorkstationMapS2CPayload.CODEC.encode(buf, payload));
    }

    @Test
    void workstationMapS2CDecodeRejectsBogusSize() {
        FriendlyByteBuf buf = buf();
        buf.writeVarInt(Integer.MAX_VALUE);
        assertThrows(DecoderException.class, () -> WorkstationMapS2CPayload.CODEC.decode(buf));
    }

    @Test
    void workstationMapS2CDecodeRejectsBogusUnboundSize() {
        FriendlyByteBuf buf = buf();
        buf.writeVarInt(0); // bound size
        buf.writeVarInt(Integer.MAX_VALUE); // unbound size — bogus
        assertThrows(DecoderException.class, () -> WorkstationMapS2CPayload.CODEC.decode(buf));
    }

    @Test
    void workstationMapS2CDecodeRejectsBogusUnclaimedSize() {
        FriendlyByteBuf buf = buf();
        buf.writeVarInt(0); // bound size
        buf.writeVarInt(0); // unbound size
        buf.writeVarInt(Integer.MAX_VALUE); // unclaimed size — bogus
        assertThrows(DecoderException.class, () -> WorkstationMapS2CPayload.CODEC.decode(buf));
    }

    @Test
    void allPayloadsReturnCorrectType() {
        assertEquals(CycleTradesC2SPayload.TYPE, new CycleTradesC2SPayload(0).type());
        assertEquals(RequestWorkstationMapC2SPayload.TYPE, new RequestWorkstationMapC2SPayload().type());
        assertEquals(SyncReputationS2CPayload.TYPE, new SyncReputationS2CPayload(0, "", 0, 0).type());
        assertEquals(FollowStateS2CPayload.TYPE, new FollowStateS2CPayload(0, false).type());
        assertEquals(RestockTimerS2CPayload.TYPE, new RestockTimerS2CPayload(0, 0, 0, false, 2400, 2).type());
        assertEquals(DemandPriceS2CPayload.TYPE, new DemandPriceS2CPayload(0, List.of()).type());
        assertEquals(VillagerInfoPanelS2CPayload.TYPE,
                new VillagerInfoPanelS2CPayload(0, "", 0, 0, 0, 0, "", 0, false, false, "").type());
        assertEquals(WorkstationMapS2CPayload.TYPE, new WorkstationMapS2CPayload(Map.of(), List.of(), List.of()).type());
        assertEquals(ConfigSyncS2CPayload.TYPE, new ConfigSyncS2CPayload("").type());
        assertEquals(TradeIndexS2CPayload.TYPE, new TradeIndexS2CPayload(List.of()).type());
    }
}
