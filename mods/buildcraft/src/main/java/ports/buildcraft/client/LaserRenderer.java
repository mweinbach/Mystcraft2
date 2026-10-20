package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.lib.Box;

/**
 * Upstream {@code LaserRenderer_BC8}, {@code CompiledLaserType} and {@code LaserBoxRenderer}: a square
 * laser between two points, built along +X in texture pixels from a laser type's rows, then scaled
 * and turned onto the line. Each vertex takes the light of the block it is in.
 */
public final class LaserRenderer {
    /** Upstream {@code LaserRow}: a strip of a laser sprite, in pixels, and the sides it may be drawn on. */
    public record Row(ResourceLocation sprite, int uMin, int vMin, int uMax, int vMax, Side... sides) {
        int width() { return uMax - uMin; }
        int height() { return vMax - vMin; }

        Row withSprite(ResourceLocation replacement) {
            return new Row(replacement, uMin, vMin, uMax, vMax, sides);
        }

        boolean validFor(Side side) {
            if (sides.length == 0) return true;
            for (Side valid : sides) {
                if (valid == side) return true;
            }
            return false;
        }
    }

    /** Upstream {@code LaserSide}: TOP is +Y, BOTTOM -Y, LEFT -Z and RIGHT +Z of the laser's own frame. */
    public enum Side { TOP, BOTTOM, LEFT, RIGHT }

    /** Upstream {@code LaserType}: caps, start and end rows, and middle rows cycled along the laser. */
    public record Type(Row capStart, Row start, Row[] middle, Row end, Row capEnd) {
        Type withSprite(ResourceLocation replacement) {
            Row[] rows = new Row[middle.length];
            for (int i = 0; i < rows.length; i++) rows[i] = middle[i].withSprite(replacement);
            return new Type(capStart.withSprite(replacement), start.withSprite(replacement), rows, end.withSprite(replacement), capEnd.withSprite(replacement));
        }
    }

    public static final ResourceLocation MARKER_VOLUME_CONNECTED_SPRITE = BuildCraft.id("lasers/marker_volume_connected");
    public static final ResourceLocation MARKER_VOLUME_POSSIBLE_SPRITE = BuildCraft.id("lasers/marker_volume_possible");
    public static final ResourceLocation MARKER_VOLUME_SIGNAL_SPRITE = BuildCraft.id("lasers/marker_volume_signal");
    public static final ResourceLocation MARKER_PATH_CONNECTED_SPRITE = BuildCraft.id("lasers/marker_path_connected");
    public static final ResourceLocation MARKER_PATH_POSSIBLE_SPRITE = BuildCraft.id("lasers/marker_path_possible");
    public static final ResourceLocation MARKER_DEFAULT_POSSIBLE_SPRITE = BuildCraft.id("lasers/marker_default_possible");
    public static final ResourceLocation STRIPES_READ_SPRITE = BuildCraft.id("lasers/stripes_read");
    public static final ResourceLocation STRIPES_WRITE_SPRITE = BuildCraft.id("lasers/stripes_write");

    /** Upstream {@code BuildCraftLaserManager}'s marker lasers. */
    public static final Type MARKER_VOLUME_CONNECTED;
    public static final Type MARKER_VOLUME_POSSIBLE;
    public static final Type MARKER_VOLUME_SIGNAL;
    public static final Type MARKER_PATH_CONNECTED;
    public static final Type MARKER_PATH_POSSIBLE;
    public static final Type MARKER_DEFAULT_POSSIBLE;
    /** Volume boxes that machines read from or write to. */
    public static final Type STRIPES_READ;
    public static final Type STRIPES_WRITE;

    static {
        ResourceLocation sprite = MARKER_VOLUME_CONNECTED_SPRITE;
        Row[] middle = new Row[6];
        for (int i = 0; i < middle.length; i++) middle[i] = new Row(sprite, 0, 2 + 2 * i, 16, 4 + 2 * i);
        MARKER_VOLUME_CONNECTED = new Type(new Row(sprite, 0, 0, 2, 2), new Row(sprite, 0, 0, 16, 2), middle,
                new Row(sprite, 0, 14, 16, 16), new Row(sprite, 14, 14, 16, 16));

        sprite = MARKER_PATH_CONNECTED_SPRITE;
        MARKER_PATH_CONNECTED = new Type(new Row(sprite, 0, 0, 3, 3), new Row(sprite, 0, 0, 16, 3), new Row[] {
                new Row(sprite, 0, 4, 16, 7, Side.TOP, Side.BOTTOM), new Row(sprite, 0, 8, 16, 11, Side.LEFT, Side.RIGHT)},
                new Row(sprite, 0, 12, 16, 15), new Row(sprite, 13, 12, 16, 15));

        sprite = MARKER_VOLUME_POSSIBLE_SPRITE;
        middle = new Row[14];
        for (int i = 0; i < middle.length; i++) middle[i] = new Row(sprite, 0, 1 + i, 16, 2 + i);
        MARKER_VOLUME_POSSIBLE = new Type(new Row(sprite, 0, 0, 1, 1), new Row(sprite, 0, 0, 16, 1), middle,
                new Row(sprite, 0, 15, 16, 16), new Row(sprite, 15, 15, 16, 16));

        MARKER_VOLUME_SIGNAL = MARKER_VOLUME_CONNECTED.withSprite(MARKER_VOLUME_SIGNAL_SPRITE);
        MARKER_PATH_POSSIBLE = MARKER_VOLUME_POSSIBLE.withSprite(MARKER_PATH_POSSIBLE_SPRITE);
        MARKER_DEFAULT_POSSIBLE = MARKER_VOLUME_POSSIBLE.withSprite(MARKER_DEFAULT_POSSIBLE_SPRITE);
        STRIPES_READ = MARKER_VOLUME_CONNECTED.withSprite(STRIPES_READ_SPRITE);
        STRIPES_WRITE = MARKER_VOLUME_CONNECTED.withSprite(STRIPES_WRITE_SPRITE);
    }

    private LaserRenderer() {}

    public static List<ResourceLocation> sprites() {
        return List.of(MARKER_VOLUME_CONNECTED_SPRITE, MARKER_VOLUME_POSSIBLE_SPRITE, MARKER_VOLUME_SIGNAL_SPRITE, MARKER_PATH_CONNECTED_SPRITE,
                MARKER_PATH_POSSIBLE_SPRITE, MARKER_DEFAULT_POSSIBLE_SPRITE, STRIPES_READ_SPRITE, STRIPES_WRITE_SPRITE);
    }

    /** Upstream {@code LaserBoxRenderer.renderLaserBoxStatic}: the twelve edges of the box, through block centres when {@code center}. */
    public static void box(Level level, BlockPos origin, PoseStack poseStack, VertexConsumer consumer, Box box, Type type, boolean center) {
        if (!box.isInitialized()) return;
        BlockPos size = box.size();
        boolean renderX = !center || size.getX() > 1;
        boolean renderY = !center || size.getY() > 1;
        boolean renderZ = !center || size.getZ() > 1;
        Vec3 min = Vec3.atLowerCornerOf(box.min()).add(center ? 0.5 : 0, center ? 0.5 : 0, center ? 0.5 : 0);
        Vec3 max = Vec3.atLowerCornerOf(box.max()).add(center ? 0.5 : 1, center ? 0.5 : 1, center ? 0.5 : 1);
        double[] xs = {min.x, max.x}, ys = {min.y, max.y}, zs = {min.z, max.z};
        List<Vec3[]> edges = new ArrayList<>();
        if (renderX) {
            edges.add(edge(xs, ys, zs, Direction.Axis.X, 0, 0));
            if (renderY) {
                edges.add(edge(xs, ys, zs, Direction.Axis.X, 1, 0));
                if (renderZ) edges.add(edge(xs, ys, zs, Direction.Axis.X, 1, 1));
            }
            if (renderZ) edges.add(edge(xs, ys, zs, Direction.Axis.X, 0, 1));
        }
        if (renderY) {
            edges.add(edge(xs, ys, zs, Direction.Axis.Y, 0, 0));
            if (renderX) {
                edges.add(edge(xs, ys, zs, Direction.Axis.Y, 1, 0));
                if (renderZ) edges.add(edge(xs, ys, zs, Direction.Axis.Y, 1, 1));
            }
            if (renderZ) edges.add(edge(xs, ys, zs, Direction.Axis.Y, 0, 1));
        }
        if (renderZ) {
            edges.add(edge(xs, ys, zs, Direction.Axis.Z, 0, 0));
            if (renderX) {
                edges.add(edge(xs, ys, zs, Direction.Axis.Z, 1, 0));
                if (renderY) edges.add(edge(xs, ys, zs, Direction.Axis.Z, 1, 1));
            }
            if (renderY) edges.add(edge(xs, ys, zs, Direction.Axis.Z, 0, 1));
        }
        for (Vec3[] edge : edges) render(level, origin, poseStack, consumer, type, edge[0], edge[1], 1 / 16.05);
    }

    /** One box edge along {@code axis}; {@code a} and {@code b} pick the min or max of the other two axes in x, y, z order. */
    private static Vec3[] edge(double[] xs, double[] ys, double[] zs, Direction.Axis axis, int a, int b) {
        return switch (axis) {
            case X -> axisLaser(new Vec3(xs[0], ys[a], zs[b]), new Vec3(xs[1], ys[a], zs[b]), axis);
            case Y -> axisLaser(new Vec3(xs[a], ys[0], zs[b]), new Vec3(xs[a], ys[1], zs[b]), axis);
            case Z -> axisLaser(new Vec3(xs[a], ys[b], zs[0]), new Vec3(xs[a], ys[b], zs[1]), axis);
        };
    }

    /**
     * Upstream {@code makeLaser}: an axis-aligned laser whose first point moves a pixel along the positive
     * axis and whose second moves a pixel back.
     */
    public static Vec3[] axisLaser(Vec3 first, Vec3 second, Direction.Axis axis) {
        Vec3 pixel = Vec3.atLowerCornerOf(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE).getNormal()).scale(1 / 16.0);
        return new Vec3[] {first.add(pixel), second.subtract(pixel)};
    }

    /** Draws a laser from {@code start} to {@code end}, in world coordinates, relative to the block entity at {@code origin}. */
    public static void render(Level level, BlockPos origin, PoseStack poseStack, VertexConsumer consumer, Type type, Vec3 start, Vec3 end, double scale) {
        Context context = new Context(level, origin, poseStack, consumer, type, start, end, scale);
        context.bake();
    }

    /** Upstream {@code LaserContext} and the baking of {@code CompiledLaserType}. */
    private static final class Context {
        private final Level level;
        private final BlockPos origin;
        private final Matrix4f pose;
        private final Matrix3f normalMatrix;
        private final VertexConsumer consumer;
        private final Type type;
        private final Vec3 start;
        private final double scale;
        private final double length;
        private final double cosY, sinY, cosZ, sinZ;
        private Row row;
        private float nx, ny, nz;

        Context(Level level, BlockPos origin, PoseStack poseStack, VertexConsumer consumer, Type type, Vec3 start, Vec3 end, double scale) {
            this.level = level;
            this.origin = origin;
            this.pose = poseStack.last().pose();
            this.normalMatrix = poseStack.last().normal();
            this.consumer = consumer;
            this.type = type;
            this.start = start;
            this.scale = scale;
            Vec3 delta = start.subtract(end);
            double dx = delta.x, dy = delta.y, dz = delta.z;
            double realLength = delta.length();
            length = realLength / scale;
            double angleZ = Math.PI - Math.atan2(dz, dx);
            double angleY;
            if (dx == 0 && dz == 0) {
                angleY = dy < 0 ? Math.PI / 2 : -Math.PI / 2;
            } else {
                angleY = -Math.atan2(dy, Math.sqrt(realLength * realLength - dy * dy));
            }
            cosY = Math.cos(angleZ);
            sinY = Math.sin(angleZ);
            cosZ = Math.cos(angleY);
            sinZ = Math.sin(angleY);
        }

        /** Upstream's laser frame: rotate about Z by the pitch, then about Y by the yaw, scale, then move to the start. */
        private Vec3 transform(double x, double y, double z) {
            double rx = x * cosZ - y * sinZ;
            double ry = x * sinZ + y * cosZ;
            double fx = rx * cosY + z * sinY;
            double fz = -rx * sinY + z * cosY;
            return new Vec3(fx, ry, fz);
        }

        void bake() {
            double startWidth = type.start().width();
            double endWidth = type.end().width();
            int middleWidth = type.middle()[0].width();
            bakeCap(type.capStart(), false);
            bakeCap(type.capEnd(), true);
            double lengthForMiddle = Math.max(0, length - startWidth - endWidth);
            int numMiddle = (int) Math.floor(lengthForMiddle / middleWidth);
            if (lengthForMiddle - middleWidth * numMiddle > 0) numMiddle++;
            double lengthEnds = length - middleWidth * numMiddle;
            double ratioStartEnd = startWidth / endWidth;
            double startLength = lengthEnds / 2 * ratioStartEnd;
            double endLength = lengthEnds / 2 / ratioStartEnd;
            if (startLength > 0) bakeSides(type.start(), 0, startLength, 1 - startLength / startWidth, 1);
            if (endLength > 0) bakeSides(type.end(), length - endLength, length, 0, endLength / endWidth);
            if (numMiddle > 0) {
                for (Side side : Side.values()) {
                    List<Row> rows = new ArrayList<>();
                    for (Row middle : type.middle()) {
                        if (middle.validFor(side)) rows.add(middle);
                    }
                    double x = startLength;
                    for (int i = 0; i < numMiddle; i++) {
                        row = rows.get(i % rows.size());
                        bakeSide(side, x, x + middleWidth, 0, 1);
                        x += middleWidth;
                    }
                }
            }
        }

        private void bakeCap(Row cap, boolean end) {
            row = cap;
            double h = cap.height() / 2.0;
            if (end) {
                normal(1, 0, 0);
                point(length, -h, h, 0, 1);
                point(length, -h, -h, 0, 0);
                point(length, h, -h, 1, 0);
                point(length, h, h, 1, 1);
            } else {
                normal(-1, 0, 0);
                point(0, h, h, 1, 1);
                point(0, h, -h, 1, 0);
                point(0, -h, -h, 0, 0);
                point(0, -h, h, 0, 1);
            }
        }

        private void bakeSides(Row strip, double from, double to, double uFrom, double uTo) {
            row = strip;
            for (Side side : Side.values()) bakeSide(side, from, to, uFrom, uTo);
        }

        private void bakeSide(Side side, double ls, double lb, double uFrom, double uTo) {
            double h = row.height() / 2.0;
            switch (side) {
                case TOP -> {
                    normal(0, 1, 0);
                    point(ls, h, -h, uFrom, 0);
                    point(ls, h, h, uFrom, 1);
                    point(lb, h, h, uTo, 1);
                    point(lb, h, -h, uTo, 0);
                }
                case BOTTOM -> {
                    normal(0, -1, 0);
                    point(lb, -h, -h, uTo, 0);
                    point(lb, -h, h, uTo, 1);
                    point(ls, -h, h, uFrom, 1);
                    point(ls, -h, -h, uFrom, 0);
                }
                case LEFT -> {
                    normal(0, 0, -1);
                    point(ls, -h, -h, uFrom, 0);
                    point(ls, h, -h, uFrom, 1);
                    point(lb, h, -h, uTo, 1);
                    point(lb, -h, -h, uTo, 0);
                }
                case RIGHT -> {
                    normal(0, 0, 1);
                    point(lb, -h, h, uTo, 0);
                    point(lb, h, h, uTo, 1);
                    point(ls, h, h, uFrom, 1);
                    point(ls, -h, h, uFrom, 0);
                }
            }
        }

        private void normal(double x, double y, double z) {
            Vec3 n = transform(x, y, z);
            nx = (float) n.x;
            ny = (float) n.y;
            nz = (float) n.z;
        }

        /** {@code u} and {@code v} run from 0 to 1 across the current row. */
        private void point(double x, double y, double z, double u, double v) {
            Vec3 world = transform(x, y, z).scale(scale).add(start);
            TextureAtlasSprite sprite = RenderUtil.sprite(row.sprite());
            float su = sprite.getU(row.uMin() + (row.uMax() - row.uMin()) * u);
            float sv = sprite.getV(row.vMin() + (row.vMax() - row.vMin()) * v);
            int light = LevelRenderer.getLightColor(level, new BlockPos(world));
            consumer.vertex(pose, (float) (world.x - origin.getX()), (float) (world.y - origin.getY()), (float) (world.z - origin.getZ()))
                    .color(255, 255, 255, 255)
                    .uv(su, sv)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(normalMatrix, nx, ny, nz)
                    .endVertex();
        }
    }
}
