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
  private final java.util.Map<Entity, Integer> effectTicks = new java.util.WeakHashMap<>();

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
    // Ordinary entity velocity is not necessarily synchronized. Position interpolation is.
    var velocity =
        new net.minecraft.util.math.Vec3d(
            entity.getX() - entity.prevX,
            entity.getY() - entity.prevY,
            entity.getZ() - entity.prevZ);
    if (velocity.lengthSquared() < 0.000001) velocity = entity.getVelocity();
    double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    float heading =
        velocity.lengthSquared() > 0.000001
            ? (float) Math.toDegrees(Math.atan2(velocity.x, velocity.z))
            : entity.getYaw(tickDelta);
    float elevation =
        velocity.lengthSquared() > 0.000001
            ? (float) Math.toDegrees(Math.atan2(velocity.y, horizontal))
            : -entity.getPitch(tickDelta);
    if (entity instanceof dev.icbm.port.entity.MissileEntity missile
        && missile.isVisuallyWarmingUp()) elevation = 90;
    if (!java.util.Objects.equals(effectTicks.put(entity, entity.age), entity.age)) {
      boolean warmup =
          entity instanceof dev.icbm.port.entity.MissileEntity missile
              && missile.isVisuallyWarmingUp();
      boolean disabled =
          entity instanceof dev.icbm.port.entity.MissileEntity missile
              && missile.isVisuallyDisabled();
      if (!disabled && (warmup || velocity.lengthSquared() > .0001)) {
        var tail =
            velocity.lengthSquared() > .0001
                ? velocity.normalize().multiply(-.55)
                : new net.minecraft.util.math.Vec3d(0, -.3, 0);
        entity.world.addParticle(
            warmup
                ? net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE
                : net.minecraft.particle.ParticleTypes.FLAME,
            entity.getX() + tail.x,
            entity.getY() + tail.y,
            entity.getZ() + tail.z,
            tail.x * .08,
            .01 + tail.y * .08,
            tail.z * .08);
        if (!warmup)
          entity.world.addParticle(
              net.minecraft.particle.ParticleTypes.SMOKE,
              entity.getX() + tail.x,
              entity.getY() + tail.y,
              entity.getZ() + tail.z,
              tail.x * .04,
              tail.y * .04,
              tail.z * .04);
      }
    }
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
