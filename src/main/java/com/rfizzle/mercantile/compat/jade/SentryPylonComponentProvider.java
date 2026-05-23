package com.rfizzle.mercantile.compat.jade;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.config.MercantileConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum SentryPylonComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!MercantileConfig.get().enableSentryPylon) return;
        CompoundTag data = accessor.getServerData();
        if (!data.contains(SentryPylonDataProvider.KEY_FUEL)) return;

        int fuel = data.getInt(SentryPylonDataProvider.KEY_FUEL);
        int maxFuel = data.getInt(SentryPylonDataProvider.KEY_MAX_FUEL);
        int sentries = data.getInt(SentryPylonDataProvider.KEY_SENTRIES);
        int maxSentries = data.getInt(SentryPylonDataProvider.KEY_MAX_SENTRIES);

        Component fuelLine = Component.translatable("tooltip.mercantile.pylon.fuel", fuel, maxFuel);
        if (fuel == 0) {
            fuelLine = Component.translatable("tooltip.mercantile.pylon.empty")
                    .withStyle(ChatFormatting.RED);
        }
        tooltip.add(fuelLine);
        tooltip.add(Component.translatable("tooltip.mercantile.pylon.sentries", sentries, maxSentries));
    }

    @Override
    public ResourceLocation getUid() {
        return Mercantile.id("sentry_pylon");
    }
}
