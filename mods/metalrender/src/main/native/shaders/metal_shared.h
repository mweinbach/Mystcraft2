#ifndef METALRENDER_SHARED_H
#define METALRENDER_SHARED_H

#ifdef __METAL_VERSION__
#include <metal_stdlib>
using namespace metal;
typedef int16_t  mr_i16;
typedef uint16_t mr_u16;
typedef int8_t   mr_i8;
typedef uint8_t  mr_u8;
typedef int32_t  mr_i32;
typedef uint32_t mr_u32;
typedef float4x4 mr_mat4;
typedef float4   mr_vec4;
typedef int4     mr_ivec4;
typedef uint4    mr_uvec4;
#else
#include <stdint.h>
#include <simd/simd.h>
typedef int16_t       mr_i16;
typedef uint16_t      mr_u16;
typedef int8_t        mr_i8;
typedef uint8_t       mr_u8;
typedef int32_t       mr_i32;
typedef uint32_t      mr_u32;
typedef simd_float4x4 mr_mat4;
typedef simd_float4   mr_vec4;
typedef simd_int4     mr_ivec4;
typedef simd_uint4    mr_uvec4;
#endif

// Layer modes stored in MetalTerrainVertex::mode
#define MR_MODE_SOLID         0u
#define MR_MODE_CUTOUT_MIPPED 1u
#define MR_MODE_CUTOUT        2u
#define MR_MODE_TRANSLUCENT   3u
#define MR_MODE_TRIPWIRE      4u

// SectionGpuMeta flags
#define MR_SECTION_ACTIVE          (1u << 0)
#define MR_SECTION_HAS_TRANSLUCENT (1u << 1)
#define MR_SECTION_CAMERA_INSIDE   (1u << 2)

// 24-byte compact terrain vertex (4-byte aligned, identical across C++ and Metal).
struct MetalTerrainVertex {
    mr_i16 px;          // 0..1: quantized (localX - 8.0f) * 1024.0f
    mr_i16 py;          // 2..3: quantized (localY - 8.0f) * 1024.0f
    mr_i16 pz;          // 4..5: quantized (localZ - 8.0f) * 1024.0f
    mr_u8  blockLight;  // 6: [0..240]
    mr_u8  skyLight;    // 7: [0..240]
    mr_u8  r;           // 8
    mr_u8  g;           // 9
    mr_u8  b;           // 10
    mr_u8  a;           // 11
    float  u;           // 12..15: block atlas U
    float  v;           // 16..19: block atlas V
    mr_i8  nx;          // 20
    mr_i8  ny;          // 21
    mr_i8  nz;          // 22
    mr_u8  mode;        // 23: MR_MODE_*
};

// 96-byte per-section GPU metadata record (16-byte aligned).
struct SectionGpuMeta {
    mr_i32 originX;             // 0..3: block X of section origin
    mr_i32 originY;             // 4..7: block Y of section origin
    mr_i32 originZ;             // 8..11: block Z of section origin
    mr_u32 flags;               // 12..15: MR_SECTION_*
    float  aabbMinX;            // 16..19: local min X relative to origin
    float  aabbMinY;            // 20..23
    float  aabbMinZ;            // 24..27
    float  pad0;                // 28..31
    float  aabbMaxX;            // 32..35: local max X relative to origin
    float  aabbMaxY;            // 36..39
    float  aabbMaxZ;            // 40..43
    float  pad1;                // 44..47
    mr_u32 solidBaseVertex;     // 48..51
    mr_u32 solidQuadCount;      // 52..55
    mr_u32 cutoutBaseVertex;    // 56..59
    mr_u32 cutoutQuadCount;     // 60..63
    mr_u32 lodSolidBaseVertex;  // 64..67
    mr_u32 lodSolidQuadCount;   // 68..71
    mr_u32 lodCutoutBaseVertex; // 72..75
    mr_u32 lodCutoutQuadCount;  // 76..79
    mr_u32 transBaseVertex;     // 80..83
    mr_u32 transQuadCount;      // 84..87
    mr_u32 meshletStart;        // 88..91
    mr_u32 meshletCount;        // 92..95
};

// 32-byte Metal 3 meshlet descriptor (up to 32 quads = 128 vertices, 64 triangles).
struct MeshletGpuMeta {
    float  localCenterX; // 0..3: center relative to section origin
    float  localCenterY; // 4..7
    float  localCenterZ; // 8..11
    float  radius;       // 12..15: bounding sphere radius
    mr_u32 sectionIndex; // 16..19
    mr_u32 baseVertex;   // 20..23: first vertex in arena
    mr_u16 quadCount;    // 24..25: 1..32 quads
    mr_u8  isCutout;     // 26: 0 = solid, 1 = cutout
    mr_u8  isLod;        // 27: 0 = full detail, 1 = LOD
    mr_i8  coneAxisX;    // 28
    mr_i8  coneAxisY;    // 29
    mr_i8  coneAxisZ;    // 30
    mr_i8  coneCutoff;   // 31: quantized cone cutoff (-127 disables cone cull)
};

// 272-byte camera-relative frame uniform block (16-byte aligned).
struct FrameUniforms {
    mr_mat4  viewProjMatrix;    // 0..63: OpenGL Proj * View (camera at origin)
    mr_vec4  frustumPlanes[6];  // 64..159: camera-relative frustum planes (nx,ny,nz,d)
    mr_ivec4 cameraBlockPos;    // 160..175: integer floor(cameraPos.xyz), w=unused
    mr_vec4  cameraFracPos;     // 176..191: frac(cameraPos.xyz), w=renderDistBlocks
    mr_vec4  fogColor;          // 192..207: (r, g, b, a)
    mr_vec4  fogParams;         // 208..223: (fogStart, fogEnd, fogShape [0=sphere,1=cyl], reserved)
    mr_uvec4 counts;            // 224..239: (sectionSlotCap, totalMeshlets, hizBaseW, hizBaseH)
    mr_uvec4 lodAndFlags;       // 240..255: (lodEnabled, lodNearChunks, lodFarChunks, hizEnabled)
    mr_vec4  screenParams;      // 256..271: (renderW, renderH, hizMipCount, distantScale)
};

// GPU atomic telemetry counters read back by /metalrender status and F3 HUD.
struct GpuCullStats {
    mr_u32 visibleSections;
    mr_u32 frustumCulledSections;
    mr_u32 occludedSections;
    mr_u32 lodSections;
    mr_u32 drawnSolidQuads;
    mr_u32 drawnCutoutQuads;
    mr_u32 drawnTransQuads;
    mr_u32 visibleMeshlets;
};

// 160-byte camera reprojection block for MetalFX temporal upscaling. Terrain is static, so
// per-pixel motion is entirely camera motion and can be rebuilt from depth alone.
struct TemporalUniforms {
    mr_mat4 invViewProj;  // 0..63: inverse of the UNJITTERED current Proj * View (camera at origin)
    mr_mat4 prevViewProj; // 64..127: UNJITTERED previous Proj * View (previous camera at origin)
    mr_vec4 cameraDelta;  // 128..143: currentCameraPos - previousCameraPos (xyz)
    mr_vec4 jitter;       // 144..159: (jitterPxX right+, jitterPxY down+, reset, reserved)
};

#ifndef __METAL_VERSION__
static_assert(sizeof(TemporalUniforms) == 160, "TemporalUniforms must be 160 bytes");
static_assert(sizeof(MetalTerrainVertex) == 24, "MetalTerrainVertex must be 24 bytes");
static_assert(sizeof(SectionGpuMeta) == 96, "SectionGpuMeta must be 96 bytes");
static_assert(sizeof(MeshletGpuMeta) == 32, "MeshletGpuMeta must be 32 bytes");
static_assert(sizeof(FrameUniforms) == 272, "FrameUniforms must be 272 bytes");
static_assert(sizeof(GpuCullStats) == 32, "GpuCullStats must be 32 bytes");
#endif

#ifdef __METAL_VERSION__
// Metal 3 bindless texture table (fragment buffer 3). The host writes MTLResourceIDs straight into
// it, which keeps the fragment shaders legal for indirect command buffers and Metal 4 argument tables.
struct TerrainTextures {
    texture2d<float, access::sample> atlas    [[id(0)]];
    texture2d<float, access::sample> lightmap [[id(1)]];
};

// Shared vertex-to-fragment interpolants for both ICB and Metal 3 Mesh pipelines.
struct TerrainRasterData {
    float4 position [[position]];
    float4 color;
    float2 uv;
    float2 lightUv;
    float  sphericalDist;
    float  cylindricalDist;
    uint   mode [[flat]];
};

inline float3 mr_cameraRelativeOrigin(constant SectionGpuMeta& sec, constant FrameUniforms& u) {
    int3 blockDelta = int3(sec.originX, sec.originY, sec.originZ) - u.cameraBlockPos.xyz;
    return float3(blockDelta) - u.cameraFracPos.xyz;
}

inline float4 mr_glClipToMetalClip(float4 glClip) {
    // Map OpenGL clip Z [-w, +w] identically onto Metal [0, +w] so NDC Z == OpenGL gl_FragDepth!
    return float4(glClip.x, glClip.y, 0.5f * glClip.z + 0.5f * glClip.w, glClip.w);
}

inline TerrainRasterData mr_evalTerrainVertex(
    device const MetalTerrainVertex& vtx,
    float3 sectionCamRelOrigin,
    constant FrameUniforms& u
) {
    TerrainRasterData out;
    const float invScale = 1.0f / 1024.0f;
    float3 localPos = float3(8.0f) + float3(float(vtx.px), float(vtx.py), float(vtx.pz)) * invScale;
    float3 camRelPos = sectionCamRelOrigin + localPos;

    float4 glClip = u.viewProjMatrix * float4(camRelPos, 1.0f);
    out.position = mr_glClipToMetalClip(glClip);
    out.color = float4(vtx.r, vtx.g, vtx.b, vtx.a) * (1.0f / 255.0f);
    out.uv = float2(vtx.u, vtx.v);
    // Match vanilla 1.19.2 minecraft_sample_lightmap: clamp(uv2 / 256.0 + 0.5 / 16.0) = (uv2 + 8.0) / 256.0
    out.lightUv = clamp((float2(float(vtx.blockLight), float(vtx.skyLight)) + 8.0f) * (1.0f / 256.0f),
                        0.5f / 16.0f, 15.5f / 16.0f);
    out.sphericalDist = length(camRelPos);
    out.cylindricalDist = max(length(camRelPos.xz), abs(camRelPos.y));
    out.mode = uint(vtx.mode);
    return out;
}

inline float4 mr_applyVanillaFog(float4 inColor, float sphericalDist, float cylindricalDist, constant FrameUniforms& u) {
    float fogStart = u.fogParams.x;
    float fogEnd = u.fogParams.y;
    if (fogEnd <= fogStart) {
        return float4(u.fogColor.rgb, inColor.a);
    }
    float dist = (u.fogParams.z > 0.5f) ? cylindricalDist : sphericalDist;
    if (dist <= fogStart) {
        return inColor;
    }
    float fogValue = dist < fogEnd ? smoothstep(fogStart, fogEnd, dist) : 1.0f;
    // Vanilla 1.19.2 linear_fog uses linear ramp clamp((dist - fogStart) / (fogEnd - fogStart), 0, 1) * fogColor.a
    float linearFactor = clamp((dist - fogStart) / (fogEnd - fogStart), 0.0f, 1.0f) * u.fogColor.a;
    (void)fogValue;
    return float4(mix(inColor.rgb, u.fogColor.rgb, linearFactor), inColor.a);
}
#endif

#endif // METALRENDER_SHARED_H
