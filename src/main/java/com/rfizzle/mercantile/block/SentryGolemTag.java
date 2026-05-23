package com.rfizzle.mercantile.block;

import com.rfizzle.mercantile.data.MercantileAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;

public final class SentryGolemTag {
    private SentryGolemTag() {
    }

    public static void markAsSentry(IronGolem golem, BlockPos pylonPos) {
        golem.setAttached(MercantileAttachments.SENTRY_GOLEM_FLAG, Boolean.TRUE);
        golem.setAttached(MercantileAttachments.SENTRY_PYLON_POS, pylonPos.asLong());
    }

    public static boolean isSentry(Entity entity) {
        if (!(entity instanceof IronGolem)) return false;
        Boolean flag = entity.getAttached(MercantileAttachments.SENTRY_GOLEM_FLAG);
        return flag != null && flag;
    }

    public static BlockPos getPylonPos(IronGolem golem) {
        Long packed = golem.getAttached(MercantileAttachments.SENTRY_PYLON_POS);
        if (packed == null || packed == MercantileAttachments.SENTRY_PYLON_POS_UNSET) return null;
        return BlockPos.of(packed);
    }
}
