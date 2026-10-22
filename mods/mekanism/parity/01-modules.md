## Modules

Generators, Tools and Additions are separate mod ids built from this port. Each loader emits
`mekanismgenerators`, `mekanismtools` and `mekanismadditions` jars that depend on the core jar.

- **Generators** is imported: registries, config, the packet handler, assets and the checked-in datagen output.
  Both loaders load the jar and register its blocks, items, fluids and block entities. Heat, solar, advanced solar,
  gas-burning, bio and wind generators produce energy (`SimpleGeneratorGameTests`): solar output in daylight, the
  gas-burning generator burning hydrogen, and energy leaving through Forge Energy and Team Reborn Energy. The wind
  rotor's render box is the extended box the block entity reports, and on Fabric the wind generator item is drawn by
  `BuiltinItemRendererRegistry`. The industrial turbine (`IndustrialTurbineGameTests`) forms at 5×6×5 with a rotor,
  blades, dispersers, a coil, a condenser, a valve and a vent. Steam enters through the gas handler — Forge's
  capability, and on Fabric the `mekanism:gas_handler` lookup Extreme Reactors' port uses to feed a neighbour — and
  energy leaves through Forge Energy or Team Reborn Energy. A condenser puts water in the vent. Saved steam, energy,
  the dumping mode and the rotor's blades come back after the blocks are removed and their NBT is loaded. The rotor's
  render box grows with its blades, and the master's box is the structure. In the Fabric development client a formed
  turbine draws that rotor and its blades through structural glass. The fission reactor
  (`FissionReactorGameTests`) forms at 3×4×3: casing on the edges, reactor glass on the faces, one fuel assembly and
  its control rod, an input port, a waste port, a coolant port and a logic adapter. A gapped fuel column, a missing
  corner, a missing control rod and a 2×2×2 stay unformed and are not merged with a neighbour. Fissile fuel and cooled
  sodium enter through the input port's gas handler; water enters through its fluid handler. Hydrogen, lava, a casing
  block and the output ports accept none of that, and a simulated fill stores nothing. Sodium is refused while the
  reactor holds water. One heating pass at a known temperature burns one mB of fuel into nuclear waste and produces
  superheated sodium or steam at the upstream heat, conductivity and enthalpy. Waste leaves the waste port and heated
  coolant leaves the coolant port; the input port gives nothing up. The logic adapter outputs redstone when the fuel
  tank is empty, the temperature is at least 1,200 K, the waste tank is full, or damage is at 100, and redstone in
  activation mode starts the reactor. Damage falls below 1,200 K. At the cap with meltdowns disabled the reactor
  force-disables and stays off. Turning meltdowns back on queues a meltdown; the test clears that queued explosion at
  radius 0 and calls `meltdownHappened`, which dumps the nuclear waste, leaves the non-radioactive fissile fuel,
  clears heated coolant, scrams, sets the post-meltdown damage and raises radiation at the core. It does not assert
  that the explosion breaks casings. Saved fuel, sodium, waste, heated coolant, burn rate, damage, logic mode and the
  waste-port mode come back after the blocks are removed and their NBT is loaded. The master's render box is the
  structure, and reactor glass joins it. Structural glass cannot: upstream rejects a manager whose name contains
  "reactor". A player in the core is not a new test; that dose is the existing `radiateEntities` path. Fusion
  gameplay is not verified.
- **Tools** is imported: paxels, tools, armor and shields for bronze, lapis lazuli, osmium, refined glowstone,
  refined obsidian and steel, plus vanilla paxels. Both loaders register them
  (`ToolsModuleGameTests`), craft the bronze gear and the paxel (including averaged durability), and run paxel
  strip and flatten. Paxels do not till, matching upstream. Fabric harvest uses the numeric tier level. The
  `needs_*_tool` tags are empty, and the tier-sorting registry only records the order. Hoe till on Fabric is vanilla's tillable map, gated by
  `canPerformAction`. `c:` tool and armor tags ship in the tools jar alongside the `forge:` tags. Shield items use
  the existing item-renderer hook, and blocking with them consumes durability on both loaders. The refined-glowstone armor model is not drawn fullbright: nothing calls
  `getGenericArmorModel`. Naturally spawned mobs do not wear the armor, because the living-spawn event is not
  posted. The Wildfire's Gender capability is not attached.
- **Additions** plastic, glow panels, obsidian TNT, baby mobs and balloons are imported. Both loaders register the
  271 blocks (`AdditionsModuleGameTests`), craft red plastic, slabs and fences, and run fluid logging, glow-panel
  support, slick friction, reinforced blast resistance, plastic roads and obsidian TNT priming. Transparent plastic
  tints a beacon. Flint, redstone, explosions, projectiles and fire spread prime obsidian TNT into its own entity.
  Baby creepers, endermen, skeletons, strays and wither skeletons register with their models and renderers, spawn
  where the parent mob spawns (Forge biome and structure modifiers; Fabric biome modifications and a structure-spawn
  mixin) at half the parent's weight, and drop the parent's loot. Balloons craft from leather, string and a dye,
  latch onto a mob and pop without dropping the item. The walkie-talkie crafts from osmium, steel and a basic
  circuit, toggles on with a sneak-use, and cycles channels 1 through 8. Its model override shows the channel while
  it is on. The voice server listens on the configured TCP port only when `voiceServerEnabled` is true (default
  false) and relays a short audio frame to other connections whose walkie is on and on that channel, including a
  walkie in the offhand. The voice key is U. GameTests cover the channel cycle, the disabled tooltip, the socket
  bind, and that relay. Microphone capture and speaker playback were not run: this environment has no capture
  device, and `VoiceInput` returns when the line is unsupported.
- Generator `forge:` fluid tags (bioethanol, deuterium, tritium, fusion fuel) ship in the Generators jar. They do
  not yet have Fabric `c:` bridges.
