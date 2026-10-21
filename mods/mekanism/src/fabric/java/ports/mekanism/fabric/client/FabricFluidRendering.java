/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.client;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import mekanism.common.Mekanism;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.event.client.ClientSpriteRegistryCallback;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.client.extensions.common.IClientFluidTypeExtensions;
import ports.mekanism.lib.fluids.FluidType;

/**
 * Registers Fabric fluid renderers from fluid-type client extensions, and marks Mekanism blocks that ask for the
 * fluid overlay as transparent. Forge does both inside its fluid renderer; Fabric reads a render-handler registry
 * and a transparent-block set instead.
 */
public final class FabricFluidRendering {

    private static final Set<ResourceLocation> SPRITES = new HashSet<>();

    private FabricFluidRendering() {
    }

    public static void register() {
        Map<FluidType, FluidRenderHandler> byType = new IdentityHashMap<>();
        for (Fluid fluid : Registry.FLUID) {
            FluidType type = fluid.getFluidType();
            type.initializeClientOnce();
            if (!(type.getRenderPropertiesInternal() instanceof IClientFluidTypeExtensions extensions)
                  || extensions == IClientFluidTypeExtensions.DEFAULT) {
                continue;
            }
            ResourceLocation still = extensions.getStillTexture();
            ResourceLocation flowing = extensions.getFlowingTexture();
            if (still == null || flowing == null) {
                continue;
            }
            ResourceLocation overlay = extensions.getOverlayTexture();
            FluidRenderHandler handler = byType.computeIfAbsent(type, ignored -> new FluidTypeRenderHandler(extensions, still, flowing, overlay));
            FluidRenderHandlerRegistry.INSTANCE.register(fluid, handler);
            BlockRenderLayerMap.INSTANCE.putFluid(fluid, RenderType.translucent());
            SPRITES.add(still);
            SPRITES.add(flowing);
            if (overlay != null) {
                SPRITES.add(overlay);
            }
        }
        registerOverlayBlocks();
        ClientSpriteRegistryCallback.event(TextureAtlas.LOCATION_BLOCKS).register((atlas, registry) -> SPRITES.forEach(registry::register));
    }

    /**
     * Forge asks the neighboring block on each fluid face. Fabric only stores a per-block flag, so the answer is
     * taken from the default state. Structural glass always returns true.
     */
    private static void registerOverlayBlocks() {
        FluidState sample = Fluids.WATER.defaultFluidState();
        BlockPos origin = BlockPos.ZERO;
        for (Block block : Registry.BLOCK) {
            ResourceLocation name = Registry.BLOCK.getKey(block);
            if (name == null || !Mekanism.MODID.equals(name.getNamespace())) {
                continue;
            }
            BlockState state = block.defaultBlockState();
            // Structural glass ignores the view, and no other Mekanism block overrides the method.
            if (block.shouldDisplayFluidOverlay(state, null, origin, sample)) {
                FluidRenderHandlerRegistry.INSTANCE.setBlockTransparency(block, true);
            }
        }
    }

    /**
     * Still, flowing and overlay sprites from the fluid type. The renderer picks the still sprite for the top and
     * bottom and the flowing sprite for the sides; the overlay replaces the side against transparent blocks.
     */
    private static final class FluidTypeRenderHandler implements FluidRenderHandler {

        private final IClientFluidTypeExtensions extensions;
        private final ResourceLocation still;
        private final ResourceLocation flowing;
        @Nullable
        private final ResourceLocation overlay;
        private final TextureAtlasSprite[] sprites;

        private FluidTypeRenderHandler(IClientFluidTypeExtensions extensions, ResourceLocation still, ResourceLocation flowing,
              @Nullable ResourceLocation overlay) {
            this.extensions = extensions;
            this.still = still;
            this.flowing = flowing;
            this.overlay = overlay;
            this.sprites = new TextureAtlasSprite[overlay == null ? 2 : 3];
        }

        @Override
        public TextureAtlasSprite[] getFluidSprites(@Nullable BlockAndTintGetter view, @Nullable BlockPos pos, FluidState state) {
            return sprites;
        }

        @Override
        public int getFluidColor(@Nullable BlockAndTintGetter view, @Nullable BlockPos pos, FluidState state) {
            return extensions.getTintColor(state, view, pos);
        }

        @Override
        public void reloadTextures(TextureAtlas atlas) {
            sprites[0] = atlas.getSprite(still);
            sprites[1] = atlas.getSprite(flowing);
            if (overlay != null) {
                sprites[2] = atlas.getSprite(overlay);
            }
        }
    }
}
