package com.rfizzle.mercantile.data;

import com.rfizzle.mercantile.Mercantile;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public class MercantileAttachments {
    public static final AttachmentType<PlayerData> PLAYER_DATA = AttachmentRegistry.<PlayerData>builder()
            .persistent(PlayerData.CODEC)
            .copyOnDeath()
            .initializer(PlayerData::new)
            .buildAndRegister(Mercantile.id("player_data"));

    public static final AttachmentType<VillagerData> VILLAGER_DATA = AttachmentRegistry.<VillagerData>builder()
            .persistent(VillagerData.CODEC)
            .initializer(VillagerData::new)
            .buildAndRegister(Mercantile.id("villager_data"));

    public static void init() {
    }
}
