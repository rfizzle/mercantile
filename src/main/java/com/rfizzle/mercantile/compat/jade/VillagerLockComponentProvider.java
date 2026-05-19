package com.rfizzle.mercantile.compat.jade;

import com.rfizzle.mercantile.Mercantile;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum VillagerLockComponentProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof Villager villager)) return;
        if (villager.getVillagerData().getProfession() == VillagerProfession.NONE) return;

        var serverData = accessor.getServerData();
        if (!serverData.contains("mercantile:professionLocked")) return;

        boolean locked = serverData.getBoolean("mercantile:professionLocked");
        String key = locked
                ? "gui.mercantile.profession.locked"
                : "gui.mercantile.profession.unlocked";
        tooltip.add(Component.translatable(key));
    }

    @Override
    public ResourceLocation getUid() {
        return Mercantile.id("profession_lock");
    }
}
