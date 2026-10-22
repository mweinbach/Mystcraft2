/*
 * Mekanism 1.19.2 port - Fabric client rendering for logistical transporters.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.math.Vector3f;
import java.nio.FloatBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Fabric draws logistical-transporter stacks on a depth-tested cutout layer. The cutout shell is already in the
 * depth buffer before block entities, and the colored glass is not drawn until afterwards, so flushing earlier
 * cannot uncover a sprite the shell has already rejected. The sprite is moved along the view to just outside that
 * shell. The sprite is moved just past the near face of its block, so the side wall and the end ring are
 * behind it, while a solid block closer to the camera still wins the depth test. Forge does not use this
 * class and keeps the upstream item renderer.
 */
public final class TransporterItems extends RenderType {

    private static final double PAST_FACE = 0.35D;

    /**
     * Ground ingots are one plane. The entity-translucent sheet culls it, so the sprite is drawn cutout with no
     * face culling. Depth testing stays on.
     */
    public static final RenderType LAYER = create("mek_transporter_item", DefaultVertexFormat.NEW_ENTITY, Mode.QUADS, 256, true, false,
          RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                .setTextureState(BLOCK_SHEET)
                .setTransparencyState(NO_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .createCompositeState(true));

    private TransporterItems() {
        super("mek_transporter_item_unused", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false, () -> {
        }, () -> {
        });
    }

    public static void faceCamera(ItemEntity entity) {
        // bobOffs is final and random, which turns the ground plane edge-on. Cancel it so the face stays along Z.
        entity.age = Math.round(-entity.bobOffs * 20.0F);
    }

    /**
     * {@code x,y,z} are the stack position in the block. The pose is the camera-relative block origin.
     */
    public static void translateClearOfShell(PoseStack pose, double x, double y, double z) {
        FloatBuffer stored = FloatBuffer.allocate(16);
        pose.last().pose().store(stored);
        // The pose is the camera pitch. Its translation is the block origin in view space, and translate()
        // takes a world-space offset. Turn the view-space direction to the camera back into world space.
        double m00 = stored.get(0);
        double m01 = stored.get(4);
        double m02 = stored.get(8);
        double m10 = stored.get(1);
        double m11 = stored.get(5);
        double m12 = stored.get(9);
        double m20 = stored.get(2);
        double m21 = stored.get(6);
        double m22 = stored.get(10);
        double viewX = -(m00 * x + m01 * y + m02 * z + stored.get(12));
        double viewY = -(m10 * x + m11 * y + m12 * z + stored.get(13));
        double viewZ = -(m20 * x + m21 * y + m22 * z + stored.get(14));
        double worldX = m00 * viewX + m10 * viewY + m20 * viewZ;
        double worldY = m01 * viewX + m11 * viewY + m21 * viewZ;
        double worldZ = m02 * viewX + m12 * viewY + m22 * viewZ;
        double length = Math.sqrt(worldX * worldX + worldY * worldY + worldZ * worldZ);
        if (length < 1.0E-4D) {
            pose.translate(x, y, z);
            return;
        }
        double dx = worldX / length;
        double dy = worldY / length;
        double dz = worldZ / length;
        // Step just past the near face of this block. That clears both the side wall and an end ring, and
        // still leaves the sprite behind a full block placed between the camera and the tube.
        double distance = firstPositive(face(x, dx), face(y, dy), face(z, dz)) + PAST_FACE;
        if (!(distance > PAST_FACE) || distance > 1.5D) {
            pose.translate(x, y, z);
            return;
        }
        pose.translate(x + dx * distance, y + dy * distance, z + dz * distance);
        // The ground plane is vertical. Tilt it to face the camera so the whole sprite shares one depth
        // and the near block face cannot clip the lower edge.
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        pose.mulPose(Vector3f.XP.rotationDegrees(-camera.getXRot()));
        pose.mulPose(Vector3f.YP.rotationDegrees(180.0F - camera.getYRot()));
    }

    public static MultiBufferSource cutout(MultiBufferSource buffer) {
        return type -> {
            if (type == Sheets.translucentCullBlockSheet() || type == Sheets.translucentItemSheet()) {
                return buffer.getBuffer(LAYER);
            }
            return buffer.getBuffer(type);
        };
    }

    public static int fullBright() {
        return LightTexture.FULL_BRIGHT;
    }

    public static void flush(MultiBufferSource buffer) {
        if (!Minecraft.useShaderTransparency() && buffer instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(LAYER);
        }
    }

    private static double face(double position, double direction) {
        if (Math.abs(direction) < 1.0E-6D) {
            return Double.POSITIVE_INFINITY;
        }
        double bound = direction > 0.0D ? 1.0D : 0.0D;
        double t = (bound - position) / direction;
        return t > 0.0D ? t : Double.POSITIVE_INFINITY;
    }

    private static double firstPositive(double a, double b, double c) {
        return Math.min(a, Math.min(b, c));
    }
}
