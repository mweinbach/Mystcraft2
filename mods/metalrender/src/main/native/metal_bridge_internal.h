// Internal C API behind the JNI layer. The JNI functions in metal_bridge.mm are thin wrappers over
// these, and the native GPU self-test (selftest/metal_selftest.mm) drives them directly.
#pragma once

#include <IOSurface/IOSurfaceRef.h>
#include <stddef.h>
#include <stdint.h>

#include "shaders/metal_shared.h"

// Keep in sync with ports.metalrender.nativebridge.MetalNative.
enum {
    MR_TIER_AUTO = 0,
    MR_TIER_COMPUTE_ICB = 1, // Metal 2.x-class: GPU culling into indirect command buffers
    MR_TIER_METAL3_MESH = 2, // Metal 3: object + mesh shaders
    MR_TIER_METAL4 = 3,      // Metal 4: MTL4 queue/allocators/argument tables + residency sets + mesh shaders
};
enum {
    MR_UPSCALE_NONE = 0,     // bilinear compute upscale when resolution scale < 1
    MR_UPSCALE_SPATIAL = 1,  // MetalFX spatial
    MR_UPSCALE_TEMPORAL = 2, // MetalFX temporal (jitter + camera motion vectors + depth)
};

#ifdef __cplusplus
extern "C" {
#endif

// requestedTier: 0 auto, 1 compute+ICB, 2 Metal 3 mesh, 3 Metal 4. Returns NULL if Metal is unusable.
void* mrCreateContext(const char* metallibDir, int requestedTier, int vertexCap, int sectionCap, int meshletCap);
void mrDestroyContext(void* ctx);

int mrActiveTier(void* ctx);
const char* mrLibraryName(void* ctx);
int mrLastUpscaleUsed(void* ctx);
void mrSetJitterSign(void* ctx, float sign);

MetalTerrainVertex* mrVertexArena(void* ctx, uint32_t* capacity);
SectionGpuMeta* mrSectionMeta(void* ctx, uint32_t* capacity);
MeshletGpuMeta* mrMeshletMeta(void* ctx, uint32_t* capacity);
int mrEnsurePatternQuads(void* ctx, uint32_t quads);
void mrSetAtlasRgba(void* ctx, int width, int height, const void* rgbaPixels);

// upscaleMode: 0 none, 1 MetalFX spatial, 2 MetalFX temporal (needs temporal uniforms).
// Returns the IOSurface ring slot written, or -1.
int mrRenderOpaque(void* ctx, int outW, int outH, float resolutionScale, int useMeshShaders, int upscaleMode,
                   const struct FrameUniforms* uniforms, const struct TemporalUniforms* temporal,
                   int activeSectionCap, int totalMeshlets);
int mrRenderTranslucent(void* ctx, int slot, int outW, int outH, const struct FrameUniforms* uniforms,
                        const uint32_t* sortedSectionIndices, int count);

IOSurfaceRef mrSurface(void* ctx, int slot, int translucent, int depth);
void mrReadStats(void* ctx, struct GpuCullStats* stats, float* opaqueGpuMs, float* transGpuMs);

// Writes "key=value;key=value;..." describing Metal / Metal 4 / MetalFX support on this machine.
size_t mrProbeCapabilities(char* out, size_t outLen);

#ifdef __cplusplus
}
#endif
