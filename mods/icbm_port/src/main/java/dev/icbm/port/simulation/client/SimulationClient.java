package dev.icbm.port.simulation.client;

import dev.icbm.port.simulation.*;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public final class SimulationClient {
  private SimulationClient() {}

  public static void register() {
    EntityRendererRegistry.register(SimulationContent.FLYING_BLOCK, FlyingBlockRenderer::new);
    EntityRendererRegistry.register(SimulationContent.REDMATTER, FlyingItemEntityRenderer::new);
  }

  private static final class FlyingBlockRenderer extends EntityRenderer<FlyingBlockEntity> {
    private final net.minecraft.client.render.block.BlockRenderManager blocks;

    FlyingBlockRenderer(EntityRendererFactory.Context context) {
      super(context);
      blocks = context.getBlockRenderManager();
      shadowRadius = .5f;
    }

    @Override
    public void render(
        FlyingBlockEntity entity,
        float yaw,
        float delta,
        MatrixStack matrices,
        VertexConsumerProvider vertices,
        int light) {
      matrices.push();
      matrices.translate(-.5, 0, -.5);
      blocks.renderBlockAsEntity(
          entity.blockState(), matrices, vertices, light, OverlayTexture.DEFAULT_UV);
      matrices.pop();
      super.render(entity, yaw, delta, matrices, vertices, light);
    }

    @Override
    public Identifier getTexture(FlyingBlockEntity entity) {
      return net.minecraft.screen.PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
  }
}
