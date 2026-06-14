package com.rfizzle.mercantile.client.particle;

import com.rfizzle.mercantile.particle.LinkMoteParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import org.joml.Vector3f;

/**
 * Soft glowing dot for the workstation link visualization, replacing the vanilla
 * dust segment. The texture is white; the per-link profession colour carried by
 * {@link LinkMoteParticleOptions} is applied via {@link #setColor} and multiplies
 * through, so each villager→workstation line keeps its colour coding.
 */
public class LinkMoteParticle extends TextureSheetParticle {

    LinkMoteParticle(ClientLevel level, double x, double y, double z, Vector3f color) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.lifetime = 12 + this.random.nextInt(8);
        this.gravity = 0.0f;
        this.hasPhysics = false;
        this.quadSize = 0.07f + this.random.nextFloat() * 0.02f;
        this.setColor(color.x(), color.y(), color.z());
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        this.alpha = 1.0f - (float) this.age / this.lifetime;
    }

    public static class Provider implements ParticleProvider<LinkMoteParticleOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(LinkMoteParticleOptions options, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            LinkMoteParticle particle = new LinkMoteParticle(level, x, y, z, options.color());
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
