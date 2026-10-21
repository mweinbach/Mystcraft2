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
OBJ and custom models. The shared GameTests (core machines and loader interop) pass on both loaders.

- Energy, items and fluids cross block boundaries as Team Reborn Energy (1 E per FE, so Mekanism's FE rate applies)
  and Transfer API storages, in both directions: every block entity the port registers is exposed through
  `EnergyStorage.SIDED`, `ItemStorage.SIDED` and `FluidStorage.SIDED`, and Forge capabilities asked of other block
  entities fall back to those lookups. Item stacks without Mekanism's own fluid or energy capability answer through
  `FluidStorage.ITEM` and `EnergyStorage.ITEM`, so vanilla buckets and other mods' tanks and batteries work in
  Mekanism's slots. The reverse is exposed too: Mekanism's energy items (the energy tablet, energy cubes and anything
  else upstream gives an item energy capability) are `EnergyStorage.ITEM` storages through `ContainerItemContext`, and
  its fluid items (fluid tanks and the other item fluid handlers) are `FluidStorage.ITEM` storages, at 81 droplets per
  mB. Chemicals stay on Mekanism's own API.
- `scripts/generate_fabric_tags.py` writes the Fabric tag bridges: the `forge:` tags Forge itself defines (from
  `scripts/forge-tags.json`, Forge 43.5.0's data), each Mekanism `forge:` tag with its `c:` equivalent as an optional
  entry, and `c:` tags holding Mekanism's own items. `--check` fails when they are stale.
- Tags of Mekanism's own registries (gases, infuse types, pigments, slurries) load from Forge's
  `tags/<namespace>/<path>` directories; those registries are synced and saved as Forge's are.

## Fabric: not written yet

- Biome modifiers for ore and salt generation: Mekanism ores do not generate on Fabric. Chunk loading tickets.
- Fluid render handlers for Mekanism's fluids.
- Forge events that are not fired yet: player ticks, living entity events (hurt, fall, attack, jump), break speed,
  item crafted, chunk data load and save, `AddReloadListenerEvent`, cancelling `EntityJoinLevelEvent`, client level
  events, recipes updated, GUI overlay rendering, block highlight, living entity rendering, fog, mouse scrolling and
  `EntityRenderersEvent.AddLayers`. Features that rely on them (MekaSuit and jetpack ticking, radiation effects on
  players, free runners, scroll-to-change modes, HUD overlays, armor layers) do not work yet.
- Mixins that call the Forge item and block hooks the shared code overrides. The hooks exist as interface defaults,
  but vanilla code does not call them yet:
  - Item: `onItemUseFirst`, `onBlockStartBreak`, `getBurnTime` (Fabric's fuel registry), elytra flight, `onArmorTick`,
    `damageItem`, `isEnderMask`, `makesPiglinsNeutral`, `canWalkOnPowderedSnow`, `getEquipmentSlot`,
    `getDefaultTooltipHideFlags`, `isNotReplaceableByPickAction`, `doesSneakBypassUse`, and enchantment levels read
    through `EnchantmentHelper`.
  - Block: `onDestroyedByPlayer`, `getExplosionResistance`, `getLightEmission`, `canConnectRedstone`,
    `onNeighborChange`, `getExpDrop`, `onBlockExploded`, `getCloneItemStack`, `isPortalFrame`, `rotate`,
    `getSoundType`, `shouldDisplayFluidOverlay`.
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
