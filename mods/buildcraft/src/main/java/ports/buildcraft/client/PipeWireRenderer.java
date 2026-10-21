package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.DyeColor;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.statement.ColourText;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.wire.PipeWires;
import ports.buildcraft.transport.wire.WireBetween;
import ports.buildcraft.transport.wire.WirePart;

/**
 * Upstream {@code PipeWireRenderer}. Each wire sprite holds 16 one-pixel rows, one per signal level; wires only
 * use the off row (0) and the on row (15). Lengths run their U along the wire and skip their end caps.
 */
final class PipeWireRenderer {
    private static final double CORNER = 4.51 / 16;
    private static final double THIN = 1 / 32.0;

    private PipeWireRenderer() {}

    static ResourceLocation sprite(DyeColor colour) {
        return BuildCraft.id("block/wire/" + ColourText.upstreamName(colour));
    }

    static List<ResourceLocation> sprites() {
        List<ResourceLocation> sprites = new ArrayList<>();
        for (DyeColor colour : DyeColor.values()) sprites.add(sprite(colour));
        return sprites;
    }

    static void render(PipeBlockEntity pipe, PoseStack poseStack, MultiBufferSource buffers, int light) {
        PipeWires wires = pipe.wires();
        if (wires.parts().isEmpty()) return;
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        for (Map.Entry<WirePart, DyeColor> entry : wires.parts().entrySet()) {
            WirePart part = entry.getKey();
            boolean on = wires.isPowered(part);
            double[] centre = {corner(part.x), corner(part.y), corner(part.z)};
            int column = positive(part.x) * 4 + positive(part.y) * 2 + positive(part.z);
            cuboid(poseStack, consumer, RenderUtil.sprite(sprite(entry.getValue())), centre, new double[] {THIN, THIN, THIN}, null,
                    column, on, light);
        }
        for (Map.Entry<WireBetween, DyeColor> entry : wires.betweens().entrySet()) {
            WireBetween between = entry.getKey();
            boolean on = wires.isPowered(between.parts[0]);
            double[] centre = new double[3];
            double[] radius = {THIN, THIN, THIN};
            int main = between.mainAxis.ordinal();
            if (between.to == null) {
                centre[main] = 0.5;
                radius[main] = 4.01 / 16;
            } else {
                centre[main] = 0.5 + 6.505 / 16 * between.to.getAxisDirection().getStep();
                radius[main] = 2.99 / 32;
            }
            boolean ax = between.mainAxis == Direction.Axis.X;
            if (!ax) centre[0] = 0.5 + (between.xy ? CORNER : -CORNER);
            if (between.mainAxis != Direction.Axis.Y) centre[1] = 0.5 + ((ax ? between.xy : between.yz) ? CORNER : -CORNER);
            if (between.mainAxis != Direction.Axis.Z) centre[2] = 0.5 + (between.yz ? CORNER : -CORNER);
            cuboid(poseStack, consumer, RenderUtil.sprite(sprite(entry.getValue())), centre, radius, between.mainAxis, 0, on, light);
        }
    }

    private static double corner(Direction.AxisDirection direction) {
        return 0.5 + direction.getStep() * CORNER;
    }

    private static int positive(Direction.AxisDirection direction) {
        return direction == Direction.AxisDirection.POSITIVE ? 1 : 0;
    }

    /**
     * A wire box. With an {@code along} axis the U follows that axis in block pixels and the faces on that axis are
     * skipped; otherwise the faces take the single pixel column {@code column}. Lit wires are full bright and, like
     * upstream's unshaded lit quads, use the up normal so that directional shading does not dim them.
     */
    private static void cuboid(PoseStack poseStack, VertexConsumer consumer, TextureAtlasSprite sprite, double[] centre,
            double[] radius, @Nullable Direction.Axis along, int column, boolean on, int light) {
        float[] min = new float[3];
        float[] max = new float[3];
        for (int i = 0; i < 3; i++) {
            min[i] = (float) (centre[i] - radius[i]);
            max[i] = (float) (centre[i] + radius[i]);
        }
        int level = on ? 15 : 0;
        int packedLight = on ? RenderUtil.FULL_BRIGHT : light;
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        for (Direction face : Direction.values()) {
            if (face.getAxis() == along) continue;
            float[][] corners = corners(face, min, max);
            Direction shade = on ? Direction.UP : face;
            for (int i = 0; i < 4; i++) {
                float[] c = corners[i];
                float u;
                float v;
                if (along != null) {
                    u = c[along.ordinal()] * 16;
                    v = level + (crossIsMax(face, along, c, max) ? 1 : 0);
                } else {
                    u = column + (i >= 2 ? 1 : 0);
                    v = level + (i == 1 || i == 2 ? 1 : 0);
                }
                consumer.vertex(pose, c[0], c[1], c[2])
                        .color(255, 255, 255, 255)
                        .uv(sprite.getU(u), sprite.getV(v))
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(packedLight)
                        .normal(normal, shade.getStepX(), shade.getStepY(), shade.getStepZ())
                        .endVertex();
            }
        }
    }

    private static boolean crossIsMax(Direction face, Direction.Axis along, float[] corner, float[] max) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != along && axis != face.getAxis()) return corner[axis.ordinal()] == max[axis.ordinal()];
        }
        return false;
    }

    /** The same counter-clockwise corner order as {@link RenderUtil#face}. */
    private static float[][] corners(Direction face, float[] min, float[] max) {
        float a = min[0], b = min[1], c = min[2], d = max[0], e = max[1], f = max[2];
        return switch (face) {
            case DOWN -> new float[][] {{a, b, f}, {a, b, c}, {d, b, c}, {d, b, f}};
            case UP -> new float[][] {{a, e, c}, {a, e, f}, {d, e, f}, {d, e, c}};
            case NORTH -> new float[][] {{d, e, c}, {d, b, c}, {a, b, c}, {a, e, c}};
            case SOUTH -> new float[][] {{a, e, f}, {a, b, f}, {d, b, f}, {d, e, f}};
            case WEST -> new float[][] {{a, e, c}, {a, b, c}, {a, b, f}, {a, e, f}};
            case EAST -> new float[][] {{d, e, f}, {d, b, f}, {d, b, c}, {d, e, c}};
        };
    }
}
