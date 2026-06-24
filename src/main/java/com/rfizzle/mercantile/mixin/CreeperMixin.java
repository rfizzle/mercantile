package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.block.SentryGolemTag;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Creeper.class)
public abstract class CreeperMixin {

    /**
     * Keep a creeper from priming when it is fighting a sentry golem. A sentry will provoke the
     * creeper into a revenge target; left alone it would swell and detonate, killing the golem and
     * cratering whatever the pylon was guarding. Forcing the swell direction negative whenever the
     * target is a sentry lets the swell decay back to zero so it never reaches the fuse.
     *
     * <p>Manual ignition (flint &amp; steel) still works — that path is player intent and sets
     * {@code isIgnited()}, which this leaves untouched. A creeper that retargets a player swells
     * and explodes as normal.
     */
    @ModifyVariable(method = "setSwellDir", at = @At("HEAD"), argsOnly = true)
    private int mercantile$dontDetonateOnSentry(int dir) {
        Creeper self = (Creeper) (Object) this;
        if (dir > 0 && !self.isIgnited() && SentryGolemTag.isSentry(self.getTarget())) {
            return -1;
        }
        return dir;
    }
}
