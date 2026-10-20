# BuildCraft 1.19.2 parity

This records what the port implements from the pinned upstream revision
(`7c86626d09c4569fb2e0f458a16aaf8c3148ebaf`, BuildCraft 8.0.x for 1.12.2), where it deliberately
differs, and what is not ported. It is an in-progress port: the implemented machines are
playable and tested on dedicated GameTest servers for both loaders, but most of upstream is
still missing and the port has not been play-tested in survival or in the Tekxit 4 pack.

## Implemented

Gameplay, registries, recipes, assets and EMC data are shared in `src/main`; Forge and Fabric
add only loader adapters.

| Area | Content |
| --- | --- |
| Core | Wood, stone, iron, gold and diamond gears; wrench (rotates engines and the output face of wooden and iron pipes); other mods' `c:wrenches` and `forge:tools/wrench` items work too. |
| Engines | Redstone (pulses 1 MJ while powered, warms slowly up to red but never overheats), Stirling (solid fuels, 1 MJ/t), Combustion (fuel, coolant and residue tanks, heat stages, overheating). Upstream heat, pulse and power-stage formulas. |
| Fluids | Oil, oil residue and light fuel with upstream density, viscosity and colours, source and flowing forms, buckets. |
| Factory | Tank (stacks into columns, fills bottom-up, balances), pump (drains connected fluid bodies up to 64 blocks away, outputs into tanks or pipes), mining well (mines straight down, outputs into inventories or pipes). |
| Builders | Quarry with frame building, digging and its tool head, using upstream's default 11×11 area. |
| Item pipes | Wood (extracts when powered by an engine), cobblestone, stone, quartz, gold, sandstone, iron, diamond (filter GUI), clay, void. |
| Fluid pipes | Wood, cobblestone, stone, quartz, gold, sandstone, iron, clay, void, with upstream capacities and transfer rates. |
| Kinesis pipes | Wood, cobblestone, stone, quartz, gold, sandstone, with upstream power limits. |
| Client | Upstream engine model (base plate, stroking piston plate and chamber, trunk textured and lit by heat stage), pipe contents, tank, mining tube and quarry renderers; Stirling, Combustion and diamond pipe screens; upstream textures. |

Pipe connection rules follow upstream: separate-material pipes (cobblestone, stone, quartz) do not
join each other, wooden pipes do not join wooden pipes, and sandstone pipes do not connect to
machines. Fluid and kinesis pipes are crafted from item pipes with waterproof or redstone and can
be turned back.

## Energy

Internally every machine keeps upstream's micro-Minecraft-Joule bookkeeping (`MjAPI.MJ`), so
constants and formulas match upstream exactly. Power leaves or enters a block only through the
loader energy API:

| Loader | API | Ratio |
| --- | --- | --- |
| Forge | Forge Energy (`ForgeCapabilities.ENERGY`, `IEnergyStorage`) | 1 MJ = 10 FE |
| Fabric | Team Reborn Energy 2.3.0 (`EnergyStorage.SIDED`), bundled in the JAR | 1 MJ = 10 E |

The ratio is upstream's own default, `MjRfConversion.DEFAULT_MJ_PER_RF = MjAPI.MJ / 10`, which is
also the classic BuildCraft 6/7 rate. Units round down, and fractional remainders stay in the
sender. Machines only receive energy; engines push it into whatever they face, and kinesis pipes
carry it between blocks.

Redstone engines only power receivers that accept them, as upstream: wooden pipes and the pump.
They do not feed the mining well, the quarry or foreign FE/E storages.

## Items and fluids

| Loader | Items | Fluids |
| --- | --- | --- |
| Forge | `ForgeCapabilities.ITEM_HANDLER` | `ForgeCapabilities.FLUID_HANDLER`, millibuckets |
| Fabric | Transfer API `ItemStorage.SIDED` | Transfer API `FluidStorage.SIDED`, 81 droplets per millibucket |

On Fabric, BuildCraft moves whole millibuckets only, so both loaders transfer the same amounts.
Storages that roll back (tanks, machine batteries) take part in transactions directly. Pipes cannot
undo inserted items or fluid, so they simulate inside a transaction and receive the committed
amount at the end of the server tick.

Recipes and tags use both conventional `c:` tags (Fabric 1.19.2 plural names such as
`c:iron_ingots`) and `forge:` tags, so either loader's materials work in the same recipe JSON.
BuildCraft's gears are tagged `c:<material>_gears` and `forge:gears/<material>`, and the wrench is
`c:wrenches` and `forge:tools/wrench`.

## Deliberate differences

- **Fluid NBT** is not modelled: fluids carrying NBT are refused by pipes, tanks and engines.
- **Fragile fluid shards** are not dropped when a fluid pipe holding fluid is broken; the fluid is lost.
- **Kinesis sections** accept no more than their neighbours requested, so simulated and real
  transfers agree for transactional callers. Upstream sections accept any amount.
- **Configuration** is fixed at upstream defaults (mining depth 512, pump distance 64, quarry area
  11×11); there is no config file.
- **Viscosity** uses Forge's scale on both loaders (water 1000, lava 6000), which is also Fabric's
  `FluidConstants` scale. The mining well and quarry pass through fluids up to water's viscosity,
  as upstream.
- **Engine model** is upstream's `engine_base.json` split in two: the base plate is a static block
  model, and `EngineRenderer` draws the piston plate, chamber and trunk. Upstream's trunk light
  (`stage_light`: 4 green, 7 yellow, 10 red and overheat) is applied as a minimum block light.
  Upstream's engine TESR stops at 1.12's 64-block range, hiding the whole engine; the port's renderer
  draws out to the chunk render distance.
- **Infinite water** detection uses vanilla's refill rule: the pump stops removing water sources
  only when a cell in the pool has two horizontal water sources over a solid block or a water
  source. Upstream also counted every already-scanned neighbour, including rejected air and stone,
  so it could treat finite pools as infinite and duplicate water.

## Not ported

- **Core:** markers, landmarks and volume boxes (so the quarry cannot be resized), paintbrush,
  list, map location, guide book, springs, decorated blocks, creative engine.
- **Energy:** oil and fuel worldgen (oil springs, lakes and wells). Oil, residue and light fuel
  therefore have no survival source yet.
- **Factory:** auto workbench, flood gate, chute, distiller, heat exchanger, water gel.
- **Builders:** filler, builder, architect table, electronic library, replacer, blueprints and templates.
- **Transport:** diamond fluid, diamond-wood, obsidian, lapis, daizuli, emzuli and stripes pipes;
  iron and diamond kinesis pipes; upstream's RF pipes (kinesis pipes speak FE/E instead); pipe
  wires, gates, plugs, facades and pluggables.
- **Silicon and robotics:** lasers, assembly and integration tables, chipsets, gates, robots.
- **Misc:** 1.12 world migration, IMC and addon APIs, statistics and advancements.

## Multiplayer

Server and clients must use the same loader. Cross-loader multiplayer and world migration between
the Forge and Fabric builds are not supported.

## Tests

Shared GameTest bodies in `src/gametestCommon` run on both loaders (`ForgeGameTests`,
`FabricGameTests`): the MJ/energy ratio, the viscosity scale, every engine powering a receiver, a
redstone engine driving a wooden pipe that delivers items through stone and gold pipes, tank
columns, the trunk stage following heat, redstone engines warming to red without overheating, pump draining (including a finite irregular pool and an infinite 2×2 pool), mining well and quarry digging, void pipes, fluid pipes filling a tank,
kinesis pipes carrying engine power, and recipes and tags. Loader-specific tests check the Forge
capabilities (`ForgeAdapterGameTests`) and the Team Reborn Energy and Transfer API storages
(`FabricAdapterGameTests`), including transaction rollback. `EmcGameTests` audits EMC coverage and,
with `-PwithProjectE` or `-PwithItemAlchemy`, the live values.
