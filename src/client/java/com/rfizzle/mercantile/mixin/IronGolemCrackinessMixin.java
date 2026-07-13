package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.data.MercantileAttachments;
import net.minecraft.world.entity.Crackiness;
import net.minecraft.world.entity.animal.IronGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drives the despawn telegraph (spec §18): while a sentry golem's parent pylon is winding it down,
 * the pylon writes an escalating {@link MercantileAttachments#SENTRY_DESPAWN_STAGE} (synced), and this
 * mixin feeds it into the vanilla crackiness render layer so the golem visibly cracks apart over the
 * countdown's final seconds instead of vanishing intact.
 *
 * <p>Client-scoped: this is a client mixin, so it never loads on a dedicated server. {@code
 * getCrackiness()} is called by the crackiness render layer but also by {@code IronGolem.hurt} on the
 * authoritative thread (which on an integrated server shares this JVM), so the override is gated to
 * the client level — the telegraph is a purely visual overlay and must not perturb the server's
 * crack-transition damage-sound logic. The stage attachment is the sole gate otherwise: it is only
 * ever non-zero on a sentry (the pylon writes it nowhere else), so the unsynced sentry flag isn't
 * needed here. The telegraph stage only ever <em>increases</em> the shown cracks: a golem already
 * cracked from combat keeps its heavier damage overlay.
 */
@Mixin(IronGolem.class)
public abstract class IronGolemCrackinessMixin {
    private static final Crackiness.Level[] mercantile$STAGE_LEVELS = {
            Crackiness.Level.NONE,
            Crackiness.Level.LOW,
            Crackiness.Level.MEDIUM,
            Crackiness.Level.HIGH,
    };

    @Inject(method = "getCrackiness", at = @At("RETURN"), cancellable = true)
    private void mercantile$telegraphDespawn(CallbackInfoReturnable<Crackiness.Level> cir) {
        IronGolem self = (IronGolem) (Object) this;
        if (!self.level().isClientSide()) return;
        Integer stage = self.getAttached(MercantileAttachments.SENTRY_DESPAWN_STAGE);
        if (stage == null || stage <= 0) return;
        int clamped = Math.min(stage, mercantile$STAGE_LEVELS.length - 1);
        Crackiness.Level telegraph = mercantile$STAGE_LEVELS[clamped];
        if (telegraph.ordinal() > cir.getReturnValue().ordinal()) {
            cir.setReturnValue(telegraph);
        }
    }
}
