package com.rfizzle.mercantile.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.rfizzle.mercantile.sound.VillagerSoundFilter;
import com.rfizzle.mercantile.config.MercantileConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ClientLevel.class)
public abstract class ClientLevelVillagerSoundMixin {

    @WrapMethod(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V")
    private void mercantile$scaleVillagerSoundPositional(
            @Nullable Player player, double x, double y, double z, Holder<SoundEvent> holder,
            SoundSource source, float volume, float pitch, long seed, Operation<Void> original) {
        float scaled = mercantile$scaleVillagerVolume(holder, volume);
        original.call(player, x, y, z, holder, source, scaled, pitch, seed);
    }

    @WrapMethod(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V")
    private void mercantile$scaleVillagerSoundEntity(
            @Nullable Player player, Entity entity, Holder<SoundEvent> holder,
            SoundSource source, float volume, float pitch, long seed, Operation<Void> original) {
        float scaled = mercantile$scaleVillagerVolume(holder, volume);
        original.call(player, entity, holder, source, scaled, pitch, seed);
    }

    @Unique
    private static float mercantile$scaleVillagerVolume(Holder<SoundEvent> holder, float volume) {
        ResourceLocation loc = holder == null
                ? null
                : holder.unwrapKey().map(k -> k.location()).orElse(null);
        return VillagerSoundFilter.scaleVolume(volume, loc, MercantileConfig.get().villagerSoundVolume);
    }
}
