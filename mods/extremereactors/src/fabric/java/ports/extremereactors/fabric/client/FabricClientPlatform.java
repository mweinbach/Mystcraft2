/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric.client;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.model.BakedModelManagerHelper;
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry;
import net.fabricmc.fabric.api.event.client.ClientSpriteRegistryCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
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
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.corelib.client.model.ICustomModelBuilder;
import ports.extremereactors.corelib.fluid.FluidStack;
import ports.extremereactors.fabric.client.model.ModelReplacements;
import ports.extremereactors.fabric.client.model.ReplacingModelProvider;
import ports.extremereactors.platform.ClientPlatform;
import ports.extremereactors.platform.Platform;

import javax.annotation.Nullable;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The Fabric implementation of the client loader services.
 */
public final class FabricClientPlatform implements ClientPlatform.Backend {

    /**
     * Called by {@code GameRendererMixin} once per frame, where Forge fires the end of its render tick.
     */
    public static void fireRenderFrameEnd() {

        for (final Runnable handler : FRAME_END_HANDLERS) {
            handler.run();
        }
    }

    public FabricClientPlatform() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new ModelsReloadedListener());
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
    public void registerModelBuilders(final List<? extends ICustomModelBuilder> builders) {

        final ModelReplacements replacements = new ModelReplacements(builders);

        ModelLoadingRegistry.INSTANCE.registerModelProvider((resourceManager, out) ->
                builders.forEach(builder -> builder.onRegisterModels(out)));
        ModelLoadingRegistry.INSTANCE.registerVariantProvider(resourceManager ->
                new ReplacingModelProvider(replacements, resourceManager));
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
        FRAME_END_HANDLERS.add(handler);
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

    /**
     * Runs after Minecraft's model manager applied a reload, the moment Forge fires its baking completed event at,
     * and hands the handlers a read-only view of the model manager's baked models.
     */
    private static final class ModelsReloadedListener
            implements SimpleSynchronousResourceReloadListener {

        @Override
        public ResourceLocation getFabricId() {
            return ID;
        }

        @Override
        public Collection<ResourceLocation> getFabricDependencies() {
            return List.of(ResourceReloadListenerKeys.MODELS);
        }

        @Override
        public void onResourceManagerReload(final ResourceManager resourceManager) {

            final Map<ResourceLocation, BakedModel> models = new BakedModelsView(Minecraft.getInstance().getModelManager());

            for (final Consumer<Map<ResourceLocation, BakedModel>> handler : BAKING_COMPLETED_HANDLERS) {
                handler.accept(models);
            }
        }

        private static final ResourceLocation ID = new ResourceLocation(Platform.MOD_ID, "models_reloaded");
    }

    /**
     * The baked models of the model manager, by model id. Only supports lookups.
     */
    private static final class BakedModelsView
            extends AbstractMap<ResourceLocation, BakedModel> {

        BakedModelsView(final ModelManager manager) {
            this._manager = manager;
        }

        @Nullable
        @Override
        public BakedModel get(final Object key) {
            return key instanceof ResourceLocation id ? BakedModelManagerHelper.getModel(this._manager, id) : null;
        }

        @Override
        public boolean containsKey(final Object key) {
            return null != this.get(key);
        }

        @Override
        public Set<Entry<ResourceLocation, BakedModel>> entrySet() {
            throw new UnsupportedOperationException("The baked models can only be looked up");
        }

        private final ModelManager _manager;
    }

    private static final List<Consumer<Map<ResourceLocation, BakedModel>>> BAKING_COMPLETED_HANDLERS = new CopyOnWriteArrayList<>();
    private static final List<Runnable> FRAME_END_HANDLERS = new CopyOnWriteArrayList<>();
}
