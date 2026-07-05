package com.rfizzle.mercantile.compat.jade;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.compat.shared.BreedingTooltipData;
import com.rfizzle.mercantile.compat.shared.BreedingTooltipFormatter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;

public enum BreedingComponentProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof Villager)) return;
        CompoundTag data = accessor.getServerData();
        if (!data.getBoolean(BreedingTooltipData.KEY_PRESENT)) return;

        // Jade natively renders a "Growing time" line for baby mobs — skip ours.
        List<Component> lines = BreedingTooltipFormatter.format(data, false);
        for (Component line : lines) {
            tooltip.add(line);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return Mercantile.id("breeding");
    }
}
