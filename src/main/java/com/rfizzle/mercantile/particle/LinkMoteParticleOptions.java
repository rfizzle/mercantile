package com.rfizzle.mercantile.particle;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import org.joml.Vector3f;

/**
 * A workstation "link mote" carrying a per-link tint. Mirrors vanilla
 * {@link net.minecraft.core.particles.DustParticleOptions} — the workstation
 * visualization colours each villager→workstation segment by profession, so
 * the particle type has to carry that colour the same way dust does. The mote
 * texture is white with soft alpha falloff; the colour multiplies through at
 * render time (see {@code LinkMoteParticle}).
 */
public record LinkMoteParticleOptions(Vector3f color) implements ParticleOptions {

    public static final MapCodec<LinkMoteParticleOptions> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    ExtraCodecs.VECTOR3F.fieldOf("color").forGetter(LinkMoteParticleOptions::color)
            ).apply(instance, LinkMoteParticleOptions::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LinkMoteParticleOptions> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VECTOR3F, LinkMoteParticleOptions::color,
                    LinkMoteParticleOptions::new);

    @Override
    public ParticleType<?> getType() {
        return MercantileParticles.LINK_MOTE;
    }
}
