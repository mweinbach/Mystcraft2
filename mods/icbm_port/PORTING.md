# Minecraft 1.19.2 / Fabric port

This port lives in `mods/icbm_port/` in the Mystcraft mod collection. Its Fabric
build is isolated from Mystcraft's Forge build. Run the local wrapper here, or
`./scripts/gradle.sh :icbm_port:build` from the workspace root. The legacy ICBM
Forge source is referenced upstream and is not imported into this collection.

User-selected target: **Minecraft 1.19.2, Fabric, Java 17**.

Source: https://github.com/BuiltBrokenModding/ICBM-Classic

Baseline: `prod/1.12`, commit `9c1d985a375adc2868ff47707b3a636452d0b63a`.

See [the Fabric README](README.md) for build commands, gameplay, and current
limitations. This is an early playable foundation, not a completed feature-parity
port. Existing Forge worlds are not compatible with this module.

Initial foundation validated October 18, 2026: Fabric build, 4 unit tests, and
8 dedicated-server GameTests passed. Interactive client playtesting is pending.

## Architecture

Following Mystcraft's documented separation of concerns:

- `core/`: loader-independent logic, starting with direct-flight normalization
  adapted from upstream `DirectFlightLogic`.
- `block/`, `entity/`: Minecraft gameplay and persistence.
- `IcbmPort`: Fabric initialization and registrations.
- `client/`: client-only renderer registration.

The upstream project has 714 Java files including tests. The new module does not
compile those Forge sources. Systems must be migrated deliberately: Forge
capabilities, registry events, networking, rendering, config, and saved data have
no drop-in compatibility with Fabric 1.19.2.

## Milestones

1. Foundation: pinned Fabric build, registries, recipes, condensed explosive,
   direct-fire missile and redstone launcher, save/reload and gameplay tests.
2. Launcher parity: coordinate targeting, launcher tiers, screens, inventories,
   energy integration, server-validated GUI packets, and ballistic game paths.
3. Explosive parity: migrate the upstream blast registry and implementations in
   groups, with blast size, damage, fuse, performance, and multiplayer checks.
4. Delivery forms: grenades, handheld launchers, bomb carts, and additional
   missile types.
5. Defense and utility systems: radar, EMP, tracking, radio networks, and defense
   equipment, followed by optional mod integrations.
6. Original art, content balancing, dedicated-server/client playtesting, and
   release packaging.

## Code and assets

Upstream code is MIT licensed; its notice is preserved and packaged in the port.
Upstream assets use a separate license whose non-permitted uses explicitly
include Fabric ports. The Fabric source sets contain only newly written JSON
models that reference vanilla Minecraft textures. No upstream textures, sounds,
models, language files, or other asset files are included in the Fabric artifact.

The display name and `icbm_port` namespace distinguish this prototype from the
official mod. Do not point Gradle resource source sets at the legacy asset tree.

## Important behavior differences

- Condensed explosions currently use Minecraft's explosion implementation at
  power 6, not the complete upstream `BlastTNT` damage and propagation algorithm.
- Missiles fly directly along the launcher's facing at 1.5 blocks/tick, up to
  200 loaded ticks. They expire at unloaded chunks, world borders, and build
  height limits; they do not load distant chunks or target coordinates.
- Launchers hold one missile and use blockstate persistence. There are no
  screens, energy costs, hopper insertion, or multi-block assemblies yet.
- Parameters are currently constants; upstream configuration files are not read.
- The port uses its own IDs. There is no legacy world, item, or entity conversion.
