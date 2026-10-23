#include "metal_shared.h"

struct TerrainFragmentOut {
    float4 color      [[color(0)]];
    float  exportedZ  [[color(1)]];
};

constexpr sampler kAtlasNearestMipSampler(
    coord::normalized,
    address::clamp_to_edge,
    filter::nearest,
    mip_filter::linear
);

constexpr sampler kAtlasSharpNoMipSampler(
    coord::normalized,
    address::clamp_to_edge,
    filter::nearest,
    mip_filter::none
);

vertex TerrainRasterData terrainVertex(
    uint vertexId                          [[vertex_id]],
    uint baseInstance                      [[base_instance]],
    device const MetalTerrainVertex* arena [[buffer(0)]],
    constant SectionGpuMeta* sections      [[buffer(1)]],
    constant FrameUniforms& uniforms       [[buffer(2)]],
    constant TerrainTextures& textures     [[buffer(3)]]
) {
    constant SectionGpuMeta& sec = sections[baseInstance];
    float3 camRelOrigin = mr_cameraRelativeOrigin(sec, uniforms);
    return mr_evalTerrainVertex(arena[vertexId], camRelOrigin, uniforms, textures.lightmap);
}

// Solid terrain fragment shader: NO discard_fragment() so Apple Silicon TBDR runs full hardware Early-Z / HSR!
fragment TerrainFragmentOut terrainFragmentSolid(
    TerrainRasterData in                   [[stage_in]],
    constant FrameUniforms& uniforms       [[buffer(2)]],
    constant TerrainTextures& textures     [[buffer(3)]]
) {
    texture2d<float, access::sample> atlas = textures.atlas;
    float4 tex = atlas.sample(kAtlasNearestMipSampler, in.uv);
    float4 lit = float4(tex.rgb * float3(in.color.rgb), 1.0f);
    float4 fogged = mr_applyVanillaFog(lit, in.fogDist, uniforms);

    TerrainFragmentOut out;
    out.color = float4(fogged.rgb, 1.0f);
    out.exportedZ = in.position.z;
    return out;
}

// Cutout Mipped (alpha >= 0.5, mipmapped) and Cutout (alpha >= 0.1, mip 0 sharp) fragment shader.
fragment TerrainFragmentOut terrainFragmentCutout(
    TerrainRasterData in                   [[stage_in]],
    constant FrameUniforms& uniforms       [[buffer(2)]],
    constant TerrainTextures& textures     [[buffer(3)]]
) {
    texture2d<float, access::sample> atlas = textures.atlas;
    bool useMips = (in.mode == MR_MODE_CUTOUT_MIPPED);
    float4 tex = useMips
        ? atlas.sample(kAtlasNearestMipSampler, in.uv)
        : atlas.sample(kAtlasSharpNoMipSampler, in.uv, level(0.0f));
    float alpha = tex.a * float(in.color.a);
    float threshold = useMips ? 0.5f : 0.1f;
    if (alpha < threshold) {
        discard_fragment();
    }

    float4 lit = float4(tex.rgb * float3(in.color.rgb), 1.0f);
    float4 fogged = mr_applyVanillaFog(lit, in.fogDist, uniforms);

    TerrainFragmentOut out;
    out.color = float4(fogged.rgb, 1.0f);
    out.exportedZ = in.position.z;
    return out;
}

// Translucent & Tripwire fragment shader: outputs premultiplied RGBA + min-blended window depth.
fragment TerrainFragmentOut terrainFragmentTranslucent(
    TerrainRasterData in                   [[stage_in]],
    constant FrameUniforms& uniforms       [[buffer(2)]],
    constant TerrainTextures& textures     [[buffer(3)]]
) {
    texture2d<float, access::sample> atlas = textures.atlas;
    bool isTripwire = (in.mode == MR_MODE_TRIPWIRE);
    float4 tex = isTripwire
        ? atlas.sample(kAtlasSharpNoMipSampler, in.uv, level(0.0f))
        : atlas.sample(kAtlasNearestMipSampler, in.uv);
    float alpha = tex.a * float(in.color.a);
    float threshold = isTripwire ? 0.1f : (1.0f / 255.0f);
    if (alpha < threshold) {
        discard_fragment();
    }

    float4 lit = float4(tex.rgb * float3(in.color.rgb), alpha);
    float4 fogged = mr_applyVanillaFog(lit, in.fogDist, uniforms);

    TerrainFragmentOut out;
    out.color = float4(fogged.rgb * fogged.a, fogged.a);
    out.exportedZ = in.position.z;
    return out;
}

// High-quality edge-preserving upscale kernel for color + nearest-depth upscale when MetalFX isn't active.
kernel void upscaleColorAndDepth(
    texture2d<float, access::sample> srcColor [[texture(0)]],
    texture2d<float, access::read>   srcDepth [[texture(1)]],
    texture2d<float, access::write>  dstColor [[texture(2)]],
    texture2d<float, access::write>  dstDepth [[texture(3)]],
    uint2 gid                                 [[thread_position_in_grid]]
) {
    uint dstW = dstColor.get_width();
    uint dstH = dstColor.get_height();
    if (gid.x >= dstW || gid.y >= dstH) {
        return;
    }
    constexpr sampler kLinearClamp(coord::normalized, address::clamp_to_edge, filter::linear);
    float2 uv = (float2(gid) + 0.5f) / float2(float(dstW), float(dstH));
    float4 c = srcColor.sample(kLinearClamp, uv);
    dstColor.write(c, gid);

    uint srcW = srcDepth.get_width();
    uint srcH = srcDepth.get_height();
    uint2 srcCoord = min(uint2(uv * float2(float(srcW), float(srcH))), uint2(srcW - 1, srcH - 1));
    float d = srcDepth.read(srcCoord).r;
    dstDepth.write(float4(d, 0.0f, 0.0f, 0.0f), gid);
}

// Scaled frames: pixels without terrain take the OpenGL frame (sky) at render resolution, so the
// upscaler blends terrain silhouettes into the real sky rather than into the clear colour.
kernel void fillBackground(
    texture2d<float, access::write> color      [[texture(0)]],
    texture2d<float, access::read>  zexp       [[texture(1)]],
    texture2d<float, access::read>  background [[texture(2)]],
    uint2 gid                                  [[thread_position_in_grid]]
) {
    if (gid.x >= color.get_width() || gid.y >= color.get_height() ||
        gid.x >= background.get_width() || gid.y >= background.get_height()) {
        return;
    }
    if (zexp.read(gid).r >= 1.0f) {
        color.write(float4(background.read(gid).rgb, 1.0f), gid);
    }
}

kernel void upscaleDepthNearest(
    texture2d<float, access::read>  srcDepth [[texture(0)]],
    texture2d<float, access::write> dstDepth [[texture(1)]],
    uint2 gid                                [[thread_position_in_grid]]
) {
    uint dstW = dstDepth.get_width();
    uint dstH = dstDepth.get_height();
    if (gid.x >= dstW || gid.y >= dstH) {
        return;
    }
    uint srcW = srcDepth.get_width();
    uint srcH = srcDepth.get_height();
    float2 uv = (float2(gid) + 0.5f) / float2(float(dstW), float(dstH));
    uint2 srcCoord = min(uint2(uv * float2(float(srcW), float(srcH))), uint2(srcW - 1, srcH - 1));
    float d = srcDepth.read(srcCoord).r;
    dstDepth.write(float4(d, 0.0f, 0.0f, 0.0f), gid);
}
