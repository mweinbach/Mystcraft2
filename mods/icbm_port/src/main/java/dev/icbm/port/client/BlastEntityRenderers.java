package dev.icbm.port.client;

import dev.icbm.port.entity.BlastEntities;
import dev.icbm.port.entity.FragmentEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3f;

/**
 * Original renderers for blast entities built only from vanilla models. Smoke is particles only.
 * Anvil fragments draw the vanilla anvil block; other fragments draw a vanilla iron nugget
 * (shrapnel) or fire charge (explosive) aligned with their flight.
 */
public final class BlastEntityRenderers {
  private BlastEntityRenderers() {}

  public static void register() {
    EntityRendererRegistry.register(BlastEntities.FRAGMENT, FragmentRenderer::new);
    EntityRendererRegistry.register(BlastEntities.SMOKE, EmptyEntityRenderer::new);
  }

  private static final class FragmentRenderer extends EntityRenderer<FragmentEntity> {
    private static final ItemStack SHRAPNEL = new ItemStack(Items.IRON_NUGGET);
    private static final ItemStack EXPLOSIVE = new ItemStack(Items.FIRE_CHARGE);
    private final ItemRenderer items;
    private final BlockRenderManager blocks;

    FragmentRenderer(EntityRendererFactory.Context context) {
      super(context);
      items = context.getItemRenderer();
      blocks = context.getBlockRenderManager();
      shadowRadius = .15f;
    }

    @Override
    public void render(
        FragmentEntity entity,
        float yaw,
        float tickDelta,
        MatrixStack matrices,
        VertexConsumerProvider consumers,
        int light) {
      matrices.push();
      if (entity.isAnvil()) {
        matrices.translate(-.5, 0, -.5);
        blocks.renderBlockAsEntity(
            Blocks.ANVIL.getDefaultState(), matrices, consumers, light, OverlayTexture.DEFAULT_UV);
      } else {
        matrices.translate(0, .25, 0);
        matrices.multiply(
            Vec3f.POSITIVE_Y.getDegreesQuaternion(
                MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw()) - 90));
        matrices.multiply(
            Vec3f.POSITIVE_Z.getDegreesQuaternion(
                MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch())));
        items.renderItem(
            entity.isExplosive() ? EXPLOSIVE : SHRAPNEL,
            ModelTransformation.Mode.GROUND,
            light,
            OverlayTexture.DEFAULT_UV,
            matrices,
            consumers,
            entity.getId());
      }
      matrices.pop();
      super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    @Override
    public Identifier getTexture(FragmentEntity entity) {
      return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
  }
}
