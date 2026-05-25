package com.rfizzle.mercantile.data;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.rfizzle.mercantile.Mercantile;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VillagerHeadTextures {
    private static final Map<ResourceLocation, String> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, ResolvableProfile> PROFILE_CACHE = new ConcurrentHashMap<>();
    // Guards TEXTURES + PROFILE_CACHE as a coherent pair (B-083). A concurrent
    // register/read otherwise interleaves the put + remove with computeIfAbsent
    // and either caches a profile against the new texture only to immediately
    // evict it, or serves a profile built from a stale texture.
    private static final Object LOCK = new Object();

    public static final ResourceLocation FALLBACK_ID = ResourceLocation.withDefaultNamespace("none");
    public static final ResourceLocation BABY = Mercantile.id("baby");

    static {
        registerVanilla("armorer", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjUyMmRiOTJmMTg4ZWJjNzcxM2NmMzViNGNiYWVkMWNmZTI2NDJhNTk4NmMzYmRlOTkzZjVjZmIzNzI3NjY0YyJ9fX0=");
        registerVanilla("butcher", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzY3NzRkMmRmNTE1ZWNlYWU5ZWVkMjkxYzFiNDBmOTRhZGY3MWRmMGFiODFjNzE5MTQwMmUxYTQ1YjNhMjA4NyJ9fX0=");
        registerVanilla("cartographer", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTQyNDhkZDA2ODAzMDVhZDczYjIxNGU4YzZiMDAwOTRlMjdhNGRkZDgwMzQ2NzY5MjFmOTA1MTMwYjg1OGJkYiJ9fX0=");
        registerVanilla("cleric", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTg4NTZlYWFmYWQ5NmQ3NmZhM2I1ZWRkMGUzYjVmNDVlZTQ5YTMwNjczMDZhZDk0ZGY5YWIzYmQ1YjJkMTQyZCJ9fX0=");
        registerVanilla("farmer", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTVhMGIwN2UzNmVhZmRlY2YwNTljOGNiMTM0YTdiZjBhMTY3ZjkwMDk2NmYxMDk5MjUyZDkwMzI3NjQ2MWNjZSJ9fX0=");
        registerVanilla("fisherman", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWMxNWU1ZmI1NmZhMTZiMDc0N2IxYmNiMDUzMzVmNTVkMWZhMzE1NjFjMDgyYjVlMzY0M2RiNTU2NTQxMDg1MiJ9fX0=");
        registerVanilla("fletcher", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTc1MzJlOTBjNTczYTM5NGM3ODAyYWE0MTU4MzA1ODAyYjU5ZTY3ZjJhMmI3ZTNmZDAzNjNhYTZlYTQyYjg0MSJ9fX0=");
        registerVanilla("leatherworker", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjc2Y2Y4YjczNzhlODg5Mzk1ZDUzOGU2MzU0YTE3YTNkZTZiMjk0YmI2YmY4ZGI5YzcwMTk1MWM2OGQzYzBlNiJ9fX0=");
        registerVanilla("librarian", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTY2YTUzZmM3MDdjZTFmZjg4YTU3NmVmNDAyMDBjZThkNDlmYWU0YWNhZDFlM2IzNzg5YzdkMWNjMWNjNTQxYSJ9fX0=");
        registerVanilla("mason", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmMwMmMzZmZkNTcwNWFiNDg4YjMwNWQ1N2ZmMDE2OGUyNmRlNzBmZDNmNzM5ZTgzOTY2MWFiOTQ3ZGZmMzdiMSJ9fX0=");
        registerVanilla("shepherd", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTllMDRhNzUyNTk2ZjkzOWY1ODE5MzA0MTQ1NjFiMTc1NDU0ZDQ1YTA1MDY1MDFlN2QyNDg4Mjk1YTVkNWRlIn19fQ==");
        registerVanilla("toolsmith", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2RmYTA3ZmQxMjQ0ZWI4OTQ1ZjRlZGVkZDAwNDI2NzUwYjc3ZWY1ZGZiYWYwM2VkNzc1NjMzNDU5ZWNlNDE1YSJ9fX0=");
        registerVanilla("weaponsmith", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWU0MDliOTU4YmM0ZmUwNDVlOTVkMzI1ZTZlOTdhNTMzMTM3ZTMzZmVjNzA0MmFjMDI3YjMwYmI2OTNhOWQ0MiJ9fX0=");
        registerVanilla("nitwit", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODIyZDhlNzUxYzhmMmZkNGM4OTQyYzQ0YmRiMmY1Y2E0ZDhhZThlNTc1ZWQzZWIzNGMxOGE4NmU5M2IifX19");
        registerVanilla("none", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODIyZDhlNzUxYzhmMmZkNGM4OTQyYzQ0YmRiMmY1Y2E0ZDhhZThlNTc1ZWQzZWIzNGMxOGE4NmU5M2IifX19");
        TEXTURES.put(BABY, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2UwY2Y0MDc3ZjMzZmI0MDU3MjA0YzQyOTc1YzhjMTg4MzBjMDc1ZGM3OWMwYjJkM2M4MDgyY2UzY2RjNjgxZiJ9fX0=");
    }

    private static void registerVanilla(String profession, String base64Value) {
        TEXTURES.put(ResourceLocation.withDefaultNamespace(profession), base64Value);
    }

    public static void register(ResourceLocation professionId, String base64TextureValue) {
        synchronized (LOCK) {
            TEXTURES.put(professionId, base64TextureValue);
            PROFILE_CACHE.remove(professionId);
        }
    }

    public static String getTextureValue(ResourceLocation professionId) {
        synchronized (LOCK) {
            return TEXTURES.getOrDefault(professionId, TEXTURES.get(FALLBACK_ID));
        }
    }

    public static ResolvableProfile getProfile(ResourceLocation professionId) {
        synchronized (LOCK) {
            ResourceLocation key = TEXTURES.containsKey(professionId) ? professionId : FALLBACK_ID;
            return PROFILE_CACHE.computeIfAbsent(key, id -> {
                UUID uuid = UUID.nameUUIDFromBytes(id.toString().getBytes(StandardCharsets.UTF_8));
                GameProfile profile = new GameProfile(uuid, "");
                profile.getProperties().put("textures", new Property("textures", TEXTURES.get(id)));
                return new ResolvableProfile(profile);
            });
        }
    }

    public static Component getDisplayName(ResourceLocation professionId) {
        String key = "entity." + professionId.getNamespace() + ".villager." + professionId.getPath();
        return Component.translatableWithFallback(key, formatRawId(professionId.getPath()));
    }

    static String formatRawId(String path) {
        String[] words = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(' ');
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }

    public static void init() {
    }
}
