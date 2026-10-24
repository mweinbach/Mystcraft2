# Loader parity and upstream gaps

Extreme Reactors 1.19.2 is one gameplay implementation with thin Forge and Fabric
adapters. This file records how each loader service is provided and every known
gap against Extreme Reactors 2 at `127cb03` (see [UPSTREAM.md](UPSTREAM.md)).

## Layout

| Path | Contents |
| --- | --- |
| `src/main/java/it/zerono/mods/extremereactors` | Extreme Reactors 2: API, reactor, turbine, reprocessor and fluidizer, content, config, client models and renderers. |
| `src/main/java/ports/extremereactors/multiblock` | ZeroCore 2's MIT multiblock API. |
| `src/main/java/ports/extremereactors/corelib` | The clean-room replacement for the rest of ZeroCore 2. |
| `src/main/java/ports/extremereactors/compat` | The port's EMI plugin, shared by both loaders. |
| `src/main/java/ports/extremereactors/platform` | `Platform` and `ClientPlatform` services, plus four classes with the same name and API on both loaders: `PlatformBlockEntity`, `PlatformBakedModel`, `PlatformLiquidBlock`, `PlatformBucketItem`. |
| `src/main/resources` | All assets, recipes, loot tables, tags, worldgen features and EMC data. |
| `src/forge` | `ForgeMod`, `ForgePlatform`, `ForgeTransfer`, `ForgeNetwork`, `ForgeConfigCondition`, `ForgeComputerCraft`, `ForgeMekanism`, the ore biome modifier, the client entrypoint and `mods.toml`. |
| `src/fabric` | `FabricMod`, `FabricPlatform`, `FabricTransfer`, `FabricEnergy`, `FabricFluid`, `FabricNetwork`, `FabricComputerCraft`, `FabricMekanism`, the `bigreactors:api` entrypoint, the `GameRendererMixin` frame hook, the client entrypoint and its model provider, the `forge:` tag bridges, the bucket model stand-in and `fabric.mod.json`. |
| `src/gametestCommon`, `src/gametestForge`, `src/gametestFabric`, `src/test` | GameTests and JUnit tests; never packaged. |
| `fabric/` | The isolated Loom build. The root Gradle build produces the Forge JAR. |

## Loader services

| Service | Forge | Fabric |
| --- | --- | --- |
| Energy | Forge Energy through `ForgeCapabilities.ENERGY` | Team Reborn Energy 2.3.0 (`EnergyStorage.SIDED`, bundled), 1 E per FE |
| Energy unit shown | `FE` (Forge Energy) | `E` (Energy), Tech Reborn's label for Reborn Energy |
| Items | `ForgeCapabilities.ITEM_HANDLER` | Transfer API `ItemStorage.SIDED`, with transactions |
| Fluids | `ForgeCapabilities.FLUID_HANDLER`, in mB | Transfer API `FluidStorage.SIDED`; 81 droplets per mB, amounts that are not whole mB are refused |
| Charging ports | Forge Energy item capability | Reborn Energy `EnergyStorage.ITEM` |
| Fluids and buckets | Forge `FluidType` with the upstream density, temperature, viscosity, light and rarity | `FabricFluid` with Forge's flowing defaults and the same properties; tinted render handlers |
| Network | `SimpleChannel` | Fabric networking, same payloads |
| Config | `config/extremereactors/client.toml` and `common.toml` through `corelib` | Same files and format |
| Recipe condition | `bigreactors:config_condition` as a Forge condition | The same id as a Fabric resource condition |
| Ores | One biome modifier per ore feature | `BiomeModifications.addFeature` for the same four features and biome tags |
| Tags | Forge's own `forge:` tags plus the shared `c:` tags | The shared `c:` tags plus bridges that define the `forge:` tags Forge would |
| Steel recipes | Load when `forge:ingots/steel` has items; a `c:steel_ingots` bridge to it | Load when `c:steel_ingots` has items, as Fabric steel mods tag it; the steel ingredients accept either tag |
| Block entity hooks | Forge `onChunkUnloaded` and `getModelData` | Fabric's server and client block entity unload events; render attachments for model data |
| Connected-casing, glass and fuel rod models | `ModelEvent.BakingCompleted` (at `HIGH` priority) runs the model builders on the baked model map | A Fabric model loading API variant provider claims the blockstates the builders replace and bakes the same models; see [Fabric models](#fabric-models) |
| Frame end hook | `RenderTickEvent` at `END`, once per frame | `GameRendererMixin` at the end of `GameRenderer.render`, once per frame, also when no world is shown |
| Bucket sounds | `FluidType` fill and empty sounds | `FabricFluid.getPickupSound` and the bucket item play `BUCKET_FILL` and `BUCKET_EMPTY` |
| Other mods' item storages | `IItemHandler` capability | `StorageItemHandler` over `ItemStorage.SIDED`: `getSlots()` takes the storage's views once, slot calls read them in O(1) |
| Add-on API calls | Forge IMC to `bigreactors` (see [API dispatcher](#api-dispatcher)) | `bigreactors:api` entrypoint (`ExtremeReactorsApiEntrypoint`), same methods and order |

Passive power taps expose an extractable energy storage and never push; active
taps push into the neighbor every tick, as upstream. The same holds for passive
and active fluid ports.

`scripts/check_loader_parity.py` checks both built JARs:

- descriptor isolation, the mod id `bigreactors`, version and license from `port.properties`;
- no Fabric or Reborn classes referenced from the Forge JAR, and no Forge classes from the Fabric JAR;
- byte-identical shared assets and data, apart from the listed loader-only paths;
- the Fabric mixin refmap;
- the same platform class set on both loaders;
- identical GameTest names and timeouts, with every shared test forwarded by both loaders;
- the Forge biome modifiers match the Fabric `addOre` calls, including each ore's config switch;
- every `forge:` tag that recipes, moderators, coils or reactant mappings reference resolves on Fabric;
- no Fabric load condition names a `forge:` tag, and every steel-gated recipe tests `c:steel_ingots` on Fabric and
  accepts it in its steel ingredients.

## Gameplay status

| Area | Status |
| --- | --- |
| Reactor multiblock (basic and reinforced) | Ported. Assembly rules, fuel rods, control rods, access ports, power taps, charging ports, fluid ports, redstone ports and the creative water generator. |
| Reactor simulation | Ported unchanged: radiation, fuel and waste, heat transfer, moderators (solid and fluid), passive energy and active coolant boiling. |
| Turbine multiblock and simulation | Ported unchanged: rotor bearing, shafts and blades, coils, steam intake, condensation and energy. |
| Reprocessor and fluidizer | Ported, including recipes. |
| Screens | All 13 upstream screen classes, for every controller and port that has a menu, with Extreme Reactors' textures, layouts, tooltips and controls, on `corelib`'s GUI layer (see below). |
| Fuel and coolant registries, modpack API (`modpack_api.json`), IMC | Ported; see the dispatcher note below. |
| Ores, ingots, dusts, blocks, fluids, buckets, wrench | Ported. Ores generate at upstream's heights and honor the worldgen switches (see below). |
| Block models, fuel rod and rotor rendering | Ported; the rotor animates from the synchronized rotor speed. The textures are the port's own (see [Machine textures](#machine-textures)). |
| EMC | Every item has a value or a documented exclusion; see [emc/README.md](emc/README.md). |

## Known gaps

**Screens.** Upstream's screen classes are imported with only their imports and
Forge's `NonNullSupplier` changed. They are built on ZeroCore's
all-rights-reserved GUI framework, which `corelib` replaces with its own layout
engines, controls, data bindings, sprites and tooltips, written from how the
screens use them. Screens use the same textures, sizes and positions, so they
match upstream's look, but `corelib` draws them with its own code and is not
pixel-identical to ZeroCore. With Patchouli installed, each screen's help
button opens its handbook entry, as upstream; without it the button is hidden.

**Integrations.** All are optional: the JARs only reference them when the mod
is loaded. The Fabric `fabric.mod.json` lists them under `suggests`
(`computercraft`, `patchouli`, `jei`, `emi`, `mekanism`); there is no
`breaks`, as no known mod is incompatible. The Fabric Mekanism adapter loads
only when the `mekanism` mod also provides this repository's
`ports.mekanism.fabric.api` classes and the Mekanism API classes it uses, so a
different Mekanism Fabric port leaves the gas port inert instead of crashing.

| Upstream | Forge | Fabric | Notes |
| --- | --- | --- | --- |
| ComputerCraft peripherals on the reactor and turbine computer ports | CC: Tweaked 1.101.3 | CC: Restitched 1.101.2 | Upstream's peripheral names and methods. `corelib`'s `ComputerCraftPeripheral` replaces ZeroCore's connector: methods that change the machine run on the server thread, reads answer from the computer thread. |
| JEI recipe categories | JEI 11.6 | JEI 11.6 | Upstream's eleven categories and catalysts, ported to JEI's common API, so one plugin serves both loaders. Fluid slots go through `JeiFluids` instead of `ForgeTypes`. |
| EMI | EMI 1.1.24 | EMI 1.1.24 | Upstream has no EMI plugin; it relied on EMI reading JEI. The port adds a native plugin with the JEI plugin's categories, layouts, workstations and reactant, coolant and vapor stacks, so it works without JEI. With both installed, EMI uses the native plugin. |
| Patchouli handbook (the Extreme Book) | Patchouli 1.19.2-77 | Patchouli 1.19.2-77 | Upstream's book, entries and templates unchanged, apart from the template component ids. `corelib` provides the crafting, smelting, spotlight and multiblock template components ZeroCore did; multiblock previews draw assembled parts. The book is in the general creative tab. |
| Mekanism gas port (`reinforced_reactorfluidport_mekanism_passive`) | Mekanism 10.3.9 | This repository's Mekanism Fabric port | The port offers the reactor's vapor tank as Mekanism gas through Mekanism's rotary recipes (steam becomes `mekanism:steam`). The mapping is `MekanismRotary`, shared by both loaders. Like upstream, the port is passive, so Mekanism pulls the gas: Forge through the gas capability, Fabric through the `mekanism:gas_handler` block lookup. Without Mekanism the block exists but moves nothing, as upstream without Mekanism. |

**Energy unit label.** Extreme Reactors 2 has no energy-unit setting: its
screens and computer API always say `FE`, the only unit ZeroCore's
`EnergySystem` enables on 1.19. The port keeps one internal unit, still saved
as `ForgeEnergy`, and takes its label from the loader. Forge shows `FE`;
Fabric shows `E`, the unit of the Team Reborn Energy API its power taps and
charging ports speak, 1:1 with FE. This covers the reactor, turbine,
reprocessor and fluidizer screens, the JEI and EMI energy tooltips, and the computer
port's `getEnergyStoredAsText` and `energySystem` stat. There is no config
switch for it. Not relabeled: the block names `Power Tap (FE)` and
`Charging Port (FE)` (upstream's lang keys and registry names say `fe`), and
the handbook text, which comes from upstream unchanged.
`reactorItemsAndEnergyThroughLoaderApis` checks the label on each loader.

**Fabric fluid physics.** Fabric has no equivalent of Forge's `FluidType`
hooks. The Fabric variant instead depends on the separate Fabric-only
[Fluid Physics](../fluidphysics/README.md) library, which is bundled
jar-in-jar. Extreme Reactors registers each reactant and moderator fluid there
with the values `ReactorFluidType.of` uses on Forge: swimmable fluids get
water-like travel and jumping, the swimming pose, drowning, current pushing, the
0.4 fall distance modifier, boat support and the `WATER` path type; steam gets
the 0.1 fall distance modifier and the `WALKABLE` path type and does not drown,
push or support boats. Walkable blocks next to any of them get the `WATER_BORDER`
path type, Forge's default `adjacentPathType`, and, as with `canExtinguish(false)`
on Forge, none of them puts out burning entities. As on Forge, `canDrown` ignores
`canBreatheUnderwater`, so undead and water mobs also lose air in a swimmable
fluid; water breathing still protects them. The Forge build and `src/main` are unchanged.

The shared GameTests pin the behaviour on both loaders:
`swimmableFluidDrowns`, `nonSwimmableFluidDoesNotDrown`,
`swimmableFluidSetsTheSwimmingPose`, `nonSwimmableFluidDoesNotSetTheSwimmingPose`,
`swimmableFluidSupportsBoats`, `fluidPathTypesMatchForge`,
`swimmableFluidPushesEntities` and `nonSwimmableFluidDoesNotPushEntities`. The
library has its own GameTests for a swimmable and a non-swimming test fluid,
including the fall distance modifiers.

Limits: Forge's `FluidType` remains a Forge API; the Fabric library exposes its
own registration API, so other mods cannot query Forge's type name. Forge also
gives item entities their `FluidType#setItemMovement` bobbing in these fluids;
the library pushes item entities and applies the fall distance modifier, but
they do not bob. Fog, underwater overlays and other visual water effects are not
reproduced, and none of this claims pixel parity with Forge.

**Fabric bucket models.** The shared bucket item models use Forge's
`forge:bucket` loader. On Fabric, `FabricClientMod` supplies those model ids
itself before their JSON is read: the vanilla bucket with a fluid overlay tinted
per fluid. It matches Forge's look closely but not pixel for pixel. The port
used to ship a `forge:item/bucket_drip` stand-in instead; in Tekxit 4, Porting
Lib reads the `loader` key, has no `forge:bucket` loader and dropped all ten
bucket models, and the Mekanism port ships a different file with the same id.

<a id="api-dispatcher"></a>**API dispatcher.** Upstream ran its own registrations directly and other mods'
calls through Forge IMC, dropping calls made after loading. The port queues
other mods' calls until IMC processing and runs late calls directly. The order
is the same on both loaders.

Fabric has no IMC. Add-ons instead declare a `bigreactors:api` entrypoint
implementing `ports.extremereactors.fabric.api.ExtremeReactorsApiEntrypoint`.
Its `sendApiMessages(messages)` is called once, at the point Forge processes
IMC, and each `messages.send(method, runnable)` stands for one
`InterModComms.sendTo("bigreactors", method, () -> runnable)`. The methods are
the ones Forge's IMC handling accepts (`ExtremeReactors.IMC_METHODS`: the
reactant, reaction, mapping, moderator, coolant and coil material
registrations and removals); a message with another method is logged and
skipped, as Forge ignores it. Messages run per method in `IMC_METHODS` order,
in the order they were sent, before the queued direct calls of that method, as
on Forge.

The port previously ran each IMC message outside the dispatcher's internal
scope, so the API calls a message made were queued after that method's queue
had already been drained and were then dropped. Messages now run inside
`InternalDispatcher.runInternal`, on both loaders, so their calls apply.

<a id="fabric-models"></a>**Fabric models.** Forge lets the model builders
replace entries of the baked model map in `ModelEvent.BakingCompleted`. Fabric
has no such event, and editing `ModelBakery`'s map breaks with mods that load
models differently (ModernFix's dynamic resources, Continuity). The Fabric
client instead registers a model loading API variant provider for the blocks
whose models a builder replaces (each builder declares them in
`ICustomModelBuilder.getReplacedModels()`). It claims every state of those
blocks, loads the original variant from the blockstate files and, when the
game bakes a replaced state, runs the builders in Forge's order against a view
holding the original baked models and the earlier builders' replacements. The
builder code and its output are the same as on Forge; other states bake their
original models. Item models (`inventory`) are not claimed.
`ModelReplacementsTest` and `BlockStateModelsTest` check the id mapping, the
builder order and the variant matching without a client; the rendered result
still needs a client check.

**Worldgen options.** Upstream defines the worldgen options but never reads them,
so its ores always generate. The port honors `enableWorldGen` and the per-ore
`yelloriteOreEnableWorldGen`, `anglesiteOreEnableWorldGen` and
`benitoiteOreEnableWorldGen`, read when a server starts. They default to on, so
default worlds match upstream. The cluster count, cluster size and
`yelloriteOreMaxY` options stay inert, as upstream: the counts, sizes and
heights come from the placed and configured features, which a datapack can
override. `enableWorldRegeneration` and `userWorldGenVersion` are inert too;
already generated chunks are never regenerated.

**Kept upstream quirks.** These are reproduced on purpose and pinned by tests
where possible:

- The turbine's rotor validation clears its found-coils list before walking the
  rotor, so the `blades_too_far`, `metal_too_near` and `invalid_metals_shape`
  checks never fire. `turbineCoilsBeforeBladesAssembleLikeUpstream` pins this.
- Upstream references `forge:storage_block/apatite` and
  `forge:storage_block/cinnabar` (singular) as moderators. No mod defines those
  tags, so they match nothing on either loader.
- The creative water generator's block entity type also lists the reinforced
  reactor fluid port as a valid block. It has no effect.

**Out of scope.** The energizer is not part of the 1.19 branch. Cross-loader
multiplayer and moving worlds between loaders are not supported, and there is
no migration from Extreme Reactors 2 worlds: the mod id is the same
(`bigreactors`), but saves are untested across the two.

## Machine textures

The port replaces upstream's machine textures with its own set, drawn by
`scripts/generate_textures.py`, so a reactor, its turbines, the reprocessor and
the fluidizer read as one plant. This is a deliberate visual departure; the
structures, rules, models and blockstates are upstream's.

- One steel palette per tier: galvanized steel for basic parts, darker gunmetal
  with hazard-striped corners for reinforced ones. Each machine keeps an accent
  on its frame corners, controller screen and glass tint: yellow for the
  reactor, blue for the turbine, green for the reprocessor, violet for the
  fluidizer.
- Casings use upstream's variant models: a seamless plate on faces and a
  continuous frame on edges and corners. Glass connects to its neighbors
  through upstream's sixteen-mask models.
- Every port has the same bezel. A glyph says what it carries (fuel, waste,
  water, steam, energy, redstone, computer), and arrows on the bezel say which
  way it flows: green in, orange out. Unconnected ports are dimmed, as upstream.
- Controllers show a red alert when the machine is not assembled, a dim idle
  screen when it is off, and an animated readout when it runs.
- Fuel rods have hazard bands and rotor blades accent-colored tips. The fuel
  column fill is drawn as stacked pellets with a slow glow moving along it,
  still tinted by the reactant color.
- Ores, ingots, dusts, items, buckets, fluids other than the fuel column, and
  the screens keep upstream's textures.

Upstream's casings and glass never showed their connected variants in this port
before this change: parts cached their model data from before the machine
assembled. `corelib` now drops that cache when a multiblock re-renders and when
a client update changes a part's model data, so assembled machines and running
controllers draw the right faces on both loaders.

## Machine effects

The port adds client-side effects that Extreme Reactors 2 does not have. They
are cosmetic: they read state the server already sends to clients and change
nothing in how machines run, so servers and clients with them turned off
behave the same. Each one can be turned off in
`config/extremereactors/effects.toml`, read when the client starts.

- **Control rod drives** (`rodDrives`). Each control rod has a drive on the
  outside of the reactor, whose shaft stands out as far as the rod is
  withdrawn. It moves a full stroke a second toward the rod's insertion.
  Drives are not drawn when a solid block covers the rod.
- **Cherenkov glow** (`cherenkovGlow`, `glowStrength`). A running reactor's
  fuel columns glow blue behind its glass, brighter with more fuel and dimmed
  column by column by its control rod. Reactors without glass draw nothing.
- **Steam** (`steam`, `particleDensity`). A running actively-cooled reactor
  above 100 °C puffs steam from output fluid ports that face open air, and a
  turbine vents a plume from its top while it exhausts ("vent all", or "vent
  overflow" with a full exhaust tank). Extreme Reactors' unused
  `disableReactorParticles` and `disableTurbineParticles` client options also
  turn these off.
- **Alarms** (`alarmLamps`, `alarmSounds`, `alarmVolume`). The controller's
  two grey indicator lamps flash red while the fuel is at or above 2000 °C or a
  running actively-cooled reactor's coolant tank is under 10%, with a looping
  alarm; and amber when a running reactor has no fuel or waste fills 90% of the
  fuel pool. Extreme Reactors has no meltdown, so these only warn. The
  thresholds are fixed (`effects.EffectThresholds`), not client options: the
  server resends a reactor's state whenever it crosses one, so it has to know
  them.
- **SCRAM** (`scram`). When a reactor shuts down and all its rods go fully in
  within a second, as its controller's SCRAM button does, the drives drop and
  bounce, the lamps flash red and white for ten seconds, steam vents from the
  top and a klaxon sounds. Clients recognize it from the two updates, so
  scripted shutdowns that do the same look the same.

Cost: the effects tick only for machines within `range` blocks (64 by
default) of the camera and forget the others. A reactor's client state is also
dropped as soon as it stops ticking (disassembled, unloaded or left behind with
its world) and when its level unloads, so the cache never keeps a controller,
its parts or an old level alive. Each reactor's part lists are
rebuilt only when its part count changes, or every five seconds. The glow is
drawn once per reactor by its first controller, two boxes per fuel column. Each
control rod is a block entity renderer of three boxes. Particles are capped per
port and per tick, and vanilla's particle setting still applies.

Network: the server now resends a machine's state to nearby clients, at most
every two seconds, when it changes noticeably or crosses a threshold an effect
switches at; a steady machine sends nothing. Values are compared with what was
last sent, so a crossing during the two seconds is sent at their end.

- A reactor: its fuel temperature by 5% (at least 20 °C) or a tank by 5%, or
  crossing 100 °C (steam), 2000 °C (overheat), 10% coolant, 90% waste, or
  running out of fuel.
- A turbine: the steam it took last tick by 25% (at least 10 mB) or starting or
  stopping, its exhaust tank by 5% or filling up, or its vent setting. Upstream
  only sent these to players with its screen open, so the plume of a turbine
  started or stopped nearby would otherwise never change.

Each update re-renders the chunk section of the machine's reference corner on
clients. Rod insertion and on/off already reached clients without a screen
open.

Gaps and risks:

- The glow uses a custom additive render type built on vanilla's lightning
  shader. Shader packs (Iris, Oculus) may draw it differently or not at all;
  turn `cherenkovGlow` off if it looks wrong.
- The reprocessor and the fluidizer have no effects.
- The sounds are the port's own synthesized clips, not recordings; their
  subtitles are only in `en_us.json`.

## Tests

| Test | Covers |
| --- | --- |
| `FuelContainerTest` (JUnit, both builds) | Fuel and waste accounting. |
| `LayoutEngineTest`, `GuiDataTest` (JUnit, both builds) | `corelib`'s fixed, vertical, horizontal, tabular and anchored layouts; text input constraints, tooltip templates and data bindings. |
| `EffectsLogicTest` (JUnit, both builds) | Rod drive motion and SCRAM drop, SCRAM recognition, alarm conditions, the reactor and turbine status sync (rate limit, threshold crossings, turbine flow, venting and vent setting), and the client cache dropping machines that stop ticking. |
| `basicReactorAssembles`, `basicTurbineAssembles` | Assembly of minimal machines. |
| `reactorWithoutControllerDoesNotAssemble`, `reactorFuelRodNeedsCasingBase`, `turbineLooseBladeDoesNotAssemble` | Validation errors. |
| `turbineCoilsBeforeBladesAssembleLikeUpstream` | The upstream coil-validation quirk. |
| `passiveReactorBurnsYellorium`, `controlRodsReduceOutput`, `graphiteModeratesBetterThanAir` | Reactor simulation. |
| `activeReactorBoilsWater`, `turbineSpinsOnSteam` | Coolant, vapor and the turbine. |
| `dataPacketsUpdateAnAssembledTurbine` | Block entity packets update an already assembled client machine. |
| `reactorItemsAndEnergyThroughLoaderApis`, `reactorCoolantThroughLoaderApis` | Forge capabilities on Forge; the Transfer API and Reborn Energy on Fabric. |
| `fluidBucketsThroughLoaderApis` | Each of the ten fluid buckets drains a full bucket of its fluid into an empty bucket, and an empty bucket filled with that fluid becomes it, through `IFluidHandlerItem` on Forge and `FluidStorage.ITEM` on Fabric. Partial amounts and filling a full bucket move nothing, as with vanilla buckets. |
| `recipeConditionsAndTags` | Config conditions, all 16 steel and steel-free recipe pairs, `c:` and `forge:` tags, reactant, coil and moderator lookups. The Fabric server puts netherite ingots in `c:steel_ingots` through the test-only `steel` data pack, so it covers the steel recipes; Forge covers the steel-free ones, and the steel ones with `-PwithMekanism`. |
| `oreFeaturesPlaceAtUpstreamHeights` | The ore placed features decode to upstream's heights. |
| `oreGenerationFollowsConfig` | The ores are in their biomes exactly when their worldgen switches are on, and not elsewhere. |
| `everyItemHasAnEmcDecision` | EMC coverage; with `-PwithProjectE` or `-PwithItemAlchemy`, the live values. |
| `computerPortControlsTheReactor` | The reactor computer port's methods, called the way ComputerCraft calls them: doubles, tables keyed by doubles, and errors for bad arguments. |
| `ModelReplacementsTest`, `BlockStateModelsTest` (JUnit, Fabric build) | The Fabric model provider: which ids and blocks it claims, builders running once each in Forge's order and seeing originals and earlier replacements, and blockstate variant matching. |
| `StorageItemHandlerTest` (JUnit, Fabric build) | Walking another mod's item storage slot by slot iterates it once, and each `getSlots()` sees its current size. |
| `apiEntrypointMessagesApplyLikeImc` (Fabric only) | A test-only `bigreactors:api` entrypoint's coil material registrations and removals apply like Forge IMC messages; an unknown method is skipped. Forge has no entrypoint; its IMC path shares `onInterModProcess`. |
| `reactorMekanismPortFollowsMekanism` | Without Mekanism, the Mekanism port exposes nothing. With `-PwithMekanism`, Mekanism's own gas capability on the port (on Fabric the port's capability bridge, which falls back to the `mekanism:gas_handler` lookup) holds the reactor's steam as `mekanism:steam` and drains it; then a Mekanism pressurized tube, its port side set to pull as the configurator does, carries steam into a Mekanism chemical tank. Forge uses Mekanism 10.3.9; Fabric uses this repository's Mekanism port. The turbine has no Mekanism port (upstream leaves it as a TODO), so only the reactor is covered. |

Still to check on a Fabric client after the model loading API change: the
assembled casings, glass, fuel rods and turbine rotor render as before, also
with ModernFix and Continuity installed; the frame-end effects (lamps, glow,
steam) run once per frame; filling a bucket from an Extreme Reactors fluid plays
the bucket fill sound. These have no client-free test.

Before that change, a Fabric development client was run: both machines assemble, the reactor
fuel rods render their fill level, the turbine rotor spins, and the buckets
render tinted. With `/ershowcase` (see the [README](README.md)), every screen
was opened on a running machine on Fabric, and the reactor and turbine
controllers on Forge, showing live values, tooltips and working buttons.
`/ershowcase build` and `/ershowcase plant` were photographed on both loaders
with the redrawn textures: assembled casings join up, glass connects, and
running controllers show their active face. With the machine effects, the
plant reactor was recorded on both loaders running, with its rods moved, then
SCRAMmed and restarted, with the game audio written to a file: the drives move
and drop, the glow follows the rods, steam comes from the output port, the
turbine and the SCRAM, the lamps flash and the alarm and klaxon play with their
subtitles. Their alarms were real: the plant reactor runs near 4000 °C with an
almost empty coolant tank.
With `-PwithCompat -PwithEmi` on Forge, every EMI category was opened with its
tooltips, and the handbook was opened on its landing page, a crafting page and
the turbine rotor's multiblock preview.
