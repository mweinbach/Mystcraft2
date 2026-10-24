package com.agricraft.agricraft.client.ber;

import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationChannelBlockEntity;
import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationComponentBlockEntity;
import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationTankBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws the water in a channel, the slopes towards its neighbours and the valve hand wheel. Ported from AgriCraft 3's
 * {@code TileEntityIrrigationChannelRenderer} (MIT).
 */
public class IrrigationChannelRenderer implements BlockEntityRenderer<IrrigationChannelBlockEntity> {

	public static final ResourceLocation MODEL_HANDWHEEL = new ResourceLocation("agricraft", "block/channel/valve_wheel");

	public IrrigationChannelRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(IrrigationChannelBlockEntity channel, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
		TextureAtlasSprite water = IrrigationRenderUtil.sprite(IrrigationRenderUtil.WATER_STILL);
		int color = IrrigationRenderUtil.waterColor(channel.getLevel(), channel.getBlockPos());
		VertexConsumer consumer = buffer.getBuffer(Sheets.translucentCullBlockSheet());
		PoseStack.Pose pose = poseStack.last();
		float level = channel.getRenderLevel(partialTicks) - channel.getBlockPos().getY();
		if (channel.getContent() > 0 && channel.isOpen()) {
			IrrigationRenderUtil.faceUp(consumer, pose, 6, 6, 10, 10, 16 * level, water, color, 1.0F, light, overlay);
		}
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			this.drawConnection(consumer, pose, channel, dir, partialTicks, water, color, light, overlay);
		}
		if (channel.hasValve() && channel.hasHandWheel()) {
			this.renderValveHandWheel(channel, poseStack, buffer, partialTicks, light, overlay);
		}
	}

	protected void drawConnection(VertexConsumer consumer, PoseStack.Pose pose, IrrigationChannelBlockEntity channel, Direction dir, float partialTicks,
	                              TextureAtlasSprite water, int color, int light, int overlay) {
		IrrigationComponentBlockEntity component = channel.getNeighbour(dir);
		if (component == null) {
			return;
		}
		float y1 = channel.getRenderLevel(partialTicks) - channel.getBlockPos().getY();
		float y2 = this.getConnectionLevel(channel, component, partialTicks);
		if (this.dontRenderConnection(channel, component, y2)) {
			return;
		}
		if (channel.isClosed()) {
			y1 = y2;
		}
		y1 *= 16;
		y2 *= 16;
		switch (dir) {
			case NORTH -> {
				vertex(consumer, pose, 10, y2, 0, water, color, light, overlay);
				vertex(consumer, pose, 6, y2, 0, water, color, light, overlay);
				vertex(consumer, pose, 6, y1, 6, water, color, light, overlay);
				vertex(consumer, pose, 10, y1, 6, water, color, light, overlay);
			}
			case EAST -> {
				vertex(consumer, pose, 16, y2, 6, water, color, light, overlay);
				vertex(consumer, pose, 10, y1, 6, water, color, light, overlay);
				vertex(consumer, pose, 10, y1, 10, water, color, light, overlay);
				vertex(consumer, pose, 16, y2, 10, water, color, light, overlay);
			}
			case SOUTH -> {
				vertex(consumer, pose, 10, y1, 10, water, color, light, overlay);
				vertex(consumer, pose, 6, y1, 10, water, color, light, overlay);
				vertex(consumer, pose, 6, y2, 16, water, color, light, overlay);
				vertex(consumer, pose, 10, y2, 16, water, color, light, overlay);
			}
			default -> {
				vertex(consumer, pose, 6, y1, 6, water, color, light, overlay);
				vertex(consumer, pose, 0, y2, 6, water, color, light, overlay);
				vertex(consumer, pose, 0, y2, 10, water, color, light, overlay);
				vertex(consumer, pose, 6, y1, 10, water, color, light, overlay);
			}
		}
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, TextureAtlasSprite water, int color, int light, int overlay) {
		IrrigationRenderUtil.vertex(consumer, pose, x, y, z, water, x, z, color, 1.0F, light, overlay, 0, 1, 0);
	}

	protected boolean dontRenderConnection(IrrigationChannelBlockEntity self, IrrigationComponentBlockEntity other, float otherLevel) {
		if (self.getContent() == 0) {
			if (other.getContent() == 0) {
				return true;
			}
			if (other instanceof IrrigationChannelBlockEntity otherChannel) {
				return otherChannel.isClosed();
			}
			return other instanceof IrrigationTankBlockEntity && otherLevel < other.getMinLevel() - other.getBlockPos().getY();
		}
		return self.isClosed() && other.getContent() == 0;
	}

	protected float getConnectionLevel(IrrigationChannelBlockEntity self, IrrigationComponentBlockEntity other, float partialTicks) {
		float ownLevel = self.getRenderLevel(partialTicks) - self.getBlockPos().getY();
		float level = other.getRenderLevel(partialTicks) - other.getBlockPos().getY();
		float minLevel = self.getMinLevel() - self.getBlockPos().getY();
		float maxLevel = self.getMaxLevel() - self.getBlockPos().getY();
		if (other instanceof IrrigationChannelBlockEntity otherChannel) {
			if (otherChannel.isOpen()) {
				return (ownLevel + level) / 2;
			}
			return self.isOpen() ? ownLevel : (ownLevel + level) / 2;
		} else if (other instanceof IrrigationTankBlockEntity) {
			return Math.max(minLevel, Math.min(level, maxLevel));
		}
		return ownLevel;
	}

	protected void renderValveHandWheel(IrrigationChannelBlockEntity channel, PoseStack poseStack, MultiBufferSource buffer, float partialTicks, int light, int overlay) {
		float f = channel.getValveAnimationProgress(partialTicks);
		float minY = Mth.lerp(f, 6, 9);
		float maxY = Mth.lerp(f, 10, 13);
		// valve stem
		IrrigationRenderUtil.prism(buffer.getBuffer(Sheets.solidBlockSheet()), poseStack.last(), 6, minY, 6, 10, maxY, 10,
				IrrigationRenderUtil.sprite(IrrigationRenderUtil.IRON_BLOCK), light, overlay);
		// hand wheel
		BakedModel model = Minecraft.getInstance().getModelManager().bakedRegistry.get(MODEL_HANDWHEEL);
		if (model == null) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5, 0, 0.5);
		poseStack.mulPose(Vector3f.YP.rotationDegrees(Mth.lerp(f, 0, 180)));
		poseStack.translate(-0.5, 0, -0.5);
		Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(poseStack.last(), buffer.getBuffer(Sheets.cutoutBlockSheet()),
				null, model, 1.0F, 1.0F, 1.0F, light, overlay);
		poseStack.popPose();
	}

}
