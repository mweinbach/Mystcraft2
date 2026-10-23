# Clean-Room Provenance

`mods/metalrender` is a **100% original, MIT-licensed clean-room implementation** of an Apple Metal rendering backend for Minecraft 1.19.2 on Fabric (macOS / Apple Silicon).

- **Reference concept**: [`webblepebbles/MetalRender`](https://github.com/webblepebbles/MetalRender) (user-facing command surface, standalone Fabric chunk/terrain Metal acceleration, LOD controls, adaptive resolution scaling, and `IOSurface` OpenGL compositing concept).
- **Clean-room boundary**: Zero Java files, Objective-C++ files, Metal shaders, textures, icons, or configuration assets were copied or translated from `webblepebbles/MetalRender`. All Java classes (`ports.metalrender.*`), Objective-C++ JNI bridges, Metal Shading Language kernels/mesh/render shaders, OpenGL 3.2 Core `IOSurface` compositors, and procedural/original icons in this directory were written from scratch against the public Minecraft 1.19.2, Fabric API, OpenGL/CGL, `IOSurface`, `Metal`, and `MetalFX` APIs.
- **License**: Released under the [MIT License](LICENSE).
