## Robit

The Robit (`RobitGameTests`) is placed from its item onto a chargepad that is not already charging. The item keeps
the entity's energy, inventory, security mode, skin and name, and placing it copies those onto the entity and
consumes the item. The home position is that chargepad. If the chargepad is gone when the Robit checks, it drops
itself as that item and is removed. With no energy, and while it is not standing on the pad, it goes home.

It follows its owner while following is enabled, the owner is in the same dimension, the Robit is more than four
blocks away, and it still has energy. Travel spends energy from the distance moved. Item pickup, while enabled,
collects a dropped item that is out of its pickup delay into the 27-slot inventory, and it will not pick up another
Robit item. The pickup goal walks toward an item within ten blocks when a path exists. A following Robit that is
unloaded keeps its chunk loaded for a second so the follow check can teleport it to the owner.

A powered chargepad charges a Robit standing on it from the pad's energy, the same pass that charges items a player
is holding. The pad is filled through its bottom: Forge Energy on Forge, Team Reborn Energy on Fabric. The Robit
itself is charged through Mekanism's energy container. Its smelting slots run an energized-smelter recipe, including
a vanilla smelting recipe such as iron dust to an iron ingot, at 100 ticks, and spend the energized smelter's energy
per tick.

The item model, the entity model and the skin textures are the upstream ones. Skins are the base skin, the allay
skin and the pride skins, and a moving Robit cycles that skin's frames. The item model leaves `#robit` unmapped and
the baked model swaps in the skin sprite from `mekanism:textures/atlas/robit.png`. Forge binds that atlas from the
model's render types. On Fabric the item renderer binds it the same way. Vanilla and Fabric's item pass bind the
block atlas only, and those UVs then sample the missing-texture sprite. The skin is not a flat
item texture left out of that atlas: the entity and the item share the Robit atlas, so stitching
the PNG onto the block atlas would split their UVs. In the Fabric client the hotbar slot and the
held item are the gray body with green eyes. The main screen shows the greeting, energy, following, pickup and owner, with the
home, rename, skin, pickup and follow buttons. The side tabs open the crafting grid, the inventory, the smelting
slots and the anvil repair screen. A normal right-click on the entity opens that screen. Shift-right-click with a
configurator picks the Robit back up. In the Fabric development client, survival, a charged Robit item used on an
idle chargepad places the entity on the pad. With the HUD hidden, the Robit is the gray body with green eyes
standing on the pad, in front of the pad's back panel. The main screen then opens on a right-click.
