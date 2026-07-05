package com.rfizzle.mercantile.compat.wthit;

import com.rfizzle.mercantile.compat.shared.MoodTooltipData;
import com.rfizzle.mercantile.compat.shared.MoodTooltipFormatter;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.network.chat.Component;

import java.util.List;

public enum MoodWthitProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
        var raw = accessor.getData().raw();
        if (!raw.getBoolean(MoodTooltipData.KEY_PRESENT)) return;
        List<Component> lines = MoodTooltipFormatter.format(raw);
        for (Component line : lines) {
            tooltip.addLine(line);
        }
    }
}
