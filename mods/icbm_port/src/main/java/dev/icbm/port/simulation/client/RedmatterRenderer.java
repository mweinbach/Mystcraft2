package dev.icbm.port.simulation.client;

import dev.icbm.port.simulation.RedmatterEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3f;

/** Original animated geometry. Uses ordinary entity layers, including with Sodium and Iris. */
public final class RedmatterRenderer extends EntityRenderer<RedmatterEntity> {
  private static final Identifier BLACK =
      new Identifier("icbm_port", "textures/entity/redmatter_core.png");
  private static final Identifier GLOW =
      new Identifier("icbm_port", "textures/entity/redmatter_disc.png");
  private static final int FULL_BRIGHT = 0xF000F0;

  public RedmatterRenderer(EntityRendererFactory.Context context) {
    super(context);
    shadowRadius = 0;
  }

  @Override
  public boolean shouldRender(
      RedmatterEntity entity, Frustum frustum, double x, double y, double z) {
    return entity.shouldRender(x, y, z)
        && frustum.isVisible(entity.getBoundingBox().expand(Math.max(3, entity.size() * 1.6)));
  }

  @Override
  public void render(
      RedmatterEntity entity,
      float yaw,
      float tickDelta,
      MatrixStack matrices,
      VertexConsumerProvider consumers,
      int light) {
    float time = entity.age + tickDelta;
    float radius = Math.max(.65f, entity.size() * .45f);
    matrices.push();
    matrices.push();
    matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(time * .35f));
    sphere(matrices, consumers.getBuffer(RenderLayer.getEntitySolid(BLACK)), radius);
    matrices.pop();
    for (int ring = 0; ring < 4; ring++) {
      matrices.push();
      matrices.multiply(
          Vec3f.POSITIVE_Y.getDegreesQuaternion(time * (ring % 2 == 0 ? 1.1f : -.8f)));
      matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(22 + ring * 41));
      matrices.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(time * .18f + ring * 35));
      float inner = radius * (1.18f + ring * .18f);
      float outer = inner + radius * (.17f + .04f * (float) Math.sin(time * .08f + ring));
      disc(
          matrices,
          consumers.getBuffer(RenderLayer.getEntityTranslucent(GLOW)),
          inner,
          outer,
          time * .07f + ring,
          ring);
      matrices.pop();
    }
    matrices.pop();
    super.render(entity, yaw, tickDelta, matrices, consumers, light);
  }

  private static void sphere(MatrixStack matrices, VertexConsumer buffer, float radius) {
    int slices = 24, rows = 12;
    for (int row = 0; row < rows; row++) {
      double lo = -Math.PI / 2 + Math.PI * row / rows;
      double hi = -Math.PI / 2 + Math.PI * (row + 1) / rows;
      for (int slice = 0; slice < slices; slice++) {
        double a = Math.PI * 2 * slice / slices, b = Math.PI * 2 * (slice + 1) / slices;
        sphereVertex(matrices, buffer, lo, a, radius);
        sphereVertex(matrices, buffer, hi, a, radius);
        sphereVertex(matrices, buffer, hi, b, radius);
        sphereVertex(matrices, buffer, lo, b, radius);
      }
    }
  }

  private static void sphereVertex(
      MatrixStack matrices,
      VertexConsumer buffer,
      double latitude,
      double longitude,
      float radius) {
    float x = (float) (Math.cos(latitude) * Math.cos(longitude));
    float y = (float) Math.sin(latitude);
    float z = (float) (Math.cos(latitude) * Math.sin(longitude));
    buffer
        .vertex(matrices.peek().getPositionMatrix(), x * radius, y * radius, z * radius)
        .color(255, 255, 255, 255)
        .texture((float) (longitude / (Math.PI * 2)), (float) (latitude / Math.PI + .5))
        .overlay(OverlayTexture.DEFAULT_UV)
        .light(FULL_BRIGHT)
        .normal(matrices.peek().getNormalMatrix(), x, y, z)
        .next();
  }

  private static void disc(
      MatrixStack matrices, VertexConsumer buffer, float inner, float outer, float time, int ring) {
    for (int segment = 0; segment < 72; segment++) {
      double a = segment * Math.PI * 2 / 72, b = (segment + 1) * Math.PI * 2 / 72;
      float pulse = .55f + .45f * (float) Math.sin(a * 3 + time);
      int red = 255, green = (int) (20 + pulse * 85), blue = (int) (30 + pulse * 80);
      int alpha = (int) (120 + pulse * 120);
      ringVertex(matrices, buffer, inner, a, red, green, blue, alpha, 0);
      ringVertex(matrices, buffer, outer, a, red, green, blue, 0, 1);
      ringVertex(matrices, buffer, outer, b, red, green, blue, 0, 1);
      ringVertex(matrices, buffer, inner, b, red, green, blue, alpha, 0);
      // Reverse winding keeps the disc visible from below without a custom render layer.
      ringVertex(matrices, buffer, inner, b, red, green, blue, alpha, 0);
      ringVertex(matrices, buffer, outer, b, red, green, blue, 0, 1);
      ringVertex(matrices, buffer, outer, a, red, green, blue, 0, 1);
      ringVertex(matrices, buffer, inner, a, red, green, blue, alpha, 0);
    }
  }

  private static void ringVertex(
      MatrixStack matrices,
      VertexConsumer buffer,
      float radius,
      double angle,
      int red,
      int green,
      int blue,
      int alpha,
      float v) {
    buffer
        .vertex(
            matrices.peek().getPositionMatrix(),
            (float) Math.cos(angle) * radius,
            0,
            (float) Math.sin(angle) * radius)
        .color(red, green, blue, alpha)
        .texture((float) (angle / (Math.PI * 2)), v)
        .overlay(OverlayTexture.DEFAULT_UV)
        .light(FULL_BRIGHT)
        .normal(matrices.peek().getNormalMatrix(), 0, 1, 0)
        .next();
  }

  @Override
  public Identifier getTexture(RedmatterEntity entity) {
    return BLACK;
  }
}
