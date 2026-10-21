/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.client.ChunkRenderTypeSet;
import ports.mekanism.lib.client.model.data.ModelData;

/**
 * A model that forwards everything, including the Forge model API, to another model. On Fabric it renders through
 * {@link ForgeModelEmitter} so subclasses that read model data see it.
 */
public abstract class BakedModelWrapper<T extends BakedModel> implements BakedModel, FabricBakedModel {

    protected final T originalModel;

    public BakedModelWrapper(T originalModel) {
        this.originalModel = originalModel;
    }

    @NotNull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand) {
        return originalModel.getQuads(state, side, rand);
    }

    @NotNull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData extraData,
          @Nullable RenderType renderType) {
        return originalModel.getQuads(state, side, rand, extraData, renderType);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return originalModel.useAmbientOcclusion();
    }

    @Override
    public boolean useAmbientOcclusion(BlockState state) {
        return originalModel.useAmbientOcclusion(state);
    }

    @Override
    public boolean useAmbientOcclusion(BlockState state, RenderType renderType) {
        return originalModel.useAmbientOcclusion(state, renderType);
    }

    @Override
    public boolean isGui3d() {
        return originalModel.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return originalModel.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return originalModel.isCustomRenderer();
    }

    @NotNull
    @Override
    public TextureAtlasSprite getParticleIcon() {
        return originalModel.getParticleIcon();
    }

    @Override
    public TextureAtlasSprite getParticleIcon(@NotNull ModelData data) {
        return originalModel.getParticleIcon(data);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public ItemTransforms getTransforms() {
        return originalModel.getTransforms();
    }

    @NotNull
    @Override
    public ItemOverrides getOverrides() {
        return originalModel.getOverrides();
    }

    @Override
    public BakedModel applyTransform(ItemTransforms.TransformType transformType, PoseStack poseStack, boolean applyLeftHandTransform) {
        return originalModel.applyTransform(transformType, poseStack, applyLeftHandTransform);
    }

    @NotNull
    @Override
    public ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData modelData) {
        return originalModel.getModelData(level, pos, state, modelData);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        return originalModel.getRenderTypes(state, rand, data);
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack itemStack, boolean fabulous) {
        return originalModel.getRenderTypes(itemStack, fabulous);
    }

    @Override
    public List<BakedModel> getRenderPasses(ItemStack itemStack, boolean fabulous) {
        return originalModel.getRenderPasses(itemStack, fabulous);
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter blockView, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier, RenderContext context) {
        ForgeModelEmitter.emitBlockQuads(this, blockView, state, pos, randomSupplier, context);
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
        ForgeModelEmitter.emitItemQuads(this, stack, randomSupplier, context);
    }
}
