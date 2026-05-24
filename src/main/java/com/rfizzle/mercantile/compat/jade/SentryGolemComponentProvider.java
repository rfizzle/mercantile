package com.rfizzle.mercantile.compat.jade;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.compat.SentryGolemTooltipData;
import com.rfizzle.mercantile.config.MercantileConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum SentryGolemComponentProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!MercantileConfig.get().enableSentryPylon) return;
        CompoundTag data = accessor.getServerData();
        if (!data.getBoolean(SentryGolemTooltipData.KEY_PRESENT)) return;

        tooltip.add(Component.translatable("tooltip.mercantile.sentry_golem.label")
                .withStyle(ChatFormatting.GOLD));

        boolean missing = data.getBoolean(SentryGolemTooltipData.KEY_PYLON_MISSING);
        if (missing) {
            tooltip.add(Component.translatable("tooltip.mercantile.sentry_golem.pylon_missing")
                    .withStyle(ChatFormatting.RED));
        } else {
            BlockPos pos = BlockPos.of(data.getLong(SentryGolemTooltipData.KEY_PYLON_POS));
            tooltip.add(Component.translatable("tooltip.mercantile.sentry_golem.pylon",
                    pos.getX(), pos.getY(), pos.getZ()));
            int seconds = data.getInt(SentryGolemTooltipData.KEY_DESPAWN_SECONDS);
            tooltip.add(Component.translatable("tooltip.mercantile.sentry_golem.despawn", seconds));
        }

        tooltip.add(Component.translatable("tooltip.mercantile.sentry_golem.no_drops")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public ResourceLocation getUid() {
        return Mercantile.id("sentry_golem");
    }
}
