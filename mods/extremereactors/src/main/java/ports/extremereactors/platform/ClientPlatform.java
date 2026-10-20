/*
 * Extreme Reactors 1.19.2 port - loader services
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.platform;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.corelib.fluid.FluidStack;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The client-only loader services. The loader client entrypoint installs a {@link Backend} before the client
 * proxy is created.
 */
public final class ClientPlatform {

    public interface Backend {

        void setRenderLayer(Block block, RenderType type);

        void registerBlockColor(BlockColor color, Block... blocks);

        /** Stitch an extra sprite into an atlas each time it is built. */
        void addAtlasSprite(ResourceLocation atlas, ResourceLocation sprite);

        /** Called while models load, to name the extra (non block state) models that must be loaded and baked. */
        void onRegisterAdditionalModels(Consumer<Consumer<ResourceLocation>> handler);

        /** Called once all the top-level models are baked; entries of the map can be replaced. */
        void onModelBakingCompleted(Consumer<Map<ResourceLocation, BakedModel>> handler);

        void onItemTooltip(TooltipHandler handler);

        /** Called after each rendered frame. */
        void onRenderFrameEnd(Runnable handler);

        /** True if the block model draws (some of) its quads in the solid chunk layer. */
        boolean isRenderedInSolidLayer(BlockState state, RandomSource random);

        /** The block atlas sprite of the flowing texture of a fluid. */
        TextureAtlasSprite getFluidFlowingSprite(FluidStack stack);

        /** The ARGB colour a fluid texture is tinted with. */
        int getFluidTint(FluidStack stack);

        /** Open the screen built by {@code factory} for the menus of a type. Call once the menus are registered. */
        <M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> void registerScreen(
                MenuType<? extends M> type, ScreenFactory<M, S> factory);
    }

    @FunctionalInterface
    public interface ScreenFactory<M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> {
        S create(M menu, Inventory inventory, Component title);
    }

    @FunctionalInterface
    public interface TooltipHandler {
        void onTooltip(ItemStack stack, TooltipFlag flag, List<Component> lines);
    }

    public static void install(final Backend implementation) {

        if (null != s_backend) {
            throw new IllegalStateException("The Extreme Reactors client platform backend is already installed");
        }

        s_backend = Objects.requireNonNull(implementation);
    }

    public static Backend backend() {

        if (null == s_backend) {
            throw new IllegalStateException("The Extreme Reactors client platform backend is not installed");
        }

        return s_backend;
    }

    private static Backend s_backend;

    private ClientPlatform() {
    }
}
