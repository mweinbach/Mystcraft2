/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.corelib.client.model.data.ModelData;
import ports.extremereactors.platform.PlatformBakedModel;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Client rendering helpers.
 */
public final class ModRenderHelper {

    public static BakedModel getMissingModel() {
        return Minecraft.getInstance().getModelManager().getMissingModel();
    }

    /**
     * The missing model from a set of baked models that is still being built.
     */
    public static BakedModel getMissingModel(final Map<ResourceLocation, BakedModel> models) {

        final BakedModel missing = models.get(ModelBakery.MISSING_MODEL_LOCATION);

        if (null == missing) {
            throw new IllegalStateException("The baked models do not contain the missing model");
        }

        return missing;
    }

    public static TextureAtlasSprite getMissingTexture() {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(MissingTextureAtlasSprite.getLocation());
    }

    public static BakedModel getModel(final BlockState state) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
    }

    /**
     * The quads of a model for the given model data. Models that do not use model data ignore it.
     */
    public static List<BakedQuad> getQuads(final BakedModel model, @Nullable final BlockState state,
                                           @Nullable final Direction side, final RandomSource random,
                                           final ModelData data, @Nullable final RenderType renderType) {
        return model instanceof PlatformBakedModel dynamic ?
                dynamic.getQuads(state, side, random, data, renderType) :
                model.getQuads(state, side, random);
    }

    /**
     * Draw all the quads of a model, untinted, at the current pose.
     */
    public static void renderModel(final BakedModel model, final ModelData data, final PoseStack stack,
                                   final VertexConsumer buffer, final int combinedLight, final int combinedOverlay,
                                   @Nullable final RenderType renderType) {

        final PoseStack.Pose pose = stack.last();
        final RandomSource random = RandomSource.create();

        for (final Direction side : RENDER_SIDES) {

            random.setSeed(MODEL_SEED);

            for (final BakedQuad quad : getQuads(model, null, side, random, data, renderType)) {
                buffer.putBulkData(pose, quad, 1.0f, 1.0f, 1.0f, combinedLight, combinedOverlay);
            }
        }
    }

    /**
     * @return the system time, in milliseconds, at which the last frame finished rendering
     */
    public static void markBlockRangeForRenderUpdate(final BlockPos min, final BlockPos max) {

        final Minecraft mc = Minecraft.getInstance();

        if (mc.isSameThread() && null != mc.levelRenderer) {
            mc.levelRenderer.setBlocksDirty(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
        }
    }

    public static long getLastRenderTime() {
        return s_lastRenderTime;
    }

    /**
     * Called by the client bootstrap at the end of each frame.
     */
    public static void onRenderFrameEnd() {
        s_lastRenderTime = System.currentTimeMillis();
    }

    private static final Direction[] RENDER_SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST, null};
    private static final long MODEL_SEED = 42L;

    private static volatile long s_lastRenderTime = System.currentTimeMillis();

    private ModRenderHelper() {
    }
}
