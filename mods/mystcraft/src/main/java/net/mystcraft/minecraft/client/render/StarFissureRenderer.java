package net.mystcraft.minecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.mystcraft.minecraft.fissure.StarFissureBlockEntity;

/** Modern end-portal projection shader replaces the original fixed-function texture projection. */
public final class StarFissureRenderer implements BlockEntityRenderer<StarFissureBlockEntity> {
    public StarFissureRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(StarFissureBlockEntity fissure, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        Matrix4f matrix = pose.last().pose();
        VertexConsumer vertices = buffers.getBuffer(RenderType.endPortal());
        vertices.vertex(matrix, 0, .1F, 0).endVertex();
        vertices.vertex(matrix, 0, .1F, 1).endVertex();
        vertices.vertex(matrix, 1, .1F, 1).endVertex();
        vertices.vertex(matrix, 1, .1F, 0).endVertex();
    }
}
