## Gear modules

`GearGameTests` runs on both loaders. The standalone jetpack and the armored jetpack are the same item with a hydrogen
tank of 24,000 mB. Normal mode, while ascend is held and the player is off the ground, adds 0.15 to vertical velocity
up to 0.5 and spends 1 mB. Hover, while airborne and not ascending, pulls vertical velocity toward 0 by 0.15, capped
at 0.2 in either direction, and spends the same 1 mB. The jetpack HUD is the mode and the stored hydrogen.

A flowing scuba tank with oxygen, worn with the scuba mask, spends 1 mB and then fills the player's missing air from
the tank. Magic damage is already cancelled by the player-tick tests. The scuba HUD is the flowing state and the
stored oxygen.

Free runners in normal mode cancel fall damage when they hold enough energy (50 J per point of damage, ratio 1) and
add 0.5 step height. Safety cancels the fall and does not add step height. Disabled does neither. The HUD is the mode
and the stored energy. The boots hold 64,000 J.

The modification station spends energy from its back for 40 ticks and installs the module in its module slot onto the
item in the container slot. The remove packet takes that module back off and returns the module item to the player.
Hydraulic propulsion on the boots then adds 0.5 step height, and removing it clears the boost.

The module tweaker locks the hotbar, armor and offhand slots. Its settings packet writes one module config. Excavation
escalation starts at normal, efficiency 16, against the bare Meka-Tool's efficiency of 4. Setting the mode to off
makes the destroy speed 0. The config index is the module's own list, after the enabled, mode-change and HUD entries.

A MekaSuit chest with the jetpack module and hydrogen lifts the same way and draws a hydrogen HUD element.
Gravitational modulation grants flight once the chest holds the 1,000 J per-tick cost, and spends that cost only while
the player is flying. Hydraulic propulsion's low jump boost adds 0.5 to a jump when boost is held and spends
1,000 J for each 0.1 of boost. Locomotive boosting's low sprint boost moves a sprinting player and spends 100 J for
each 0.1 of boost. Magnetic attraction's low range pulls a nearby item that has no pickup delay and spends 250 J.
Nutritional injection feeds a hungry player from nutritional paste in the helmet. Inhalation purification shortens a
harmful effect by the upstream nine extra ticks and spends 40,000 J. Vision enhancement starts disabled; once enabled
it spends 500 J a tick and draws an enabled HUD element. Electrolytic breathing, with the mask in water and the player
short of air, restores air and spends energy. Radiation shielding on the chest is 0.4 while the module is enabled, and
the dosimeter draws a HUD element from the player's radiation capability. Motorized servo applies swift sneak, frost
walker applies frost walker, and the silk touch and fortune modules apply those enchantments. The elytra unit allows
elytra flight when the chest holds the 32,000 J per-second cost and spends it on a twentieth flight tick. Charge
distribution equalizes energy across the worn MekaSuit and, with charge inventory on, charges an energy tablet from
the chest at the 10,000 J inventory rate.

Attack amplification's default medium mode adds 8 to the Meka-Tool's base damage of 4 when the tool has the energy.
Farming's low radius flattens the clicked grass into a path, because the shovel is tried before the hoe, and a second use tills that path into farmland. The neighbour stays grass. The shearing module shears a sheep, a mooshroom
and a snow golem and carves a pumpkin; on Fabric, sheep, mooshrooms and snow golems implement the shearable interface
and pumpkin carving accepts the shears carve action. Vein mining breaks a connected iron-ore vein and leaves stone
beside it. Blasting's low radius breaks the 3×3 plane of the face the player is looking at. Teleportation moves the
player onto a targeted block at least sqrt(5) blocks away when the two blocks above it are air, and spends
1,000 J per 10 blocks of distance squared.

On a Fabric development client at noon, with the HUD, debug screen, chat and toasts hidden, a survival player in close
third person is airborne above a smooth-stone pad on a flat grass world. The jetpack model is on the player's back and
orange flame particles trail from the thrusters. The sky is clear.
