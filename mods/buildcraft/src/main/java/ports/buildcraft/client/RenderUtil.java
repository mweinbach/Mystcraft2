package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/** Box drawing in block-model pixel units (0 to 16), with the face vertex order and UVs of vanilla models. */
public final class RenderUtil {
    public static final int FULL_BRIGHT = 0xF000F0;

    @FunctionalInterface
    public interface FaceUv {
        /** UV rectangle {u1, v1, u2, v2} in sprite pixels for {@code face}, or null to skip the face. */
        @Nullable float[] uv(Direction face, float x1, float y1, float z1, float x2, float y2, float z2);
    }

    /** Vanilla's default UVs: each face projects the box onto the sprite. */
    public static final FaceUv AUTO = RenderUtil::autoUv;

    private RenderUtil() {}

    public static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    public static float[] autoUv(Direction face, float x1, float y1, float z1, float x2, float y2, float z2) {
        return switch (face) {
            case DOWN -> new float[] {x1, 16 - z2, x2, 16 - z1};
            case UP -> new float[] {x1, z1, x2, z2};
            case NORTH -> new float[] {16 - x2, 16 - y2, 16 - x1, 16 - y1};
            case SOUTH -> new float[] {x1, 16 - y2, x2, 16 - y1};
            case WEST -> new float[] {z1, 16 - y2, z2, 16 - y1};
            case EAST -> new float[] {16 - z2, 16 - y2, 16 - z1, 16 - y1};
        };
    }

    public static void box(PoseStack poseStack, VertexConsumer consumer, TextureAtlasSprite sprite, FaceUv uvs,
            float x1, float y1, float z1, float x2, float y2, float z2, int argb, int light) {
        if (x2 <= x1 || y2 <= y1 || z2 <= z1) return;
        for (Direction face : Direction.values()) {
            float[] uv = uvs.uv(face, x1, y1, z1, x2, y2, z2);
            if (uv != null) face(poseStack, consumer, sprite, face, uv, x1, y1, z1, x2, y2, z2, argb, light);
        }
    }

    public static void face(PoseStack poseStack, VertexConsumer consumer, TextureAtlasSprite sprite, Direction face, float[] uv,
            float x1, float y1, float z1, float x2, float y2, float z2, int argb, int light) {
        float a = x1 / 16, b = y1 / 16, c = z1 / 16, d = x2 / 16, e = y2 / 16, f = z2 / 16;
        float[][] corners = switch (face) {
            case DOWN -> new float[][] {{a, b, f}, {a, b, c}, {d, b, c}, {d, b, f}};
            case UP -> new float[][] {{a, e, c}, {a, e, f}, {d, e, f}, {d, e, c}};
            case NORTH -> new float[][] {{d, e, c}, {d, b, c}, {a, b, c}, {a, e, c}};
            case SOUTH -> new float[][] {{a, e, f}, {a, b, f}, {d, b, f}, {d, e, f}};
            case WEST -> new float[][] {{a, e, c}, {a, b, c}, {a, b, f}, {a, e, f}};
            case EAST -> new float[][] {{d, e, f}, {d, b, f}, {d, b, c}, {d, e, c}};
        };
        float u1 = sprite.getU(uv[0]), v1 = sprite.getV(uv[1]), u2 = sprite.getU(uv[2]), v2 = sprite.getV(uv[3]);
        float[][] uvCorners = {{u1, v1}, {u1, v2}, {u2, v2}, {u2, v1}};
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        Vector3f n = face.step();
        int alpha = argb >>> 24, red = argb >> 16 & 0xFF, green = argb >> 8 & 0xFF, blue = argb & 0xFF;
        for (int i = 0; i < 4; i++) {
            consumer.vertex(pose, corners[i][0], corners[i][1], corners[i][2])
                    .color(red, green, blue, alpha)
                    .uv(uvCorners[i][0], uvCorners[i][1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(normal, n.x(), n.y(), n.z())
                    .endVertex();
        }
    }

    /** Rotates the pose about the block centre so that a model built facing up faces {@code facing}. */
    public static void rotateFromUp(PoseStack poseStack, Direction facing) {
        if (facing == Direction.UP) return;
        poseStack.translate(0.5, 0.5, 0.5);
        switch (facing) {
            case DOWN -> poseStack.mulPose(Vector3f.XP.rotationDegrees(180));
            case NORTH -> poseStack.mulPose(Vector3f.XP.rotationDegrees(-90));
            case SOUTH -> poseStack.mulPose(Vector3f.XP.rotationDegrees(90));
            case EAST -> poseStack.mulPose(Vector3f.ZP.rotationDegrees(-90));
            case WEST -> poseStack.mulPose(Vector3f.ZP.rotationDegrees(90));
            default -> {}
        }
        poseStack.translate(-0.5, -0.5, -0.5);
    }

    /**
     * Rotates the pose about the block centre so that a model built facing west faces the horizontal
     * {@code facing}, as the blockstate rotations west 0, north 90, east 180 and south 270 do.
     */
    public static void rotateFromWest(PoseStack poseStack, Direction facing) {
        int yRot = Math.floorMod(facing.get2DDataValue() - 1, 4) * 90;
        if (yRot == 0) return;
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Vector3f.YP.rotationDegrees(-yRot));
        poseStack.translate(-0.5, -0.5, -0.5);
    }

    /**
     * An axis-aligned beam between the centres of its end faces, in block units. The sprite row
     * starting at {@code vMin} tiles once per block along the beam, as upstream laser rows do; the end
     * faces use {@code endUv}, or the middle of that row when null.
     */
    public static void beam(PoseStack poseStack, VertexConsumer consumer, TextureAtlasSprite sprite, int light, double radius, float vMin, @Nullable float[] endUv,
            double x1, double y1, double z1, double x2, double y2, double z2) {
        double ax = Math.min(x1, x2) - (x1 == x2 ? radius : 0), bx = Math.max(x1, x2) + (x1 == x2 ? radius : 0);
        double ay = Math.min(y1, y2) - (y1 == y2 ? radius : 0), by = Math.max(y1, y2) + (y1 == y2 ? radius : 0);
        double az = Math.min(z1, z2) - (z1 == z2 ? radius : 0), bz = Math.max(z1, z2) + (z1 == z2 ? radius : 0);
        Direction.Axis axis = x1 != x2 ? Direction.Axis.X : y1 != y2 ? Direction.Axis.Y : Direction.Axis.Z;
        double start = axis.choose(ax, ay, az);
        double end = axis.choose(bx, by, bz);
        float thickness = (float) (radius * 32);
        for (double from = start; from < end - 1e-4; from = Math.min(end, Math.floor(from + 1))) {
            double to = Math.min(end, Math.floor(from) + 1);
            double[] lo = {ax, ay, az};
            double[] hi = {bx, by, bz};
            lo[axis.ordinal()] = from;
            hi[axis.ordinal()] = to;
            float length = (float) (to - from) * 16;
            for (Direction face : Direction.values()) {
                if (face.getAxis() == axis && (face.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? from > start : to < end)) continue;
                float[] uv = face.getAxis() != axis ? new float[] {0, vMin, length, vMin + thickness}
                        : endUv != null ? endUv : new float[] {8 - thickness / 2, vMin, 8 + thickness / 2, vMin + thickness};
                RenderUtil.face(poseStack, consumer, sprite, face, uv, (float) lo[0] * 16, (float) lo[1] * 16, (float) lo[2] * 16,
                        (float) hi[0] * 16, (float) hi[1] * 16, (float) hi[2] * 16, 0xFFFFFFFF, light);
            }
        }
    }
}
