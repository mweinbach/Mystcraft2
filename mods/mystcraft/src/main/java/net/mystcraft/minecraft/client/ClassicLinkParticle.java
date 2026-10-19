package net.mystcraft.minecraft.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Mechanical adaptation of classic EntityFXLink (scale three). */
public final class ClassicLinkParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float initialScale;
    private ClassicLinkParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.sprites = sprites;
        xd *= .1; yd *= .1; zd *= .1;
        rCol = gCol = bCol = random.nextFloat() * .3F;
        quadSize *= .75F * 3;
        initialScale = quadSize;
        lifetime = (int)(8 / (random.nextFloat() * .8 + .2)) * 3;
        setSpriteFromAge(sprites);
    }
    @Override public float getQuadSize(float partial) {
        return initialScale * Mth.clamp((age + partial) / lifetime * 32, 0, 1);
    }
    @Override public void tick() {
        xo = x; yo = y; zo = z;
        if (age++ >= lifetime) { remove(); return; }
        setSpriteFromAge(sprites);
        xd += .005 * random.nextGaussian(); yd += .001; zd += .005 * random.nextGaussian();
        move(xd, yd, zd);
        if (y == yo) { xd *= 1.1; zd *= 1.1; }
        xd *= .9599999785; yd *= .9599999785; zd *= .9599999785;
        if (onGround) { xd *= .699999988; zd *= .699999988; }
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_OPAQUE; }
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new ClassicLinkParticle(level, x, y, z, sprites);
        }
    }
}
