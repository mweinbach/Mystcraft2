package com.agricraft.agricraft.client.ber;

import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationTankBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Draws the water surface of a tank, ported from AgriCraft 3's {@code TileEntityIrrigationTankRenderer} (MIT).
 */
public class IrrigationTankRenderer implements BlockEntityRenderer<IrrigationTankBlockEntity> {

	public IrrigationTankRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(IrrigationTankBlockEntity tank, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
		if (tank.getContent() <= 0) {
			return;
		}
		float level = tank.getRenderLevel(partialTicks) - tank.getBlockPos().getY();
		// only the block that contains the surface draws it
		if (level <= 0 || level > 1) {
			return;
		}
		IrrigationRenderUtil.faceUp(buffer.getBuffer(Sheets.translucentCullBlockSheet()), poseStack.last(), 0, 0, 16, 16, 16 * level,
				IrrigationRenderUtil.sprite(IrrigationRenderUtil.WATER_STILL), IrrigationRenderUtil.waterColor(tank.getLevel(), tank.getBlockPos()),
				1.0F, light, overlay);
	}

}
