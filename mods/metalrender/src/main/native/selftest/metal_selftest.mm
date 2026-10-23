// Native GPU self-test for the MetalRender bridge. Renders a synthetic terrain scene through every
// tier this Mac supports and checks the results against each other and against native-resolution
// references. Usage: metalrender_selftest <dir containing shaders_*.metallib>
#import <Foundation/Foundation.h>
#import <IOSurface/IOSurface.h>
#include <simd/simd.h>

#include <cmath>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include "../metal_bridge_internal.h"

namespace {

constexpr int kW = 1280;
constexpr int kH = 720;
constexpr int kGrid = 4; // 4x4 sections of 16x16 blocks
int gFailures = 0;

void check(bool ok, const char* what) {
    std::printf("  [%s] %s\n", ok ? "PASS" : "FAIL", what);
    if (!ok) gFailures++;
}

struct Scene {
    std::vector<MetalTerrainVertex> verts;
    std::vector<SectionGpuMeta> sections;
    std::vector<MeshletGpuMeta> meshlets;
    std::vector<uint32_t> transOrder;
};

void addQuad(std::vector<MetalTerrainVertex>& out, simd_float3 origin, simd_float3 p[4], simd_float3 normal,
             float shade, uint8_t alpha, uint8_t mode) {
    simd_float3 n = simd_cross(p[1] - p[0], p[2] - p[0]);
    if (simd_dot(n, normal) < 0.0f) std::swap(p[1], p[3]); // force CCW seen from outside
    const float uv[4][2] = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
    for (int i = 0; i < 4; ++i) {
        simd_float3 local = p[i] - origin;
        MetalTerrainVertex v{};
        v.px = (int16_t)std::lround((local.x - 8.0f) * 1024.0f);
        v.py = (int16_t)std::lround((local.y - 8.0f) * 1024.0f);
        v.pz = (int16_t)std::lround((local.z - 8.0f) * 1024.0f);
        v.blockLight = 240;
        v.skyLight = 240;
        v.r = v.g = v.b = (uint8_t)std::lround(255.0f * shade);
        v.a = alpha;
        v.u = uv[i][0];
        v.v = uv[i][1];
        v.nx = (int8_t)(normal.x * 127.0f);
        v.ny = (int8_t)(normal.y * 127.0f);
        v.nz = (int8_t)(normal.z * 127.0f);
        v.mode = mode;
        out.push_back(v);
    }
}

void addBox(std::vector<MetalTerrainVertex>& out, simd_float3 origin, simd_float3 lo, simd_float3 hi) {
    const simd_float3 normals[6] = {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
    const float shades[6] = {1.0f, 0.5f, 0.8f, 0.8f, 0.65f, 0.65f};
    for (int f = 0; f < 6; ++f) {
        simd_float3 n = normals[f];
        simd_float3 p[4];
        if (n.y != 0) {
            float y = n.y > 0 ? hi.y : lo.y;
            p[0] = {lo.x, y, lo.z}; p[1] = {lo.x, y, hi.z}; p[2] = {hi.x, y, hi.z}; p[3] = {hi.x, y, lo.z};
        } else if (n.x != 0) {
            float x = n.x > 0 ? hi.x : lo.x;
            p[0] = {x, lo.y, lo.z}; p[1] = {x, hi.y, lo.z}; p[2] = {x, hi.y, hi.z}; p[3] = {x, lo.y, hi.z};
        } else {
            float z = n.z > 0 ? hi.z : lo.z;
            p[0] = {lo.x, lo.y, z}; p[1] = {hi.x, lo.y, z}; p[2] = {hi.x, hi.y, z}; p[3] = {lo.x, hi.y, z};
        }
        addQuad(out, origin, p, n, shades[f], 255, MR_MODE_SOLID);
    }
}

Scene buildScene() {
    Scene s;
    for (int sz = 0; sz < kGrid; ++sz) {
        for (int sx = 0; sx < kGrid; ++sx) {
            SectionGpuMeta m{};
            m.originX = sx * 16;
            m.originY = 0;
            m.originZ = sz * 16;
            m.flags = MR_SECTION_ACTIVE;
            simd_float3 origin = {(float)m.originX, 0.0f, (float)m.originZ};
            uint32_t base = (uint32_t)s.verts.size();
            // Ground: top faces of a 16x16 block layer.
            for (int z = 0; z < 16; ++z) {
                for (int x = 0; x < 16; ++x) {
                    simd_float3 p[4] = {origin + simd_make_float3(x, 1, z), origin + simd_make_float3(x, 1, z + 1),
                                        origin + simd_make_float3(x + 1, 1, z + 1), origin + simd_make_float3(x + 1, 1, z)};
                    addQuad(s.verts, origin, p, simd_make_float3(0, 1, 0), ((x + z) & 1) ? 1.0f : 0.85f, 255, MR_MODE_SOLID);
                }
            }
            // A pillar per section, height varying, so there is parallax and real occlusion.
            int h = 2 + ((sx * 3 + sz * 5) % 6);
            addBox(s.verts, origin, origin + simd_make_float3(6, 1, 6), origin + simd_make_float3(10, 1 + h, 10));
            uint32_t solidQuads = ((uint32_t)s.verts.size() - base) / 4u;
            m.solidBaseVertex = base;
            m.solidQuadCount = solidQuads;
            m.aabbMinX = 0; m.aabbMinY = 0; m.aabbMinZ = 0;
            m.aabbMaxX = 16; m.aabbMaxY = (float)(1 + h); m.aabbMaxZ = 16;

            // Translucent glass sheet floating over the diagonal sections.
            if (sx == sz) {
                uint32_t tBase = (uint32_t)s.verts.size();
                for (int x = 0; x < 16; x += 4) {
                    simd_float3 p[4] = {origin + simd_make_float3(x, 12, 0), origin + simd_make_float3(x, 12, 16),
                                        origin + simd_make_float3(x + 3, 12, 16), origin + simd_make_float3(x + 3, 12, 0)};
                    addQuad(s.verts, origin, p, simd_make_float3(0, 1, 0), 0.9f, 128, MR_MODE_TRANSLUCENT);
                }
                m.transBaseVertex = tBase;
                m.transQuadCount = ((uint32_t)s.verts.size() - tBase) / 4u;
                m.flags |= MR_SECTION_HAS_TRANSLUCENT;
                m.aabbMaxY = 13;
                s.transOrder.push_back((uint32_t)s.sections.size());
            }

            // 32-quad meshlets over the solid range.
            m.meshletStart = (uint32_t)s.meshlets.size();
            for (uint32_t q = 0; q < solidQuads; q += 32) {
                uint32_t count = std::min<uint32_t>(32, solidQuads - q);
                simd_float3 lo = {1e9f, 1e9f, 1e9f}, hi = {-1e9f, -1e9f, -1e9f};
                for (uint32_t v = 0; v < count * 4; ++v) {
                    const MetalTerrainVertex& tv = s.verts[base + q * 4 + v];
                    simd_float3 lp = {8.0f + tv.px / 1024.0f, 8.0f + tv.py / 1024.0f, 8.0f + tv.pz / 1024.0f};
                    lo = simd_min(lo, lp);
                    hi = simd_max(hi, lp);
                }
                MeshletGpuMeta ml{};
                simd_float3 c = (lo + hi) * 0.5f;
                ml.localCenterX = c.x; ml.localCenterY = c.y; ml.localCenterZ = c.z;
                ml.radius = simd_length(hi - c) + 0.01f;
                ml.sectionIndex = (uint32_t)s.sections.size();
                ml.baseVertex = base + q * 4;
                ml.quadCount = (uint16_t)count;
                ml.coneCutoff = -127;
                s.meshlets.push_back(ml);
            }
            m.meshletCount = (uint32_t)s.meshlets.size() - m.meshletStart;
            s.sections.push_back(m);
        }
    }
    return s;
}

struct Camera {
    simd_double3 pos;
    simd_float3 target;
};

simd_float4x4 viewProj(const Camera& cam, float aspect) {
    float fov = 70.0f * (float)M_PI / 180.0f, n = 0.05f, f = 512.0f, t = 1.0f / std::tan(fov * 0.5f);
    simd_float4x4 proj = {{{t / aspect, 0, 0, 0}, {0, t, 0, 0}, {0, 0, (f + n) / (n - f), -1}, {0, 0, 2 * f * n / (n - f), 0}}};
    simd_float3 eye = {(float)cam.pos.x, (float)cam.pos.y, (float)cam.pos.z};
    simd_float3 fw = simd_normalize(cam.target - eye);
    simd_float3 sd = simd_normalize(simd_cross(fw, simd_make_float3(0, 1, 0)));
    simd_float3 up = simd_cross(sd, fw);
    simd_float4x4 view = {{{sd.x, up.x, -fw.x, 0}, {sd.y, up.y, -fw.y, 0}, {sd.z, up.z, -fw.z, 0}, {0, 0, 0, 1}}};
    return simd_mul(proj, view);
}

FrameUniforms makeUniforms(const Camera& cam, simd_float4x4 vp) {
    FrameUniforms u{};
    u.viewProjMatrix = vp;
    // Gribb-Hartmann planes from the OpenGL clip matrix (rows of vp).
    simd_float4 r[4];
    for (int i = 0; i < 4; ++i) r[i] = simd_make_float4(vp.columns[0][i], vp.columns[1][i], vp.columns[2][i], vp.columns[3][i]);
    simd_float4 planes[6] = {r[3] + r[0], r[3] - r[0], r[3] + r[1], r[3] - r[1], r[3] + r[2], r[3] - r[2]};
    for (int i = 0; i < 6; ++i) u.frustumPlanes[i] = planes[i] / simd_length(planes[i].xyz);
    simd_double3 fl = {std::floor(cam.pos.x), std::floor(cam.pos.y), std::floor(cam.pos.z)};
    u.cameraBlockPos = simd_make_int4((int)fl.x, (int)fl.y, (int)fl.z, 0);
    u.cameraFracPos = simd_make_float4((float)(cam.pos.x - fl.x), (float)(cam.pos.y - fl.y), (float)(cam.pos.z - fl.z), 256.0f);
    u.fogColor = simd_make_float4(0.6f, 0.7f, 0.9f, 1.0f);
    u.fogParams = simd_make_float4(1000.0f, 2000.0f, 0.0f, 0.0f);
    u.lodAndFlags = simd_make_uint4(0, 0, 0, 1);
    u.screenParams.w = 1.0f;
    return u;
}

simd_float4x4 jitterMatrix(float jx, float jy, int w, int h) {
    simd_float4x4 j = matrix_identity_float4x4;
    j.columns[3][0] = 2.0f * jx / (float)w;
    j.columns[3][1] = -2.0f * jy / (float)h;
    return j;
}

float halton(int i, int b) {
    float f = 1.0f, r = 0.0f;
    for (; i > 0; i /= b) { f /= (float)b; r += f * (float)(i % b); }
    return r;
}

struct Image {
    std::vector<uint8_t> bgra;
    std::vector<float> depth;
};

Image readImage(void* ctx, int slot, int translucent) {
    Image img;
    IOSurfaceRef cs = mrSurface(ctx, slot, translucent, 0);
    IOSurfaceRef ds = mrSurface(ctx, slot, translucent, 1);
    IOSurfaceLock(cs, kIOSurfaceLockReadOnly, nullptr);
    IOSurfaceLock(ds, kIOSurfaceLockReadOnly, nullptr);
    size_t cRow = IOSurfaceGetBytesPerRow(cs), dRow = IOSurfaceGetBytesPerRow(ds);
    auto* cb = static_cast<const uint8_t*>(IOSurfaceGetBaseAddress(cs));
    auto* db = static_cast<const uint8_t*>(IOSurfaceGetBaseAddress(ds));
    img.bgra.resize((size_t)kW * kH * 4);
    img.depth.resize((size_t)kW * kH);
    for (int y = 0; y < kH; ++y) {
        std::memcpy(&img.bgra[(size_t)y * kW * 4], cb + y * cRow, (size_t)kW * 4);
        std::memcpy(&img.depth[(size_t)y * kW], db + y * dRow, (size_t)kW * 4);
    }
    IOSurfaceUnlock(ds, kIOSurfaceLockReadOnly, nullptr);
    IOSurfaceUnlock(cs, kIOSurfaceLockReadOnly, nullptr);
    return img;
}

double coverage(const Image& img) {
    size_t n = 0;
    for (size_t i = 0; i < img.depth.size(); ++i) n += img.bgra[i * 4 + 3] > 0;
    return (double)n / (double)img.depth.size();
}

// PSNR over RGB where the reference has terrain.
double psnr(const Image& ref, const Image& img) {
    double se = 0.0;
    size_t n = 0;
    for (size_t i = 0; i < ref.depth.size(); ++i) {
        if (ref.bgra[i * 4 + 3] == 0) continue;
        for (int c = 0; c < 3; ++c) {
            double d = (double)ref.bgra[i * 4 + c] - (double)img.bgra[i * 4 + c];
            se += d * d;
        }
        n += 3;
    }
    if (n == 0) return 0.0;
    double mse = se / (double)n;
    return mse <= 1e-9 ? 99.0 : 10.0 * std::log10(255.0 * 255.0 / mse);
}

void* makeContext(const char* dir, int tier, const Scene& s) {
    void* ctx = mrCreateContext(dir, tier, (int)s.verts.size() + 4096, (int)s.sections.size(), (int)s.meshlets.size());
    if (!ctx) return nullptr;
    uint32_t cap = 0;
    std::memcpy(mrVertexArena(ctx, &cap), s.verts.data(), s.verts.size() * sizeof(MetalTerrainVertex));
    std::memcpy(mrSectionMeta(ctx, &cap), s.sections.data(), s.sections.size() * sizeof(SectionGpuMeta));
    std::memcpy(mrMeshletMeta(ctx, &cap), s.meshlets.data(), s.meshlets.size() * sizeof(MeshletGpuMeta));
    mrEnsurePatternQuads(ctx, (uint32_t)s.verts.size() / 4u);
    // 16x16 block texture with 8px features, roughly the detail level of a vanilla block face.
    std::vector<uint32_t> atlas(16 * 16);
    for (int y = 0; y < 16; ++y)
        for (int x = 0; x < 16; ++x)
            atlas[(size_t)y * 16 + x] = (((x >> 3) + (y >> 3)) & 1) ? 0xFF3A8F4Bu : 0xFF6FD08Cu; // RGBA bytes
    mrSetAtlasRgba(ctx, 16, 16, atlas.data());
    return ctx;
}

const Camera kCam = {{32.0, 22.0, 78.0}, {32.0, 0.0, 28.0}};

Image renderPlain(void* ctx, const Scene& s, const Camera& cam, float scale, int upscale, int useMesh) {
    FrameUniforms u = makeUniforms(cam, viewProj(cam, (float)kW / kH));
    int slot = mrRenderOpaque(ctx, kW, kH, scale, useMesh, upscale, &u, nullptr, (int)s.sections.size(), (int)s.meshlets.size());
    return slot < 0 ? Image{} : readImage(ctx, slot, 0);
}

// Runs `frames` jittered frames of MetalFX temporal at 0.5x while the camera pans by `panPerFrame`.
// zeroMotion feeds identity reprojection (what you get without motion vectors) for comparison.
Image renderTemporal(void* ctx, const Scene& s, int frames, double panPerFrame, bool zeroMotion, Camera* finalCam) {
    const int rw = kW / 2, rh = kH / 2;
    Camera cam = kCam, prev = kCam;
    simd_float4x4 prevVp = viewProj(prev, (float)kW / kH);
    int slot = -1;
    for (int i = 0; i < frames; ++i) {
        cam.pos.x = kCam.pos.x + panPerFrame * i;
        cam.target.x = kCam.target.x + (float)(panPerFrame * i);
        simd_float4x4 vp = viewProj(cam, (float)kW / kH);
        float jx = halton(i + 1, 2) - 0.5f, jy = halton(i + 1, 3) - 0.5f;
        FrameUniforms u = makeUniforms(cam, simd_mul(jitterMatrix(jx, jy, rw, rh), vp));
        TemporalUniforms t{};
        t.invViewProj = simd_inverse(vp);
        t.prevViewProj = zeroMotion ? vp : prevVp;
        simd_double3 d = zeroMotion ? simd_make_double3(0, 0, 0) : cam.pos - prev.pos;
        t.cameraDelta = simd_make_float4((float)d.x, (float)d.y, (float)d.z, 0);
        t.jitter = simd_make_float4(jx, jy, i == 0 ? 1.0f : 0.0f, 0);
        slot = mrRenderOpaque(ctx, kW, kH, 0.5f, 1, MR_UPSCALE_TEMPORAL, &u, &t, (int)s.sections.size(), (int)s.meshlets.size());
        prev = cam;
        prevVp = vp;
    }
    if (finalCam) *finalCam = cam;
    return slot < 0 ? Image{} : readImage(ctx, slot, 0);
}

const char* tierName(int t) {
    switch (t) {
        case MR_TIER_COMPUTE_ICB: return "Compute+ICB";
        case MR_TIER_METAL3_MESH: return "Metal 3 mesh";
        case MR_TIER_METAL4: return "Metal 4";
        default: return "?";
    }
}

} // namespace

int main(int argc, char** argv) {
    @autoreleasepool {
        if (argc < 2) {
            std::fprintf(stderr, "usage: %s <metallib dir>\n", argv[0]);
            return 2;
        }
        const char* dir = argv[1];
        char caps[1024];
        mrProbeCapabilities(caps, sizeof(caps));
        std::printf("capabilities: %s\n", caps);
        std::string capStr(caps);
        bool hasMetal4 = capStr.find("metal4=1") != std::string::npos;
        bool hasTemporal = capStr.find("metalfxTemporal=1") != std::string::npos;

        Scene scene = buildScene();
        std::printf("scene: %zu quads, %zu sections, %zu meshlets\n", scene.verts.size() / 4, scene.sections.size(), scene.meshlets.size());

        Image reference;
        const int tiers[3] = {MR_TIER_COMPUTE_ICB, MR_TIER_METAL3_MESH, MR_TIER_METAL4};
        for (int tier : tiers) {
            void* ctx = makeContext(dir, tier, scene);
            std::printf("\n== requested %s\n", tierName(tier));
            if (!ctx) { check(false, "context created"); continue; }
            int active = mrActiveTier(ctx);
            std::printf("  active tier: %s, shader library: %s\n", tierName(active), mrLibraryName(ctx));
            if (tier == MR_TIER_METAL4 && hasMetal4) check(active == MR_TIER_METAL4, "Metal 4 tier active on Metal 4 GPU");
            if (tier != MR_TIER_METAL4) check(active == tier, "requested tier honoured");

            Image a = renderPlain(ctx, scene, kCam, 1.0f, MR_UPSCALE_NONE, tier != MR_TIER_COMPUTE_ICB);
            Image b = renderPlain(ctx, scene, kCam, 1.0f, MR_UPSCALE_NONE, tier != MR_TIER_COMPUTE_ICB); // Hi-Z now valid
            GpuCullStats st{};
            float oms = 0, tms = 0;
            mrReadStats(ctx, &st, &oms, &tms);
            double cov = a.depth.empty() ? 0 : coverage(a);
            std::printf("  coverage %.1f%%, opaque GPU %.3f ms, solid quads %u, meshlets %u, occluded %u, frustum-culled %u\n",
                        cov * 100.0, oms, st.drawnSolidQuads, st.visibleMeshlets, st.occludedSections, st.frustumCulledSections);
            check(cov > 0.3, "terrain covers > 30% of frame");
            check(!b.depth.empty() && psnr(a, b) > 60.0, "Hi-Z occlusion frame identical to first frame");
            if (reference.depth.empty()) {
                reference = a;
            } else {
                double p = psnr(reference, a);
                std::printf("  PSNR vs Compute+ICB reference: %.2f dB\n", p);
                check(p > 40.0, "matches Compute+ICB reference image");
            }

            int tslot = mrRenderTranslucent(ctx, 0, kW, kH, nullptr, nullptr, 0); // empty call must be harmless
            (void)tslot;
            FrameUniforms u = makeUniforms(kCam, viewProj(kCam, (float)kW / kH));
            int slot = mrRenderOpaque(ctx, kW, kH, 1.0f, 1, MR_UPSCALE_NONE, &u, nullptr, (int)scene.sections.size(), (int)scene.meshlets.size());
            bool drew = mrRenderTranslucent(ctx, slot, kW, kH, &u, scene.transOrder.data(), (int)scene.transOrder.size()) != 0;
            Image tr = readImage(ctx, slot, 1);
            mrReadStats(ctx, &st, &oms, &tms);
            std::printf("  translucent: %u quads, coverage %.1f%%, GPU %.3f ms\n", st.drawnTransQuads, coverage(tr) * 100.0, tms);
            check(drew && coverage(tr) > 0.01, "translucent pass draws glass");

            if (tier == MR_TIER_COMPUTE_ICB) { mrDestroyContext(ctx); continue; }

            Image bilinear = renderPlain(ctx, scene, kCam, 0.5f, MR_UPSCALE_NONE, 1);
            Image spatial = renderPlain(ctx, scene, kCam, 0.5f, MR_UPSCALE_SPATIAL, 1);
            int usedSpatial = mrLastUpscaleUsed(ctx);
            double pb = psnr(reference, bilinear), ps = psnr(reference, spatial);
            std::printf("  0.5x bilinear %.2f dB, MetalFX spatial %.2f dB (mode used %d)\n", pb, ps, usedSpatial);
            check(usedSpatial == MR_UPSCALE_SPATIAL, "MetalFX spatial engaged");
            check(psnr(bilinear, spatial) > 20.0 && coverage(spatial) > 0.3, "MetalFX spatial output matches the scene");

            if (hasTemporal) {
                double best = -1.0;
                float bestSign = 1.0f;
                for (float sign : {1.0f, -1.0f}) {
                    mrSetJitterSign(ctx, sign);
                    Image t = renderTemporal(ctx, scene, 32, 0.0, false, nullptr);
                    double pt = psnr(reference, t);
                    std::printf("  0.5x MetalFX temporal, static, jitter sign %+.0f: %.2f dB (mode used %d)\n", sign, pt, mrLastUpscaleUsed(ctx));
                    if (pt > best) { best = pt; bestSign = sign; }
                }
                check(mrLastUpscaleUsed(ctx) == MR_UPSCALE_TEMPORAL, "MetalFX temporal engaged");
                check(best > pb, "MetalFX temporal beats bilinear at 0.5x");
                mrSetJitterSign(ctx, bestSign);

                Camera endCam;
                Image moving = renderTemporal(ctx, scene, 32, 0.08, false, &endCam);
                Image still = renderTemporal(ctx, scene, 32, 0.08, true, nullptr);
                Image movingRef = renderPlain(ctx, scene, endCam, 1.0f, MR_UPSCALE_NONE, 1);
                double pm = psnr(movingRef, moving), pz = psnr(movingRef, still);
                std::printf("  panning camera: with motion vectors %.2f dB, without %.2f dB (best jitter sign %+.0f)\n", pm, pz, bestSign);
                check(pm > pz, "camera motion vectors improve temporal result while moving");
            }
            mrDestroyContext(ctx);
        }
        std::printf("\n%s (%d failure%s)\n", gFailures ? "SELFTEST FAILED" : "SELFTEST PASSED", gFailures, gFailures == 1 ? "" : "s");
        return gFailures ? 1 : 0;
    }
}
