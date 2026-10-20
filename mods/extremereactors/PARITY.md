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
| `src/main/java/ports/extremereactors/platform` | `Platform` and `ClientPlatform` services, plus four classes with the same name and API on both loaders: `PlatformBlockEntity`, `PlatformBakedModel`, `PlatformLiquidBlock`, `PlatformBucketItem`. |
| `src/main/resources` | All assets, recipes, loot tables, tags, worldgen features and EMC data. |
| `src/forge` | `ForgeMod`, `ForgePlatform`, `ForgeTransfer`, `ForgeNetwork`, `ForgeConfigCondition`, the ore biome modifier, the client entrypoint and `mods.toml`. |
| `src/fabric` | `FabricMod`, `FabricPlatform`, `FabricTransfer`, `FabricEnergy`, `FabricFluid`, `FabricNetwork`, mixins, the client entrypoint, the `forge:` tag bridges, the bucket model stand-in and `fabric.mod.json`. |
| `src/gametestCommon`, `src/gametestForge`, `src/gametestFabric`, `src/test` | GameTests and JUnit tests; never packaged. |
| `fabric/` | The isolated Loom build. The root Gradle build produces the Forge JAR. |

## Loader services

| Service | Forge | Fabric |
| --- | --- | --- |
| Energy | Forge Energy through `ForgeCapabilities.ENERGY` | Team Reborn Energy 2.3.0 (`EnergyStorage.SIDED`, bundled), 1 E per FE |
| Items | `ForgeCapabilities.ITEM_HANDLER` | Transfer API `ItemStorage.SIDED`, with transactions |
| Fluids | `ForgeCapabilities.FLUID_HANDLER`, in mB | Transfer API `FluidStorage.SIDED`; 81 droplets per mB, amounts that are not whole mB are refused |
| Charging ports | Forge Energy item capability | Reborn Energy `EnergyStorage.ITEM` |
| Fluids and buckets | Forge `FluidType` with the upstream density, temperature, viscosity, light and rarity | `FabricFluid` with Forge's flowing defaults and the same properties; tinted render handlers |
| Network | `SimpleChannel` | Fabric networking, same payloads |
| Config | `config/extremereactors/client.toml` and `common.toml` through `corelib` | Same files and format |
| Recipe condition | `bigreactors:config_condition` as a Forge condition | The same id as a Fabric resource condition |
| Ores | One biome modifier per ore feature | `BiomeModifications.addFeature` for the same four features and biome tags |
| Tags | Forge's own `forge:` tags plus the shared `c:` tags | The shared `c:` tags plus bridges that define the `forge:` tags Forge would |
| Block entity hooks | Forge `onChunkUnloaded` and `getModelData` | Fabric's server and client block entity unload events; render attachments for model data |

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
- every `forge:` tag that recipes, moderators, coils or reactant mappings reference resolves on Fabric.

## Gameplay status

| Area | Status |
| --- | --- |
| Reactor multiblock (basic and reinforced) | Ported. Assembly rules, fuel rods, control rods, access ports, power taps, charging ports, fluid ports, redstone ports and the creative water generator. |
| Reactor simulation | Ported unchanged: radiation, fuel and waste, heat transfer, moderators (solid and fluid), passive energy and active coolant boiling. |
| Turbine multiblock and simulation | Ported unchanged: rotor bearing, shafts and blades, coils, steam intake, condensation and energy. |
| Reprocessor and fluidizer | Ported, including recipes. Without screens they are driven by automation only. |
| Fuel and coolant registries, modpack API (`modpack_api.json`), IMC | Ported; see the dispatcher note below. |
| Ores, ingots, dusts, blocks, fluids, buckets, wrench | Ported. Ores generate at upstream's heights and honor the worldgen switches (see below). |
| Block models, fuel rod and rotor rendering | Ported; the rotor animates from the synchronized rotor speed. |
| EMC | Every item has a value or a documented exclusion; see [emc/README.md](emc/README.md). |

## Known gaps

**Screens are not in this release.** Upstream's 24 screen classes need ZeroCore's
all-rights-reserved GUI framework, so they are being rebuilt on `corelib` with
Extreme Reactors' own textures and layouts in the next pull request. Until then,
right-clicking a controller or port opens nothing, and a machine can only be
switched on by a redstone port whose setting is placed with NBT, for example
`{zcvase_payload:{setting:{sensor:"inputActive",behavior:"SetFromSignal",v1:0,v2:0}}}`
(the turbine also offers `inputEngageCoils`), or by automation through the
computer port once that is wired up.

**Integrations not yet ported.**

| Upstream | State |
| --- | --- |
| ComputerCraft peripherals on the reactor and turbine computer ports | The blocks and `IComputerPort` exist; no peripheral is registered on either loader yet. |
| JEI (Forge), EMI (Fabric) recipe categories | Planned for a later pull request. |
| Patchouli handbook | Its book data and templates are kept, but the handbook recipe and item are off: the templates need ZeroCore's Patchouli multiblock component. |
| Mekanism gas ports | The `reinforced_reactorfluidport_mekanism_passive` block exists, but its handler is empty, so it moves nothing. |

**Fabric fluid physics.** Fabric has no equivalent of Forge's `FluidType`
motion hooks. Extreme Reactors' fluids flow, render and transfer the same, but
entities do not swim in or get pushed by them on Fabric, because vanilla only
applies that to fluids tagged as water or lava.

**Fabric bucket models.** The shared bucket item models use
`forge:item/bucket_drip` as their parent. Fabric gets a stand-in with that id:
the vanilla bucket with a fluid overlay tinted per fluid. It matches Forge's
look closely but not pixel for pixel.

**API dispatcher.** Upstream ran its own registrations directly and other mods'
calls through Forge IMC, dropping calls made after loading. The port queues
other mods' calls until IMC processing and runs late calls directly. The order
is the same on both loaders.

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

## Tests

| Test | Covers |
| --- | --- |
| `FuelContainerTest` (JUnit, both builds) | Fuel and waste accounting. |
| `basicReactorAssembles`, `basicTurbineAssembles` | Assembly of minimal machines. |
| `reactorWithoutControllerDoesNotAssemble`, `reactorFuelRodNeedsCasingBase`, `turbineLooseBladeDoesNotAssemble` | Validation errors. |
| `turbineCoilsBeforeBladesAssembleLikeUpstream` | The upstream coil-validation quirk. |
| `passiveReactorBurnsYellorium`, `controlRodsReduceOutput`, `graphiteModeratesBetterThanAir` | Reactor simulation. |
| `activeReactorBoilsWater`, `turbineSpinsOnSteam` | Coolant, vapor and the turbine. |
| `dataPacketsUpdateAnAssembledTurbine` | Block entity packets update an already assembled client machine. |
| `reactorItemsAndEnergyThroughLoaderApis`, `reactorCoolantThroughLoaderApis` | Forge capabilities on Forge; the Transfer API and Reborn Energy on Fabric. |
| `recipeConditionsAndTags` | Config conditions, the steel gate, `c:` and `forge:` tags, reactant, coil and moderator lookups. |
| `oreFeaturesPlaceAtUpstreamHeights` | The ore placed features decode to upstream's heights. |
| `oreGenerationFollowsConfig` | The ores are in their biomes exactly when their worldgen switches are on, and not elsewhere. |
| `everyItemHasAnEmcDecision` | EMC coverage; with `-PwithProjectE` or `-PwithItemAlchemy`, the live values. |

A Fabric development client was also run: both machines assemble, the reactor
fuel rods render their fill level, the turbine rotor spins, and the buckets
render tinted.
