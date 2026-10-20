/*
 * Extreme Reactors 1.19.2 port - loader services
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.platform;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.IDynamicBakedModel;
import ports.extremereactors.corelib.client.model.data.ModelData;
import ports.extremereactors.corelib.client.model.data.ModelDataOverride;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Forge side of the baked model base: reads the corelib model data out of Forge's model data.
 * The Fabric source set has a class with the same name and API.
 */
public abstract class PlatformBakedModel implements IDynamicBakedModel {

    public abstract List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                             ModelData data, @Nullable RenderType renderType);

    public TextureAtlasSprite getParticleIcon(final ModelData data) {
        return this.getParticleIcon();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable final BlockState state, @Nullable final Direction side,
                                    final RandomSource random) {
        return this.getQuads(state, side, random, ModelData.EMPTY, null);
    }

    @Override
    public final List<BakedQuad> getQuads(@Nullable final BlockState state, @Nullable final Direction side,
                                          final RandomSource random,
                                          final net.minecraftforge.client.model.data.ModelData extraData,
                                          @Nullable final RenderType renderType) {
        final ModelData override = ModelDataOverride.get(state);

        return this.getQuads(state, side, random, null != override ? override : unwrap(extraData), renderType);
    }

    @Override
    public final TextureAtlasSprite getParticleIcon(final net.minecraftforge.client.model.data.ModelData data) {
        return this.getParticleIcon(unwrap(data));
    }

    private static ModelData unwrap(final net.minecraftforge.client.model.data.ModelData data) {

        final ModelData ours = data.get(PlatformBlockEntity.MODEL_DATA);

        return null != ours ? ours : ModelData.EMPTY;
    }
}
