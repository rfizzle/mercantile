package com.rfizzle.mercantile.data;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import com.rfizzle.mercantile.trade.OfferIdentityHash;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class VillagerPickupHelper {
    public static final int CURRENT_DATA_VERSION = 1;

    public static ItemStack createHeadItem(Villager villager) {
        CompoundTag nbt = new CompoundTag();
        villager.saveWithoutId(nbt);
        nbt.remove("UUID");
        nbt.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(villager.getType()).toString());
        nbt.putInt("MercantileDataVersion", CURRENT_DATA_VERSION);

        ResourceLocation professionId = villager.isBaby()
                ? VillagerHeadTextures.BABY
                : BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());

        ItemStack item = new ItemStack(Items.PLAYER_HEAD);
        item.set(DataComponents.PROFILE, VillagerHeadTextures.getProfile(professionId));
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        item.set(DataComponents.CUSTOM_NAME, buildDisplayName(villager, professionId));
        item.set(DataComponents.LORE, buildLore(villager));

        return item;
    }

    public static ItemStack createHeadItem(WanderingTrader trader) {
        CompoundTag nbt = new CompoundTag();
        trader.saveWithoutId(nbt);
        nbt.remove("UUID");
        nbt.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(trader.getType()).toString());
        nbt.putInt("MercantileDataVersion", CURRENT_DATA_VERSION);

        ItemStack item = new ItemStack(Items.PLAYER_HEAD);
        item.set(DataComponents.PROFILE, VillagerHeadTextures.getProfile(VillagerHeadTextures.WANDERING_TRADER));
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        item.set(DataComponents.CUSTOM_NAME, buildTraderDisplayName(trader));
        item.set(DataComponents.LORE, buildTraderLore(trader));

        return item;
    }

    private static Component buildDisplayName(Villager villager, ResourceLocation professionId) {
        Component suffix = professionSuffix(villager, professionId);
        MutableComponent name = villager.hasCustomName()
                ? Component.translatable("mercantile.pickup.named_profession_villager",
                        villager.getCustomName(), suffix)
                : suffix.copy();
        return name.withStyle(style -> style.withColor(ChatFormatting.YELLOW).withItalic(false));
    }

    private static Component professionSuffix(Villager villager, ResourceLocation professionId) {
        if (villager.isBaby()) {
            return Component.translatable("mercantile.pickup.baby_villager");
        }
        if (villager.getVillagerData().getProfession() == VillagerProfession.NONE) {
            return Component.translatable("mercantile.pickup.villager");
        }
        return Component.translatable("mercantile.pickup.profession_villager",
                VillagerHeadTextures.getDisplayName(professionId));
    }

    private static ItemLore buildLore(Villager villager) {
        List<Component> lines = new ArrayList<>();

        lines.add(line("mercantile.pickup.lore.instruction", ChatFormatting.DARK_GRAY));

        VillagerProfession profession = villager.getVillagerData().getProfession();
        int level = villager.getVillagerData().getLevel();
        if (profession != VillagerProfession.NONE
                && profession != VillagerProfession.NITWIT
                && !villager.isBaby()) {
            ResourceLocation profId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            lines.add(Component.translatable("mercantile.pickup.lore.profession_level",
                            VillagerHeadTextures.getDisplayName(profId),
                            Component.translatable("merchant.level." + level))
                    .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false)));
        }

        MercantileVillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        appendTradesSection(lines, villager.getOffers(), data.getLockedTrades());

        return new ItemLore(lines, lines);
    }

    private static Component buildTraderDisplayName(WanderingTrader trader) {
        Component label = Component.translatable("mercantile.pickup.wandering_trader");
        MutableComponent name = trader.hasCustomName()
                ? Component.translatable("mercantile.pickup.named_trader",
                        trader.getCustomName(), label)
                : label.copy();
        return name.withStyle(style -> style.withColor(ChatFormatting.YELLOW).withItalic(false));
    }

    private static ItemLore buildTraderLore(WanderingTrader trader) {
        List<Component> lines = new ArrayList<>();

        lines.add(line("mercantile.pickup.lore.instruction", ChatFormatting.DARK_GRAY));

        int despawnTicks = trader.getDespawnDelay();
        if (despawnTicks > 0) {
            lines.add(Component.translatable("mercantile.pickup.lore.despawn_remaining",
                            despawnTicks / 20)
                    .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false)));
        }

        appendTradesSection(lines, trader.getOffers(), Set.of());

        return new ItemLore(lines, lines);
    }

    private static void appendTradesSection(List<Component> lines, MerchantOffers offers, Set<String> lockedHashes) {
        if (offers.isEmpty()) return;

        lines.add(Component.empty());
        lines.add(line("mercantile.pickup.lore.trades", ChatFormatting.GRAY));

        int lockedCount = 0;
        for (MerchantOffer offer : offers) {
            boolean locked = lockedHashes.contains(OfferIdentityHash.compute(offer));
            if (locked) lockedCount++;
            lines.add(formatTradeLine(offer, locked));
        }

        if (lockedCount > 0) {
            int unlocked = offers.size() - lockedCount;
            lines.add(Component.translatable("mercantile.pickup.lore.trade_count",
                            unlocked, offers.size())
                    .withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY).withItalic(false)));
        }
    }

    private static Component formatTradeLine(MerchantOffer offer, boolean locked) {
        ItemStack costA = offer.getBaseCostA();
        ItemStack costB = offer.getCostB();
        ItemStack result = offer.getResult();

        MutableComponent trade = Component.literal("  ")
                .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false));

        trade.append(itemName(costA));
        if (costA.getCount() > 1) trade.append(" x" + costA.getCount());

        if (!costB.isEmpty()) {
            trade.append(Component.literal(" + ").withStyle(ChatFormatting.DARK_GRAY));
            trade.append(itemName(costB));
            if (costB.getCount() > 1) trade.append(" x" + costB.getCount());
        }

        trade.append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY));

        if (result.is(Items.ENCHANTED_BOOK)) {
            ItemEnchantments stored = result.get(DataComponents.STORED_ENCHANTMENTS);
            if (stored != null && !stored.isEmpty()) {
                var entry = stored.entrySet().iterator().next();
                trade.append(Enchantment.getFullname(entry.getKey(), entry.getIntValue())
                        .copy().withStyle(style -> style.withColor(ChatFormatting.GRAY)));
            } else {
                trade.append(itemName(result));
            }
        } else {
            trade.append(itemName(result));
            if (result.getCount() > 1) trade.append(" x" + result.getCount());
        }

        if (offer.isOutOfStock()) {
            trade.withStyle(style -> style.withStrikethrough(true));
        }

        if (locked) {
            MutableComponent wrapper = Component.empty();
            wrapper.append(trade);
            wrapper.append(Component.literal(" •")
                    .withStyle(style -> style.withColor(ChatFormatting.GOLD)
                            .withStrikethrough(false).withItalic(false)));
            return wrapper;
        }

        return trade;
    }

    private static Component itemName(ItemStack stack) {
        return stack.getHoverName().copy()
                .withStyle(style -> style.withColor(ChatFormatting.GRAY));
    }

    private static Component line(String key, ChatFormatting color) {
        return Component.translatable(key)
                .withStyle(style -> style.withColor(color).withItalic(false));
    }

}
