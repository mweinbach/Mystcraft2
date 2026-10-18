package dev.icbm.port.client;

import dev.icbm.port.IcbmPort;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

public final class IcbmPortClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        EntityRendererRegistry.register(IcbmPort.MISSILE_ENTITY, FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(IcbmPort.PRIMED_EXPLOSIVE, FlyingItemEntityRenderer::new);
    }
}
