# Mekanism loader parity and known gaps

Gameplay, registries, recipes, assets and data are shared in `src/main` and are upstream Mekanism 10.3.9. Forge
runs them on Forge itself. Fabric runs them on the port's Forge API mirror (`src/fabric/java/ports/mekanism/lib`),
injected interfaces on vanilla classes and the mixins in `ports.mekanism.fabric.mixin`. This file lists where the
Fabric variant does not yet match Forge and where the port differs from upstream.

## Not ported from upstream

The JEI plugin, CraftTweaker, ComputerCraft, OpenComputers 2, Curios, Wildfire's Gender Mod, The One Probe, Jade,
WTHIT, ProjectE, Flux Networks and Recipe Stages integrations, and the Defense module. See [UPSTREAM.md](UPSTREAM.md).

## Fabric: not written yet

These block the Fabric JAR from loading or from behaving like Forge:

- The entry points `ports.mekanism.fabric.FabricMod` and `FabricClientMod`, the `Platform` backend, and the
  lifecycle driver that fires the mod-bus events and registers content in Forge's order (fluid types, fluids,
  blocks, items, then everything else).
- Config file loading, the network channel, menu opening and entity spawn packets.
- Team Reborn Energy, Transfer API (items and fluids) and `BlockApiLookup` bridges for the capability system, and
  `c:` tag equivalents of the `forge:` tags Mekanism's data uses.
- Biome modifiers for ore and salt generation, chunk loading tickets and permission nodes.
- Fluid rendering registration, the Forge bucket model, built-in item renderers and the Fabric renderer path for
  Mekanism's baked models.
- Mixins that call the Forge item and block hooks the shared code overrides. The hooks exist as interface defaults,
  but vanilla code does not call them yet:
  - Item: `onItemUseFirst`, `onBlockStartBreak`, `getBurnTime` (Fabric's fuel registry), elytra flight, `onArmorTick`,
    `damageItem`, `isEnderMask`, `makesPiglinsNeutral`, `canWalkOnPowderedSnow`, `getEquipmentSlot`,
    `getMaxStackSize(ItemStack)`, `getDefaultTooltipHideFlags`, `isNotReplaceableByPickAction`, `doesSneakBypassUse`,
    and enchantment levels read through `EnchantmentHelper`.
  - Block: `onDestroyedByPlayer`, `getExplosionResistance`, `getLightEmission`, `canConnectRedstone`,
    `onNeighborChange`, `getExpDrop`, `onBlockExploded`, `getCloneItemStack`, `isPortalFrame`, `rotate`,
    `getSoundType`, `shouldDisplayFluidOverlay`.
  - Block entity update packets (`onDataPacket`, `handleUpdateTag`), `getRenderBoundingBox` and
    `Entity.onRemovedFromWorld`.
  - Key mappings bound to more than one key, block model geometry loaders and custom ingredient types.
- Mekanism GameTests on both loaders and a loader-parity check script.
- EMC values for ProjectE and Item Alchemy.

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
