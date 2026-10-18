package dev.icbm.port.defense;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

@Environment(EnvType.CLIENT)
final class DefenseClient {
  private DefenseClient() {}

  static void register() {
    EntityRendererRegistry.register(DefenseContent.SAM_INTERCEPTOR, FlyingItemEntityRenderer::new);
    net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlocks(
        net.minecraft.client.render.RenderLayer.getCutout(),
        DefenseContent.REINFORCED_GLASS,
        DefenseContent.GLASS_BUTTON,
        DefenseContent.GLASS_PRESSURE_PLATE,
        DefenseContent.SPIKES,
        DefenseContent.POISON_SPIKES,
        DefenseContent.FIRE_SPIKES);
  }
}
