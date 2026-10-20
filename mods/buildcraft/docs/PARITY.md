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
| Builders | Quarry with frame building, digging and its tool head. As upstream's `TileQuarry.onPlacedBy`, a quarry placed with its back against a land mark whose box spans at least four blocks along X and Z takes that box as its area, and otherwise one whose back faces the bottom edge of a loaded land mark box at its own height, at least three blocks along X and Z; either way it breaks the box's marks, which drop. Without marks it uses upstream's default 11×11 area. The creative filler (upstream `TileFiller` and `TemplateBuilder`, no recipe, as upstream): placed with its back against a volume box, it copies the box and locks it against removal and resizing, drawn in write stripes; otherwise it takes a land mark area behind it and breaks the marks. It fills that area with one of upstream's 18 patterns (clear, fill, box, frame, pyramid, stairs, sphere and its half, quarter and eighth, and the arc, circle, hexagon, octagon, pentagon, semicircle, square and triangle 2D shapes) with their parameters (hollow or filled, axis, direction, centre, facing, rotation), optionally inverted. It uses the first block item in its 27 insert-only resource slots for each block, and with excavation on (the default) breaks every block the pattern leaves empty before placing. As upstream's `SnapshotBuilder`, it checks 10 blocks a tick, queues up to 16 break and 16 place tasks, spends up to 256 MJ a tick from a 16,000 MJ battery split between them (more the fuller the battery), breaks the layers farthest from the filler's height first, each from the box's centre out, places the layers nearest its height first, each from the far side in, and rechecks blocks when the world changes them. The creative builder (upstream `TileBuilder`, `BlockBuilder`, `ContainerBuilder` and `BlueprintBuilder`, no recipe, as upstream) builds the recorded template or blueprint in its snapshot slot at the recorded offset from the block behind it, turned from the facing it was recorded with to its own. Templates are built from any block item in its 27 resource slots, as the filler builds. Blueprints place each block only once every item and fluid its schematic costs is in the resources or the four 8,000 mB tanks and the blocks it rests on are built; a fluid is drained from the tanks and returned to them if the placement is cancelled. With a full battery the builder then spawns the blueprint's entities from their items, and removes entities in its area that a capturing rule names but the blueprint does not have. As upstream, any entity within 0.1 blocks of where the blueprint has one counts as that entity, whatever its kind. Until it is done its GUI lists what the blueprint still needs, with counts. Excavation is always on, as upstream commented out the button. Placed with its back against a path mark, it takes the path, removes the marks and builds at every block along it in turn, stopping at the last. As upstream, a step counts as done once everything left in it is queued, so the builder moves on and cancels any placement still waiting for power. A placement costs 10 MJ per block of distance from the builder, and an empty battery allows about 0.2 MJ a tick (a full one about 128), so a builder that starts a path uncharged skips every step but the last; charge it before giving it a snapshot. The slot on its front shows whether it holds a template or a blueprint, and a wrench turns it only while it has nothing to build. Its slots and tanks are open to automation from every side, and it shares the filler's battery, task queues and power use. Upstream gives the filler, builder, electronic library and replacer no recipe, and their tooltips read "Not usable in survival", so in this port they are creative-only and have no EMC value. |
| Blueprints | The architect table (upstream `TileArchitectTable`, `BlockArchitectTable`, `ContainerArchitectTable`): placed with its back against a volume box, it copies the box and locks it against removal and resizing, drawn in read stripes; otherwise it takes a land mark area behind it and breaks the marks; with neither it is invalid and its front light goes out. A blank template or blueprint in its input is recorded at 900 or 300 blocks a tick, X then Z then Y, onto a recorded one in its output, carrying the owner, the date and the name typed into the GUI (up to 32 characters, upstream's field limit). Automation inserts blanks and extracts recordings from any side. Templates (upstream `Template`) record which blocks are filled. Blueprints (upstream `Blueprint`) record a palette of block schematics and the entities a capturing rule names: upstream `SchematicBlockDefault` (block state, block entity data, cost and the neighbours to build first, by upstream's `RulesLoader` JSON rules), `SchematicBlockFluid`, `SchematicBlockAir` and `SchematicEntityDefault` (entity data, position and, for item frames and paintings, the block and side they hang on), chosen by upstream's priorities. Blueprints record Minecraft's blocks and those of mods that ship rules, never BuildCraft's own, as upstream's release builds; fluid sources are recorded whatever mod adds them, BuildCraft's oil and fuels included, since upstream's `SchematicBlockFluid` takes no namespace check. Recordings are kept as gzipped NBT files named by their SHA-256 hash in `snapshots-server` next to the saves folder, shared by every world, as upstream's `GlobalSavedDataSnapshots`. Blank templates and blueprints stack to 16 and recorded ones do not stack, as upstream. The rules (`assets/buildcraft/compat/buildcraft/builders`) restate upstream's 28 vanilla rule files for 1.19.2 blocks, entities and items. |
| Item pipes | Wood (extracts when powered by an engine), cobblestone, stone, quartz, gold, sandstone, iron, diamond (filter GUI), clay, void. |
| Fluid pipes | Wood, cobblestone, stone, quartz, gold, sandstone, iron, clay, void, with upstream capacities and transfer rates. |
| Kinesis pipes | Wood, cobblestone, stone, quartz, gold, sandstone, with upstream power limits. |
| Client | Upstream engine model (base plate, stroking piston plate and chamber, trunk textured and lit by heat stage), pipe contents, tank, mining tube and quarry renderers; distiller (bobbing power indicators coloured by power use, three fluid tanks) and heat exchanger (input and output tanks, coolant and heatant flowing through the row while it runs) renderers on upstream's block models; Stirling, Combustion, distiller, diamond pipe and filler screens (upstream `GuiFiller`: the pattern and parameter slots with upstream's pop-up rings of choices, patterns dragged in from the side panel, excavate and invert buttons, the lock icon, and the progress ledger counting blocks left to break and place); the filler's blocks flying from it to where they land, its robot hovering over the blocks it breaks and firing upstream's red-to-blue power lasers at them, and the write stripes around a land mark area it took (upstream `RenderFiller` and `RenderSnapshotBuilder`); the architect table screen (upstream `GuiArchitectTable`: the snapshot slots, the progress arrow and the name field), the glow over every block it records, fading over 50 ticks (upstream `RenderArchitectTables`), and the read stripes around a land mark area it took (upstream `RenderArchitectTable`); the builder screen (upstream `GuiBuilder`: the snapshot and resource slots, the four tank gauges and what the blueprint still needs), its write stripes around the area it builds, the directional path laser along its path, and its flying blocks and robot as the filler's (upstream `RenderBuilder`); recorded templates and blueprints show their name and author; upstream textures. Marker lasers follow upstream's `LaserRenderer_BC8` and `BuildCraftLaserManager` (laser textures, row layout, scales and colours): land mark boxes, signal lasers 64 blocks along a powered land mark's free axes, path lasers along each step, and, while a marker connector is held, the connections it could make, in the default colour for the one the player is looking along. Volume boxes are drawn in the connected land mark laser, in the signal laser while the player edits them, and in the striped laser of a machine that holds them (upstream `RenderVolumeBoxes`). As upstream's `WidgetFluidTank`, a container carried onto a distiller or combustion engine gauge empties into that tank or fills from it (ice melts into the engine's coolant), and shift-clicked containers go to the distiller's input or try the engine's fuel, coolant and residue tanks in turn. |

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

## Not ported

- **Core:** volume box addons (the filler planner and its addon slots), paintbrush, list, map location, guide book, decorated blocks, creative engine, and the
  `markers` and `path_markers` advancements.
- **Energy:** the oil spring's tracking of how much of its well each player has pumped, which
  unlocked upstream's `black_gold` advancement.
- **Factory:** auto workbench, flood gate, chute, water gel.
- **Builders:** the filler's gate actions and control modes (so its pattern is never locked and it
  never pauses), the filler planner addon, upstream's in-GUI help overlays, the electronic library,
  the replacer, the snapshot preview in recorded items' tooltips, the architect table's `architect`
  advancement and the builder's `paving_the_way` advancement, which upstream never unlocked because its
  path index stops one short of the condition. The rules do not yet cover beehive and bee nest
  occupants, composter levels or a triggered dropper (upstream only ignored `triggered` on dispensers).
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
`FabricGameTests`): the MJ/energy ratio, the viscosity scale, gases rising in the world, every engine powering a receiver, a
redstone engine driving a wooden pipe that delivers items through stone and gold pipes, tank
columns, the trunk stage following heat, redstone engines warming to red without overheating, pump draining (including a finite irregular pool and an infinite 2×2 pool), mining well and quarry digging, land marks
connecting into a box (saved, synced to clients, shrinking as marks break, showing signals when powered), path marks forming
paths and loops that save, reverse and split, the marker connector joining marks, volume boxes made once per spot and surviving saving and syncing with their locks, the connector grabbing, stretching, confirming, cancelling and removing boxes and pausing edits of absent players, locks holding until their block is gone, marks turning with the wrench and
breaking without support, a quarry taking a land mark box from a mark behind it or from the box's edge, filler patterns (upstream counts for the simple shapes, and every pattern at five sizes with each value of each parameter), the pattern statement keeping matching parameters and saving, the filler taking and locking a volume box until it breaks (dropping its resources) or taking a land mark area, its menu accepting only block items, its buttons, inversion and saving, building a box of stone after clearing its centre, clearing a box only once excavation is on, and keeping to each tick's power allowance while counting queued blocks as left to do, the architect table locking a volume box, recording a template (every scanned block sent to clients, the same area hashing alike) and a blueprint (block and entity costs, a door waiting for its lower half, surviving a save), taking a land mark area, refusing to record without an area, its menu slots, sides, name message and saving, snapshot storage, every builder rule naming 1.19.2 blocks, properties, entities and items that exist with upstream's costs and dependencies for flattened blocks, recorded blocks building back with their state and contents, and the template, blueprint and table recipes, the builder building a template turned a quarter round and digging out what the template leaves empty, building a blueprint's blocks turned and with their contents only once every item is in hand while listing (and syncing) what is still needed, placing a water source from its tanks, removing a captured entity the blueprint lacks and spawning its armour stand turned, following a path of path marks (skipping, as upstream, the steps it cannot power at once), and its menu slots, tanks, wrench turning, saving and drops, void pipes, fluid pipes filling a tank,
kinesis pipes carrying engine power, oil well rates and determinism over 65,536 seeded chunks
with the placed feature in the Overworld's lakes step, the oil biomes' thirtyfold well bonus, the
oil desert and ocean replacing deserts and oceans where upstream's noise says (recomputed
independently over a 65,536-block square) with their parents' climate, tags and surface, the refinery chain from oil to light fuel,
the combustion engine burning oil into residue, the distiller splitting oil and refunding blocked
progress, heat exchanger rows
forming, rotating and breaking (dropping shards), the water spring refilling, a heat exchanger heating oil with lava, fragile
fluid shards splitting fluid, dropping from every broken fluid holder and emptying into a tank,
containers clicked and shift-clicked on GUI tanks in survival and creative, and recipes and tags. Loader-specific tests check the Forge
capabilities (`ForgeAdapterGameTests`) and the Team Reborn Energy and Transfer API storages
(`FabricAdapterGameTests`), including transaction rollback and draining shards. `EmcGameTests` audits EMC coverage and,
with `-PwithProjectE` or `-PwithItemAlchemy`, the live values.
