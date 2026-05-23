package com.rfizzle.mercantile.compat.wthit;

import com.rfizzle.mercantile.compat.StateIndicatorData;
import com.rfizzle.mercantile.compat.StateIndicatorFormatter;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.component.ItemComponent;

import java.util.List;

public enum StateIndicatorWthitProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
        var raw = accessor.getData().raw();
        if (!raw.getBoolean(StateIndicatorData.KEY_PRESENT)) return;
        List<StateIndicatorFormatter.IndicatorLine> lines = StateIndicatorFormatter.format(raw);
        for (StateIndicatorFormatter.IndicatorLine line : lines) {
            tooltip.addLine()
                    .with(new ItemComponent(line.icon()))
                    .with(line.label());
        }
    }
}
