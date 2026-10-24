/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.extensions;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.client.ChunkRenderTypeSet;
import ports.projecte.lib.client.model.data.ModelData;

/**
 * Methods Forge adds to {@link BakedModel}, injected into it on Fabric.
 */
public interface IForgeBakedModel {

    private BakedModel self() {
        return (BakedModel) this;
    }

    @NotNull
    default List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData data, @Nullable RenderType renderType) {
        return self().getQuads(state, side, rand);
    }

    default boolean useAmbientOcclusion(BlockState state) {
        return self().useAmbientOcclusion();
    }

    default boolean useAmbientOcclusion(BlockState state, RenderType renderType) {
        return useAmbientOcclusion(state);
    }

    default BakedModel applyTransform(ItemTransforms.TransformType transformType, PoseStack poseStack, boolean applyLeftHandTransform) {
        self().getTransforms().getTransform(transformType).apply(applyLeftHandTransform, poseStack);
        return self();
    }

    @NotNull
    default ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData modelData) {
        return modelData;
    }

    default TextureAtlasSprite getParticleIcon(@NotNull ModelData data) {
        return self().getParticleIcon();
    }

    default ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        return ChunkRenderTypeSet.of(ItemBlockRenderTypes.getChunkRenderType(state));
    }

    default List<RenderType> getRenderTypes(ItemStack itemStack, boolean fabulous) {
        if (itemStack.getItem() instanceof BlockItem blockItem) {
            RenderType chunkType = ItemBlockRenderTypes.getChunkRenderType(blockItem.getBlock().defaultBlockState());
            if (chunkType == RenderType.translucent()) {
                return List.of(fabulous ? Sheets.translucentItemSheet() : Sheets.translucentCullBlockSheet());
            }
            return List.of(Sheets.cutoutBlockSheet());
        }
        return List.of(fabulous ? Sheets.translucentItemSheet() : Sheets.translucentCullBlockSheet());
    }

    default List<BakedModel> getRenderPasses(ItemStack itemStack, boolean fabulous) {
        return List.of(self());
    }
}
