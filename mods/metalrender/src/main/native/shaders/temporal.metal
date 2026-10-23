#include "metal_shared.h"

// Rebuilds per-pixel motion vectors for MetalFX temporal upscaling from the opaque depth buffer.
// Terrain never moves, so reprojecting each pixel through the previous camera is exact for terrain.
// Output is in render-resolution pixels pointing from the current pixel to where it was last frame,
// which matches MTLFXTemporalScaler with motionVectorScale = (1, 1).
kernel void computeCameraMotion(
    depth2d<float, access::read>    depth  [[texture(0)]],
    texture2d<float, access::write> motion [[texture(1)]],
    constant TemporalUniforms& t           [[buffer(0)]],
    uint2 gid                              [[thread_position_in_grid]]
) {
    uint w = motion.get_width();
    uint h = motion.get_height();
    if (gid.x >= w || gid.y >= h) {
        return;
    }
    float2 size = float2(float(w), float(h));
    float d = depth.read(gid);

    // Pixel centre in NDC of the jittered frame, then remove the jitter. The renderer shifts image
    // content by +jitter.x pixels right and +jitter.y pixels down.
    float2 pix = float2(gid) + 0.5f;
    float2 ndc = float2(pix.x / size.x * 2.0f - 1.0f, 1.0f - pix.y / size.y * 2.0f);
    ndc -= float2(2.0f * t.jitter.x / size.x, -2.0f * t.jitter.y / size.y);

    float4 world = t.invViewProj * float4(ndc, d * 2.0f - 1.0f, 1.0f);
    if (abs(world.w) < 1e-7f) {
        motion.write(float4(0.0f), gid);
        return;
    }
    world /= world.w;

    float4 prevClip = t.prevViewProj * float4(world.xyz + t.cameraDelta.xyz, 1.0f);
    if (prevClip.w <= 1e-5f) {
        motion.write(float4(0.0f), gid);
        return;
    }
    float2 prevNdc = prevClip.xy / prevClip.w;
    float2 currPix = float2((ndc.x * 0.5f + 0.5f) * size.x, (0.5f - ndc.y * 0.5f) * size.y);
    float2 prevPix = float2((prevNdc.x * 0.5f + 0.5f) * size.x, (0.5f - prevNdc.y * 0.5f) * size.y);
    motion.write(float4(prevPix - currPix, 0.0f, 0.0f), gid);
}
