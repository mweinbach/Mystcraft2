/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
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
import net.minecraft.world.level.material.Fluid;
import ports.extremereactors.corelib.client.gui.sprite.AtlasSpriteSupplier;
import ports.extremereactors.corelib.client.gui.sprite.ISprite;
import ports.extremereactors.corelib.client.model.data.ModelData;
import ports.extremereactors.corelib.data.gfx.Colour;
import ports.extremereactors.corelib.fluid.FluidStack;
import ports.extremereactors.platform.ClientPlatform;
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

    public static void markBlockRangeForRenderUpdate(final BlockPos min, final BlockPos max) {

        final Minecraft mc = Minecraft.getInstance();

        if (mc.isSameThread() && null != mc.levelRenderer) {
            mc.levelRenderer.setBlocksDirty(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
        }
    }

    /**
     * @return the system time, in milliseconds, at which the last frame finished rendering
     */
    public static long getLastRenderTime() {
        return s_lastRenderTime;
    }

    /**
     * Called by the client bootstrap at the end of each frame.
     */
    public static void onRenderFrameEnd() {
        s_lastRenderTime = System.currentTimeMillis();
    }

    //region fluids

    /** The flowing texture of a fluid, as shown in tanks and gauges. */
    public static ISprite getFlowingFluidSprite(final Fluid fluid) {
        return AtlasSpriteSupplier.of(ClientPlatform.backend().getFluidFlowingSprite(new FluidStack(fluid, 1000)));
    }

    public static ISprite getFlowingFluidSprite(final FluidStack stack) {
        return AtlasSpriteSupplier.of(ClientPlatform.backend().getFluidFlowingSprite(stack));
    }

    /** The colour the fluid textures are tinted with. */
    public static Colour getFluidTintColour(final Fluid fluid) {
        return Colour.fromARGB(ClientPlatform.backend().getFluidTint(new FluidStack(fluid, 1000)));
    }

    public static Colour getFluidTintColour(final FluidStack stack) {
        return Colour.fromARGB(ClientPlatform.backend().getFluidTint(stack));
    }

    //endregion
    //region screen painting
    // All coordinates are screen pixels; the second corner of a rectangle is exclusive.

    /** Paint a sprite, scaled to the given size, then its overlay, if any. */
    public static void paintSprite(final PoseStack matrix, final ISprite sprite, final int x, final int y,
                                   final int z, final int width, final int height) {
        paintSprite(matrix, sprite, Colour.WHITE, x, y, z, width, height);
    }

    public static void paintSprite(final PoseStack matrix, final ISprite sprite, final Colour tint, final int x,
                                   final int y, final int z, final int width, final int height) {

        if (sprite.isEmpty() || width <= 0 || height <= 0) {
            return;
        }

        paintTexturedQuad(matrix, sprite.getTextureLocation(), tint, x, y, x + width, y + height, z,
                sprite.getMinU(), sprite.getMinV(), sprite.getMaxU(), sprite.getMaxV());
        sprite.getSpriteOverlay().ifPresent(overlay -> paintSprite(matrix, overlay, x, y, z, width, height));
    }

    /**
     * Fill a rectangle with copies of a sprite at its natural size. Copies that do not fit are cut; the first whole
     * copy is placed in the corner given by {@code fromRight} and {@code fromBottom}, so that a rectangle that grows
     * from that corner reveals more of the same picture.
     */
    public static void paintTiledSprite(final PoseStack matrix, final ISprite sprite, final Colour tint,
                                        final int x, final int y, final int z, final int width, final int height,
                                        final boolean fromRight, final boolean fromBottom) {

        final int spriteWidth = sprite.getWidth();
        final int spriteHeight = sprite.getHeight();

        if (sprite.isEmpty() || width <= 0 || height <= 0) {
            return;
        }

        final float uSpan = sprite.getMaxU() - sprite.getMinU();
        final float vSpan = sprite.getMaxV() - sprite.getMinV();

        for (int dy = 0; dy < height; ) {

            final int rows = Math.min(spriteHeight, height - dy);
            final int y1 = fromBottom ? y + height - dy - rows : y + dy;
            final float v0 = fromBottom ? sprite.getMaxV() - vSpan * rows / spriteHeight : sprite.getMinV();
            final float v1 = fromBottom ? sprite.getMaxV() : sprite.getMinV() + vSpan * rows / spriteHeight;

            for (int dx = 0; dx < width; ) {

                final int columns = Math.min(spriteWidth, width - dx);
                final int x1 = fromRight ? x + width - dx - columns : x + dx;
                final float u0 = fromRight ? sprite.getMaxU() - uSpan * columns / spriteWidth : sprite.getMinU();
                final float u1 = fromRight ? sprite.getMaxU() : sprite.getMinU() + uSpan * columns / spriteWidth;

                paintTexturedQuad(matrix, sprite.getTextureLocation(), tint, x1, y1, x1 + columns, y1 + rows, z,
                        u0, v0, u1, v1);
                dx += columns;
            }

            dy += rows;
        }
    }

    public static void paintTexturedQuad(final PoseStack matrix, final ResourceLocation texture, final Colour tint,
                                         final int x1, final int y1, final int x2, final int y2, final int z,
                                         final float u0, final float v0, final float u1, final float v1) {

        final Matrix4f pose = matrix.last().pose();
        final BufferBuilder builder = Tesselator.getInstance().getBuilder();

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(tint.glRed(), tint.glGreen(), tint.glBlue(), tint.glAlpha());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(pose, x1, y2, z).uv(u0, v1).endVertex();
        builder.vertex(pose, x2, y2, z).uv(u1, v1).endVertex();
        builder.vertex(pose, x2, y1, z).uv(u1, v0).endVertex();
        builder.vertex(pose, x1, y1, z).uv(u0, v0).endVertex();
        BufferUploader.drawWithShader(builder.end());

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void paintSolidRect(final PoseStack matrix, final int x1, final int y1, final int x2, final int y2,
                                      final int z, final Colour colour) {
        paintGradientRect(matrix, x1, y1, x2, y2, z, colour, colour, colour, colour);
    }

    /** Fill a rectangle with a colour gradient between its four corners. */
    public static void paintGradientRect(final PoseStack matrix, final int x1, final int y1, final int x2,
                                         final int y2, final int z, final Colour topLeft, final Colour topRight,
                                         final Colour bottomLeft, final Colour bottomRight) {

        if (x2 <= x1 || y2 <= y1) {
            return;
        }

        final Matrix4f pose = matrix.last().pose();
        final BufferBuilder builder = Tesselator.getInstance().getBuilder();

        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        builder.vertex(pose, x1, y2, z).color(bottomLeft.red(), bottomLeft.green(), bottomLeft.blue(), bottomLeft.alpha()).endVertex();
        builder.vertex(pose, x2, y2, z).color(bottomRight.red(), bottomRight.green(), bottomRight.blue(), bottomRight.alpha()).endVertex();
        builder.vertex(pose, x2, y1, z).color(topRight.red(), topRight.green(), topRight.blue(), topRight.alpha()).endVertex();
        builder.vertex(pose, x1, y1, z).color(topLeft.red(), topLeft.green(), topLeft.blue(), topLeft.alpha()).endVertex();
        BufferUploader.drawWithShader(builder.end());

        RenderSystem.enableTexture();
    }

    /** Paint a one pixel wide frame just inside the rectangle. */
    public static void paintFrame(final PoseStack matrix, final int x1, final int y1, final int x2, final int y2,
                                  final int z, final Colour colour) {
        paintFrame(matrix, x1, y1, x2, y2, z, colour, colour);
    }

    /** Paint a one pixel wide frame: {@code topLeft} on the top and left edges, {@code bottomRight} on the others. */
    public static void paintFrame(final PoseStack matrix, final int x1, final int y1, final int x2, final int y2,
                                  final int z, final Colour topLeft, final Colour bottomRight) {

        if (x2 - x1 < 2 || y2 - y1 < 2) {

            paintSolidRect(matrix, x1, y1, x2, y2, z, topLeft);
            return;
        }

        paintSolidRect(matrix, x1, y1, x2 - 1, y1 + 1, z, topLeft);
        paintSolidRect(matrix, x1, y1 + 1, x1 + 1, y2 - 1, z, topLeft);
        paintSolidRect(matrix, x1 + 1, y2 - 1, x2, y2, z, bottomRight);
        paintSolidRect(matrix, x2 - 1, y1, x2, y2 - 1, z, bottomRight);
    }

    /**
     * Paint a raised (or, swapping the border colours, sunken) box: a frame lit from the top left around a gradient
     * that darkens towards the bottom right corner.
     */
    public static void paint3DBox(final PoseStack matrix, final int x1, final int y1, final int x2, final int y2,
                                  final int z, final Colour borderLight, final Colour borderDark,
                                  final Colour gradientLight, final Colour gradientDark) {

        paintFrame(matrix, x1, y1, x2, y2, z, borderLight, borderDark);
        paintGradientRect(matrix, x1 + 1, y1 + 1, x2 - 1, y2 - 1, z, gradientLight, gradientLight, gradientLight,
                gradientDark);
    }

    //endregion

    private static final Direction[] RENDER_SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST, null};
    private static final long MODEL_SEED = 42L;

    private static volatile long s_lastRenderTime = System.currentTimeMillis();

    private ModRenderHelper() {
    }
}
