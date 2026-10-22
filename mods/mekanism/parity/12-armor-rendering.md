## Armor rendering

The MekaSuit, jetpack, armored jetpack, scuba tank, scuba mask, free runners and armored free runners are the
upstream items. Each one hides vanilla's armor mesh with `mekanism:render/null_armor.png` and draws a custom model.
Hazmat is the vanilla armor mesh with `mekanism:textures/models/armor/hazmat_layer_1.png` (layer 2 on the legs).
The HDPE elytra is not armor. `AddLayers` attaches `MekanismElytraLayer`, which draws `textures/entity/hdpe_elytra.png`.

Forge draws the custom models from `MekanismArmorLayer`, added by the same `AddLayers` event. Fabric registers
Fabric API's `ArmorRenderer` for every piece in `GearArmorPieces` and then removes that armor layer, so the model
is drawn once. The renderer copies the entity pose onto the slot's armor model, the way `MekanismArmorLayer` does,
and calls the same `ICustomArmor`. The MekaSuit is the upstream OBJ (`models/entity/mekasuit.obj`) with the
`rendertype_mekasuit` shader. The jetpack, scuba set and free runners are the upstream Java models and
`render/jetpack.png`, `render/scuba_set.png` and `render/free_runners.png`. A bare MekaSuit has no module
geometry. Module ticking is in the gear section. The port's OBJ loader still has no emissive faces; LED parts use the
existing fullbright quad transform.

`ArmorRenderingGameTests` runs on both loaders. Each piece equips into its slot, a full MekaSuit stays worn
together, and a jetpack stays worn with a scuba mask. The HDPE elytra equips in the chest slot.

On a Fabric development client at noon, with the debug screen off and chat hidden, the player is in third person.
One view is the full MekaSuit. The other is the jetpack with the scuba mask. First-person MekaSuit arms stay on
`RenderArmEvent` and were not in those shots.
