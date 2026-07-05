package com.rfizzle.mercantile.compat.wthit;

import com.rfizzle.mercantile.compat.shared.SentryGolemTooltipData;
import com.rfizzle.mercantile.config.MercantileConfig;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public enum SentryGolemWthitProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
        if (!MercantileConfig.get().enableSentryPylon) return;
        CompoundTag data = accessor.getData().raw();
        if (!data.getBoolean(SentryGolemTooltipData.KEY_PRESENT)) return;

        tooltip.addLine(Component.translatable("tooltip.mercantile.sentry_golem.label")
                .withStyle(ChatFormatting.GOLD));

        boolean missing = data.getBoolean(SentryGolemTooltipData.KEY_PYLON_MISSING);
        if (missing) {
            tooltip.addLine(Component.translatable("tooltip.mercantile.sentry_golem.pylon_missing")
                    .withStyle(ChatFormatting.RED));
        } else {
            BlockPos pos = BlockPos.of(data.getLong(SentryGolemTooltipData.KEY_PYLON_POS));
            tooltip.addLine(Component.translatable("tooltip.mercantile.sentry_golem.pylon",
                    pos.getX(), pos.getY(), pos.getZ()));
            int seconds = data.getInt(SentryGolemTooltipData.KEY_DESPAWN_SECONDS);
            tooltip.addLine(Component.translatable("tooltip.mercantile.sentry_golem.despawn", seconds));
        }

        tooltip.addLine(Component.translatable("tooltip.mercantile.sentry_golem.no_drops")
                .withStyle(ChatFormatting.GRAY));
    }
}
