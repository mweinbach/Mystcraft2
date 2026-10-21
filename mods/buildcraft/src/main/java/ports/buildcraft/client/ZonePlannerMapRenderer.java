package ports.buildcraft.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.lwjgl.opengl.GL11;
import ports.buildcraft.robotics.ZonePlannerBlockEntity;
import ports.buildcraft.robotics.zone.ZonePlan;
import ports.buildcraft.robotics.zone.ZonePlannerMapChunk;
import ports.buildcraft.robotics.zone.ZonePlannerMapChunkKey;

/**
 * Upstream {@code GuiZonePlanner}'s map and {@code ZonePlannerMapRenderer}. Display lists and GLU are gone in
 * 1.19, so each slice is drawn immediately and the camera is a perspective matrix. The highlight is a line box:
 * the core profile has no polygon mode. A private vertex-array object crashes this host's driver inside
 * {@code glDrawElements}, so the map uses the buffer uploader the rest of the GUI already draws with.
 */
public final class ZonePlannerMapRenderer {
    /** Upstream {@code EnumDyeColor.getColorValue}, indexed by dye id. */
    private static final int[] DYE = {
            16383998, 16351261, 13061821, 3847130, 16701501, 8439583, 15961002, 4673362,
            10329495, 1481884, 8991416, 3949738, 8606770, 6192150, 11546150, 1908001
    };
    @Nullable private static Matrix4f lastProjection;
    @Nullable private static Matrix4f lastModel;
    private static int viewX, viewY, viewW, viewH;
    private static int mapLevel;
    private static long viewedAt;

    private ZonePlannerMapRenderer() {}

    /** The slice the player's feet are in, as upstream's {@code TileZonePlanner.getLevel}. */
    public static int mapLevel(Level level) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos start = minecraft.player == null ? BlockPos.ZERO : minecraft.player.blockPosition();
        return ZonePlannerMapChunkKey.levelOf(level, start);
    }

    /**
     * Draws the map into the GUI rectangle and returns the column under the mouse, in world coordinates.
     * {@code layers} is indexed by dye id; a null entry is skipped and a non-null one is drawn instead of the
     * planner's own layer.
     */
    @Nullable
    public static BlockPos render(ZonePlannerBlockEntity planner, int guiLeft, int guiTop, float cameraX, float cameraY, float cameraZ,
            int mouseX, int mouseY, @Nullable DyeColor brush, @Nullable ZonePlan[] painted) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) return null;
        Window window = minecraft.getWindow();
        double scale = window.getGuiScale();
        int pixelX = (int) ((guiLeft + 8) * scale);
        int pixelY = (int) (window.getHeight() - (guiTop + 9 + 100) * scale);
        int pixelW = (int) (213 * scale);
        int pixelH = (int) (100 * scale);
        if (pixelW <= 0 || pixelH <= 0) return null;

        mapLevel = mapLevel(level);
        int dimension = ZonePlannerMapChunkKey.dimensionId(level);
        long now = System.nanoTime();

        Matrix4f projection = Matrix4f.createScaleMatrix((float) scale, (float) scale, 1);
        projection.multiply(Matrix4f.perspective(70, 213F / 100F, 1, 10000));
        PoseStack pose = new PoseStack();
        pose.mulPose(Vector3f.XP.rotationDegrees(90));
        pose.translate(-cameraX, -cameraY, -cameraZ);
        Matrix4f model = pose.last().pose().copy();
        lastProjection = projection;
        lastModel = model;
        viewX = pixelX;
        viewY = pixelY;
        viewW = pixelW;
        viewH = pixelH;
        viewedAt = now;

        int minScreenX = pixelX;
        int maxScreenX = pixelX + pixelW;
        int minScreenY = (int) ((window.getGuiScaledHeight() - (guiTop + 9)) * scale);
        int maxScreenY = (int) ((window.getGuiScaledHeight() - (guiTop + 9 + 100)) * scale);
        int posX = (int) cameraX;
        int posZ = (int) cameraZ;
        int minChunkX = (posX >> 4) - 8;
        int maxChunkX = (posX >> 4) + 8;
        int minChunkZ = (posZ >> 4) - 8;
        int maxChunkZ = (posZ >> 4) + 8;
        for (int sx : new int[] {minScreenX, maxScreenX}) {
            for (int sy : new int[] {minScreenY, maxScreenY}) {
                BlockPos hit = ray(sx, sy);
                if (hit == null) continue;
                ChunkPos chunk = new ChunkPos(hit);
                minChunkX = Math.min(minChunkX, chunk.x);
                maxChunkX = Math.max(maxChunkX, chunk.x);
                minChunkZ = Math.min(minChunkZ, chunk.z);
                maxChunkZ = Math.max(maxChunkZ, chunk.z);
            }
        }
        minChunkX--;
        maxChunkX++;
        minChunkZ--;
        maxChunkZ++;

        RenderSystem.enableScissor(pixelX, pixelY, pixelW, pixelH);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.viewport(pixelX, pixelY, pixelW, pixelH);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projection);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        float previousFogStart = RenderSystem.getShaderFogStart();
        float previousFogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        PoseStack camera = RenderSystem.getModelViewStack();
        camera.mulPose(Vector3f.XP.rotationDegrees(90));
        camera.translate(-cameraX, -cameraY, -cameraZ);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.reset();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                drawChunk(level, new ZonePlannerMapChunkKey(new ChunkPos(chunkX, chunkZ), dimension, mapLevel));
            }
        }

        int windowX = (int) (mouseX * scale);
        int windowY = (int) (window.getHeight() - mouseY * scale);
        BlockPos found = windowX >= minScreenX && windowX <= maxScreenX && windowY <= minScreenY && windowY >= maxScreenY
                ? ray(windowX, windowY) : null;
        if (found != null) {
            ZonePlannerMapChunk chunk = chunk(level, new ChunkPos(found), dimension);
            int colour = chunk == null ? 0 : chunk.colour(found.getX(), found.getZ());
            RenderSystem.disableDepthTest();
            RenderSystem.lineWidth(2);
            drawBox(found.getX(), found.getY(), found.getZ(), 1, 0.5F, shade(colour, 0.7F), 0x77);
            RenderSystem.enableDepthTest();
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BlockPos origin = planner.getBlockPos();
        for (int layer = 0; layer < ZonePlannerBlockEntity.LAYERS; layer++) {
            if (brush != null && brush.getId() != layer) continue;
            ZonePlan plan = painted != null && painted[layer] != null ? painted[layer] : planner.layers[layer];
            if (plan.chunks().isEmpty()) continue;
            BufferBuilder builder = new BufferBuilder(plan.chunks().size() * 256 * 20 * 16);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            boolean any = false;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                    ZonePlannerMapChunk map = chunk(level, chunkPos, dimension);
                    if (map == null) continue;
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int blockX = chunkPos.getMinBlockX() + x;
                            int blockZ = chunkPos.getMinBlockZ() + z;
                            if (!plan.get(blockX - origin.getX(), blockZ - origin.getZ())) continue;
                            ZonePlannerMapChunk.Column column = map.column(x, z);
                            if (column == null) continue;
                            cuboid(builder, blockX, column.y() + 0.1, blockZ, 0.6, 0.5, DYE[layer], 0x55);
                            any = true;
                        }
                    }
                }
            }
            if (!any) {
                builder.discard();
            } else {
                BufferUploader.drawWithShader(builder.end());
            }
        }
        RenderSystem.disableBlend();
        RenderSystem.setShaderFogStart(previousFogStart);
        RenderSystem.setShaderFogEnd(previousFogEnd);

        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.disableScissor();
        return found;
    }

    /** A ray through the map as it was last drawn, in window pixels with a bottom-left origin. */
    @Nullable
    public static BlockPos ray(int screenX, int screenY) {
        if (lastProjection == null || lastModel == null || viewW <= 0 || viewH <= 0) return null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || System.nanoTime() - viewedAt > 1_000_000_000L) return null;
        Matrix4f invProjection = lastProjection.copy();
        Matrix4f invModel = lastModel.copy();
        if (!invProjection.invert() || !invModel.invert()) return null;
        float nearX = (screenX - viewX) / (float) viewW * 2 - 1;
        float nearY = (screenY - viewY) / (float) viewH * 2 - 1;
        Vector4f near = world(invProjection, invModel, nearX, nearY, -1);
        Vector4f far = world(invProjection, invModel, nearX, nearY, 1);
        double dx = far.x() - near.x();
        double dy = far.y() - near.y();
        double dz = far.z() - near.z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4) return null;
        dx /= length;
        dy /= length;
        dz /= length;
        double x = near.x();
        double y = near.y();
        double z = near.z();
        int dimension = ZonePlannerMapChunkKey.dimensionId(minecraft.level);
        for (int i = 0; i < 10000; i++) {
            int roundedX = (int) Math.round(x);
            int roundedZ = (int) Math.round(z);
            int chunkX = roundedX >> 4;
            int chunkZ = roundedZ >> 4;
            ZonePlannerMapChunk chunk = chunk(minecraft.level, new ChunkPos(chunkX, chunkZ), dimension);
            if (chunk == null) return null;
            ZonePlannerMapChunk.Column column = chunk.column(roundedX, roundedZ);
            if (column != null && column.y() >= Math.round(y)) {
                return new BlockPos(roundedX, column.y(), roundedZ);
            }
            x += dx;
            y += dy;
            z += dz;
        }
        return null;
    }

    private static Vector4f world(Matrix4f invProjection, Matrix4f invModel, float ndcX, float ndcY, float ndcZ) {
        Vector4f point = new Vector4f(ndcX, ndcY, ndcZ, 1);
        point.transform(invProjection);
        if (point.w() != 0) point.set(point.x() / point.w(), point.y() / point.w(), point.z() / point.w(), 1);
        point.transform(invModel);
        if (point.w() != 0) point.set(point.x() / point.w(), point.y() / point.w(), point.z() / point.w(), 1);
        return point;
    }

    @Nullable
    private static ZonePlannerMapChunk chunk(Level level, ChunkPos pos, int dimension) {
        return ZonePlannerMapDataClient.INSTANCE.getChunk(level, new ZonePlannerMapChunkKey(pos, dimension, mapLevel));
    }

    private static void drawChunk(Level level, ZonePlannerMapChunkKey key) {
        ZonePlannerMapChunk chunk = ZonePlannerMapDataClient.INSTANCE.getChunk(level, key);
        if (chunk == null) return;
        // One column is five quads of position-colour vertices. Size the buffer for a full slice so it does not grow.
        BufferBuilder builder = new BufferBuilder(16 * 16 * 20 * 16);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                ZonePlannerMapChunk.Column column = chunk.column(x, z);
                if (column == null) continue;
                cuboid(builder, key.chunkPos.getMinBlockX() + x, column.y(), key.chunkPos.getMinBlockZ() + z, column.y(), 0.5, column.colour(), 255);
                any = true;
            }
        }
        if (!any) {
            builder.discard();
            return;
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static void drawBox(double x, double y, double z, double height, double radius, int rgb, int alpha) {
        BufferBuilder builder = new BufferBuilder(24 * 16);
        builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        double rY = height * 0.5;
        double cy = y - rY;
        double x0 = x - radius, x1 = x + radius, y0 = cy - rY, y1 = cy + rY, z0 = z - radius, z1 = z + radius;
        line(builder, x0, y0, z0, x1, y0, z0, rgb, alpha);
        line(builder, x1, y0, z0, x1, y0, z1, rgb, alpha);
        line(builder, x1, y0, z1, x0, y0, z1, rgb, alpha);
        line(builder, x0, y0, z1, x0, y0, z0, rgb, alpha);
        line(builder, x0, y1, z0, x1, y1, z0, rgb, alpha);
        line(builder, x1, y1, z0, x1, y1, z1, rgb, alpha);
        line(builder, x1, y1, z1, x0, y1, z1, rgb, alpha);
        line(builder, x0, y1, z1, x0, y1, z0, rgb, alpha);
        line(builder, x0, y0, z0, x0, y1, z0, rgb, alpha);
        line(builder, x1, y0, z0, x1, y1, z0, rgb, alpha);
        line(builder, x1, y0, z1, x1, y1, z1, rgb, alpha);
        line(builder, x0, y0, z1, x0, y1, z1, rgb, alpha);
        BufferUploader.drawWithShader(builder.end());
    }

    private static void line(BufferBuilder builder, double x0, double y0, double z0, double x1, double y1, double z1, int rgb, int alpha) {
        vertex(builder, x0, y0, z0, rgb, alpha);
        vertex(builder, x1, y1, z1, rgb, alpha);
    }

    /** Upstream {@code ZonePlannerMapRenderer.drawBlockCuboid}, with its side shading baked into the colour. */
    private static void cuboid(BufferBuilder builder, double x, double y, double z, double height, double radius, int rgb, int alpha) {
        double rY = height * 0.5;
        double cy = y - rY;
        double x0 = x - radius, x1 = x + radius, y0 = cy - rY, y1 = cy + rY, z0 = z - radius, z1 = z + radius;
        quad(builder, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, rgb, alpha);
        int dark = shade(rgb, 0.6F);
        quad(builder, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, dark, alpha);
        quad(builder, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, dark, alpha);
        int mid = shade(rgb, 0.8F);
        quad(builder, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, mid, alpha);
        quad(builder, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, mid, alpha);
    }

    private static void quad(BufferBuilder builder, double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2,
            double x3, double y3, double z3, int rgb, int alpha) {
        vertex(builder, x0, y0, z0, rgb, alpha);
        vertex(builder, x1, y1, z1, rgb, alpha);
        vertex(builder, x2, y2, z2, rgb, alpha);
        vertex(builder, x3, y3, z3, rgb, alpha);
    }

    private static void vertex(BufferBuilder builder, double x, double y, double z, int rgb, int alpha) {
        builder.vertex(x, y, z).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha).endVertex();
    }

    private static int shade(int rgb, float scale) {
        int red = (int) (((rgb >> 16) & 0xFF) * scale);
        int green = (int) (((rgb >> 8) & 0xFF) * scale);
        int blue = (int) ((rgb & 0xFF) * scale);
        return red << 16 | green << 8 | blue;
    }
}
