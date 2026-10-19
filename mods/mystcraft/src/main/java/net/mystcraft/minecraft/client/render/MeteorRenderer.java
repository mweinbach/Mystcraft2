package net.mystcraft.minecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.mystcraft.minecraft.entity.MeteorEntity;

/** Pinned ModelMeteor's fifteen one-unit slabs and RenderMeteor's End-portal texture/pose. */
public final class MeteorRenderer extends EntityRenderer<MeteorEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/entity/end_portal.png");
    private final ModelPart model;
    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        var mesh = new MeshDefinition();
        for (int i = 0; i < 15; i++) {
            int size = 15 - Math.abs(7 - i);
            mesh.getRoot().addOrReplaceChild("slab" + i,
                    CubeListBuilder.create().texOffs(0, 0).addBox(-size / 2, -7 + i, -size / 2, size, 1, size), PartPose.ZERO);
        }
        model = LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }
    @Override public void render(MeteorEntity meteor, float yaw, float delta, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        // ModelPart uses sixteenths; the original renders its model at unit scale, then scales by /10.
        float scale = meteor.getScale() * 1.6F;
        pose.scale(scale, scale, scale);
        pose.mulPose(Vector3f.YN.rotationDegrees(meteor.getYRot()));
        pose.mulPose(Vector3f.ZP.rotationDegrees(meteor.getXRot()));
        model.render(pose, buffers.getBuffer(RenderType.entitySolid(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(meteor, yaw, delta, pose, buffers, light);
    }
    @Override public ResourceLocation getTextureLocation(MeteorEntity entity) { return TEXTURE; }
}
