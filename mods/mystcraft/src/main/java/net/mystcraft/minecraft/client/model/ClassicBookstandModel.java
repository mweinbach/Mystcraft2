package net.mystcraft.minecraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Original four-part Techne geometry, adapted from Mystcraft-Legacy ModelBookstand.
 * XCompWiz and contributors, LGPL-3.0; see NOTICE.md for the pinned source revision.
 */
public final class ClassicBookstandModel {
    private final ModelPart root = createLayer().bakeRoot();
    private static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(4, 8).mirror()
                        .addBox(-0.5F, -0.5F, -1.5F, 6, 1, 3),
                PartPose.offsetAndRotation(0.25F, 0, -0.25F, 0.5235988F, 0, -0.2617994F));
        root.addOrReplaceChild("post", CubeListBuilder.create().texOffs(0, 8).mirror()
                        .addBox(-0.5F, 0, -0.5F, 1, 6, 1), PartPose.ZERO);
        root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).mirror()
                        .addBox(-2.5F, 0, -2.5F, 5, 3, 5), PartPose.offset(0, 5, 0));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(4, 8)
                        .addBox(-0.5F, -0.5F, -1.5F, 6, 1, 3),
                PartPose.offsetAndRotation(-0.25F, 0, -0.25F, -0.5235988F, 3.141593F, 0.2617994F));
        return LayerDefinition.create(mesh, 64, 32);
    }
    public void render(PoseStack pose, VertexConsumer vertices, int light, int overlay) {
        root.render(pose, vertices, light, overlay);
    }
}
