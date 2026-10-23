# MetalRender (Fabric 1.19.2)

Clean-room, MIT-licensed Apple Metal terrain renderer for Minecraft 1.19.2 on Fabric. Solid and
cutout terrain (most of the world) is rendered in Metal and handed to Minecraft's OpenGL frame
through shared `IOSurface` color and depth, so translucent terrain, entities, particles and the GUI
still draw in OpenGL with correct depth occlusion. It replaces Sodium on macOS clients and disables
itself on Linux, Windows and dedicated servers.

> Status: in progress. The native engine and the Fabric integration are written and pass automated
> tests on Apple silicon (M5 Ultra), including an in-game comparison against vanilla terrain. Nobody
> has played it interactively yet, and it has not run on Intel Macs. See [Verification](#verification).

## GPU tiers

The bridge picks the best tier the OS and GPU support at startup. A config value can cap it lower.

| Tier | Requires | What it uses |
| --- | --- | --- |
| Metal 4 | macOS 26+, `MTLGPUFamilyMetal4` (Apple silicon M1 and later) | `MTL4CommandQueue`, command allocator, argument tables, explicit barriers and fences, `MTLResidencySet`, mesh shaders, MTL4FX upscalers. Shaders compiled as MSL 4.1 on macOS 27+, MSL 4.0 on macOS 26. |
| Metal 3 mesh | macOS 13+, `MTLGPUFamilyMetal3` | Object and mesh shaders with 32-quad meshlets, frustum-culled in the object stage. Residency set on macOS 15+. |
| Compute + ICB | Any Metal Mac on macOS 13+ | GPU section culling that encodes draws into indirect command buffers. |

All tiers share the same shaders and produce the same image: in the native self-test, frames from
all three tiers match exactly.

## MetalFX

| Mode | Requires | Notes |
| --- | --- | --- |
| Spatial | `MTLFXSpatialScaler` support | Sharpening upscale from 0.5x–1.0x internal resolution. |
| Temporal | `MTLFXTemporalScaler` support | Uses projection jitter, depth and per-pixel camera motion vectors. Can also run at 1.0x as anti-aliasing. |
| Frame interpolation | Detected only | `MTLFXFrameInterpolator` is detected and reported but not used yet. Minecraft presents frames through GLFW/OpenGL, so generated frames would need their own presentation path and a separate UI layer. |
| Temporal denoised | Detected only | Built for ray-traced lighting, which this renderer doesn't have. |

On the Metal 4 tier, the bridge creates the MTL4FX scalers through the `MTL4Compiler` and encodes
them into `MTL4CommandBuffer`s. On the other tiers it uses the classic MetalFX scalers.

Terrain is static, so the bridge rebuilds motion vectors from the depth buffer and the change in
camera position (`shaders/temporal.metal`). Moving entities are drawn in OpenGL and never pass
through MetalFX.

## Native layout

- `src/main/native/shaders/`: `terrain.metal` (vertex, fragment and upscale kernels),
  `mesh_terrain.metal` (object and mesh stages), `cull_icb.metal` (ICB culling and Hi-Z),
  `temporal.metal` (motion vectors), `metal_shared.h` (layouts shared with C++).
- `src/main/native/metal_bridge.mm`: the Metal engine and JNI entry points.
- `src/main/native/metal_bridge_internal.h`: the C API underneath the JNI layer.
- `src/main/native/selftest/metal_selftest.mm`: the hardware self-test.

`build_native.sh` produces `shaders_metal3.metallib`, `shaders_metal4.metallib`,
`shaders_metal4_1.metallib` and a universal (`arm64` + `x86_64`) `libmetalrender.dylib`. Warnings
fail the build, including unguarded API availability.

## How it plugs into Minecraft

- **Loading.** The client entrypoint ([`MetalRenderClient`](src/main/java/ports/metalrender/client/MetalRenderClient.java))
  extracts the dylib and metallibs to `.metalrender/natives/<version>/` in the game directory,
  probes the GPU and creates the Metal context. Off macOS, or if anything fails, it logs why and
  leaves vanilla rendering alone. The main entrypoint does nothing on a dedicated server.
- **Chunk geometry.** Mixins on `ChunkRenderDispatcher.uploadChunkLayer` take each rebuilt layer
  on the chunk worker thread, repack vanilla's 32-byte `BLOCK` vertices into the 24-byte Metal
  format, build the meshlets, and free the vanilla buffer. The arena write happens on the render
  thread. Each `RenderChunk` owns one section slot, freed when the chunk resets or moves.
- **Alpha test only where it can fail.** When the block atlas loads, MetalRender records which
  16x16 cells of it hold only fully opaque sprites (every pixel of every animation frame has alpha
  255). Cutout-mipped quads that sample only those cells, such as grass block sides and tops, are
  placed first in each section's cutout range, and every tier draws them with the solid shader,
  which has no `discard_fragment` and keeps the GPU's hidden-surface removal on: the mesh-shader
  tiers through their own meshlets, the Compute+ICB tier through a second solid-pipeline command per
  section. The image is unchanged: those quads could never fail the alpha test.
- **Frames.** The opaque pass (solid and both cutout layers) is encoded and committed just before
  vanilla's `setupRender`, once the sky, fog and camera are final, so the GPU draws terrain while
  the CPU runs vanilla's visibility and chunk scheduling. `LevelRenderer.renderChunkLayer` for the
  solid layer then waits for it and composites it with a full-screen triangle that writes
  `gl_FragDepth`. Chunk uploads and releases that arrive while the pass is in flight are applied
  right after it, so they show one frame later. Translucent and tripwire terrain stay in vanilla
  OpenGL, so water and stained glass sort against entities, particles and each other exactly as
  without the mod.
- **Shading.** As in vanilla's terrain shaders, the lightmap is sampled per vertex and folded into
  the vertex colour, and the fog distance is computed per vertex. The fragment shader samples only
  the atlas.
- **Render scale below 100%.** Before the opaque pass, the OpenGL frame (the sky so far) is copied
  at render resolution into an IOSurface. Metal fills pixels without terrain from that copy before
  upscaling, so silhouettes blend into the real sky. The compositor also writes the blended pixels
  just outside the nearest-upscaled terrain coverage, instead of cutting the edge along
  half-resolution steps.
- **Textures.** The block atlas is read back from OpenGL (all mip levels) after a resource reload,
  and its opaque-cell map is rebuilt from the sprite images. Animated sprites are mirrored as they
  upload (about 1.8 µs per sprite frame, measured), and the lightmap on every update.
- **Compositing.** Each IOSurface pair is attached to its OpenGL textures once and re-attached only
  when the surfaces are recreated (on resize), since an IOSurface-backed texture always shows the
  surface's current contents.

## Settings and commands

Video Settings has a **MetalRender...** button. Changes apply when the screen closes and are saved
to `config/metalrender.json`.

| Setting | Default | Notes |
| --- | --- | --- |
| Metal Terrain | on | Off returns terrain to vanilla OpenGL. |
| Tier | Auto | Auto, Metal 4, Metal 3 Mesh or Compute+ICB. The hardware can lower it further. |
| Upscaler | Off | Off, MetalFX Spatial or MetalFX Temporal. |
| Render Scale | 100% | 50–100% internal resolution when an upscaler is on. |
| Mesh Shaders | on | Off draws without mesh shaders, so there is no per-meshlet culling. |
| F3 Details | on | Adds tier, culling, quad and GPU-time lines to F3. |

Client commands (`/metalrender` or `/mr`): `status`, `caps`, `enable`, `disable`, `reload`,
`tier <auto|metal4|metal3_mesh|compute_icb>`, `upscaler <none|metalfx_spatial|metalfx_temporal>`,
`scale <0.5-1.0>`, `meshshaders <true|false>`.

## Build and verify

```sh
./scripts/gradle.sh -p mods/metalrender build               # JUnit, JNI hardware tests, nativeSelfTest
./scripts/gradle.sh -p mods/metalrender runGametest         # dedicated-server GameTests
./scripts/gradle.sh -p mods/metalrender runAutotestClient   # macOS: in-game comparison, opens a window
./scripts/gradle.sh -p mods/metalrender runBenchmarkClient  # macOS: frame-time benchmark, opens a window
```

`nativeSelfTest` runs with Metal API validation enabled. It renders a synthetic terrain scene
through every tier this Mac supports and checks:

- coverage
- identical output across tiers
- Hi-Z stability (the native culling code is kept and tested, but the mod never enables it)
- the native translucent pass (also unused by the mod)
- MetalFX spatial and temporal against native-resolution references
- that motion vectors improve the temporal result while the camera moves

The JUnit suite covers the allocator, vertex packing, section bookkeeping, frame maths and config.
On macOS it also renders sections packed by the Java code through the real dylib on every
supported tier. `nativeSelfTest` and the JNI tests are skipped off macOS.

`runAutotestClient` starts a development client, creates a fixed-seed creative world and builds a
small scene with solid, cutout and translucent blocks. It then screenshots the same view with
vanilla terrain and with each Metal tier and MetalFX mode. A Metal frame passes when telemetry
shows it came from the Metal path and its PSNR against the vanilla frame is at least 24 dB (20 dB
with MetalFX at 50%). The report also counts pixels that differ from vanilla by more than 24. The client then turns around and checks that sections behind the camera are
culled on the GPU. Output goes to `build/autotest-client/autotest/`: `report.txt` and one PNG per
variant.

`runBenchmarkClient` creates a fixed-seed world, hovers 30 blocks above spawn at render distance
16 with vsync and the frame cap off, and for vanilla, each Metal tier and each MetalFX mode times a
full chunk rebuild and a 360° turn. It reports frame time percentiles, `renderLevel` CPU time,
OpenGL GPU time, buffer-swap time, the Metal pass (encode, wait, and how much vanilla work ran
while the GPU drew), Metal GPU time, opaque quads drawn and animated-sprite upload cost to
`build/benchmark-client/benchmark/report.txt`. `-PbenchmarkVariants=vanilla,metal4` runs a subset,
`-PbenchmarkJfr` also records a JFR profile, and `-PbenchmarkHeight=2` stands the camera on the
surface. `-PbenchmarkHeight=cave` builds the chunks from 30 blocks up, then moves the camera into a
cave below spawn, so the surface stays built but out of sight, as after walking into a cave.

## Verification

Verified on an Apple M5 Ultra (macOS 27, Metal 4 with MSL 4.1), October 2026:

- `nativeSelfTest`: every check passes on all three tiers.
- JUnit: 22 tests pass, including the JNI tests on all three tiers.
- `runGametest`: both dedicated-server GameTests pass. MetalRender loads on a server without
  touching client classes.
- `runAutotestClient` passes in a real 1.19.2 client at 2560×1440 (Retina):

| Variant | PSNR vs vanilla | Pixels off by >24 | Notes |
| --- | --- | --- | --- |
| Metal 4 | 41.1 dB | 118 | per-meshlet culling |
| Metal 3 mesh | 41.1 dB | 118 | per-meshlet culling |
| Compute+ICB | 41.1 dB | 125 | per-section culling (39.9 dB in the latest run, see below) |
| MetalFX spatial, 50% | 29.2 dB | 68,345 | 28.2 dB before the sky-edge fix |
| MetalFX temporal, 50% | 33.6 dB | 30,032 | 28.7 dB before the sky-edge fix |

  After turning around, the mesh path drew 1,793 of 13,882 meshlets and the ICB path culled 167
  sections and drew 117. The test world differs slightly from run to run (passive mobs wander), so
  pixel counts move by a few hundred between runs. In the latest run, a 120×15 px patch of sky
  appeared where earlier frames had a grey surface, in the Compute+ICB frame and in both MetalFX
  frames taken after it (those use the mesh path), so the scene changed between captures. That
  lowered the Compute+ICB score to 39.9 dB with 1,001 pixels off.

  Measured on the band within 3 px of the vanilla sky line, the PSNR of the MetalFX frames rose
  from 14.2 to 15.8 dB (spatial) and from 13.4 to 20.4 dB (temporal). For comparison, the vanilla
  frame downsampled to 50% and upscaled bilinearly scores 16.0 dB on the same band, so what is left
  there comes from the lower render resolution.

- `runBenchmarkClient` at render distance 16, 2560×1440, before (`a8578f56`) and after the
  performance work. Times are per frame, averaged over the turn:

| Variant | Metal GPU before | after | Metal pass on the render thread before | after |
| --- | --- | --- | --- | --- |
| Metal 4 | 0.86 ms | 0.69 ms | 1.25 ms | 0.84 ms |
| Metal 3 mesh | 0.86 ms | 0.69 ms | 1.21 ms | 0.80 ms |
| Compute+ICB | 0.75 ms | 0.69 ms | 1.06 ms | 0.83 ms |
| MetalFX spatial, 50% | 1.11 ms | 0.95 ms | 1.66 ms | 1.18 ms |
| MetalFX temporal, 50% | 2.18 ms | 2.08 ms | 2.98 ms | 2.57 ms |

  The render thread spends about 2.4 ms per frame outside the buffer swap with Metal terrain,
  against about 4.1 ms with vanilla terrain, whose solid and cutout draws alone take 2.6 ms of CPU.
  Frame rates are not in the table: other builds on the same machine (load average 11 to 25) moved
  the buffer-swap time, and with it the frame rate, by up to 2 ms between runs. In quiet runs Metal 4
  reached 205 to 215 fps against 130 to 155 fps for vanilla. What changed:
  - The pass is committed before vanilla's terrain setup instead of being encoded and waited for
    in one go; about 0.2 ms of vanilla work now runs while the GPU draws.
  - The lightmap is sampled per vertex, which removes a texture read and two interpolants from
    every fragment (about 0.1 to 0.2 ms of GPU time).
  - Cutout quads on fully opaque sprites skip the alpha test on the mesh-shader tiers (about
    0.05 ms in this scene; about 0.2 ms is the ceiling measured with no alpha test at all).
  - The object stage adds its statistics once per threadgroup instead of twice per visible meshlet.
  - Tried and dropped: grouping each section's quads by face direction so the object stage could
    reject meshlets facing away from the camera. It doubled the meshlet count and cost about 0.1 ms
    of GPU time, more than the culling saved, because the hardware already rejects back faces
    cheaply.

- A second round, measured the same way (Metal 4, Metal 3 mesh and Compute+ICB, 30 blocks up):
  - The IOSurfaces are attached to OpenGL once instead of every frame. The Metal pass on the render
    thread went from 0.80–0.84 ms to 0.76–0.81 ms on the three native-resolution tiers.
  - The Compute+ICB tier now draws the cutout quads on fully opaque sprites without the alpha test
    too. Its GPU time stayed at 0.69–0.71 ms on the M5 Ultra, within run-to-run noise. The tier
    exists for Intel and AMD Macs, where `discard_fragment` also turns off early depth testing, but
    it has not been measured on one.
  - Animated sprites cost about 1.7 µs per sprite frame to mirror, around 40 sprite frames per tick
    in the benchmark world (about 0.07 ms per tick), so they were left as they are. A pack with
    1,000 animated block textures would spend under 2 ms per tick on it.
  - Tried and dropped: restricting the Metal pass to vanilla's visible-section list (its cave and
    occlusion graph). Vanilla only builds the sections its graph reaches, so the resident set is
    already close to that list, and the GPU frustum test removes most of the rest. In a cave
    reached after building the surface (`-PbenchmarkHeight=cave`), it removed under 0.1% of the quads
    drawn with no change in GPU time. Building the list cost about 0.1 ms of render-thread time
    per frame, and the pass had to start after vanilla's visibility update, losing the overlap.
  - Not attempted: meshing on the GPU. Modded block models are Java code (baked models and the
    Fabric renderer API), so they can only be meshed by running them, and repacking vanilla's
    output already takes about 55 ms of worker time per 960,000 quads.
  - The buffer swap takes 2.3–3.1 ms per frame with vanilla and Metal terrain alike. It is
    macOS OpenGL presentation, outside what the mod draws, and was not changed.

The in-game runs found and fixed these problems, which the synthetic tests missed:

- MetalFX turned the sky black.
- Choosing the Compute+ICB tier still ran mesh shaders.
- Hi-Z occlusion culled visible geometry in a static view. The option was removed; an old
  `hiZOcclusion` entry in the config file is ignored.
- Translucent terrain composited with the depth of its front-most layer only, so particles or
  entities between two translucent layers could sort wrongly. Translucent terrain is now drawn by
  vanilla.
- At render scales below 100%, terrain silhouettes against the sky were cut along half-resolution
  steps. Fixed with the sky copy and edge-aware compositing described above.
- Turning Metal off mid-frame (`/metalrender disable`, or the fallback after an error) crashed with
  a `NullPointerException`: vanilla tried to draw chunk buffers that Metal had taken over and that
  had never been uploaded to OpenGL. Such draws are now skipped until the chunk rebuilds.

Not verified:

- Interactive play: moving around, block edits, resource-pack reloads, dimension changes, long sessions.
- Intel Macs and AMD GPUs. The `x86_64` slice builds but has never run.
- Running alongside other rendering mods (Iris, Canvas and similar). Sodium is declared incompatible.

## Known limitations

- Below 100% render scale, entities, block entities and translucent terrain depth-test against
  terrain depth upscaled with nearest filtering, so where they meet terrain the edge steps in 2 px
  blocks at 50%. At 100% there is no difference from vanilla.
- MetalFX output only covers solid and cutout terrain. Translucent terrain, entities, block
  entities and particles stay at native resolution in OpenGL.
- Translucent terrain uses vanilla's OpenGL path, so it gains nothing from Metal.
- Distant LOD exists in the native data layout but nothing generates LOD meshes yet.
- MetalFX frame interpolation and the temporal denoiser are detected but not used.
