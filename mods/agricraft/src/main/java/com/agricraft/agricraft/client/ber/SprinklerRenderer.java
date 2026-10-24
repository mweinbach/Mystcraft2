package com.agricraft.agricraft.client.ber;

import com.agricraft.agricraft.common.block.entity.irrigation.SprinklerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Draws the rotating sprinkler head, ported from AgriCraft 3's {@code TileEntitySprinklerRenderer} (MIT).
 */
public class SprinklerRenderer implements BlockEntityRenderer<SprinklerBlockEntity> {

	public SprinklerRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(SprinklerBlockEntity sprinkler, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
		poseStack.pushPose();
		poseStack.translate(0.5, 0, 0.5);
		poseStack.mulPose(Vector3f.YP.rotationDegrees(sprinkler.getAngle(partialTicks)));
		poseStack.translate(-0.5, 0, -0.5);
		VertexConsumer consumer = buffer.getBuffer(Sheets.solidBlockSheet());
		TextureAtlasSprite sprite = IrrigationRenderUtil.sprite(IrrigationRenderUtil.IRON_BLOCK);
		PoseStack.Pose pose = poseStack.last();
		IrrigationRenderUtil.prism(consumer, pose, 6.5F, 4.0F, 6.5F, 9.5F, 15.0F, 9.5F, sprite, light, overlay);
		IrrigationRenderUtil.prism(consumer, pose, 7.5F, 5.0F, 3.5F, 8.5F, 6.0F, 12.5F, sprite, light, overlay);
		IrrigationRenderUtil.prism(consumer, pose, 3.5F, 5.0F, 7.5F, 12.5F, 6.0F, 8.5F, sprite, light, overlay);
		poseStack.popPose();
	}

}
