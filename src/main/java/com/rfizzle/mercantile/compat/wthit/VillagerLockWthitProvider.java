package com.rfizzle.mercantile.compat.wthit;

import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public enum VillagerLockWthitProvider implements IEntityComponentProvider {
    INSTANCE;

    static final String KEY = "mercantile:professionLocked";

    @Override
    public void appendBody(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
        Villager villager = accessor.getEntity();
        if (villager.getVillagerData().getProfession() == VillagerProfession.NONE) return;

        var raw = accessor.getData().raw();
        if (!raw.contains(KEY)) return;

        boolean locked = raw.getBoolean(KEY);
        String key = locked
                ? "gui.mercantile.profession.locked"
                : "gui.mercantile.profession.unlocked";
        tooltip.addLine(Component.translatable(key));
    }
}
