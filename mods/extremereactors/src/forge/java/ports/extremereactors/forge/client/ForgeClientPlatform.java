/*
 * Extreme Reactors 1.19.2 port - Forge adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.forge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import ports.extremereactors.platform.ClientPlatform;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The Forge implementation of the client loader services.
 */
final class ForgeClientPlatform implements ClientPlatform.Backend {

    ForgeClientPlatform(final IEventBus modBus) {

        this._modBus = modBus;
        this._atlasSprites = new CopyOnWriteArrayList<>();

        modBus.addListener(EventPriority.NORMAL, false, TextureStitchEvent.Pre.class, event -> {

            final ResourceLocation atlas = event.getAtlas().location();

            for (final AtlasSprite entry : this._atlasSprites) {
                if (entry.atlas().equals(atlas)) {
                    event.addSprite(entry.sprite());
                }
            }
        });
    }

    @SuppressWarnings("removal")
    @Override
    public void setRenderLayer(final Block block, final RenderType type) {
        ItemBlockRenderTypes.setRenderLayer(block, type);
    }

    /**
     * Called from the client setup, after Forge built the block colors.
     */
    @Override
    public void registerBlockColor(final BlockColor color, final Block... blocks) {
        Minecraft.getInstance().getBlockColors().register(color, blocks);
    }

    @Override
    public void addAtlasSprite(final ResourceLocation atlas, final ResourceLocation sprite) {
        this._atlasSprites.add(new AtlasSprite(atlas, sprite));
    }

    @Override
    public void onRegisterAdditionalModels(final Consumer<Consumer<ResourceLocation>> handler) {
        this._modBus.addListener(EventPriority.NORMAL, false, ModelEvent.RegisterAdditional.class,
                event -> handler.accept(event::register));
    }

    @Override
    public void onModelBakingCompleted(final Consumer<Map<ResourceLocation, BakedModel>> handler) {
        this._modBus.addListener(EventPriority.NORMAL, false, ModelEvent.BakingCompleted.class,
                event -> handler.accept(event.getModels()));
    }

    @Override
    public void onItemTooltip(final ClientPlatform.TooltipHandler handler) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ItemTooltipEvent.class,
                event -> handler.onTooltip(event.getItemStack(), event.getFlags(), event.getToolTip()));
    }

    @Override
    public void onRenderFrameEnd(final Runnable handler) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.RenderTickEvent.class, event -> {

            if (TickEvent.Phase.END == event.phase) {
                handler.run();
            }
        });
    }

    @Override
    public boolean isRenderedInSolidLayer(final BlockState state, final RandomSource random) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(state)
                .getRenderTypes(state, random, ModelData.EMPTY).contains(RenderType.solid());
    }

    private record AtlasSprite(ResourceLocation atlas, ResourceLocation sprite) {
    }

    private final IEventBus _modBus;
    private final List<AtlasSprite> _atlasSprites;
}
