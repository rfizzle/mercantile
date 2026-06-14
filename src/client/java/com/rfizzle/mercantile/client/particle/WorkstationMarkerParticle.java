package com.rfizzle.mercantile.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A floating status icon above a workstation (green check = claimed, white
 * question mark = unclaimed). Re-emitted one-per-workstation by
 * {@code WorkstationLinkRenderer}, which keeps a TTL so exactly one is alive at
 * a time. Renders camera-facing (a {@link TextureSheetParticle} billboard) and
 * full-bright so it stays legible in shade, with a gentle vertical bob and
 * alpha pulse. The motion runs one full cycle over the lifetime and starts/ends
 * at the same phase, so successive re-emissions are seamless.
 */
public class WorkstationMarkerParticle extends TextureSheetParticle {

    private static final int FULLBRIGHT = 0xF000F0;
    private static final double BOB_AMPLITUDE = 0.09; // blocks
    private final double baseY;

    WorkstationMarkerParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.baseY = y;
        this.lifetime = 30;
        this.gravity = 0.0f;
        this.hasPhysics = false;
        this.friction = 1.0f;
        this.quadSize = 0.30f;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return FULLBRIGHT;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        double t = (this.age / (double) this.lifetime) * Math.PI * 2.0;
        this.y = this.baseY + BOB_AMPLITUDE * Math.sin(t);
        this.alpha = 0.75f + 0.25f * (float) Math.sin(t);
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
            WorkstationMarkerParticle particle = new WorkstationMarkerParticle(level, x, y, z);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
