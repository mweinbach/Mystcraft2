package ports.jurassicraft.decor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.decor.ParkSignEntity;

public final class ParkSignRenderer extends EntityRenderer<ParkSignEntity> {
    public ParkSignRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(ParkSignEntity sign) {
        return new ResourceLocation(PortMod.MOD_ID, "textures/" + (sign.attraction() ? "attraction_sign/" : "paddock/") + sign.artwork() + ".png");
    }
    @Override public boolean shouldRender(ParkSignEntity sign, Frustum frustum, double x, double y, double z) {
        if (!sign.attraction()) return super.shouldRender(sign, frustum, x, y, z);
        // The original attraction image continues four blocks below its support box.
        return sign.shouldRender(x, y, z) && frustum.isVisible(sign.getBoundingBox().expandTowards(0, -4, 0).inflate(.5));
    }
    @Override public void render(ParkSignEntity sign, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Vector3f.YP.rotationDegrees(180.0F - yaw));
        float half = sign.attraction() ? 4 : .5F;
        float bottom = sign.attraction() ? -8 : -.5F;
        float top = sign.attraction() ? 0 : .5F;
        float back = sign.attraction() ? -.0625F : -.015625F;
        float front = sign.attraction() ? .03125F : .015625F;
        layer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(sign))), half, bottom, top, back, front, light);
        if (sign.attraction()) {
            ResourceLocation popout = new ResourceLocation(PortMod.MOD_ID, "textures/attraction_sign/" + sign.artwork() + "_popout.png");
            layer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(popout)), half, bottom, top, back - .001F, front + .001F, light);
        }
        pose.popPose();
        super.render(sign, yaw, partialTicks, pose, buffers, light);
    }
    private static void layer(PoseStack pose, VertexConsumer out, float half, float bottom, float top, float back, float front, int light) {
        face(pose, out, half, bottom, top, back, light);
        face(pose, out, half, bottom, top, front, light);
        // Pixel-wide slices extrude the transparent silhouette, as the original models do.
        for (int i = 0; i <= 128; i++) {
            float t = i / 128.0F, x = -half + 2 * half * t, y = bottom + (top - bottom) * t;
            float u = 1 - t, v = 1 - t;
            vertex(pose, out, x, bottom, back, u, 1, light);
            vertex(pose, out, x, bottom, front, u, 1, light);
            vertex(pose, out, x, top, front, u, 0, light);
            vertex(pose, out, x, top, back, u, 0, light);
            vertex(pose, out, -half, y, back, 1, v, light);
            vertex(pose, out, -half, y, front, 1, v, light);
            vertex(pose, out, half, y, front, 0, v, light);
            vertex(pose, out, half, y, back, 0, v, light);
        }
    }
    private static void face(PoseStack pose, VertexConsumer consumer, float half, float bottom, float top, float z, int light) {
        vertex(pose, consumer, -half, bottom, z, 1, 1, light);
        vertex(pose, consumer, half, bottom, z, 0, 1, light);
        vertex(pose, consumer, half, top, z, 0, 0, light);
        vertex(pose, consumer, -half, top, z, 1, 0, light);
    }
    private static void vertex(PoseStack pose, VertexConsumer consumer, float x, float y, float z, float u, float v, int light) {
        consumer.vertex(pose.last().pose(), x, y, z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.last().normal(), 0, 0, -1).endVertex();
    }
}
