/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;
import ports.metalrender.MetalRenderMod;
import ports.metalrender.config.MetalRenderConfig;
import ports.metalrender.mixin.NativeImageAccessor;
import ports.metalrender.nativebridge.MetalNative;
import ports.metalrender.render.FrameMath;
import ports.metalrender.render.FrameUniformWriter;
import ports.metalrender.render.TemporalState;
import ports.metalrender.terrain.PackedLayer;
import ports.metalrender.terrain.SectionSlot;
import ports.metalrender.terrain.SectionStore;
import ports.metalrender.terrain.TerrainLayer;

/**
 * Render-thread side of MetalRender: applies chunk uploads to the {@link SectionStore}, mirrors the
 * block atlas and lightmap into Metal, and replaces vanilla's solid and cutout chunk layer draws with
 * the Metal opaque pass composited into the OpenGL framebuffer. Translucent and tripwire layers stay
 * with vanilla, so they sort against entities and particles exactly as without the mod.
 */
public final class MetalTerrainRenderer {

    private static final Queue<Runnable> PENDING = new ConcurrentLinkedQueue<>();
    /** Section store changes that arrived while the GPU was reading the store (render thread only). */
    private static final ArrayDeque<Runnable> DEFERRED = new ArrayDeque<>();
    private static final ByteBuffer FRAME = direct(FrameUniformWriter.FRAME_BYTES);
    private static final ByteBuffer TEMPORAL = direct(FrameUniformWriter.TEMPORAL_BYTES);
    private static final TemporalState TEMPORAL_STATE = new TemporalState();
    private static final FloatBuffer MATRIX = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static final int[] COUNTERS = new int[8];
    private static final float[] GPU_MS = new float[2];

    private static GlCompositor compositor;
    private static boolean atlasDirty = true;
    private static long frames;

    /** An opaque pass is committed and not yet waited for; the section store must not change. */
    private static boolean inFlight;
    private static int inFlightEdge;
    private static long inFlightSince;

    private MetalTerrainRenderer() {
    }

    private static ByteBuffer direct(final int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    // ------------------------------------------------------------------ context lifecycle

    static void onContextCreated() {
        atlasDirty = true;
        TEMPORAL_STATE.invalidate();
    }

    /** Waits for an in-flight pass and applies the store changes it held back. */
    static void settle() {
        if (inFlight) {
            inFlight = false;
            MetalNative.finishOpaquePass(MetalRenderClient.context());
        }
        Runnable op;
        while ((op = DEFERRED.poll()) != null) {
            op.run();
        }
    }

    static void onContextDestroyed() {
        settle();
        PENDING.clear();
        if (compositor != null) {
            compositor.close();
            compositor = null;
        }
    }

    static void onSettingsChanged() {
        TEMPORAL_STATE.invalidate();
    }

    // ------------------------------------------------------------------ chunk data (render thread)

    /** Runs a section store change now, or after the in-flight pass while the GPU reads the store. */
    private static void mutate(final Runnable op) {
        if (inFlight) {
            DEFERRED.add(op);
        } else {
            op.run();
        }
    }

    /** Called when vanilla resets a chunk (moved to a new origin or released). */
    public static void releaseSection(final RenderChunkExt chunk) {
        mutate(() -> releaseSectionNow(chunk));
    }

    private static void releaseSectionNow(final RenderChunkExt chunk) {
        final SectionSlot slot = chunk.metalrender$slot();
        if (slot != null) {
            final SectionStore store = MetalRenderClient.store();
            if (store != null) {
                store.release(slot);
            }
            chunk.metalrender$setSlot(null);
        }
    }

    static void applyUpload(final ChunkRenderDispatcher.RenderChunk chunk, final TerrainLayer layer,
                            final PackedLayer data, final int epoch, final int generation, final BlockPos origin) {
        mutate(() -> {
            final long start = System.nanoTime();
            applyUploadNow(chunk, layer, data, epoch, generation, origin);
            PerfStats.uploadNanos += System.nanoTime() - start;
            PerfStats.uploads++;
        });
    }

    private static void applyUploadNow(final ChunkRenderDispatcher.RenderChunk chunk, final TerrainLayer layer,
                                       final PackedLayer data, final int epoch, final int generation,
                                       final BlockPos origin) {
        final SectionStore store = MetalRenderClient.store();
        final RenderChunkExt ext = (RenderChunkExt) chunk;
        if (store == null || epoch != MetalRenderClient.epoch() || ext.metalrender$released()
                || ext.metalrender$generation() != generation || !chunk.getOrigin().equals(origin)) {
            return; // stale: the chunk moved, was released, or the Metal context was replaced
        }
        SectionSlot slot = ext.metalrender$slot();
        if (slot == null || slot.isReleased()) {
            if (data.quads() == 0) {
                return;
            }
            slot = store.allocate(origin.getX(), origin.getY(), origin.getZ(), chunk);
            if (slot == null) {
                MetalRenderMod.LOGGER.warn("MetalRender could not grow its section buffer; {} stays empty", origin);
                return;
            }
            ext.metalrender$setSlot(slot);
        }
        if (!store.applyLayer(slot, layer, data)) {
            MetalRenderMod.LOGGER.warn("MetalRender could not grow its vertex arena; {} {} not drawn", origin, layer);
        }
    }

    /**
     * Called (possibly on a worker thread) when a rebuild finished. Layers the rebuild produced no
     * geometry for are cleared on the render thread, before any later upload runs.
     */
    public static void queueEmptyLayerSync(final ChunkRenderDispatcher.RenderChunk chunk) {
        if (!MetalRenderClient.isActive()) {
            return;
        }
        final ChunkRenderDispatcher.CompiledChunk compiled = chunk.getCompiledChunk();
        PENDING.add(() -> syncEmptyLayers(chunk, compiled));
    }

    private static void syncEmptyLayers(final ChunkRenderDispatcher.RenderChunk chunk,
                                        final ChunkRenderDispatcher.CompiledChunk compiled) {
        final SectionStore store = MetalRenderClient.store();
        final RenderChunkExt ext = (RenderChunkExt) chunk;
        final SectionSlot slot = ext.metalrender$slot();
        if (store == null || slot == null || slot.isReleased() || chunk.getCompiledChunk() != compiled) {
            return;
        }
        for (final TerrainLayer layer : TerrainLayer.values()) {
            if (compiled.isEmpty(Layers.renderType(layer))) {
                store.clearLayer(slot, layer);
            }
        }
        if (slot.totalQuads() == 0) {
            store.release(slot);
            ext.metalrender$setSlot(null);
        }
    }

    public static void drainPendingOps() {
        Runnable op;
        while ((op = PENDING.poll()) != null) {
            mutate(op);
        }
    }

    // ------------------------------------------------------------------ textures

    public static void markAtlasDirty() {
        atlasDirty = true;
    }

    /** Mirrors an animated sprite frame (all mip levels) into the Metal atlas. */
    public static void onSpriteUpload(final ResourceLocation atlas, final int x, final int y, final int width,
                                      final int height, final int frameX, final int frameY, final NativeImage[] frames) {
        final long ctx = MetalRenderClient.context();
        if (ctx == 0 || atlasDirty || !TextureAtlas.LOCATION_BLOCKS.equals(atlas)) {
            return;
        }
        final long start = System.nanoTime();
        for (int level = 0; level < frames.length; level++) {
            final int w = width >> level;
            final int h = height >> level;
            if (w <= 0 || h <= 0) {
                break;
            }
            final long pixels = ((NativeImageAccessor) (Object) frames[level]).metalrender$pixels();
            if (pixels != 0) {
                MetalNative.uploadAtlasRegion(ctx, level, x >> level, y >> level, frameX >> level, frameY >> level,
                        w, h, frames[level].getWidth(), pixels);
            }
        }
        PerfStats.spriteUploadNanos += System.nanoTime() - start;
        PerfStats.spriteUploads++;
    }

    public static void onLightmapUpdated(final NativeImage lightPixels) {
        final long ctx = MetalRenderClient.context();
        if (ctx != 0) {
            final long pixels = ((NativeImageAccessor) (Object) lightPixels).metalrender$pixels();
            if (pixels != 0) {
                MetalNative.uploadLightmap(ctx, pixels);
            }
        }
    }

    /** Reads every mip level of the block atlas back from OpenGL into the Metal atlas. */
    private static void syncAtlas(final long ctx) {
        final int texture = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getId();
        final int previous = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(texture);
        final int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        final int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (width > 0 && height > 0) {
            final int maxLevel = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL);
            final int possible = 32 - Integer.numberOfLeadingZeros(Math.max(width, height));
            final int levels = Math.max(1, Math.min(maxLevel + 1, possible));
            MetalNative.initAtlasTexture(ctx, width, height, levels);
            GlStateManager._pixelStore(GL11.GL_PACK_ALIGNMENT, 4);
            GlStateManager._pixelStore(GL11.GL_PACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL11.GL_PACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL11.GL_PACK_SKIP_ROWS, 0);
            for (int level = 0; level < levels; level++) {
                final int w = Math.max(1, width >> level);
                final int h = Math.max(1, height >> level);
                final long pixels = MemoryUtil.nmemAlloc((long) w * h * 4L);
                try {
                    GlStateManager._getTexImage(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
                    MetalNative.uploadAtlasRegion(ctx, level, 0, 0, 0, 0, w, h, w, pixels);
                } finally {
                    MemoryUtil.nmemFree(pixels);
                }
            }
            MetalRenderMod.LOGGER.info("MetalRender mirrored the block atlas ({}x{}, {} mip levels)", width, height, levels);
        }
        GlStateManager._bindTexture(previous);
        atlasDirty = false;
    }

    // ------------------------------------------------------------------ frame

    /**
     * Called from {@code renderLevel} just before vanilla's terrain setup, once the sky, fog and
     * camera for the frame are final. Commits the opaque pass so the GPU renders terrain while the
     * CPU runs vanilla's visibility and chunk scheduling; {@link #renderLayer} waits for it. Store
     * changes made in between (chunk uploads, releases) are held back until then and show from the
     * next frame.
     */
    public static void beginFrame(final PoseStack poseStack, final Camera camera, final Matrix4f projection) {
        settle();
        if (!MetalRenderClient.isActive()) {
            return;
        }
        final Vec3 pos = camera.getPosition();
        try {
            begin(poseStack, pos.x, pos.y, pos.z, projection);
        } catch (final RuntimeException | LinkageError e) {
            MetalRenderMod.LOGGER.error("MetalRender failed while rendering terrain; falling back to vanilla", e);
            MetalRenderClient.fail("render error: " + e);
        }
    }

    /** Called when {@code renderLevel} returns: never leave a pass in flight across frames. */
    public static void endFrame() {
        settle();
    }

    /**
     * Replaces {@code LevelRenderer.renderChunkLayer} for solid and cutout layers. Returns true when
     * the layer was handled (the vanilla draw is skipped).
     */
    public static boolean renderLayer(final RenderType type, final PoseStack poseStack, final double camX,
                                      final double camY, final double camZ, final Matrix4f projection) {
        if (!MetalRenderClient.isActive()) {
            return false;
        }
        final TerrainLayer layer = Layers.of(type);
        if (layer == null) {
            return false; // translucent, tripwire and anything else: vanilla
        }
        try {
            if (layer == TerrainLayer.SOLID) {
                if (!inFlight) {
                    begin(poseStack, camX, camY, camZ, projection); // beginFrame did not run this frame
                }
                if (inFlight) {
                    finishAndComposite(type);
                }
            }
            // cutout layers are drawn by the opaque pass
        } catch (final RuntimeException | LinkageError e) {
            MetalRenderMod.LOGGER.error("MetalRender failed while rendering terrain; falling back to vanilla", e);
            MetalRenderClient.fail("render error: " + e);
            return false;
        }
        return true;
    }

    private static void begin(final PoseStack poseStack, final double camX, final double camY, final double camZ,
                              final Matrix4f projection) {
        final long start = System.nanoTime();
        drainPendingOps();
        final long ctx = MetalRenderClient.context();
        final SectionStore store = MetalRenderClient.store();
        final MetalRenderConfig config = MetalRenderClient.config();
        if (atlasDirty) {
            syncAtlas(ctx);
        }
        if (compositor == null) {
            compositor = GlCompositor.create();
        }
        final RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        final int outWidth = target.width;
        final int outHeight = target.height;
        final int sections = store.sectionHighWater();
        if (sections == 0) {
            return;
        }

        final float[] viewProj = FrameMath.multiply(toArray(projection), toArray(poseStack.last().pose()));
        final float[] planes = FrameMath.frustumPlanes(viewProj);
        // Same rounding as the native side, so the background copy matches the render size.
        final float scale = Math.max(0.5f, Math.min(1.0f, config.resolutionScale));
        final int renderW = Math.max(1, (int) Math.round((double) outWidth * (double) scale));
        final int renderH = Math.max(1, (int) Math.round((double) outHeight * (double) scale));
        final int upscale = config.upscaler.nativeId;
        float[] drawViewProj = viewProj;
        ByteBuffer temporal = null;
        if (upscale == MetalNative.UPSCALE_TEMPORAL) {
            final TemporalState.Frame frame = TEMPORAL_STATE.next(viewProj, camX, camY, camZ, renderW, renderH);
            drawViewProj = FrameMath.jitter(viewProj, frame.jitterX(), frame.jitterY(), renderW, renderH);
            FrameUniformWriter.writeTemporal(TEMPORAL, frame);
            temporal = TEMPORAL;
        } else {
            TEMPORAL_STATE.invalidate();
        }
        final float renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0f;
        FrameUniformWriter.writeFrame(FRAME, drawViewProj, planes, camX, camY, camZ, renderDistance,
                RenderSystem.getShaderFogColor(), RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(),
                RenderSystem.getShaderFogShape().getIndex());

        if (renderW != outWidth || renderH != outHeight || upscale == MetalNative.UPSCALE_TEMPORAL) {
            compositor.copyBackground(ctx, target, renderW, renderH);
        }
        final int slot = MetalNative.beginOpaquePass(ctx, outWidth, outHeight, scale, config.meshShaders, upscale,
                FRAME, temporal, sections, store.meshletHighWater());
        if (slot < 0) {
            return;
        }
        // Upscaled silhouettes extend up to one source pixel past the nearest-upscaled coverage.
        inFlightEdge = MetalNative.getLastBackgroundUsed(ctx) ? Math.max(1, (int) Math.ceil(1.0f / scale)) : 0;
        inFlight = true;
        inFlightSince = System.nanoTime();
        PerfStats.beginNanos += inFlightSince - start;
        PerfStats.opaqueNanos += inFlightSince - start;
    }

    private static void finishAndComposite(final RenderType type) {
        final long ctx = MetalRenderClient.context();
        final long start = System.nanoTime();
        PerfStats.overlapNanos += start - inFlightSince;
        inFlight = false;
        final int slot = MetalNative.finishOpaquePass(ctx);
        final long waited = System.nanoTime();
        PerfStats.waitNanos += waited - start;
        settle(); // apply the store changes held back during the pass
        if (slot >= 0) {
            type.setupRenderState();
            final boolean composited = compositor.composite(ctx, slot, Minecraft.getInstance().getMainRenderTarget().height,
                    inFlightEdge);
            type.clearRenderState();
            if (!composited) {
                throw new IllegalStateException("could not attach the Metal IOSurface to OpenGL");
            }
        }
        if (++frames % 10 == 0 && MetalRenderClient.config().debugInfo) {
            MetalNative.readTelemetry(ctx, COUNTERS, GPU_MS);
        }
        PerfStats.opaqueNanos += System.nanoTime() - start;
        PerfStats.opaqueFrames++;
    }

    private static float[] toArray(final Matrix4f matrix) {
        MATRIX.clear();
        matrix.store(MATRIX);
        final float[] out = new float[16];
        MATRIX.get(0, out);
        return out;
    }

    // ------------------------------------------------------------------ diagnostics

    public static List<String> debugLines() {
        final List<String> lines = new ArrayList<>();
        if (!MetalRenderClient.config().debugInfo) {
            return lines;
        }
        lines.add("");
        lines.add("[MetalRender] " + MetalRenderClient.status());
        final long ctx = MetalRenderClient.context();
        final SectionStore store = MetalRenderClient.store();
        if (ctx == 0 || store == null || !MetalRenderClient.isActive()) {
            return lines;
        }
        final MetalRenderConfig config = MetalRenderClient.config();
        lines.add(String.format("Upscale: %s at %.0f%%, mesh shaders %s",
                MetalNative.upscaleName(MetalNative.getLastUpscaleMode(ctx)), config.resolutionScale * 100.0f,
                config.meshShaders ? "on" : "off"));
        final boolean meshPath = MetalNative.getActiveTier(ctx) != MetalNative.TIER_COMPUTE_ICB && config.meshShaders;
        if (meshPath) {
            lines.add(String.format("Sections: %d resident; culled per meshlet, %d of %d meshlets drawn",
                    store.residentSections(), COUNTERS[7], store.usedMeshlets()));
        } else {
            lines.add(String.format("Sections: %d resident, %d visible, %d frustum-culled",
                    store.residentSections(), COUNTERS[0], COUNTERS[1]));
        }
        lines.add(String.format("Quads: %d solid, %d cutout, %d meshlets (translucent terrain: vanilla)",
                COUNTERS[4], COUNTERS[5], COUNTERS[7]));
        lines.add(String.format("GPU: %.2f ms opaque, arena %d/%d MB",
                GPU_MS[0], (long) store.usedVertices() * 24L >> 20, (long) store.vertexCapacity() * 24L >> 20));
        return lines;
    }
}
