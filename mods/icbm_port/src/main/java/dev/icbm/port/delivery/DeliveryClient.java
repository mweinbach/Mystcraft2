package dev.icbm.port.delivery;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.*;

@Environment(EnvType.CLIENT)
public final class DeliveryClient {
  public static void registerClient() {
    EntityRendererRegistry.register(
        DeliveryContent.HELD_MISSILE, dev.icbm.port.client.ProjectileItemRenderer::new);
    EntityRendererRegistry.register(DeliveryContent.CARGO, CargoCarrierRenderer::new);
    EntityRendererRegistry.register(DeliveryContent.BOMBLET, TumbledPayloadRenderer::new);
    EntityRendererRegistry.register(DeliveryContent.GRENADE, TumbledPayloadRenderer::new);
    EntityRendererRegistry.register(DeliveryContent.BOMB_CART, TntMinecartEntityRenderer::new);
  }

  private DeliveryClient() {}
}
