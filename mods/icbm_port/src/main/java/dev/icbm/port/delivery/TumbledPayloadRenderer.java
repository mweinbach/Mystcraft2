package dev.icbm.port.delivery;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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

/** Solid payload geometry with render-only tumbling that settles when it touches the ground. */
@Environment(EnvType.CLIENT)
public final class TumbledPayloadRenderer<T extends Entity & FlyingItemEntity>
    extends EntityRenderer<T> {
  private final ItemRenderer items;
  private final Map<T, Spin> spins = new WeakHashMap<>();

  private static final class Spin {
    double lastTime;
    float pitch;
    float roll;

    Spin(double time, int id) {
      lastTime = time;
      pitch = (id * 47) % 360;
      roll = (id * 29) % 360;
    }
  }

  public TumbledPayloadRenderer(EntityRendererFactory.Context context) {
    super(context);
    items = context.getItemRenderer();
    shadowRadius = .12F;
  }

  @Override
  public void render(
      T entity,
      float yaw,
      float tickDelta,
      MatrixStack matrices,
      VertexConsumerProvider consumers,
      int light) {
    // Some delivery entities run baseTick rather than Entity.tick, so their age does not advance.
    // The client world clock supplies the same partial-tick interpolation without changing physics.
    double time = entity.world.getTime() + (double) tickDelta;
    Spin spin = spins.computeIfAbsent(entity, e -> new Spin(time, e.getId()));
    double elapsed = Math.max(0, Math.min(2, time - spin.lastTime));
    spin.lastTime = time;
    if (!entity.isOnGround() && !entity.hasVehicle()) {
      float rate = entity instanceof GrenadeEntity ? 10 : 6;
      spin.pitch = (spin.pitch + (float) elapsed * rate) % 360;
      spin.roll = (spin.roll + (float) elapsed * rate * .45F) % 360;
    }
    matrices.push();
    matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion((entity.getId() * 61) % 360));
    matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(spin.pitch));
    matrices.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(spin.roll));
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
