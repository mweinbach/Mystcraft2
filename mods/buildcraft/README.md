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
- Factory and builders: tank, pump, mining well, quarry and its frame, and the creative filler, which
  fills the land mark area or volume box behind it with one of upstream's 18 patterns from the
  block items it holds and clears what the pattern leaves empty.
- Blueprints: the architect table records the land mark area or volume box behind it onto
  templates (which blocks are filled) and blueprints (each block, what it costs and what must be
  built first, following upstream's builder rules restated for 1.19.2 blocks). The creative
  builder builds a recorded template or blueprint behind it, turned to its own facing, from the
  items and fluids it holds, including a blueprint's chest contents and entities, and builds it
  again at every block along a path of path marks placed behind it.
- Markers: land marks that connect into boxes (a quarry placed against one takes its box as its
  area), path marks that connect into paths and loops, and the marker connector, with upstream's
  lasers; creative volume boxes that the connector resizes and removes.
- Oil: medium and large oil wells in the Overworld (surface lake, underground deposit and
  gushing spout), with an oil spring under large wells that slowly regrows oil, and the oil desert
  and oil ocean biomes, where wells are thirty times as common.
- Refinery: the distiller and the heat exchanger, with upstream's ten oil and fuel fluids, each at
  three heat stages, and the nine combustion-engine fuels.
- Gears, wrench and waterproof; broken fluid holders drop their fluid as fragile fluid shards.
- Recipes and tags use both `c:` and `forge:` tags, so materials from either loader's mods work.

**This is not full BuildCraft.** Gates and wires, facades, lasers and silicon, the
electronic library and replacer, robots and several pipe types are not ported. The port has passed GameTests on dedicated servers for both loaders but has not been
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
the real EMC mod. Every item has an EMC value derived from its recipe, except the creative-only
frame, filler, builder, oil spring, water spring and volume box, fragile fluid shards (whose fluid is in their NBT) and the refinery's
by-products and intermediate heats. Oil is valued like lava,
light fuel at the two buckets of oil the refinery needs to make one, and a recorded template or
blueprint like a blank one. See [emc/README.md](emc/README.md) and
[docs/emc.md](../../docs/emc.md).

Assets and data are regenerated from an upstream checkout with
`python3 mods/buildcraft/tools/generate_assets.py --upstream <path>`; the builder rules with
`python3 mods/buildcraft/tools/generate_builder_rules.py <blockstates folder>` from Minecraft
1.19.2's block state files (see the script for how to extract them); the GameTest arena with
`python3 mods/buildcraft/tools/generate_test_structure.py`.

## Attribution

BuildCraft is by SpaceToad, AlexIIL and the BuildCraft team. Code follows upstream's MPL-2.0
sources and assets come from upstream under the MMPL 1.0.1; see [UPSTREAM.md](UPSTREAM.md),
[LICENSE](LICENSE) and [licenses/](licenses/). This is not an official BuildCraft release.
