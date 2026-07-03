package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.mood.MoodManager;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(Villager.class)
public abstract class VillagerRestockMoodMixin {

    // Scales the vanilla 2400-tick gap between a villager's restocks by mood:
    // Happy villagers restock sooner, Miserable ones later. The effective interval
    // is mirrored to the client via RestockTimerS2CPayload so the countdown stays accurate.
    @ModifyConstant(method = "allowedToRestock", constant = @Constant(longValue = 2400L))
    private long mercantile$scaleRestockIntervalByMood(long original) {
        return MoodManager.restockIntervalTicks((Villager) (Object) this, original);
    }
}
