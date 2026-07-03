package com.rfizzle.mercantile.compat.jade;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.compat.MoodTooltipData;
import com.rfizzle.mercantile.compat.MoodTooltipFormatter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;

public enum MoodComponentProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof Villager)) return;
        CompoundTag data = accessor.getServerData();
        if (!data.getBoolean(MoodTooltipData.KEY_PRESENT)) return;

        List<Component> lines = MoodTooltipFormatter.format(data);
        for (Component line : lines) {
            tooltip.add(line);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return Mercantile.id("mood");
    }
}
