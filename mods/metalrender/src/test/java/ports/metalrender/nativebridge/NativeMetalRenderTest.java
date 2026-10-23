/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.nativebridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ports.metalrender.render.FrameMath;
import ports.metalrender.render.FrameUniformWriter;
import ports.metalrender.render.TemporalState;
import ports.metalrender.terrain.PackedLayer;
import ports.metalrender.terrain.SectionSlot;
import ports.metalrender.terrain.SectionStore;
import ports.metalrender.terrain.TerrainLayer;
import ports.metalrender.terrain.TerrainVertexPacker;

/**
 * Drives the real dylib through the Java declarations, Java packer and Java section store on every
 * tier this Mac supports. Skipped on other platforms. Proves the JNI signatures, buffer byte order
 * and data layouts agree with the native engine.
 */
class NativeMetalRenderTest {

    private static final int SIZE = 64;
    private static Path nativeDir;

    @BeforeAll
    static void loadNatives() throws Exception {
        assumeTrue(NativeLibraryLoader.isMacOs(), "Metal is macOS-only");
        nativeDir = NativeLibraryLoader.load(Path.of("natives").toAbsolutePath());
        assumeTrue(MetalNative.isAvailable(), "no Metal device");
    }

    /** One vanilla BLOCK-format quad, corners counter-clockwise as seen from the front. */
    private static ByteBuffer quad(final float[][] corners, final int r, final int g, final int b, final int a) {
        final ByteBuffer buf = ByteBuffer.allocate(4 * TerrainVertexPacker.SOURCE_STRIDE).order(ByteOrder.LITTLE_ENDIAN);
        final float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int v = 0; v < 4; v++) {
            final int at = v * TerrainVertexPacker.SOURCE_STRIDE;
            buf.putFloat(at, corners[v][0]).putFloat(at + 4, corners[v][1]).putFloat(at + 8, corners[v][2]);
            buf.put(at + 12, (byte) r).put(at + 13, (byte) g).put(at + 14, (byte) b).put(at + 15, (byte) a);
            buf.putFloat(at + 16, uv[v][0]).putFloat(at + 20, uv[v][1]);
            buf.putShort(at + 24, (short) 240).putShort(at + 26, (short) 240);
            buf.put(at + 30, (byte) 127);
        }
        return buf;
    }

    private static sun.misc.Unsafe unsafe() throws ReflectiveOperationException {
        final Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }

    private static int channel(final int bgra, final int shift) {
        return (bgra >>> shift) & 0xFF;
    }

    @Test
    void probeReportsThisMac() {
        final HardwareCapabilities caps = HardwareCapabilities.parse(MetalNative.probeCapabilities());
        assertTrue(caps.metal());
        assertNotEquals("unknown", caps.device());
    }

    @Test
    void rendersJavaPackedSectionsOnEverySupportedTier() throws Exception {
        final HardwareCapabilities caps = HardwareCapabilities.parse(MetalNative.probeCapabilities());
        int tested = 0;
        for (final int tier : new int[] {MetalNative.TIER_COMPUTE_ICB, MetalNative.TIER_METAL3_MESH, MetalNative.TIER_METAL4}) {
            if (tier == MetalNative.TIER_METAL3_MESH && !caps.meshShaders() || tier == MetalNative.TIER_METAL4 && !caps.metal4()) {
                continue;
            }
            final long ctx = MetalNative.initContext(nativeDir.toString(), tier, 4096, 16, 64);
            assertNotEquals(0L, ctx, "context for tier " + tier);
            try {
                assertEquals(tier, MetalNative.getActiveTier(ctx), "requested tier was not honoured");
                renderScene(ctx, caps, tier != MetalNative.TIER_COMPUTE_ICB);
                tested++;
            } finally {
                MetalNative.destroyContext(ctx);
            }
        }
        assertTrue(tested > 0);
    }

    private void renderScene(final long ctx, final HardwareCapabilities caps, final boolean mesh) throws Exception {
        // 16x16 pure red atlas, uploaded from native memory like the game's GL read-back.
        final sun.misc.Unsafe unsafe = unsafe();
        final long pixels = unsafe.allocateMemory(16 * 16 * 4);
        for (int i = 0; i < 256; i++) {
            unsafe.putInt(pixels + i * 4L, 0xFF0000FF); // little-endian bytes R=FF G=00 B=00 A=FF
        }
        MetalNative.initAtlasTexture(ctx, 16, 16, 1);
        MetalNative.uploadAtlasRegion(ctx, 0, 0, 0, 0, 0, 16, 16, 16, pixels);
        unsafe.freeMemory(pixels);

        // A white wall facing +Z at world z = 16.
        final SectionStore store = new SectionStore(new NativeArena(ctx));
        final SectionSlot slot = store.allocate(0, 0, 0, null);
        final PackedLayer wall = TerrainVertexPacker.pack(
                quad(new float[][] {{0, 0, 16}, {16, 0, 16}, {16, 16, 16}, {0, 16, 16}}, 255, 255, 255, 255),
                4, TerrainLayer.SOLID.mode);
        assertTrue(store.applyLayer(slot, TerrainLayer.SOLID, wall));

        // Camera at (8, 8, 40) looking down -Z.
        final float[] viewProj = perspective();
        final ByteBuffer frame = ByteBuffer.allocateDirect(FrameUniformWriter.FRAME_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        FrameUniformWriter.writeFrame(frame, viewProj, FrameMath.frustumPlanes(viewProj), 8.0, 8.0, 40.0, 256f,
                new float[] {0f, 0f, 0f, 1f}, 1000f, 2000f, 0);

        final int opaque = MetalNative.renderOpaquePass(ctx, SIZE, SIZE, 1.0f, mesh, MetalNative.UPSCALE_NONE, frame, null,
                store.sectionHighWater(), store.meshletHighWater());
        assertTrue(opaque >= 0);
        final int centre = MetalNative.readSurfaceBgraPixel(ctx, opaque, false, SIZE / 2, SIZE / 2);
        assertEquals(255, channel(centre, 24), "wall alpha");
        assertTrue(channel(centre, 16) > 200 && channel(centre, 8) < 40 && channel(centre, 0) < 40,
                "wall should be atlas red, got " + Integer.toHexString(centre));
        final float depth = MetalNative.readSurfaceDepthPixel(ctx, opaque, false, SIZE / 2, SIZE / 2);
        assertTrue(depth > 0.5f && depth < 1.0f, "window depth " + depth);
        assertEquals(0, channel(MetalNative.readSurfaceBgraPixel(ctx, opaque, false, 1, SIZE / 2), 24),
                "pixels outside the wall stay transparent");

        final int[] counters = new int[8];
        final float[] gpuMs = new float[2];
        MetalNative.readTelemetry(ctx, counters, gpuMs);
        assertTrue(counters[4] + counters[5] > 0, "no solid quads drawn");

        if (caps.metalFxTemporal()) {
            final TemporalState temporal = new TemporalState();
            final ByteBuffer t = ByteBuffer.allocateDirect(FrameUniformWriter.TEMPORAL_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            int slotUsed = -1;
            for (int i = 0; i < 4; i++) {
                final TemporalState.Frame f = temporal.next(viewProj, 8.0, 8.0, 40.0, SIZE / 2, SIZE / 2);
                FrameUniformWriter.writeFrame(frame, FrameMath.jitter(viewProj, f.jitterX(), f.jitterY(), SIZE / 2, SIZE / 2),
                        FrameMath.frustumPlanes(viewProj), 8.0, 8.0, 40.0, 256f, new float[] {0f, 0f, 0f, 1f},
                        1000f, 2000f, 0);
                FrameUniformWriter.writeTemporal(t, f);
                slotUsed = MetalNative.renderOpaquePass(ctx, SIZE, SIZE, 0.5f, mesh, MetalNative.UPSCALE_TEMPORAL, frame, t,
                        store.sectionHighWater(), store.meshletHighWater());
                assertTrue(slotUsed >= 0);
            }
            assertEquals(MetalNative.UPSCALE_TEMPORAL, MetalNative.getLastUpscaleMode(ctx));
            final int up = MetalNative.readSurfaceBgraPixel(ctx, slotUsed, false, SIZE / 2, SIZE / 2);
            assertTrue(channel(up, 16) > 200 && channel(up, 8) < 40, "MetalFX output " + Integer.toHexString(up));
        }
        store.releaseAll();
    }

    private static float[] perspective() {
        final float f = (float) (1.0 / Math.tan(Math.toRadians(70) / 2.0));
        final float near = 0.05f;
        final float far = 512f;
        final float[] m = new float[16];
        m[0] = f;
        m[5] = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1.0f;
        m[14] = 2.0f * far * near / (near - far);
        return m;
    }
}
