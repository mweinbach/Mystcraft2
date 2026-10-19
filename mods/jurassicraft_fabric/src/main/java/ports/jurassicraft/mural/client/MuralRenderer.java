package ports.jurassicraft.mural.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.mural.MuralEntity;

public final class MuralRenderer extends EntityRenderer<MuralEntity> {
    public MuralRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(MuralEntity mural) {
        return new ResourceLocation(PortMod.MOD_ID, "textures/murals/" + mural.artwork().id() + ".png");
    }
    @Override public void render(MuralEntity mural, float yaw, float tick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Vector3f.YP.rotationDegrees(180 - yaw));
        float half = mural.getWidth() / 32F, bottom = -mural.getHeight() / 16F;
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(mural)));
        // Two faces, as in the original, keep the artwork visible from either side.
        quad(pose, consumer, half, bottom, -.03125F, light);
        quad(pose, consumer, half, bottom, .03125F, light);
        pose.popPose();
        super.render(mural, yaw, tick, pose, buffers, light);
    }
    private static void quad(PoseStack pose, VertexConsumer consumer, float half, float bottom, float z, int light) {
        vertex(pose, consumer, -half, bottom, z, 1, 1, light);
        vertex(pose, consumer, half, bottom, z, 0, 1, light);
        vertex(pose, consumer, half, 0, z, 0, 0, light);
        vertex(pose, consumer, -half, 0, z, 1, 0, light);
    }
    private static void vertex(PoseStack pose, VertexConsumer c, float x, float y, float z, float u, float v, int light) {
        c.vertex(pose.last().pose(), x, y, z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.last().normal(), 0, 0, -1).endVertex();
    }
}
