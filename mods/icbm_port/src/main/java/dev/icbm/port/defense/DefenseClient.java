package dev.icbm.port.defense;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

@Environment(EnvType.CLIENT)
final class DefenseClient {
  private DefenseClient() {}

  static void register() {
    net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
        DefenseContent.MACHINE_BLOCK_ENTITY, DefenseMachineRenderer::new);
    net.minecraft.client.gui.screen.ingame.HandledScreens.register(
        DefenseContent.MACHINE_SCREEN, DefenseScreen::new);
    EntityRendererRegistry.register(
        DefenseContent.SAM_INTERCEPTOR, dev.icbm.port.client.ProjectileItemRenderer::new);
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
