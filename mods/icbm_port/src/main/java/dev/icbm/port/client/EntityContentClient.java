package dev.icbm.port.client;

import dev.icbm.port.entity.EntityContent;
import dev.icbm.port.entity.LightBeamEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Client renderers for the seat (invisible, as upstream RenderSeat), light beam and actionable missile. */
@Environment(EnvType.CLIENT)
public final class EntityContentClient {
  private EntityContentClient() {}

  public static void register() {
    EntityRendererRegistry.register(EntityContent.PLAYER_SEAT, EmptyEntityRenderer::new);
    EntityRendererRegistry.register(EntityContent.LIGHT_BEAM, LightBeamRenderer::new);
    EntityRendererRegistry.register(EntityContent.ACTIONABLE_MISSILE, ProjectileItemRenderer::new);
  }

  /**
   * Original light-beam renderer built on the vanilla beacon beam: a coloured inner column and a
   * translucent glow whose radii scale with the synchronized beam progress, rising from five
   * blocks below the entity to the build limit (upstream {@code RenderLightBeam} geometry, vanilla
   * texture).
   */
  static final class LightBeamRenderer extends EntityRenderer<LightBeamEntity> {
    LightBeamRenderer(EntityRendererFactory.Context context) {
      super(context);
    }

    @Override
    public boolean shouldRender(LightBeamEntity entity, Frustum frustum, double x, double y, double z) {
      return entity.getProgress() > 0 || entity.clientProgress > 0;
    }

    @Override
    public void render(
        LightBeamEntity beam,
        float yaw,
        float tickDelta,
        MatrixStack matrices,
        VertexConsumerProvider vertices,
        int light) {
      float progress = beam.clientProgress;
      if (progress <= 0) return;
      int bottom = (int) Math.floor(beam.getY()) - 5;
      int height = Math.max(1, beam.world.getTopY() - bottom);
      matrices.push();
      matrices.translate(-0.5, -5 - (beam.getY() - Math.floor(beam.getY())), -0.5);
      BeaconBlockEntityRenderer.renderBeam(
          matrices,
          vertices,
          BeaconBlockEntityRenderer.BEAM_TEXTURE,
          tickDelta,
          1,
          beam.world.getTime(),
          0,
          height,
          beam.colorComponents(),
          Math.max(0.01F, progress * beam.getBeamSize() * 0.5F),
          Math.max(0.02F, progress * beam.getGlowSize() * 0.5F));
      matrices.pop();
      super.render(beam, yaw, tickDelta, matrices, vertices, light);
    }

    @Override
    public Identifier getTexture(LightBeamEntity entity) {
      return BeaconBlockEntityRenderer.BEAM_TEXTURE;
    }
  }
}
