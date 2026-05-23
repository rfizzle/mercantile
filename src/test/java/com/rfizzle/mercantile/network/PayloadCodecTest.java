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
import java.util.Optional;
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
    void followVillagerC2S() {
        var original = new FollowVillagerC2SPayload(99);
        assertEquals(original, roundTrip(FollowVillagerC2SPayload.CODEC, original));
    }

    @Test
    void requestWorkstationMapC2SEmpty() {
        var original = new RequestWorkstationMapC2SPayload();
        assertEquals(original, roundTrip(RequestWorkstationMapC2SPayload.CODEC, original));
    }

    @Test
    void requestVillageBoundsC2SEmpty() {
        var original = new RequestVillageBoundsC2SPayload();
        assertEquals(original, roundTrip(RequestVillageBoundsC2SPayload.CODEC, original));
    }

    // --- S2C payloads ---

    @Test
    void syncReputationS2C() {
        var original = new SyncReputationS2CPayload(75, "mercantile.tier.trusted");
        assertEquals(original, roundTrip(SyncReputationS2CPayload.CODEC, original));
    }

    @Test
    void syncReputationS2CNegativeScore() {
        var original = new SyncReputationS2CPayload(-80, "mercantile.tier.reviled");
        assertEquals(original, roundTrip(SyncReputationS2CPayload.CODEC, original));
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
        var original = new RestockTimerS2CPayload(10, 6000, 1, true);
        assertEquals(original, roundTrip(RestockTimerS2CPayload.CODEC, original));
    }

    @Test
    void restockTimerS2CNoWorkstation() {
        var original = new RestockTimerS2CPayload(10, 0, 0, false);
        assertEquals(original, roundTrip(RestockTimerS2CPayload.CODEC, original));
    }

    @Test
    void demandPriceS2CWithComponents() {
        var components = List.of(
                new DemandPriceS2CPayload.PriceComponent(10, 2, -1, -3, 8),
                new DemandPriceS2CPayload.PriceComponent(32, 0, -5, 0, 27)
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
        var original = new DemandPriceS2CPayload.PriceComponent(64, 5, -10, -2, 57);
        assertEquals(original, roundTrip(DemandPriceS2CPayload.PriceComponent.STREAM_CODEC, original));
    }

    @Test
    void villagerInfoPanelS2C() {
        var original = new VillagerInfoPanelS2CPayload(
                42, "farmer", 3, 150, 250, 75, "mercantile.tier.trusted", 28, true, true);
        assertEquals(original, roundTrip(VillagerInfoPanelS2CPayload.CODEC, original));
    }

    @Test
    void villagerInfoPanelS2CDefaults() {
        var original = new VillagerInfoPanelS2CPayload(
                1, "none", 1, 0, 10, 0, "mercantile.tier.neutral", 0, false, false);
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
    void villageBoundsS2CMixedPois() {
        var pois = List.of(
                new VillageBoundsS2CPayload.PoiEntry(
                        new BlockPos(10, 64, 10), "workstation",
                        Optional.of(new BlockPos(12, 64, 10))),
                new VillageBoundsS2CPayload.PoiEntry(
                        new BlockPos(20, 64, 20), "bed",
                        Optional.empty()),
                new VillageBoundsS2CPayload.PoiEntry(
                        new BlockPos(15, 64, 15), "bell",
                        Optional.of(new BlockPos(14, 64, 15)))
        );
        var original = new VillageBoundsS2CPayload(
                new BlockPos(15, 64, 15),
                new BlockPos(0, 54, 0),
                new BlockPos(30, 74, 30),
                pois);
        assertEquals(original, roundTrip(VillageBoundsS2CPayload.CODEC, original));
    }

    @Test
    void villageBoundsS2CAllUnclaimed() {
        var pois = List.of(
                new VillageBoundsS2CPayload.PoiEntry(
                        new BlockPos(0, 64, 0), "bed", Optional.empty()),
                new VillageBoundsS2CPayload.PoiEntry(
                        new BlockPos(5, 64, 5), "workstation", Optional.empty())
        );
        var original = new VillageBoundsS2CPayload(
                new BlockPos(2, 64, 2),
                new BlockPos(-10, 54, -10),
                new BlockPos(15, 74, 15),
                pois);
        assertEquals(original, roundTrip(VillageBoundsS2CPayload.CODEC, original));
    }

    @Test
    void villageBoundsS2CEmptyPois() {
        var original = new VillageBoundsS2CPayload(
                BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO, List.of());
        assertEquals(original, roundTrip(VillageBoundsS2CPayload.CODEC, original));
    }

    @Test
    void configSyncS2C() {
        var original = new ConfigSyncS2CPayload(
                "{\"enableTradeCycling\":true,\"tradeCycleEmeraldCost\":6}");
        assertEquals(original, roundTrip(ConfigSyncS2CPayload.CODEC, original));
    }

    @Test
    void pylonStateS2C() {
        var original = new PylonStateS2CPayload(
                new BlockPos(100, 64, -50), 5, 8, false, true);
        assertEquals(original, roundTrip(PylonStateS2CPayload.CODEC, original));
    }

    @Test
    void pylonStateS2CEmpty() {
        var original = new PylonStateS2CPayload(BlockPos.ZERO, 0, 8, true, false);
        assertEquals(original, roundTrip(PylonStateS2CPayload.CODEC, original));
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
    void configSyncS2CRejectsOversizedString() {
        String oversized = "a".repeat(ConfigSyncS2CPayload.MAX_CONFIG_JSON_CHARS + 1);
        var payload = new ConfigSyncS2CPayload(oversized);
        FriendlyByteBuf buf = buf();
        assertThrows(EncoderException.class, () -> ConfigSyncS2CPayload.CODEC.encode(buf, payload));
    }

    @Test
    void villageBoundsS2CRejectsTooManyPois() {
        List<VillageBoundsS2CPayload.PoiEntry> pois = new java.util.ArrayList<>(VillageBoundsS2CPayload.MAX_POIS + 1);
        for (int i = 0; i < VillageBoundsS2CPayload.MAX_POIS + 1; i++) {
            pois.add(new VillageBoundsS2CPayload.PoiEntry(new BlockPos(i, 64, i), "bed", Optional.empty()));
        }
        var payload = new VillageBoundsS2CPayload(BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO, pois);
        FriendlyByteBuf buf = buf();
        assertThrows(EncoderException.class, () -> VillageBoundsS2CPayload.CODEC.encode(buf, payload));
    }

    @Test
    void villageBoundsS2CRejectsOversizedPoiType() {
        String hugeType = "x".repeat(VillageBoundsS2CPayload.MAX_POI_TYPE_LEN + 1);
        var pois = List.of(new VillageBoundsS2CPayload.PoiEntry(
                new BlockPos(0, 64, 0), hugeType, Optional.empty()));
        var payload = new VillageBoundsS2CPayload(BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO, pois);
        FriendlyByteBuf buf = buf();
        assertThrows(EncoderException.class, () -> VillageBoundsS2CPayload.CODEC.encode(buf, payload));
    }

    @Test
    void villageBoundsS2CDecodeRejectsBogusSize() {
        FriendlyByteBuf buf = buf();
        buf.writeBlockPos(BlockPos.ZERO);
        buf.writeBlockPos(BlockPos.ZERO);
        buf.writeBlockPos(BlockPos.ZERO);
        buf.writeVarInt(Integer.MAX_VALUE);
        assertThrows(DecoderException.class, () -> VillageBoundsS2CPayload.CODEC.decode(buf));
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
        assertEquals(FollowVillagerC2SPayload.TYPE, new FollowVillagerC2SPayload(0).type());
        assertEquals(RequestWorkstationMapC2SPayload.TYPE, new RequestWorkstationMapC2SPayload().type());
        assertEquals(RequestVillageBoundsC2SPayload.TYPE, new RequestVillageBoundsC2SPayload().type());
        assertEquals(SyncReputationS2CPayload.TYPE, new SyncReputationS2CPayload(0, "").type());
        assertEquals(FollowStateS2CPayload.TYPE, new FollowStateS2CPayload(0, false).type());
        assertEquals(RestockTimerS2CPayload.TYPE, new RestockTimerS2CPayload(0, 0, 0, false).type());
        assertEquals(DemandPriceS2CPayload.TYPE, new DemandPriceS2CPayload(0, List.of()).type());
        assertEquals(VillagerInfoPanelS2CPayload.TYPE,
                new VillagerInfoPanelS2CPayload(0, "", 0, 0, 0, 0, "", 0, false, false).type());
        assertEquals(WorkstationMapS2CPayload.TYPE, new WorkstationMapS2CPayload(Map.of(), List.of(), List.of()).type());
        assertEquals(VillageBoundsS2CPayload.TYPE,
                new VillageBoundsS2CPayload(BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO, List.of()).type());
        assertEquals(ConfigSyncS2CPayload.TYPE, new ConfigSyncS2CPayload("").type());
        assertEquals(PylonStateS2CPayload.TYPE,
                new PylonStateS2CPayload(BlockPos.ZERO, 0, 0, false, false).type());
    }
}
