package com.rfizzle.mercantile.contract;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.MercantileVillagerData;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import com.rfizzle.mercantile.reputation.ReputationManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side delivery-contract orchestration (issue #86): rolling offers onto villagers, writing
 * the contract item when a player accepts with paper, and settling a delivery. State lives in
 * {@link MercantileVillagerData#getContract()}; the periodic sweep and cue particles live in
 * {@link ContractManager}.
 */
public final class ContractService {

    /** Schema version of the contract item's CUSTOM_DATA blob, mirroring the memorial pattern. */
    public static final int CURRENT_DATA_VERSION = 1;

    /** An un-accepted offer retracts after half an in-game day. */
    public static final long OFFER_WINDOW_TICKS = 12_000L;

    // CUSTOM_DATA keys on the contract item.
    public static final String TAG_VERSION = "MercantileDataVersion";
    public static final String TAG_CONTRACT_ID = "ContractId";
    public static final String TAG_ITEM = "Item";
    public static final String TAG_COUNT = "Count";
    public static final String TAG_PAYMENT = "Payment";
    public static final String TAG_DEADLINE = "DeadlineGameTime";
    public static final String TAG_VILLAGER_NAME = "VillagerName";
    public static final String TAG_POS = "VillagerPos";
    public static final String TAG_DIMENSION = "Dimension";

    public enum DeliveryResult {
        COMPLETED,
        WRONG_VILLAGER,
        EXPIRED,
        MISSING_ITEMS,
        INVALID
    }

    /** Outcome plus the numbers the action-bar messages need. */
    public record Delivery(DeliveryResult result, int paid, int stillMissing) {
        static Delivery of(DeliveryResult result) {
            return new Delivery(result, 0, 0);
        }
    }

    private ContractService() {
    }

    /** Employed adults can carry contracts; babies, jobless, and nitwits never roll offers. */
    public static boolean isEligible(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        return !villager.isBaby() && villager.isAlive()
                && profession != VillagerProfession.NONE
                && profession != VillagerProfession.NITWIT;
    }

    /** Registry path of the villager's profession — the contract-pool key. */
    public static String professionKey(Villager villager) {
        return BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession()).getPath();
    }

    /**
     * Rolls a fresh offer from the villager's profession pool onto its attachment.
     *
     * @return the offer, or {@code null} when the profession has no pool.
     */
    public static DeliveryContract rollOffer(ServerLevel level, Villager villager, MercantileConfig config) {
        ContractPools.ContractEntry entry = ContractPools.roll(professionKey(villager), level.random);
        if (entry == null) return null;
        int count = ContractPools.rollRange(entry.minCount(), entry.maxCount(), level.random);
        int payment = DeliveryContract.scalePayment(
                ContractPools.rollRange(entry.minPayment(), entry.maxPayment(), level.random),
                config.contractPaymentScale);
        DeliveryContract offer = new DeliveryContract(UUID.randomUUID(),
                BuiltInRegistries.ITEM.getKey(entry.item()), count, payment,
                false, level.getGameTime() + OFFER_WINDOW_TICKS);
        villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA).setContract(offer);
        return offer;
    }

    /**
     * Accepts the villager's pending offer: stamps the delivery deadline, and returns the written
     * contract item to hand to the player. Caller guarantees a live, un-expired offer.
     */
    public static ItemStack accept(ServerLevel level, Villager villager, DeliveryContract offer,
                                   MercantileConfig config) {
        long deadline = level.getGameTime() + (long) config.contractDeadlineDays * 24_000L;
        DeliveryContract accepted = offer.accept(deadline);
        villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA).setContract(accepted);
        return createContractItem(villager, accepted);
    }

    /** Builds the written-contract stack; terms live in a versioned CUSTOM_DATA blob plus lore. */
    public static ItemStack createContractItem(Villager villager, DeliveryContract contract) {
        BlockPos pos = villager.blockPosition();
        String villagerName = villager.getDisplayName().getString();

        CompoundTag nbt = new CompoundTag();
        nbt.putInt(TAG_VERSION, CURRENT_DATA_VERSION);
        nbt.putUUID(TAG_CONTRACT_ID, contract.id());
        nbt.putString(TAG_ITEM, contract.itemId().toString());
        nbt.putInt(TAG_COUNT, contract.count());
        nbt.putInt(TAG_PAYMENT, contract.payment());
        nbt.putLong(TAG_DEADLINE, contract.expiryGameTime());
        nbt.putString(TAG_VILLAGER_NAME, villagerName);
        nbt.putLong(TAG_POS, pos.asLong());
        nbt.putString(TAG_DIMENSION, villager.level().dimension().location().toString());

        ItemStack stack = new ItemStack(MercantileRegistry.DELIVERY_CONTRACT);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        stack.set(DataComponents.LORE, buildLore(contract, villagerName, pos));
        return stack;
    }

    private static ItemLore buildLore(DeliveryContract contract, String villagerName, BlockPos pos) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("mercantile.contract.lore.request",
                        contract.count(), requestedItemName(contract), villagerName)
                .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false)));
        lines.add(Component.translatable("mercantile.contract.lore.payment", contract.payment())
                .withStyle(style -> style.withColor(ChatFormatting.GREEN).withItalic(false)));
        lines.add(Component.translatable("mercantile.contract.lore.deadline",
                        DeliveryContract.deadlineDay(contract.expiryGameTime()))
                .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false)));
        lines.add(Component.translatable("mercantile.contract.lore.location",
                        pos.getX(), pos.getY(), pos.getZ())
                .withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY).withItalic(false)));
        lines.add(Component.translatable("mercantile.contract.lore.hint")
                .withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY).withItalic(false)));
        return new ItemLore(lines, lines);
    }

    public static Component requestedItemName(DeliveryContract contract) {
        Item item = BuiltInRegistries.ITEM.get(contract.itemId());
        return item == Items.AIR
                ? Component.literal(contract.itemId().toString())
                : item.getDescription();
    }

    /** The contract id stored on a written-contract stack, if the blob is present and readable. */
    public static Optional<UUID> readContractId(ItemStack stack) {
        CompoundTag nbt = readTag(stack);
        if (nbt == null || !nbt.hasUUID(TAG_CONTRACT_ID)) return Optional.empty();
        if (nbt.getInt(TAG_VERSION) > CURRENT_DATA_VERSION) return Optional.empty();
        return Optional.of(nbt.getUUID(TAG_CONTRACT_ID));
    }

    /** Raw CUSTOM_DATA of a contract stack, or {@code null} when absent. */
    public static CompoundTag readTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    /**
     * Attempts to settle the held contract against this villager. On success the requested items
     * and the contract are consumed, the emerald payment is handed over, the cap-bypassing rep
     * bonus is granted, and the villager's contract slot is cleared.
     */
    public static Delivery deliver(ServerPlayer player, Villager villager, ItemStack contractStack) {
        Optional<UUID> heldId = readContractId(contractStack);
        if (heldId.isEmpty()) return Delivery.of(DeliveryResult.INVALID);

        // getAttached, not getAttachedOrCreate: a refused delivery is a read and must not
        // persist empty attachment data on the villager.
        MercantileVillagerData data = villager.getAttached(MercantileAttachments.VILLAGER_DATA);
        DeliveryContract contract = data == null ? null : data.getContract();
        if (contract == null || !contract.accepted() || !contract.id().equals(heldId.get())) {
            return Delivery.of(DeliveryResult.WRONG_VILLAGER);
        }

        long now = player.serverLevel().getGameTime();
        if (contract.isExpired(now)) {
            // The villager forgets the lapsed contract; the item vanishes on its own inventory tick.
            data.setContract(null);
            return Delivery.of(DeliveryResult.EXPIRED);
        }

        Item requested = BuiltInRegistries.ITEM.get(contract.itemId());
        if (requested == Items.AIR) {
            // The requested item no longer exists (datapack change); void the contract.
            data.setContract(null);
            return Delivery.of(DeliveryResult.INVALID);
        }

        int have = countItem(player, requested);
        if (have < contract.count()) {
            return new Delivery(DeliveryResult.MISSING_ITEMS, 0, contract.count() - have);
        }

        removeItem(player, requested, contract.count());
        contractStack.shrink(1);
        payEmeralds(player, contract.payment());
        ReputationManager.gainContractRep(player);
        data.setContract(null);
        return new Delivery(DeliveryResult.COMPLETED, contract.payment(), 0);
    }

    /** Counts an item across the player's main inventory and offhand (EmeraldPayment pattern). */
    public static int countItem(ServerPlayer player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) count += stack.getCount();
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    /** Deducts an item across the player's main inventory and offhand (EmeraldPayment pattern). */
    public static void removeItem(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) break;
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (remaining <= 0) break;
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    private static void payEmeralds(ServerPlayer player, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int size = Math.min(remaining, Items.EMERALD.getDefaultMaxStackSize());
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, size));
            remaining -= size;
        }
    }
}
