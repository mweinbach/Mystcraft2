package ports.buildcraft.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.buildcraft.BuildCraft;

/** The shared {@link BCTests}, {@link FillerTests}, {@link ArchitectTests}, {@link BuilderTests}, {@link LibraryTests}, {@link SiliconTests}, {@link GateTests}, {@link WireTests}, {@link LensTests}, {@link PulsarTimerTests}, {@link SensorControlTests}, {@link FacadeTests} and {@link ZonePlannerTests} run on Forge. */
@GameTestHolder(BuildCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForgeGameTests {
    private ForgeGameTests() {}

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void mjConversionRatio(GameTestHelper helper) { BCTests.mjConversionRatio(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void fluidViscosityScale(GameTestHelper helper) { BCTests.fluidViscosityScale(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void guiTanksTradeContainers(GameTestHelper helper) { BCTests.guiTanksTradeContainers(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 100)
    public static void waterSpringRefills(GameTestHelper helper) { BCTests.waterSpringRefills(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 200)
    public static void gaseousFluidsFlowUp(GameTestHelper helper) { BCTests.gaseousFluidsFlowUp(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 1200)
    public static void redstoneEngineDrivesWoodenPipe(GameTestHelper helper) { BCTests.redstoneEngineDrivesWoodenPipe(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 200)
    public static void stirlingEngineBurnsCoal(GameTestHelper helper) { BCTests.stirlingEngineBurnsCoal(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 300)
    public static void combustionEngineBurnsFuel(GameTestHelper helper) { BCTests.combustionEngineBurnsFuel(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 100)
    public static void engineTrunkFollowsHeat(GameTestHelper helper) { BCTests.engineTrunkFollowsHeat(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 500)
    public static void redstoneEngineWarmsButNeverOverheats(GameTestHelper helper) { BCTests.redstoneEngineWarmsButNeverOverheats(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 100)
    public static void tankColumnFillsFromTheBottom(GameTestHelper helper) { BCTests.tankColumnFillsFromTheBottom(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 400)
    public static void pumpDrainsSourceIntoTank(GameTestHelper helper) { BCTests.pumpDrainsSourceIntoTank(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 800)
    public static void pumpDrainsIrregularFinitePool(GameTestHelper helper) { BCTests.pumpDrainsIrregularFinitePool(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 800)
    public static void pumpTreatsSquarePoolAsInfinite(GameTestHelper helper) { BCTests.pumpTreatsSquarePoolAsInfinite(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 400)
    public static void miningWellMinesIntoChest(GameTestHelper helper) { BCTests.miningWellMinesIntoChest(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 1200)
    public static void quarryBuildsFrameAndDigs(GameTestHelper helper) { BCTests.quarryBuildsFrameAndDigs(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void landMarksConnectIntoBox(GameTestHelper helper) { BCTests.landMarksConnectIntoBox(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void pathMarksFormPathsAndLoops(GameTestHelper helper) { BCTests.pathMarksFormPathsAndLoops(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void markerConnectorJoinsMarks(GameTestHelper helper) { BCTests.markerConnectorJoinsMarks(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void volumeBoxItemMakesBoxes(GameTestHelper helper) { BCTests.volumeBoxItemMakesBoxes(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void markerConnectorEditsVolumeBoxes(GameTestHelper helper) { BCTests.markerConnectorEditsVolumeBoxes(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void lockedVolumeBoxesResistTheConnector(GameTestHelper helper) { BCTests.lockedVolumeBoxesResistTheConnector(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void markersRotateAndNeedSupport(GameTestHelper helper) { BCTests.markersRotateAndNeedSupport(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void quarryTakesLandMarkArea(GameTestHelper helper) { BCTests.quarryTakesLandMarkArea(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void quarryFindsLandMarkBoxEdge(GameTestHelper helper) { BCTests.quarryFindsLandMarkBoxEdge(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void fillerPatternsMakeTemplates(GameTestHelper helper) { FillerTests.fillerPatternsMakeTemplates(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void patternStatementKeepsParameters(GameTestHelper helper) { FillerTests.patternStatementKeepsParameters(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void fillerTakesVolumeBox(GameTestHelper helper) { FillerTests.fillerTakesVolumeBox(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void fillerTakesLandMarkArea(GameTestHelper helper) { FillerTests.fillerTakesLandMarkArea(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void fillerSettingsAndSaving(GameTestHelper helper) { FillerTests.fillerSettingsAndSaving(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 400)
    public static void fillerBuildsBox(GameTestHelper helper) { FillerTests.fillerBuildsBox(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 600)
    public static void fillerClearsOnlyWhenExcavating(GameTestHelper helper) { FillerTests.fillerClearsOnlyWhenExcavating(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 600)
    public static void fillerKeepsToItsPowerAndCountsQueuedWork(GameTestHelper helper) { FillerTests.fillerKeepsToItsPowerAndCountsQueuedWork(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 100)
    public static void architectScansTemplate(GameTestHelper helper) { ArchitectTests.architectScansTemplate(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 100)
    public static void architectScansBlueprint(GameTestHelper helper) { ArchitectTests.architectScansBlueprint(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void architectNeedsAnArea(GameTestHelper helper) { ArchitectTests.architectNeedsAnArea(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void architectMenuNameAndSaving(GameTestHelper helper) { ArchitectTests.architectMenuNameAndSaving(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void snapshotStorageRoundTrip(GameTestHelper helper) { ArchitectTests.snapshotStorageRoundTrip(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void builderRulesCover119Blocks(GameTestHelper helper) { ArchitectTests.builderRulesCover119Blocks(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void schematicsBuildBack(GameTestHelper helper) { ArchitectTests.schematicsBuildBack(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void architectRecipes(GameTestHelper helper) { ArchitectTests.architectRecipes(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 200)
    public static void builderBuildsRotatedTemplate(GameTestHelper helper) { BuilderTests.builderBuildsRotatedTemplate(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 400)
    public static void builderBuildsBlueprint(GameTestHelper helper) { BuilderTests.builderBuildsBlueprint(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 200)
    public static void builderPlacesFluidsFromTanks(GameTestHelper helper) { BuilderTests.builderPlacesFluidsFromTanks(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 300)
    public static void builderSpawnsAndRemovesEntities(GameTestHelper helper) { BuilderTests.builderSpawnsAndRemovesEntities(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 400)
    public static void builderFollowsPathMarks(GameTestHelper helper) { BuilderTests.builderFollowsPathMarks(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 400)
    public static void builderSkipsPathStepsItCannotPowerAtOnce(GameTestHelper helper) { BuilderTests.builderSkipsPathStepsItCannotPowerAtOnce(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void builderMenuSavingAndDrops(GameTestHelper helper) { BuilderTests.builderMenuSavingAndDrops(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 100)
    public static void libraryCopiesSnapshotsDownAndUp(GameTestHelper helper) { LibraryTests.libraryCopiesSnapshotsDownAndUp(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void libraryMenuSidesSavingAndDrops(GameTestHelper helper) { LibraryTests.libraryMenuSidesSavingAndDrops(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void replacerSwapsBlueprintBlocks(GameTestHelper helper) { LibraryTests.replacerSwapsBlueprintBlocks(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void singleSchematicRecordsAndPlaces(GameTestHelper helper) { LibraryTests.singleSchematicRecordsAndPlaces(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void oilWellsAreRareAndDeterministic(GameTestHelper helper) { BCTests.oilWellsAreRareAndDeterministic(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void oilBiomesHaveThirtyTimesTheWells(GameTestHelper helper) { BCTests.oilBiomesHaveThirtyTimesTheWells(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void oilBiomesReplaceDesertsAndOceans(GameTestHelper helper) { BCTests.oilBiomesReplaceDesertsAndOceans(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void lightFuelChainFromOil(GameTestHelper helper) { BCTests.lightFuelChainFromOil(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 600)
    public static void combustionEngineBurnsOilIntoResidue(GameTestHelper helper) { BCTests.combustionEngineBurnsOilIntoResidue(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 400)
    public static void distillerSplitsOil(GameTestHelper helper) { BCTests.distillerSplitsOil(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void distillerRefundsBlockedProgress(GameTestHelper helper) { BCTests.distillerRefundsBlockedProgress(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 100)
    public static void heatExchangerFormsRows(GameTestHelper helper) { BCTests.heatExchangerFormsRows(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 400)
    public static void heatExchangerHeatsOilWithLava(GameTestHelper helper) { BCTests.heatExchangerHeatsOilWithLava(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void fragileShardsSplitFluid(GameTestHelper helper) { BCTests.fragileShardsSplitFluid(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void brokenMachinesDropFluidShards(GameTestHelper helper) { BCTests.brokenMachinesDropFluidShards(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void shardEmptiesIntoTank(GameTestHelper helper) { BCTests.shardEmptiesIntoTank(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 400)
    public static void voidPipeDestroysItems(GameTestHelper helper) { BCTests.voidPipeDestroysItems(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 600)
    public static void fluidPipesFillTank(GameTestHelper helper) { BCTests.fluidPipesFillTank(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void diamondFluidPipeDefinition(GameTestHelper helper) { BCTests.diamondFluidPipeDefinition(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void diamondFluidFiltersFaces(GameTestHelper helper) { BCTests.diamondFluidFiltersFaces(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 400)
    public static void diamondFluidPipeRoutesFilteredFluid(GameTestHelper helper) { BCTests.diamondFluidPipeRoutesFilteredFluid(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 400)
    public static void kinesisPipesCarryEnginePower(GameTestHelper helper) { BCTests.kinesisPipesCarryEnginePower(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void kinesisLimiterPipeDefinitions(GameTestHelper helper) { BCTests.kinesisLimiterPipeDefinitions(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 240)
    public static void ironKinesisPipeCapsDeliveredPower(GameTestHelper helper) { BCTests.ironKinesisPipeCapsDeliveredPower(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 100)
    public static void ironKinesisPipeFeedsEveryRequestingFace(GameTestHelper helper) { BCTests.ironKinesisPipeFeedsEveryRequestingFace(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 100)
    public static void diamondKinesisPipeCarriesMoreThanBase(GameTestHelper helper) { BCTests.diamondKinesisPipeCarriesMoreThanBase(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 100)
    public static void kinesisSimulatedTransferMatchesReal(GameTestHelper helper) { BCTests.kinesisSimulatedTransferMatchesReal(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 60)
    public static void laserPowersTablesInSight(GameTestHelper helper) { SiliconTests.laserPowersTablesInSight(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void assemblyTableMakesSavedChipsets(GameTestHelper helper) { SiliconTests.assemblyTableMakesSavedChipsets(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void assemblyTablePagesRecipesAndDropsStaleOutputs(GameTestHelper helper) {
        SiliconTests.assemblyTablePagesRecipesAndDropsStaleOutputs(helper);
    }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void plugBlocksConnectionsAndBreaksOff(GameTestHelper helper) { BCTests.plugBlocksConnectionsAndBreaksOff(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 1200)
    public static void powerAdaptorDrivesWoodenPipeThroughBlockedFace(GameTestHelper helper) {
        BCTests.powerAdaptorDrivesWoodenPipeThroughBlockedFace(helper);
    }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void structurePipesConnectOnlyToEachOther(GameTestHelper helper) { BCTests.structurePipesConnectOnlyToEachOther(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void gatePlacesAndKeepsItsConfiguration(GameTestHelper helper) { GateTests.gatePlacesAndKeepsItsConfiguration(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 60)
    public static void gateTurnsRedstoneInputIntoSignal(GameTestHelper helper) { GateTests.gateTurnsRedstoneInputIntoSignal(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void gateGroupsPairsByLogic(GameTestHelper helper) { GateTests.gateGroupsPairsByLogic(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void gateCopierCopiesPastesAndClears(GameTestHelper helper) { GateTests.gateCopierCopiesPastesAndClears(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void gateMenuOnlyAcceptsWhatTheGateCanUse(GameTestHelper helper) { GateTests.gateMenuOnlyAcceptsWhatTheGateCanUse(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void gateTurnsIronPipeAndReadsInventories(GameTestHelper helper) { GateTests.gateTurnsIronPipeAndReadsInventories(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void gateAndWrenchSetKinesisPowerLimit(GameTestHelper helper) { GateTests.gateAndWrenchSetKinesisPowerLimit(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void wirePlacesJoinsAndSaves(GameTestHelper helper) { WireTests.wirePlacesJoinsAndSaves(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void wireBreaksOffAndDrops(GameTestHelper helper) { WireTests.wireBreaksOffAndDrops(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 60)
    public static void pipeSignalCarriesAGateSignal(GameTestHelper helper) { WireTests.pipeSignalCarriesAGateSignal(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void wireSystemsFollowPluggables(GameTestHelper helper) { WireTests.wireSystemsFollowPluggables(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void lensPlacesSyncsAndSaves(GameTestHelper helper) { LensTests.lensPlacesSyncsAndSaves(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 100)
    public static void lensesPaintAndFiltersSort(GameTestHelper helper) { LensTests.lensesPaintAndFiltersSort(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 100)
    public static void neighbourFilterSteersAndClearLensWashes(GameTestHelper helper) { LensTests.neighbourFilterSteersAndClearLensWashes(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 100)
    public static void diamondPipeSplitKeepsFilterPriority(GameTestHelper helper) { LensTests.diamondPipeSplitKeepsFilterPriority(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void pulsarAndTimerPlaceSaveAndSync(GameTestHelper helper) { PulsarTimerTests.pulsarAndTimerPlaceSaveAndSync(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void pulsarPulsesEveryTwentyTicks(GameTestHelper helper) { PulsarTimerTests.pulsarPulsesEveryTwentyTicks(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void singlePulseRetriesUntilThePipeCanTakeIt(GameTestHelper helper) { PulsarTimerTests.singlePulseRetriesUntilThePipeCanTakeIt(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 1000)
    public static void timerTriggersAndPulsarActions(GameTestHelper helper) { PulsarTimerTests.timerTriggersAndPulsarActions(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void lightSensorPlacesAndReadsLight(GameTestHelper helper) { SensorControlTests.lightSensorPlacesAndReadsLight(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void machineControlOfferedOnlyForTheFiller(GameTestHelper helper) { SensorControlTests.machineControlOfferedOnlyForTheFiller(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void gateSetsFillerPatternAndSkipsFirstParameter(GameTestHelper helper) { SensorControlTests.gateSetsFillerPatternAndSkipsFirstParameter(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 200)
    public static void gateTurnsTheFillerOffAndOn(GameTestHelper helper) { SensorControlTests.gateTurnsTheFillerOffAndOn(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void fillerControlModeSurvivesSaving(GameTestHelper helper) { SensorControlTests.fillerControlModeSurvivesSaving(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void advancedCraftingTableCraftsBlueprint(GameTestHelper helper) { SiliconTests.advancedCraftingTableCraftsBlueprint(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void integrationTableIntegratesAroundCentre(GameTestHelper helper) { SiliconTests.integrationTableIntegratesAroundCentre(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void facadeRules(GameTestHelper helper) { FacadeTests.facadeRules(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void assemblyAndSwap(GameTestHelper helper) { FacadeTests.assemblyAndSwap(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void fullFacadeBlocksAndHollowStaysOpen(GameTestHelper helper) { FacadeTests.fullFacadeBlocksAndHollowStaysOpen(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void facadeQuadsStayInTheShell(GameTestHelper helper) { FacadeTests.facadeQuadsStayInTheShell(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void zonePlanStoresColumns(GameTestHelper helper) { ZonePlannerTests.zonePlanStoresColumns(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void mapLocationRecordsAndCleans(GameTestHelper helper) { ZonePlannerTests.mapLocationRecordsAndCleans(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void zonePlannerImportsAndExports(GameTestHelper helper) { ZonePlannerTests.zonePlannerImportsAndExports(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 40)
    public static void zonePlannerMenuRejectsAndDrops(GameTestHelper helper) { ZonePlannerTests.zonePlannerMenuRejectsAndDrops(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void zonePlannerMapColoursTheTopBlock(GameTestHelper helper) { ZonePlannerTests.zonePlannerMapColoursTheTopBlock(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void paintbrushAndZonePlannerRecipes(GameTestHelper helper) { ZonePlannerTests.paintbrushAndZonePlannerRecipes(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 200)
    public static void lapisPipePaintsItemsAndWrenchCyclesColour(GameTestHelper helper) { SortingPipeTests.lapisPipePaintsItemsAndWrenchCyclesColour(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 200)
    public static void daizuliPipeRoutesItsColourToItsFace(GameTestHelper helper) { SortingPipeTests.daizuliPipeRoutesItsColourToItsFace(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 200)
    public static void obsidianPipeSucksItemsInRange(GameTestHelper helper) { SortingPipeTests.obsidianPipeSucksItemsInRange(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 200)
    public static void diamondWoodPipeExtractsByFilterMode(GameTestHelper helper) { ExtractionPipeTests.diamondWoodPipeExtractsByFilterMode(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void diamondWoodFluidPipeHasNoRoundRobin(GameTestHelper helper) { ExtractionPipeTests.diamondWoodFluidPipeHasNoRoundRobin(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 400)
    public static void emzuliPipeExtractsPerPresetAndPaints(GameTestHelper helper) { ExtractionPipeTests.emzuliPipeExtractsPerPresetAndPaints(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 400)
    public static void stripesPipePlacesBreaksAndDrops(GameTestHelper helper) { StripesPipeTests.stripesPipePlacesBreaksAndDrops(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 100)
    public static void stripesPipeTurnsByGate(GameTestHelper helper) { StripesPipeTests.stripesPipeTurnsByGate(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 300)
    public static void emzuliPipeMovesOnFromClearedFilter(GameTestHelper helper) { ExtractionPipeTests.emzuliPipeMovesOnFromClearedFilter(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 200)
    public static void stripesPipeLaysTwoPipesInOneTick(GameTestHelper helper) { StripesPipeTests.stripesPipeLaysTwoPipesInOneTick(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 400)
    public static void stripesPipeRetractsTwiceInOneTick(GameTestHelper helper) { StripesPipeTests.stripesPipeRetractsTwiceInOneTick(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void recipesAndTags(GameTestHelper helper) { BCTests.recipesAndTags(helper); }
}
