# BuildCraft for Minecraft 1.19.2

An unofficial, in-progress port of [BuildCraft](https://github.com/BuildCraft/BuildCraft) 8.0.x
(1.12.2) to Minecraft 1.19.2 for **Fabric and Forge**. Tekxit 4 runs on Fabric, so the Fabric build
is the primary target; the Forge build shares the same gameplay.

| Loader | Requirements | Energy | Items and fluids |
| --- | --- | --- | --- |
| Fabric | Loader 0.16.14+, Fabric API 0.77.0+1.19.2 | Team Reborn Energy (bundled) | Transfer API |
| Forge | Forge 43.5.0 | Forge Energy | Forge capabilities |

Both loaders use 1 MJ = 10 energy units (FE or E), upstream's default conversion. Install the JAR
for your loader on the server and every client; the two builds cannot be mixed in one game, and
worlds do not migrate between them or from 1.12.

## What works

- Engines: redstone, Stirling and combustion, with upstream heat and power stages.
- Pipes: item (wood, cobblestone, stone, quartz, gold, sandstone, iron, diamond, clay, void),
  fluid (wood, cobblestone, stone, quartz, gold, sandstone, iron, clay, void) and kinesis (wood,
  cobblestone, stone, quartz, gold, sandstone).
- Factory and builders: tank, pump, mining well, quarry and its frame.
- Gears, wrench, waterproof, oil, oil residue and light fuel.
- Recipes and tags use both `c:` and `forge:` tags, so materials from either loader's mods work.

**This is not full BuildCraft.** Oil worldgen, gates and wires, facades, lasers and silicon,
builders other than the quarry, markers and landmarks, robots and several pipe types are not
ported. The port has passed GameTests on dedicated servers for both loaders but has not been
play-tested in survival or in the Tekxit 4 pack. See [docs/PARITY.md](docs/PARITY.md) for the
details, deliberate differences and the full list of gaps.

## Build and test

```sh
./scripts/gradle.sh :buildcraft:build
./scripts/gradle.sh -p mods/buildcraft/fabric build
python3 mods/buildcraft/scripts/check_loader_parity.py
./scripts/gradle.sh :buildcraft:runGameTestServer
./scripts/gradle.sh -p mods/buildcraft/fabric runGametest
```

JARs are written to `build/libs/` (Forge) and `fabric/build/libs/` (Fabric). The parity script checks
that both JARs share identical gameplay classes and resources and that no loader code leaks into
the other.

The GameTests run the same gameplay checks on both loaders plus loader adapter checks, and audit EMC
coverage. With `-PwithProjectE` (Forge) or `-PwithItemAlchemy` (Fabric) they also compare against
the real EMC mod. Every item has an EMC value derived from its recipe, except the frame and the oil
buckets, which are creative-only in this port. See [emc/README.md](emc/README.md) and
[docs/emc.md](../../docs/emc.md).

Assets and data are regenerated from an upstream checkout with
`python3 mods/buildcraft/tools/generate_assets.py --upstream <path>`; the GameTest arena with
`python3 mods/buildcraft/tools/generate_test_structure.py`.

## Attribution

BuildCraft is by SpaceToad, AlexIIL and the BuildCraft team. Code follows upstream's MPL-2.0
sources and assets come from upstream under the MMPL 1.0.1; see [UPSTREAM.md](UPSTREAM.md),
[LICENSE](LICENSE) and [licenses/](licenses/). This is not an official BuildCraft release.
