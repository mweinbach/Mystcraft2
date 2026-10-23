/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.nativebridge;

import java.nio.ByteBuffer;

/**
 * JNI surface of {@code libmetalrender.dylib} ({@code src/main/native/metal_bridge.mm}). Load the
 * library with {@link NativeLibraryLoader} before calling anything here. Tier and upscale constants
 * mirror {@code metal_bridge_internal.h}.
 */
public final class MetalNative {

    public static final int TIER_AUTO = 0;
    public static final int TIER_COMPUTE_ICB = 1;
    public static final int TIER_METAL3_MESH = 2;
    public static final int TIER_METAL4 = 3;

    public static final int UPSCALE_NONE = 0;
    public static final int UPSCALE_SPATIAL = 1;
    public static final int UPSCALE_TEMPORAL = 2;

    private MetalNative() {
    }

    public static String tierName(final int tier) {
        return switch (tier) {
            case TIER_COMPUTE_ICB -> "Compute+ICB";
            case TIER_METAL3_MESH -> "Metal 3 mesh shaders";
            case TIER_METAL4 -> "Metal 4";
            default -> "none";
        };
    }

    public static String upscaleName(final int mode) {
        return switch (mode) {
            case UPSCALE_SPATIAL -> "MetalFX spatial";
            case UPSCALE_TEMPORAL -> "MetalFX temporal";
            default -> "off";
        };
    }

    public static native boolean isAvailable();

    /** {@code key=value;key=value;...} describing Metal, Metal 4 and MetalFX support. */
    public static native String probeCapabilities();

    /** Returns a context handle, or 0 when Metal is unusable. */
    public static native long initContext(String metallibDir, int requestedTier, int vertexCapacity,
                                          int sectionCapacity, int meshletCapacity);

    public static native void destroyContext(long ctx);

    public static native int getActiveTier(long ctx);

    public static native int getLastUpscaleMode(long ctx);

    /** {@code name|unified|apple7|apple8|apple9|mesh|icb|metalfxSpatial|workingSetMB|tier|library|metalfxTemporal|mtl4fx|residency}. */
    public static native String queryDeviceInfo(long ctx);

    public static native ByteBuffer getVertexArenaBuffer(long ctx);

    public static native boolean resizeVertexArena(long ctx, int newVertexCapacity);

    public static native ByteBuffer getSectionMetaBuffer(long ctx);

    public static native boolean resizeSectionMeta(long ctx, int newSectionCapacity);

    public static native ByteBuffer getMeshletMetaBuffer(long ctx);

    public static native boolean resizeMeshletMeta(long ctx, int newMeshletCapacity);

    public static native void ensurePatternQuadCapacity(long ctx, int quads);

    public static native void initAtlasTexture(long ctx, int width, int height, int mipLevels);

    public static native void uploadAtlasRegion(long ctx, int mipLevel, int dstX, int dstY, int srcX, int srcY,
                                                int width, int height, int srcRowPixels, long pixels);

    /** 16x16 RGBA8 lightmap. */
    public static native void uploadLightmap(long ctx, long pixels);

    /**
     * Binds a render-resolution IOSurface to {@code glColorTexture} (a GL_TEXTURE_RECTANGLE) and
     * marks it as the background of the next opaque pass. Copy the OpenGL frame into that texture
     * and {@code glFlush} before {@link #beginOpaquePass}; scaled frames fill pixels without
     * terrain from it before upscaling.
     */
    public static native boolean prepareBackground(long ctx, int glColorTexture, int width, int height);

    /** Whether the last opaque pass filled empty pixels from the background copy. */
    public static native boolean getLastBackgroundUsed(long ctx);

    /** Renders solid + cutout terrain into an IOSurface ring slot; returns the slot or -1. */
    public static native int renderOpaquePass(long ctx, int outWidth, int outHeight, float resolutionScale,
                                              boolean useMeshShaders, int upscaleMode, ByteBuffer frameUniforms,
                                              ByteBuffer temporalUniforms, int sectionCount, int meshletCount);

    /**
     * Encodes and commits the opaque pass without waiting; returns its slot or -1. Until
     * {@link #finishOpaquePass} the arena, section and meshlet buffers must not change.
     */
    public static native int beginOpaquePass(long ctx, int outWidth, int outHeight, float resolutionScale,
                                             boolean useMeshShaders, int upscaleMode, ByteBuffer frameUniforms,
                                             ByteBuffer temporalUniforms, int sectionCount, int meshletCount);

    /** Waits for the pass from {@link #beginOpaquePass}; returns its slot, or -1 if none is in flight. */
    public static native int finishOpaquePass(long ctx);

    /** Attaches a slot's IOSurfaces to two GL_TEXTURE_RECTANGLE textures on the current CGL context. */
    public static native boolean bindIOSurfaceToGLTexture(long ctx, int slot, boolean translucent,
                                                          int glColorTexture, int glDepthTexture);

    public static native long createHeadlessCGLContext();

    public static native void destroyHeadlessCGLContext(long cgl);

    public static native int readSurfaceBgraPixel(long ctx, int slot, boolean translucent, int x, int y);

    public static native float readSurfaceDepthPixel(long ctx, int slot, boolean translucent, int x, int y);

    /**
     * counters: visible sections, frustum-culled, occluded, LOD, solid quads, cutout quads,
     * translucent quads, visible meshlets. gpuMs: opaque, translucent.
     */
    public static native void readTelemetry(long ctx, int[] counters, float[] gpuMs);
}
