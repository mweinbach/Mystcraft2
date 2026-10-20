/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric.client;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.ClientSpriteRegistryCallback;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.platform.ClientPlatform;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The Fabric implementation of the client loader services.
 */
public final class FabricClientPlatform implements ClientPlatform.Backend {

    /**
     * Called by the model bakery mixin once every top-level model is baked.
     */
    public static void fireModelBakingCompleted(final Map<ResourceLocation, BakedModel> models) {

        for (final Consumer<Map<ResourceLocation, BakedModel>> handler : BAKING_COMPLETED_HANDLERS) {
            handler.accept(models);
        }
    }

    @Override
    public void setRenderLayer(final Block block, final RenderType type) {
        BlockRenderLayerMap.INSTANCE.putBlock(block, type);
    }

    @Override
    public void registerBlockColor(final BlockColor color, final Block... blocks) {
        ColorProviderRegistry.BLOCK.register(color, blocks);
    }

    @Override
    public void addAtlasSprite(final ResourceLocation atlas, final ResourceLocation sprite) {
        ClientSpriteRegistryCallback.event(atlas).register((texture, registry) -> registry.register(sprite));
    }

    @Override
    public void onRegisterAdditionalModels(final Consumer<Consumer<ResourceLocation>> handler) {
        ModelLoadingRegistry.INSTANCE.registerModelProvider((resourceManager, out) -> handler.accept(out));
    }

    @Override
    public void onModelBakingCompleted(final Consumer<Map<ResourceLocation, BakedModel>> handler) {
        BAKING_COMPLETED_HANDLERS.add(handler);
    }

    @Override
    public void onItemTooltip(final ClientPlatform.TooltipHandler handler) {
        ItemTooltipCallback.EVENT.register(handler::onTooltip);
    }

    @Override
    public void onRenderFrameEnd(final Runnable handler) {
        WorldRenderEvents.END.register(context -> handler.run());
    }

    @Override
    public boolean isRenderedInSolidLayer(final BlockState state, final RandomSource random) {
        return RenderType.solid() == ItemBlockRenderTypes.getChunkRenderType(state);
    }

    private static final List<Consumer<Map<ResourceLocation, BakedModel>>> BAKING_COMPLETED_HANDLERS = new CopyOnWriteArrayList<>();
}
