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
- Pipes: item (wood, cobblestone, stone, quartz, gold, sandstone, iron, diamond, clay, void,
  obsidian, lapis, daizuli, diamond-wood, emzuli, stripes),
  fluid (wood, cobblestone, stone, quartz, gold, sandstone, iron, diamond, clay, void, diamond-wood) and kinesis
  (wood, cobblestone, stone, quartz, gold, sandstone, iron, diamond, diamond-wood), and the structure pipe, which
  only joins other structure pipes. The diamond item and fluid pipes hold nine phantom filters per face; the fluid
  pipe reads fluid containers placed in them. The iron and diamond kinesis pipes limit the power
  they pass on to a level set with the wrench or a gate. The obsidian pipe sucks dropped items in front of its
  open face, further with engine power; the lapis pipe paints the items passing through it, and the
  daizuli pipe sends items of its colour out of its selected face and the rest elsewhere (a wrench
  steps either pipe's colour, and gates can paint). The diamond-wood pipes extract like wooden pipes through
  nine filters (whitelist, blacklist, or round robin for items); the emzuli pipe extracts and paints the items
  of whichever of its four preset slots a gate selects; the stripes pipe places, plants, uses or dispenses the
  items that reach its open end, lays item pipes to extend itself, and with engine power breaks the block in
  front. Pipe plugs close a pipe's face, and power adapters let an engine drive a wooden pipe through a closed
  face.
- Factory and builders: tank, pump, mining well, quarry and its frame, and the filler, which
  fills the land mark area or volume box behind it with one of upstream's 18 patterns from the
  block items it holds and clears what the pattern leaves empty.
- Blueprints: the architect table records the land mark area or volume box behind it onto
  templates (which blocks are filled) and blueprints (each block, what it costs and what must be
  built first, following upstream's builder rules restated for 1.19.2 blocks). The
  builder builds a recorded template or blueprint behind it, turned to its own facing, from the
  items and fluids it holds, including a blueprint's chest contents and entities, and builds it
  again at every block along a path of path marks placed behind it. The electronic
  library copies recordings between the server and each player's local library, the
  replacer swaps one block for another throughout a blueprint, and the single block
  schematic records and rebuilds one block; recordings show their snapshot turning in their tooltip.
  The filler, builder, architect table and electronic library use BuildCraft 7.1's crafting recipes;
  the replacer and single block schematics, which 7.1 lacked, have recipes in the same style (see
  [docs/PARITY.md](docs/PARITY.md)). Every other item is craftable or listed, with its reason, in
  [uncraftable.txt](uncraftable.txt), which a GameTest checks on both loaders.
- Markers: land marks that connect into boxes (a quarry placed against one takes its box as its
  area), path marks that connect into paths and loops, and the marker connector, with upstream's
  lasers; creative volume boxes that the connector resizes and removes.
- Silicon: the laser, which beams engine power into laser tables in sight, the assembly table, which
  makes the five chipsets, the advanced crafting table, which crafts a set blueprint from its materials,
  and the integration table (as upstream, no integration recipes ship), with upstream's recipes.
- Gates: the 25 gate variants (clay brick, iron, nether brick and gold, AND or OR, with lapis, quartz
  or diamond modifiers) go on a pipe face like plugs. Each pairs triggers (always on, redstone,
  inventories, tanks, machines at work, engine heat, items or fluid in the pipe, power requested)
  with actions (a redstone signal from the pipe, turning an iron, wooden or stripes pipe, painting, and
  selecting an emzuli preset), grouped by the gate's
  logic, through upstream's gate GUI. The gate copier copies one gate's setup onto others.
- Pipe wire: sixteen colours of wire laid along pipe edges, made in the assembly table. Wires of
  one colour that touch form a network; a gate's pipe signal action powers it, and gates on the
  network react to it with pipe signal triggers.
- Lenses and filters: sixteen colours and clear, made in the assembly table, go on item pipe faces.
  A lens paints the items passing through it (a clear lens washes them), and a filter lets through
  only items of its colour and pulls them its way.
- Pulsars and timers: a pipe pulsar, made in the assembly table from a redstone engine and two iron
  ingots, goes on a wooden pipe and injects a 1 MJ pulse every second while clicked on or while a gate
  holds it on; a gate can also ask for a single pulse. A timer, made from a clock, adds 5, 10 and 15
  second triggers to gates on its pipe.
- Light sensor: made in the assembly table from a daylight detector, goes on a pipe face and adds
  Bright and Dark triggers to a gate on that face.
- Facades: made in the assembly table from three structure pipes and a block, six full and six hollow.
  A facade copies that block onto a pipe face, including connected and translucent blocks; a hollow
  one leaves the pipe connected. One facade in a crafting grid flips hollow.
- Zone planner: paints a robot zone onto a map of the world. A coloured paintbrush chooses the layer, and a
  map location carries a spot, an area, a path or a zone in and out. The block shows that map on its face.
- Machine control: a gate facing the filler can turn it on, off or into a loop and set its pattern.
  The filler's screen stays locked while a gate holds the pattern, and shows the mode's icon when the
  filler is off or looping. Other machines do not take gate control.
- Oil: medium and large oil wells in the Overworld (surface lake, underground deposit and
  gushing spout), with an oil spring under large wells that slowly regrows oil, and the oil desert
  and oil ocean biomes, where wells are thirty times as common.
- Refinery: the distiller and the heat exchanger, with upstream's ten oil and fuel fluids, each at
  three heat stages, and the nine combustion-engine fuels.
- Gears, wrench and waterproof; broken fluid holders drop their fluid as fragile fluid shards.
- Recipes and tags use both `c:` and `forge:` tags, so materials from either loader's mods work.

**This is not full BuildCraft.** Robots, docking stations and several pipe types are not ported. The port has passed GameTests on dedicated servers for both loaders but has not been
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
frame, oil spring, water spring and volume box, fragile fluid shards (whose fluid is in their NBT), the map location (which upstream gives no recipe) and the refinery's
by-products and intermediate heats. Paintbrushes and the zone planner take their value from their recipes. Oil is valued like lava,
light fuel at the two buckets of oil the refinery needs to make one, a recorded template,
blueprint or single block schematic like a blank one, chipsets, pipe wire, the pipe pulsar, the timer and the light sensor like their assembly ingredients, gates and the gate copier at the cheaper of their crafting and assembly ingredients, and a facade as the dirt recipe (one full block plus three structure pipes) divided by six, with laser power unvalued. See [emc/README.md](emc/README.md) and
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
