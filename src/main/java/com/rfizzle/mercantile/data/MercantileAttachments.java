package com.rfizzle.mercantile.data;

import com.mojang.serialization.Codec;
import com.rfizzle.mercantile.Mercantile;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;

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

    /**
     * The sentry golem's despawn-telegraph stage (0 = none, 1..3 = escalating cracks over the final
     * seconds of its pylon's despawn countdown). Written server-side by the parent pylon each tick and
     * synced to tracking clients so {@code IronGolemCrackinessMixin} can drive the vanilla crackiness
     * render layer. Not persisted — it is derived from the pylon's saved {@code idleTicks} and
     * recomputed the next tick after any reload. Any non-zero value implies a sentry (the pylon only
     * ever writes it to a tracked sentry), so the client render path keys off this rather than the
     * unsynced {@link #SENTRY_GOLEM_FLAG}.
     */
    public static final AttachmentType<Integer> SENTRY_DESPAWN_STAGE = AttachmentRegistry.<Integer>builder()
            .initializer(() -> 0)
            .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
            .buildAndRegister(Mercantile.id("sentry_despawn_stage"));

    public static void init() {
    }
}
