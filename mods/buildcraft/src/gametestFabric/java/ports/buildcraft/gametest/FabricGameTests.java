package ports.buildcraft.gametest;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import ports.buildcraft.BuildCraft;

/** The shared {@link BCTests}, {@link FillerTests}, {@link ArchitectTests}, {@link BuilderTests}, {@link LibraryTests}, {@link SiliconTests}, {@link GateTests}, {@link WireTests}, {@link LensTests}, {@link PulsarTimerTests}, {@link SensorControlTests}, {@link FacadeTests}, {@link ZonePlannerTests} and {@link RecipeAuditTests} run on Fabric, with the same timeouts as the Forge wrappers. */
public final class FabricGameTests implements FabricGameTest {
    /** Fabric uses the template name verbatim and reads {@code data/buildcraft/gametest/structures/arena.snbt}. */
    static final String TEMPLATE = BuildCraft.MOD_ID + ":" + BCTests.TEMPLATE;

    static {
        // TestProtection's stand-in protection mod: refuses Fabric's block break event where a test refuses it.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !TestProtection.refuses(pos, player));
        TestProtection.installed("fabric");
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void mjConversionRatio(GameTestHelper helper) { BCTests.mjConversionRatio(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void fluidViscosityScale(GameTestHelper helper) { BCTests.fluidViscosityScale(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void guiTanksTradeContainers(GameTestHelper helper) { BCTests.guiTanksTradeContainers(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public void waterSpringRefills(GameTestHelper helper) { BCTests.waterSpringRefills(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public void gaseousFluidsFlowUp(GameTestHelper helper) { BCTests.gaseousFluidsFlowUp(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public void redstoneEngineDrivesWoodenPipe(GameTestHelper helper) { BCTests.redstoneEngineDrivesWoodenPipe(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public void stirlingEngineBurnsCoal(GameTestHelper helper) { BCTests.stirlingEngineBurnsCoal(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public void stirlingEngineKeepsFuelRemainder(GameTestHelper helper) { BCTests.stirlingEngineKeepsFuelRemainder(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public void combustionEngineBurnsFuel(GameTestHelper helper) { BCTests.combustionEngineBurnsFuel(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public void engineTrunkFollowsHeat(GameTestHelper helper) { BCTests.engineTrunkFollowsHeat(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public void redstoneEngineWarmsButNeverOverheats(GameTestHelper helper) { BCTests.redstoneEngineWarmsButNeverOverheats(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public void tankColumnFillsFromTheBottom(GameTestHelper helper) { BCTests.tankColumnFillsFromTheBottom(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public void pumpDrainsSourceIntoTank(GameTestHelper helper) { BCTests.pumpDrainsSourceIntoTank(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 800)
    public void pumpDrainsIrregularFinitePool(GameTestHelper helper) { BCTests.pumpDrainsIrregularFinitePool(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 800)
    public void pumpTreatsSquarePoolAsInfinite(GameTestHelper helper) { BCTests.pumpTreatsSquarePoolAsInfinite(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public void miningWellMinesIntoChest(GameTestHelper helper) { BCTests.miningWellMinesIntoChest(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public void quarryBuildsFrameAndDigs(GameTestHelper helper) { BCTests.quarryBuildsFrameAndDigs(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void landMarksConnectIntoBox(GameTestHelper helper) { BCTests.landMarksConnectIntoBox(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void pathMarksFormPathsAndLoops(GameTestHelper helper) { BCTests.pathMarksFormPathsAndLoops(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void markerConnectorJoinsMarks(GameTestHelper helper) { BCTests.markerConnectorJoinsMarks(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void volumeBoxItemMakesBoxes(GameTestHelper helper) { BCTests.volumeBoxItemMakesBoxes(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void markerConnectorEditsVolumeBoxes(GameTestHelper helper) { BCTests.markerConnectorEditsVolumeBoxes(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void lockedVolumeBoxesResistTheConnector(GameTestHelper helper) { BCTests.lockedVolumeBoxesResistTheConnector(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void markersRotateAndNeedSupport(GameTestHelper helper) { BCTests.markersRotateAndNeedSupport(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void quarryTakesLandMarkArea(GameTestHelper helper) { BCTests.quarryTakesLandMarkArea(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void quarryFindsLandMarkBoxEdge(GameTestHelper helper) { BCTests.quarryFindsLandMarkBoxEdge(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void fillerPatternsMakeTemplates(GameTestHelper helper) { FillerTests.fillerPatternsMakeTemplates(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void patternStatementKeepsParameters(GameTestHelper helper) { FillerTests.patternStatementKeepsParameters(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void fillerTakesVolumeBox(GameTestHelper helper) { FillerTests.fillerTakesVolumeBox(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void fillerTakesLandMarkArea(GameTestHelper helper) { FillerTests.fillerTakesLandMarkArea(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void fillerSettingsAndSaving(GameTestHelper helper) { FillerTests.fillerSettingsAndSaving(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public void fillerBuildsBox(GameTestHelper helper) { FillerTests.fillerBuildsBox(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public void fillerClearsOnlyWhenExcavating(GameTestHelper helper) { FillerTests.fillerClearsOnlyWhenExcavating(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public void fillerKeepsToItsPowerAndCountsQueuedWork(GameTestHelper helper) { FillerTests.fillerKeepsToItsPowerAndCountsQueuedWork(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public void fillerRespectsRefusedBreaks(GameTestHelper helper) { FillerTests.fillerRespectsRefusedBreaks(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public void architectScansTemplate(GameTestHelper helper) { ArchitectTests.architectScansTemplate(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public void architectScansBlueprint(GameTestHelper helper) { ArchitectTests.architectScansBlueprint(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void architectNeedsAnArea(GameTestHelper helper) { ArchitectTests.architectNeedsAnArea(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void architectMenuNameAndSaving(GameTestHelper helper) { ArchitectTests.architectMenuNameAndSaving(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void snapshotStorageRoundTrip(GameTestHelper helper) { ArchitectTests.snapshotStorageRoundTrip(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void builderRulesCover119Blocks(GameTestHelper helper) { ArchitectTests.builderRulesCover119Blocks(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void schematicsBuildBack(GameTestHelper helper) { ArchitectTests.schematicsBuildBack(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void architectRecipes(GameTestHelper helper) { ArchitectTests.architectRecipes(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public void builderBuildsRotatedTemplate(GameTestHelper helper) { BuilderTests.builderBuildsRotatedTemplate(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public void builderBuildsBlueprint(GameTestHelper helper) { BuilderTests.builderBuildsBlueprint(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public void builderPlacesFluidsFromTanks(GameTestHelper helper) { BuilderTests.builderPlacesFluidsFromTanks(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public void builderSpawnsAndRemovesEntities(GameTestHelper helper) { BuilderTests.builderSpawnsAndRemovesEntities(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public void builderFollowsPathMarks(GameTestHelper helper) { BuilderTests.builderFollowsPathMarks(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public void builderSkipsPathStepsItCannotPowerAtOnce(GameTestHelper helper) { BuilderTests.builderSkipsPathStepsItCannotPowerAtOnce(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void builderMenuSavingAndDrops(GameTestHelper helper) { BuilderTests.builderMenuSavingAndDrops(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public void libraryCopiesSnapshotsDownAndUp(GameTestHelper helper) { LibraryTests.libraryCopiesSnapshotsDownAndUp(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void libraryRefusesForgedUploads(GameTestHelper helper) { LibraryTests.libraryRefusesForgedUploads(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void libraryTakesOnlyAskedForUploadsWithinQuota(GameTestHelper helper) { LibraryTests.libraryTakesOnlyAskedForUploadsWithinQuota(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void forgedBlocksAreRefused(GameTestHelper helper) { SchematicSafetyTests.forgedBlocksAreRefused(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void forgedBlockEntityDataIsSanitized(GameTestHelper helper) { SchematicSafetyTests.forgedBlockEntityDataIsSanitized(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void forgedEntitiesAreRefused(GameTestHelper helper) { SchematicSafetyTests.forgedEntitiesAreRefused(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void schematicDataIsBounded(GameTestHelper helper) { SchematicSafetyTests.schematicDataIsBounded(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void libraryMenuSidesSavingAndDrops(GameTestHelper helper) { LibraryTests.libraryMenuSidesSavingAndDrops(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void replacerSwapsBlueprintBlocks(GameTestHelper helper) { LibraryTests.replacerSwapsBlueprintBlocks(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void singleSchematicRecordsAndPlaces(GameTestHelper helper) { LibraryTests.singleSchematicRecordsAndPlaces(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void oilWellsAreRareAndDeterministic(GameTestHelper helper) { BCTests.oilWellsAreRareAndDeterministic(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void oilBiomesHaveThirtyTimesTheWells(GameTestHelper helper) { BCTests.oilBiomesHaveThirtyTimesTheWells(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void oilBiomesReplaceDesertsAndOceans(GameTestHelper helper) { BCTests.oilBiomesReplaceDesertsAndOceans(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void lightFuelChainFromOil(GameTestHelper helper) { BCTests.lightFuelChainFromOil(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public void combustionEngineBurnsOilIntoResidue(GameTestHelper helper) { BCTests.combustionEngineBurnsOilIntoResidue(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public void distillerSplitsOil(GameTestHelper helper) { BCTests.distillerSplitsOil(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void distillerRefundsBlockedProgress(GameTestHelper helper) { BCTests.distillerRefundsBlockedProgress(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public void heatExchangerFormsRows(GameTestHelper helper) { BCTests.heatExchangerFormsRows(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public void heatExchangerHeatsOilWithLava(GameTestHelper helper) { BCTests.heatExchangerHeatsOilWithLava(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void fragileShardsSplitFluid(GameTestHelper helper) { BCTests.fragileShardsSplitFluid(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void brokenMachinesDropFluidShards(GameTestHelper helper) { BCTests.brokenMachinesDropFluidShards(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void shardEmptiesIntoTank(GameTestHelper helper) { BCTests.shardEmptiesIntoTank(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public void voidPipeDestroysItems(GameTestHelper helper) { BCTests.voidPipeDestroysItems(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public void fluidPipesFillTank(GameTestHelper helper) { BCTests.fluidPipesFillTank(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void diamondFluidPipeDefinition(GameTestHelper helper) { BCTests.diamondFluidPipeDefinition(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void diamondFluidFiltersFaces(GameTestHelper helper) { BCTests.diamondFluidFiltersFaces(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public void diamondFluidPipeRoutesFilteredFluid(GameTestHelper helper) { BCTests.diamondFluidPipeRoutesFilteredFluid(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public void kinesisPipesCarryEnginePower(GameTestHelper helper) { BCTests.kinesisPipesCarryEnginePower(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void kinesisLimiterPipeDefinitions(GameTestHelper helper) { BCTests.kinesisLimiterPipeDefinitions(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public void ironKinesisPipeCapsDeliveredPower(GameTestHelper helper) { BCTests.ironKinesisPipeCapsDeliveredPower(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public void ironKinesisPipeFeedsEveryRequestingFace(GameTestHelper helper) { BCTests.ironKinesisPipeFeedsEveryRequestingFace(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public void diamondKinesisPipeCarriesMoreThanBase(GameTestHelper helper) { BCTests.diamondKinesisPipeCarriesMoreThanBase(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public void kinesisSimulatedTransferMatchesReal(GameTestHelper helper) { BCTests.kinesisSimulatedTransferMatchesReal(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public void laserPowersTablesInSight(GameTestHelper helper) { SiliconTests.laserPowersTablesInSight(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void assemblyTableMakesSavedChipsets(GameTestHelper helper) { SiliconTests.assemblyTableMakesSavedChipsets(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void assemblyTablePagesRecipesAndDropsStaleOutputs(GameTestHelper helper) {
        SiliconTests.assemblyTablePagesRecipesAndDropsStaleOutputs(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void plugBlocksConnectionsAndBreaksOff(GameTestHelper helper) { BCTests.plugBlocksConnectionsAndBreaksOff(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void cancelledBreakKeepsPluggable(GameTestHelper helper) { BCTests.cancelledBreakKeepsPluggable(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public void powerAdaptorDrivesWoodenPipeThroughBlockedFace(GameTestHelper helper) {
        BCTests.powerAdaptorDrivesWoodenPipeThroughBlockedFace(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void structurePipesConnectOnlyToEachOther(GameTestHelper helper) { BCTests.structurePipesConnectOnlyToEachOther(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void gatePlacesAndKeepsItsConfiguration(GameTestHelper helper) { GateTests.gatePlacesAndKeepsItsConfiguration(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public void gateTurnsRedstoneInputIntoSignal(GameTestHelper helper) { GateTests.gateTurnsRedstoneInputIntoSignal(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void redstoneWireJoinsOnlyTheGateFace(GameTestHelper helper) { GateTests.redstoneWireJoinsOnlyTheGateFace(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void gateGroupsPairsByLogic(GameTestHelper helper) { GateTests.gateGroupsPairsByLogic(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void gateCopierCopiesPastesAndClears(GameTestHelper helper) { GateTests.gateCopierCopiesPastesAndClears(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void gateMenuOnlyAcceptsWhatTheGateCanUse(GameTestHelper helper) { GateTests.gateMenuOnlyAcceptsWhatTheGateCanUse(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void gateTurnsIronPipeAndReadsInventories(GameTestHelper helper) { GateTests.gateTurnsIronPipeAndReadsInventories(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void gateAndWrenchSetKinesisPowerLimit(GameTestHelper helper) { GateTests.gateAndWrenchSetKinesisPowerLimit(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void wirePlacesJoinsAndSaves(GameTestHelper helper) { WireTests.wirePlacesJoinsAndSaves(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void wireBreaksOffAndDrops(GameTestHelper helper) { WireTests.wireBreaksOffAndDrops(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public void pipeSignalCarriesAGateSignal(GameTestHelper helper) { WireTests.pipeSignalCarriesAGateSignal(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void wireSystemsFollowPluggables(GameTestHelper helper) { WireTests.wireSystemsFollowPluggables(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void lensPlacesSyncsAndSaves(GameTestHelper helper) { LensTests.lensPlacesSyncsAndSaves(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public void lensesPaintAndFiltersSort(GameTestHelper helper) { LensTests.lensesPaintAndFiltersSort(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public void neighbourFilterSteersAndClearLensWashes(GameTestHelper helper) { LensTests.neighbourFilterSteersAndClearLensWashes(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public void diamondPipeSplitKeepsFilterPriority(GameTestHelper helper) { LensTests.diamondPipeSplitKeepsFilterPriority(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void pulsarAndTimerPlaceSaveAndSync(GameTestHelper helper) { PulsarTimerTests.pulsarAndTimerPlaceSaveAndSync(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void pulsarPulsesEveryTwentyTicks(GameTestHelper helper) { PulsarTimerTests.pulsarPulsesEveryTwentyTicks(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void singlePulseRetriesUntilThePipeCanTakeIt(GameTestHelper helper) { PulsarTimerTests.singlePulseRetriesUntilThePipeCanTakeIt(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 1000)
    public void timerTriggersAndPulsarActions(GameTestHelper helper) { PulsarTimerTests.timerTriggersAndPulsarActions(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void lightSensorPlacesAndReadsLight(GameTestHelper helper) { SensorControlTests.lightSensorPlacesAndReadsLight(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void machineControlOfferedOnlyForTheFiller(GameTestHelper helper) { SensorControlTests.machineControlOfferedOnlyForTheFiller(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void gateSetsFillerPatternAndSkipsFirstParameter(GameTestHelper helper) { SensorControlTests.gateSetsFillerPatternAndSkipsFirstParameter(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public void gateTurnsTheFillerOffAndOn(GameTestHelper helper) { SensorControlTests.gateTurnsTheFillerOffAndOn(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void fillerControlModeSurvivesSaving(GameTestHelper helper) { SensorControlTests.fillerControlModeSurvivesSaving(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void advancedCraftingTableCraftsBlueprint(GameTestHelper helper) { SiliconTests.advancedCraftingTableCraftsBlueprint(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void integrationTableIntegratesAroundCentre(GameTestHelper helper) { SiliconTests.integrationTableIntegratesAroundCentre(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void facadeRules(GameTestHelper helper) { FacadeTests.facadeRules(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void assemblyAndSwap(GameTestHelper helper) { FacadeTests.assemblyAndSwap(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void fullFacadeBlocksAndHollowStaysOpen(GameTestHelper helper) { FacadeTests.fullFacadeBlocksAndHollowStaysOpen(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void facadeQuadsStayInTheShell(GameTestHelper helper) { FacadeTests.facadeQuadsStayInTheShell(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void zonePlanStoresColumns(GameTestHelper helper) { ZonePlannerTests.zonePlanStoresColumns(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void mapLocationRecordsAndCleans(GameTestHelper helper) { ZonePlannerTests.mapLocationRecordsAndCleans(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void zonePlannerImportsAndExports(GameTestHelper helper) { ZonePlannerTests.zonePlannerImportsAndExports(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void zonePlannerMenuRejectsAndDrops(GameTestHelper helper) { ZonePlannerTests.zonePlannerMenuRejectsAndDrops(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void zonePlannerMapColoursTheTopBlock(GameTestHelper helper) { ZonePlannerTests.zonePlannerMapColoursTheTopBlock(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void paintbrushAndZonePlannerRecipes(GameTestHelper helper) { ZonePlannerTests.paintbrushAndZonePlannerRecipes(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void buildersMachineRecipes(GameTestHelper helper) { RecipeAuditTests.buildersMachineRecipes(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void everyItemCraftableOrListed(GameTestHelper helper) { RecipeAuditTests.everyItemCraftableOrListed(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public void lapisPipePaintsItemsAndWrenchCyclesColour(GameTestHelper helper) { SortingPipeTests.lapisPipePaintsItemsAndWrenchCyclesColour(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public void daizuliPipeRoutesItsColourToItsFace(GameTestHelper helper) { SortingPipeTests.daizuliPipeRoutesItsColourToItsFace(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public void obsidianPipeSucksItemsInRange(GameTestHelper helper) { SortingPipeTests.obsidianPipeSucksItemsInRange(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public void diamondWoodPipeExtractsByFilterMode(GameTestHelper helper) { ExtractionPipeTests.diamondWoodPipeExtractsByFilterMode(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void diamondWoodFluidPipeHasNoRoundRobin(GameTestHelper helper) { ExtractionPipeTests.diamondWoodFluidPipeHasNoRoundRobin(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public void emzuliPipeExtractsPerPresetAndPaints(GameTestHelper helper) { ExtractionPipeTests.emzuliPipeExtractsPerPresetAndPaints(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public void stripesPipePlacesBreaksAndDrops(GameTestHelper helper) { StripesPipeTests.stripesPipePlacesBreaksAndDrops(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public void stripesPipeTurnsByGate(GameTestHelper helper) { StripesPipeTests.stripesPipeTurnsByGate(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public void emzuliPipeMovesOnFromClearedFilter(GameTestHelper helper) { ExtractionPipeTests.emzuliPipeMovesOnFromClearedFilter(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public void stripesPipeLaysTwoPipesInOneTick(GameTestHelper helper) { StripesPipeTests.stripesPipeLaysTwoPipesInOneTick(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public void stripesPipeRetractsTwiceInOneTick(GameTestHelper helper) { StripesPipeTests.stripesPipeRetractsTwiceInOneTick(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public void stripesPipeRespectsRefusedBreak(GameTestHelper helper) { StripesPipeTests.stripesPipeRespectsRefusedBreak(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public void stripesPipeRespectsRefusedExtension(GameTestHelper helper) { StripesPipeTests.stripesPipeRespectsRefusedExtension(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public void stripesPipeStaysAtBuildLimit(GameTestHelper helper) { StripesPipeTests.stripesPipeStaysAtBuildLimit(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void recipesAndTags(GameTestHelper helper) { BCTests.recipesAndTags(helper); }
}
