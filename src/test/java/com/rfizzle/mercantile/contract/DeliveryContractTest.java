package com.rfizzle.mercantile.contract;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryContractTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final ResourceLocation WHEAT = ResourceLocation.withDefaultNamespace("wheat");

    @Test
    void codecRoundTripsAllFields() {
        DeliveryContract original = new DeliveryContract(ID, WHEAT, 32, 5, true, 48_000L);

        var encoded = DeliveryContract.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg));
        DeliveryContract decoded = DeliveryContract.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(msg -> new AssertionError("decode failed: " + msg));

        assertEquals(original, decoded);
    }

    @Test
    void codecDefaultsOptionalFields() {
        // A blob written before accepted/expiryGameTime existed must still parse.
        var partial = DeliveryContract.CODEC.encodeStart(JsonOps.INSTANCE,
                        new DeliveryContract(ID, WHEAT, 8, 2, false, -1L))
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg))
                .getAsJsonObject();
        partial.remove("accepted");
        partial.remove("expiryGameTime");

        DeliveryContract decoded = DeliveryContract.CODEC.parse(JsonOps.INSTANCE, partial)
                .getOrThrow(msg -> new AssertionError("decode failed: " + msg));

        assertFalse(decoded.accepted());
        assertEquals(-1L, decoded.expiryGameTime());
    }

    @Test
    void constructorClampsUntrustedValues() {
        DeliveryContract tampered = new DeliveryContract(ID, WHEAT, -5, 999_999, false, 0L);
        assertEquals(1, tampered.count(), "count clamps up to 1");
        assertEquals(DeliveryContract.MAX_PAYMENT, tampered.payment(), "payment clamps to the cap");

        DeliveryContract oversized = new DeliveryContract(ID, WHEAT, 5_000, -3, false, 0L);
        assertEquals(DeliveryContract.MAX_COUNT, oversized.count(), "count clamps to the cap");
        assertEquals(0, oversized.payment(), "payment clamps up to 0");
    }

    @Test
    void expiryIsInclusiveOfTheDeadlineTick() {
        DeliveryContract contract = new DeliveryContract(ID, WHEAT, 8, 2, true, 24_000L);
        assertFalse(contract.isExpired(23_999L));
        assertTrue(contract.isExpired(24_000L), "the deadline tick itself counts as expired");
        assertTrue(contract.isExpired(30_000L));
    }

    @Test
    void unsetExpiryNeverExpires() {
        DeliveryContract contract = new DeliveryContract(ID, WHEAT, 8, 2, false, -1L);
        assertFalse(contract.isExpired(Long.MAX_VALUE));
    }

    @Test
    void acceptKeepsTermsAndStampsDeadline() {
        DeliveryContract offer = new DeliveryContract(ID, WHEAT, 16, 4, false, 6_000L);
        DeliveryContract accepted = offer.accept(60_000L);

        assertTrue(accepted.accepted());
        assertEquals(60_000L, accepted.expiryGameTime());
        assertEquals(offer.id(), accepted.id());
        assertEquals(offer.itemId(), accepted.itemId());
        assertEquals(offer.count(), accepted.count());
        assertEquals(offer.payment(), accepted.payment());
    }

    @Test
    void paymentScalesByPercent() {
        assertEquals(6, DeliveryContract.scalePayment(6, 100), "100% = as authored");
        assertEquals(3, DeliveryContract.scalePayment(6, 50));
        assertEquals(12, DeliveryContract.scalePayment(6, 200));
        assertEquals(0, DeliveryContract.scalePayment(6, 0), "0% zeroes the payment");
        assertEquals(DeliveryContract.MAX_PAYMENT, DeliveryContract.scalePayment(4_000, 1_000),
                "scaling can never exceed the payment cap");
    }

    @Test
    void deadlineDayIsTheCalendarDayOfTheDeadline() {
        assertEquals(0, DeliveryContract.deadlineDay(12_000L));
        assertEquals(2, DeliveryContract.deadlineDay(48_000L));
        assertEquals(2, DeliveryContract.deadlineDay(71_999L));
    }
}
