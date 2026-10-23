#include "metal_shared.h"

// Metal 3 Object + Mesh Shader pipeline (-std=metal3.0).
// Each meshlet holds up to 32 quads = 128 vertices and 64 triangles.
#define MR_MAX_MESHLET_VERTS 128
#define MR_MAX_MESHLET_PRIMS 64
#define MR_OBJECT_TG_SIZE    32

struct MeshletPayload {
    uint meshletIndices[MR_OBJECT_TG_SIZE];
};

using TerrainMeshType = metal::mesh<
    TerrainRasterData,
    void,
    MR_MAX_MESHLET_VERTS,
    MR_MAX_MESHLET_PRIMS,
    metal::topology::triangle
>;

inline bool mr_sphereInFrustum(float3 center, float radius, constant FrameUniforms& u) {
    for (uint i = 0; i < 6; ++i) {
        float4 plane = u.frustumPlanes[i];
        if (dot(plane.xyz, center) + plane.w < -radius) {
            return false;
        }
    }
    return true;
}

inline bool mr_sphereOccludedByHiZ(
    float3 center,
    float radius,
    constant FrameUniforms& u,
    texture2d<float, access::read> hizPyramid
) {
    if (u.lodAndFlags.w == 0u || u.counts.z == 0u || u.counts.w == 0u) {
        return false;
    }
    float distSq = dot(center, center);
    if (distSq <= (radius + 2.5f) * (radius + 2.5f)) {
        return false;
    }
    float3 bmin = center - float3(radius);
    float3 bmax = center + float3(radius);

    float2 ndcMin = float2(1e9f);
    float2 ndcMax = float2(-1e9f);
    float minDepth = 1.0f;

    for (uint corner = 0; corner < 8; ++corner) {
        float3 p = float3(
            (corner & 1u) ? bmax.x : bmin.x,
            (corner & 2u) ? bmax.y : bmin.y,
            (corner & 4u) ? bmax.z : bmin.z
        );
        float4 glClip = u.viewProjMatrix * float4(p, 1.0f);
        if (glClip.w <= 0.05f) {
            return false;
        }
        float4 mClip = mr_glClipToMetalClip(glClip);
        float invW = 1.0f / mClip.w;
        float2 ndc = mClip.xy * invW;
        float z = mClip.z * invW;
        ndcMin = min(ndcMin, ndc);
        ndcMax = max(ndcMax, ndc);
        minDepth = min(minDepth, z);
    }

    float2 uvMin = clamp(float2(ndcMin.x * 0.5f + 0.5f, 0.5f - ndcMax.y * 0.5f), 0.0f, 1.0f);
    float2 uvMax = clamp(float2(ndcMax.x * 0.5f + 0.5f, 0.5f - ndcMin.y * 0.5f), 0.0f, 1.0f);

    float2 hizSize = float2(float(u.counts.z), float(u.counts.w));
    float2 boxPixels = max((uvMax - uvMin) * hizSize, float2(1.0f));
    uint maxMip = uint(max(0.0f, u.screenParams.z - 1.0f));
    uint mip = min(uint(ceil(log2(max(boxPixels.x, boxPixels.y)))), maxMip);

    uint mipW = max(1u, u.counts.z >> mip);
    uint mipH = max(1u, u.counts.w >> mip);
    uint2 p00 = min(uint2(uvMin * float2(float(mipW), float(mipH))), uint2(mipW - 1u, mipH - 1u));
    uint2 p11 = min(uint2(uvMax * float2(float(mipW), float(mipH))), uint2(mipW - 1u, mipH - 1u));

    float d0 = hizPyramid.read(uint2(p00.x, p00.y), mip).r;
    float d1 = hizPyramid.read(uint2(p11.x, p00.y), mip).r;
    float d2 = hizPyramid.read(uint2(p00.x, p11.y), mip).r;
    float d3 = hizPyramid.read(uint2(p11.x, p11.y), mip).r;
    float occluderMaxZ = max(max(d0, d1), max(d2, d3));
    return (minDepth > occluderMaxZ + 0.0015f);
}

[[object, max_total_threads_per_threadgroup(MR_OBJECT_TG_SIZE), max_total_threadgroups_per_mesh_grid(MR_OBJECT_TG_SIZE)]]
void terrainObjectStage(
    object_data MeshletPayload& payload         [[payload]],
    mesh_grid_properties mgp,
    constant MeshletGpuMeta* meshlets           [[buffer(1)]],
    constant SectionGpuMeta* sections           [[buffer(2)]],
    constant FrameUniforms& uniforms            [[buffer(3)]],
    constant uint& targetCutoutFlag             [[buffer(4)]],
    device atomic_uint* statsWords              [[buffer(5)]],
    texture2d<float, access::read> hizPyramid   [[texture(0)]],
    uint tid                                    [[thread_index_in_threadgroup]],
    uint gid                                    [[thread_position_in_grid]]
) {
    threadgroup atomic_uint visibleCount;
    if (tid == 0u) {
        atomic_store_explicit(&visibleCount, 0u, memory_order_relaxed);
    }
    threadgroup_barrier(mem_flags::mem_threadgroup);

    bool visible = false;
    if (gid < uniforms.counts.y) {
        constant MeshletGpuMeta& m = meshlets[gid];
        if (uint(m.isCutout) == targetCutoutFlag && m.quadCount > 0u && m.sectionIndex < uniforms.counts.x) {
            constant SectionGpuMeta& sec = sections[m.sectionIndex];
            if ((sec.flags & MR_SECTION_ACTIVE) != 0u) {
                float3 camRelOrigin = mr_cameraRelativeOrigin(sec, uniforms);
                float3 secCenter = camRelOrigin + float3(8.0f);
                float horizDistChunks = length(secCenter.xz) * (1.0f / 16.0f);
                float maxChunks = uniforms.cameraFracPos.w * (1.0f / 16.0f) + 2.0f;

                bool secHasLod = (sec.lodSolidQuadCount + sec.lodCutoutQuadCount) > 0u;
                bool wantLod = (uniforms.lodAndFlags.x != 0u) &&
                               (horizDistChunks >= float(uniforms.lodAndFlags.y)) &&
                               secHasLod;

                if (horizDistChunks <= maxChunks && (bool(m.isLod) == wantLod)) {
                    float3 mCenter = camRelOrigin + float3(m.localCenterX, m.localCenterY, m.localCenterZ);
                    if (mr_sphereInFrustum(mCenter, m.radius, uniforms)) {
                        // Cone backface cull when coneCutoff > -126
                        bool coneCulled = false;
                        if (m.coneCutoff > -126) {
                            float3 coneAxis = normalize(float3(float(m.coneAxisX), float(m.coneAxisY), float(m.coneAxisZ)));
                            float dist = length(mCenter);
                            if (dist > m.radius + 0.001f) {
                                float cutoff = float(m.coneCutoff) * (1.0f / 127.0f);
                                // View ray from camera (0,0,0) to meshlet center is normalize(mCenter).
                                if (dot(mCenter / dist, coneAxis) >= cutoff + (m.radius / dist)) {
                                    coneCulled = true;
                                }
                            }
                        }
                        if (!coneCulled && !mr_sphereOccludedByHiZ(mCenter, m.radius, uniforms, hizPyramid)) {
                            visible = true;
                        }
                    }
                }
            }
        }
    }

    if (visible) {
        uint slot = atomic_fetch_add_explicit(&visibleCount, 1u, memory_order_relaxed);
        payload.meshletIndices[slot] = gid;
        constant MeshletGpuMeta& m = meshlets[gid];
        uint qCount = uint(m.quadCount);
        atomic_fetch_add_explicit(&statsWords[targetCutoutFlag == 0u ? 4u : 5u], qCount, memory_order_relaxed);
        atomic_fetch_add_explicit(&statsWords[7u], 1u, memory_order_relaxed);
    }

    threadgroup_barrier(mem_flags::mem_threadgroup);
    if (tid == 0u) {
        uint count = atomic_load_explicit(&visibleCount, memory_order_relaxed);
        mgp.set_threadgroups_per_grid(uint3(count, 1u, 1u));
    }
}

[[mesh, max_total_threads_per_threadgroup(64)]]
void terrainMeshStage(
    TerrainMeshType outMesh,
    const object_data MeshletPayload& payload [[payload]],
    device const MetalTerrainVertex* arena    [[buffer(0)]],
    constant MeshletGpuMeta* meshlets         [[buffer(1)]],
    constant SectionGpuMeta* sections         [[buffer(2)]],
    constant FrameUniforms& uniforms          [[buffer(3)]],
    uint tid                                  [[thread_index_in_threadgroup]],
    uint tgid                                 [[threadgroup_position_in_grid]]
) {
    uint meshletIdx = payload.meshletIndices[tgid];
    constant MeshletGpuMeta& m = meshlets[meshletIdx];
    constant SectionGpuMeta& sec = sections[m.sectionIndex];
    float3 camRelOrigin = mr_cameraRelativeOrigin(sec, uniforms);

    uint quadCount = min(uint(m.quadCount), 32u);
    uint vertCount = quadCount * 4u;
    uint primCount = quadCount * 2u;

    if (tid == 0u) {
        outMesh.set_primitive_count(primCount);
    }

    // 64 threads emit up to 128 vertices (2 vertices per thread).
    uint v0 = tid * 2u;
    if (v0 < vertCount) {
        outMesh.set_vertex(v0, mr_evalTerrainVertex(arena[m.baseVertex + v0], camRelOrigin, uniforms));
    }
    uint v1 = v0 + 1u;
    if (v1 < vertCount) {
        outMesh.set_vertex(v1, mr_evalTerrainVertex(arena[m.baseVertex + v1], camRelOrigin, uniforms));
    }

    // 64 threads emit up to 64 triangles (1 triangle per thread).
    if (tid < primCount) {
        uint q = tid >> 1u;
        uint base = q * 4u;
        if ((tid & 1u) == 0u) {
            outMesh.set_index(tid * 3u + 0u, base + 0u);
            outMesh.set_index(tid * 3u + 1u, base + 1u);
            outMesh.set_index(tid * 3u + 2u, base + 2u);
        } else {
            outMesh.set_index(tid * 3u + 0u, base + 2u);
            outMesh.set_index(tid * 3u + 1u, base + 3u);
            outMesh.set_index(tid * 3u + 2u, base + 0u);
        }
    }
}
