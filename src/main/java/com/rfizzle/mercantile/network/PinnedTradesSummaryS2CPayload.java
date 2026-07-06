package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.data.PlayerData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The receiving player's full pinned-trade list with each pin's current stock status —
 * the player-scoped, persistent counterpart to {@link TradePinsS2CPayload} (which is scoped
 * to one open merchant screen). Sent on join and after every change to the player's pins
 * (toggle, command remove/clear, prune, restock), so the reputation detail panel can list
 * pins across villages without a screen open. Snapshots (name, summary) are the same
 * server-locale strings {@code /mercantile pins} prints.
 */
public record PinnedTradesSummaryS2CPayload(List<Entry> pins) implements CustomPacketPayload {

    /** One pinned trade: the villager and trade snapshots plus a {@link Status} ordinal. */
    public record Entry(String villagerName, String tradeSummary, int status) {

        public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(PinTradeName.MAX),
                Entry::villagerName,
                ByteBufCodecs.stringUtf8(PinTradeName.MAX),
                Entry::tradeSummary,
                ByteBufCodecs.VAR_INT,
                Entry::status,
                Entry::new);
    }

    /** Stock status of a pin; ordinal is what {@link Entry#status()} carries over the wire. */
    public enum Status {
        IN_STOCK,
        OUT_OF_STOCK,
        UNKNOWN;

        private static final Status[] VALUES = values();

        public static Status fromOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : UNKNOWN;
        }
    }

    // Bounds the wire string length; snapshots are already clamped shorter at pin time.
    private static final class PinTradeName {
        private static final int MAX = 256;
    }

    public static final Type<PinnedTradesSummaryS2CPayload> TYPE =
            new Type<>(Mercantile.id("pinned_trades_summary_s2c"));

    public static final StreamCodec<ByteBuf, PinnedTradesSummaryS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(ArrayList::new, Entry.CODEC, PlayerData.MAX_PINNED_TRADES),
                    PinnedTradesSummaryS2CPayload::pins,
                    PinnedTradesSummaryS2CPayload::new);

    /** An empty summary, sent to clear the client list when pinning is disabled. */
    public static final PinnedTradesSummaryS2CPayload EMPTY =
            new PinnedTradesSummaryS2CPayload(List.of());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
