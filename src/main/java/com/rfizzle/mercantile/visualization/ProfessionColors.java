package com.rfizzle.mercantile.visualization;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public final class ProfessionColors {

    private static final Vector3f DEFAULT = rgb(0xAAAAAA);

    private static final Map<ResourceLocation, Vector3f> BY_ID = new HashMap<>();

    static {
        put("armorer", 0x8B8B8B);        // blast furnace — dark gray
        put("butcher", 0xC97A55);        // smoker — orange-brown
        put("cartographer", 0xDCD0B0);   // cartography table — pale tan
        put("cleric", 0x6E3F87);         // brewing stand — purple-ish
        put("farmer", 0x8B5A2B);         // composter — brown
        put("fisherman", 0x6F9FCC);      // barrel/water — light blue
        put("fletcher", 0xC4A464);       // fletching table — light tan
        put("leatherworker", 0xA86B3D);  // cauldron contents / leather — tan-brown
        put("librarian", 0x6D4B2C);      // lectern — brown
        put("mason", 0xC9C2BB);          // stonecutter — light gray
        put("shepherd", 0xE0E0E0);       // loom — white-cream
        put("toolsmith", 0x6F6F6F);      // smithing table — dark gray
        put("weaponsmith", 0x8E8E8E);    // grindstone — medium gray
    }

    private ProfessionColors() {
    }

    public static Vector3f lookup(VillagerProfession profession) {
        if (profession == null) return DEFAULT;
        ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        return lookup(id);
    }

    public static Vector3f lookup(ResourceLocation id) {
        if (id == null) return DEFAULT;
        Vector3f color = BY_ID.get(id);
        return color != null ? color : DEFAULT;
    }

    public static Vector3f defaultColor() {
        return DEFAULT;
    }

    private static void put(String path, int rgb) {
        BY_ID.put(ResourceLocation.withDefaultNamespace(path), rgb(rgb));
    }

    private static Vector3f rgb(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        return new Vector3f(r, g, b);
    }
}
