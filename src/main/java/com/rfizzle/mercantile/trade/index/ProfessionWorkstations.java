package com.rfizzle.mercantile.trade.index;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ProfessionWorkstations {

    private static volatile Map<ResourceLocation, Block> CACHE;

    private ProfessionWorkstations() {
    }

    public static Block forProfession(ResourceLocation professionId) {
        if (professionId == null) return null;
        return cache().get(professionId);
    }

    public static Optional<Block> get(VillagerProfession profession) {
        if (profession == null) return Optional.empty();
        ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(cache().get(id));
    }

    public static Map<ResourceLocation, Block> snapshot() {
        return cache();
    }

    private static Map<ResourceLocation, Block> cache() {
        Map<ResourceLocation, Block> local = CACHE;
        if (local != null) return local;
        return buildIfNeeded();
    }

    private static synchronized Map<ResourceLocation, Block> buildIfNeeded() {
        Map<ResourceLocation, Block> local = CACHE;
        if (local != null) return local;
        Map<ResourceLocation, Block> built = build();
        CACHE = built;
        return built;
    }

    private static Map<ResourceLocation, Block> build() {
        Map<ResourceLocation, Block> map = new LinkedHashMap<>();
        for (Holder.Reference<VillagerProfession> profHolder
                : (Iterable<Holder.Reference<VillagerProfession>>) BuiltInRegistries.VILLAGER_PROFESSION.holders()::iterator) {
            VillagerProfession profession = profHolder.value();
            ResourceLocation profId = profHolder.key().location();
            Block block = resolveWorkstation(profession);
            if (block != null) {
                map.put(profId, block);
            }
        }
        return Map.copyOf(map);
    }

    private static Block resolveWorkstation(VillagerProfession profession) {
        for (Holder.Reference<PoiType> poiHolder
                : (Iterable<Holder.Reference<PoiType>>) BuiltInRegistries.POINT_OF_INTEREST_TYPE.holders()::iterator) {
            if (!profession.heldJobSite().test(poiHolder)) continue;
            PoiType poi = poiHolder.value();
            if (poi.matchingStates().isEmpty()) continue;
            BlockState state = poi.matchingStates().iterator().next();
            return state.getBlock();
        }
        return null;
    }

    // @VisibleForTesting
    public static void invalidateForTesting() {
        CACHE = null;
    }
}
