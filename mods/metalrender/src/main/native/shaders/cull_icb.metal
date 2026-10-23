#include "metal_shared.h"

struct IcbContainer {
    command_buffer solidIcb  [[id(0)]];
    command_buffer cutoutIcb [[id(1)]];
};

inline bool mr_aabbInFrustum(float3 bmin, float3 bmax, constant FrameUniforms& u) {
    for (uint i = 0; i < 6; ++i) {
        float4 plane = u.frustumPlanes[i];
        float3 pVertex = float3(
            plane.x >= 0.0f ? bmax.x : bmin.x,
            plane.y >= 0.0f ? bmax.y : bmin.y,
            plane.z >= 0.0f ? bmax.z : bmin.z
        );
        if (dot(plane.xyz, pVertex) + plane.w < 0.0f) {
            return false;
        }
    }
    return true;
}

// Conservative screen-space AABB test against previous-frame Hi-Z max-depth mip pyramid.
inline bool mr_aabbOccludedByHiZ(
    float3 bmin,
    float3 bmax,
    constant FrameUniforms& u,
    texture2d<float, access::read> hizPyramid
) {
    if (u.lodAndFlags.w == 0u || u.counts.z == 0u || u.counts.w == 0u) {
        return false;
    }
    // Never occlusion-cull if camera is inside or within 2 blocks of the AABB.
    if (all(bmin - 2.0f <= float3(0.0f)) && all(bmax + 2.0f >= float3(0.0f))) {
        return false;
    }

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
            // Corner crosses near plane -> conservatively visible.
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

    // Convert Metal NDC [-1..1] (with +Y up) to texture UV [0..1] (with +Y down).
    float2 uvMin = clamp(float2(ndcMin.x * 0.5f + 0.5f, 0.5f - ndcMax.y * 0.5f), 0.0f, 1.0f);
    float2 uvMax = clamp(float2(ndcMax.x * 0.5f + 0.5f, 0.5f - ndcMin.y * 0.5f), 0.0f, 1.0f);

    float2 hizSize = float2(float(u.counts.z), float(u.counts.w));
    float2 boxPixels = max((uvMax - uvMin) * hizSize, float2(1.0f));
    float maxSide = max(boxPixels.x, boxPixels.y);
    uint maxMip = uint(max(0.0f, u.screenParams.z - 1.0f));
    uint mip = min(uint(ceil(log2(maxSide))), maxMip);

    uint mipW = max(1u, u.counts.z >> mip);
    uint mipH = max(1u, u.counts.w >> mip);
    uint2 p00 = min(uint2(uvMin * float2(float(mipW), float(mipH))), uint2(mipW - 1u, mipH - 1u));
    uint2 p11 = min(uint2(uvMax * float2(float(mipW), float(mipH))), uint2(mipW - 1u, mipH - 1u));

    float d0 = hizPyramid.read(uint2(p00.x, p00.y), mip).r;
    float d1 = hizPyramid.read(uint2(p11.x, p00.y), mip).r;
    float d2 = hizPyramid.read(uint2(p00.x, p11.y), mip).r;
    float d3 = hizPyramid.read(uint2(p11.x, p11.y), mip).r;
    float occluderMaxZ = max(max(d0, d1), max(d2, d3));

    // Add small depth bias to prevent false occlusion during slight camera motion.
    return (minDepth > occluderMaxZ + 0.0015f);
}

kernel void cullAndEncodeSections(
    constant SectionGpuMeta* sections          [[buffer(0)]],
    constant FrameUniforms& uniforms           [[buffer(1)]],
    device uint* patternIndices                [[buffer(2)]],
    constant IcbContainer& icbs                [[buffer(3)]],
    device atomic_uint* statsWords             [[buffer(4)]],
    texture2d<float, access::read> hizPyramid  [[texture(0)]],
    uint gid                                   [[thread_position_in_grid]]
) {
    if (gid >= uniforms.counts.x) {
        return;
    }

    // Reset both ICB commands for this slot first so culled slots become no-ops.
    render_command solidCmd(icbs.solidIcb, gid);
    render_command cutoutCmd(icbs.cutoutIcb, gid);
    solidCmd.reset();
    cutoutCmd.reset();

    constant SectionGpuMeta& sec = sections[gid];
    if ((sec.flags & MR_SECTION_ACTIVE) == 0u) {
        return;
    }

    uint hasAnyOpaque = sec.solidQuadCount + sec.cutoutQuadCount;
    if (hasAnyOpaque == 0u) {
        return;
    }

    float3 camRelOrigin = mr_cameraRelativeOrigin(sec, uniforms);
    float3 bmin = camRelOrigin + float3(sec.aabbMinX, sec.aabbMinY, sec.aabbMinZ);
    float3 bmax = camRelOrigin + float3(sec.aabbMaxX, sec.aabbMaxY, sec.aabbMaxZ);

    // Distance limit check (cylindrical chunk render distance).
    float3 center = 0.5f * (bmin + bmax);
    float horizDistChunks = length(center.xz) * (1.0f / 16.0f);
    float maxChunks = uniforms.cameraFracPos.w * (1.0f / 16.0f) + 2.0f;
    if (horizDistChunks > maxChunks) {
        return;
    }

    if (!mr_aabbInFrustum(bmin, bmax, uniforms)) {
        atomic_fetch_add_explicit(&statsWords[1], 1u, memory_order_relaxed);
        return;
    }

    if (mr_aabbOccludedByHiZ(bmin, bmax, uniforms, hizPyramid)) {
        atomic_fetch_add_explicit(&statsWords[2], 1u, memory_order_relaxed);
        return;
    }

    atomic_fetch_add_explicit(&statsWords[0], 1u, memory_order_relaxed);

    bool useLod = (uniforms.lodAndFlags.x != 0u) &&
                  (horizDistChunks >= float(uniforms.lodAndFlags.y)) &&
                  ((sec.lodSolidQuadCount + sec.lodCutoutQuadCount) > 0u || (sec.solidQuadCount + sec.cutoutQuadCount) == 0u);

    uint solidBase  = useLod && sec.lodSolidQuadCount > 0u ? sec.lodSolidBaseVertex : sec.solidBaseVertex;
    uint solidQuads = useLod ? (sec.lodSolidQuadCount > 0u ? sec.lodSolidQuadCount : sec.solidQuadCount) : sec.solidQuadCount;

    uint cutoutBase  = useLod && sec.lodCutoutQuadCount > 0u ? sec.lodCutoutBaseVertex : sec.cutoutBaseVertex;
    uint cutoutQuads = useLod ? (sec.lodCutoutQuadCount > 0u ? sec.lodCutoutQuadCount : sec.cutoutQuadCount) : sec.cutoutQuadCount;

    if (useLod) {
        atomic_fetch_add_explicit(&statsWords[3], 1u, memory_order_relaxed);
    }

    if (solidQuads > 0u) {
        solidCmd.draw_indexed_primitives(
            primitive_type::triangle,
            solidQuads * 6u,
            patternIndices,
            1u,
            solidBase,
            gid
        );
        atomic_fetch_add_explicit(&statsWords[4], solidQuads, memory_order_relaxed);
    }

    if (cutoutQuads > 0u) {
        cutoutCmd.draw_indexed_primitives(
            primitive_type::triangle,
            cutoutQuads * 6u,
            patternIndices,
            1u,
            cutoutBase,
            gid
        );
        atomic_fetch_add_explicit(&statsWords[5], cutoutQuads, memory_order_relaxed);
    }
}

// Populate Hi-Z mip 0 from the full-res Depth32Float / R32Float exported depth texture.
kernel void buildHiZMip0(
    texture2d<float, access::read>  srcDepth [[texture(0)]],
    texture2d<float, access::write> dstMip0  [[texture(1)]],
    uint2 gid                                [[thread_position_in_grid]]
) {
    uint dstW = dstMip0.get_width();
    uint dstH = dstMip0.get_height();
    if (gid.x >= dstW || gid.y >= dstH) {
        return;
    }
    uint srcW = srcDepth.get_width();
    uint srcH = srcDepth.get_height();
    uint2 base = gid * 2u;
    uint2 c00 = min(base + uint2(0u, 0u), uint2(srcW - 1u, srcH - 1u));
    uint2 c10 = min(base + uint2(1u, 0u), uint2(srcW - 1u, srcH - 1u));
    uint2 c01 = min(base + uint2(0u, 1u), uint2(srcW - 1u, srcH - 1u));
    uint2 c11 = min(base + uint2(1u, 1u), uint2(srcW - 1u, srcH - 1u));
    float maxZ = max(max(srcDepth.read(c00).r, srcDepth.read(c10).r),
                     max(srcDepth.read(c01).r, srcDepth.read(c11).r));
    dstMip0.write(float4(maxZ, 0.0f, 0.0f, 0.0f), gid);
}

// Conservative 2x2 max reduction across Hi-Z mip levels (including odd dimensions!).
kernel void downsampleHiZMip(
    texture2d<float, access::read>  srcMip [[texture(0)]],
    texture2d<float, access::write> dstMip [[texture(1)]],
    uint2 gid                              [[thread_position_in_grid]]
) {
    uint dstW = dstMip.get_width();
    uint dstH = dstMip.get_height();
    if (gid.x >= dstW || gid.y >= dstH) {
        return;
    }
    uint srcW = srcMip.get_width();
    uint srcH = srcMip.get_height();
    uint2 base = gid * 2u;

    float maxZ = 0.0f;
    // Sample 3x3 when source dimension is odd so edge texels are never dropped (conservative occlusion!).
    uint extraX = ((srcW & 1u) != 0u && gid.x == dstW - 1u) ? 2u : 1u;
    uint extraY = ((srcH & 1u) != 0u && gid.y == dstH - 1u) ? 2u : 1u;
    for (uint dy = 0u; dy <= extraY; ++dy) {
        for (uint dx = 0u; dx <= extraX; ++dx) {
            uint2 sc = min(base + uint2(dx, dy), uint2(srcW - 1u, srcH - 1u));
            maxZ = max(maxZ, srcMip.read(sc).r);
        }
    }
    dstMip.write(float4(maxZ, 0.0f, 0.0f, 0.0f), gid);
}
