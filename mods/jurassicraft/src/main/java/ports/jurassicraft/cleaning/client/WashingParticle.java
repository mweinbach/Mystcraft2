package ports.jurassicraft.cleaning.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** The 1.12.2 washing droplet: a short-lived splash sprite with no collision. */
final class WashingParticle extends TextureSheetParticle {
    WashingParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, int rotation) {
        super(level, x, y, z, xd, yd, zd);
        int xSprinkle = (zd != 0 ? 1 : 0) * (rotation == 0 || rotation == 3 ? 1 : -1);
        int zSprinkle = (xd != 0 ? 1 : 0) * (rotation == 0 || rotation == 3 ? 1 : -1);
        this.x += (Math.random() * 0.2) * xSprinkle;
        this.z += (Math.random() * 0.2) * zSprinkle;
        this.xo = this.x;
        this.zo = this.z;
        this.xd = xd + (Math.random() * 0.06) * xSprinkle;
        this.yd = yd;
        this.zd = zd + (Math.random() * 0.06) * zSprinkle;
        this.rCol = 1;
        this.gCol = 1;
        this.bCol = 1;
        this.quadSize = 0.1F;
        this.gravity = 0.04F;
        this.lifetime = (int) (1.0D / (Math.random() * 0.8D + 0.2D));
        this.hasPhysics = false;
        setSize(0.01F, 0.01F);
        int sprite = random.nextInt(4);
        // 1.19.2 keeps splash frames on the particle engine's atlas, not ModelManager.
        var texture = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        if (texture instanceof TextureAtlas atlas) {
            setSprite(atlas.getSprite(new ResourceLocation("particle/splash_" + sprite)));
        }
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        yd -= 0.04D * gravity;
        move(xd, yd, zd);
        double drag = 0.99F;
        xd *= drag;
        yd *= drag;
        zd *= drag;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
