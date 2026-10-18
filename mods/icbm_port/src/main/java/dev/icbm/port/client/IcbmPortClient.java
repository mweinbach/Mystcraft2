package dev.icbm.port.client;

import dev.icbm.port.IcbmPort;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

public final class IcbmPortClient implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    dev.icbm.port.exposure.ExposureContent.registerClient();
    dev.icbm.port.simulation.client.SimulationClient.register();
    dev.icbm.port.launcher.LauncherContent.registerClient();
    dev.icbm.port.delivery.DeliveryClient.registerClient();
    dev.icbm.port.defense.DefenseContent.registerClient();
    net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlocks(
        net.minecraft.client.render.RenderLayer.getCutout(),
        dev.icbm.port.defense.DefenseContent.REINFORCED_GLASS,
        dev.icbm.port.defense.DefenseContent.GLASS_BUTTON,
        dev.icbm.port.defense.DefenseContent.GLASS_PRESSURE_PLATE);
    EntityRendererRegistry.register(IcbmPort.MISSILE_ENTITY, ProjectileItemRenderer::new);
    EntityRendererRegistry.register(IcbmPort.PRIMED_EXPLOSIVE, FlyingItemEntityRenderer::new);
  }
}
