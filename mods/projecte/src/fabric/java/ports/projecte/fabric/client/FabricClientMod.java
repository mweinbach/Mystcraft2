/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.client;

import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import ports.projecte.lib.event.entity.player.ItemTooltipEvent;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.ClientSpriteRegistryCallback;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.HitResult;
import ports.projecte.fabric.FabricGameEvents;
import ports.projecte.fabric.FabricLifecycle;
import ports.projecte.lib.client.ForgeHooksClient;
import ports.projecte.lib.client.event.EntityRenderersEvent;
import ports.projecte.lib.client.event.EntityRenderersEvent.AddLayers;
import ports.projecte.lib.client.event.RegisterClientReloadListenersEvent;
import ports.projecte.lib.client.event.RegisterColorHandlersEvent;
import ports.projecte.lib.client.event.RegisterGuiOverlaysEvent;
import ports.projecte.lib.client.event.RegisterItemDecorationsEvent;
import ports.projecte.lib.client.event.RegisterKeyMappingsEvent;
import ports.projecte.lib.client.event.RegisterParticleProvidersEvent;
import ports.projecte.lib.client.event.RenderLevelStageEvent;
import ports.projecte.lib.client.event.TextureStitchEvent;
import ports.projecte.lib.event.TickEvent;
import ports.projecte.lib.eventbus.api.EventPriority;
import ports.projecte.lib.fml.LogicalSide;
import ports.projecte.lib.fml.ModList;

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
        // ProjectE's models are plain vanilla JSON, so Forge's geometry loaders and additional-model registration are
        // not carried over.
        FabricLifecycle.forEachMod(container -> container.getEventBus().post(new RegisterGuiOverlaysEvent(container.getNamespace())));
        ClientSpriteRegistryCallback.event(TextureAtlas.LOCATION_BLOCKS).register((atlas, registry) -> {
            Set<ResourceLocation> sprites = new java.util.HashSet<>();
            FabricLifecycle.postToMods(new TextureStitchEvent.Pre(atlas, sprites));
            sprites.forEach(registry::register);
        });
    }

    /**
     * Client entrypoints run before the window exists, while Forge fires reload listener registration and client
     * setup once a GL context is current. Called just before the initial resource reload.
     */
    public static void beforeInitialResourceReload() {
        FabricLifecycle.forEachMod(container -> container.getEventBus().post(new RegisterClientReloadListenersEvent(container.getNamespace())));
        FabricLifecycle.startClient();
    }

    private static void registerGameEvents() {
        ItemTooltipCallback.EVENT.register((stack, flag, lines) ->
              FabricGameEvents.post(new ItemTooltipEvent(stack, Minecraft.getInstance().player, lines, flag)));
        // Key presses are counted before any tick listener reads them.
        ClientTickEvents.START_CLIENT_TICK.register(client -> ports.projecte.lib.client.settings.ForgeKeyMapping.tickAll());
        ClientTickEvents.START_CLIENT_TICK.register(client -> FabricGameEvents.post(new TickEvent.ClientTickEvent(TickEvent.Phase.START)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> FabricGameEvents.post(new TickEvent.ClientTickEvent(TickEvent.Phase.END)));
        ClientTickEvents.START_WORLD_TICK.register(level -> FabricGameEvents.post(new TickEvent.LevelTickEvent(LogicalSide.CLIENT, TickEvent.Phase.START, level, () -> true)));
        ClientTickEvents.END_WORLD_TICK.register(level -> FabricGameEvents.post(new TickEvent.LevelTickEvent(LogicalSide.CLIENT, TickEvent.Phase.END, level, () -> true)));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> renderStage(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS, context));
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register(FabricClientMod::beforeBlockOutline);
    }

    /**
     * Forge posts the highlight event before the block outline and skips vanilla when it is canceled. Fabric asks the
     * listener to return {@code false} to skip the outline. Entity targets have no vanilla outline; the block event is
     * the one the configurator and wrench use.
     */
    private static boolean beforeBlockOutline(WorldRenderContext context, HitResult hit) {
        if (context.matrixStack() == null || context.consumers() == null || hit == null) {
            return true;
        }
        boolean canceled = ForgeHooksClient.onDrawHighlight(context.worldRenderer(), context.camera(), hit, context.tickDelta(),
              context.matrixStack(), context.consumers());
        return !canceled;
    }

    /**
     * {@code AFTER_PARTICLES} is posted from {@code LevelRenderer} immediately after particles, where Forge posts it.
     */
    private static void renderStage(RenderLevelStageEvent.Stage stage, WorldRenderContext context) {
        FabricGameEvents.post(new RenderLevelStageEvent(stage, context.worldRenderer(), context.matrixStack(), context.projectionMatrix(),
              context.worldRenderer().ticks, context.tickDelta(), context.camera(), context.frustum()));
    }
}
