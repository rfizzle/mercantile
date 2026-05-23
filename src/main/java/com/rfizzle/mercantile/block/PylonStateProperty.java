package com.rfizzle.mercantile.block;

import net.minecraft.util.StringRepresentable;

public enum PylonStateProperty implements StringRepresentable {
    IDLE("idle"),
    ACTIVE("active"),
    EMPTY("empty");

    private final String name;

    PylonStateProperty(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
