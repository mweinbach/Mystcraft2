package dev.icbm.port.delivery;

import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3f;

/** Upright original canopy item model; real cargo passengers render independently below it. */
public final class CargoCarrierRenderer extends EntityRenderer<CargoEntity> {
  private final ItemRenderer items;

  public CargoCarrierRenderer(EntityRendererFactory.Context context) {
    super(context);
    items = context.getItemRenderer();
  }

  @Override
  public void render(
      CargoEntity entity,
      float yaw,
      float tickDelta,
      MatrixStack matrices,
      VertexConsumerProvider consumers,
      int light) {
    matrices.push();
    // Rotate around the carrier origin so the independently rendered passenger anchor stays fixed.
    double time = entity.world.getTime() + (double) tickDelta;
    double phase = entity.getId() * .71;
    float swayX = (float) Math.sin(time * .065 + phase) * 2.2F;
    float swayZ = (float) Math.cos(time * .052 + phase) * 1.8F;
    float driftYaw = (float) Math.sin(time * .025 + phase) * 5;
    matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(driftYaw));
    matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(swayX));
    matrices.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(swayZ));
    matrices.scale(2, 2, 2);
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
  public Identifier getTexture(CargoEntity entity) {
    return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
  }
}
