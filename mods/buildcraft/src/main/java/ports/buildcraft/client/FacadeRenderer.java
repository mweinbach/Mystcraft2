package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.silicon.plug.FacadeBlockStateInfo;
import ports.buildcraft.silicon.plug.FacadeInstance;
import ports.buildcraft.silicon.plug.FacadeItem;
import ports.buildcraft.silicon.plug.FacadePhasedState;
import ports.buildcraft.silicon.plug.PluggableFacade;
import ports.buildcraft.transport.pluggable.PipePluggable;

/**
 * Upstream {@code PlugBakerFacade}: the copied block's face quads, remapped onto the two-pixel facade shell.
 * A hollow facade keeps a four-pixel frame and the hole's inner walls. Quads baked with no face are included
 * when their direction is that face, because 1.19 models often store culled quads that way.
 */
public final class FacadeRenderer {
    private static final int CACHE_LIMIT = 2048;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<CacheKey> KEYS = new ArrayList<>();
    private static final List<List<BakedQuad>> VALUES = new ArrayList<>();

    private FacadeRenderer() {}

    public static void render(PipePluggable plug, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (!(plug instanceof PluggableFacade facade)) return;
        FacadeBlockStateInfo info = facade.active().info;
        draw(info, facade.side, facade.isHollow(), facade.holder.getLevel(), facade.holder.getBlockPos(), poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
        if (!facade.isHollow() && !info.transparent) PluggableRenderers.renderBlocker(facade.side, poseStack, buffers, light);
    }

    /** The item shows the state a held phased facade cycles, on the west face, as upstream's item model. */
    public static void renderItem(ItemStack stack, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        FacadeInstance states = FacadeItem.get(stack);
        FacadePhasedState shown = states.currentForStack();
        draw(shown.info, Direction.WEST, states.hollow, null, null, poseStack, buffers, light, overlay);
        if (!states.hollow && !shown.info.transparent) PluggableRenderers.renderBlocker(Direction.WEST, poseStack, buffers, light);
    }

    private static void draw(FacadeBlockStateInfo info, Direction side, boolean hollow, BlockAndTintGetter level,
            BlockPos pos, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = info.state;
        if (state.isAir()) return;
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        List<BakedQuad> quads = cached(state, side, hollow, model);
        RenderType layer = ItemBlockRenderTypes.getChunkRenderType(state);
        VertexConsumer consumer = buffers.getBuffer(layer);
        PoseStack.Pose pose = poseStack.last();
        for (BakedQuad quad : quads) {
            float red = 1, green = 1, blue = 1;
            int tint = quad.getTintIndex();
            if (tint != -1) {
                int colour = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, tint);
                if (colour != -1) {
                    red = ((colour >> 16) & 0xFF) / 255F;
                    green = ((colour >> 8) & 0xFF) / 255F;
                    blue = (colour & 0xFF) / 255F;
                }
            }
            consumer.putBulkData(pose, quad, red, green, blue, light, overlay);
        }
    }

    private static List<BakedQuad> cached(BlockState state, Direction side, boolean hollow, BakedModel model) {
        int modelId = System.identityHashCode(model);
        for (int i = 0; i < KEYS.size(); i++) {
            CacheKey key = KEYS.get(i);
            if (key.state == state && key.side == side && key.hollow == hollow && key.modelId == modelId) return VALUES.get(i);
        }
        if (KEYS.size() >= CACHE_LIMIT) {
            KEYS.clear();
            VALUES.clear();
        }
        List<BakedQuad> baked = bake(state, model, side, hollow);
        KEYS.add(new CacheKey(state, side, hollow, modelId));
        VALUES.add(baked);
        return baked;
    }

    private static List<BakedQuad> bake(BlockState state, BakedModel model, Direction side, boolean hollow) {
        int size = PluggableFacade.SIZE;
        int inner = 16 - size;
        List<BakedQuad> quads = new ArrayList<>();
        if (!hollow) {
            add(quads, state, model, side, v(0, 16, 0), v(16, 16, 0), v(16, 0, 0), v(0, 0, 0));
            add(quads, state, model, side.getOpposite(), v(size, inner, inner), v(inner, inner, inner), v(inner, size, inner), v(size, size, inner));
        }
        for (Rotation rotation : Rotation.values()) {
            if (hollow) {
                boolean even = rotation.ordinal() % 2 == 0;
                addRotated(quads, state, model, side, rotation,
                        v(0, even ? 4 : 0, 0), v(4, even ? 4 : 0, 0), v(4, even ? 16 : 12, 0), v(0, even ? 16 : 12, 0));
            }
            addRotated(quads, state, model, side.getOpposite(), rotation,
                    v(0, 16, 16), v(size, inner, inner), v(size, size, inner), v(0, 0, 16));
            if (hollow) {
                boolean even = rotation.ordinal() % 2 == 0;
                addRotated(quads, state, model, side.getOpposite(), rotation,
                        v(size, even ? inner : 12, inner), v(4, even ? inner : 12, inner),
                        v(4, even ? 4 : size, inner), v(size, even ? 4 : size, inner));
            }
        }
        if (hollow) {
            boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            for (Direction facing : Direction.values()) {
                if (facing.getAxis() == side.getAxis()) continue;
                boolean across = side.getAxis() == Direction.Axis.Z && facing.getAxis() == Direction.Axis.X
                        || side.getAxis() == Direction.Axis.X && facing.getAxis() == Direction.Axis.Y
                        || side.getAxis() == Direction.Axis.Y && facing.getAxis() == Direction.Axis.Z;
                if (across) {
                    add(quads, state, model, facing,
                            v(positive ? 16 : size, 4, 12.003), v(positive ? 16 : size, 12, 12.003),
                            v(positive ? inner : 0, 12, 12.003), v(positive ? inner : 0, 4, 12.003));
                } else {
                    add(quads, state, model, facing,
                            v(4, positive ? 16 : size, 12.003), v(4, positive ? inner : 0, 12.003),
                            v(12, positive ? inner : 0, 12.003), v(12, positive ? 16 : size, 12.003));
                }
            }
        }
        return quads;
    }

    private static void addRotated(List<BakedQuad> quads, BlockState state, BakedModel model, Direction side, Rotation rotation,
            Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
        add(quads, state, model, side, rotate(p0, rotation), rotate(p1, rotation), rotate(p2, rotation), rotate(p3, rotation));
    }

    private static void add(List<BakedQuad> quads, BlockState state, BakedModel model, Direction side, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
        for (BakedQuad quad : faceQuads(model, state, side)) quads.add(transform(quad, side, p0, p1, p2, p3));
    }

    /** Face-culled quads plus general quads whose direction is that face. */
    private static List<BakedQuad> faceQuads(BakedModel model, BlockState state, Direction side) {
        RANDOM.setSeed(0);
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(state, side, RANDOM));
        RANDOM.setSeed(0);
        for (BakedQuad quad : model.getQuads(state, null, RANDOM)) {
            if (quad.getDirection() == side && !quads.contains(quad)) quads.add(quad);
        }
        return quads;
    }

    private static BakedQuad transform(BakedQuad quad, Direction side, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
        Vertex[] source = read(quad);
        Vec3[] targets = {map(p0, side), map(p1, side), map(p2, side), map(p3, side)};
        Vec3[] origins = new Vec3[4];
        float minU = Float.MAX_VALUE, minV = Float.MAX_VALUE, maxU = -Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            origins[i] = new Vec3(source[i].x, source[i].y, source[i].z);
            minU = Math.min(minU, source[i].u);
            minV = Math.min(minV, source[i].v);
            maxU = Math.max(maxU, source[i].u);
            maxV = Math.max(maxV, source[i].v);
        }
        Vertex[] moved = {source[0].copy(), source[1].copy(), source[2].copy(), source[3].copy()};
        boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        for (int corner = 0; corner < 4; corner++) {
            boolean min1 = corner == 0 || corner == 1;
            boolean min2 = corner == 0 || corner == 3;
            Vec3 position = targets[index(targets, side.getAxis(), min1, min2)];
            Vertex vertex = moved[index(origins, side.getAxis(), min1, min2)];
            vertex.x = (float) position.x;
            vertex.y = (float) position.y;
            vertex.z = (float) position.z;
            float u = switch (side.getAxis()) {
                case X -> (float) (minU + (maxU - minU) * (positive ? 1 - position.z : position.z));
                case Y -> (float) (minU + (maxU - minU) * position.x);
                case Z -> (float) (minU + (maxU - minU) * (positive ? position.x : 1 - position.x));
            };
            float v = switch (side.getAxis()) {
                case X, Z -> (float) (minV + (maxV - minV) * (1 - position.y));
                case Y -> (float) (minV + (maxV - minV) * (positive ? position.z : 1 - position.z));
            };
            vertex.u = u;
            vertex.v = v;
        }
        return write(quad, moved);
    }

    /** Upstream {@code PlugBakerFacade.rotate}, including its swapped ninety-degree cases. */
    private static Vec3 rotate(Vec3 vec, Rotation rotation) {
        return switch (rotation) {
            case NONE -> vec;
            case CLOCKWISE_90 -> new Vec3(1 - vec.y, 1 - vec.x, vec.z);
            case CLOCKWISE_180 -> new Vec3(1 - vec.x, 1 - vec.y, vec.z);
            case COUNTERCLOCKWISE_90 -> new Vec3(vec.y, vec.x, vec.z);
        };
    }

    private static Vec3 map(Vec3 pos, Direction side) {
        boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        return switch (side.getAxis()) {
            case X -> new Vec3(positive ? 1 - pos.z : pos.z, pos.y, pos.x);
            case Y -> new Vec3(pos.x, positive ? 1 - pos.z : pos.z, pos.y);
            case Z -> new Vec3(pos.y, pos.x, positive ? 1 - pos.z : pos.z);
        };
    }

    private static int index(Vec3[] positions, Direction.Axis axis, boolean min1, boolean min2) {
        Direction.Axis axis1 = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        Direction.Axis axis2 = axis == Direction.Axis.Y ? Direction.Axis.Z : (axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.Y);
        double minA = Double.POSITIVE_INFINITY, minB = Double.POSITIVE_INFINITY;
        double maxA = Double.NEGATIVE_INFINITY, maxB = Double.NEGATIVE_INFINITY;
        for (Vec3 pos : positions) {
            minA = Math.min(minA, component(pos, axis1));
            minB = Math.min(minB, component(pos, axis2));
            maxA = Math.max(maxA, component(pos, axis1));
            maxB = Math.max(maxB, component(pos, axis2));
        }
        double center1 = (minA + maxA) / 2;
        double center2 = (minB + maxB) / 2;
        for (int i = 0; i < positions.length; i++) {
            Vec3 pos = positions[i];
            boolean first = min1 ? component(pos, axis1) < center1 : component(pos, axis1) > center1;
            boolean second = min2 ? component(pos, axis2) < center2 : component(pos, axis2) > center2;
            if (first && second) return i;
        }
        return 0;
    }

    private static double component(Vec3 pos, Direction.Axis axis) {
        return switch (axis) {
            case X -> pos.x;
            case Y -> pos.y;
            case Z -> pos.z;
        };
    }

    private static Vec3 v(double x, double y, double z) {
        return new Vec3(x / 16.0, y / 16.0, z / 16.0);
    }

    private static Vertex[] read(BakedQuad quad) {
        int[] data = quad.getVertices();
        int stride = data.length / 4;
        Vertex[] vertices = new Vertex[4];
        for (int i = 0; i < 4; i++) {
            int at = i * stride;
            vertices[i] = new Vertex(Float.intBitsToFloat(data[at]), Float.intBitsToFloat(data[at + 1]), Float.intBitsToFloat(data[at + 2]),
                    data[at + 3], Float.intBitsToFloat(data[at + 4]), Float.intBitsToFloat(data[at + 5]), data[at + 6], data[at + 7]);
        }
        return vertices;
    }

    private static BakedQuad write(BakedQuad source, Vertex[] vertices) {
        int[] from = source.getVertices();
        int stride = from.length / 4;
        int[] data = from.clone();
        for (int i = 0; i < 4; i++) {
            int at = i * stride;
            Vertex vertex = vertices[i];
            data[at] = Float.floatToRawIntBits(vertex.x);
            data[at + 1] = Float.floatToRawIntBits(vertex.y);
            data[at + 2] = Float.floatToRawIntBits(vertex.z);
            data[at + 4] = Float.floatToRawIntBits(vertex.u);
            data[at + 5] = Float.floatToRawIntBits(vertex.v);
        }
        return new BakedQuad(data, source.getTintIndex(), source.getDirection(), source.getSprite(), source.isShade());
    }

    private record CacheKey(BlockState state, Direction side, boolean hollow, int modelId) {}

    private static final class Vertex {
        float x, y, z, u, v;
        final int color, uv2, normal;

        Vertex(float x, float y, float z, int color, float u, float v, int uv2, int normal) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
            this.u = u;
            this.v = v;
            this.uv2 = uv2;
            this.normal = normal;
        }

        Vertex copy() {
            return new Vertex(x, y, z, color, u, v, uv2, normal);
        }
    }
}
