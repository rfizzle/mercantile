package com.rfizzle.mercantile.registry;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.block.SentryPylonBlock;
import com.rfizzle.mercantile.block.SentryPylonBlockEntity;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MercantileRegistry {
    public static final Map<ResourceLocation, Block> BLOCKS = new LinkedHashMap<>();
    public static final List<Item> STANDALONE_ITEMS = new ArrayList<>();

    public static final Block SENTRY_PYLON = new SentryPylonBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .sound(SoundType.STONE)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops());

    public static BlockEntityType<SentryPylonBlockEntity> SENTRY_PYLON_BE;

    private static boolean registered = false;

    private MercantileRegistry() {
    }

    public static void register() {
        if (registered) return;
        registered = true;

        registerBlock("sentry_pylon", SENTRY_PYLON, new Item.Properties());

        SENTRY_PYLON_BE = BlockEntityType.Builder
                .of(SentryPylonBlockEntity::new, SENTRY_PYLON)
                .build(null);
        registerBlockEntityType("sentry_pylon", SENTRY_PYLON_BE);

        registerCreativeTab();
    }

    private static <T extends Block> T registerBlock(String name, T block, Item.Properties itemProps) {
        ResourceLocation id = Mercantile.id(name);
        Registry.register(BuiltInRegistries.BLOCK, id, block);
        BLOCKS.put(id, block);
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, itemProps));
        return block;
    }

    private static <T extends BlockEntity> BlockEntityType<T> registerBlockEntityType(String name,
                                                                                      BlockEntityType<T> type) {
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Mercantile.id(name), type);
        return type;
    }

    private static void registerCreativeTab() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Mercantile.id("mercantile"),
                FabricItemGroup.builder()
                        .title(Component.translatable("itemGroup.mercantile"))
                        .icon(() -> new ItemStack(SENTRY_PYLON))
                        .displayItems((params, output) -> {
                            BLOCKS.values().forEach(output::accept);
                            STANDALONE_ITEMS.forEach(output::accept);
                        })
                        .build());
    }
}
