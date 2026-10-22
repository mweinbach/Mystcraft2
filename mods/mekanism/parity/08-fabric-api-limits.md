## Fabric: deliberate limits of the Forge API mirror

- Capabilities attached to item stacks, entities and block entities are kept in a lazily created store per object,
  saved under Forge's `ForgeCaps` tag and invalidated and revived where Forge does it.
- Block entity model data is the block entity's Fabric render attachment; `requestModelDataUpdate` does nothing.
- Permission checks use each node's default value; no permission mod is consulted.
- Creative tabs past vanilla's twelve rely on Fabric API's tab pages.
- `MobEffectInstance.isCurativeItem` only accepts milk buckets, Forge's default.
- `Item.Properties.setNoRepair` does nothing; Mekanism only uses it on items without durability.
- `Entity.getMaxHeightFluidType`, `isEyeInFluidType` and `isInFluidType` know water and lava only.
- `Entity.changeDimension(ServerLevel, ITeleporter)` asks `getPortalInfo`, then `placeEntity`. The reposition
  function moves the entity with `FabricDimensions.teleport` and ignores its portal flag: Fabric's dimension API
  does not build a nether portal. `playTeleportSound` is not consulted, so a player can still hear the vanilla
  portal travel sound. Mekanism plays the enderman teleport sound itself.
- Tooltips drawn through `Screen.renderComponentTooltip` ignore the font and item stack arguments.
- Forced chunks do not random-tick. The ticket level matches vanilla `/forceload`: entities and block entities tick, and
  random ticks still happen only near a player. The dimensional stabilizer asks Forge for fully ticking chunks; on
  Fabric that flag is saved so validation can tell it from an anchor upgrade, and it does not enable random ticks.
- The ender dragon's part entities are not exposed through Forge's `PartEntity` API.
- Key mapping modifiers are not saved.
- Modded HUD overlays anchored inside vanilla's health, armor, food and air group are drawn after that whole group.
  Vanilla draws the group in one call, so they cannot sit between armor and food the way Forge's separate overlays do.
  Their screen position still comes from the same left and right bar heights.
- Block-model faces with Forge's `emissivity` key (0–15) bake that light into the quad, and item quads keep the brighter
  of the baked light and the light passed in. OBJ models still do not support emissive faces or per-vertex colors; fluid
  container models ignore fluid luminosity.
  Refined glowstone armor does not use that path. It is the vanilla armor layer drawn fullbright.
- Fabric's fluid renderer ignores tint alpha. Mekanism's fluid tints are opaque, and steam's transparency is in its
  texture. `shouldDisplayFluidOverlay` is read from the default block state when the client starts, not again per face;
  structural glass always requests the overlay, so the fluids drawn against it match Forge.
- Data generation runs on Forge only; both loaders ship the checked-in generated data.
