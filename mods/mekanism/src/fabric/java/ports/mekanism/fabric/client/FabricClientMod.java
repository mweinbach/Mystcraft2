/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.client;

import java.util.HashSet;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.ClientSpriteRegistryCallback;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import ports.mekanism.fabric.FabricGameEvents;
import ports.mekanism.fabric.FabricLifecycle;
import ports.mekanism.lib.client.event.EntityRenderersEvent;
import ports.mekanism.lib.client.event.ModelEvent;
import ports.mekanism.lib.client.event.RegisterClientReloadListenersEvent;
import ports.mekanism.lib.client.event.RegisterColorHandlersEvent;
import ports.mekanism.lib.client.event.RegisterGuiOverlaysEvent;
import ports.mekanism.lib.client.event.RegisterItemDecorationsEvent;
import ports.mekanism.lib.client.event.RegisterKeyMappingsEvent;
import ports.mekanism.lib.client.event.RegisterParticleProvidersEvent;
import ports.mekanism.lib.client.event.RenderLevelStageEvent;
import ports.mekanism.lib.client.event.TextureStitchEvent;
import ports.mekanism.lib.event.TickEvent;
import ports.mekanism.lib.fml.LogicalSide;

/**
 * The Fabric client entrypoint: fires Forge's client registration events, then client setup.
 */
public final class FabricClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FabricClientNetworking.install();
        registerGameEvents();

        FabricLifecycle.postToMods(new RegisterKeyMappingsEvent());
        FabricLifecycle.postToMods(new EntityRenderersEvent.RegisterLayerDefinitions());
        FabricLifecycle.postToMods(new EntityRenderersEvent.RegisterRenderers());
        FabricLifecycle.postToMods(new RegisterParticleProvidersEvent());
        FabricLifecycle.postToMods(new RegisterColorHandlersEvent.Block());
        FabricLifecycle.postToMods(new RegisterColorHandlersEvent.Item());
        FabricLifecycle.postToMods(new RegisterItemDecorationsEvent());
        FabricLifecycle.forEachMod(container -> {
            String namespace = container.getNamespace();
            container.getEventBus().post(new ModelEvent.RegisterGeometryLoaders(namespace));
            container.getEventBus().post(new RegisterGuiOverlaysEvent(namespace));
            container.getEventBus().post(new RegisterClientReloadListenersEvent(namespace));
        });
        ModelLoadingRegistry.INSTANCE.registerModelProvider((manager, out) -> {
            Set<ResourceLocation> models = new HashSet<>();
            FabricLifecycle.postToMods(new ModelEvent.RegisterAdditional(models));
            models.forEach(out);
        });
        ClientSpriteRegistryCallback.event(TextureAtlas.LOCATION_BLOCKS).register((atlas, registry) -> {
            Set<ResourceLocation> sprites = new HashSet<>();
            FabricLifecycle.postToMods(new TextureStitchEvent.Pre(atlas, sprites));
            sprites.forEach(registry::register);
        });
        FabricLifecycle.startClient();
    }

    private static void registerGameEvents() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> FabricGameEvents.post(new TickEvent.ClientTickEvent(TickEvent.Phase.START)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> FabricGameEvents.post(new TickEvent.ClientTickEvent(TickEvent.Phase.END)));
        ClientTickEvents.START_WORLD_TICK.register(level -> FabricGameEvents.post(new TickEvent.LevelTickEvent(LogicalSide.CLIENT, TickEvent.Phase.START, level, () -> true)));
        ClientTickEvents.END_WORLD_TICK.register(level -> FabricGameEvents.post(new TickEvent.LevelTickEvent(LogicalSide.CLIENT, TickEvent.Phase.END, level, () -> true)));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> renderStage(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS, context));
        WorldRenderEvents.LAST.register(context -> renderStage(RenderLevelStageEvent.Stage.AFTER_PARTICLES, context));
    }

    /**
     * Fabric has no stage between particles and weather; Forge's {@code AFTER_PARTICLES} fires at the end of the level
     * render instead.
     */
    private static void renderStage(RenderLevelStageEvent.Stage stage, WorldRenderContext context) {
        FabricGameEvents.post(new RenderLevelStageEvent(stage, context.worldRenderer(), context.matrixStack(), context.projectionMatrix(),
              context.worldRenderer().ticks, context.tickDelta(), context.camera(), context.frustum()));
    }
}
