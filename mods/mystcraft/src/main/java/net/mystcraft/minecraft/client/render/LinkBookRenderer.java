package net.mystcraft.minecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.mystcraft.minecraft.entity.LinkBookEntity;

/** Classic RenderLinkbook pose and genuine classic book texture on the 3D book mesh. */
public final class LinkBookRenderer extends EntityRenderer<LinkBookEntity> {
    private final ClassicBookVisuals book = new ClassicBookVisuals();
    public LinkBookRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.2F;
    }
    @Override public void render(LinkBookEntity entity, float yaw, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, 0.0625, 0);
        pose.mulPose(Vector3f.YN.rotationDegrees(yaw + 90));
        pose.mulPose(Vector3f.ZP.rotationDegrees(90));
        pose.scale(0.8F, 0.8F, 0.8F);
        book.render(entity.getBook(), pose, buffers, light,
                entity.isVisuallyHurt() ? OverlayTexture.pack(0, true) : OverlayTexture.NO_OVERLAY, 1.2F);
        pose.popPose();
        ClassicBookLabels.render(entity.getBook(), entity.position(), 0, 0, 0, pose, buffers, light);
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }
    @Override public ResourceLocation getTextureLocation(LinkBookEntity entity) {
        return ClassicBookVisuals.texture(entity.getBook());
    }
}
