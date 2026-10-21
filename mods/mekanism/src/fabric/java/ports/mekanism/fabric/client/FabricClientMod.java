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
import net.minecraft.world.phys.HitResult;
import ports.mekanism.fabric.FabricGameEvents;
import ports.mekanism.fabric.FabricLifecycle;
import ports.mekanism.lib.client.ForgeHooksClient;
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
import ports.mekanism.lib.client.model.CompositeModel;
import ports.mekanism.lib.client.model.DynamicFluidContainerModel;
import ports.mekanism.lib.client.model.ElementsModel;
import ports.mekanism.lib.client.model.SeparateTransformsModel;
import ports.mekanism.lib.client.model.obj.ObjLoader;
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
        registerForgeGeometryLoaders();
        FabricLifecycle.forEachMod(container -> {
            String namespace = container.getNamespace();
            container.getEventBus().post(new ModelEvent.RegisterGeometryLoaders(namespace));
            container.getEventBus().post(new RegisterGuiOverlaysEvent(namespace));
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
    }

    /**
     * Client entrypoints run before the window exists, while Forge fires reload listener registration and client
     * setup once a GL context is current. Called just before the initial resource reload.
     */
    public static void beforeInitialResourceReload() {
        FabricLifecycle.forEachMod(container -> container.getEventBus().post(new RegisterClientReloadListenersEvent(container.getNamespace())));
        FabricLifecycle.startClient();
    }

    /**
     * The loaders Forge itself registers. The OBJ loader's caches are cleared on each resource reload.
     */
    private static void registerForgeGeometryLoaders() {
        ModelEvent.RegisterGeometryLoaders forge = new ModelEvent.RegisterGeometryLoaders("forge");
        forge.register("elements", ElementsModel.Loader.INSTANCE);
        forge.register("obj", ObjLoader.INSTANCE);
        forge.register("fluid_container", DynamicFluidContainerModel.Loader.INSTANCE);
        forge.register("composite", CompositeModel.Loader.INSTANCE);
        forge.register("separate_transforms", SeparateTransformsModel.Loader.INSTANCE);
        new RegisterClientReloadListenersEvent("forge").registerReloadListener(ObjLoader.INSTANCE);
    }

    private static void registerGameEvents() {
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
