## Fabric: what runs

The Fabric JAR loads on a dedicated server and in a development client. Its entry points construct Mekanism and fire
Forge's mod-bus events in Forge's order; configs, the network channel and menus work, and entity spawn packets are
wired but not yet exercised. In the
client the enrichment chamber, crusher, energized smelter and metallurgic infuser process their recipes, powered by
creative energy cubes directly and through universal cables, and energy cubes and transmitters render with their
OBJ and custom models. The basic smelting factory's screen opened with its three slots, energy bar and sorting tab.
The infusion bar, gas bar, sawmill's second output row and the wider ultimate factory screen were not opened.
Osmium, tin, lead, uranium, fluorite and salt generate in biomes tagged
`#minecraft:is_overworld`. Fabric reads the shared `forge/biome_modifier` files and adds the placed features those
modifiers contribute, at the same decoration step. Vein size, height, attempts per chunk and the generate toggles are
the shared placement modifiers. Mekanism fluids render from their fluid-type client extensions: each still and flowing
fluid is registered with Fabric's fluid render handler (still texture, flowing texture, overlay and tint) and on the
translucent block render layer, and those textures are stitched into the block atlas that bucket models and fluid
gauges sample. `shouldDisplayFluidOverlay` is called for each Mekanism block's default state, and blocks that return
true (structural glass) are marked transparent so the overlay sprite is drawn against them. Client ticks, the HUD,
fog, living-entity and arm rendering, armor layers, block highlighting and in-game key and scroll input are wired
(see below). An anchor upgrade keeps that machine's chunk loaded, and the same tickets come back when the level loads.
Factories for all nine recipe types and four tiers place their tiles. A basic smelting factory smelts one item in
each of its three slots in the same operation, auto-sort spreads one input stack across those slots, and an advanced
tier installer replaces the block while keeping the items, stored energy and auto-sort flag. The chemical infuser,
oxidizer, dissolution chamber, washer, crystallizer, purification chamber, chemical injection chamber, rotary
condensentrator, pressurized reaction chamber, isotopic centrifuge and solar neutron activator each run one recipe.
Gases and slurries move through the Forge capabilities and the Fabric lookups. The chemical infuser screen
opened in the development client. The nutritional liquifier turns one dried kelp into 50 mB of nutritional paste,
the pigment extractor pulls red pigment from red wool, the pigment mixer combines red and yellow into orange, the
painting machine paints dye base into red dye, and the antiprotonic nucleosynthesizer turns a tin ingot and
antimatter into an iron ingot. Pigments move through the pigment lookup. The pigment extractor screen opened in
the development client; the other chemical machine screens were not opened. A dynamic tank shell of
casing, structural glass and one valve forms at 3×3×3, rejects a missing block or a 2×2×2, and moves fluid through
the valve's loader fluid API. Breaking the blocks and loading their saved cache brings the fluid and the container
edit mode back. The master's render box is that structure. In the development client that formed tank draws the fluid
it is holding through the structural glass. An induction matrix of induction casing, one port, one basic cell and one
basic provider forms at 3×3×4. A missing block, a 2×2×2 shell and stone inside the shell do not. The port moves
energy through Forge Energy and Team Reborn Energy at the provider's transfer rate, and only in the direction its
input or output mode allows. A casing block does not accept energy. An energy tablet in the port's charge slot is
charged from the matrix, and cobblestone is refused. Breaking the blocks and loading their saved data brings the
cell's energy back, and the reloaded port can output it. The master's render box is that structure. Upstream 10.3.9
has no separate induction-matrix renderer, so the formed structure is the casing and port models. The development
client draws that formed casing and port. A formed industrial turbine in that client draws its rotor and blades
through the structural glass. A thermoelectric boiler (`ThermoelectricBoilerGameTests`) forms at 3×5×3 with one
superheating element under a disperser plane and one valve. It rejects a missing block, a 2×2×2, a shell with no
disperser, and a superheating element above the disperser. Water enters through the valve's loader fluid API.
Superheated sodium enters through the gas handler, and the steam produced each tick is the boil rate that temperature,
heat capacity and the superheating element allow. Heat inserted through the casing's heat handler — Forge's heat
capability, and on Fabric the `mekanism:heat_handler` lookup — boils water with no coolant in the tanks. Steam leaves
through the gas handler when the valve is set to output steam; an input valve gives none up. Saved water, steam,
heated and cooled sodium, the temperature and the valve mode come back after the blocks are removed and their NBT is
loaded. The master's render box is the structure. In the development client a formed boiler shows its casing
and valves in input, steam-output and coolant-output modes, and the boiler screen opens. The water and steam
bodies use the same multiblock renderer as the dynamic tank. Boiler casing is opaque, so those bodies are not
visible from outside the shell. In the development client `/mek build fission` built an 18×18×18 reactor.
Reactor glass shows the casing frame and a checkerboard of fuel assemblies and control rods. The reactor was not
fueled, so the burn glow and the coolant body were not drawn, and the reactor screen was not opened. In the
development client `/mek build sps` built a 7×7×7 shell. Structural glass shows the casing frame and the empty
corner cells of each face. The build has no port and no coil, so the orb and the coil bolts were not drawn. The shared
GameTests (core machines, factories, chemical machines, pigment machines, loader interop, chemical lookups, ore generation, item and
block hooks, fluid placement, chunk loading, render bounds, the player and living events, a Generators registry
smoke test, the simple generators, the dynamic tank, the induction matrix, the thermoelectric boiler, the
supercritical phase shifter, the industrial turbine, the fission reactor, Tools registration, crafting, tool actions
and shield blocking, and Additions
registration, crafting, block behaviour, baby mobs and balloons) pass on both loaders.

- Energy, items and fluids cross block boundaries as Team Reborn Energy (1 E per FE, so Mekanism's FE rate applies)
  and Transfer API storages, in both directions: every block entity the port registers is exposed through
  `EnergyStorage.SIDED`, `ItemStorage.SIDED` and `FluidStorage.SIDED`, and Forge capabilities asked of other block
  entities fall back to those lookups. Item stacks without Mekanism's own fluid or energy capability answer through
  `FluidStorage.ITEM` and `EnergyStorage.ITEM`, so vanilla buckets and other mods' tanks and batteries work in
  Mekanism's slots. The reverse is exposed too: Mekanism's energy items (the energy tablet, energy cubes and anything
  else upstream gives an item energy capability) are `EnergyStorage.ITEM` storages through `ContainerItemContext`, and
  its fluid items (fluid tanks and the other item fluid handlers) are `FluidStorage.ITEM` storages, at 81 droplets per
  mB. Gas, infusion, pigment, slurry and heat are published on Fabric API lookups under `mekanism:*` ids
  (`mekanism:gas_handler`, `mekanism:infusion_handler`, `mekanism:pigment_handler`, `mekanism:slurry_handler`,
  `mekanism:heat_handler`); the ids, context classes and the `ports.mekanism.fabric.api.MekanismChemicalLookups`
  fields are listed in [README.md](../README.md). The handler is the port's capability for that side or stack, in
  Mekanism chemical units. Mekanism registers its own blocks and items. Other mods can register on the same lookups.
  A Forge chemical or heat capability asked of a foreign block falls back to the matching lookup, which is how a
  pressurized tube pulls gas another mod registered. Mekanism's own block entities are skipped, so the fallback does
  not call their lookup providers. Forge `conditions` on a data file are applied while JSON is prepared only when
  every condition is a Forge type this port can read and none is `forge:tag_empty` (tags are not bound yet, and an
  empty condition context would treat every tag as empty). Unknown types and tag conditions stay in the file for
  that mod's own loader conditions.
- `scripts/generate_fabric_tags.py` writes the Fabric tag bridges: the `forge:` tags Forge itself defines (from
  `scripts/forge-tags.json`, Forge 43.5.0's data), each Mekanism `forge:` tag with its `c:` equivalent as an optional
  entry, and `c:` tags holding Mekanism's own items. `--check` fails when they are stale.
- Tags of Mekanism's own registries (gases, infuse types, pigments, slurries) load from Forge's
  `tags/<namespace>/<path>` directories; those registries are synced and saved as Forge's are.
- Chunk tickets. The digital miner, dimensional stabilizer, teleporter and quantum entangloporter use
  `TileComponentChunkLoader`, and any machine with an anchor upgrade does too. Fabric saves those tickets with the
  level and, when the level loads, asks Mekanism to validate them before the chunks are loaded again. The ticket is
  vanilla's `/forceload` level, so the chunk, its entities and its block entities tick.
- Block entity renderers are culled with `getRenderBoundingBox`. The default is Forge's collision shape, with the
  chest, enchanting table, beacon and structure block cases. Multiblocks, the digital miner, the dimensional
  stabilizer, the teleporter, transmitters, the pigment mixer, the seismic vibrator and the industrial alarm override
  that box, so they keep drawing while any of it is in the frustum. `Entity.onRemovedFromWorld` runs when an entity
  leaves the level's tracking lists. A following Robit uses it to hold its chunk for a second after it is unloaded.
