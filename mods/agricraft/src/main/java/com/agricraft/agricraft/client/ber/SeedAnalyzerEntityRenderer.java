package com.agricraft.agricraft.client.ber;

import com.agricraft.agricraft.common.block.entity.SeedAnalyzerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.world.item.ItemStack;
import com.mojang.math.Vector3f;

/**
 * Renderer for the agricraft crop block
 */
public class SeedAnalyzerEntityRenderer implements BlockEntityRenderer<SeedAnalyzerBlockEntity> {

	public SeedAnalyzerEntityRenderer(BlockEntityRendererProvider.Context context) {

	}

	@Override
	public void render(SeedAnalyzerBlockEntity analyzer, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
		if (analyzer.hasSeed()) {
			ItemStack seed = analyzer.getSeed();
			poseStack.pushPose();
			poseStack.translate(0.5, 0.5, 0.5);
			float angle = (float) ((720F * (System.currentTimeMillis() & 0x3FFFL) / 0x3FFFL) * Math.PI / 180F);
			poseStack.mulPose(Vector3f.YP.rotation(angle));  // Vector3f.YP, this.calculateAngle(), true
			Minecraft.getInstance().getItemRenderer().renderStatic(seed, ItemTransforms.TransformType.GROUND, packedLight, packedOverlay, poseStack, buffer, 0);
			poseStack.popPose();
		}
	}

}
