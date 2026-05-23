package com.rfizzle.mercantile.compat.jade;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.compat.StateIndicatorData;
import com.rfizzle.mercantile.compat.StateIndicatorFormatter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.List;

public enum StateIndicatorComponentProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof Villager)) return;
        CompoundTag data = accessor.getServerData();
        if (!data.getBoolean(StateIndicatorData.KEY_PRESENT)) return;

        List<StateIndicatorFormatter.IndicatorLine> lines = StateIndicatorFormatter.format(data);
        if (lines.isEmpty()) return;

        IElementHelper helper = IElementHelper.get();
        for (StateIndicatorFormatter.IndicatorLine line : lines) {
            tooltip.add(List.of(
                    helper.smallItem(line.icon()),
                    helper.text(line.label())
            ));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return Mercantile.id("state_indicators");
    }
}
