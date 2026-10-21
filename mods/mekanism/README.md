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

## Status: core module; core machines run on Fabric, many features still missing

This branch holds the Mekanism core module only. Generators, Tools and Additions are later pieces.

- **Forge.** The upstream core sources build against Forge 43.5.0 unchanged except for the removed integrations
  listed in [UPSTREAM.md](UPSTREAM.md). The shared GameTests pass on a dedicated GameTest server. The Forge client
  has not been launched.
- **Fabric.** The same sources compile on Fabric. Their Forge API references are rewritten at build time to the
  port's Forge API mirror in `src/fabric/java/ports/mekanism/lib`, and the methods Forge adds to vanilla classes are
  supplied by injected interfaces and mixins. The JAR loads on a server and in a development client, where the
  enrichment chamber, crusher, energized smelter and metallurgic infuser run on energy delivered directly or through
  universal cables. Energy, items and fluids reach other mods through Team Reborn Energy and the Transfer API, and
  other mods can charge Mekanism's energy items and fill its fluid items the same way.
  Item and block hooks vanilla calls (break, fuel, enchanting, redstone, light, portals, elytra) are wired.
  Osmium, tin, lead, uranium, fluorite and salt generate in overworld biomes with the same placed features Forge uses.
  An anchor upgrade keeps that machine's chunk loaded, including after a restart. Forced chunks do not random-tick.
  The Forge events behind gear ticking, HUD overlays and player effects are not;
  [PARITY.md](PARITY.md) lists every gap.
- Upstream's 60 JUnit tests pass on both loaders, and the shared GameTests (core machines, loader interop, ore
  generation, item and block hooks, and chunk loading) pass on both loaders.

Do not install either JAR in a world you care about.

## Layout

| Path | Contents |
| --- | --- |
| `src/main` | Upstream Mekanism API, core code, assets and generated data, shared by both loaders |
| `src/forge` | Forge `mods.toml` and upstream's access transformer |
| `src/fabric` | Fabric metadata, access widener, mixins and the Forge API mirror (`ports.mekanism.lib`) |
| `fabric/` | The isolated Loom build of the Fabric variant |
| `src/test` | Upstream JUnit tests, run on both loaders |
| `src/gametestCommon`, `src/gametestForge`, `src/gametestFabric` | Shared GameTests and each loader's `TestPlatform`; development only |
| `scripts` | The Fabric tag bridge generator, the loader-parity check and its Forge tag baseline |
| `emc` | EMC policy and the generated ProjectE and Item Alchemy values |

## Build and test

```sh
./scripts/gradle.sh :mekanism:build
./scripts/gradle.sh -p mods/mekanism/fabric build
./scripts/gradle.sh :mekanism:runGameTestServer
./scripts/gradle.sh -p mods/mekanism/fabric runGametest
python3 mods/mekanism/scripts/generate_fabric_tags.py --check
python3 mods/mekanism/scripts/check_loader_parity.py
python3 scripts/emc.py check
```

## License

Mekanism is MIT licensed, Copyright (c) 2017-2023 Aidan C. Brady; see [LICENSE](LICENSE). The port's own code is MIT
licensed; see [licenses/PORT-LICENSE.txt](licenses/PORT-LICENSE.txt).
