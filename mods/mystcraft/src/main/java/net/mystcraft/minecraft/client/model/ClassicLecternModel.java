package net.mystcraft.minecraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/** Original ModelLectern/ModelPrism wedge dimensions and atlas regions, adapted from LGPL source. */
public final class ClassicLecternModel {
    private static final float[][] POINTS = {{-8,0,-8},{8,0,-8},{8,7,-8},{-8,1,-8},
            {-8,0,8},{8,0,8},{8,7,8},{-8,1,8}};
    private static final int[][] FACES = {{6,5,1,2},{3,0,4,7},{1,5,4,0},{6,2,3,7},{3,2,1,0},{4,5,6,7}};
    private static final int[][] UV = {{32,0,39,16},{39,0,40,16},{0,0,16,16},{16,0,32,16},{0,16,16,23},{16,16,32,23}};
    private final ModelPart lip;
    public ClassicLecternModel() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("lip", CubeListBuilder.create().texOffs(32, 2)
                .addBox(-8, 0, -7, 1, 2, 14), PartPose.ZERO);
        lip = LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }
    public void render(PoseStack poses, VertexConsumer vertices, int light, int overlay) {
        for (int face = 0; face < 6; face++) {
            float[] a = POINTS[FACES[face][0]], b = POINTS[FACES[face][1]], c = POINTS[FACES[face][2]];
            Vector3f first = new Vector3f(a[0] - b[0], a[1] - b[1], a[2] - b[2]);
            Vector3f normal = new Vector3f(c[0] - b[0], c[1] - b[1], c[2] - b[2]);
            normal.cross(first); normal.normalize();
            int[] uv = UV[face];
            for (int corner = 0; corner < 4; corner++) {
                float[] point = POINTS[FACES[face][corner]];
                float u = (corner == 0 || corner == 3 ? uv[2] : uv[0]) / 64F;
                float v = (corner < 2 ? uv[1] : uv[3]) / 32F;
                vertices.vertex(poses.last().pose(), point[0] / 16, point[1] / 16, point[2] / 16)
                        .color(1F, 1F, 1F, 1F).uv(u, v).overlayCoords(overlay).uv2(light)
                        .normal(poses.last().normal(), normal.x(), normal.y(), normal.z()).endVertex();
            }
        }
        lip.render(poses, vertices, light, overlay);
    }
}
