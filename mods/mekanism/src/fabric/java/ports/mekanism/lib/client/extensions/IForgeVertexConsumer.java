/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.extensions;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Vec3i;

/**
 * Forge's {@code VertexConsumer.putBulkData} overload with an alpha value, injected into {@link VertexConsumer} on
 * Fabric. Like Forge's, it keeps light and normals baked into the quad: the brighter of the baked and the given light
 * is used, and a baked normal replaces the face normal.
 */
public interface IForgeVertexConsumer {

    default void putBulkData(PoseStack.Pose pose, BakedQuad quad, float red, float green, float blue, float alpha, int packedLight, int packedOverlay,
          boolean readExistingColor) {
        VertexConsumer self = (VertexConsumer) this;
        int[] vertices = quad.getVertices();
        Vec3i faceNormal = quad.getDirection().getNormal();
        //Baked quads use the block vertex format: position, color, uv, light, normal in eight ints per vertex
        for (int start = 0; start + 8 <= vertices.length; start += 8) {
            Vector4f position = new Vector4f(Float.intBitsToFloat(vertices[start]), Float.intBitsToFloat(vertices[start + 1]),
                  Float.intBitsToFloat(vertices[start + 2]), 1);
            position.transform(pose.pose());
            float r = red, g = green, b = blue, a = alpha;
            if (readExistingColor) {
                int color = vertices[start + 3];
                r *= (color & 0xFF) / 255F;
                g *= (color >> 8 & 0xFF) / 255F;
                b *= (color >> 16 & 0xFF) / 255F;
                a *= (color >>> 24) / 255F;
            }
            Vector3f normal = bakedNormal(vertices[start + 7]);
            if (normal == null) {
                normal = new Vector3f(faceNormal.getX(), faceNormal.getY(), faceNormal.getZ());
            }
            normal.transform(pose.normal());
            self.vertex(position.x(), position.y(), position.z(), r, g, b, a, Float.intBitsToFloat(vertices[start + 4]),
                  Float.intBitsToFloat(vertices[start + 5]), packedOverlay, brighter(packedLight, vertices[start + 6]), normal.x(), normal.y(),
                  normal.z());
        }
    }

    private static Vector3f bakedNormal(int packed) {
        byte x = (byte) packed, y = (byte) (packed >> 8), z = (byte) (packed >> 16);
        if (x == 0 && y == 0 && z == 0) {
            return null;
        }
        return new Vector3f(x / 127F, y / 127F, z / 127F);
    }

    private static int brighter(int light, int baked) {
        return LightTexture.pack(Math.max(LightTexture.block(light), LightTexture.block(baked)), Math.max(LightTexture.sky(light), LightTexture.sky(baked)));
    }
}
