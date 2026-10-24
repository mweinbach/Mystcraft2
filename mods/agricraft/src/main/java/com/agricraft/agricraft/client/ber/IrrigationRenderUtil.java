package com.agricraft.agricraft.client.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import org.jetbrains.annotations.Nullable;

/**
 * Small quad helpers for the irrigation renderers, replacing InfinityLib's tessellator. Coordinates are in 1/16 block.
 */
public final class IrrigationRenderUtil {

	public static final ResourceLocation WATER_STILL = new ResourceLocation("minecraft", "block/water_still");
	public static final ResourceLocation IRON_BLOCK = new ResourceLocation("minecraft", "block/iron_block");
	private static final float UNIT = 1 / 16.0F;

	private IrrigationRenderUtil() {
	}

	public static TextureAtlasSprite sprite(ResourceLocation id) {
		return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);
	}

	public static int waterColor(@Nullable BlockAndTintGetter level, BlockPos pos) {
		return level == null ? 0x3F76E4 : BiomeColors.getAverageWaterColor(level, pos);
	}

	/** A vertex at (x, y, z) in 1/16 block, with texture coordinates (u, v) in 1/16 sprite. */
	public static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, TextureAtlasSprite sprite, float u, float v,
	                          int color, float alpha, int light, int overlay, float nx, float ny, float nz) {
		Matrix4f matrix = pose.pose();
		Matrix3f normal = pose.normal();
		consumer.vertex(matrix, x * UNIT, y * UNIT, z * UNIT)
				.color((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, alpha)
				.uv(sprite.getU(u), sprite.getV(v))
				.overlayCoords(overlay)
				.uv2(light)
				.normal(normal, nx, ny, nz)
				.endVertex();
	}

	/** An upward facing quad at height y. */
	public static void faceUp(VertexConsumer consumer, PoseStack.Pose pose, float minX, float minZ, float maxX, float maxZ, float y,
	                          TextureAtlasSprite sprite, int color, float alpha, int light, int overlay) {
		vertex(consumer, pose, minX, y, minZ, sprite, minX, minZ, color, alpha, light, overlay, 0, 1, 0);
		vertex(consumer, pose, minX, y, maxZ, sprite, minX, maxZ, color, alpha, light, overlay, 0, 1, 0);
		vertex(consumer, pose, maxX, y, maxZ, sprite, maxX, maxZ, color, alpha, light, overlay, 0, 1, 0);
		vertex(consumer, pose, maxX, y, minZ, sprite, maxX, minZ, color, alpha, light, overlay, 0, 1, 0);
	}

	/** An axis-aligned box with all six faces. */
	public static void prism(VertexConsumer consumer, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2,
	                         TextureAtlasSprite sprite, int light, int overlay) {
		int c = 0xFFFFFF;
		// down
		vertex(consumer, pose, x1, y1, z2, sprite, x1, z2, c, 1, light, overlay, 0, -1, 0);
		vertex(consumer, pose, x1, y1, z1, sprite, x1, z1, c, 1, light, overlay, 0, -1, 0);
		vertex(consumer, pose, x2, y1, z1, sprite, x2, z1, c, 1, light, overlay, 0, -1, 0);
		vertex(consumer, pose, x2, y1, z2, sprite, x2, z2, c, 1, light, overlay, 0, -1, 0);
		// up
		faceUp(consumer, pose, x1, z1, x2, z2, y2, sprite, c, 1, light, overlay);
		// north
		vertex(consumer, pose, x2, y2, z1, sprite, x2, 16 - y2, c, 1, light, overlay, 0, 0, -1);
		vertex(consumer, pose, x2, y1, z1, sprite, x2, 16 - y1, c, 1, light, overlay, 0, 0, -1);
		vertex(consumer, pose, x1, y1, z1, sprite, x1, 16 - y1, c, 1, light, overlay, 0, 0, -1);
		vertex(consumer, pose, x1, y2, z1, sprite, x1, 16 - y2, c, 1, light, overlay, 0, 0, -1);
		// south
		vertex(consumer, pose, x1, y2, z2, sprite, x1, 16 - y2, c, 1, light, overlay, 0, 0, 1);
		vertex(consumer, pose, x1, y1, z2, sprite, x1, 16 - y1, c, 1, light, overlay, 0, 0, 1);
		vertex(consumer, pose, x2, y1, z2, sprite, x2, 16 - y1, c, 1, light, overlay, 0, 0, 1);
		vertex(consumer, pose, x2, y2, z2, sprite, x2, 16 - y2, c, 1, light, overlay, 0, 0, 1);
		// west
		vertex(consumer, pose, x1, y2, z1, sprite, z1, 16 - y2, c, 1, light, overlay, -1, 0, 0);
		vertex(consumer, pose, x1, y1, z1, sprite, z1, 16 - y1, c, 1, light, overlay, -1, 0, 0);
		vertex(consumer, pose, x1, y1, z2, sprite, z2, 16 - y1, c, 1, light, overlay, -1, 0, 0);
		vertex(consumer, pose, x1, y2, z2, sprite, z2, 16 - y2, c, 1, light, overlay, -1, 0, 0);
		// east
		vertex(consumer, pose, x2, y2, z2, sprite, z2, 16 - y2, c, 1, light, overlay, 1, 0, 0);
		vertex(consumer, pose, x2, y1, z2, sprite, z2, 16 - y1, c, 1, light, overlay, 1, 0, 0);
		vertex(consumer, pose, x2, y1, z1, sprite, z1, 16 - y1, c, 1, light, overlay, 1, 0, 0);
		vertex(consumer, pose, x2, y2, z1, sprite, z1, 16 - y2, c, 1, light, overlay, 1, 0, 0);
	}

}
