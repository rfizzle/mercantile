package com.rfizzle.mercantile.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class PylonMoteParticle extends TextureSheetParticle {

    PylonMoteParticle(ClientLevel level, double x, double y, double z,
                      double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.lifetime = 20 + this.random.nextInt(11);
        this.gravity = -0.01f;
        this.hasPhysics = false;
        this.quadSize = 0.06f + this.random.nextFloat() * 0.03f;
        this.xd = (this.random.nextFloat() - 0.5f) * 0.01f;
        this.yd = 0.02f + this.random.nextFloat() * 0.02f;
        this.zd = (this.random.nextFloat() - 0.5f) * 0.01f;
        this.rCol = 0.69f;
        this.gCol = 0.75f;
        this.bCol = 0.82f;
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
            PylonMoteParticle particle = new PylonMoteParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
