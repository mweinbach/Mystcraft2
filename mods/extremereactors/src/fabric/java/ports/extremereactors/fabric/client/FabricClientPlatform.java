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
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry;
import net.fabricmc.fabric.api.event.client.ClientSpriteRegistryCallback;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.corelib.fluid.FluidStack;
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

    @Override
    public TextureAtlasSprite getFluidFlowingSprite(final FluidStack stack) {

        final TextureAtlasSprite[] sprites = FluidVariantRendering.getSprites(toVariant(stack));

        if (null != sprites && sprites.length > 1 && null != sprites[1]) {
            return sprites[1];
        }

        return null != sprites && sprites.length > 0 && null != sprites[0] ? sprites[0] :
                Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(MissingTextureAtlasSprite.getLocation());
    }

    @Override
    public int getFluidTint(final FluidStack stack) {

        final int colour = FluidVariantRendering.getColor(toVariant(stack));

        return 0 == (colour >>> 24) ? colour | 0xFF000000 : colour;
    }

    // Fabric API only widens access to the vanilla screen registry for its own use; this is its public entry point
    @SuppressWarnings("deprecation")
    @Override
    public <M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> void registerScreen(
            final MenuType<? extends M> type, final ClientPlatform.ScreenFactory<M, S> factory) {
        ScreenRegistry.<M, S>register(type, factory::create);
    }

    private static FluidVariant toVariant(final FluidStack stack) {
        return FluidVariant.of(stack.getFluid(), stack.hasTag() ? stack.getTag() : null);
    }

    private static final List<Consumer<Map<ResourceLocation, BakedModel>>> BAKING_COMPLETED_HANDLERS = new CopyOnWriteArrayList<>();
}
