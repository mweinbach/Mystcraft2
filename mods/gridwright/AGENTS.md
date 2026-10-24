# Gridwright contributor instructions

Read [DESIGN.md](DESIGN.md) before adding or changing blocks, items, textures, models,
animations or screens. It records the approved visual direction and how to extend it.

- Use original native 32x32 textures and compact 3D forms where they explain the block's function.
  Reuse the shared materials and symbols in the design guide when extending a family.
- A model change must account for selection, collision, support, interaction and light occlusion.
  A resource-pack preview alone does not implement those behaviors.
- **Fabric first.** Fabric is Gridwright's primary loader and Forge support is optional. Build, test
  and verify on Fabric first; nothing is done until it works on Fabric. Keep Forge working where that
  is cheap, but a Forge-only difficulty never blocks or reshapes a Fabric feature: ship it on Fabric,
  record the Forge gap in the README and `docs/PARITY.md`, and list any Fabric-only GameTest in
  `gametest-loader-only.txt` with its reason.
- Keep gameplay and assets shared between Forge and Fabric. Follow the repository's clean-room
  rules, attribution requirements and Gridwright verification commands in the root `AGENTS.md`.
- Keep proposed art distinct from installed assets. Update the design guide when a new family
  establishes a reusable pattern, and keep the preview's implementation status accurate.
