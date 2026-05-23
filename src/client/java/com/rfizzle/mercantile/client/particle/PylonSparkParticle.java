package com.rfizzle.mercantile.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class PylonSparkParticle extends TextureSheetParticle {

    PylonSparkParticle(ClientLevel level, double x, double y, double z,
                      double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.lifetime = 10 + this.random.nextInt(6);
        this.gravity = 0.0f;
        this.hasPhysics = false;
        this.quadSize = 0.12f + this.random.nextFloat() * 0.06f;
        this.xd = (this.random.nextFloat() - 0.5f) * 0.06f;
        this.yd = 0.02f + this.random.nextFloat() * 0.04f;
        this.zd = (this.random.nextFloat() - 0.5f) * 0.06f;
        this.rCol = 1.0f;
        this.gCol = 0.7f;
        this.bCol = 0.1f;
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

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            PylonSparkParticle particle = new PylonSparkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
