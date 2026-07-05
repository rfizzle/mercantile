package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.contract.ContractService;
import com.rfizzle.mercantile.contract.DeliveryContract;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.MercantileVillagerData;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts {@code Villager#mobInteract} (HEAD, cancellable) for delivery contracts (issue #86):
 * sneak + right-clicking a villager that has a pending offer with <b>paper</b> writes the contract
 * (one paper consumed, waived in creative; the sneak keeps plain paper-in-hand trading with
 * librarians and cartographers untouched), and right-clicking the contract's villager with the
 * <b>written contract item</b> settles the delivery. A held contract always intercepts — the
 * trade screen never opens over it, and the wrong villager politely refuses. The item-hand guards
 * across the mobInteract mixins are mutually exclusive: {@link VillagerPickupMixin} requires an
 * empty hand, {@link VillagerFollowMixin} requires an emerald, {@link VillagerBabyFeedMixin}
 * requires a villager breeding food, {@link VillagerNitwitRehabMixin} requires a golden apple,
 * {@link VillagerWorkOrderMixin} requires a workstation block item, and this mixin requires paper
 * or the delivery-contract item (neither of which is any of the former). Future authors must
 * preserve this invariant so no two mobInteract injections can fire for the same interaction.
 * The paper branch additionally only cancels when the villager actually holds a live offer —
 * server-only knowledge — so a paper-holding client falls through and lets the server decide.
 */
@Mixin(Villager.class)
public abstract class VillagerContractMixin extends AbstractVillager {

    protected VillagerContractMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void mercantile$handleContract(Player player, InteractionHand hand,
                                           CallbackInfoReturnable<InteractionResult> cir) {
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableContracts || !config.enableReputation) return;
        if (hand != InteractionHand.MAIN_HAND) return;

        ItemStack held = player.getMainHandItem();
        boolean holdingContract = held.is(MercantileRegistry.DELIVERY_CONTRACT);
        if (!holdingContract && !held.is(Items.PAPER)) return;

        Villager self = (Villager) (Object) this;
        // This HEAD injection runs before vanilla's own isAlive/isSleeping gate — replicate it.
        if (!self.isAlive() || self.isSleeping()) return;
        // Babies never carry contracts; let vanilla (and the baby-feed mixin) handle them.
        if (self.isBaby()) return;

        if (holdingContract) {
            mercantile$deliver(self, player, held, cir);
        } else {
            mercantile$acceptOffer(self, player, held, config, cir);
        }
    }

    private void mercantile$deliver(Villager self, Player player, ItemStack held,
                                    CallbackInfoReturnable<InteractionResult> cir) {
        if (self.level().isClientSide) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        ServerPlayer serverPlayer = (ServerPlayer) player;
        CompoundTag nbt = ContractService.readTag(held);
        ContractService.Delivery delivery = ContractService.deliver(serverPlayer, self, held);

        switch (delivery.result()) {
            case COMPLETED -> {
                self.playSound(SoundEvents.VILLAGER_YES, 1.0f, self.getVoicePitch());
                // Entity event 14 = vanilla green "happy villager" particles.
                ((ServerLevel) self.level()).broadcastEntityEvent(self, (byte) 14);
                serverPlayer.displayClientMessage(
                        Component.translatable("mercantile.contract.completed",
                                        self.getDisplayName(), delivery.paid())
                                .withStyle(ChatFormatting.GREEN), true);
                cir.setReturnValue(InteractionResult.SUCCESS);
            }
            case MISSING_ITEMS -> {
                self.playSound(SoundEvents.VILLAGER_NO, 1.0f, self.getVoicePitch());
                serverPlayer.displayClientMessage(
                        Component.translatable("mercantile.contract.denied.missing_items",
                                        delivery.stillMissing(), mercantile$requestedName(nbt))
                                .withStyle(ChatFormatting.RED), true);
                cir.setReturnValue(InteractionResult.FAIL);
            }
            case WRONG_VILLAGER -> {
                self.playSound(SoundEvents.VILLAGER_NO, 1.0f, self.getVoicePitch());
                serverPlayer.displayClientMessage(
                        Component.translatable("mercantile.contract.denied.wrong_villager",
                                        mercantile$payeeName(nbt))
                                .withStyle(ChatFormatting.RED), true);
                cir.setReturnValue(InteractionResult.FAIL);
            }
            case EXPIRED -> {
                self.playSound(SoundEvents.VILLAGER_NO, 1.0f, self.getVoicePitch());
                serverPlayer.displayClientMessage(
                        Component.translatable("mercantile.contract.denied.expired")
                                .withStyle(ChatFormatting.RED), true);
                cir.setReturnValue(InteractionResult.FAIL);
            }
            case INVALID -> {
                self.playSound(SoundEvents.VILLAGER_NO, 1.0f, self.getVoicePitch());
                serverPlayer.displayClientMessage(
                        Component.translatable("mercantile.contract.denied.invalid")
                                .withStyle(ChatFormatting.RED), true);
                cir.setReturnValue(InteractionResult.FAIL);
            }
        }
    }

    private void mercantile$acceptOffer(Villager self, Player player, ItemStack held,
                                        MercantileConfig config,
                                        CallbackInfoReturnable<InteractionResult> cir) {
        // Sneak distinguishes "sign the contract" from trading a paper stack with a librarian
        // or cartographer — an un-modified right-click with paper always trades as vanilla.
        if (!player.isShiftKeyDown()) return;
        // The offer lives server-side only; the client cannot tell paper-for-contract from
        // paper-in-hand trading, so it falls through and mirrors whatever the server decides.
        if (self.level().isClientSide) return;

        // getAttached, not getAttachedOrCreate: no-offer interactions are reads and must not
        // persist empty attachment data. ContractService.accept attaches on write.
        MercantileVillagerData data = self.getAttached(MercantileAttachments.VILLAGER_DATA);
        if (data == null) return;
        DeliveryContract contract = data.getContract();
        long now = self.level().getGameTime();
        if (contract != null && contract.isExpired(now)) {
            data.setContract(null);
            contract = null;
        }
        if (contract == null || contract.accepted()) return; // no live offer — vanilla trade

        ServerPlayer serverPlayer = (ServerPlayer) player;
        ServerLevel serverLevel = (ServerLevel) self.level();
        ItemStack contractItem = ContractService.accept(serverLevel, self, contract, config);

        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        serverPlayer.getInventory().placeItemBackInInventory(contractItem);

        self.playSound(SoundEvents.VILLAGER_YES, 1.0f, self.getVoicePitch());
        serverLevel.broadcastEntityEvent(self, (byte) 14);
        serverPlayer.displayClientMessage(
                Component.translatable("mercantile.contract.accepted",
                                contract.count(), ContractService.requestedItemName(contract),
                                self.getDisplayName())
                        .withStyle(ChatFormatting.GREEN), true);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    private static Component mercantile$payeeName(CompoundTag nbt) {
        String name = nbt == null ? "" : nbt.getString(ContractService.TAG_VILLAGER_NAME);
        return Component.literal(name.isEmpty() ? "?" : name);
    }

    private static Component mercantile$requestedName(CompoundTag nbt) {
        if (nbt == null) return Component.literal("?");
        ResourceLocation id = ResourceLocation.tryParse(nbt.getString(ContractService.TAG_ITEM));
        if (id == null) return Component.literal("?");
        var item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? Component.literal("?") : item.getDescription();
    }
}
