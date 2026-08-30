package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.gametest.util.MockPlayers;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Outbound-packet assertions over the {@link EmbeddedChannel} a
 * {@link MockPlayers.Connected} hands back. The channel comes from the mock
 * factory itself — {@code MockPlayers.connectedServerPlayerInLevel(helper)} —
 * so nothing here reflects into Mojang internals.
 */
final class GametestNetUtil {

    private GametestNetUtil() {
    }

    static <P extends CustomPacketPayload> int countPayloads(EmbeddedChannel channel, Class<P> payloadType) {
        int n = 0;
        for (Object msg : channel.outboundMessages()) {
            if (msg instanceof ClientboundCustomPayloadPacket custom
                    && payloadType.isInstance(custom.payload())) {
                n++;
            }
        }
        return n;
    }

    static <P extends CustomPacketPayload> P findUniquePayload(
            GameTestHelper helper, EmbeddedChannel channel, Class<P> payloadType) {
        P found = null;
        int matches = 0;
        for (Object msg : channel.outboundMessages()) {
            if (msg instanceof ClientboundCustomPayloadPacket custom
                    && payloadType.isInstance(custom.payload())) {
                found = payloadType.cast(custom.payload());
                matches++;
            }
        }
        if (matches != 1) {
            helper.fail("expected 1 " + payloadType.getSimpleName() + "; saw " + matches);
            return null;
        }
        return found;
    }
}
