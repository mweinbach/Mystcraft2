# Mekanism loader parity and known gaps

Gameplay, registries, recipes, assets and data are shared in `src/main` and are upstream Mekanism 10.3.9. Forge
runs them on Forge itself. Fabric runs them on the port's Forge API mirror (`src/fabric/java/ports/mekanism/lib`),
injected interfaces on vanilla classes and the mixins in `ports.mekanism.fabric.mixin`. This file lists where the
Fabric variant does not yet match Forge and where the port differs from upstream.

## Modules

Generators, Tools and Additions are separate mod ids built from this port. Each loader emits
`mekanismgenerators`, `mekanismtools` and `mekanismadditions` jars that depend on the core jar.

- **Generators** is imported: registries, config, the packet handler, assets and the checked-in datagen output.
  Both loaders load the jar and register its blocks, items, fluids and block entities. Heat, solar, advanced solar,
  gas-burning, bio and wind generators produce energy (`SimpleGeneratorGameTests`): solar output in daylight, the
  gas-burning generator burning hydrogen, and energy leaving through Forge Energy and Team Reborn Energy. The wind
  rotor's render box is the extended box the block entity reports, and on Fabric the wind generator item is drawn by
  `BuiltinItemRendererRegistry`. Turbine, fission and fusion gameplay is not verified.
- **Tools** is imported: paxels, tools, armor and shields for bronze, lapis lazuli, osmium, refined glowstone,
  refined obsidian and steel, plus vanilla paxels. Both loaders register them
  (`ToolsModuleGameTests`), craft the bronze gear and the paxel (including averaged durability), and run paxel
  strip and flatten. Paxels do not till, matching upstream. Fabric harvest uses the numeric tier level. The
  `needs_*_tool` tags are empty, and the tier-sorting registry only records the order. Hoe till on Fabric is vanilla's tillable map, gated by
  `canPerformAction`. `c:` tool and armor tags ship in the tools jar alongside the `forge:` tags. Shield items use
  the existing item-renderer hook, and blocking with them consumes durability on both loaders. The refined-glowstone armor model is not drawn fullbright: nothing calls
  `getGenericArmorModel`. Naturally spawned mobs do not wear the armor, because the living-spawn event is not
  posted. The Wildfire's Gender capability is not attached.
- **Additions** loads as an empty module. Its items, blocks and entities are not imported.
- Generator `forge:` fluid tags (bioethanol, deuterium, tritium, fusion fuel) ship in the Generators jar. They do
  not yet have Fabric `c:` bridges.

## Not ported from upstream

The JEI plugin, CraftTweaker, ComputerCraft, OpenComputers 2, Curios, Wildfire's Gender Mod, The One Probe, Jade,
WTHIT, ProjectE, Flux Networks and Recipe Stages integrations, and the Defense module. See [UPSTREAM.md](UPSTREAM.md).

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
tier installer replaces the block while keeping the items, stored energy and auto-sort flag. A dynamic tank shell of
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
client draws that formed casing and port. The shared GameTests (core machines, factories, loader interop, chemical
lookups, ore generation, the item and block hooks, fluid placement, chunk loading, render bounds, the player and
living events, a Generators registry smoke test, the simple generators, the dynamic tank, the induction matrix, and
Tools registration, crafting, tool actions and shield blocking) pass on both loaders.

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
  fields are listed in [README.md](README.md). The handler is the port's capability for that side or stack, in
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

## Fabric: item and block hooks vanilla calls

Re-equip animation and block-break reset were already answered through Fabric's item API. These vanilla call sites now
reach the same hooks Forge's 43.5.0 patches call. Right-click and left-click **events** stay unwired; only the item
and block methods are.

- Items: `onItemUseFirst` and `doesSneakBypassUse` (server use and the client prediction), `onBlockStartBreak`,
  `onDestroyedByPlayer` and break experience from `getExpDrop`, `getBurnTime` (then Fabric's fuel registry), elytra
  flight (`canElytraFly` / `elytraFlightTick`, including vanilla elytra), `onArmorTick`, `damageItem`, `isEnderMask`,
  `makesPiglinsNeutral`, `canWalkOnPowderedSnow`, `getEquipmentSlot`, `getDefaultTooltipHideFlags`,
  `isNotReplaceableByPickAction`, `onDestroyed` for item entities, and enchantment levels through
  `EnchantmentHelper` (including the enchanting table's `canApplyAtEnchantingTable` and an anvil book the item
  rejects).
- Blocks: `getExplosionResistance` (block and fluid), `getLightEmission` (block lookups, chunk light lists, proto
  chunks, saved chunks and `Level.setBlock`), `canConnectRedstone` (dust, including the Forge default for wire,
  repeaters, observers and signal sources), `onNeighborChange` / `getWeakChanges` for comparator updates,
  `onBlockExploded` and `canDropFromExplosion`, `getCloneItemStack` when the client pick-blocks, `isPortalFrame`,
  and `getSoundType` for steps, fall landing, block placement and the client break sound.
- `Block.rotate(state, Rotation)` is still the method structures call. The four-argument Forge overload exists on the
  block; nothing in vanilla 1.19.2 structure placement calls it.

## Fabric: client events

These fire at the same points Forge 43.5.0 does. Fabric API is used where it has the callback; the rest are mixins.

- Client tick start and end, and the client level tick, from Fabric's client tick events.
- Render tick start immediately before `GameRenderer.render`, and render tick end after the toast pass. The radiation
  overlay and the fading item-mode line are drawn on the render tick end.
- HUD overlays registered above the hotbar and above the armor level are drawn at those anchors. The crosshair overlay
  pre event can cancel the crosshair. Vanilla still draws its own HUD parts; only the crosshair posts
  `RenderGuiOverlayEvent`, because that is the vanilla overlay Mekanism cancels.
- Fog color, then fog distance, after vanilla has computed each. A canceled distance event replaces the near plane, far
  plane and shape, which is how the MekaSuit's vision enhancement pushes fog out.
- Level stage after translucent blocks, from Fabric's world render event, and after particles (both the normal and the
  fabulous path). Sky, solid, cutout, tripwire and weather stages are not posted.
- Living entity render pre and post. A canceled pre skips the entity and does not post the matching post event.
  First-person arms post `RenderArmEvent` and skip vanilla's arm when it is canceled, which is where the MekaSuit arm
  is drawn.
- `EntityRenderersEvent.AddLayers` after the entity render dispatcher rebuilds its renderers. That attaches the
  MekaSuit armor layer and the elytra layer.
- Block highlight before vanilla's outline, from Fabric's before-block-outline event. Canceling a block highlight
  skips the outline. The configurator side overlay and custom selection boxes use this event. An entity under the
  crosshair posts the entity highlight from the same callback; vanilla still draws no entity outline.
- Mouse wheel in game with no screen open, after a whole notch has accumulated and before the hotbar moves. On macOS a
  horizontal wheel with no vertical movement counts as vertical. Canceling the event consumes the notch. Shift-scroll
  mode changes use this.
- A key press is delivered to every mapping bound to that key, so an unmodified binding and a modified one both see it.
  The mapping still has to be in an active conflict context with its modifier held before it counts as down.

Checked on a Fabric development client in creative and then survival: the MekaSuit module list, jetpack mode,
configurator mode and compass draw, and the survival energy bar draws above the health row. The radiation tint reads
the client dose. That dose is synced from the player tick. The player tick was unwired when the client was checked,
so the tint was not seen. The MekaSuit armor layer is attached when the renderers reload.

## Fabric: player and living events

These fire from the same vanilla methods Forge 43.5.0 patches. Listeners on the port's bus see them, so the server
applies jetpack thrust, scuba air, free-runner fall absorption, radiation, step assist and the MekaSuit's break-speed
modules. `onArmorTick` already ran; the modules that only needed a player tick now run with it.

- Player tick, start and end. The server end tick is what drives the jetpack, scuba tank, radiation and step assist.
  Step assist and the hydrostatic repulsor write Forge's step-height and swim-speed attributes. Living entities have
  those attributes, players also have reach distance, collision steps up by the step-height attribute, and swimming
  scales by swim speed. The client publishes the ascend key from `ClientTickEvent` start, which the client tick
  wiring posts, so a normal-mode jetpack follows the jump key.
- Living tick, attack, hurt, damage, fall, jump and death. A non-player posts living attack from `LivingEntity.hurt`.
  A player posts it from `Player.hurt` and skips the living-entity copy, matching Forge. Death is posted from
  `LivingEntity.die`, `Player.die` and `ServerPlayer.die`, so an uncancelled player death posts three times.
  `ServerPlayer` posts after its death game event. Living damage is posted from the health change inside
  `actuallyHurt`, and the adjusted amount is what combat tracking, food exhaustion, damage statistics and the
  following absorption update use. Cancelling the event skips that bookkeeping. The attacker's absorbed-damage
  statistic stays the absorption applied before the event. When armor and absorption already reduced the damage to
  zero, vanilla never changes health, so that zero-damage event is not posted.
- Break speed, including the position `getDestroyProgress` is measuring. Direct `getDestroySpeed` calls pass an empty
  position. A cancelled break speed returns -1, which stops the break. This is the hook the Meka-Tool, atomic
  disassembler and MekaSuit gyroscopic stabilization use.
- Item crafted, after `ItemStack.onCraftedBy` in the crafting result slot, and the crafting player is set around the
  remainder lookup. Bin insert recipes consume the rest of the grid through this event.
- Entity join, cancellable, from `PersistentEntitySectionManager.addEntity` before the entity is added. A cardboard
  box uses it to swallow dropped items and experience. Players are included once, after a duplicate UUID is removed.
  Forge posts a player's join slightly earlier, from `ServerLevel.addPlayer`, and then skips this second post.
- Entity leave, from Fabric's server entity unload, after the entity stops being tracked. Forge also posts it on the
  client; the client copy is unwired.

## Fabric: not written yet

- Forge events that are not fired yet: chunk data load and save, `AddReloadListenerEvent`, client level load and
  unload, and recipes updated. Entity leave is posted on the server; the client copy Forge also posts is unwired.
- One key mapping bound to more than one key, and custom ingredient types. Modifiers on a mapping are not saved.
- Not yet checked in the client: chemical machines, the other multiblocks (evaporation, boiler and SPS), QIO, the digital miner, Robit, gear,
  the energy cube's core renderer, the simple generators' block-entity renderers, and the turbine, fission and fusion
  renderers. Factory screens other than the basic smelting factory were not opened. The wind generator item is
  registered with Fabric's builtin item renderer; that registration has not been watched in a client. A formed
  dynamic tank holding water was checked: the fluid body renders inside the structural glass. A formed induction
  matrix was checked: the casing and port models draw. Upstream 10.3.9 ships no separate matrix renderer.

## Fabric: deliberate limits of the Forge API mirror

- Capabilities attached to item stacks, entities and block entities are kept in a lazily created store per object,
  saved under Forge's `ForgeCaps` tag and invalidated and revived where Forge does it.
- Block entity model data is the block entity's Fabric render attachment; `requestModelDataUpdate` does nothing.
- Permission checks use each node's default value; no permission mod is consulted.
- Creative tabs past vanilla's twelve rely on Fabric API's tab pages.
- `MobEffectInstance.isCurativeItem` only accepts milk buckets, Forge's default.
- `Item.Properties.setNoRepair` does nothing; Mekanism only uses it on items without durability.
- `Entity.getMaxHeightFluidType`, `isEyeInFluidType` and `isInFluidType` know water and lava only.
- `Entity.changeDimension(ServerLevel, ITeleporter)` moves the entity with Fabric's dimension API. The reposition
  function handed to `ITeleporter.placeEntity` ignores its portal flag, and `playTeleportSound` is not consulted.
- Tooltips drawn through `Screen.renderComponentTooltip` ignore the font and item stack arguments.
- Forced chunks do not random-tick. The ticket level matches vanilla `/forceload`: entities and block entities tick, and
  random ticks still happen only near a player. The dimensional stabilizer asks Forge for fully ticking chunks; on
  Fabric that flag is saved so validation can tell it from an anchor upgrade, and it does not enable random ticks.
- The ender dragon's part entities are not exposed through Forge's `PartEntity` API.
- Key mapping modifiers are not saved.
- Modded HUD overlays anchored inside vanilla's health, armor, food and air group are drawn after that whole group.
  Vanilla draws the group in one call, so they cannot sit between armor and food the way Forge's separate overlays do.
  Their screen position still comes from the same left and right bar heights.
- OBJ models do not support emissive faces or per-vertex colors; fluid container models ignore fluid luminosity.
- Fabric's fluid renderer ignores tint alpha. Mekanism's fluid tints are opaque, and steam's transparency is in its
  texture. `shouldDisplayFluidOverlay` is read from the default block state when the client starts, not again per face;
  structural glass always requests the overlay, so the fluids drawn against it match Forge.
- Data generation runs on Forge only; both loaders ship the checked-in generated data.
