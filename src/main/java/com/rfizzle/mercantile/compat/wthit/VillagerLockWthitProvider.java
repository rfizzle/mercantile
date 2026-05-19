package com.rfizzle.mercantile.compat.wthit;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import mcp.mobius.waila.api.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public enum VillagerLockWthitProvider implements IEntityComponentProvider {
    INSTANCE;

    private static final String KEY = "mercantile:professionLocked";

    @Override
    public void appendDataContext(IDataWriter data, IEntityAccessor accessor, IPluginConfig config) {
        if (!MercantileConfig.get().enableProfessionLock) return;
        if (!(accessor.<Villager>getEntity() instanceof Villager villager)) return;

        var modData = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        data.raw().putBoolean(KEY, modData.isProfessionLocked());
    }

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
