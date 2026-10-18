package dev.icbm.port.delivery;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.model.EntityModelLayers;

@Environment(EnvType.CLIENT)
public final class DeliveryClient {
  public static void registerClient() {
    EntityRendererRegistry.register(DeliveryContent.HELD_MISSILE, FlyingItemEntityRenderer::new);
    EntityRendererRegistry.register(DeliveryContent.CARGO, FlyingItemEntityRenderer::new);
    EntityRendererRegistry.register(DeliveryContent.BOMBLET, FlyingItemEntityRenderer::new);
    EntityRendererRegistry.register(DeliveryContent.GRENADE, FlyingItemEntityRenderer::new);
    EntityRendererRegistry.register(
        DeliveryContent.BOMB_CART,
        context -> new MinecartEntityRenderer<>(context, EntityModelLayers.TNT_MINECART));
  }

  private DeliveryClient() {}
}
