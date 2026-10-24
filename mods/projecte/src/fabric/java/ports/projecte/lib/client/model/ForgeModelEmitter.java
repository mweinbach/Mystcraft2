/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.model;

import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachedBlockView;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.client.ChunkRenderTypeSet;
import ports.projecte.lib.client.model.data.ModelData;

/**
 * Renders a model written against Forge's model API through Fabric's renderer: the block entity's render attachment
 * is the model data, and each of the model's chunk render types becomes the blend mode of its quads.
 */
public final class ForgeModelEmitter {

    private static final Direction[] SIDES_AND_NULL = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};

    private ForgeModelEmitter() {
    }

    public static ModelData modelData(BlockAndTintGetter level, BlockPos pos) {
        if (level instanceof RenderAttachedBlockView view && view.getBlockEntityRenderAttachment(pos) instanceof ModelData data) {
            return data;
        }
        return ModelData.EMPTY;
    }

    public static void emitBlockQuads(BakedModel model, BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier,
          RenderContext context) {
        ModelData data = model.getModelData(level, pos, state, modelData(level, pos));
        ChunkRenderTypeSet renderTypes = model.getRenderTypes(state, randomSupplier.get(), data);
        QuadEmitter emitter = context.getEmitter();
        for (RenderType renderType : renderTypes) {
            RenderMaterial material = material(renderType);
            for (Direction side : SIDES_AND_NULL) {
                emit(emitter, model.getQuads(state, side, randomSupplier.get(), data, renderType), material, side);
            }
        }
    }

    public static void emitItemQuads(BakedModel model, ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
        QuadEmitter emitter = context.getEmitter();
        RenderMaterial material = material(null);
        for (Direction side : SIDES_AND_NULL) {
            emit(emitter, model.getQuads(null, side, randomSupplier.get(), ModelData.EMPTY, null), material, side);
        }
    }

    private static void emit(QuadEmitter emitter, List<BakedQuad> quads, RenderMaterial material, @Nullable Direction cullFace) {
        for (BakedQuad quad : quads) {
            emitter.fromVanilla(quad, material, cullFace);
            emitter.emit();
        }
    }

    private static RenderMaterial material(@Nullable RenderType renderType) {
        Renderer renderer = RendererAccess.INSTANCE.getRenderer();
        BlendMode blendMode = renderType == null ? BlendMode.DEFAULT : BlendMode.fromRenderLayer(renderType);
        return renderer.materialFinder().blendMode(0, blendMode).find();
    }
}
