package com.rfizzle.mercantile.compat.wthit;

import com.rfizzle.mercantile.compat.BreedingTooltipData;
import com.rfizzle.mercantile.compat.BreedingTooltipFormatter;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.network.chat.Component;

import java.util.List;

public enum BreedingWthitProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
        var raw = accessor.getData().raw();
        if (!raw.getBoolean(BreedingTooltipData.KEY_PRESENT)) return;
        // WTHIT has no native growing-time line, so keep ours.
        List<Component> lines = BreedingTooltipFormatter.format(raw, true);
        for (Component line : lines) {
            tooltip.addLine(line);
        }
    }
}
