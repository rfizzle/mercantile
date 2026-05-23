package com.rfizzle.mercantile.gametest;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

import java.lang.reflect.Field;

/**
 * Reflection helpers shared between gametests that need to introspect the
 * {@link EmbeddedChannel} sitting under a {@code makeMockServerPlayerInLevel()}
 * connection. Both fields touched here are Mojang-internal — a mapping rename
 * should fail in exactly one place.
 */
final class GametestNetUtil {

    private GametestNetUtil() {
    }

    static EmbeddedChannel extractEmbeddedChannel(GameTestHelper helper, ServerPlayer player) {
        Connection connection;
        try {
            Field connField = ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
            connField.setAccessible(true);
            connection = (Connection) connField.get(player.connection);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            helper.fail("ServerCommonPacketListenerImpl.connection field not accessible: " + e);
            throw new AssertionError(e);
        }
        try {
            Field channelField = Connection.class.getDeclaredField("channel");
            channelField.setAccessible(true);
            Object channel = channelField.get(connection);
            if (!(channel instanceof EmbeddedChannel embedded)) {
                helper.fail("Mock player connection channel is not EmbeddedChannel; got "
                        + (channel == null ? "null" : channel.getClass().getName()));
                throw new AssertionError("not embedded");
            }
            return embedded;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            helper.fail("Could not access Connection.channel: " + e);
            throw new AssertionError(e);
        }
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
