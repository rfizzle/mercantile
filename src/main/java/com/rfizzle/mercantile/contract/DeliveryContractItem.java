package com.rfizzle.mercantile.contract;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * The written delivery contract (issue #86). The terms live in a versioned CUSTOM_DATA blob
 * written by {@link ContractService#createContractItem}; the lore carries a static summary.
 * Right-clicking in the air reads the full terms — with live time remaining — into chat, and the
 * stack quietly vanishes once its deadline lapses (no penalty), checked on a slow inventory tick.
 */
public class DeliveryContractItem extends Item {

    /** Deadline poll cadence while sitting in an inventory (~5 s; expiry needs no precision). */
    private static final int EXPIRY_CHECK_INTERVAL_TICKS = 100;

    public DeliveryContractItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        CompoundTag nbt = ContractService.readTag(stack);
        if (nbt == null || !nbt.hasUUID(ContractService.TAG_CONTRACT_ID)) {
            return InteractionResultHolder.pass(stack);
        }
        printDetails(player, nbt, level.getGameTime());
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide) return;
        // Stagger by slot so many contracts don't all poll their deadline on the same tick.
        if ((level.getGameTime() + slot) % EXPIRY_CHECK_INTERVAL_TICKS != 0) return;
        CompoundTag nbt = ContractService.readTag(stack);
        if (nbt == null || !nbt.contains(ContractService.TAG_DEADLINE)) return;
        long deadline = nbt.getLong(ContractService.TAG_DEADLINE);
        if (deadline < 0 || level.getGameTime() < deadline) return;

        // Expired contracts simply vanish (issue #86 — no penalty at this iteration).
        stack.setCount(0);
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("notification.mercantile.contract.expired_vanish",
                            nbt.getString(ContractService.TAG_VILLAGER_NAME))
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    private static void printDetails(Player player, CompoundTag nbt, long now) {
        String villagerName = nbt.getString(ContractService.TAG_VILLAGER_NAME);
        BlockPos pos = BlockPos.of(nbt.getLong(ContractService.TAG_POS));
        long deadline = nbt.getLong(ContractService.TAG_DEADLINE);

        player.displayClientMessage(Component.translatable("message.mercantile.contract.details.header")
                .withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.translatable("message.mercantile.contract.details.request",
                        nbt.getInt(ContractService.TAG_COUNT),
                        itemName(nbt.getString(ContractService.TAG_ITEM)),
                        villagerName)
                .withStyle(ChatFormatting.WHITE), false);
        player.displayClientMessage(Component.translatable("message.mercantile.contract.details.payment",
                        nbt.getInt(ContractService.TAG_PAYMENT))
                .withStyle(ChatFormatting.GREEN), false);
        player.displayClientMessage(Component.translatable("message.mercantile.contract.details.location",
                        villagerName, pos.getX(), pos.getY(), pos.getZ())
                .withStyle(ChatFormatting.GRAY), false);

        if (now >= deadline) {
            player.displayClientMessage(Component.translatable("message.mercantile.contract.details.expired")
                    .withStyle(ChatFormatting.RED), false);
        } else {
            long remaining = deadline - now;
            player.displayClientMessage(Component.translatable("message.mercantile.contract.details.remaining",
                            remaining / 24_000L, (remaining % 24_000L) / 1_000L)
                    .withStyle(ChatFormatting.YELLOW), false);
        }
    }

    private static Component itemName(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return Component.literal(itemId);
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? Component.literal(itemId) : item.getDescription();
    }
}
