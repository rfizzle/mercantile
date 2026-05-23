package com.rfizzle.mercantile.compat.wthit;

import com.rfizzle.mercantile.config.MercantileConfig;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public enum SentryPylonWthitProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
        if (!MercantileConfig.get().enableSentryPylon) return;
        CompoundTag data = accessor.getData().raw();
        if (!data.contains(SentryPylonWthitDataProvider.KEY_FUEL)) return;

        int fuel = data.getInt(SentryPylonWthitDataProvider.KEY_FUEL);
        int maxFuel = data.getInt(SentryPylonWthitDataProvider.KEY_MAX_FUEL);
        int sentries = data.getInt(SentryPylonWthitDataProvider.KEY_SENTRIES);
        int maxSentries = data.getInt(SentryPylonWthitDataProvider.KEY_MAX_SENTRIES);

        Component fuelLine = Component.translatable("tooltip.mercantile.pylon.fuel", fuel, maxFuel);
        if (fuel == 0) {
            fuelLine = Component.translatable("tooltip.mercantile.pylon.empty")
                    .withStyle(ChatFormatting.RED);
        }
        tooltip.addLine(fuelLine);
        tooltip.addLine(Component.translatable("tooltip.mercantile.pylon.sentries", sentries, maxSentries));
    }
}
