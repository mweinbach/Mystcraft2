# Upstream provenance

| | |
| --- | --- |
| Project | [BuildCraft](https://github.com/BuildCraft/BuildCraft) by SpaceToad, AlexIIL and the BuildCraft team |
| Pinned revision | [`7c86626d09c4569fb2e0f458a16aaf8c3148ebaf`](https://github.com/BuildCraft/BuildCraft/tree/7c86626d09c4569fb2e0f458a16aaf8c3148ebaf) (8.0.x, Minecraft 1.12.2) |
| Code license | Mozilla Public License 2.0 ([`licenses/MPL-2.0.txt`](licenses/MPL-2.0.txt), also [`LICENSE`](LICENSE)), upstream's `LICENSE-NEW` for `common/` and `BuildCraftAPI/` |
| Asset license | Minecraft Mod Public License 1.0.1 ([`licenses/MMPL-1.0.1-assets.txt`](licenses/MMPL-1.0.1-assets.txt)), upstream's `buildcraft_resources/LICENSE.BUILDCRAFT` |

Only MPL-2.0 upstream code was used: the port follows `common/` and `BuildCraftAPI/`, never the
MMPL-licensed `src_old_license/` or `common_old_license/` trees.

This is an unofficial port and not a BuildCraft release. Both loader JARs carry this file as
`META-INF/BuildCraft-UPSTREAM.md` and both license texts under `META-INF/licenses/buildcraft/`.

## Code

The Java sources under `src/main/java/ports/buildcraft` are a rewrite of the upstream 1.12.2 classes
against Minecraft 1.19.2's official mappings. Logic, constants and formulas follow the upstream
classes, which each port class names in its Javadoc (`Upstream {@code ...}`). The main sources are:

| Port package | Upstream sources |
| --- | --- |
| `core` | `BCCoreItems`, `BCCoreBlocks`, `BlockSpring`, `api.enums.EnumSpring`, `ItemFragileFluidContainer`, `api.items.FluidItemDrops`, `lib.fluid.TankManager` drops, `BlockMarkerVolume`, `BlockMarkerPath`, `TileMarkerVolume`, `TileMarkerPath`, `ItemMarkerConnector`, `ItemVolumeBox`, `ItemWrench_Neptune`, `api.blocks.ICustomRotationHandler`, `lib.block.BlockMarkerBase`; `core.marker`: `VolumeCache`, `VolumeSubCache`, `VolumeConnection`, `PathCache`, `PathSubCache`, `PathConnection`; `core.marker.volume`: `VolumeBox`, `WorldSavedDataVolumeBoxes`, `ClientVolumeBoxes`, `MessageVolumeBoxes`, `Lock`; `BCCoreEventDist` (volume box ticks and joins); `core.statements`: `BCCoreStatements`, `CoreTriggerProvider`, `CoreActionProvider`, `BCStatement`, `StatementParamGateSideOnly`, `TriggerTrue`, `TriggerRedstoneInput`, `ActionRedstoneOutput`, `ActionMachineControl`, `TriggerInventory`, `TriggerInventoryLevel`, `TriggerFluidContainer`, `TriggerFluidContainerLevel`, `TriggerMachine`, `TriggerEnginePowerStage`; `api.tiles.IHasWork` |
| `statement` | `api.statements` (`IStatement`, `ITrigger`, `IAction`, `IStatementParameter`, `StatementParameterItemStack`, `StatementManager`, `ITriggerProvider`, `IActionProvider`, `containers.IStatementContainer`, `IRedstoneStatementContainer`, `ISidedStatementContainer`), `api.gates.IGate`, `lib.statement` (`StatementWrapper`, `TriggerWrapper`, `ActionWrapper`, `FullStatement`), `lib.misc.ColourUtil` tooltip colours |
| `lib` | `buildcraft.lib.misc.data.SimplexNoise` (Stefan Gustavson's public-domain simplex noise), `buildcraft.lib.mj.MjBattery`, `lib.misc.BlockUtil`, `lib.inventory` transactors, `lib.fluid.Tank` (including `transferStackToTank`), `lib.gui.widget.WidgetFluidTank`, `lib.misc.data.Box`/`BoxIterator`, `api.mj.MjAPI`, `api.core.IAreaProvider`, `api.tiles.IControllable`; `lib.marker`: `MarkerCache`, `MarkerSubCache`, `MarkerConnection`, `MarkerSavedData`, `lib.tile.TileMarker` |
| `energy` | `TileEngineBase_BC8`, `TileEngineRedstone_BC8`, `TileEngineStone_BC8`, `TileEngineIron_BC8`, `EnumPowerStage`, `BCEnergyFluids`, `BCEnergyRecipes`, `BCEnergyConfig`, `OilGenerator` (including the `excessiveBiomes` bonus), `OilGenStructure`, `BiomeOilDesert`, `BiomeOilOcean`, `BiomeInitializer`, `GenLayerBiomeReplacer`, `GenLayerAddOilDesert`, `GenLayerAddOilOcean`, `BCEnergyWorldGen`, `BlockSpring`, `EnumSpring`, `ContainerEngineIron_BC8`, `lib.fluid.BCFluidBlock` and Forge 1.12 `BlockFluidClassic` (gases rising) |
| `factory` | `TileMiner`, `TileMiningWell`, `TilePump`, `TileTank`, `TileDistiller_BC8`, `TileHeatExchange`, `BlockTube`, `BlockTank`, `BlockPump`, `BlockDistiller`, `BlockHeatExchange`, `ContainerDistiller` |
| `builders` | `TileQuarry`, `BlockQuarry`, `BlockFrame`, `BCBuildersConfig`, `TileFiller`, `BlockFiller`, `ContainerFiller`, `BCBuildersStatements`; `builders.filler`: `FillerStatementContext`, `FillerUtil`; `builders.snapshot.pattern`: `Pattern`, `PatternNone`, `PatternClear`, `PatternFill`, `PatternBox`, `PatternFrame`, `PatternPyramid`, `PatternStairs`, `PatternShape2d*`, `PatternSphere`, `PatternSpherePart` and the `parameter` package; `Template.FilledTemplate`, `api.filler.IFilledTemplate`; `lib.statement.FullStatement`; `builders.snapshot`: `SnapshotBuilder`, `TemplateBuilder`, `ITileForSnapshotBuilder`, `ITileForTemplateBuilder`, `Template.BuildingInfo`; `TileArchitectTable`, `BlockArchitectTable`, `ContainerArchitectTable`, `ItemSnapshot`, `BCBuildersSchematics`, `client.ClientArchitectTables`; `builders.snapshot`: `Snapshot`, `Template`, `Blueprint`, `GlobalSavedDataSnapshots`, `SchematicBlockManager`, `SchematicBlockDefault`, `SchematicBlockFluid`, `SchematicBlockAir`, `SchematicEntityManager`, `SchematicEntityDefault`, `RulesLoader`, `JsonRule`, `JsonSelector`, `RequiredExtractor*`, `ItemStackRef`, `FluidStackRef`, `NbtRef`, `NbtPath`, `EnumNbtCompareOperation`; `api.schematics` (`ISchematicBlock`, `ISchematicEntity`, `SchematicBlockContext`, `SchematicEntityContext`, the factory registries); `api.enums.EnumSnapshotType`; `TileBuilder`, `BlockBuilder`, `ContainerBuilder`, `api.enums.EnumOptionalSnapshotType`, `builders.snapshot`: `BlueprintBuilder`, `ITileForBlueprintBuilder`, `Blueprint.BuildingInfo`; `lib.misc.PositionUtil` (`getAllOnPath`), `lib.misc.StackUtil.mergeSameItems`, `lib.fluid.FluidUtilBC.mergeSameFluids`; `lib.misc.HashUtil`, `lib.misc.NBTUtilBC`, `lib.delta.DeltaInt`; `TileElectronicLibrary`, `BlockElectronicLibrary`, `ContainerElectronicLibrary`, `TileReplacer`, `BlockReplacer`, `ContainerReplacer`, `ItemSchematicSingle`, `builders.snapshot.MessageSnapshotRequest`, `MessageSnapshotResponse` |
| `silicon` | `TileLaser`, `BlockLaser`, `TileLaserTableBase`, `BlockLaserTable`, `TileAssemblyTable`, `TileAdvancedCraftingTable`, `TileIntegrationTable`, `ContainerAssemblyTable`, `ContainerAdvancedCraftingTable`, `ContainerIntegrationTable`, `EnumAssemblyRecipeState`, `BCSiliconBlocks`, `BCSiliconItems`, `BCSiliconRecipes`, `lib.recipe.IntegrationRecipeBasic`; `api.recipes`: `AssemblyRecipeBasic`, `IngredientStack`; `api.mj`: `ILaserTarget`, `ILaserTargetBlock`; `lib.tile.craft.WorkbenchCrafting`, `lib.tile.item.ItemHandlerSimple` (as `lib.SlotRange`), `lib.misc.VolumeUtil`, `lib.misc.data.AverageLong`, `api.core.SafeTimeTracker`, `lib.gui.slot` (`SlotDisplay`, `SlotPhantom`, `SlotOutput`); `silicon.gate`: `EnumGateLogic`, `EnumGateMaterial`, `EnumGateModifier`, `GateVariant`, `GateLogic`, `TriggerType`, `ActionType`, `plug.PluggableGate`, `item.ItemPluggableGate`, `item.ItemGateCopier`, `container.ContainerGate`, `BCSiliconStatements`, the gate parts of `BCSiliconItems`, `BCSiliconPlugs` and `BCSiliconRecipes`; `silicon.plug`: `plug.PluggableLens`, `plug.FilterEventHandler`, `item.ItemPluggableLens` and the lens parts of `BCSiliconItems`, `BCSiliconPlugs` and `BCSiliconRecipes`; `plug.PluggablePulsar`, `plug.PluggableTimer`, `statement.ActionPowerPulsar`, `statement.TriggerTimer` and the pulsar and timer parts of `BCSiliconItems`, `BCSiliconPlugs`, `BCSiliconStatements` and `BCSiliconRecipes`; `plug.PluggableLightSensor`, `statement.TriggerLightSensor` and the light-sensor parts of `BCSiliconItems`, `BCSiliconPlugs`, `BCSiliconStatements` and `BCSiliconRecipes`; `plug.PluggableFacade`, `item.ItemPluggableFacade`, `plug.FacadeStateManager`, `plug.FacadeInstance`, `plug.FacadeBlockStateInfo`, `plug.FacadePhasedState`, `recipe.FacadeAssemblyRecipes`, `recipe.FacadeSwapRecipe`; `lib.misc.ColourUtil.getLightHex` |
| `robotics` | `BlockZonePlanner`, `TileZonePlanner`, `ContainerZonePlanner`; `robotics.zone`: `ZonePlan`, `ZoneChunk`, `ZonePlannerMapChunk`, `ZonePlannerMapChunkKey`, `ZonePlannerMapData`, `ZonePlannerMapDataServer` and the map request and response messages; `ItemMapLocation`, `ItemPaintbrush_BC8`, `api.items.IMapLocation` |
| `transport` | `TilePipeHolder`, `BlockPipeHolder`, `PipeFlowItems`, `PipeFlowFluids`, `PipeFlowPower`, `PipeFlowStructure`, `TravellingItem`, `DelayedList`, `BCTransportPipes` and the `PipeBehaviour*` classes; `ContainerDiamondWoodPipe`, `ContainerEmzuliPipe_BC8`; for the stripes pipe, `pipe.StripesRegistry`, `api.transport.IStripesActivator`, `IStripesHandlerItem`, `IStripesRegistry` and `transport.stripes` (`PipeExtensionManager` and the `StripesHandler*` classes `BCTransportRegistries` registers); for `transport.pluggable`, `api.transport.pluggable` (`PipePluggable`, `PluggableDefinition`), `api.transport.IItemPluggable`, `pipe.PluggableHolder`, `lib.item.ItemPluggableSimple`, `plug.PluggableBlocker`, `plug.PluggablePowerAdaptor`, `BCTransportPlugs`, `BCTransportItems` (`plugBlocker`, `plugPowerAdaptor`); `transport.statements`: `BCTransportStatements`, `TriggerProviderPipes`, `ActionProviderPipes`, `TriggerItemsTraversing`, `TriggerFluidsTraversing`, `TriggerPowerRequested`, `ActionPipeDirection`, `ActionExtractionPreset`, `TriggerPipeSignal`, `ActionPipeSignal`, `TriggerParameterSignal`, `ActionParameterSignal`; `TilePipeHolder`'s redstone output and `BlockPipeHolder`'s signal methods; for `transport.wire`, `ItemWire`, `WireManager`, `api.transport.EnumWirePart`, `EnumWireBetween`, `WireNode`, `WireSystem`, `WorldSavedDataWireSystems`, `api.transport.IWireEmitter`, `BCTransportItems.wire` and `BlockPipeHolder`'s wire placement and removal; the item colour carried by `TravellingItem` and `PipeEventItem` (`TryInsert`, `OnInsert`, `SideCheck`, `ReachEnd`), and `api.transport.IInjectable`'s colour parameter |
| `client` | `RenderEngine_BC8`, `PipeFlowRenderer*`, `RenderTank`, `RenderTube`, `RenderQuarry`, `RenderDistiller`, `RenderHeatExchange`, `FluidRenderer`, `GuiDiamondPipe`, `GuiDiamondWoodPipe`, `GuiEmzuliPipe_BC8`, `GuiDistiller`, `GuiEngine*`, `lib.client.render.laser` (`LaserRenderer_BC8`, `LaserData_BC8`, `LaserBoxRenderer`, `LaserContext`), `BuildCraftLaserManager`, `lib.client.render.MarkerRenderer`, `RenderMarkerVolume`, `RenderTickListener` (possible marker connections), `RenderVolumeBoxes`, `RenderFiller`, `RenderSnapshotBuilder`, `RenderBuilder`, `GuiBuilder`, `GuiFiller` and its `filler.json` layout, `GuiArchitectTable`, `RenderArchitectTable`, `RenderArchitectTables`, `GuiElectronicLibrary`, `GuiReplacer`, `builders.snapshot.ClientSnapshots` (snapshot previews), `BCBuildersEventDist.onRenderTooltipPostText`, `lib.gui.statement` (`GuiElementStatement`, `GuiElementStatementSource`, `GuiElementStatementVariant`), `lib.gui.ledger.Ledger_Neptune`, `RenderLaser`, `GuiAssemblyTable`, `GuiAdvancedCraftingTable`, `GuiIntegrationTable`, `LedgerTablePower`, `lib.gui.GuiIcon.drawCutInside`, `PlugBakerSimple` and upstream's `models/plugs` (`blocker`, `power_adapter`), `PlugBakerFacade`, `ModelFacadeItem`; `GuiGate` and its `gate.json` layout, `lib.gui.statement` (`GuiElementStatement`, `GuiElementStatementParam`, `GuiElementStatementDrag`, `ParameterRenderer`), `PlugGateBaker` and `models/plugs/gate.json` and `gate_dynamic.json`, `PipeWireRenderer`, `PlugBakerLens` and `models/plugs/lens.json` and `filter.json`, `PipeFlowRendererItems`' painted item boxes, `PlugPulsarRenderer` and `models/plugs/pulsar_static.json`, `pulsar_dynamic.json`, `timer.json` and `light_sensor.json`, and the filler screen's machine-control icon, `GuiZonePlanner`, `RenderZonePlanner` |

`src/forge` and `src/fabric` contain only loader adapters written for this port (registration,
capabilities, Transfer API and Team Reborn Energy bridges, client hooks).

## Assets and data

`tools/generate_assets.py --upstream <checkout>` produces every file under `src/main/resources`
except the builder rules and the EMC data:

- Textures are copied from upstream `buildcraft_resources`; fluid sprites are recoloured with the
  light and dark colours from upstream `BCEnergyFluids`, as upstream does at runtime.
- Block and item models, blockstates, language entries, loot tables, recipes and tags are generated
  from upstream's JSON definitions and recipe registrations, translated to 1.19.2 formats with both
  `c:` and `forge:` tags.
- Upstream 8.0 has no recipes for the filler, builder, electronic library and replacer. The generator
  writes the BuildCraft 7.1 shapes for the first three (recipe data only; no 7.1 code is used) and a
  recipe in the same style for the replacer, plus upstream 8.0's commented-out single block schematic
  recipe (`BCBuildersRecipes`). It no longer writes upstream's "Not usable in survival" tips for the
  filler, builder, electronic library and architect table.
- `data/buildcraft/tags/blocks/schematic_blacklist.json` is not upstream: blocks no builder places from a
  blueprint, whatever it says (see `SchematicSafety` and PARITY's "Untrusted snapshots").
- The silicon chipsets' assembly recipes restate upstream `BCSiliconRecipes`' code registrations (also shipped as
  upstream's `assembly_recipes_examples`) as `buildcraft:assembly` datapack recipes.
- The pipe wire's assembly recipes restate upstream `BCTransportRecipes`' code registrations the same way, and the
  lens and filter recipes those of `BCSiliconRecipes`.
- Lens and filter item models are upstream's `plugs/lens.json`, with `plugs/filter.json`'s bars added for filters, each
  face turned into a vanilla model face with the UVs and rotation upstream's model loader gave it.
- The facade assembly and hollow-swap recipes restate upstream `FacadeAssemblyRecipes` and `FacadeSwapRecipe` as
  `buildcraft:facade_assembly` and `buildcraft:facade_swap`. The item model is `builtin/entity`; `PlugBakerFacade`
  draws the copied block.
- The pipe pulsar and timer assembly recipes restate upstream `BCSiliconRecipes`. The timer item is upstream's
  `plugs/timer.json`. The pulsar item is the switched-off state of `pulsar_static.json` and `pulsar_dynamic.json`
  (stage 0, dark lights), which is what upstream's `setModelVariablesForItem` showed.
- The oil desert and oil ocean biome JSON start from vanilla 1.19.2's desert and ocean, which
  `tools/vanilla/worldgen/biome` holds as dumped by the Minecraft 1.19.2 server's `--reports`
  option, as upstream's oil biomes extended `BiomeDesert` and `BiomeOcean`.

`tools/generate_builder_rules.py` writes the builder rules under
`assets/buildcraft/compat/buildcraft/builders`: upstream's
`buildcraftbuilders/compat/buildcraft/builders/vanilla` rule files restated for 1.19.2 ids, with each block
family read from Minecraft 1.19.2's block state files, plus `port_additions.json` for 1.19.2 blocks upstream's
files had no counterpart for.

`src/main/resources/data/buildcraft/{pe_custom_conversions,default_emcs}` are generated by
`scripts/emc.py` from [`emc/policy.json`](emc/policy.json); see [`emc/README.md`](emc/README.md).

Team Reborn Energy 2.3.0 (MIT) is bundled inside the Fabric JAR as a nested library; it is not
modified. MixinExtras 0.4.1 (MIT, [`licenses/mixinextras-LICENSE.txt`](licenses/mixinextras-LICENSE.txt)) is
bundled the same way for the Fabric-only mixins; the Forge JAR uses none.
