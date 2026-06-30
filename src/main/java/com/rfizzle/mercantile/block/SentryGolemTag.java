package com.rfizzle.mercantile.block;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;

public final class SentryGolemTag {
    private SentryGolemTag() {
    }

    public static void markAsSentry(IronGolem golem, BlockPos pylonPos) {
        golem.setAttached(MercantileAttachments.SENTRY_GOLEM_FLAG, Boolean.TRUE);
        golem.setAttached(MercantileAttachments.SENTRY_PYLON_POS, pylonPos.asLong());
        // Match the navigable range to the defended radius. Vanilla sizes a mob's pathable region
        // off FOLLOW_RANGE, so without this a sentry pushed past the default ~32 blocks can't plot a
        // path the whole way back to its pylon. It also widens the golem's awareness to match the
        // pylon scan radius (the target goal pins the effective aggro to pylonDetectionRadius).
        AttributeInstance followRange = golem.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(MercantileConfig.get().pylonDetectionRadius);
        }
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
