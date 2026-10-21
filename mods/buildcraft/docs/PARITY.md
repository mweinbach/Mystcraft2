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
| Core | Wood, stone, iron, gold and diamond gears; wrench (rotates engines and the output face of wooden and iron pipes); other mods' `c:wrenches` and `forge:tools/wrench` items work too. Fragile fluid shards (upstream `ItemFragileFluidContainer`): breaking a tank, pump, distiller, heat exchanger, combustion engine or fluid pipe drops its fluid as unstackable shards of up to 500 mB, which can only be emptied (into a tank, or through the loader fluid API). The creative water spring (upstream `EnumSpring.WATER`), which puts a water source above itself every 5 ticks. Markers (upstream `BlockMarkerVolume`, `BlockMarkerPath`, `TileMarker*`, `MarkerCache` and the `core.marker` caches), placed on a solid face and turned to another face by sneak-using the wrench: using a land mark connects it to the nearest land mark within 64 blocks along each free axis and takes in marks standing on the new box's corners, so up to three axes outline a box; a mark placed on a box's corner joins it, breaking a mark shrinks the box, and a redstone signal shows lasers along the mark's free axes. Path marks connect end to end into paths and closed loops, using a path mark reverses its path, and breaking a middle mark splits it; path marks break without their support, while land marks stay, as upstream. The marker connector connects the two marks whose line the player is looking along (within 3 blocks and 0.3 blocks of the line, upstream `MarkerLineInteraction`). Connections are saved per dimension under upstream's names. Volume boxes (upstream `VolumeBox`, `WorldSavedDataVolumeBoxes`, `ItemVolumeBox`, `Lock`): the creative volume box item makes a one-block box against the clicked face. Using the marker connector on a box's corner within 4 blocks grabs it: the opposite corner stays put and the grabbed one follows the block the player looks at, at the grab distance plus half a block (at least 1.5), until the next click confirms it; sneaking cancels the edit, or removes the box the player looks at. An edit pauses while its player is away and resumes when they return. Machines lock boxes against resizing and removal until the machine's block is gone. Boxes are saved per dimension and sent to every player in it on each change. |
| Engines | Redstone (pulses 1 MJ while powered, warms slowly up to red but never overheats), Stirling (solid fuels, 1 MJ/t), Combustion (fuel, coolant and residue tanks, heat stages, overheating). Upstream heat, pulse and power-stage formulas. |
| Fluids | Upstream's ten oil and fuel families (oil, residue, heavy, dense and distilled oil; dense, mixed heavy, light, mixed light and gaseous fuel), each at three heat stages, with upstream density, viscosity, spread, boiling heat and colours: 30 fluids with source and flowing forms and buckets. Hot stages are lighter and runnier, and stages at or above a fluid's boiling heat are gases that flow up in the world and fill tanks and pipes from the top. |
| Combustion fuels | Upstream's nine fuels (gaseous, light, dense, mixed light and mixed heavy fuel; distilled, heavy and dense oil; oil), with power and burn times derived from their refinery amounts as upstream; the dirty fuels leave oil residue. Only the cool stage burns. Water, ice and packed ice cool the engine. |
| Oil worldgen | Upstream `OilGenerator` with its default configuration: from the world seed each chunk rolls a large (0.04%) or medium (0.1%) oil well in the Overworld, but never in a flat world. A well is a surface oil lake with tendrils, an oil sphere at y 20–29, and a spout from the sphere that gushes 10–20 (large) or 6–12 (medium) blocks above the surface. Large wells also run an oil tube down to an oil spring at the bottom of the world, which every 6000 ticks and on random ticks has a one in eight chance to put an oil source above itself. The oil desert and oil ocean biomes (upstream `BiomeOilDesert`, `BiomeOilOcean` and `GenLayerBiomeReplacer`) replace deserts and oceans wherever upstream's seeded simplex noise passes its thresholds (scale 0.001 over 0.7 for deserts, 0.0005 over 0.9 for oceans), and wells are thirty times likelier in them, as upstream's default `excessiveBiomes`. |
| Refinery | Distiller (upstream `TileDistiller_BC8`: 4000 mB tanks, up to 6 MJ/t, upstream's ten distillations, each at its own heat stage; gas leaves the top and liquid the bottom; GUI with upstream gauges and animation) and heat exchanger (upstream `TileHeatExchange`: rows of three to five form a start, middles and an end; the coolant must be hotter than the fluid it heats; 5, 10 or 20 mB/t for one to three middles after a 120-tick warm-up; lava heats and water cools; the wrench turns a row around). |
| Factory | Tank (stacks into columns, fills bottom-up, balances), pump (drains connected fluid bodies up to 64 blocks away, outputs into tanks or pipes), mining well (mines straight down, outputs into inventories or pipes). |
| Builders | Quarry with frame building, digging and its tool head. As upstream's `TileQuarry.onPlacedBy`, a quarry placed with its back against a land mark whose box spans at least four blocks along X and Z takes that box as its area, and otherwise one whose back faces the bottom edge of a loaded land mark box at its own height, at least three blocks along X and Z; either way it breaks the box's marks, which drop. Without marks it uses upstream's default 11×11 area. The creative filler (upstream `TileFiller` and `TemplateBuilder`, no recipe, as upstream): placed with its back against a volume box, it copies the box and locks it against removal and resizing, drawn in write stripes; otherwise it takes a land mark area behind it and breaks the marks. It fills that area with one of upstream's 18 patterns (clear, fill, box, frame, pyramid, stairs, sphere and its half, quarter and eighth, and the arc, circle, hexagon, octagon, pentagon, semicircle, square and triangle 2D shapes) with their parameters (hollow or filled, axis, direction, centre, facing, rotation), optionally inverted. A gate facing the filler can set that pattern, locking it for three ticks and for as long as the gate keeps setting it, and can turn the filler on, off or into a loop; off skips building. It uses the first block item in its 27 insert-only resource slots for each block, and with excavation on (the default) breaks every block the pattern leaves empty before placing. As upstream's `SnapshotBuilder`, it checks 10 blocks a tick, queues up to 16 break and 16 place tasks, spends up to 256 MJ a tick from a 16,000 MJ battery split between them (more the fuller the battery), breaks the layers farthest from the filler's height first, each from the box's centre out, places the layers nearest its height first, each from the far side in, and rechecks blocks when the world changes them. The creative builder (upstream `TileBuilder`, `BlockBuilder`, `ContainerBuilder` and `BlueprintBuilder`, no recipe, as upstream) builds the recorded template or blueprint in its snapshot slot at the recorded offset from the block behind it, turned from the facing it was recorded with to its own. Templates are built from any block item in its 27 resource slots, as the filler builds. Blueprints place each block only once every item and fluid its schematic costs is in the resources or the four 8,000 mB tanks and the blocks it rests on are built; a fluid is drained from the tanks and returned to them if the placement is cancelled. With a full battery the builder then spawns the blueprint's entities from their items, and removes entities in its area that a capturing rule names but the blueprint does not have. As upstream, any entity within 0.1 blocks of where the blueprint has one counts as that entity, whatever its kind. Until it is done its GUI lists what the blueprint still needs, with counts. Excavation is always on, as upstream commented out the button. Placed with its back against a path mark, it takes the path, removes the marks and builds at every block along it in turn, stopping at the last. As upstream, a step counts as done once everything left in it is queued, so the builder moves on and cancels any placement still waiting for power. A placement costs 10 MJ per block of distance from the builder, and an empty battery allows about 0.2 MJ a tick (a full one about 128), so a builder that starts a path uncharged skips every step but the last; charge it before giving it a snapshot. The slot on its front shows whether it holds a template or a blueprint, and a wrench turns it only while it has nothing to build. Its slots and tanks are open to automation from every side, and it shares the filler's battery, task queues and power use. Upstream gives the filler, builder, electronic library and replacer no recipe, and the first three's tooltips read "Not usable in survival" (upstream named no replacer and gave it no tooltip), so in this port they are creative-only and have no EMC value. |
| Blueprints | The architect table (upstream `TileArchitectTable`, `BlockArchitectTable`, `ContainerArchitectTable`): placed with its back against a volume box, it copies the box and locks it against removal and resizing, drawn in read stripes; otherwise it takes a land mark area behind it and breaks the marks; with neither it is invalid and its front light goes out. A blank template or blueprint in its input is recorded at 900 or 300 blocks a tick, X then Z then Y, onto a recorded one in its output, carrying the owner, the date and the name typed into the GUI (up to 32 characters, upstream's field limit). Automation inserts blanks and extracts recordings from any side. Templates (upstream `Template`) record which blocks are filled. Blueprints (upstream `Blueprint`) record a palette of block schematics and the entities a capturing rule names: upstream `SchematicBlockDefault` (block state, block entity data, cost and the neighbours to build first, by upstream's `RulesLoader` JSON rules), `SchematicBlockFluid`, `SchematicBlockAir` and `SchematicEntityDefault` (entity data, position and, for item frames and paintings, the block and side they hang on), chosen by upstream's priorities. Blueprints record Minecraft's blocks and those of mods that ship rules, never BuildCraft's own, as upstream's release builds; fluid sources are recorded whatever mod adds them, BuildCraft's oil and fuels included, since upstream's `SchematicBlockFluid` takes no namespace check. Recordings are kept as gzipped NBT files named by their SHA-256 hash in `snapshots-server` next to the saves folder, shared by every world, as upstream's `GlobalSavedDataSnapshots`. Blank templates and blueprints stack to 16 and recorded ones do not stack, as upstream. The rules (`assets/buildcraft/compat/buildcraft/builders`) restate upstream's 28 vanilla rule files for 1.19.2 blocks, entities and items. |
| Library | The creative electronic library (upstream `TileElectronicLibrary`, `BlockElectronicLibrary`, `ContainerElectronicLibrary`, no recipe, as upstream) copies recordings between the server and each player's local library, kept as the server's are in `snapshots-client` next to the saves folder. A recorded template or blueprint in `downIn` comes out of `downOut` after 50 ticks, and its snapshot goes to every player with the GUI open, who keep it in their local library under its name. Clicking a row of the GUI's list selects a local snapshot for the library; with one selected and any template or blueprint in `upIn`, after 50 ticks the players with the GUI open are asked for it, whoever has it uploads it in 4 KiB parts, and the server stores it and puts a recording of it, under its local name, in `upOut`. The arrows fill over each copy, and Del deletes the selected snapshot from the local library. Automation reaches every slot from every side but takes only from the outputs; a wrench turns the library. The creative replacer (upstream `TileReplacer`, `BlockReplacer`, `ContainerReplacer`, no recipe) takes a recorded blueprint and two recorded single block schematics, stores a copy of the blueprint with every block matching the first schematic swapped for the second, puts a recording of the copy in place of the blueprint, under its name and owned by whoever placed the replacer, and uses up both schematics. Automation reaches none of its slots. The single block schematic (upstream `ItemSchematicSingle`, creative-only because upstream commented out its recipe) used on a block records it before the block's own use runs, so chests and doors do not react; a recorded one builds the block again on the clicked face from the items in the player's inventory, in creative too, and otherwise says on the action bar what is missing. Used while sneaking it is wiped. Blank schematics stack to 16 and recorded ones do not stack, as upstream. |
| Silicon | The laser (upstream `TileLaser`, `BlockLaser`) stores up to 1024 MJ from engines or the loader energy API and, every tick, beams power into a random laser table that needs it within a six-block square cone in front of it with a clear line of sight: 4 MJ scaled by how full its battery is (4 MJ × (stored + 4 MJ) / 512 MJ), at most 4 MJ and at most what the table still needs. It looks for tables again when a block within six blocks changes and picks another every 10 to 20 ticks. As upstream it is placed facing the player, or up or down when placed from below or from more than two blocks above while standing beside it, and the wrench turns it through all six faces. The assembly table (upstream `TileAssemblyTable`, `ContainerAssemblyTable`) lists every assembly recipe its twelve slots could make; clicking a listed recipe saves it, and saved recipes with enough ingredients take turns, each made once its laser power is in and put into a neighbouring inventory or dropped. A saved recipe stays listed without ingredients; unsaving it drops it from the list. Upstream's five chipset recipes are datapack recipes of type `buildcraft:assembly` (redstone 10,000 MJ, iron 20,000, gold 40,000, quartz 60,000, diamond 80,000) with upstream's ingredients under both `c:` and `forge:` tags. The advanced crafting table (upstream `TileAdvancedCraftingTable`, `ContainerAdvancedCraftingTable` and `WorkbenchCrafting`) crafts its phantom 3×3 blueprint for 500 MJ a craft once its fifteen material slots hold exact copies of every blueprint item, putting the result in its nine result slots and returning remainders such as buckets to the materials. The integration table (upstream `TileIntegrationTable`, `ContainerIntegrationTable`) integrates the eight stacks around its centre into the centre stack for a recipe's laser power; recipes are datapack recipes of type `buildcraft:integration`, and as upstream none ship, so its tooltip reads "No recipes use this!". Each table collects power up to its current job's cost, drops everything but phantom slots when broken, and fills a progress bar towards the job; the assembly table also shows the job's cost, the power stored and the average received in upstream's power ledger. The laser and tables have upstream's crafting recipes. |
| Item pipes | Wood (extracts when powered by an engine), cobblestone, stone, quartz, gold, sandstone, iron, diamond (filter GUI), clay, void. |
| Fluid pipes | Wood, cobblestone, stone, quartz, gold, sandstone, iron, clay, void, with upstream capacities and transfer rates. |
| Kinesis pipes | Wood, cobblestone, stone, quartz, gold, sandstone, with upstream power limits. |
| Structure pipe | Upstream `PipeFlowStructure`: carries nothing, connects only to other structure pipes and never to machines; eight from cobblestone, gravel and cobblestone. Its tooltip reads "Support pipe". |
| Pluggables | Upstream's pluggable framework (`PipePluggable`, `PluggableDefinition`, `IItemPluggable`, `PluggableHolder`): each face of a pipe holds at most one pluggable, put on by using its item on that face, saved and synced with the pipe and added to its shape. Breaking a pluggable takes off only that pluggable, which drops unless the player is in creative; a pipe broken in survival drops its pluggables, and a creative player breaking the pipe itself removes them without drops, as upstream's `removedByPlayer`. The pipe plug (upstream `PluggableBlocker`, four from a structure pipe) blocks its face: the pipe stops connecting there and offers no item, fluid or energy port on it. The power adapter (upstream `PluggablePowerAdaptor`, four from six structure pipes, a gold ingot, a stone gear and redstone) blocks its face the same way but passes energy on it to the pipe's behaviour, so an engine or the loader energy API drives a wooden pipe without connecting to it. As upstream's `PIPE_BEHAVIOUR_ACCEPTS_RS_POWER`, it only goes on a face whose pipe takes redstone engine power. |
| Gates | Upstream's gates (`GateVariant`, `GateLogic`, `PluggableGate`, `ItemPluggableGate`, `BCSiliconStatements`) and statements (`api.statements`, `lib.statement`): 25 gate items, the basic clay brick gate and iron, nether brick and gold gates in AND and OR, each plain or with a lapis, quartz or diamond modifier. A gate goes on a pipe face by using it there, blocks that face as a plug does, and drops as its own item. Clay brick gates have one trigger and action pair, iron two, nether brick four and gold eight; a lapis modifier gives each trigger an item parameter, and quartz (one trigger and one action parameter) and diamond (three of each) cost half the pairs. Every tick the gate checks its triggers and runs its actions: an ungrouped pair acts on its own trigger, and pairs joined by the toggles between them act together when all (AND) or any (OR) of the group's triggers are on. The gate glows while any action runs. Triggers: always on; a redstone signal reaching the pipe, or with Gate Side Only the gate's face, on or off; a neighbouring inventory empty, holding items, with room, full, or filled below a quarter, half or three quarters, optionally for one item; a neighbouring tank likewise, optionally for the fluid in a container item; a neighbouring mining well, pump, distiller or laser table with or without work; a neighbouring engine at each heat stage; items, optionally of one kind, or fluid in the gate's pipe; power requested from a kinesis pipe. Actions: a full-strength redstone signal from every face of the pipe, or with Gate Side Only from the gate's face, and facing an iron or wooden pipe to any side it can face. Redstone wire connects to a pipe carrying a gate. As upstream's, the pipe's signal is strong as well as weak, so a conductive block touching the pipe is powered by it and feeds it back: a gate that turns a redstone input into a signal stays on once such a block touches its pipe. The gate copier (upstream `ItemGateCopier`) copies a gate's statements, parameters and grouping and pastes them onto other gates from either hand, saying so on the action bar; on an empty gate with nothing copied it says there is nothing to copy and the gate's GUI opens, and sneak-using it clears it. Basic, iron and nether brick gates and iron lapis and quartz gates have upstream's crafting recipes, a one-item recipe swaps any other gate between AND and OR, and the assembly table makes iron, nether brick and gold gates from chipsets, adds each modifier and makes the copier, at upstream's laser power. |
| Pipe wire | Upstream's pipe wire (`ItemWire`, `WireManager`, `EnumWirePart`, `EnumWireBetween`, `WireNode`, `WireSystem`, `WorldSavedDataWireSystems`) and pipe signal statements (`TriggerPipeSignal`, `ActionPipeSignal`, `TriggerParameterSignal`, `ActionParameterSignal`): sixteen colours of wire, eight from a redstone dust and a dye in the assembly table at 10,000 MJ, as upstream. Using wire on a pipe puts it on the corner of the pipe nearest the click; using it on a wire puts it on the next corner across the clicked face, in the next pipe if the face leads there. Each corner holds one wire. Wires of one colour join along the pipe's edges and across faces to connected pipes (and from structure pipes to any pipe their unblocked face touches), and the lengths between them join the pipe's shape. Joined wires of a colour and the gates on their pipes form a wire system, saved per dimension; at the end of every tick a system is powered while any of its gates emits its colour, as upstream's `gatesChanged` flag was never cleared. Breaking a wire while looking at it takes off only that wire: a corner drops its wire, a length along the pipe takes off and drops the corners at both ends, and a length to the next pipe takes off this pipe's corner. A broken pipe drops its wires. A gate offers "Pipe Signal On" and "Off" triggers and a "Pipe Signal" action for each colour of wire on its pipe. The action powers its colour and up to three more chosen as parameters; each trigger's three parameters each require another colour on or off. Lit wires are drawn full bright with upstream's lit texture row, unlit ones in the world's light. |
| Lenses and filters | Upstream's lenses and filters (`PluggableLens`, `ItemPluggableLens`, `FilterEventHandler`): 34 items, a lens and a filter in each of the sixteen colours and clear. The assembly table makes a lens from a glass block of its colour, and a filter from the same glass and iron bars, for 500 MJ, as upstream; any glass, stained or not, makes the clear ones, as upstream's `blockGlass`. They go only on item pipes, by using them on a face, and stay open to connections, adding a thin box at the face to the pipe's shape. An item carries its colour through pipes, saved and sent to clients as upstream. A lens paints every item crossing its face, either way, and a clear lens washes the colour off. A filter refuses items of any other colour; when the pipe picks a way out, it puts items of its own colour through its face first, never sends items of other colours through it, and sends unpainted items through it last. A filter on the next pipe's facing side steers the same way from the pipe before it: items that will carry its colour there go towards it first and unpainted ones last. Painted items in pipes are drawn inside a box of their colour. |
| Pulsars and timers | Upstream's pipe pulsar and timer (`PluggablePulsar`, `PluggableTimer`, `ActionPowerPulsar`, `TriggerTimer`). The assembly table makes a pulsar from a redstone engine and two iron ingots for 1000 MJ, and a timer from a clock for 500 MJ. A pulsar goes only on a pipe that takes redstone engine power, by using it on a face, and blocks that face. Clicking it toggles a manual switch (the lever click, quieter when switching off). While it is on it injects 1 MJ every 20 ticks. A gate on the same pipe can hold the pulsar on its face on, or queue one pulse, which waits until the pipe can take the power and then stops. A timer blocks its face and adds 5, 10 and 15 second triggers to every gate on its pipe; each is active for the one tick the world time divides that many seconds. |
| Light sensor | Upstream's light sensor (`PluggableLightSensor`, `TriggerLightSensor`). The assembly table makes one from a daylight detector for 500 MJ. It goes on any pipe, by using it on a face, and blocks that face. A gate is offered Bright and Dark only on that face: Bright is on when the block just outside is light 8 or more, and Dark when it is darker. |
| Facades | Upstream's facades (`PluggableFacade`, `ItemPluggableFacade`, `FacadeStateManager`, `FacadeAssemblyRecipes`, `FacadeSwapRecipe`). One `plug_facade` item stores the copied block state and the hollow flag in `facade` NBT. The assembly table makes six full and six hollow facades from three structure pipes and one block for 64 MJ. A full facade blocks its face, as a plug does; a hollow one leaves the pipe connected. Both add a two-pixel slab across the face, and a sturdy copied face holds a torch. A single facade in a crafting grid flips hollow. The creative tab lists one phased example and every visible state, full and hollow. |
| Machine control | Upstream's machine control (`ActionMachineControl`, `IControllable`) and the filler's patterns as gate actions (`Pattern`, `BCBuildersStatements`). A gate facing a filler offers On, Off and Loop, which set its control mode, and each pattern the gate has enough parameter slots to hold: none, clear, fill, box and frame on every gate, a sphere once the gate has one action parameter, and the other shapes once it has two or three. Setting a pattern locks the filler's screen. In the tracked 8.0 sources only the filler accepts control. |
| Robotics | The zone planner (upstream `BlockZonePlanner`, `TileZonePlanner`, `ContainerZonePlanner`, `GuiZonePlanner` and `RenderZonePlanner`) and the map location and paintbrush it uses (upstream `ItemMapLocation`, `ItemPaintbrush_BC8`, `ZonePlan` and `ZoneChunk`). Sixteen paintbrush slots, an import pair and an export pair. A coloured paintbrush and one zone map copy that zone onto the brush's layer over 201 ticks, stored relative to the planner; the same pair copies the layer back onto a blank map in world coordinates. The 256×228 screen is a top-down map you paint and pan, with the progress arrows and the slots. The block's face shows that map. A map location records a spot, a land mark's area or a path mark's path, and carries a zone; sneaking in the air clears one. A clean paintbrush and one per dye colour craft from upstream's recipe, and a dye recolours a clean brush. |
| Client | Upstream engine model (base plate, stroking piston plate and chamber, trunk textured and lit by heat stage), pipe contents, tank, mining tube and quarry renderers; distiller (bobbing power indicators coloured by power use, three fluid tanks) and heat exchanger (input and output tanks, coolant and heatant flowing through the row while it runs) renderers on upstream's block models; Stirling, Combustion, distiller, diamond pipe and filler screens (upstream `GuiFiller`: the pattern and parameter slots with upstream's pop-up rings of choices, patterns dragged in from the side panel, excavate and invert buttons, the lock icon, the machine-control icon when a gate has turned it off or set it looping, and the progress ledger counting blocks left to break and place); the filler's blocks flying from it to where they land, its robot hovering over the blocks it breaks and firing upstream's red-to-blue power lasers at them, and the write stripes around a land mark area it took (upstream `RenderFiller` and `RenderSnapshotBuilder`); the architect table screen (upstream `GuiArchitectTable`: the snapshot slots, the progress arrow and the name field), the glow over every block it records, fading over 50 ticks (upstream `RenderArchitectTables`), and the read stripes around a land mark area it took (upstream `RenderArchitectTable`); the builder screen (upstream `GuiBuilder`: the snapshot and resource slots, the four tank gauges and what the blueprint still needs), its write stripes around the area it builds, the directional path laser along its path, and its flying blocks and robot as the filler's (upstream `RenderBuilder`); recorded templates and blueprints show their name and author; the electronic library screen (upstream `GuiElectronicLibrary`: the local library's list, the copy arrows and the Del button) and the replacer screen (upstream `GuiReplacer`: the blueprint drawn turning above the slots); recorded templates, blueprints and single block schematics show their snapshot turning in their tooltip, fetched from the server once per snapshot (upstream `ClientSnapshots` and `BCBuildersEventDist`); the laser's beam to its table in upstream's four power laser colours by average power, drawn above 0.2 MJ a tick and moving to a new spot on the table every 5 to 10 ticks (upstream `RenderLaser`); the assembly table screen (upstream `GuiAssemblyTable`: inputs, the listed recipes with their saved and active marks, the progress bar and the power ledger), the advanced crafting table screen (upstream `GuiAdvancedCraftingTable`: blueprint, materials, results and the blueprint's result) and the integration table screen (upstream `GuiIntegrationTable`); the gate screen (upstream `GuiGate` and its `gate.json` layout: each pair's trigger, action and parameters with their on lights, the toggles that group a pair with the next, and the triggers and actions the gate can use on either side, grouped by face; pressing a statement or parameter opens upstream's ring of choices, sources are dragged in, shift-clicking clears a statement and right-dragging copies one; tooltips name each statement and its face), gates drawn as upstream's `plugs/gate.json` and `gate_dynamic.json` (material body, logic plate, modifier studs and a light that glows at full brightness while the gate is on), and the copier's empty and full models; pipe wire and the lengths between wires drawn as upstream's `PipeWireRenderer` within the pipe renderer's 64-block range; lenses and filters drawn from upstream's `lens.json` and `filter.json` models, tinted with upstream's light colours, and painted items in pipes inside upstream's coloured item box; upstream textures. Marker lasers follow upstream's `LaserRenderer_BC8` and `BuildCraftLaserManager` (laser textures, row layout, scales and colours): land mark boxes, signal lasers 64 blocks along a powered land mark's free axes, path lasers along each step, and, while a marker connector is held, the connections it could make, in the default colour for the one the player is looking along. Volume boxes are drawn in the connected land mark laser, in the signal laser while the player edits them, and in the striped laser of a machine that holds them (upstream `RenderVolumeBoxes`). As upstream's `WidgetFluidTank`, a container carried onto a distiller or combustion engine gauge empties into that tank or fills from it (ice melts into the engine's coolant), and shift-clicked containers go to the distiller's input or try the engine's fuel, coolant and residue tanks in turn. |

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
- **Fragile fluid shards** are drawn as upstream's base and fluid textures, with the fluid layer
  tinted by the fluid sprite's average colour; upstream used Forge 1.12's bucket model, which
  masked the fluid's own sprite. Shards of gases are drawn upside down, as upstream's `flipGas`.
- **Kinesis sections** accept no more than their neighbours requested, so simulated and real
  transfers agree for transactional callers. Upstream sections accept any amount.
- **Water springs** are not generated in the world, as upstream: its `SpringPopulate` and
  `generateWaterSprings` option exist at the pinned revision, but nothing registers the generator.
- **Configuration** is fixed at upstream defaults (mining depth 512, pump distance 64, quarry area
  11×11, marker range 64); there is no config file.
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
- **Oil well placement** drops 1.12's eight-block decoration offset, so a well is centred inside the
  chunk that rolled it. Wells generate in the Overworld's lakes step through a Forge biome modifier
  or Fabric's biome API, which matches upstream's default dimension and biome exclusions.
- **Oil biomes** are placed by a mixin on the multi-noise biome source, since 1.19.2 has no biome
  layers: each biome it picks for the Overworld's quarter-block cells goes through upstream's ocean
  then desert replacer, with the noise read at the cell's first block as 1.12's block-resolution
  layer did. Only the overworld's own biome source is touched, as upstream only wrapped the
  overworld's layers; other dimensions keep their biomes even when they reuse the overworld preset,
  and a flat or single-biome overworld has no oil biomes. Both layers share upstream's offsets from `new Random(seed)`, so the fields correlate
  as upstream's did. Deserts and oceans are matched by the `buildcraft:oil_desert_replaceable` and
  `buildcraft:oil_ocean_replaceable` biome tags instead of upstream's biome dictionary: its hot, dry
  and sandy types become vanilla's desert plus `forge:is_desert` and `c:desert`, and its ocean type
  every `minecraft:is_ocean` biome. The oil biomes carry the matching Forge and `c:` tags. The biomes copy their
  parent's 1.19.2 climate, features, spawns and colours, and keep the parent's surface: surface rules
  that name the desert or ocean also match their oil field. They join the parent's mineshaft, ruined
  portal, ocean ruin, rabbit, frog and music tags but not its village, pyramid, temple or monument
  tags, since upstream only allowed villages in vanilla's own biomes and oil fields are meant to be
  open ground. The 1.12 config toggles for the biomes and the excessive and surface-deposit biome
  lists are not ported; as upstream's defaults, no biome forces surface-only lake wells.
- **Gaseous fluids in the world** flow up, as upstream's negative-density fluid blocks did: a gas
  rises in a column, spreads under ceilings and seeks the nearest way up, mirroring vanilla's
  downward spread with the same slope-find distance and drop-off. They are still drawn by vanilla's
  fluid renderer, so a thin gas layer under a ceiling sits at the bottom of its block instead of
  hanging from the top as upstream drew it.
- **Heat exchanger tanks** are saved on both the start and the end; upstream saved only the end's,
  so the start's fluid was lost on reload. When a row re-forms, a surviving start or end carries its
  tanks to the new row's start or end; a broken start or end, or one left in a row shorter than
  three, drops its fluid as fragile fluid shards, as upstream. Middle sections accept and give no fluid. The flowing
  coolant and heatant use the fluid's animated sprite, where upstream used its first frame.
- **Distiller progress** on a recipe whose input runs out or whose output is blocked returns to the
  battery even when the battery has refilled to capacity; upstream discarded it then.
- **Marker sync** uses each mark's block entity data instead of upstream's `MessageMarker`: a mark
  sends the positions of its connection when the connection changes, and clients rebuild the
  connection from it. Clients therefore only know the marks they have loaded, so the possible
  connections drawn while holding a connector skip unloaded marks, and a connection is drawn from
  the first of its marks the client has loaded. Closing a path into a loop from its last mark
  resyncs the path, which upstream left to the next change. A connector click that connects marks
  is consumed, so it does not also use the item in the other hand; upstream let it fall through.
- **Volume box sync** sends the box list in one loader message per change (Forge `SimpleChannel`, Fabric
  networking API) in place of upstream's `MessageVolumeBoxes`. A connector click on a box is predicted
  from the boxes the client was sent, and a click that does nothing falls through to the other hand,
  as upstream's FAIL did in 1.12.
- **Volume box locks and paused edits:** a removal lock keeps a box from the connector's sneak-click
  as well as a resize lock; upstream only checked for a resize lock. A player who takes over an edit
  that was paused when its player left also ends the absent player's claim, so the absent player
  cannot resume it on rejoining; upstream kept the claim and resumed an edit with no held corner.
- **Filler placement** uses each block item on the top face, facing south, without a player, as
  upstream's fake player did with its default facing; the owner's permissions are not checked, as for
  the quarry. Broken blocks are destroyed without drops, as upstream discarded them. A block that
  could not be placed goes back to the resources and drops if they are full; upstream voided it then.
  The builder hears block changes through a mixin on `ServerLevel` in place of 1.12's world event
  listener. The octagon makes nothing and the square and octagon take only two parameters, as
  upstream. The progress ledger stays open between GUIs only until the game closes; upstream saved it
  in its config. The filler is drawn wherever it is loaded, without 1.12's render box around its area.
- **Builder power and progress:** each tick, the break and place tasks split that tick's allowance
  by how many were queued when the tick began. Upstream divided by the shrinking queue as tasks
  finished, so the tasks after them could take more than the allowance. The blocks left to break
  and place include those already queued; upstream left them out, so its progress ledger read zero
  while up to 16 blocks were still in hand.
- **Builder saving:** the builder saves its place along a path and reloads its snapshot from the item
  in its slot. Upstream did not save its snapshot, and reloading the slot sent the builder back to the
  first step of its path. The slot model comes from a `snapshot_type` block state property in place of
  upstream's `getActualState`. Upstream sent what a blueprint still needs in one message per change;
  here it reaches clients in the builder's block entity data. After a wrench turn, a builder without a path builds behind its
  new back; upstream kept building behind the old one.
- **Marker block state:** upstream's `active` property, which its model ignored, is not ported.
- **Path mark permissions** only check that the player may build; upstream also asked the
  permission API, which nothing in the port provides.
- **Architect recordings** each start with an empty palette, and a scan whose input changes between
  template and blueprint starts over. Upstream never cleared its palette, so later blueprints carried
  every earlier one's entries and the same area could record to a different hash. A scan restarts after
  a reload, as upstream's did. Each tick's scanned blocks reach clients in the table's block entity data
  instead of one upstream message per block, and the progress arrow and the glow count game time instead
  of client ticks; the glow is cleared when the client changes level. The glow is upstream's path without
  the stencil buffer, which is not clipped to the box's outline on screen. The template recipe drops
  upstream's `random_things` recipe group.
- **Snapshot items:** upstream's one `snapshot` item with four metadata values is four items:
  `template_clean`, `template_used`, `blueprint_clean` and `blueprint_used`. Recipes and rules name items
  without metadata, so upstream's `meta` fields have no equivalent, and a rule naming an unknown item costs
  nothing for it. Recordings are always written as gzipped NBT; upstream also read its own packed format.
- **Schematic data:** the block offsets a block needs and the properties it ignores are saved sorted,
  where upstream wrote hash set order, so equal schematics always hash alike. Waterlogged blocks, which
  1.12 did not have, also cost the 1000 mB of water they hold; fluid schematics only cover fluid blocks.
  Recorded entities turn with the rotation the builder applies; upstream turned them the opposite way.
  Item frames save their side as `Facing` and paintings as `facing`, as 1.19.2 does; 1.12 used `Facing`
  for both.
- **Builder rules for 1.19.2:** each upstream rule is restated for the flattened ids, and a family
  upstream listed (slabs, stairs, doors, beds, signs, banners, saplings, flowers, rails and so on) covers
  all its 1.19.2 members: double slabs cost two, potted plants a pot and the plant, snow layers one snow
  per layer, water cauldrons one water bottle per level, lava and powder snow cauldrons their bucket.
  Upstream's lever rule named `up_x` twice and no `up_z`, so some floor levers had no support; the port
  gives every floor lever the block below. `port_additions.json` adds what upstream had no block for:
  candles, sea pickles and turtle eggs cost one per count, candle cakes a cake and a candle, campfires,
  lecterns and jukeboxes the items they hold, and sticky pistons the same piston handling as pistons.

- **Silicon:** upstream's single `redstone_chipset` item with five metadata values is five items, `redstone_chipset`,
  `iron_chipset`, `gold_chipset`, `quartz_chipset` and `diamond_chipset`. Upstream registered assembly recipes in code and
  integration recipes only from addons; both are datapack recipe types here, with upstream's assembly JSON fields
  (`result`, `MJ`, `components` of `ingredient` and `amount`) and, for integration, the centre stack as `target`. The
  assembly table lists recipes ordered by recipe id, then output; upstream ordered them by the sum of two string
  comparisons, which is not a consistent order. Upstream's screen showed only the first twelve listed recipes; here each
  viewer pages through the rest twelve at a time with arrows under the list. A saved recipe whose recipe a datapack reload
  removed, or changed to make something else, is dropped from the list. Upstream let automation insert into the integration table's result slot
  and not take from it; here automation takes results and cannot insert them. The laser sends its target and average
  power to clients when the target or the beam's colour band changes, where upstream sent them every tick; clients
  only use them to draw the beam. Phantom blueprint slots take a single item per click; upstream's
  `SlotPhantom` also adjusted counts, which the advanced crafting table ignores. The table menus send power figures as
  whole longs in vanilla menu data instead of upstream's GUI messages.
- **Electronic library:** upstream stored any upload, from any player, and filled `upOut` even when
  `upIn` was empty, which made recordings from nothing. Here an upload counts only from a player with
  that library's GUI open and only while `upIn` holds an item; each player has one upload in progress,
  of at most 32 MiB, and the server reads no snapshot that expands past 64 MiB or spans more than
  16,777,216 blocks (a 256-block cube, the most a blueprint's data fits in 64 MiB). Snapshots read
  anywhere must be at least one block along each axis and fewer than 2^31 blocks. Upstream filled the down
  arrow with a negative width, which drew texels beside it instead of the arrow; it fills from the right
  here. Upstream drew every row of the list, running past its panel over the inventory, with no
  scrolling; here the 13 rows that fit on the panel are drawn and clickable, so a local library of more
  than 13 snapshots cannot reach the rest from this GUI. As upstream, the local library names each file
  by the snapshot's hash alone and skips a snapshot it already has a file for, so a second copy of the
  same contents under another name or date keeps the first header it arrived with.
- **Replacer:** upstream labelled the new recording with the old blueprint's key, so the result still
  built the unchanged blueprint; here it names the replaced copy. Upstream's lang named no replacer, so
  the port calls it "Replacer". Its name field takes typing and does nothing, as upstream left it
  unconnected.
- **Single block schematic:** upstream recorded the whole held stack into one unstackable item, losing
  the rest of a stack of blanks; here one blank of the stack is recorded. Upstream used Forge's
  `onItemUseFirst`; each loader's block click event (`PlayerInteractEvent.RightClickBlock`,
  `UseBlockCallback`) runs it here. Upstream removed a clicked replaceable block such as tall grass or
  snow before checking the schematic and the items, so a failed build destroyed it and a schematic of
  that same block rebuilt it at a cost; here the block is removed only once the build can go ahead,
  put back if the build fails, and a schematic of the block already there builds nothing.
- **Snapshot previews:** upstream built each snapshot into a fake world and drew its chunk buffer. Here
  each block's model is baked once into vertex buffers without a world, so blocks take no shape or tint
  from their neighbours (fences do not join, grass takes its default colour), blocks with every side
  covered are skipped instead of culling faces, and fluids are drawn as boxes of their height; block
  entities (for snapshots under 32 blocks, as upstream) and entities are drawn each frame from the same
  schematics. The camera distance uses the largest of the three sizes, where upstream took the largest
  of X, Y and Y again. As upstream, the preview zooms with the GUI scale. In tooltips the preview is part
  of the tooltip instead of a separate box under it, and takes no room until the snapshot arrives.
- **Gates:** upstream's one gate item with the variant in its NBT is 25 items, one per variant, named as upstream's
  variants (`gate_clay_brick`, `gate_<material>_<and|or>_<modifier>`). Upstream's statement, glow and GUI messages
  become the pipe's client data (whether the gate is on), vanilla menu data (whether each trigger and action is on) and
  one loader message for the GUI's changes; the server takes a statement only when the gate could use it, as the GUI
  offers. That includes grouping: a gate with two columns has no link between the last pair of its left column and the
  first of its right, and the server refuses that link where upstream's container checked only the index. The pipe is a signal source while it carries a pluggable redstone connects to, a `redstone` block state set on
  the pipe's next tick, where upstream's pipe was always one. On Forge, wire connects only through a face with such a
  pluggable, as upstream; vanilla wire on Fabric connects to any signal source, so there it connects to every face of
  such a pipe. Upstream's copier check for an empty gate counted saved tags that are always present, so it never fired;
  here an empty gate with nothing copied says there is nothing to copy. A gate item's tooltip counts actions on its
  action line, where upstream repeated the trigger count. Upstream's tank Empty trigger asked whether anything could be
  drained, so a fill-only tank holding fluid (the distiller's input, an engine's fuel and coolant) read as both Empty
  and Contains; here Empty means every tank on that face is empty. The inventory Space and Full triggers without an
  item parameter count only empty slots, as upstream, so a chest whose slots all hold part stacks reads Full. Gates
  read no 1.12 string statements, as 1.12 worlds do not load.
- **Pipe wire:** upstream's one `wire` item with sixteen metadata values is sixteen items, `wire_<colour>`, named with
  1.19.2's colours (`wire_light_gray` for upstream's silver); the textures keep upstream's names. Upstream sent every wire
  system and which were powered to the players near them (`MessageWireSystems`, `MessageWireSystemsPowered`); here each
  pipe's client data carries its wires and which of its corners are powered, and the pipe sends it again when that
  changes. Upstream's flood fill loaded any chunk a wire led into; here it stops at unloaded chunks, and a gate in an
  unloaded chunk keeps the signal it last emitted until its chunk loads again. Taking off a wire with no joined neighbour
  also removes its old system, which upstream's `removeParts` left in the saved data. Upstream rebuilt the systems
  around a pipe when a pluggable came or went, but before the pipe's connections caught up and only from the corners
  next to its wires; here a pipe with wires also rebuilds them, and its lengths, once its connections change, and the
  rebuild starts from its own wires too, so a blocker splits a wire and a gate joins a lone corner. As for pluggables, breaking a wire
  goes through the player game mode mixins, and pick-block on a wire gives the pipe where upstream gave the wire. A
  wire's name colours its colour word with upstream's tooltip formatting (`ColourUtil.getTextFullTooltip`) instead of
  upstream's special font renderer, and the gate copier leaves out which colours a gate is emitting.
- **Lenses:** upstream's one `plug_lens` item with 34 metadata values is 34 items, `lens_<colour>`,
  `filter_<colour>`, `lens_clear` and `filter_clear`, named with 1.19.2's colours. Upstream baked lenses into the
  pipe's block model (`PlugBakerLens`); here the pipe's block entity renderer draws the lens item's model at the
  face. 1.12's water texture was already blue but 1.19.2's is grey and tinted per biome, so the clear lens tints
  it with the default water colour (`0x3F76E4`). Colour names use upstream's tooltip formatting instead of its
  special font renderer, and pick-block on a lens gives the pipe. Upstream's `IInjectable.injectItem` already
  took a colour; the port's `Injectable` gains the same parameter.
  Upstream's diamond pipe split each stack between every face it allowed, whatever their priority, so a
  diamond pipe with no matching filter sent painted items out of unfiltered faces beside their lens filter
  and unpainted items through the filters; here it splits only between the faces of highest priority. The
  faces matching its own filters always come first, so without lens filters it splits as upstream.
- **Pulsars and timers:** upstream baked the pulsar's moving piston and lights with its expression model
  (`PlugPulsarRenderer`, `pulsar_dynamic.json`); here the pipe's block entity renderer draws the same cuboids, UVs,
  stage formula and light colours, and the item shows the switched-off model (stage 0, dark lights) as upstream's
  `setModelVariablesForItem`. A pulsar loaded onto a pipe that does not take redstone power does nothing instead of
  throwing; placement still only allows pipes that do. Ids are `plug_pulsar` and `plug_timer` under `buildcraft:`
  rather than `buildcraftsilicon:pulsar` and `timer`. The port does not load 1.12 worlds, so upstream's transport
  alias is not registered. Pick-block gives the pipe, as for other pluggables. A saved timer trigger stays off
  unless that pipe still has a timer: upstream's `isTriggerActive` only tests the world time, and a gate keeps
  firing after the timer is removed. Clicking a pulsar marks the pipe's chunk unsaved; upstream folded that into
  `scheduleNetworkUpdate`, while `queueSync` only sends the client packet.
- **Facades:** one `plug_facade` item with NBT, rather than one item per block. Upstream's `FacadeAPI` IMC disable list and the leftover lang key `config.general.pipes.facadeBlacklist` (the Java config is gone at the pin) are the block tag `buildcraft:facade_blacklist`, empty unless a pack fills it. A placed facade keeps its states but does not change which one is showing: the pinned sources load `activeState` and never update it, which robotics would have done. The item tooltip names a hollow facade; upstream had `item.Facade.state_hollow` and did not append it. Rendering follows `PlugBakerFacade` in the pipe's block entity renderer, and also uses quads baked with no face when their direction is that face, because 1.19 models often store them there. Pipes are dynamic-shaped so 1.19 reads the live support shape: a facade face holds a torch when the copied block's face does. The item is `builtin/entity` with the plug display transforms. Pick-block gives the pipe, as for other pluggables. EMC is one value for every block, the dirt recipe divided by six, because ProjectE and Item Alchemy strip NBT; that counts a full block and cannot pay out a more valuable one. See the EMC notes.
- **Light sensor:** drawn like the timer, from `light_sensor.json`, by the pipe's block entity renderer. The item id is
  `plug_light_sensor` and the definition id is `daylight_sensor`, both under `buildcraft:` rather than
  `buildcraftsilicon:`. Light is `Level.getMaxLocalRawBrightness` of the block outside the face. Upstream's
  `World.getLightFromNeighbors` reads the brightest neighbour when the block is not a full cube. Pick-block gives
  the pipe. Removing the sensor stops the gate offering the triggers; `isTriggerActive` still reads the light, as
  upstream's does.
- **Machine control:** the port has no sided capabilities. A filler accepts On, Off and Loop from whichever side
  the gate faces, matching upstream registering the capability on every face. Only the filler is controllable, as
  in 8.0's common code. The quarry, builder, mining well, pump, laser, distiller and heat exchanger do not pause
  for a gate. A gate pattern change rebuilds the template immediately; upstream's `setPattern` did not, so a
  gate's pattern never reached the builder until something else refreshed it. Repeating the same pattern does not
  cancel the build. `FullStatement.set` skips parameter 0, so a gate never writes the pattern's first parameter,
  matching upstream. `finished` is never set true, so On and Loop both keep building, while `isFinished` still
  treats Loop as not finished. The screen shows the mode's sprite and its lowercase name as a tooltip when the
  mode is not On. Upstream's help overlay is not drawn.
- **Pluggables:** upstream baked each pluggable's model into the pipe's block model (`IPluggableStaticBaker`);
  here the pipe's block entity renderer draws upstream's plug and power adapter cuboids and textures, and the
  items use the same cuboids as their models. Breaking a pluggable goes through mixins on both sides' player game
  mode (`destroyBlock`) in place of Forge 1.12's `removedByPlayer`, and the client keeps the pipe instead of
  predicting it away. Pick-block on a pluggable gives the pipe; upstream gave the pluggable. The power adapter
  passes loader energy straight through the pipe's own port, which already converts at 1 MJ = 10 units, so it
  keeps none of upstream's `storedMJ` RF remainder.
- **Zone planner:** upstream stored the paintbrush and the map location as one item each, with metadata. This port uses a clean paintbrush, one item per dye colour, and one map location whose `mapType` tag drives the `buildcraft:type` model. A mixin keeps a clean map stacking to 16 and a recorded one to 1. Using a stack records one map and leaves the rest clean, so the recorded copy is not lost. A clean paintbrush is not a colour. Copying a zone chunk keeps a completely filled chunk, and reading a plan replaces what was stored. A plan packet applies only while that planner's GUI is open and still valid, and a plan refuses more than 4096 chunks. Map colours are read at world positions. The face texture is cached per facing, so a wrench updates the picture. The highlight is a line box. The scroll wheel uses the 1.19 notch (`delta * 4` in place of `dWheel / 30`). Painting and panning start on the map, so the slots stay usable. The recipe is the commented `BCRoboticsRecipes` pattern with a diamond item pipe in the centre of the top row, which upstream left as a TODO. The map location has no recipe. Block painting and the paintbrush's 64 uses are not part of the planner. Display lists are vertex buffers, and the three vanilla dimensions keep upstream's ids. The planner is not a container, so hoppers cannot see it. Kept from upstream: clearing a full chunk with `BitSet.flip(0, 255)` also clears column (15, 15); `distanceToSquared` uses `x << 4 + 8`; `columns()` lists a column in chunk 0 twice; the column search stops at y 255; import and export progress, including resuming from -1; the painted rectangle is half-open; the server scans the player's world for map chunks; and an input tick returns before the output runs.

## Not ported

- **Core:** volume box addons (the filler planner and its addon slots), list, guide book, decorated blocks, creative engine, and the
  `markers` and `path_markers` advancements.
- **Energy:** the oil spring's tracking of how much of its well each player has pumped, which
  unlocked upstream's `black_gold` advancement.
- **Factory:** auto workbench, flood gate, chute, water gel.
- **Builders:** the filler planner addon, upstream's in-GUI help overlays, the architect table's `architect`
  advancement and the builder's `paving_the_way` advancement, which upstream never unlocked because its
  path index stops one short of the condition. The rules do not yet cover beehive and bee nest
  occupants, composter levels or a triggered dropper (upstream only ignored `triggered` on dispensers).
- **Transport:** diamond fluid, diamond-wood, obsidian, lapis, daizuli, emzuli and stripes pipes;
  iron and diamond kinesis pipes; upstream's RF pipes (kinesis pipes speak FE/E instead);
  the pipe paint, extraction preset and kinesis power limit actions, which belong to pipes not yet ported;
  the gates' `pipe_logic` and `extended_logic` advancements; the wire's `logic_transportation` advancement; the pipe plug's `plugging_the_gap` advancement and `all_plugged_up`, which
  nothing in upstream unlocks.
- **Silicon:** the stored power triggers (`TriggerPower`, which no BuildCraft block offered); lists as item parameters; the charging and
  programming tables, which upstream only registered in development builds; the `laser_power` and `precision_crafting`
  advancements; the `renderLaserBeams` display option and goggles (beams are always drawn, upstream's default); the
  advanced crafting table's recipe book and upstream's in-GUI help ledgers.
- **Robotics:** robots, docking stations, the requester, redstone boards, the robot station pluggable, robot goggles and the old map system. At the pinned revision those live only in the MMPL `src_old_license` and `common_old_license` trees, so they are later slices. The MPL `robotics` module is the zone planner, which this port includes. The programming table remains the unregistered silicon stub.
- **Misc:** 1.12 world migration, IMC and addon APIs, statistics and advancements.

## Multiplayer

Server and clients must use the same loader. Cross-loader multiplayer and world migration between
the Forge and Fabric builds are not supported.

## Tests

Shared GameTest bodies in `src/gametestCommon` run on both loaders (`ForgeGameTests`,
`FabricGameTests`): the MJ/energy ratio, the viscosity scale, gases rising in the world, every engine powering a receiver, a
redstone engine driving a wooden pipe that delivers items through stone and gold pipes, tank
columns, the trunk stage following heat, redstone engines warming to red without overheating, pump draining (including a finite irregular pool and an infinite 2×2 pool), mining well and quarry digging, land marks
connecting into a box (saved, synced to clients, shrinking as marks break, showing signals when powered), path marks forming
paths and loops that save, reverse and split, the marker connector joining marks, volume boxes made once per spot and surviving saving and syncing with their locks, the connector grabbing, stretching, confirming, cancelling and removing boxes and pausing edits of absent players, locks holding until their block is gone, marks turning with the wrench and
breaking without support, a quarry taking a land mark box from a mark behind it or from the box's edge, filler patterns (upstream counts for the simple shapes, and every pattern at five sizes with each value of each parameter), the pattern statement keeping matching parameters and saving, the filler taking and locking a volume box until it breaks (dropping its resources) or taking a land mark area, its menu accepting only block items, its buttons, inversion and saving, building a box of stone after clearing its centre, clearing a box only once excavation is on, and keeping to each tick's power allowance while counting queued blocks as left to do, the architect table locking a volume box, recording a template (every scanned block sent to clients, the same area hashing alike) and a blueprint (block and entity costs, a door waiting for its lower half, surviving a save), taking a land mark area, refusing to record without an area, its menu slots, sides, name message and saving, snapshot storage, every builder rule naming 1.19.2 blocks, properties, entities and items that exist with upstream's costs and dependencies for flattened blocks, recorded blocks building back with their state and contents, and the template, blueprint and table recipes, the builder building a template turned a quarter round and digging out what the template leaves empty, building a blueprint's blocks turned and with their contents only once every item is in hand while listing (and syncing) what is still needed, placing a water source from its tanks, removing a captured entity the blueprint lacks and spawning its armour stand turned, following a path of path marks (skipping, as upstream, the steps it cannot power at once), and its menu slots, tanks, wrench turning, saving and drops, the electronic library copying a recording down to the client store after 50 ticks and uploading a client-only snapshot in parts into a recording (refusing uploads with `upIn` empty or the GUI closed, and selections for another library), its menu slots, sides, wrench turning, saving and drops, the replacer swapping a blueprint's stone for gold under the old name and owner while keeping the original, refusing automation and saving, the single block schematic recording from a stack, refusing air, building from the inventory, wiping when sneaking, recording a chest through each loader's block click hook without opening it, and tooltip previews, the laser powering only tables it can see within its cone at upstream's battery-scaled rate (saved, synced, re-targeting when sight is blocked, and turning with the wrench), the assembly table listing, saving and taking turns making iron and redstone chipsets into a chest with laser power (handing back excess power, keeping shift-clicks out of its display slots, opening every input to automation, saving and dropping its ingredients), paging through thirteen test recipes per viewer and dropping a saved output its recipe no longer makes, the advanced crafting table taking its blueprint by clicks, crafting a cake for 500 MJ with the buckets returned, keeping automation off its blueprint, saving and dropping all but its blueprint, the integration table integrating a test recipe around its centre into its extract-only result slot, pipe plugs placed by use breaking both sides' connections and the item port, growing the shape, saving and syncing, and breaking off alone through the player game mode (dropping, or removed with the pipe in creative), the power adapter refused on a stone pipe and letting a redstone engine drive a wooden pipe through a face a plug had blocked, structure pipes joining only each other, gates placed by use with their variant's pairs and parameters, saving their statements and grouping and sending clients whether they are on, a gate turning a redstone input into a signal from every face or only its own that drives a piston, AND and OR grouping, the gate copier copying, pasting from either hand, reporting an empty gate and clearing, the gate menu offering a chest's inventory triggers on its face and the server taking only statements, parameters and groupings the gate can use (refusing a gold gate's link across its columns), an iron pipe turned by its gate while inventory triggers read a chest and tank triggers read a distiller's fill-only input as holding fluid and not empty, pipe wire placed by use on the nearest corner or across a wire's face into the next pipe, joining only its own colour and not across a plugged face, in one wire system, growing the shape, saving and syncing, wires breaking off alone (a corner, both ends of a length along the pipe, this pipe's end of a length to the next) with their drops and a broken pipe dropping its wires, a gate's pipe signal powering only its colour along three pipes so that another gate's signal trigger turns on a redstone output, action parameters powering a second colour that trigger parameters require, the signal statements saving and offered only in the colours on the gate's pipe, and the wires turning off with the action, a blocker splitting and rejoining a wire system and a gate joining a lone corner's system, lenses and filters made in the assembly table (clear ones from any glass), named, placed only on item pipes, adding their box, saving, syncing and dropping, a filter's refusal and side priorities, a lens painting items that keep their colour when saved and on clients, filters sorting red and blue items at a junction while unpainted ones go the open way, and a filter on the next pipe steering its colour towards it while a clear lens on the way washes items, a diamond pipe splitting stacks only between the faces its lens filters put first, a pulsar placed only on a pipe that takes redstone engine power, pulsing 1 MJ every 20 ticks while switched on or held on by a gate (a single pulse waiting until the pipe can take it, and stopping once it has), a timer offering its 5, 10 and 15 second triggers for the one tick the world time divides, both saving and syncing, a light sensor placed on a pipe, offering Bright and Dark only on its face, reading glowstone-lit air as bright and stone as dark, and still reading the light once removed, facade rules (stone, glass and logs copy; chests, slabs, water and moving pistons do not; the blacklist drops a full cube), assembly of six full and six hollow facades for 64 MJ and the hollow swap, a full facade blocking both pipes and a hollow one leaving them connected, both saving, syncing and breaking off as their own item, and every copied-block corner staying inside the two-pixel shell on all six faces, machine control and filler patterns offered only beside a filler and only with enough parameter slots, a gate's circle skipping its first parameter and keeping the template until a later parameter changes, a gate turning the filler off (which places nothing), on (which places), off again (which freezes the count) and back on, and the control mode saving, a plug closing its face to Fabric block API caches, to Transfer API storages and Forge capabilities other mods kept, and removing it opening the face again, void pipes, fluid pipes filling a tank,
kinesis pipes carrying engine power, oil well rates and determinism over 65,536 seeded chunks
with the placed feature in the Overworld's lakes step, the oil biomes' thirtyfold well bonus, the
oil desert and ocean replacing deserts and oceans where upstream's noise says (recomputed
independently over a 65,536-block square) with their parents' climate, tags and surface, the refinery chain from oil to light fuel,
the combustion engine burning oil into residue, the distiller splitting oil and refunding blocked
progress, heat exchanger rows
forming, rotating and breaking (dropping shards), the water spring refilling, a heat exchanger heating oil with lava, fragile
fluid shards splitting fluid, dropping from every broken fluid holder and emptying into a tank,
containers clicked and shift-clicked on GUI tanks in survival and creative, the zone planner storing columns and a half-open painted rectangle (keeping upstream's full-chunk clear and chunk distance), map locations recording a spot, a land mark area and a path from a stack and clearing in the air, the planner importing and exporting a zone over 201 ticks and saving it, its menu refusing hoppers and a plan packet once the GUI closes and dropping its brushes, the map colouring the top block of a column, the paintbrush and zone planner recipes, and recipes (including the silicon machines and chipsets) and tags. Loader-specific tests check the Forge
capabilities (`ForgeAdapterGameTests`) and the Team Reborn Energy and Transfer API storages
(`FabricAdapterGameTests`), including transaction rollback and draining shards. `EmcGameTests` audits EMC coverage and,
with `-PwithProjectE` or `-PwithItemAlchemy`, the live values.
