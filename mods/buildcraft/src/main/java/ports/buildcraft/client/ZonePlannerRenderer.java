package ports.buildcraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.robotics.BCRobotics;
import ports.buildcraft.robotics.ZonePlannerBlock;
import ports.buildcraft.robotics.ZonePlannerBlockEntity;
import ports.buildcraft.robotics.zone.ZonePlannerMapChunk;
import ports.buildcraft.robotics.zone.ZonePlannerMapChunkKey;

/**
 * Upstream {@code RenderZonePlanner}: a 10×8 map of the land in front of the block, four blocks a texel, on the
 * face the block faces. The texture waits until every texel has arrived and is dropped after five minutes.
 */
public final class ZonePlannerRenderer implements BlockEntityRenderer<ZonePlannerBlockEntity> {
    private static final int WIDTH = 10;
    private static final int HEIGHT = 8;
    private static final int SCALE = 4;
    private static final long LIFE = 5 * 60 * 1_000_000_000L;
    private static final Map<FaceKey, Face> FACES = new ConcurrentHashMap<>();

    private record FaceKey(BlockPos pos, Direction side) {}

    private record Face(DynamicTexture texture, ResourceLocation id, long created) {}

    @Override
    public void render(ZonePlannerBlockEntity planner, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = planner.getLevel();
        if (level == null || !planner.getBlockState().is(BCRobotics.ZONE_PLANNER.get())) return;
        Direction side = planner.getBlockState().getValue(ZonePlannerBlock.FACING).getOpposite();
        Face face = face(planner, side);
        if (face == null) return;
        expire();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(face.id));
        Matrix4f pose = poseStack.last().pose();
        double offset = 0.001;
        double minX = 3 / 16D - offset, maxX = 13 / 16D + offset, minY = 5 / 16D - offset, maxY = 13 / 16D + offset;
        double minZ = -offset, maxZ = 1 + offset;
        Vec3 min;
        Vec3 max;
        switch (side) {
            case NORTH -> { min = new Vec3(minX, minY, maxZ); max = new Vec3(maxX, maxY, maxZ); }
            case EAST -> { min = new Vec3(minZ, minY, minX); max = new Vec3(minZ, maxY, maxX); }
            case SOUTH -> { min = new Vec3(minX, minY, minZ); max = new Vec3(maxX, maxY, minZ); }
            default -> { min = new Vec3(maxZ, minY, minX); max = new Vec3(maxZ, maxY, maxX); }
        }
        vertex(consumer, pose, min.x, min.y, min.z, 0, 1);
        vertex(consumer, pose, max.x, min.y, max.z, 1, 1);
        vertex(consumer, pose, max.x, max.y, max.z, 1, 0);
        vertex(consumer, pose, min.x, max.y, min.z, 0, 0);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, double x, double y, double z, float u, float v) {
        consumer.vertex(pose, (float) x, (float) y, (float) z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(RenderUtil.FULL_BRIGHT).normal(0, 1, 0).endVertex();
    }

    @Nullable
    private static Face face(ZonePlannerBlockEntity planner, Direction side) {
        FaceKey key = new FaceKey(planner.getBlockPos(), side);
        Face existing = FACES.get(key);
        if (existing != null && System.nanoTime() - existing.created < LIFE) return existing;
        if (existing != null) release(existing);
        Face created = create(planner, side);
        if (created != null) FACES.put(key, created);
        return created;
    }

    @Nullable
    private static Face create(ZonePlannerBlockEntity planner, Direction side) {
        Level level = planner.getLevel();
        if (level == null) return null;
        NativeImage image = new NativeImage(WIDTH, HEIGHT, true);
        int slice = ZonePlannerMapRenderer.mapLevel(level);
        int dimension = ZonePlannerMapChunkKey.dimensionId(level);
        BlockPos origin = planner.getBlockPos();
        for (int textureX = 0; textureX < WIDTH; textureX++) {
            for (int textureY = 0; textureY < HEIGHT; textureY++) {
                int offset1 = (textureX - WIDTH / 2) * SCALE;
                int offset2 = (textureY - HEIGHT / 2) * SCALE;
                int posX;
                int posZ;
                switch (side) {
                    case NORTH -> { posX = origin.getX() + offset1; posZ = origin.getZ() - offset2; }
                    case EAST -> { posX = origin.getX() + offset2; posZ = origin.getZ() + offset1; }
                    case SOUTH -> { posX = origin.getX() + offset1; posZ = origin.getZ() + offset2; }
                    default -> { posX = origin.getX() - offset2; posZ = origin.getZ() + offset1; }
                }
                ZonePlannerMapChunk chunk = ZonePlannerMapDataClient.INSTANCE.getChunk(level,
                        new ZonePlannerMapChunkKey(new ChunkPos(posX >> 4, posZ >> 4), dimension, slice));
                if (chunk == null) {
                    image.close();
                    return null;
                }
                image.setPixelRGBA(textureX, textureY, abgr(chunk.colour(posX, posZ)));
            }
        }
        DynamicTexture texture = new DynamicTexture(image);
        texture.upload();
        ResourceLocation id = BuildCraft.id("dynamic/zone_planner/" + origin.asLong() + "_" + side.get3DDataValue());
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return new Face(texture, id, System.nanoTime());
    }

    /** Upstream stored {@code colour | 0xFF000000}; an empty column's -1 stays white. Native images are ABGR. */
    private static int abgr(int colour) {
        int red = (colour >> 16) & 0xFF;
        int green = (colour >> 8) & 0xFF;
        int blue = colour & 0xFF;
        return 0xFF000000 | blue << 16 | green << 8 | red;
    }

    private static void expire() {
        long now = System.nanoTime();
        Iterator<Face> iterator = FACES.values().iterator();
        while (iterator.hasNext()) {
            Face face = iterator.next();
            if (now - face.created < LIFE) continue;
            release(face);
            iterator.remove();
        }
    }

    private static void release(Face face) {
        Minecraft.getInstance().getTextureManager().release(face.id);
        face.texture.close();
    }
}
