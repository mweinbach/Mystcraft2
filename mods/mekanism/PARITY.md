# Mekanism loader parity and known gaps

Gameplay, registries, recipes, assets and data are shared in `src/main` and are upstream Mekanism 10.3.9. Forge
runs them on Forge itself. Fabric runs them on the port's Forge API mirror (`src/fabric/java/ports/mekanism/lib`),
injected interfaces on vanilla classes and the mixins in `ports.mekanism.fabric.mixin`. This file lists where the
Fabric variant does not yet match Forge and where the port differs from upstream.

## Not ported from upstream

The JEI plugin, CraftTweaker, ComputerCraft, OpenComputers 2, Curios, Wildfire's Gender Mod, The One Probe, Jade,
WTHIT, ProjectE, Flux Networks and Recipe Stages integrations, and the Defense module. See [UPSTREAM.md](UPSTREAM.md).

## Fabric: what runs

The Fabric JAR loads on a dedicated server and in a development client. Its entry points construct Mekanism and fire
Forge's mod-bus events in Forge's order; configs, the network channel and menus work, and entity spawn packets are
wired but not yet exercised. In the
client the enrichment chamber, crusher, energized smelter and metallurgic infuser process their recipes, powered by
creative energy cubes directly and through universal cables, and energy cubes and transmitters render with their
OBJ and custom models. Osmium, tin, lead, uranium, fluorite and salt generate in biomes tagged
`#minecraft:is_overworld`. Fabric reads the shared `forge/biome_modifier` files and adds the placed features those
modifiers contribute, at the same decoration step. Vein size, height, attempts per chunk and the generate toggles are
the shared placement modifiers. The shared GameTests (core machines, loader interop, chemical lookups, ore
generation, and the item and block hooks) pass on both loaders.

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
  A Forge chemical or heat capability asked of a foreign block does not fall back to them.
- `scripts/generate_fabric_tags.py` writes the Fabric tag bridges: the `forge:` tags Forge itself defines (from
  `scripts/forge-tags.json`, Forge 43.5.0's data), each Mekanism `forge:` tag with its `c:` equivalent as an optional
  entry, and `c:` tags holding Mekanism's own items. `--check` fails when they are stale.
- Tags of Mekanism's own registries (gases, infuse types, pigments, slurries) load from Forge's
  `tags/<namespace>/<path>` directories; those registries are synced and saved as Forge's are.

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

## Fabric: not written yet

- Chunk loading tickets.
- Fluid render handlers for Mekanism's fluids. `shouldDisplayFluidOverlay` is part of Forge's fluid-sprite rewrite and
  is not called.
- Forge events that are not fired yet: player ticks, living entity events (hurt, fall, attack, jump), break speed,
  item crafted, chunk data load and save, `AddReloadListenerEvent`, cancelling `EntityJoinLevelEvent`, client level
  events, recipes updated, GUI overlay rendering, block highlight, living entity rendering, fog, mouse scrolling and
  `EntityRenderersEvent.AddLayers`. Features that rely on them (MekaSuit and jetpack ticking, radiation effects on
  players, free runners' fall behavior, scroll-to-change modes, HUD overlays, armor layers) do not work yet.
  `onArmorTick` does run; the modules that need a player tick still do not.
- `BlockEntity.getRenderBoundingBox` and `Entity.onRemovedFromWorld`.
- Key mappings bound to more than one key and custom ingredient types.
- Not yet checked in the client: chemical machines, multiblocks, QIO, the digital miner, Robit, gear and the
  energy cube's core renderer.

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
- Forced chunks do not random-tick.
- The ender dragon's part entities are not exposed through Forge's `PartEntity` API.
- Key mapping modifiers are not saved.
- HUD overlays are drawn in registration order rather than Forge's overlay order.
- OBJ models do not support emissive faces or per-vertex colors; fluid container models ignore fluid luminosity.
- Data generation runs on Forge only; both loaders ship the checked-in generated data.
