package com.rfizzle.mercantile.memorial;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.VillagerHeadTextures;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameRules;

import java.util.ArrayList;
import java.util.List;

/**
 * A named villager's death leaves a keepsake: a memorial item whose tooltip records the
 * villager's name, profession, level, and cause of death. The same death event also
 * triggers the cosmetic mourning reaction and feeds the fear markup — each independently
 * config-gated, so this listener is the single dispatch point for villager deaths.
 */
public final class MemorialManager {

    /** Schema version of the memorial's CUSTOM_DATA blob, mirroring the pickup-head pattern. */
    public static final int CURRENT_DATA_VERSION = 1;

    private MemorialManager() {
    }

    public static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register(MemorialManager::onVillagerDeath);
    }

    private static void onVillagerDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof Villager villager)) return;
        if (!(villager.level() instanceof ServerLevel level)) return;

        MercantileConfig config = MercantileConfig.get();
        if (config.enableMemorials && villager.hasCustomName()
                && level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            dropMemorial(level, villager, source);
        }
        if (config.enableMourning) {
            MourningManager.startMourning(level, villager);
        }
        if (config.enableFearMarkup && source.getEntity() instanceof ServerPlayer killer) {
            FearManager.recordKill(level, killer, villager.blockPosition());
        }
    }

    private static void dropMemorial(ServerLevel level, Villager villager, DamageSource source) {
        ItemEntity drop = new ItemEntity(level,
                villager.getX(), villager.getY() + 0.5, villager.getZ(),
                createMemorialItem(villager, source));
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
    }

    /** Builds the memorial stack for a named villager. Caller guarantees a custom name. */
    public static ItemStack createMemorialItem(Villager villager, DamageSource source) {
        Component name = villager.getCustomName();
        VillagerProfession profession = villager.getVillagerData().getProfession();
        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        int level = villager.getVillagerData().getLevel();

        CompoundTag nbt = new CompoundTag();
        nbt.putInt("MercantileDataVersion", CURRENT_DATA_VERSION);
        nbt.putString("VillagerName", name.getString());
        nbt.putString("Profession", professionId.toString());
        nbt.putInt("Level", level);
        nbt.putString("CauseOfDeath", source.getMsgId());

        ItemStack stack = new ItemStack(MercantileRegistry.MEMORIAL);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("tooltip.mercantile.memorial.name", name)
                .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withItalic(false)));
        stack.set(DataComponents.LORE, buildLore(villager, source, professionId, level));
        return stack;
    }

    private static ItemLore buildLore(Villager villager, DamageSource source,
                                      ResourceLocation professionId, int level) {
        List<Component> lines = new ArrayList<>();

        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (!villager.isBaby()
                && profession != VillagerProfession.NONE
                && profession != VillagerProfession.NITWIT) {
            lines.add(Component.translatable("tooltip.mercantile.memorial.lore.profession_level",
                            VillagerHeadTextures.getDisplayName(professionId),
                            Component.translatable("merchant.level." + level))
                    .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false)));
        }

        lines.add(source.getLocalizedDeathMessage(villager).copy()
                .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false)));
        lines.add(Component.translatable("tooltip.mercantile.memorial.lore.keepsake")
                .withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY).withItalic(false)));

        return new ItemLore(lines, lines);
    }
}
