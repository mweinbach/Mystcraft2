package dev.icbm.port.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3f;

/** Original item geometry oriented along the projectile's synchronized movement. */
public final class ProjectileItemRenderer<T extends Entity & FlyingItemEntity>
    extends EntityRenderer<T> {
  private final ItemRenderer items;

  public ProjectileItemRenderer(EntityRendererFactory.Context context) {
    super(context);
    items = context.getItemRenderer();
  }

  @Override
  public void render(
      T entity,
      float yaw,
      float tickDelta,
      MatrixStack matrices,
      VertexConsumerProvider consumers,
      int light) {
    matrices.push();
    var velocity = entity.getVelocity();
    double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    float heading = (float) Math.toDegrees(Math.atan2(velocity.x, velocity.z));
    float elevation = (float) Math.toDegrees(Math.atan2(velocity.y, horizontal));
    matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(heading));
    matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(90 - elevation));
    items.renderItem(
        entity.getStack(),
        ModelTransformation.Mode.NONE,
        light,
        OverlayTexture.DEFAULT_UV,
        matrices,
        consumers,
        entity.getId());
    matrices.pop();
    super.render(entity, yaw, tickDelta, matrices, consumers, light);
  }

  @Override
  public Identifier getTexture(T entity) {
    return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
  }
}
