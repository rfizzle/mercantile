package com.rfizzle.mercantile.compat.shared;

import com.rfizzle.mercantile.block.SentryGolemTag;
import com.rfizzle.mercantile.block.SentryPylonBlockEntity;
import com.rfizzle.mercantile.config.MercantileConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class SentryGolemTooltipData {

    public static final String KEY_PRESENT = "mercantile:sentryGolemPresent";
    public static final String KEY_PYLON_POS = "mercantile:sentryPylonPos";
    public static final String KEY_PYLON_MISSING = "mercantile:sentryPylonMissing";
    public static final String KEY_DESPAWN_SECONDS = "mercantile:sentryDespawnSeconds";

    private SentryGolemTooltipData() {}

    public static void write(CompoundTag tag, IronGolem golem) {
        if (!SentryGolemTag.isSentry(golem)) return;

        tag.putBoolean(KEY_PRESENT, true);

        BlockPos pylonPos = SentryGolemTag.getPylonPos(golem);
        if (pylonPos == null) {
            tag.putBoolean(KEY_PYLON_MISSING, true);
            return;
        }
        tag.putLong(KEY_PYLON_POS, pylonPos.asLong());

        BlockEntity be = golem.level().getBlockEntity(pylonPos);
        if (!(be instanceof SentryPylonBlockEntity pylon)) {
            tag.putBoolean(KEY_PYLON_MISSING, true);
            return;
        }
        int maxDespawn = MercantileConfig.get().sentryDespawnSeconds;
        int thresholdTicks = maxDespawn * 20;
        int remainingTicks = Math.max(0, thresholdTicks - pylon.getIdleTicks());
        int remainingSeconds = Math.ceilDiv(remainingTicks, 20);
        tag.putInt(KEY_DESPAWN_SECONDS, remainingSeconds);
    }
}
