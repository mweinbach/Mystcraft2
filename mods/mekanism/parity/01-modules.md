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
  force-disables and stays off. Turning meltdowns back on queues a meltdown; that test clears the queued explosion at
  radius 0 and calls `meltdownHappened`, which dumps the nuclear waste, leaves the non-radioactive fissile fuel,
  clears heated coolant, scrams, sets the post-meltdown damage and raises radiation at the core. A separate test, in
  its own batch, leaves meltdowns on and the radius at the upstream default of 8. Crossing the damage cap at 1,800 K
  queues a meltdown whose saved radius is 8. The explosion breaks the shell. A dirt block about 28 blocks away, past
  the ray reach of that radius, stays dirt. Fissile fuel and superheated sodium have no radiation attribute, so the
  release is `(1,000 mB + 0.5 partial waste) × 0.01 Sv/h per mB × the 50× multiplier`. A full waste tank that burns
  one mB of fuel leaks `0.01` Sv/h at the core. A survival player standing outside the shell, not in the burn
  `hotZone`, accumulates that field on the player tick, and the same happens after `meltdownHappened`. The dosimeter
  reports the capability dose in Sv and the Geiger counter reports `getRadiationLevel` in Sv/h. A player in the core
  is still the existing `radiateEntities` path. Saved fuel, sodium, waste, heated coolant, burn rate, damage, logic
  mode and the waste-port mode come back after the blocks are removed and their NBT is loaded. The master's render
  box is the structure, and reactor glass joins it. Structural glass cannot: upstream rejects a manager whose name
  contains "reactor". The fusion
  reactor (`FusionReactorGameTests`) forms at 5×5×5. The frame follows upstream's face grid: the corners and the
  twelve holes on each face stay air, frame slots are reactor frame, and the controller is the top centre. A laser
  focus matrix, one port and a logic adapter fill other slots. A missing frame block, a controller off the top
  centre, stone in the hollow centre, structural glass in a casing slot and a 2×2×2 stay unformed and are not merged
  with a neighbour. Structural glass set against the shell does not join it. Deuterium, tritium and D-T fuel enter
  through the port's gas handler — Forge's capability, and on Fabric the `mekanism:gas_handler` lookup. Hydrogen,
  steam and lava are refused, a casing block accepts none of them, and a simulated fill stores nothing. A hohlraum
  takes D-T fuel through the item gas handler and refuses deuterium. One that is not full does not ignite. A laser
  under the focus matrix, fed through its back, raises the plasma by one tick of laser energy, and the matrix's
  laser receptor — the call that laser makes — brings the plasma to the burn temperature. That ray is the laser's
  own clip. Forge 43.5.0's clip constructor uses an empty collision context when the entity is null; vanilla calls
  `CollisionContext.of(null)` and crashes, so the Fabric build matches Forge there. That consumes the
  hohlraum, leaves part of its D-T fuel in the fuel tank and starts the burn. Water and steam capacity stay 0 until
  the injection rate changes. Upstream sizes those tanks only inside `setInjectionRate`, and only when the new rate
  differs from the current one, so a fresh reactor left at the default rate of 2 cannot take water until that rate
  is set again. After the rate is set, water enters through the fluid handler. One heating pass at a known casing
  temperature turns it into steam at the upstream water-heating ratio, steam efficiency and enthalpy, and stores
  casing heat as energy at the thermocouple efficiency. Energy leaves the port through Forge Energy or Team Reborn
  Energy; a casing block gives none up, and steam leaves through the gas handler. The logic adapter outputs redstone
  when the fuel tank is empty and deuterium or tritium is short, when the plasma has reached the ready temperature,
  and not while it is disabled. Saved deuterium, tritium, D-T fuel, the injection rate, the logic mode, the
  cooled-logic flag and the port's output mode come back after the blocks are removed and their NBT is loaded.
  Plasma is still hot after that reload, and stored energy does not fall. The master's render box is the structure.
- **Tools** is imported: paxels, tools, armor and shields for bronze, lapis lazuli, osmium, refined glowstone,
  refined obsidian and steel, plus vanilla paxels. Both loaders register them
  (`ToolsModuleGameTests`), craft the bronze gear and the paxel (including averaged durability), and run paxel
  strip and flatten. Paxels do not till, matching upstream. Fabric harvest uses the numeric tier level. The
  `needs_*_tool` tags are empty, and the tier-sorting registry only records the order. Hoe till on Fabric is vanilla's tillable map, gated by
  `canPerformAction`. `c:` tool and armor tags ship in the tools jar alongside the `forge:` tags. Shield items are
  built-in renderers. Forge draws them with `RenderMekanismShieldItem` through `IClientItemExtensions` (the BEWLR),
  and Fabric registers that same renderer on `BuiltinItemRendererRegistry`. The six `ShieldTextures` materials are
  stitched onto the blocks atlas. The `mekanismtools:blocking` property selects the blocking model while the shield
  is in use, and blocking consumes durability on both loaders. A Fabric development client held and blocked with each
  material shield in third person; the shield faces matched the upstream textures. Refined glowstone armor is the
  vanilla armor layer drawn fullbright. Forge calls `getGenericArmorModel`, and Fabric's `ArmorRenderer` calls the
  same hook, which wraps the layer in `GlowArmor`. That glow is not an OBJ emissive material. The port's OBJ loader
  still does not support emissive faces or per-vertex colors, so gear that relies on an OBJ emissive pass does not
  glow that way. The same client showed the refined glowstone set lit in an unlit room. Zombies, skeletons and
  piglins sometimes spawn wearing Mekanism armor, and a zombie can also spawn holding a Mekanism sword
  (`ToolsMobArmorGameTests`). Upstream's list is those three classes, so a husk, drowned, zombie villager or
  zombified piglin is included with zombies, while a stray, wither skeleton or piglin brute is left out. The first
  roll is `mobArmorSpawnRate` (default 0.03). A hit then picks bronze, lapis lazuli, osmium, refined glowstone,
  refined obsidian or steel at equal weight, except a piglin always uses refined glowstone. Each piece then rolls
  its own chance (default 0.5) and fills an empty slot. Forge fires `LivingSpawnEvent.SpecialSpawn` on the callers
  that are about to run `finalizeSpawn`: natural and chunk spawning, spawners, spawn eggs, the summon command,
  structure mobs, patrols, phantoms, cats and strider jockeys. Fabric posts that event at the start of every
  `Mob.finalizeSpawn`. A zombie reinforcement and a village siege call `finalizeSpawn` without that Forge event, so
  on Fabric those zombies roll for Mekanism gear too. Cancelling the event returns before `Mob.finalizeSpawn`'s own
  body; a subclass that continues after `super.finalizeSpawn` still runs. The mob spawner Forge passes from a spawner
  block is absent, and a spawner whose entity NBT is more than an id skips `finalizeSpawn`, so that mob is not
  rolled. The Wildfire's Gender capability is not attached.
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
  false). A player receives a one-time session token on the game connection and the voice socket presents that
  token, so two players behind one address stay distinct. It relays a short audio frame to other connections whose
  walkie is on and on that channel, including a walkie in the offhand, and one recipient's frame is written whole.
  The voice key is U. While it is held, the microphone read blocks instead of polling an empty buffer. GameTests
  cover the channel cycle, the disabled tooltip, the socket bind, the session token, whole frames, and that relay.
  Microphone capture and speaker playback were not run: this environment has no capture device, and `VoiceInput`
  returns when the line is unsupported.
- Generator `forge:` fluid tags (bioethanol, deuterium, tritium, fusion fuel) ship in the Generators jar. They do
  not yet have Fabric `c:` bridges.
