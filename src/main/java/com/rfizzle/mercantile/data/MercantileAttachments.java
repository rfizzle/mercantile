package com.rfizzle.mercantile.data;

import com.mojang.serialization.Codec;
import com.rfizzle.mercantile.Mercantile;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public class MercantileAttachments {
    public static final AttachmentType<PlayerData> PLAYER_DATA = AttachmentRegistry.<PlayerData>builder()
            .persistent(PlayerData.CODEC)
            .copyOnDeath()
            .initializer(PlayerData::new)
            .buildAndRegister(Mercantile.id("player_data"));

    public static final AttachmentType<MercantileVillagerData> VILLAGER_DATA = AttachmentRegistry.<MercantileVillagerData>builder()
            .persistent(MercantileVillagerData.CODEC)
            .initializer(MercantileVillagerData::new)
            .buildAndRegister(Mercantile.id("villager_data"));

    public static final AttachmentType<Boolean> SENTRY_GOLEM_FLAG = AttachmentRegistry.<Boolean>builder()
            .persistent(Codec.BOOL)
            .initializer(() -> Boolean.FALSE)
            .buildAndRegister(Mercantile.id("sentry_golem"));

    public static final long SENTRY_PYLON_POS_UNSET = Long.MIN_VALUE;

    public static final AttachmentType<Long> SENTRY_PYLON_POS = AttachmentRegistry.<Long>builder()
            .persistent(Codec.LONG)
            .initializer(() -> SENTRY_PYLON_POS_UNSET)
            .buildAndRegister(Mercantile.id("sentry_pylon_pos"));

    public static void init() {
    }
}
