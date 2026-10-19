package dev.icbm.port.client;

import dev.icbm.port.entity.PrimedExplosiveEntity;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3f;

/** Original fuse presentation: physical block, accelerating pulse and a final white flash. */
@Environment(EnvType.CLIENT)
public final class PrimedExplosiveRenderer extends EntityRenderer<PrimedExplosiveEntity> {
  private final ItemRenderer items;

  public PrimedExplosiveRenderer(EntityRendererFactory.Context context) {
    super(context);
    items = context.getItemRenderer();
    shadowRadius = .45F;
  }

  @Override
  public void render(
      PrimedExplosiveEntity entity,
      float yaw,
      float delta,
      MatrixStack matrices,
      VertexConsumerProvider vertices,
      int light) {
    float fuse = Math.max(0, entity.getFuse() - delta);
    float danger = 1 - MathHelper.clamp(fuse / 30F, 0, 1);
    float age = entity.age + delta;
    float swell = MathHelper.clamp(1 - fuse / 10F, 0, 1);
    float scale =
        .95F + swell * swell * .2F + danger * .025F * MathHelper.sin(age * (.35F + danger * .6F));
    boolean flash = entity.getFuse() / 5 % 2 == 0;
    matrices.push();
    matrices.translate(0, .49, 0);
    matrices.scale(scale, scale, scale);
    matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(age * .6F));
    matrices.multiply(
        Vec3f.POSITIVE_Z.getDegreesQuaternion(MathHelper.sin(age * .7F) * danger * 3));
    int overlay =
        flash
            ? OverlayTexture.packUv(OverlayTexture.getU(.8F), OverlayTexture.getV(false))
            : OverlayTexture.DEFAULT_UV;
    items.renderItem(
        entity.getStack(),
        ModelTransformation.Mode.NONE,
        light,
        overlay,
        matrices,
        vertices,
        entity.getId());
    matrices.pop();
    super.render(entity, yaw, delta, matrices, vertices, light);
  }

  @Override
  public Identifier getTexture(PrimedExplosiveEntity entity) {
    return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
  }
}
