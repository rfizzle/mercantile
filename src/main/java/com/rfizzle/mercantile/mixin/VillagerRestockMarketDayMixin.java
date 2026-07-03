package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.market.MarketDayManager;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(Villager.class)
public abstract class VillagerRestockMarketDayMixin {

    // Raises the vanilla two-restocks-per-day cap during market day so villagers get one
    // extra restock cycle. The effective cap is mirrored to the client via
    // RestockTimerS2CPayload so the "Restocks: x/y" indicator stays accurate.
    @ModifyConstant(method = "allowedToRestock", constant = @Constant(intValue = 2))
    private int mercantile$raiseRestockCapOnMarketDay(int original) {
        return MarketDayManager.maxRestocksToday((Villager) (Object) this);
    }
}
