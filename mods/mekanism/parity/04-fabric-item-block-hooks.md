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
