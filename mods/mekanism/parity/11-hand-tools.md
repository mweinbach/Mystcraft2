## Hand tools

The configurator, network reader, gauge dropper, atomic disassembler, flamethrower and Meka-Tool are the upstream
items, models and textures. `HandToolGameTests` runs on both loaders. Energy items accept Forge Energy or Team
Reborn Energy. The gauge dropper's fluid and the flamethrower's hydrogen use the item capability, which on Fabric
is the Transfer API. Vein mining uses the existing `onBlockStartBreak` hook, and the flamethrower uses the existing
server player tick. Module ticking, armor rendering and the MekaSuit are later slices. A bare Meka-Tool still digs.

The configurator starts in item-configuration mode. A right click on a machine side reports that side and spends
nothing. A shift-right click steps the side through the data types that side supports and spends
`configuratorEnergyPerConfigure`, 400 J, unless the player is creative. An enrichment chamber's item sides start as
input, with output on the right and energy on the back, so the top steps from input to output. Wrench mode
sneak-bypasses use, so the block is used instead of the item. Empty and rotate stay the upstream modes. Shift-scroll
mode changes are the client packet in the client-events section.

The network reader spends `networkReaderEnergyUsage`, 400 J, on a block and reports what it finds. On a transmitter
that already has a network, the report includes the transmitter count. Two adjacent basic universal cables join one
network of two on the server tick.

The gauge dropper acts on the cursor stack. Fill moves the machine tank into the dropper, drain moves it back, and
dump empties the tank. The dropper holds 16 buckets. External transfer is limited to 256 mB; a manual use bypasses
that limit, so one use moves the 1,000 mB in a basic fluid tank. Dumping a creative tank is refused for a survival
player.

The atomic disassembler's vein mode is off in the default gear config, and a disabled mode reads back as normal.
With vein mining enabled, vein mode breaks the same block in the 3×3×3 search around the click. The click itself is
the normal break. The ore tag is `#forge:ores` and `#minecraft:logs`; Fabric still provides the forge ore tag,
including osmium, beside the `c:` ore tags. Three osmium ore in a row drop at least three raw osmium, and stone
beside the vein stays. A creative player does not vein mine. At non-zero hardness the click costs the 10 J base
times the mode efficiency, 200 J in normal and vein. Each extra block costs half of that times distance to the
power 1.5. The disassembler holds 1,000,000 J.

The flamethrower in heat mode, selected and marked on in the player state, spawns one flame at the end of the
server player tick and spends 1 mB of hydrogen in survival. The tank holds 24,000 mB. The flame smelts an item
entity older than 100 ticks when a smelting recipe exists, so dropped cobblestone becomes stone. Otherwise it burns
the item. Combat and inferno stay the upstream modes.

A Meka-Tool with no modules is a correct tool for drops and performs the disassembler's dig actions: axe, hoe,
shovel, pickaxe and sword. Strip, flatten and till need their modules. Base efficiency is 4 and the energy usage is
10 J, so stone costs 40 J and drops cobblestone. The bare tool holds 16,000,000 J.

On a Fabric development client at noon, with the debug screen off and chat hidden, the six tools are in the hotbar:
configurator, network reader, gauge dropper, atomic disassembler, flamethrower and Meka-Tool. The atomic
disassembler is selected. Its item model is the orange and dark body in the hand, and the mode line reads Normal.
The MekaSuit was not worn.
