/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.extensions.common;

import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Describes a fluid whose type defines no rendering of its own through its Fabric fluid render handler.
 */
record FabricFluidRenderExtensions(Fluid fluid) implements IClientFluidTypeExtensions {

    @Nullable
    private FluidRenderHandler handler() {
        return FluidRenderHandlerRegistry.INSTANCE.get(fluid);
    }

    @Nullable
    private ResourceLocation sprite(@Nullable BlockAndTintGetter getter, @Nullable BlockPos pos, FluidState state, int index) {
        FluidRenderHandler handler = handler();
        if (handler == null) {
            return null;
        }
        TextureAtlasSprite[] sprites = handler.getFluidSprites(getter, pos, state);
        return sprites != null && sprites.length > index && sprites[index] != null ? sprites[index].getName() : null;
    }

    private int tint(@Nullable BlockAndTintGetter getter, @Nullable BlockPos pos, FluidState state) {
        FluidRenderHandler handler = handler();
        if (handler == null) {
            return 0xFFFFFFFF;
        }
        int color = handler.getFluidColor(getter, pos, state);
        return color == -1 ? 0xFFFFFFFF : color | 0xFF000000;
    }

    @Override
    public int getTintColor() {
        return tint(null, null, fluid.defaultFluidState());
    }

    @Nullable
    @Override
    public ResourceLocation getStillTexture() {
        return sprite(null, null, fluid.defaultFluidState(), 0);
    }

    @Nullable
    @Override
    public ResourceLocation getFlowingTexture() {
        return sprite(null, null, fluid.defaultFluidState(), 1);
    }

    @Nullable
    @Override
    public ResourceLocation getOverlayTexture() {
        return sprite(null, null, fluid.defaultFluidState(), 2);
    }

    @Nullable
    @Override
    public ResourceLocation getStillTexture(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        return sprite(getter, pos, state, 0);
    }

    @Nullable
    @Override
    public ResourceLocation getFlowingTexture(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        return sprite(getter, pos, state, 1);
    }

    @Nullable
    @Override
    public ResourceLocation getOverlayTexture(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        return sprite(getter, pos, state, 2);
    }

    @Override
    public int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        return tint(getter, pos, state);
    }
}
