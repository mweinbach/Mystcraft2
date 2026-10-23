#define GL_SILENCE_DEPRECATION 1

#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#import <MetalFX/MetalFX.h>
#import <IOSurface/IOSurface.h>
#import <CoreVideo/CoreVideo.h>
#import <OpenGL/OpenGL.h>
#import <OpenGL/gl3.h>
#import <OpenGL/CGLIOSurface.h>

#include <jni.h>
#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "metal_bridge_internal.h"

namespace {

constexpr int kRingSlots = 3;

constexpr MTLTextureUsage kColorUsage =
    MTLTextureUsageRenderTarget | MTLTextureUsageShaderRead | MTLTextureUsageShaderWrite;
constexpr MTLTextureUsage kDepthUsage = MTLTextureUsageRenderTarget | MTLTextureUsageShaderRead;

struct SurfacePair {
    int width = 0;
    int height = 0;
    IOSurfaceRef colorSurface = nullptr;
    IOSurfaceRef depthSurface = nullptr;
    id<MTLTexture> colorTex = nil;
    id<MTLTexture> depthTex = nil;

    void destroy() {
        colorTex = nil;
        depthTex = nil;
        if (colorSurface) {
            CFRelease(colorSurface);
            colorSurface = nullptr;
        }
        if (depthSurface) {
            CFRelease(depthSurface);
            depthSurface = nullptr;
        }
        width = 0;
        height = 0;
    }
};

static IOSurfaceRef createSurface(int w, int h, OSType pixelFormat) {
    NSDictionary* props = @{
        (id)kIOSurfaceWidth: @(w),
        (id)kIOSurfaceHeight: @(h),
        (id)kIOSurfaceBytesPerElement: @(4),
        (id)kIOSurfacePixelFormat: @(pixelFormat),
    };
    return IOSurfaceCreate((__bridge CFDictionaryRef)props);
}

static bool ensureSurfacePair(id<MTLDevice> dev, SurfacePair& pair, int w, int h) {
    if (pair.width == w && pair.height == h && pair.colorTex != nil && pair.depthTex != nil) {
        return true;
    }
    pair.destroy();

    pair.colorSurface = createSurface(w, h, kCVPixelFormatType_32BGRA);
    pair.depthSurface = createSurface(w, h, kCVPixelFormatType_OneComponent32Float);
    if (!pair.colorSurface || !pair.depthSurface) {
        pair.destroy();
        return false;
    }

    MTLTextureDescriptor* cDesc = [MTLTextureDescriptor
        texture2DDescriptorWithPixelFormat:MTLPixelFormatBGRA8Unorm
                                     width:(NSUInteger)w
                                    height:(NSUInteger)h
                                 mipmapped:NO];
    cDesc.storageMode = MTLStorageModeShared;
    cDesc.usage = MTLTextureUsageRenderTarget | MTLTextureUsageShaderRead | MTLTextureUsageShaderWrite;

    MTLTextureDescriptor* dDesc = [MTLTextureDescriptor
        texture2DDescriptorWithPixelFormat:MTLPixelFormatR32Float
                                     width:(NSUInteger)w
                                    height:(NSUInteger)h
                                 mipmapped:NO];
    dDesc.storageMode = MTLStorageModeShared;
    dDesc.usage = MTLTextureUsageRenderTarget | MTLTextureUsageShaderRead | MTLTextureUsageShaderWrite;

    pair.colorTex = [dev newTextureWithDescriptor:cDesc iosurface:pair.colorSurface plane:0];
    pair.depthTex = [dev newTextureWithDescriptor:dDesc iosurface:pair.depthSurface plane:0];
    if (!pair.colorTex || !pair.depthTex) {
        pair.destroy();
        return false;
    }

    pair.width = w;
    pair.height = h;
    return true;
}

struct MetalContext {
    id<MTLDevice> device = nil;
    id<MTLCommandQueue> queue = nil;
    id<MTLLibrary> library = nil;

    // Classic / ICB Render Pipeline States
    id<MTLRenderPipelineState> psoSolid = nil;
    id<MTLRenderPipelineState> psoCutout = nil;
    id<MTLRenderPipelineState> psoTranslucent = nil;

    // Metal 3 Object + Mesh Render Pipeline States
    id<MTLRenderPipelineState> psoMeshSolid = nil;
    id<MTLRenderPipelineState> psoMeshCutout = nil;
    bool supportsMeshShaders = false;
    bool supportsIcb = false;
    bool supportsMetalFX = false;
    bool apple7 = false;
    bool apple8 = false;
    bool apple9 = false;

    // Compute Pipeline States
    id<MTLComputePipelineState> csoCullIcb = nil;
    id<MTLComputePipelineState> csoBuildHiZMip0 = nil;
    id<MTLComputePipelineState> csoDownsampleHiZ = nil;
    id<MTLComputePipelineState> csoUpscaleColorDepth = nil;
    id<MTLComputePipelineState> csoUpscaleDepthNearest = nil;
    id<MTLComputePipelineState> csoCameraMotion = nil;

    // Depth Stencil States
    id<MTLDepthStencilState> dssOpaqueWrite = nil;
    id<MTLDepthStencilState> dssTranslucentTest = nil;

    // Unified Memory Arena Buffers
    id<MTLBuffer> vertexArenaBuf = nil;
    uint32_t vertexCap = 0;

    id<MTLBuffer> sectionMetaBuf = nil;
    uint32_t sectionCap = 0;

    id<MTLBuffer> meshletMetaBuf = nil;
    uint32_t meshletCap = 0;

    id<MTLBuffer> patternIndexBuf = nil;
    uint32_t patternQuadCap = 0;

    // Indirect Command Buffers (for Compute + ICB path)
    id<MTLIndirectCommandBuffer> icbSolid = nil;
    id<MTLIndirectCommandBuffer> icbCutout = nil;
    id<MTLBuffer> icbArgBuf = nil;

    // GPU Stats & Telemetry
    id<MTLBuffer> statsBuf = nil;
    GpuCullStats lastStats{};
    float lastOpaqueGpuMs = 0.0f;
    float lastTransGpuMs = 0.0f;

    // Block Atlas & Lightmap Textures
    id<MTLTexture> atlasTex = nil;
    id<MTLTexture> lightmapTex = nil;
    id<MTLBuffer> textureTableBuf = nil; // TerrainTextures: {atlas, lightmap} MTLResourceIDs

    void updateTextureTable() {
        if (@available(macOS 13.0, *)) {
            auto* ids = static_cast<MTLResourceID*>([textureTableBuf contents]);
            ids[0] = atlasTex.gpuResourceID;
            ids[1] = lightmapTex.gpuResourceID;
        }
    }

    // Internal Render Targets (used when resolutionScale < 1.0, plus hardware Depth32Float)
    int internalW = 0;
    int internalH = 0;
    id<MTLTexture> hwDepthTex = nil;
    id<MTLTexture> scaledOpaqueColorTex = nil;
    id<MTLTexture> scaledOpaqueDepthTex = nil;
    id<MTLTexture> scaledTransColorTex = nil;
    id<MTLTexture> scaledTransDepthTex = nil;

    // Hi-Z Occlusion Depth Pyramid (mip 0 = internalW/2 x internalH/2)
    int hizBaseW = 0;
    int hizBaseH = 0;
    int hizMipCount = 0;
    bool hizValid = false;
    id<MTLTexture> hizPyramidTex = nil;
    std::vector<id<MTLTexture>> hizMipViews;

    // MetalFX upscaler (spatial or temporal; classic MTLFX or MTL4FX), cached per size/mode.
    int mfxKind = MR_UPSCALE_NONE;
    bool mfxIsMtl4 = false;
    int mfxInW = 0;
    int mfxInH = 0;
    int mfxOutW = 0;
    int mfxOutH = 0;
    id mfxScaler = nil;               // id<MTLFXSpatialScaler|MTLFXTemporalScaler|MTL4FX...>
    id<MTLTexture> mfxOutputTex = nil; // private BGRA8 at output size (MetalFX requires private output)
    id<MTLTexture> motionTex = nil;    // RG16Float camera motion at render size
    bool temporalReset = true;
    bool supportsTemporalFX = false;
    bool supportsMetal4FX = false;
    int lastUpscaleUsed = MR_UPSCALE_NONE;

    // Copy of the OpenGL frame (sky) at render resolution. Scaled frames fill empty pixels with it
    // before upscaling, so terrain edges blend into the real sky instead of the clear colour.
    IOSurfaceRef bgSurface = nullptr;
    id<MTLTexture> bgTex = nil;
    int bgW = 0;
    int bgH = 0;
    int bgGlTex = 0;
    bool bgPending = false;
    bool lastBackgroundUsed = false;
    id<MTLComputePipelineState> csoFillBackground = nil;

    // Tier selection
    int activeTier = MR_TIER_COMPUTE_ICB;
    bool metal3Family = false;
    bool metal4Family = false;
    const char* libraryName = "";

    // Residency set (macOS 15+): one set holds every persistent allocation for both queue types.
    id residencySet = nil; // id<MTLResidencySet>
    NSArray* residentSnapshot = nil;

    // Metal 4 command model (macOS 26+). Typed as id so the dylib still loads on macOS 13.
    id queue4 = nil;      // id<MTL4CommandQueue>
    id cmdBuf4 = nil;     // id<MTL4CommandBuffer>
    id allocator4 = nil;  // id<MTL4CommandAllocator>
    id compiler4 = nil;   // id<MTL4Compiler>
    id tableVertex4 = nil, tableFragment4 = nil, tableObject4 = nil, tableMesh4 = nil, tableCompute4 = nil;
    id<MTLSharedEvent> frameEvent4 = nil;
    uint64_t frameEventValue4 = 0;
    id<MTLFence> fence4 = nil;
    id<MTLBuffer> constBuf4 = nil; // uniforms / flags staged for argument tables (passes are synchronous)
    std::shared_ptr<std::atomic<float>> gpuMsOpaque4 = std::make_shared<std::atomic<float>>(0.0f);
    std::shared_ptr<std::atomic<float>> gpuMsTrans4 = std::make_shared<std::atomic<float>>(0.0f);
    // Sign applied to jitter before handing it to MetalFX (verified by the native self-test).
    float jitterSign = 1.0f;

    // Triple-buffered IOSurface export rings
    SurfacePair opaqueSurfaces[kRingSlots];
    SurfacePair transSurfaces[kRingSlots];
    uint64_t frameCounter = 0;

    bool rebuildIcb(uint32_t maxCommands) {
        if (!supportsIcb || !csoCullIcb) {
            return false;
        }
        MTLIndirectCommandBufferDescriptor* icbDesc = [[MTLIndirectCommandBufferDescriptor alloc] init];
        icbDesc.commandTypes = MTLIndirectCommandTypeDrawIndexed;
        icbDesc.inheritPipelineState = YES;
        icbDesc.inheritBuffers = YES;
        icbDesc.maxVertexBufferBindCount = 0;
        icbDesc.maxFragmentBufferBindCount = 0;

        icbSolid = [device newIndirectCommandBufferWithDescriptor:icbDesc maxCommandCount:maxCommands options:0];
        icbCutout = [device newIndirectCommandBufferWithDescriptor:icbDesc maxCommandCount:maxCommands options:0];
        if (!icbSolid || !icbCutout) {
            icbSolid = nil;
            icbCutout = nil;
            return false;
        }

        id<MTLFunction> fnCull = [library newFunctionWithName:@"cullAndEncodeSections"];
        id<MTLArgumentEncoder> enc = [fnCull newArgumentEncoderWithBufferIndex:3];
        icbArgBuf = [device newBufferWithLength:enc.encodedLength options:MTLResourceStorageModeShared];
        [enc setArgumentBuffer:icbArgBuf offset:0];
        [enc setIndirectCommandBuffer:icbSolid atIndex:0];
        [enc setIndirectCommandBuffer:icbCutout atIndex:1];
        return true;
    }

    bool ensurePatternIndices(uint32_t minQuads) {
        if (minQuads <= patternQuadCap && patternIndexBuf != nil) {
            return true;
        }
        uint32_t newCap = patternQuadCap == 0 ? 65536u : patternQuadCap;
        while (newCap < minQuads) {
            newCap *= 2u;
        }
        size_t byteLen = (size_t)newCap * 6u * sizeof(uint32_t);
        id<MTLBuffer> buf = [device newBufferWithLength:byteLen options:MTLResourceStorageModeShared];
        if (!buf) {
            return false;
        }
        uint32_t* dst = static_cast<uint32_t*>([buf contents]);
        for (uint32_t q = 0; q < newCap; ++q) {
            uint32_t v = q * 4u;
            size_t i = (size_t)q * 6u;
            dst[i + 0] = v + 0u;
            dst[i + 1] = v + 1u;
            dst[i + 2] = v + 2u;
            dst[i + 3] = v + 2u;
            dst[i + 4] = v + 3u;
            dst[i + 5] = v + 0u;
        }
        patternIndexBuf = buf;
        patternQuadCap = newCap;
        return true;
    }

    bool ensureInternalTargets(int renderW, int renderH, bool scaled) {
        if (internalW != renderW || internalH != renderH || hwDepthTex == nil) {
            MTLTextureDescriptor* dDesc = [MTLTextureDescriptor
                texture2DDescriptorWithPixelFormat:MTLPixelFormatDepth32Float
                                             width:(NSUInteger)renderW
                                            height:(NSUInteger)renderH
                                         mipmapped:NO];
            dDesc.storageMode = MTLStorageModePrivate;
            dDesc.usage = MTLTextureUsageRenderTarget | MTLTextureUsageShaderRead;
            hwDepthTex = [device newTextureWithDescriptor:dDesc];
            if (!hwDepthTex) {
                return false;
            }

            // Rebuild Hi-Z pyramid
            hizBaseW = std::max(1, renderW / 2);
            hizBaseH = std::max(1, renderH / 2);
            int mips = 1;
            int mw = hizBaseW;
            int mh = hizBaseH;
            while (mw > 1 || mh > 1) {
                mw = std::max(1, mw / 2);
                mh = std::max(1, mh / 2);
                mips++;
            }
            hizMipCount = mips;
            MTLTextureDescriptor* hDesc = [MTLTextureDescriptor
                texture2DDescriptorWithPixelFormat:MTLPixelFormatR32Float
                                             width:(NSUInteger)hizBaseW
                                            height:(NSUInteger)hizBaseH
                                         mipmapped:YES];
            hDesc.mipmapLevelCount = (NSUInteger)hizMipCount;
            hDesc.storageMode = MTLStorageModePrivate;
            hDesc.usage = MTLTextureUsageShaderRead | MTLTextureUsageShaderWrite;
            hizPyramidTex = [device newTextureWithDescriptor:hDesc];
            hizMipViews.clear();
            for (int m = 0; m < hizMipCount; ++m) {
                id<MTLTexture> view = [hizPyramidTex
                    newTextureViewWithPixelFormat:MTLPixelFormatR32Float
                                      textureType:MTLTextureType2D
                                           levels:NSMakeRange((NSUInteger)m, 1)
                                           slices:NSMakeRange(0, 1)];
                hizMipViews.push_back(view);
            }
            hizValid = false;
            internalW = renderW;
            internalH = renderH;
            scaledOpaqueColorTex = nil;
            scaledOpaqueDepthTex = nil;
            scaledTransColorTex = nil;
            scaledTransDepthTex = nil;
        }

        if (scaled && (!scaledOpaqueColorTex || (int)scaledOpaqueColorTex.width != renderW || (int)scaledOpaqueColorTex.height != renderH)) {
            MTLTextureDescriptor* cDesc = [MTLTextureDescriptor
                texture2DDescriptorWithPixelFormat:MTLPixelFormatBGRA8Unorm
                                             width:(NSUInteger)renderW
                                            height:(NSUInteger)renderH
                                         mipmapped:NO];
            cDesc.storageMode = MTLStorageModePrivate;
            cDesc.usage = kColorUsage;

            MTLTextureDescriptor* zDesc = [MTLTextureDescriptor
                texture2DDescriptorWithPixelFormat:MTLPixelFormatR32Float
                                             width:(NSUInteger)renderW
                                            height:(NSUInteger)renderH
                                         mipmapped:NO];
            zDesc.storageMode = MTLStorageModePrivate;
            zDesc.usage = MTLTextureUsageRenderTarget | MTLTextureUsageShaderRead;

            scaledOpaqueColorTex = [device newTextureWithDescriptor:cDesc];
            scaledOpaqueDepthTex = [device newTextureWithDescriptor:zDesc];
            scaledTransColorTex = [device newTextureWithDescriptor:cDesc];
            scaledTransDepthTex = [device newTextureWithDescriptor:zDesc];
            if (!scaledOpaqueColorTex || !scaledOpaqueDepthTex || !scaledTransColorTex || !scaledTransDepthTex) {
                return false;
            }
        }
        return true;
    }

    // Keeps the residency set equal to the current persistent allocations. Buffers, IOSurface
    // textures and render targets are swapped on resize, so compare identities each pass.
    void syncResidency() {
        if (!residencySet) {
            return;
        }
        if (@available(macOS 15.0, *)) {
            NSMutableArray* now = [NSMutableArray arrayWithCapacity:32];
            auto add = [&](id obj) { if (obj) [now addObject:obj]; };
            add(vertexArenaBuf); add(sectionMetaBuf); add(meshletMetaBuf); add(patternIndexBuf);
            add(statsBuf); add(textureTableBuf); add(icbSolid); add(icbCutout); add(icbArgBuf); add(constBuf4);
            add(atlasTex); add(lightmapTex); add(hwDepthTex); add(hizPyramidTex);
            add(scaledOpaqueColorTex); add(scaledOpaqueDepthTex); add(scaledTransColorTex); add(scaledTransDepthTex);
            add(mfxOutputTex); add(motionTex); add(bgTex);
            for (int i = 0; i < kRingSlots; ++i) {
                add(opaqueSurfaces[i].colorTex); add(opaqueSurfaces[i].depthTex);
                add(transSurfaces[i].colorTex); add(transSurfaces[i].depthTex);
            }
            if (residentSnapshot && [now isEqualToArray:residentSnapshot]) {
                return;
            }
            id<MTLResidencySet> set = (id<MTLResidencySet>)residencySet;
            [set removeAllAllocations];
            for (id obj in now) {
                [set addAllocation:(id<MTLAllocation>)obj];
            }
            [set commit];
            residentSnapshot = now;
        }
    }

    static void warnUsage(const char* what, MTLTextureUsage need, MTLTextureUsage have) {
        if ((need & ~have) != 0) {
            fprintf(stderr, "[MetalRender] MetalFX %s texture usage 0x%lx missing bits 0x%lx\n",
                    what, (unsigned long)have, (unsigned long)(need & ~have));
        }
    }

    static id<MTLTexture> makePrivateTex(id<MTLDevice> dev, MTLPixelFormat fmt, int w, int h, MTLTextureUsage usage) {
        MTLTextureDescriptor* d = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:fmt
                                                                                     width:(NSUInteger)w
                                                                                    height:(NSUInteger)h
                                                                                 mipmapped:NO];
        d.storageMode = MTLStorageModePrivate;
        d.usage = usage;
        return [dev newTextureWithDescriptor:d];
    }

    // Creates (or reuses) the MetalFX scaler for this mode and size. mtl4 selects the MTL4FX variant,
    // which encodes into MTL4 command buffers and builds its pipelines through the MTL4 compiler.
    bool ensureUpscaler(int kind, int inW, int inH, int outW, int outH, bool mtl4) {
        if (kind == MR_UPSCALE_NONE) {
            return false;
        }
        if (!mfxOutputTex || (int)mfxOutputTex.width != outW || (int)mfxOutputTex.height != outH) {
            mfxOutputTex = makePrivateTex(device, MTLPixelFormatBGRA8Unorm, outW, outH, kColorUsage);
        }
        if (kind == MR_UPSCALE_TEMPORAL &&
            (!motionTex || (int)motionTex.width != inW || (int)motionTex.height != inH)) {
            motionTex = makePrivateTex(device, MTLPixelFormatRG16Float, inW, inH, kColorUsage);
        }
        if (!mfxOutputTex || (kind == MR_UPSCALE_TEMPORAL && !motionTex)) {
            return false;
        }
        if (mfxScaler != nil && mfxKind == kind && mfxIsMtl4 == mtl4 &&
            mfxInW == inW && mfxInH == inH && mfxOutW == outW && mfxOutH == outH) {
            return true;
        }
        mfxScaler = nil;
        mfxKind = MR_UPSCALE_NONE;
        temporalReset = true;

        id scaler = nil;
        if (@available(macOS 13.0, *)) {
            if (kind == MR_UPSCALE_SPATIAL && supportsMetalFX) {
                MTLFXSpatialScalerDescriptor* d = [[MTLFXSpatialScalerDescriptor alloc] init];
                d.inputWidth = (NSUInteger)inW;
                d.inputHeight = (NSUInteger)inH;
                d.outputWidth = (NSUInteger)outW;
                d.outputHeight = (NSUInteger)outH;
                d.colorTextureFormat = MTLPixelFormatBGRA8Unorm;
                d.outputTextureFormat = MTLPixelFormatBGRA8Unorm;
                d.colorProcessingMode = MTLFXSpatialScalerColorProcessingModePerceptual;
                if (mtl4) {
                    if (@available(macOS 26.0, *)) {
                        if (compiler4) {
                            scaler = [d newSpatialScalerWithDevice:device compiler:(id<MTL4Compiler>)compiler4];
                        }
                    }
                } else {
                    scaler = [d newSpatialScalerWithDevice:device];
                }
                if (scaler) {
                    id<MTLFXSpatialScalerBase> b = (id<MTLFXSpatialScalerBase>)scaler;
                    warnUsage("spatial color", b.colorTextureUsage, kColorUsage);
                    warnUsage("spatial output", b.outputTextureUsage, kColorUsage);
                }
            } else if (kind == MR_UPSCALE_TEMPORAL && supportsTemporalFX) {
                MTLFXTemporalScalerDescriptor* d = [[MTLFXTemporalScalerDescriptor alloc] init];
                d.inputWidth = (NSUInteger)inW;
                d.inputHeight = (NSUInteger)inH;
                d.outputWidth = (NSUInteger)outW;
                d.outputHeight = (NSUInteger)outH;
                d.colorTextureFormat = MTLPixelFormatBGRA8Unorm;
                d.depthTextureFormat = MTLPixelFormatDepth32Float;
                d.motionTextureFormat = MTLPixelFormatRG16Float;
                d.outputTextureFormat = MTLPixelFormatBGRA8Unorm;
                d.autoExposureEnabled = NO;
                if (mtl4) {
                    if (@available(macOS 26.0, *)) {
                        if (compiler4) {
                            scaler = [d newTemporalScalerWithDevice:device compiler:(id<MTL4Compiler>)compiler4];
                        }
                    }
                } else {
                    scaler = [d newTemporalScalerWithDevice:device];
                }
                if (scaler) {
                    id<MTLFXTemporalScalerBase> b = (id<MTLFXTemporalScalerBase>)scaler;
                    warnUsage("temporal color", b.colorTextureUsage, kColorUsage);
                    warnUsage("temporal depth", b.depthTextureUsage, kDepthUsage);
                    warnUsage("temporal motion", b.motionTextureUsage, kColorUsage);
                    warnUsage("temporal output", b.outputTextureUsage, kColorUsage);
                }
            }
        }
        if (!scaler) {
            return false;
        }
        mfxScaler = scaler;
        mfxKind = kind;
        mfxIsMtl4 = mtl4;
        mfxInW = inW;
        mfxInH = inH;
        mfxOutW = outW;
        mfxOutH = outH;
        return true;
    }

    // Encodes the cached MetalFX scaler into either an MTLCommandBuffer or an MTL4CommandBuffer.
    void encodeUpscaler(id cmdBuf, id<MTLTexture> color, int inW, int inH, const TemporalUniforms* tu) {
        if (@available(macOS 13.0, *)) {
            if (mfxKind == MR_UPSCALE_SPATIAL) {
                id<MTLFXSpatialScalerBase> b = (id<MTLFXSpatialScalerBase>)mfxScaler;
                b.colorTexture = color;
                b.outputTexture = mfxOutputTex;
                b.inputContentWidth = (NSUInteger)inW;
                b.inputContentHeight = (NSUInteger)inH;
                if (mfxIsMtl4) {
                    if (@available(macOS 26.0, *)) {
                        b.fence = fence4;
                        [(id<MTL4FXSpatialScaler>)mfxScaler encodeToCommandBuffer:(id<MTL4CommandBuffer>)cmdBuf];
                    }
                } else {
                    [(id<MTLFXSpatialScaler>)mfxScaler encodeToCommandBuffer:(id<MTLCommandBuffer>)cmdBuf];
                }
            } else if (mfxKind == MR_UPSCALE_TEMPORAL && tu) {
                id<MTLFXTemporalScalerBase> b = (id<MTLFXTemporalScalerBase>)mfxScaler;
                b.colorTexture = color;
                b.depthTexture = hwDepthTex;
                b.motionTexture = motionTex;
                b.outputTexture = mfxOutputTex;
                b.inputContentWidth = (NSUInteger)inW;
                b.inputContentHeight = (NSUInteger)inH;
                b.jitterOffsetX = jitterSign * tu->jitter.x;
                b.jitterOffsetY = jitterSign * tu->jitter.y;
                b.motionVectorScaleX = 1.0f;
                b.motionVectorScaleY = 1.0f;
                b.depthReversed = NO;
                b.reset = (temporalReset || tu->jitter.z > 0.5f) ? YES : NO;
                if (mfxIsMtl4) {
                    if (@available(macOS 26.0, *)) {
                        b.fence = fence4;
                        [(id<MTL4FXTemporalScaler>)mfxScaler encodeToCommandBuffer:(id<MTL4CommandBuffer>)cmdBuf];
                    }
                } else {
                    [(id<MTLFXTemporalScaler>)mfxScaler encodeToCommandBuffer:(id<MTLCommandBuffer>)cmdBuf];
                }
                temporalReset = false;
            }
        }
    }

    ~MetalContext() {
        for (int i = 0; i < kRingSlots; ++i) {
            opaqueSurfaces[i].destroy();
            transSurfaces[i].destroy();
        }
        hizMipViews.clear();
        mfxScaler = nil;
        bgTex = nil;
        if (bgSurface) {
            CFRelease(bgSurface);
            bgSurface = nullptr;
        }
    }
};

static id<MTLRenderPipelineState> createClassicPso(
    id<MTLDevice> dev,
    id<MTLLibrary> lib,
    NSString* fragName,
    bool translucent,
    bool supportIcb
) {
    id<MTLFunction> vertFn = [lib newFunctionWithName:@"terrainVertex"];
    id<MTLFunction> fragFn = [lib newFunctionWithName:fragName];
    if (!vertFn || !fragFn) {
        return nil;
    }
    MTLRenderPipelineDescriptor* desc = [[MTLRenderPipelineDescriptor alloc] init];
    desc.vertexFunction = vertFn;
    desc.fragmentFunction = fragFn;
    desc.depthAttachmentPixelFormat = MTLPixelFormatDepth32Float;
    desc.supportIndirectCommandBuffers = supportIcb ? YES : NO;

    desc.colorAttachments[0].pixelFormat = MTLPixelFormatBGRA8Unorm;
    desc.colorAttachments[1].pixelFormat = MTLPixelFormatR32Float;

    if (translucent) {
        // Premultiplied OVER blend on BGRA8 color(0)
        desc.colorAttachments[0].blendingEnabled = YES;
        desc.colorAttachments[0].rgbBlendOperation = MTLBlendOperationAdd;
        desc.colorAttachments[0].alphaBlendOperation = MTLBlendOperationAdd;
        desc.colorAttachments[0].sourceRGBBlendFactor = MTLBlendFactorOne;
        desc.colorAttachments[0].destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
        desc.colorAttachments[0].sourceAlphaBlendFactor = MTLBlendFactorOne;
        desc.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorOneMinusSourceAlpha;

        // Minimum window-space depth on R32Float color(1) (cleared to 1.0f)
        desc.colorAttachments[1].blendingEnabled = YES;
        desc.colorAttachments[1].rgbBlendOperation = MTLBlendOperationMin;
        desc.colorAttachments[1].alphaBlendOperation = MTLBlendOperationMin;
        desc.colorAttachments[1].sourceRGBBlendFactor = MTLBlendFactorOne;
        desc.colorAttachments[1].destinationRGBBlendFactor = MTLBlendFactorOne;
        desc.colorAttachments[1].sourceAlphaBlendFactor = MTLBlendFactorOne;
        desc.colorAttachments[1].destinationAlphaBlendFactor = MTLBlendFactorOne;
    }

    NSError* err = nil;
    id<MTLRenderPipelineState> pso = [dev newRenderPipelineStateWithDescriptor:desc error:&err];
    if (!pso && err) {
        fprintf(stderr, "[MetalRender] Failed to create classic PSO (%s): %s\n",
                [fragName UTF8String], [[err localizedDescription] UTF8String]);
    }
    return pso;
}

static id<MTLRenderPipelineState> createMeshPso(
    id<MTLDevice> dev,
    id<MTLLibrary> lib,
    NSString* fragName
) {
    if (@available(macOS 13.0, *)) {
        id<MTLFunction> objFn = [lib newFunctionWithName:@"terrainObjectStage"];
        id<MTLFunction> meshFn = [lib newFunctionWithName:@"terrainMeshStage"];
        id<MTLFunction> fragFn = [lib newFunctionWithName:fragName];
        if (!objFn || !meshFn || !fragFn) {
            return nil;
        }
        MTLMeshRenderPipelineDescriptor* desc = [[MTLMeshRenderPipelineDescriptor alloc] init];
        desc.objectFunction = objFn;
        desc.meshFunction = meshFn;
        desc.fragmentFunction = fragFn;
        desc.payloadMemoryLength = 32 * sizeof(uint32_t);
        desc.maxTotalThreadsPerObjectThreadgroup = 32;
        desc.maxTotalThreadsPerMeshThreadgroup = 64;
        desc.depthAttachmentPixelFormat = MTLPixelFormatDepth32Float;
        desc.colorAttachments[0].pixelFormat = MTLPixelFormatBGRA8Unorm;
        desc.colorAttachments[1].pixelFormat = MTLPixelFormatR32Float;

        NSError* err = nil;
        MTLAutoreleasedRenderPipelineReflection refl = nil;
        id<MTLRenderPipelineState> pso = [dev
            newRenderPipelineStateWithMeshDescriptor:desc
                                             options:MTLPipelineOptionNone
                                          reflection:&refl
                                               error:&err];
        if (!pso && err) {
            fprintf(stderr, "[MetalRender] Mesh PSO (%s) unavailable: %s\n",
                    [fragName UTF8String], [[err localizedDescription] UTF8String]);
        }
        return pso;
    }
    return nil;
}

static bool initMetal4(MetalContext* ctx) {
    if (@available(macOS 26.0, *)) {
        id<MTLDevice> dev = ctx->device;
        ctx->queue4 = [dev newMTL4CommandQueue];
        ctx->cmdBuf4 = [dev newCommandBuffer];
        ctx->allocator4 = [dev newCommandAllocator];
        NSError* err = nil;
        ctx->compiler4 = [dev newCompilerWithDescriptor:[[MTL4CompilerDescriptor alloc] init] error:&err];
        MTL4ArgumentTableDescriptor* td = [[MTL4ArgumentTableDescriptor alloc] init];
        td.maxBufferBindCount = 8;
        td.maxTextureBindCount = 4;
        td.maxSamplerStateBindCount = 0;
        td.initializeBindings = YES;
        ctx->tableVertex4 = [dev newArgumentTableWithDescriptor:td error:&err];
        ctx->tableFragment4 = [dev newArgumentTableWithDescriptor:td error:&err];
        ctx->tableObject4 = [dev newArgumentTableWithDescriptor:td error:&err];
        ctx->tableMesh4 = [dev newArgumentTableWithDescriptor:td error:&err];
        ctx->tableCompute4 = [dev newArgumentTableWithDescriptor:td error:&err];
        ctx->frameEvent4 = [dev newSharedEvent];
        ctx->fence4 = [dev newFence];
        ctx->constBuf4 = [dev newBufferWithLength:4096 options:MTLResourceStorageModeShared];
        bool ok = ctx->queue4 && ctx->cmdBuf4 && ctx->allocator4 && ctx->compiler4 && ctx->tableVertex4 &&
                  ctx->tableFragment4 && ctx->tableObject4 && ctx->tableMesh4 && ctx->tableCompute4 &&
                  ctx->frameEvent4 && ctx->fence4 && ctx->constBuf4 && ctx->residencySet;
        if (!ok) {
            fprintf(stderr, "[MetalRender] Metal 4 bring-up failed%s%s\n", err ? ": " : "",
                    err ? [[err localizedDescription] UTF8String] : "");
            return false;
        }
        // Metal 4 never tracks residency implicitly: everything the GPU touches lives in this set.
        [(id<MTL4CommandQueue>)ctx->queue4 addResidencySet:(id<MTLResidencySet>)ctx->residencySet];
        ctx->supportsMetal4FX = [MTLFXSpatialScalerDescriptor supportsMetal4FX:dev] &&
                                [MTLFXTemporalScalerDescriptor supportsMetal4FX:dev];
        return true;
    }
    return false;
}

static id<MTLLibrary> loadLibrary(id<MTLDevice> dev, NSString* dir, const char* name) {
    NSString* path = [dir stringByAppendingPathComponent:[NSString stringWithFormat:@"%s.metallib", name]];
    NSError* err = nil;
    id<MTLLibrary> lib = [dev newLibraryWithURL:[NSURL fileURLWithPath:path] error:&err];
    if (!lib) {
        fprintf(stderr, "[MetalRender] Could not load %s: %s\n", [path UTF8String],
                err ? [[err localizedDescription] UTF8String] : "unknown error");
    }
    return lib;
}

} // namespace

extern "C" {

void* mrCreateContext(const char* metallibDir, int requestedTier, int initialVertexCap, int initialSectionCap,
                      int initialMeshletCap) {
    @autoreleasepool {
        id<MTLDevice> dev = MTLCreateSystemDefaultDevice();
        if (!dev || !metallibDir) {
            return nullptr;
        }
        NSString* dir = [NSString stringWithUTF8String:metallibDir];
        bool metal3 = false, metal4 = false;
        if (@available(macOS 13.0, *)) metal3 = [dev supportsFamily:MTLGPUFamilyMetal3];
        if (@available(macOS 26.0, *)) metal4 = [dev supportsFamily:MTLGPUFamilyMetal4];

        // Newest shading language the OS + GPU can load: 4.1 (macOS 27) > 4.0 (macOS 26) > 3.0.
        bool wantMetal4 = metal4 && (requestedTier == MR_TIER_AUTO || requestedTier == MR_TIER_METAL4);
        const char* libName = "shaders_metal3";
        id<MTLLibrary> lib = nil;
        if (wantMetal4) {
            if (@available(macOS 27.0, *)) {
                libName = "shaders_metal4_1";
            } else {
                libName = "shaders_metal4";
            }
            lib = loadLibrary(dev, dir, libName);
            if (!lib) {
                wantMetal4 = false;
            }
        }
        if (!lib) {
            libName = "shaders_metal3";
            lib = loadLibrary(dev, dir, libName);
        }
        if (!lib) {
            return nullptr;
        }

        auto* ctx = new MetalContext();
        ctx->device = dev;
        ctx->queue = [dev newCommandQueue];
        ctx->library = lib;
        ctx->libraryName = libName;
        ctx->metal3Family = metal3;
        ctx->metal4Family = metal4;
        ctx->apple7 = [dev supportsFamily:MTLGPUFamilyApple7];
        ctx->apple8 = [dev supportsFamily:MTLGPUFamilyApple8];
        ctx->apple9 = [dev supportsFamily:MTLGPUFamilyApple9];
        ctx->supportsIcb = ([dev argumentBuffersSupport] != MTLArgumentBuffersTier1) || ctx->apple7;
        if (@available(macOS 13.0, *)) {
            ctx->supportsMetalFX = [MTLFXSpatialScalerDescriptor supportsDevice:dev];
            ctx->supportsTemporalFX = [MTLFXTemporalScalerDescriptor supportsDevice:dev];
        }
        if (@available(macOS 15.0, *)) {
            MTLResidencySetDescriptor* rd = [[MTLResidencySetDescriptor alloc] init];
            rd.label = @"MetalRender persistent";
            rd.initialCapacity = 64;
            NSError* rErr = nil;
            id<MTLResidencySet> set = [dev newResidencySetWithDescriptor:rd error:&rErr];
            if (set) {
                ctx->residencySet = set;
                [set requestResidency];
                [ctx->queue addResidencySet:set];
            }
        }

        auto makeCompute = [&](NSString* name) -> id<MTLComputePipelineState> {
            id<MTLFunction> fn = [lib newFunctionWithName:name];
            if (!fn) return nil;
            NSError* cErr = nil;
            id<MTLComputePipelineState> cso = [dev newComputePipelineStateWithFunction:fn error:&cErr];
            if (!cso && cErr) {
                fprintf(stderr, "[MetalRender] Compute PSO %s failed: %s\n",
                        [name UTF8String], [[cErr localizedDescription] UTF8String]);
            }
            return cso;
        };
        ctx->csoCullIcb = makeCompute(@"cullAndEncodeSections");
        ctx->csoBuildHiZMip0 = makeCompute(@"buildHiZMip0");
        ctx->csoDownsampleHiZ = makeCompute(@"downsampleHiZMip");
        ctx->csoUpscaleColorDepth = makeCompute(@"upscaleColorAndDepth");
        ctx->csoUpscaleDepthNearest = makeCompute(@"upscaleDepthNearest");
        ctx->csoFillBackground = makeCompute(@"fillBackground");
        ctx->csoCameraMotion = makeCompute(@"computeCameraMotion");

        ctx->psoSolid = createClassicPso(dev, lib, @"terrainFragmentSolid", false, ctx->supportsIcb);
        ctx->psoCutout = createClassicPso(dev, lib, @"terrainFragmentCutout", false, ctx->supportsIcb);
        ctx->psoTranslucent = createClassicPso(dev, lib, @"terrainFragmentTranslucent", true, false);
        if (!ctx->psoSolid || !ctx->psoCutout || !ctx->psoTranslucent || !ctx->csoUpscaleColorDepth ||
            !ctx->csoUpscaleDepthNearest || !ctx->csoFillBackground) {
            delete ctx;
            return nullptr;
        }
        if (metal3) {
            ctx->psoMeshSolid = createMeshPso(dev, lib, @"terrainFragmentSolid");
            ctx->psoMeshCutout = createMeshPso(dev, lib, @"terrainFragmentCutout");
            ctx->supportsMeshShaders = (ctx->psoMeshSolid != nil && ctx->psoMeshCutout != nil);
        }

        MTLDepthStencilDescriptor* dssOpaque = [[MTLDepthStencilDescriptor alloc] init];
        dssOpaque.depthCompareFunction = MTLCompareFunctionLessEqual;
        dssOpaque.depthWriteEnabled = YES;
        ctx->dssOpaqueWrite = [dev newDepthStencilStateWithDescriptor:dssOpaque];
        MTLDepthStencilDescriptor* dssTrans = [[MTLDepthStencilDescriptor alloc] init];
        dssTrans.depthCompareFunction = MTLCompareFunctionLessEqual;
        dssTrans.depthWriteEnabled = NO;
        ctx->dssTranslucentTest = [dev newDepthStencilStateWithDescriptor:dssTrans];

        ctx->vertexCap = (uint32_t)std::max(4096, initialVertexCap);
        ctx->vertexArenaBuf = [dev newBufferWithLength:(size_t)ctx->vertexCap * sizeof(MetalTerrainVertex)
                                               options:MTLResourceStorageModeShared];
        ctx->sectionCap = (uint32_t)std::max(256, initialSectionCap);
        ctx->sectionMetaBuf = [dev newBufferWithLength:(size_t)ctx->sectionCap * sizeof(SectionGpuMeta)
                                               options:MTLResourceStorageModeShared];
        std::memset([ctx->sectionMetaBuf contents], 0, [ctx->sectionMetaBuf length]);
        ctx->meshletCap = (uint32_t)std::max(1024, initialMeshletCap);
        ctx->meshletMetaBuf = [dev newBufferWithLength:(size_t)ctx->meshletCap * sizeof(MeshletGpuMeta)
                                               options:MTLResourceStorageModeShared];
        std::memset([ctx->meshletMetaBuf contents], 0, [ctx->meshletMetaBuf length]);
        ctx->statsBuf = [dev newBufferWithLength:sizeof(GpuCullStats) options:MTLResourceStorageModeShared];
        std::memset([ctx->statsBuf contents], 0, sizeof(GpuCullStats));
        ctx->textureTableBuf = [dev newBufferWithLength:64 options:MTLResourceStorageModeShared];
        if (!ctx->ensurePatternIndices(65536u)) {
            delete ctx;
            return nullptr;
        }
        if (ctx->supportsIcb && !ctx->rebuildIcb(ctx->sectionCap)) {
            ctx->supportsIcb = false;
        }

        // Default 1x1 white atlas and 16x16 white lightmap until the game uploads real ones.
        MTLTextureDescriptor* defAtlas = [MTLTextureDescriptor
            texture2DDescriptorWithPixelFormat:MTLPixelFormatRGBA8Unorm width:1 height:1 mipmapped:NO];
        defAtlas.storageMode = MTLStorageModeShared;
        defAtlas.usage = MTLTextureUsageShaderRead;
        ctx->atlasTex = [dev newTextureWithDescriptor:defAtlas];
        uint32_t whitePixel = 0xFFFFFFFFu;
        [ctx->atlasTex replaceRegion:MTLRegionMake2D(0, 0, 1, 1) mipmapLevel:0 withBytes:&whitePixel bytesPerRow:4];
        MTLTextureDescriptor* lmDesc = [MTLTextureDescriptor
            texture2DDescriptorWithPixelFormat:MTLPixelFormatRGBA8Unorm width:16 height:16 mipmapped:NO];
        lmDesc.storageMode = MTLStorageModeShared;
        lmDesc.usage = MTLTextureUsageShaderRead;
        ctx->lightmapTex = [dev newTextureWithDescriptor:lmDesc];
        std::vector<uint32_t> whiteLm(16 * 16, 0xFFFFFFFFu);
        [ctx->lightmapTex replaceRegion:MTLRegionMake2D(0, 0, 16, 16) mipmapLevel:0
                              withBytes:whiteLm.data() bytesPerRow:16 * 4];

        // Tier: Metal 4 > Metal 3 mesh > compute + ICB, capped by the request.
        if (wantMetal4 && ctx->supportsMeshShaders && initMetal4(ctx)) {
            ctx->activeTier = MR_TIER_METAL4;
        } else {
            ctx->queue4 = nil;
            if (ctx->supportsMeshShaders && requestedTier != MR_TIER_COMPUTE_ICB) {
                ctx->activeTier = MR_TIER_METAL3_MESH;
            } else {
                ctx->activeTier = MR_TIER_COMPUTE_ICB;
            }
        }
        ctx->syncResidency();
        return ctx;
    }
}

void mrDestroyContext(void* handle) {
    @autoreleasepool {
        auto* ctx = static_cast<MetalContext*>(handle);
        if (!ctx) return;
        if (@available(macOS 26.0, *)) {
            if (ctx->queue4 && ctx->frameEvent4) {
                uint64_t v = ++ctx->frameEventValue4;
                [(id<MTL4CommandQueue>)ctx->queue4 signalEvent:ctx->frameEvent4 value:v];
                [ctx->frameEvent4 waitUntilSignaledValue:v timeoutMS:5000];
            }
        }
        id<MTLCommandBuffer> fenceCmd = [ctx->queue commandBuffer];
        [fenceCmd commit];
        [fenceCmd waitUntilCompleted];
        if (@available(macOS 15.0, *)) {
            if (ctx->residencySet) {
                [(id<MTLResidencySet>)ctx->residencySet endResidency];
            }
        }
        delete ctx;
    }
}

int mrActiveTier(void* h) { return h ? static_cast<MetalContext*>(h)->activeTier : -1; }
const char* mrLibraryName(void* h) { return h ? static_cast<MetalContext*>(h)->libraryName : ""; }
int mrLastUpscaleUsed(void* h) { return h ? static_cast<MetalContext*>(h)->lastUpscaleUsed : 0; }
void mrSetJitterSign(void* h, float sign) { if (h) static_cast<MetalContext*>(h)->jitterSign = sign < 0.0f ? -1.0f : 1.0f; }

MetalTerrainVertex* mrVertexArena(void* h, uint32_t* cap) {
    auto* ctx = static_cast<MetalContext*>(h);
    if (cap) *cap = ctx->vertexCap;
    return static_cast<MetalTerrainVertex*>([ctx->vertexArenaBuf contents]);
}
SectionGpuMeta* mrSectionMeta(void* h, uint32_t* cap) {
    auto* ctx = static_cast<MetalContext*>(h);
    if (cap) *cap = ctx->sectionCap;
    return static_cast<SectionGpuMeta*>([ctx->sectionMetaBuf contents]);
}
MeshletGpuMeta* mrMeshletMeta(void* h, uint32_t* cap) {
    auto* ctx = static_cast<MetalContext*>(h);
    if (cap) *cap = ctx->meshletCap;
    return static_cast<MeshletGpuMeta*>([ctx->meshletMetaBuf contents]);
}
int mrEnsurePatternQuads(void* h, uint32_t quads) {
    return static_cast<MetalContext*>(h)->ensurePatternIndices(quads) ? 1 : 0;
}
void mrSetAtlasRgba(void* h, int w, int hgt, const void* rgba) {
    @autoreleasepool {
        auto* ctx = static_cast<MetalContext*>(h);
        MTLTextureDescriptor* d = [MTLTextureDescriptor
            texture2DDescriptorWithPixelFormat:MTLPixelFormatRGBA8Unorm width:(NSUInteger)w height:(NSUInteger)hgt mipmapped:NO];
        d.storageMode = MTLStorageModeShared;
        d.usage = MTLTextureUsageShaderRead;
        id<MTLTexture> tex = [ctx->device newTextureWithDescriptor:d];
        [tex replaceRegion:MTLRegionMake2D(0, 0, (NSUInteger)w, (NSUInteger)hgt) mipmapLevel:0
                 withBytes:rgba bytesPerRow:(NSUInteger)w * 4];
        ctx->atlasTex = tex;
    }
}
IOSurfaceRef mrSurface(void* h, int slot, int translucent, int depth) {
    auto* ctx = static_cast<MetalContext*>(h);
    if (!ctx || slot < 0 || slot >= kRingSlots) return nullptr;
    SurfacePair& p = translucent ? ctx->transSurfaces[slot] : ctx->opaqueSurfaces[slot];
    return depth ? p.depthSurface : p.colorSurface;
}
void mrReadStats(void* h, GpuCullStats* stats, float* opaqueMs, float* transMs) {
    auto* ctx = static_cast<MetalContext*>(h);
    if (stats) *stats = ctx->lastStats;
    if (opaqueMs) *opaqueMs = ctx->lastOpaqueGpuMs;
    if (transMs) *transMs = ctx->lastTransGpuMs;
}

size_t mrProbeCapabilities(char* out, size_t outLen) {
    @autoreleasepool {
        id<MTLDevice> dev = MTLCreateSystemDefaultDevice();
        NSOperatingSystemVersion os = [[NSProcessInfo processInfo] operatingSystemVersion];
        if (!dev) {
            return (size_t)std::snprintf(out, outLen, "metal=0;os=%ld.%ld", (long)os.majorVersion, (long)os.minorVersion);
        }
        int metal3 = 0, metal4 = 0, apple10 = 0, residency = 0, mtl4queue = 0, msl41 = 0;
        int fxSpatial = 0, fxTemporal = 0, fxDenoised = 0, fxInterp = 0, fx4Spatial = 0, fx4Temporal = 0, fx4Interp = 0;
        float temporalMin = 0.0f, temporalMax = 0.0f;
        if (@available(macOS 13.0, *)) {
            metal3 = [dev supportsFamily:MTLGPUFamilyMetal3];
            fxSpatial = [MTLFXSpatialScalerDescriptor supportsDevice:dev];
            fxTemporal = [MTLFXTemporalScalerDescriptor supportsDevice:dev];
        }
        if (@available(macOS 14.0, *)) {
            if (fxTemporal) {
                temporalMin = [MTLFXTemporalScalerDescriptor supportedInputContentMinScaleForDevice:dev];
                temporalMax = [MTLFXTemporalScalerDescriptor supportedInputContentMaxScaleForDevice:dev];
            }
        }
        if (@available(macOS 15.0, *)) residency = 1;
        if (@available(macOS 26.0, *)) {
            metal4 = [dev supportsFamily:MTLGPUFamilyMetal4];
            apple10 = [dev supportsFamily:MTLGPUFamilyApple10];
            mtl4queue = metal4;
            fxDenoised = [MTLFXTemporalDenoisedScalerDescriptor supportsDevice:dev];
            fxInterp = [MTLFXFrameInterpolatorDescriptor supportsDevice:dev];
            fx4Spatial = [MTLFXSpatialScalerDescriptor supportsMetal4FX:dev];
            fx4Temporal = [MTLFXTemporalScalerDescriptor supportsMetal4FX:dev];
            fx4Interp = [MTLFXFrameInterpolatorDescriptor supportsMetal4FX:dev];
        }
        if (@available(macOS 27.0, *)) msl41 = metal4;
        return (size_t)std::snprintf(out, outLen,
            "metal=1;device=%s;os=%ld.%ld;unifiedMemory=%d;apple7=%d;apple8=%d;apple9=%d;apple10=%d;"
            "metal3=%d;metal4=%d;msl41=%d;mtl4Queue=%d;residencySets=%d;meshShaders=%d;"
            "metalfxSpatial=%d;metalfxTemporal=%d;metalfxTemporalScale=%.3f-%.3f;metalfxDenoised=%d;"
            "metalfxFrameInterpolation=%d;mtl4fxSpatial=%d;mtl4fxTemporal=%d;mtl4fxFrameInterpolation=%d;workingSetMB=%llu",
            [[dev name] UTF8String], (long)os.majorVersion, (long)os.minorVersion, [dev hasUnifiedMemory] ? 1 : 0,
            (int)[dev supportsFamily:MTLGPUFamilyApple7], (int)[dev supportsFamily:MTLGPUFamilyApple8],
            (int)[dev supportsFamily:MTLGPUFamilyApple9], apple10, metal3, metal4, msl41, mtl4queue, residency,
            metal3, fxSpatial, fxTemporal, temporalMin, temporalMax, fxDenoised, fxInterp, fx4Spatial, fx4Temporal,
            fx4Interp, (unsigned long long)([dev recommendedMaxWorkingSetSize] / (1024ull * 1024ull)));
    }
}

JNIEXPORT jboolean JNICALL Java_ports_metalrender_nativebridge_MetalNative_isAvailable(JNIEnv*, jclass) {
    @autoreleasepool {
        return MTLCreateSystemDefaultDevice() != nil ? JNI_TRUE : JNI_FALSE;
    }
}

JNIEXPORT jstring JNICALL Java_ports_metalrender_nativebridge_MetalNative_probeCapabilities(JNIEnv* env, jclass) {
    char buf[1024];
    mrProbeCapabilities(buf, sizeof(buf));
    return env->NewStringUTF(buf);
}

JNIEXPORT jlong JNICALL Java_ports_metalrender_nativebridge_MetalNative_initContext(
    JNIEnv* env, jclass, jstring jMetallibDir, jint requestedTier, jint initialVertexCap, jint initialSectionCap,
    jint initialMeshletCap
) {
    if (!jMetallibDir) return 0;
    const char* dir = env->GetStringUTFChars(jMetallibDir, nullptr);
    void* ctx = mrCreateContext(dir, requestedTier, initialVertexCap, initialSectionCap, initialMeshletCap);
    env->ReleaseStringUTFChars(jMetallibDir, dir);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL Java_ports_metalrender_nativebridge_MetalNative_destroyContext(JNIEnv*, jclass, jlong ctxPtr) {
    mrDestroyContext(reinterpret_cast<void*>(ctxPtr));
}

JNIEXPORT jint JNICALL Java_ports_metalrender_nativebridge_MetalNative_getActiveTier(JNIEnv*, jclass, jlong ctxPtr) {
    return (jint)mrActiveTier(reinterpret_cast<void*>(ctxPtr));
}

JNIEXPORT jint JNICALL Java_ports_metalrender_nativebridge_MetalNative_getLastUpscaleMode(JNIEnv*, jclass, jlong ctxPtr) {
    return (jint)mrLastUpscaleUsed(reinterpret_cast<void*>(ctxPtr));
}

// name|unified|apple7|apple8|apple9|mesh|icb|metalfxSpatial|workingSetMB|tier|library|metalfxTemporal|mtl4fx|residency
JNIEXPORT jstring JNICALL Java_ports_metalrender_nativebridge_MetalNative_queryDeviceInfo(JNIEnv* env, jclass, jlong ctxPtr) {
    @autoreleasepool {
        auto* ctx = reinterpret_cast<MetalContext*>(ctxPtr);
        if (!ctx || !ctx->device) {
            return env->NewStringUTF("Unavailable|0|0|0|0|0|0|0|0|0||0|0|0");
        }
        char buf[512];
        std::snprintf(buf, sizeof(buf), "%s|%d|%d|%d|%d|%d|%d|%d|%llu|%d|%s|%d|%d|%d",
            [[ctx->device name] UTF8String], [ctx->device hasUnifiedMemory] ? 1 : 0, ctx->apple7 ? 1 : 0,
            ctx->apple8 ? 1 : 0, ctx->apple9 ? 1 : 0, ctx->supportsMeshShaders ? 1 : 0, ctx->supportsIcb ? 1 : 0,
            ctx->supportsMetalFX ? 1 : 0,
            (unsigned long long)([ctx->device recommendedMaxWorkingSetSize] / (1024ull * 1024ull)),
            ctx->activeTier, ctx->libraryName, ctx->supportsTemporalFX ? 1 : 0, ctx->supportsMetal4FX ? 1 : 0,
            ctx->residencySet ? 1 : 0);
        return env->NewStringUTF(buf);
    }
}

JNIEXPORT jobject JNICALL Java_ports_metalrender_nativebridge_MetalNative_getVertexArenaBuffer(
    JNIEnv* env,
    jclass,
    jlong ctxPtr
) {
    auto* ctx = reinterpret_cast<MetalContext*>(ctxPtr);
    if (!ctx || !ctx->vertexArenaBuf) return nullptr;
    return env->NewDirectByteBuffer([ctx->vertexArenaBuf contents], (jlong)[ctx->vertexArenaBuf length]);
}

JNIEXPORT jboolean JNICALL Java_ports_metalrender_nativebridge_MetalNative_resizeVertexArena(
    JNIEnv*,
    jclass,
    jlong ctxPtr,
    jint newVertexCap
) {
    @autoreleasepool {
        auto* ctx = reinterpret_cast<MetalContext*>(ctxPtr);
        if (!ctx || newVertexCap <= (jint)ctx->vertexCap) return JNI_TRUE;
        size_t newLen = (size_t)newVertexCap * sizeof(MetalTerrainVertex);
        id<MTLBuffer> next = [ctx->device newBufferWithLength:newLen options:MTLResourceStorageModeShared];
        if (!next) return JNI_FALSE;
        std::memcpy([next contents], [ctx->vertexArenaBuf contents], [ctx->vertexArenaBuf length]);
        ctx->vertexArenaBuf = next;
        ctx->vertexCap = (uint32_t)newVertexCap;
        return JNI_TRUE;
    }
}

JNIEXPORT jobject JNICALL Java_ports_metalrender_nativebridge_MetalNative_getSectionMetaBuffer(
    JNIEnv* env,
    jclass,
    jlong ctxPtr
) {
    auto* ctx = reinterpret_cast<MetalContext*>(ctxPtr);
    if (!ctx || !ctx->sectionMetaBuf) return nullptr;
    return env->NewDirectByteBuffer([ctx->sectionMetaBuf contents], (jlong)[ctx->sectionMetaBuf length]);
}

JNIEXPORT jboolean JNICALL Java_ports_metalrender_nativebridge_MetalNative_resizeSectionMeta(
    JNIEnv*,
    jclass,
    jlong ctxPtr,
    jint newSectionCap
) {
    @autoreleasepool {
        auto* ctx = reinterpret_cast<MetalContext*>(ctxPtr);
        if (!ctx || newSectionCap <= (jint)ctx->sectionCap) return JNI_TRUE;
        size_t oldLen = [ctx->sectionMetaBuf length];
        size_t newLen = (size_t)newSectionCap * sizeof(SectionGpuMeta);
        id<MTLBuffer> next = [ctx->device newBufferWithLength:newLen options:MTLResourceStorageModeShared];
        if (!next) return JNI_FALSE;
        std::memcpy([next contents], [ctx->sectionMetaBuf contents], oldLen);
        std::memset(static_cast<uint8_t*>([next contents]) + oldLen, 0, newLen - oldLen);
        ctx->sectionMetaBuf = next;
        ctx->sectionCap = (uint32_t)newSectionCap;
        if (ctx->supportsIcb) {
            ctx->rebuildIcb(ctx->sectionCap);
        }
        return JNI_TRUE;
    }
}

JNIEXPORT jobject JNICALL Java_ports_metalrender_nativebridge_MetalNative_getMeshletMetaBuffer(
    JNIEnv* env,
    jclass,
    jlong ctxPtr
) {
    auto* ctx = reinterpret_cast<MetalContext*>(ctxPtr);
    if (!ctx || !ctx->meshletMetaBuf) return nullptr;
    return env->NewDirectByteBuffer([ctx->meshletMetaBuf contents], (jlong)[ctx->meshletMetaBuf length]);
}

JNIEXPORT jboolean JNICALL Java_ports_metalrender_nativebridge_MetalNative_resizeMeshletMeta(
    JNIEnv*,
    jclass,
    jlong ctxPtr,
    jint newMeshletCap
) {
    @autoreleasepool {
        auto* ctx = reinterpret_cast<MetalContext*>(ctxPtr);
        if (!ctx || newMeshletCap <= (jint)ctx->meshletCap) return JNI_TRUE;
        size_t oldLen = [ctx->meshletMetaBuf length];
        size_t newLen = (size_t)newMeshletCap * sizeof(MeshletGpuMeta);
        id<MTLBuffer> next = [ctx->device newBufferWithLength:newLen options:MTLResourceStorageModeShared];
        if (!next) return JNI_FALSE;
        std::memcpy([next contents], [ctx->meshletMetaBuf contents], oldLen);
        std::memset(static_cast<uint8_t*>([next contents]) + oldLen, 0, newLen - oldLen);
        ctx->meshletMetaBuf = next;
        ctx->meshletCap = (uint32_t)newMeshletCap;
        return JNI_TRUE;
    }
}

JNIEXPORT void JNICALL Java_ports_metalrender_nativebridge_MetalNative_ensurePatternQuadCapacity(
    JNIEnv*,
    jclass,
    jlong ctxPtr,
    jint minQuads
) {
    @autoreleasepool {
        auto* ctx = reinterpret_cast<MetalContext*>(ctxPtr);
        if (!ctx || minQuads <= 0) return;
        ctx->ensurePatternIndices((uint32_t)minQuads);
    }
}

JNIEXPORT void JNICALL Java_ports_metalrender_nativebridge_MetalNative_initAtlasTexture(
    JNIEnv*,
    jclass,
    jlong ctxPtr,
    jint width,
    jint height,
    jint mipLevels
) {
    @autoreleasepool {
        auto* ctx = reinterpret_cast<MetalContext*>(ctxPtr);
        if (!ctx || width <= 0 || height <= 0) return;
        int mips = std::max(1, (int)mipLevels);
        int maxPossible = 1 + (int)std::floor(std::log2((double)std::max(width, height)));
        mips = std::min(mips, maxPossible);

        MTLTextureDescriptor* desc = [MTLTextureDescriptor
            texture2DDescriptorWithPixelFormat:MTLPixelFormatRGBA8Unorm
                                         width:(NSUInteger)width
                                        height:(NSUInteger)height
                                     mipmapped:(mips > 1 ? YES : NO)];
        desc.mipmapLevelCount = (NSUInteger)mips;
        desc.storageMode = MTLStorageModeShared;
        desc.usage = MTLTextureUsageShaderRead;
        id<MTLTexture> tex = [ctx->device newTextureWithDescriptor:desc];
        if (tex) {
            ctx->atlasTex = tex;
        }
    }
}

JNIEXPORT void JNICALL Java_ports_metalrender_nativebridge_MetalNative_uploadAtlasRegion(
    JNIEnv*,
    jclass,
    jlong ctxPtr,
    jint mipLevel,
    jint dstX,
    jint dstY,
    jint srcX,
    jint srcY,
    jint width,
    jint height,
    jint srcRowPixels,
    jlong nativePixelsPtr
) {
    @autoreleasepool {
        auto* ctx = reinterpret_cast<MetalContext*>(ctxPtr);
        if (!ctx || !ctx->atlasTex || nativePixelsPtr == 0 || width <= 0 || height <= 0) return;
        if (mipLevel < 0 || (NSUInteger)mipLevel >= ctx->atlasTex.mipmapLevelCount) return;

        NSUInteger mipW = std::max<NSUInteger>(1, ctx->atlasTex.width >> mipLevel);
        NSUInteger mipH = std::max<NSUInteger>(1, ctx->atlasTex.height >> mipLevel);
        if ((NSUInteger)dstX >= mipW || (NSUInteger)dstY >= mipH) return;
        NSUInteger clampedW = std::min<NSUInteger>((NSUInteger)width, mipW - (NSUInteger)dstX);
        NSUInteger clampedH = std::min<NSUInteger>((NSUInteger)height, mipH - (NSUInteger)dstY);
        if (clampedW == 0 || clampedH == 0) return;

        const uint8_t* base = reinterpret_cast<const uint8_t*>(nativePixelsPtr);
        size_t strideBytes = (size_t)srcRowPixels * 4u;
        const uint8_t* srcOffset = base + ((size_t)srcY * (size_t)srcRowPixels + (size_t)srcX) * 4u;

        [ctx->atlasTex replaceRegion:MTLRegionMake2D((NSUInteger)dstX, (NSUInteger)dstY, clampedW, clampedH)
                         mipmapLevel:(NSUInteger)mipLevel
                           withBytes:srcOffset
                         bytesPerRow:strideBytes];
    }
}

JNIEXPORT void JNICALL Java_ports_metalrender_nativebridge_MetalNative_uploadLightmap(
    JNIEnv*,
    jclass,
    jlong ctxPtr,
    jlong nativePixelsPtr
) {
    @autoreleasepool {
        auto* ctx = reinterpret_cast<MetalContext*>(ctxPtr);
        if (!ctx || !ctx->lightmapTex || nativePixelsPtr == 0) return;
        const void* src = reinterpret_cast<const void*>(nativePixelsPtr);
        [ctx->lightmapTex replaceRegion:MTLRegionMake2D(0, 0, 16, 16)
                            mipmapLevel:0
                              withBytes:src
                            bytesPerRow:16 * 4];
    }
}

// ---------------------------------------------------------------------------------------------
// Opaque + translucent terrain passes. Shared preparation feeds either the classic MTLCommandQueue
// encoder (Compute+ICB and Metal 3 mesh tiers) or the Metal 4 encoder (MTL4CommandQueue, command
// allocator, argument tables, explicit barriers, residency set, MTL4FX upscalers).
// ---------------------------------------------------------------------------------------------

struct OpaqueFrame {
    int slot = 0;
    int outW = 0, outH = 0, renderW = 0, renderH = 0;
    bool useInternal = false; // render into internal targets, then upscale into the IOSurface
    bool useBackground = false; // fill empty internal pixels from bgTex before upscaling
    bool buildHiZ = false;      // only when the caller asked for Hi-Z occlusion culling
    int upscale = MR_UPSCALE_NONE;
    bool meshPath = false, icbPath = false;
    uint32_t secLimit = 0, meshletLimit = 0;
    FrameUniforms u{};
    TemporalUniforms tu{};
    bool hasTemporal = false;
    id<MTLTexture> targetColor = nil;
    id<MTLTexture> targetZExp = nil;
};

static MTLSize gridFor(int w, int h) {
    return MTLSizeMake(((NSUInteger)w + 15) / 16, ((NSUInteger)h + 15) / 16, 1);
}

static bool prepareOpaque(MetalContext* ctx, int outW, int outH, float scale, bool useMesh, int upscaleMode,
                          const FrameUniforms* uIn, const TemporalUniforms* tuIn, int secCap, int meshlets,
                          OpaqueFrame& f) {
    f.slot = (int)((ctx->frameCounter++) % (uint64_t)kRingSlots);
    f.outW = outW;
    f.outH = outH;
    if (!ensureSurfacePair(ctx->device, ctx->opaqueSurfaces[f.slot], outW, outH)) {
        return false;
    }
    float clampedScale = std::max(0.5f, std::min(1.0f, scale));
    f.renderW = std::max(1, (int)std::lround((double)outW * (double)clampedScale));
    f.renderH = std::max(1, (int)std::lround((double)outH * (double)clampedScale));
    bool isScaled = (f.renderW != outW || f.renderH != outH);
    bool metal4 = ctx->activeTier == MR_TIER_METAL4;

    // Resolve the upscaler actually usable this frame (temporal can run at 1.0x as MetalFX TAA).
    int mode = upscaleMode;
    if (mode == MR_UPSCALE_TEMPORAL && (!tuIn || !ctx->supportsTemporalFX || !ctx->csoCameraMotion)) {
        mode = MR_UPSCALE_SPATIAL;
    }
    if (mode == MR_UPSCALE_SPATIAL && (!isScaled || !ctx->supportsMetalFX)) {
        mode = MR_UPSCALE_NONE;
    }
    if (mode != MR_UPSCALE_NONE && metal4 && !ctx->supportsMetal4FX) {
        mode = MR_UPSCALE_NONE;
    }
    f.useInternal = isScaled || mode == MR_UPSCALE_TEMPORAL;
    if (!ctx->ensureInternalTargets(f.renderW, f.renderH, f.useInternal)) {
        return false;
    }
    if (mode != MR_UPSCALE_NONE && !ctx->ensureUpscaler(mode, f.renderW, f.renderH, outW, outH, metal4)) {
        mode = MR_UPSCALE_NONE;
        if (!isScaled) {
            f.useInternal = false;
        }
    }
    f.upscale = mode;

    f.u = *uIn;
    f.secLimit = std::min<uint32_t>((uint32_t)std::max(0, secCap), ctx->sectionCap);
    f.meshletLimit = std::min<uint32_t>((uint32_t)std::max(0, meshlets), ctx->meshletCap);
    f.u.counts.x = f.secLimit;
    f.u.counts.y = f.meshletLimit;
    f.u.counts.z = ctx->hizValid ? (uint32_t)ctx->hizBaseW : 0u;
    f.u.counts.w = ctx->hizValid ? (uint32_t)ctx->hizBaseH : 0u;
    if (!ctx->hizValid) {
        f.u.lodAndFlags.w = 0u;
    }
    f.u.screenParams.x = (float)f.renderW;
    f.u.screenParams.y = (float)f.renderH;
    f.u.screenParams.z = (float)ctx->hizMipCount;
    if (tuIn) {
        f.tu = *tuIn;
        f.hasTemporal = true;
    }

    f.meshPath = useMesh && ctx->supportsMeshShaders && ctx->activeTier != MR_TIER_COMPUTE_ICB;
    f.icbPath = !f.meshPath && !metal4 && ctx->supportsIcb && ctx->icbSolid != nil && ctx->icbCutout != nil;
    f.targetColor = f.useInternal ? ctx->scaledOpaqueColorTex : ctx->opaqueSurfaces[f.slot].colorTex;
    f.targetZExp = f.useInternal ? ctx->scaledOpaqueDepthTex : ctx->opaqueSurfaces[f.slot].depthTex;
    f.useBackground = ctx->bgPending && f.useInternal && ctx->bgTex != nil &&
                      ctx->bgW == f.renderW && ctx->bgH == f.renderH;
    ctx->bgPending = false;
    ctx->lastBackgroundUsed = f.useBackground;
    f.buildHiZ = uIn->lodAndFlags.w != 0u;
    if (!f.buildHiZ) {
        ctx->hizValid = false; // a pyramid left from earlier frames would be stale when re-enabled
    }
    std::memset([ctx->statsBuf contents], 0, sizeof(GpuCullStats));
    ctx->updateTextureTable();
    ctx->syncResidency();
    return true;
}

static void fillOpaqueAttachments(MTLRenderPassColorAttachmentDescriptorArray* colors,
                                  MTLRenderPassDepthAttachmentDescriptor* depth,
                                  const OpaqueFrame& f, id<MTLTexture> hwDepth) {
    colors[0].texture = f.targetColor;
    colors[0].loadAction = MTLLoadActionClear;
    colors[0].storeAction = MTLStoreActionStore;
    colors[0].clearColor = MTLClearColorMake(0.0, 0.0, 0.0, 0.0);
    colors[1].texture = f.targetZExp;
    colors[1].loadAction = MTLLoadActionClear;
    colors[1].storeAction = MTLStoreActionStore;
    colors[1].clearColor = MTLClearColorMake(1.0, 0.0, 0.0, 0.0);
    depth.texture = hwDepth;
    depth.loadAction = MTLLoadActionClear;
    depth.storeAction = MTLStoreActionStore;
    depth.clearDepth = 1.0;
}

static void encodeOpaqueClassic(MetalContext* ctx, OpaqueFrame& f) {
    FrameUniforms& u = f.u;
    id<MTLCommandBuffer> cmdBuf = [ctx->queue commandBuffer];

    if (f.icbPath && f.secLimit > 0u) {
        id<MTLComputeCommandEncoder> cEnc = [cmdBuf computeCommandEncoder];
        [cEnc setComputePipelineState:ctx->csoCullIcb];
        [cEnc setBuffer:ctx->sectionMetaBuf offset:0 atIndex:0];
        [cEnc setBytes:&u length:sizeof(FrameUniforms) atIndex:1];
        [cEnc setBuffer:ctx->patternIndexBuf offset:0 atIndex:2];
        [cEnc setBuffer:ctx->icbArgBuf offset:0 atIndex:3];
        [cEnc setBuffer:ctx->statsBuf offset:0 atIndex:4];
        [cEnc setTexture:ctx->hizPyramidTex atIndex:0];
        [cEnc useResource:ctx->icbSolid usage:MTLResourceUsageWrite];
        [cEnc useResource:ctx->icbCutout usage:MTLResourceUsageWrite];
        [cEnc useResource:ctx->patternIndexBuf usage:MTLResourceUsageRead];
        NSUInteger tgSize = std::min<NSUInteger>(64, ctx->csoCullIcb.maxTotalThreadsPerThreadgroup);
        NSUInteger groups = ((NSUInteger)f.secLimit + tgSize - 1) / tgSize;
        [cEnc dispatchThreadgroups:MTLSizeMake(groups, 1, 1) threadsPerThreadgroup:MTLSizeMake(tgSize, 1, 1)];
        [cEnc endEncoding];
    }

    MTLRenderPassDescriptor* rp = [MTLRenderPassDescriptor renderPassDescriptor];
    fillOpaqueAttachments(rp.colorAttachments, rp.depthAttachment, f, ctx->hwDepthTex);
    id<MTLRenderCommandEncoder> rEnc = [cmdBuf renderCommandEncoderWithDescriptor:rp];
    [rEnc setViewport:(MTLViewport){0.0, 0.0, (double)f.renderW, (double)f.renderH, 0.0, 1.0}];
    [rEnc setFrontFacingWinding:MTLWindingCounterClockwise];
    [rEnc setCullMode:MTLCullModeBack];
    [rEnc setDepthStencilState:ctx->dssOpaqueWrite];
    [rEnc setFragmentBytes:&u length:sizeof(FrameUniforms) atIndex:2];
    [rEnc setFragmentBuffer:ctx->textureTableBuf offset:0 atIndex:3];
    [rEnc useResource:ctx->atlasTex usage:MTLResourceUsageRead stages:MTLRenderStageFragment];
    [rEnc useResource:ctx->lightmapTex usage:MTLResourceUsageRead stages:MTLRenderStageFragment];

    if (f.meshPath) {
        if (@available(macOS 13.0, *)) {
            if (f.meshletLimit > 0u) {
                [rEnc setMeshBuffer:ctx->vertexArenaBuf offset:0 atIndex:0];
                [rEnc setMeshBuffer:ctx->meshletMetaBuf offset:0 atIndex:1];
                [rEnc setMeshBuffer:ctx->sectionMetaBuf offset:0 atIndex:2];
                [rEnc setMeshBytes:&u length:sizeof(FrameUniforms) atIndex:3];
                [rEnc setObjectBuffer:ctx->meshletMetaBuf offset:0 atIndex:1];
                [rEnc setObjectBuffer:ctx->sectionMetaBuf offset:0 atIndex:2];
                [rEnc setObjectBytes:&u length:sizeof(FrameUniforms) atIndex:3];
                [rEnc setObjectBuffer:ctx->statsBuf offset:0 atIndex:5];
                [rEnc setObjectTexture:ctx->hizPyramidTex atIndex:0];
                NSUInteger objGroups = ((NSUInteger)f.meshletLimit + 31u) / 32u;
                const uint32_t flags[2] = {0u, 1u};
                id<MTLRenderPipelineState> psos[2] = {ctx->psoMeshSolid, ctx->psoMeshCutout};
                for (int pass = 0; pass < 2; ++pass) {
                    [rEnc setRenderPipelineState:psos[pass]];
                    [rEnc setObjectBytes:&flags[pass] length:sizeof(uint32_t) atIndex:4];
                    [rEnc drawMeshThreadgroups:MTLSizeMake(objGroups, 1, 1)
                   threadsPerObjectThreadgroup:MTLSizeMake(32, 1, 1)
                     threadsPerMeshThreadgroup:MTLSizeMake(64, 1, 1)];
                }
            }
        }
    } else if (f.secLimit > 0u) {
        [rEnc setVertexBuffer:ctx->vertexArenaBuf offset:0 atIndex:0];
        [rEnc setVertexBuffer:ctx->sectionMetaBuf offset:0 atIndex:1];
        [rEnc setVertexBytes:&u length:sizeof(FrameUniforms) atIndex:2];
        if (f.icbPath) {
            [rEnc useResource:ctx->vertexArenaBuf usage:MTLResourceUsageRead stages:MTLRenderStageVertex];
            [rEnc useResource:ctx->sectionMetaBuf usage:MTLResourceUsageRead stages:MTLRenderStageVertex];
            [rEnc useResource:ctx->patternIndexBuf usage:MTLResourceUsageRead stages:MTLRenderStageVertex];
            [rEnc setRenderPipelineState:ctx->psoSolid];
            [rEnc executeCommandsInBuffer:ctx->icbSolid withRange:NSMakeRange(0, (NSUInteger)f.secLimit)];
            [rEnc setRenderPipelineState:ctx->psoCutout];
            [rEnc executeCommandsInBuffer:ctx->icbCutout withRange:NSMakeRange(0, (NSUInteger)f.secLimit)];
        } else {
            // Direct CPU loop when neither mesh shaders nor ICBs are usable.
            const auto* secs = static_cast<const SectionGpuMeta*>([ctx->sectionMetaBuf contents]);
            for (int pass = 0; pass < 2; ++pass) {
                [rEnc setRenderPipelineState:pass == 0 ? ctx->psoSolid : ctx->psoCutout];
                for (uint32_t i = 0; i < f.secLimit; ++i) {
                    uint32_t quads = pass == 0 ? secs[i].solidQuadCount : secs[i].cutoutQuadCount;
                    uint32_t base = pass == 0 ? secs[i].solidBaseVertex : secs[i].cutoutBaseVertex;
                    if ((secs[i].flags & MR_SECTION_ACTIVE) == 0u || quads == 0u) continue;
                    [rEnc drawIndexedPrimitives:MTLPrimitiveTypeTriangle
                                     indexCount:quads * 6u
                                      indexType:MTLIndexTypeUInt32
                                    indexBuffer:ctx->patternIndexBuf
                              indexBufferOffset:0
                                  instanceCount:1
                                     baseVertex:(NSInteger)base
                                   baseInstance:i];
                }
            }
        }
    }
    [rEnc endEncoding];

    // Background fill for scaled frames, Hi-Z pyramid for next frame's occlusion culling, plus
    // camera motion for MetalFX temporal.
    id<MTLComputeCommandEncoder> hEnc = [cmdBuf computeCommandEncoder];
    if (f.useBackground) {
        [hEnc setComputePipelineState:ctx->csoFillBackground];
        [hEnc setTexture:f.targetColor atIndex:0];
        [hEnc setTexture:f.targetZExp atIndex:1];
        [hEnc setTexture:ctx->bgTex atIndex:2];
        [hEnc dispatchThreadgroups:gridFor(f.renderW, f.renderH) threadsPerThreadgroup:MTLSizeMake(16, 16, 1)];
    }
    if (f.buildHiZ && ctx->csoBuildHiZMip0 && ctx->csoDownsampleHiZ && !ctx->hizMipViews.empty()) {
        [hEnc setComputePipelineState:ctx->csoBuildHiZMip0];
        [hEnc setTexture:f.targetZExp atIndex:0];
        [hEnc setTexture:ctx->hizMipViews[0] atIndex:1];
        [hEnc dispatchThreadgroups:gridFor(ctx->hizBaseW, ctx->hizBaseH) threadsPerThreadgroup:MTLSizeMake(16, 16, 1)];
        [hEnc setComputePipelineState:ctx->csoDownsampleHiZ];
        int mw = ctx->hizBaseW, mh = ctx->hizBaseH;
        for (int m = 1; m < ctx->hizMipCount; ++m) {
            mw = std::max(1, mw / 2);
            mh = std::max(1, mh / 2);
            [hEnc setTexture:ctx->hizMipViews[(size_t)(m - 1)] atIndex:0];
            [hEnc setTexture:ctx->hizMipViews[(size_t)m] atIndex:1];
            [hEnc dispatchThreadgroups:gridFor(mw, mh) threadsPerThreadgroup:MTLSizeMake(16, 16, 1)];
        }
        ctx->hizValid = true;
    }
    if (f.upscale == MR_UPSCALE_TEMPORAL) {
        [hEnc setComputePipelineState:ctx->csoCameraMotion];
        [hEnc setTexture:ctx->hwDepthTex atIndex:0];
        [hEnc setTexture:ctx->motionTex atIndex:1];
        [hEnc setBytes:&f.tu length:sizeof(TemporalUniforms) atIndex:0];
        [hEnc dispatchThreadgroups:gridFor(f.renderW, f.renderH) threadsPerThreadgroup:MTLSizeMake(16, 16, 1)];
    }
    [hEnc endEncoding];

    SurfacePair& surf = ctx->opaqueSurfaces[f.slot];
    if (f.upscale != MR_UPSCALE_NONE) {
        ctx->encodeUpscaler(cmdBuf, f.targetColor, f.renderW, f.renderH, f.hasTemporal ? &f.tu : nullptr);
        id<MTLBlitCommandEncoder> blit = [cmdBuf blitCommandEncoder];
        [blit copyFromTexture:ctx->mfxOutputTex toTexture:surf.colorTex];
        [blit endEncoding];
        id<MTLComputeCommandEncoder> uEnc = [cmdBuf computeCommandEncoder];
        [uEnc setComputePipelineState:ctx->csoUpscaleDepthNearest];
        [uEnc setTexture:f.targetZExp atIndex:0];
        [uEnc setTexture:surf.depthTex atIndex:1];
        [uEnc dispatchThreadgroups:gridFor(f.outW, f.outH) threadsPerThreadgroup:MTLSizeMake(16, 16, 1)];
        [uEnc endEncoding];
    } else if (f.useInternal) {
        id<MTLComputeCommandEncoder> uEnc = [cmdBuf computeCommandEncoder];
        [uEnc setComputePipelineState:ctx->csoUpscaleColorDepth];
        [uEnc setTexture:f.targetColor atIndex:0];
        [uEnc setTexture:f.targetZExp atIndex:1];
        [uEnc setTexture:surf.colorTex atIndex:2];
        [uEnc setTexture:surf.depthTex atIndex:3];
        [uEnc dispatchThreadgroups:gridFor(f.outW, f.outH) threadsPerThreadgroup:MTLSizeMake(16, 16, 1)];
        [uEnc endEncoding];
    }

    [cmdBuf commit];
    [cmdBuf waitUntilCompleted];
    if (cmdBuf.error) {
        fprintf(stderr, "[MetalRender] opaque pass failed: %s\n", [[cmdBuf.error localizedDescription] UTF8String]);
    }
    double gpuSec = [cmdBuf GPUEndTime] - [cmdBuf GPUStartTime];
    ctx->lastOpaqueGpuMs = gpuSec > 0.0 ? (float)(gpuSec * 1000.0) : 0.0f;
}

// Offsets inside constBuf4 (passes are synchronous, so one staging buffer is enough).
constexpr NSUInteger kC4OpaqueUniforms = 0;
constexpr NSUInteger kC4Temporal = 512;
constexpr NSUInteger kC4CutoutFlags = 1024;
constexpr NSUInteger kC4TransUniforms = 2048;

static bool submitMetal4(MetalContext* ctx, id cbObj, std::shared_ptr<std::atomic<float>> gpuMs) API_AVAILABLE(macos(26.0)) {
    id<MTL4CommandQueue> q = (id<MTL4CommandQueue>)ctx->queue4;
    id<MTL4CommandBuffer> cb = (id<MTL4CommandBuffer>)cbObj;
    [cb endCommandBuffer];
    // Commit feedback fires once the GPU finishes, so it doubles as the completion wait and carries
    // the GPU timestamps (MTL4 command buffers have no waitUntilCompleted).
    dispatch_semaphore_t done = dispatch_semaphore_create(0);
    MTL4CommitOptions* opts = [[MTL4CommitOptions alloc] init];
    [opts addFeedbackHandler:^(id<MTL4CommitFeedback> fb) {
        if (fb.error) {
            fprintf(stderr, "[MetalRender] Metal 4 submission failed: %s\n", [[fb.error localizedDescription] UTF8String]);
        }
        double s = fb.GPUEndTime - fb.GPUStartTime;
        gpuMs->store(s > 0.0 ? (float)(s * 1000.0) : 0.0f);
        dispatch_semaphore_signal(done);
    }];
    id<MTL4CommandBuffer> list[1] = {cb};
    [q commit:list count:1 options:opts];
    return dispatch_semaphore_wait(done, dispatch_time(DISPATCH_TIME_NOW, 5 * NSEC_PER_SEC)) == 0;
}

static void encodeOpaqueMetal4(MetalContext* ctx, OpaqueFrame& f) {
    if (@available(macOS 26.0, *)) {
        id<MTL4CommandBuffer> cb = (id<MTL4CommandBuffer>)ctx->cmdBuf4;
        id<MTL4CommandAllocator> alloc = (id<MTL4CommandAllocator>)ctx->allocator4;
        id<MTL4ArgumentTable> tObj = (id<MTL4ArgumentTable>)ctx->tableObject4;
        id<MTL4ArgumentTable> tMesh = (id<MTL4ArgumentTable>)ctx->tableMesh4;
        id<MTL4ArgumentTable> tVert = (id<MTL4ArgumentTable>)ctx->tableVertex4;
        id<MTL4ArgumentTable> tFrag = (id<MTL4ArgumentTable>)ctx->tableFragment4;
        id<MTL4ArgumentTable> tComp = (id<MTL4ArgumentTable>)ctx->tableCompute4;

        uint8_t* staging = static_cast<uint8_t*>([ctx->constBuf4 contents]);
        std::memcpy(staging + kC4OpaqueUniforms, &f.u, sizeof(FrameUniforms));
        std::memcpy(staging + kC4Temporal, &f.tu, sizeof(TemporalUniforms));
        const uint32_t flags[2] = {0u, 1u};
        std::memcpy(staging + kC4CutoutFlags, flags, sizeof(flags));
        MTLGPUAddress cAddr = ctx->constBuf4.gpuAddress;

        [alloc reset];
        [cb beginCommandBufferWithAllocator:alloc];

        MTL4RenderPassDescriptor* rp = [[MTL4RenderPassDescriptor alloc] init];
        fillOpaqueAttachments(rp.colorAttachments, rp.depthAttachment, f, ctx->hwDepthTex);
        id<MTL4RenderCommandEncoder> r = [cb renderCommandEncoderWithDescriptor:rp];
        [r setViewport:(MTLViewport){0.0, 0.0, (double)f.renderW, (double)f.renderH, 0.0, 1.0}];
        [r setFrontFacingWinding:MTLWindingCounterClockwise];
        [r setCullMode:MTLCullModeBack];
        [r setDepthStencilState:ctx->dssOpaqueWrite];

        [tFrag setAddress:cAddr + kC4OpaqueUniforms atIndex:2];
        [tFrag setAddress:ctx->textureTableBuf.gpuAddress atIndex:3];
        [r setArgumentTable:tFrag atStages:MTLRenderStageFragment];

        if (f.meshPath && f.meshletLimit > 0u) {
            [tMesh setAddress:ctx->vertexArenaBuf.gpuAddress atIndex:0];
            [tMesh setAddress:ctx->meshletMetaBuf.gpuAddress atIndex:1];
            [tMesh setAddress:ctx->sectionMetaBuf.gpuAddress atIndex:2];
            [tMesh setAddress:cAddr + kC4OpaqueUniforms atIndex:3];
            [tObj setAddress:ctx->meshletMetaBuf.gpuAddress atIndex:1];
            [tObj setAddress:ctx->sectionMetaBuf.gpuAddress atIndex:2];
            [tObj setAddress:cAddr + kC4OpaqueUniforms atIndex:3];
            [tObj setAddress:ctx->statsBuf.gpuAddress atIndex:5];
            [tObj setTexture:ctx->hizPyramidTex.gpuResourceID atIndex:0];
            [r setArgumentTable:tMesh atStages:MTLRenderStageMesh];
            NSUInteger objGroups = ((NSUInteger)f.meshletLimit + 31u) / 32u;
            id<MTLRenderPipelineState> psos[2] = {ctx->psoMeshSolid, ctx->psoMeshCutout};
            for (int pass = 0; pass < 2; ++pass) {
                // Argument tables are snapshotted per draw, so rebinding slot 4 between draws is safe.
                [tObj setAddress:cAddr + kC4CutoutFlags + (NSUInteger)pass * sizeof(uint32_t) atIndex:4];
                [r setArgumentTable:tObj atStages:MTLRenderStageObject];
                [r setRenderPipelineState:psos[pass]];
                [r drawMeshThreadgroups:MTLSizeMake(objGroups, 1, 1)
            threadsPerObjectThreadgroup:MTLSizeMake(32, 1, 1)
              threadsPerMeshThreadgroup:MTLSizeMake(64, 1, 1)];
            }
        } else if (!f.meshPath && f.secLimit > 0u) {
            [tVert setAddress:ctx->vertexArenaBuf.gpuAddress atIndex:0];
            [tVert setAddress:ctx->sectionMetaBuf.gpuAddress atIndex:1];
            [tVert setAddress:cAddr + kC4OpaqueUniforms atIndex:2];
            [r setArgumentTable:tVert atStages:MTLRenderStageVertex];
            const auto* secs = static_cast<const SectionGpuMeta*>([ctx->sectionMetaBuf contents]);
            for (int pass = 0; pass < 2; ++pass) {
                [r setRenderPipelineState:pass == 0 ? ctx->psoSolid : ctx->psoCutout];
                for (uint32_t i = 0; i < f.secLimit; ++i) {
                    uint32_t quads = pass == 0 ? secs[i].solidQuadCount : secs[i].cutoutQuadCount;
                    uint32_t base = pass == 0 ? secs[i].solidBaseVertex : secs[i].cutoutBaseVertex;
                    if ((secs[i].flags & MR_SECTION_ACTIVE) == 0u || quads == 0u) continue;
                    [r drawIndexedPrimitives:MTLPrimitiveTypeTriangle
                                  indexCount:quads * 6u
                                   indexType:MTLIndexTypeUInt32
                                 indexBuffer:ctx->patternIndexBuf.gpuAddress
                           indexBufferLength:[ctx->patternIndexBuf length]
                               instanceCount:1
                                  baseVertex:(NSInteger)base
                                baseInstance:i];
                }
            }
        }
        [r endEncoding];

        // Metal 4 has no automatic hazard tracking: wait for the render pass explicitly.
        id<MTL4ComputeCommandEncoder> c = [cb computeCommandEncoder];
        [c barrierAfterQueueStages:MTLStageFragment beforeStages:MTLStageDispatch | MTLStageBlit
                 visibilityOptions:MTL4VisibilityOptionDevice];
        [c setArgumentTable:tComp];
        if (f.useBackground) {
            [c setComputePipelineState:ctx->csoFillBackground];
            [tComp setTexture:f.targetColor.gpuResourceID atIndex:0];
            [tComp setTexture:f.targetZExp.gpuResourceID atIndex:1];
            [tComp setTexture:ctx->bgTex.gpuResourceID atIndex:2];
            [c dispatchThreadgroups:gridFor(f.renderW, f.renderH) threadsPerThreadgroup:MTLSizeMake(16, 16, 1)];
            [c barrierAfterEncoderStages:MTLStageDispatch beforeEncoderStages:MTLStageDispatch | MTLStageBlit
                       visibilityOptions:MTL4VisibilityOptionDevice];
        }
        if (f.buildHiZ && ctx->csoBuildHiZMip0 && ctx->csoDownsampleHiZ && !ctx->hizMipViews.empty()) {
            [c setComputePipelineState:ctx->csoBuildHiZMip0];
            [tComp setTexture:f.targetZExp.gpuResourceID atIndex:0];
            [tComp setTexture:ctx->hizMipViews[0].gpuResourceID atIndex:1];
            [c dispatchThreadgroups:gridFor(ctx->hizBaseW, ctx->hizBaseH) threadsPerThreadgroup:MTLSizeMake(16, 16, 1)];
            [c setComputePipelineState:ctx->csoDownsampleHiZ];
            int mw = ctx->hizBaseW, mh = ctx->hizBaseH;
            for (int m = 1; m < ctx->hizMipCount; ++m) {
                mw = std::max(1, mw / 2);
                mh = std::max(1, mh / 2);
                [c barrierAfterEncoderStages:MTLStageDispatch beforeEncoderStages:MTLStageDispatch
                           visibilityOptions:MTL4VisibilityOptionDevice];
                [tComp setTexture:ctx->hizMipViews[(size_t)(m - 1)].gpuResourceID atIndex:0];
                [tComp setTexture:ctx->hizMipViews[(size_t)m].gpuResourceID atIndex:1];
                [c dispatchThreadgroups:gridFor(mw, mh) threadsPerThreadgroup:MTLSizeMake(16, 16, 1)];
            }
            ctx->hizValid = true;
        }
        SurfacePair& surf = ctx->opaqueSurfaces[f.slot];
        if (f.upscale == MR_UPSCALE_TEMPORAL) {
            [c setComputePipelineState:ctx->csoCameraMotion];
            [tComp setTexture:ctx->hwDepthTex.gpuResourceID atIndex:0];
            [tComp setTexture:ctx->motionTex.gpuResourceID atIndex:1];
            [tComp setAddress:cAddr + kC4Temporal atIndex:0];
            [c dispatchThreadgroups:gridFor(f.renderW, f.renderH) threadsPerThreadgroup:MTLSizeMake(16, 16, 1)];
        }
        if (f.upscale != MR_UPSCALE_NONE) {
            // MTL4FX waits on and then updates this fence around its own passes.
            [c updateFence:ctx->fence4 afterEncoderStages:MTLStageDispatch];
        } else if (f.useInternal) {
            [c setComputePipelineState:ctx->csoUpscaleColorDepth];
            [tComp setTexture:f.targetColor.gpuResourceID atIndex:0];
            [tComp setTexture:f.targetZExp.gpuResourceID atIndex:1];
            [tComp setTexture:surf.colorTex.gpuResourceID atIndex:2];
            [tComp setTexture:surf.depthTex.gpuResourceID atIndex:3];
            [c dispatchThreadgroups:gridFor(f.outW, f.outH) threadsPerThreadgroup:MTLSizeMake(16, 16, 1)];
        }
        [c endEncoding];

        if (f.upscale != MR_UPSCALE_NONE) {
            ctx->encodeUpscaler(cb, f.targetColor, f.renderW, f.renderH, f.hasTemporal ? &f.tu : nullptr);
            id<MTL4ComputeCommandEncoder> c2 = [cb computeCommandEncoder];
            [c2 waitForFence:ctx->fence4 beforeEncoderStages:MTLStageDispatch | MTLStageBlit];
            [c2 setArgumentTable:tComp];
            [c2 copyFromTexture:ctx->mfxOutputTex toTexture:surf.colorTex];
            [c2 setComputePipelineState:ctx->csoUpscaleDepthNearest];
            [tComp setTexture:f.targetZExp.gpuResourceID atIndex:0];
            [tComp setTexture:surf.depthTex.gpuResourceID atIndex:1];
            [c2 dispatchThreadgroups:gridFor(f.outW, f.outH) threadsPerThreadgroup:MTLSizeMake(16, 16, 1)];
            [c2 endEncoding];
        }

        if (!submitMetal4(ctx, cb, ctx->gpuMsOpaque4)) {
            fprintf(stderr, "[MetalRender] Metal 4 opaque pass timed out\n");
        }
        ctx->lastOpaqueGpuMs = ctx->gpuMsOpaque4->load();
    }
}

static void finishOpaqueStats(MetalContext* ctx, const OpaqueFrame& f) {
    std::memcpy(&ctx->lastStats, [ctx->statsBuf contents], sizeof(GpuCullStats));
    ctx->lastUpscaleUsed = f.upscale;
    if (f.meshPath) {
        // The mesh path culls per meshlet; report populated sections for the HUD.
        uint32_t populated = 0;
        const auto* secs = static_cast<const SectionGpuMeta*>([ctx->sectionMetaBuf contents]);
        for (uint32_t i = 0; i < f.secLimit; ++i) {
            if ((secs[i].flags & MR_SECTION_ACTIVE) && (secs[i].solidQuadCount + secs[i].cutoutQuadCount) > 0u) {
                populated++;
            }
        }
        ctx->lastStats.visibleSections = populated;
    }
}

int mrRenderOpaque(void* handle, int outW, int outH, float resolutionScale, int useMeshShaders, int upscaleMode,
                   const FrameUniforms* uniforms, const TemporalUniforms* temporal,
                   int activeSectionCap, int totalMeshlets) {
    @autoreleasepool {
        auto* ctx = static_cast<MetalContext*>(handle);
        if (!ctx || outW <= 0 || outH <= 0 || !uniforms) {
            return -1;
        }
        OpaqueFrame f;
        if (!prepareOpaque(ctx, outW, outH, resolutionScale, useMeshShaders != 0, upscaleMode,
                           uniforms, temporal, activeSectionCap, totalMeshlets, f)) {
            return -1;
        }
        if (ctx->activeTier == MR_TIER_METAL4) {
            encodeOpaqueMetal4(ctx, f);
        } else {
            encodeOpaqueClassic(ctx, f);
        }
        finishOpaqueStats(ctx, f);
        return f.slot;
    }
}

int mrRenderTranslucent(void* handle, int slot, int outW, int outH, const FrameUniforms* uIn,
                        const uint32_t* secIndices, int count) {
    @autoreleasepool {
        auto* ctx = static_cast<MetalContext*>(handle);
        if (!ctx) {
            return 0;
        }
        if (slot < 0 || slot >= kRingSlots || outW <= 0 || outH <= 0 || count <= 0 || !uIn || !secIndices) {
            ctx->lastTransGpuMs = 0.0f;
            ctx->lastStats.drawnTransQuads = 0u;
            return 0;
        }
        if (!ensureSurfacePair(ctx->device, ctx->transSurfaces[slot], outW, outH)) {
            return 0;
        }
        int renderW = ctx->internalW > 0 ? ctx->internalW : outW;
        int renderH = ctx->internalH > 0 ? ctx->internalH : outH;
        bool isScaled = (renderW != outW || renderH != outH);
        if (isScaled && !ctx->ensureInternalTargets(renderW, renderH, true)) {
            return 0;
        }
        ctx->updateTextureTable();
        ctx->syncResidency();

        FrameUniforms u = *uIn;
        u.screenParams.x = (float)renderW;
        u.screenParams.y = (float)renderH;
        SurfacePair& surf = ctx->transSurfaces[slot];
        id<MTLTexture> targetColor = isScaled ? ctx->scaledTransColorTex : surf.colorTex;
        id<MTLTexture> targetZExp = isScaled ? ctx->scaledTransDepthTex : surf.depthTex;

        // Count first so an empty pass costs nothing.
        const auto* secs = static_cast<const SectionGpuMeta*>([ctx->sectionMetaBuf contents]);
        uint32_t drawnQuads = 0;
        for (int i = 0; i < count; ++i) {
            uint32_t s = secIndices[i];
            if (s < ctx->sectionCap && (secs[s].flags & MR_SECTION_ACTIVE)) drawnQuads += secs[s].transQuadCount;
        }
        if (drawnQuads == 0u) {
            ctx->lastTransGpuMs = 0.0f;
            ctx->lastStats.drawnTransQuads = 0u;
            return 0;
        }

        auto fillAttachments = [&](MTLRenderPassColorAttachmentDescriptorArray* colors,
                                   MTLRenderPassDepthAttachmentDescriptor* depth) {
            colors[0].texture = targetColor;
            colors[0].loadAction = MTLLoadActionClear;
            colors[0].storeAction = MTLStoreActionStore;
            colors[0].clearColor = MTLClearColorMake(0.0, 0.0, 0.0, 0.0);
            colors[1].texture = targetZExp;
            colors[1].loadAction = MTLLoadActionClear;
            colors[1].storeAction = MTLStoreActionStore;
            colors[1].clearColor = MTLClearColorMake(1.0, 1.0, 1.0, 1.0);
            depth.texture = ctx->hwDepthTex; // depth-test against opaque terrain
            depth.loadAction = MTLLoadActionLoad;
            depth.storeAction = MTLStoreActionDontCare;
        };

        if (ctx->activeTier == MR_TIER_METAL4) {
            if (@available(macOS 26.0, *)) {
                id<MTL4CommandBuffer> cb = (id<MTL4CommandBuffer>)ctx->cmdBuf4;
                id<MTL4CommandAllocator> alloc = (id<MTL4CommandAllocator>)ctx->allocator4;
                id<MTL4ArgumentTable> tVert = (id<MTL4ArgumentTable>)ctx->tableVertex4;
                id<MTL4ArgumentTable> tFrag = (id<MTL4ArgumentTable>)ctx->tableFragment4;
                id<MTL4ArgumentTable> tComp = (id<MTL4ArgumentTable>)ctx->tableCompute4;
                std::memcpy(static_cast<uint8_t*>([ctx->constBuf4 contents]) + kC4TransUniforms, &u, sizeof(u));
                MTLGPUAddress uAddr = ctx->constBuf4.gpuAddress + kC4TransUniforms;

                [alloc reset];
                [cb beginCommandBufferWithAllocator:alloc];
                MTL4RenderPassDescriptor* rp = [[MTL4RenderPassDescriptor alloc] init];
                fillAttachments(rp.colorAttachments, rp.depthAttachment);
                id<MTL4RenderCommandEncoder> r = [cb renderCommandEncoderWithDescriptor:rp];
                [r setViewport:(MTLViewport){0.0, 0.0, (double)renderW, (double)renderH, 0.0, 1.0}];
                [r setFrontFacingWinding:MTLWindingCounterClockwise];
                [r setCullMode:MTLCullModeBack];
                [r setDepthStencilState:ctx->dssTranslucentTest];
                [r setRenderPipelineState:ctx->psoTranslucent];
                [tVert setAddress:ctx->vertexArenaBuf.gpuAddress atIndex:0];
                [tVert setAddress:ctx->sectionMetaBuf.gpuAddress atIndex:1];
                [tVert setAddress:uAddr atIndex:2];
                [tFrag setAddress:uAddr atIndex:2];
                [tFrag setAddress:ctx->textureTableBuf.gpuAddress atIndex:3];
                [r setArgumentTable:tVert atStages:MTLRenderStageVertex];
                [r setArgumentTable:tFrag atStages:MTLRenderStageFragment];
                for (int i = 0; i < count; ++i) {
                    uint32_t s = secIndices[i];
                    if (s >= ctx->sectionCap || (secs[s].flags & MR_SECTION_ACTIVE) == 0u || secs[s].transQuadCount == 0u) continue;
                    [r drawIndexedPrimitives:MTLPrimitiveTypeTriangle
                                  indexCount:secs[s].transQuadCount * 6u
                                   indexType:MTLIndexTypeUInt32
                                 indexBuffer:ctx->patternIndexBuf.gpuAddress
                           indexBufferLength:[ctx->patternIndexBuf length]
                               instanceCount:1
                                  baseVertex:(NSInteger)secs[s].transBaseVertex
                                baseInstance:s];
                }
                [r endEncoding];
                if (isScaled) {
                    id<MTL4ComputeCommandEncoder> c = [cb computeCommandEncoder];
                    [c barrierAfterQueueStages:MTLStageFragment beforeStages:MTLStageDispatch
                             visibilityOptions:MTL4VisibilityOptionDevice];
                    [c setArgumentTable:tComp];
                    [c setComputePipelineState:ctx->csoUpscaleColorDepth];
                    [tComp setTexture:targetColor.gpuResourceID atIndex:0];
                    [tComp setTexture:targetZExp.gpuResourceID atIndex:1];
                    [tComp setTexture:surf.colorTex.gpuResourceID atIndex:2];
                    [tComp setTexture:surf.depthTex.gpuResourceID atIndex:3];
                    [c dispatchThreadgroups:gridFor(outW, outH) threadsPerThreadgroup:MTLSizeMake(16, 16, 1)];
                    [c endEncoding];
                }
                if (!submitMetal4(ctx, cb, ctx->gpuMsTrans4)) {
                    fprintf(stderr, "[MetalRender] Metal 4 translucent pass timed out\n");
                }
                ctx->lastTransGpuMs = ctx->gpuMsTrans4->load();
            }
        } else {
            MTLRenderPassDescriptor* rp = [MTLRenderPassDescriptor renderPassDescriptor];
            fillAttachments(rp.colorAttachments, rp.depthAttachment);
            id<MTLCommandBuffer> cmdBuf = [ctx->queue commandBuffer];
            id<MTLRenderCommandEncoder> rEnc = [cmdBuf renderCommandEncoderWithDescriptor:rp];
            [rEnc setViewport:(MTLViewport){0.0, 0.0, (double)renderW, (double)renderH, 0.0, 1.0}];
            [rEnc setFrontFacingWinding:MTLWindingCounterClockwise];
            [rEnc setCullMode:MTLCullModeBack];
            [rEnc setDepthStencilState:ctx->dssTranslucentTest];
            [rEnc setRenderPipelineState:ctx->psoTranslucent];
            [rEnc setVertexBuffer:ctx->vertexArenaBuf offset:0 atIndex:0];
            [rEnc setVertexBuffer:ctx->sectionMetaBuf offset:0 atIndex:1];
            [rEnc setVertexBytes:&u length:sizeof(FrameUniforms) atIndex:2];
            [rEnc setFragmentBytes:&u length:sizeof(FrameUniforms) atIndex:2];
            [rEnc setFragmentBuffer:ctx->textureTableBuf offset:0 atIndex:3];
            [rEnc useResource:ctx->atlasTex usage:MTLResourceUsageRead stages:MTLRenderStageFragment];
            [rEnc useResource:ctx->lightmapTex usage:MTLResourceUsageRead stages:MTLRenderStageFragment];
            for (int i = 0; i < count; ++i) {
                uint32_t s = secIndices[i];
                if (s >= ctx->sectionCap || (secs[s].flags & MR_SECTION_ACTIVE) == 0u || secs[s].transQuadCount == 0u) continue;
                [rEnc drawIndexedPrimitives:MTLPrimitiveTypeTriangle
                                 indexCount:secs[s].transQuadCount * 6u
                                  indexType:MTLIndexTypeUInt32
                                indexBuffer:ctx->patternIndexBuf
                          indexBufferOffset:0
                              instanceCount:1
                                 baseVertex:(NSInteger)secs[s].transBaseVertex
                               baseInstance:s];
            }
            [rEnc endEncoding];
            if (isScaled) {
                id<MTLComputeCommandEncoder> uEnc = [cmdBuf computeCommandEncoder];
                [uEnc setComputePipelineState:ctx->csoUpscaleColorDepth];
                [uEnc setTexture:targetColor atIndex:0];
                [uEnc setTexture:targetZExp atIndex:1];
                [uEnc setTexture:surf.colorTex atIndex:2];
                [uEnc setTexture:surf.depthTex atIndex:3];
                [uEnc dispatchThreadgroups:gridFor(outW, outH) threadsPerThreadgroup:MTLSizeMake(16, 16, 1)];
                [uEnc endEncoding];
            }
            [cmdBuf commit];
            [cmdBuf waitUntilCompleted];
            double gpuSec = [cmdBuf GPUEndTime] - [cmdBuf GPUStartTime];
            ctx->lastTransGpuMs = gpuSec > 0.0 ? (float)(gpuSec * 1000.0) : 0.0f;
        }
        ctx->lastStats.drawnTransQuads = drawnQuads;
        return 1;
    }
}

JNIEXPORT jint JNICALL Java_ports_metalrender_nativebridge_MetalNative_renderOpaquePass(
    JNIEnv* env, jclass, jlong ctxPtr, jint outWidth, jint outHeight, jfloat resolutionScale,
    jboolean useMeshShaders, jint upscaleMode, jobject frameUniformsBuf, jobject temporalUniformsBuf,
    jint activeSectionCap, jint totalMeshlets
) {
    if (!frameUniformsBuf) {
        return -1;
    }
    auto* u = static_cast<const FrameUniforms*>(env->GetDirectBufferAddress(frameUniformsBuf));
    const TemporalUniforms* t = temporalUniformsBuf
        ? static_cast<const TemporalUniforms*>(env->GetDirectBufferAddress(temporalUniformsBuf))
        : nullptr;
    return (jint)mrRenderOpaque(reinterpret_cast<void*>(ctxPtr), outWidth, outHeight, resolutionScale,
                                useMeshShaders == JNI_TRUE ? 1 : 0, upscaleMode, u, t,
                                activeSectionCap, totalMeshlets);
}

JNIEXPORT jboolean JNICALL Java_ports_metalrender_nativebridge_MetalNative_bindIOSurfaceToGLTexture(
    JNIEnv*,
    jclass,
    jlong ctxPtr,
    jint slot,
    jboolean translucent,
    jint glColorTexRect,
    jint glDepthTexRect
) {
    auto* ctx = reinterpret_cast<MetalContext*>(ctxPtr);
    if (!ctx || slot < 0 || slot >= kRingSlots) return JNI_FALSE;
    CGLContextObj cglCtx = CGLGetCurrentContext();
    if (!cglCtx) return JNI_FALSE;

    const SurfacePair& pair = (translucent == JNI_TRUE) ? ctx->transSurfaces[slot] : ctx->opaqueSurfaces[slot];
    if (!pair.colorSurface || !pair.depthSurface || pair.width <= 0 || pair.height <= 0) {
        return JNI_FALSE;
    }

    glBindTexture(GL_TEXTURE_RECTANGLE, (GLuint)glColorTexRect);
    CGLError e1 = CGLTexImageIOSurface2D(
        cglCtx,
        GL_TEXTURE_RECTANGLE,
        GL_RGBA8,
        pair.width,
        pair.height,
        GL_BGRA,
        GL_UNSIGNED_INT_8_8_8_8_REV,
        pair.colorSurface,
        0
    );

    glBindTexture(GL_TEXTURE_RECTANGLE, (GLuint)glDepthTexRect);
    CGLError e2 = CGLTexImageIOSurface2D(
        cglCtx,
        GL_TEXTURE_RECTANGLE,
        GL_R32F,
        pair.width,
        pair.height,
        GL_RED,
        GL_FLOAT,
        pair.depthSurface,
        0
    );

    glBindTexture(GL_TEXTURE_RECTANGLE, 0);
    return (e1 == kCGLNoError && e2 == kCGLNoError) ? JNI_TRUE : JNI_FALSE;
}

// Binds a render-resolution BGRA IOSurface to glColorTexRect (recreated on size change) and marks it
// as the background for the next opaque pass. The caller blits the GL frame into that texture and
// flushes before rendering.
JNIEXPORT jboolean JNICALL Java_ports_metalrender_nativebridge_MetalNative_prepareBackground(
    JNIEnv*,
    jclass,
    jlong ctxPtr,
    jint glColorTexRect,
    jint width,
    jint height
) {
    auto* ctx = reinterpret_cast<MetalContext*>(ctxPtr);
    if (!ctx || width <= 0 || height <= 0) return JNI_FALSE;
    @autoreleasepool {
        if (!ctx->bgSurface || ctx->bgW != width || ctx->bgH != height || ctx->bgGlTex != glColorTexRect) {
            CGLContextObj cglCtx = CGLGetCurrentContext();
            if (!cglCtx) return JNI_FALSE;
            if (!ctx->bgSurface || ctx->bgW != width || ctx->bgH != height) {
                ctx->bgTex = nil;
                if (ctx->bgSurface) {
                    CFRelease(ctx->bgSurface);
                }
                ctx->bgSurface = createSurface(width, height, kCVPixelFormatType_32BGRA);
                ctx->bgW = ctx->bgH = 0;
                if (!ctx->bgSurface) return JNI_FALSE;
                MTLTextureDescriptor* d = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatBGRA8Unorm
                                                                                             width:(NSUInteger)width
                                                                                            height:(NSUInteger)height
                                                                                         mipmapped:NO];
                d.usage = MTLTextureUsageShaderRead;
                d.storageMode = MTLStorageModeShared;
                ctx->bgTex = [ctx->device newTextureWithDescriptor:d iosurface:ctx->bgSurface plane:0];
                if (!ctx->bgTex) return JNI_FALSE;
            }
            glBindTexture(GL_TEXTURE_RECTANGLE, (GLuint)glColorTexRect);
            CGLError e = CGLTexImageIOSurface2D(cglCtx, GL_TEXTURE_RECTANGLE, GL_RGBA8, width, height, GL_BGRA,
                                                GL_UNSIGNED_INT_8_8_8_8_REV, ctx->bgSurface, 0);
            glBindTexture(GL_TEXTURE_RECTANGLE, 0);
            if (e != kCGLNoError) return JNI_FALSE;
            ctx->bgW = width;
            ctx->bgH = height;
            ctx->bgGlTex = glColorTexRect;
        }
        ctx->bgPending = true;
        return JNI_TRUE;
    }
}

JNIEXPORT jboolean JNICALL Java_ports_metalrender_nativebridge_MetalNative_getLastBackgroundUsed(
    JNIEnv*,
    jclass,
    jlong ctxPtr
) {
    auto* ctx = reinterpret_cast<MetalContext*>(ctxPtr);
    return (ctx && ctx->lastBackgroundUsed) ? JNI_TRUE : JNI_FALSE;
}

// Helper for headless hardware verification tests (creates an offscreen CGL 3.2 Core context when GLFW is absent).
JNIEXPORT jlong JNICALL Java_ports_metalrender_nativebridge_MetalNative_createHeadlessCGLContext(
    JNIEnv*,
    jclass
) {
    CGLPixelFormatAttribute attrs[] = {
        kCGLPFAOpenGLProfile, (CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core,
        kCGLPFAAccelerated,
        (CGLPixelFormatAttribute)0
    };
    CGLPixelFormatObj pix = nullptr;
    GLint npix = 0;
    if (CGLChoosePixelFormat(attrs, &pix, &npix) != kCGLNoError || !pix) {
        return 0;
    }
    CGLContextObj cgl = nullptr;
    CGLError err = CGLCreateContext(pix, nullptr, &cgl);
    CGLDestroyPixelFormat(pix);
    if (err != kCGLNoError || !cgl) {
        return 0;
    }
    CGLSetCurrentContext(cgl);
    return reinterpret_cast<jlong>(cgl);
}

JNIEXPORT void JNICALL Java_ports_metalrender_nativebridge_MetalNative_destroyHeadlessCGLContext(
    JNIEnv*,
    jclass,
    jlong cglPtr
) {
    auto cgl = reinterpret_cast<CGLContextObj>(cglPtr);
    if (cgl) {
        CGLSetCurrentContext(nullptr);
        CGLDestroyContext(cgl);
    }
}

// Direct IOSurface pixel inspection helper for unit tests on macOS hardware.
JNIEXPORT jint JNICALL Java_ports_metalrender_nativebridge_MetalNative_readSurfaceBgraPixel(
    JNIEnv*,
    jclass,
    jlong ctxPtr,
    jint slot,
    jboolean translucent,
    jint x,
    jint y
) {
    auto* ctx = reinterpret_cast<MetalContext*>(ctxPtr);
    if (!ctx || slot < 0 || slot >= kRingSlots) return 0;
    const SurfacePair& pair = (translucent == JNI_TRUE) ? ctx->transSurfaces[slot] : ctx->opaqueSurfaces[slot];
    if (!pair.colorSurface || x < 0 || y < 0 || x >= pair.width || y >= pair.height) return 0;
    IOSurfaceLock(pair.colorSurface, kIOSurfaceLockReadOnly, nullptr);
    const auto* base = static_cast<const uint8_t*>(IOSurfaceGetBaseAddress(pair.colorSurface));
    size_t bpr = IOSurfaceGetBytesPerRow(pair.colorSurface);
    uint32_t bgra = 0;
    std::memcpy(&bgra, base + (size_t)y * bpr + (size_t)x * 4u, 4u);
    IOSurfaceUnlock(pair.colorSurface, kIOSurfaceLockReadOnly, nullptr);
    return (jint)bgra;
}

JNIEXPORT jfloat JNICALL Java_ports_metalrender_nativebridge_MetalNative_readSurfaceDepthPixel(
    JNIEnv*,
    jclass,
    jlong ctxPtr,
    jint slot,
    jboolean translucent,
    jint x,
    jint y
) {
    auto* ctx = reinterpret_cast<MetalContext*>(ctxPtr);
    if (!ctx || slot < 0 || slot >= kRingSlots) return 1.0f;
    const SurfacePair& pair = (translucent == JNI_TRUE) ? ctx->transSurfaces[slot] : ctx->opaqueSurfaces[slot];
    if (!pair.depthSurface || x < 0 || y < 0 || x >= pair.width || y >= pair.height) return 1.0f;
    IOSurfaceLock(pair.depthSurface, kIOSurfaceLockReadOnly, nullptr);
    const auto* base = static_cast<const uint8_t*>(IOSurfaceGetBaseAddress(pair.depthSurface));
    size_t bpr = IOSurfaceGetBytesPerRow(pair.depthSurface);
    float z = 1.0f;
    std::memcpy(&z, base + (size_t)y * bpr + (size_t)x * 4u, 4u);
    IOSurfaceUnlock(pair.depthSurface, kIOSurfaceLockReadOnly, nullptr);
    return (jfloat)z;
}

JNIEXPORT void JNICALL Java_ports_metalrender_nativebridge_MetalNative_readTelemetry(
    JNIEnv* env,
    jclass,
    jlong ctxPtr,
    jintArray outCounters8,
    jfloatArray outGpuMs2
) {
    auto* ctx = reinterpret_cast<MetalContext*>(ctxPtr);
    if (!ctx) return;
    if (outCounters8 && env->GetArrayLength(outCounters8) >= 8) {
        jint tmp[8] = {
            (jint)ctx->lastStats.visibleSections,
            (jint)ctx->lastStats.frustumCulledSections,
            (jint)ctx->lastStats.occludedSections,
            (jint)ctx->lastStats.lodSections,
            (jint)ctx->lastStats.drawnSolidQuads,
            (jint)ctx->lastStats.drawnCutoutQuads,
            (jint)ctx->lastStats.drawnTransQuads,
            (jint)ctx->lastStats.visibleMeshlets
        };
        env->SetIntArrayRegion(outCounters8, 0, 8, tmp);
    }
    if (outGpuMs2 && env->GetArrayLength(outGpuMs2) >= 2) {
        jfloat ms[2] = { ctx->lastOpaqueGpuMs, ctx->lastTransGpuMs };
        env->SetFloatArrayRegion(outGpuMs2, 0, 2, ms);
    }
}

} // extern "C"
