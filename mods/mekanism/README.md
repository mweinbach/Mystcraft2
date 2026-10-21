# Mekanism for Minecraft 1.19.2

An unofficial, in-progress port of [Mekanism](https://github.com/mekanism/Mekanism) 10.3.9 (the last 1.19.2
release, `v1.19.2-10.3.9.13`) to **Fabric and Forge** from one shared codebase. Tekxit 4 runs on Fabric, so the Fabric
build is the primary target. Upstream attribution and every edit to upstream files are listed in
[UPSTREAM.md](UPSTREAM.md); gaps are tracked in [PARITY.md](PARITY.md).

| Loader | Requirements | Energy | Items and fluids | Chemicals |
| --- | --- | --- | --- | --- |
| Fabric | Loader 0.16.14+, Fabric API 0.77.0+1.19.2 | Team Reborn Energy (bundled) | Transfer API | Mekanism API |
| Forge | Forge 43.5.0 | Forge Energy | Forge capabilities | Mekanism API |

These are the planned interop targets. The two builds cannot be mixed in one game, and worlds do not migrate between
them.

## Status: core module, not playable on Fabric yet

This branch holds the Mekanism core module only. Generators, Tools and Additions are later pieces.

- **Forge.** The upstream core sources build against Forge 43.5.0 unchanged except for the removed integrations
  listed in [UPSTREAM.md](UPSTREAM.md). A dedicated GameTest server loads the mod, its registries, configs and world
  generation, then shuts down cleanly. No Mekanism GameTests exist yet, and the client has not been launched.
- **Fabric.** The same sources compile on Fabric. Their Forge API references are rewritten at build time to the
  port's Forge API mirror in `src/fabric/java/ports/mekanism/lib`, and the methods Forge adds to vanilla classes are
  supplied by injected interfaces and mixins. **The Fabric JAR does not load yet.** The entry points it declares
  (`ports.mekanism.fabric.FabricMod` and `FabricClientMod`), the registration and lifecycle driver, networking,
  Team Reborn Energy and Transfer API bridges, and many gameplay hooks the mixins must call are still to be written.
  See [PARITY.md](PARITY.md).
- Upstream's 60 JUnit tests pass on both loaders.

Do not install either JAR in a world you care about.

## Layout

| Path | Contents |
| --- | --- |
| `src/main` | Upstream Mekanism API, core code, assets and generated data, shared by both loaders |
| `src/forge` | Forge `mods.toml` and upstream's access transformer |
| `src/fabric` | Fabric metadata, access widener, mixins and the Forge API mirror (`ports.mekanism.lib`) |
| `fabric/` | The isolated Loom build of the Fabric variant |
| `src/test` | Upstream JUnit tests, run on both loaders |

## Build and test

```sh
./scripts/gradle.sh :mekanism:build
./scripts/gradle.sh -p mods/mekanism/fabric build
./scripts/gradle.sh :mekanism:runGameTestServer
```

## License

Mekanism is MIT licensed, Copyright (c) 2017-2023 Aidan C. Brady; see [LICENSE](LICENSE). The port's own code is MIT
licensed; see [licenses/PORT-LICENSE.txt](licenses/PORT-LICENSE.txt).
