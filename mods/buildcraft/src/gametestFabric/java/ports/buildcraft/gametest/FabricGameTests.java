package ports.buildcraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import ports.buildcraft.BuildCraft;

/** The shared {@link BCTests}, {@link FillerTests}, {@link ArchitectTests} and {@link BuilderTests} run on Fabric, with the same timeouts as the Forge wrappers. */
public final class FabricGameTests implements FabricGameTest {
    /** Fabric uses the template name verbatim and reads {@code data/buildcraft/gametest/structures/arena.snbt}. */
    static final String TEMPLATE = BuildCraft.MOD_ID + ":" + BCTests.TEMPLATE;

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

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public void kinesisPipesCarryEnginePower(GameTestHelper helper) { BCTests.kinesisPipesCarryEnginePower(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public void laserPowersTablesInSight(GameTestHelper helper) { SiliconTests.laserPowersTablesInSight(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void assemblyTableMakesSavedChipsets(GameTestHelper helper) { SiliconTests.assemblyTableMakesSavedChipsets(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void assemblyTablePagesRecipesAndDropsStaleOutputs(GameTestHelper helper) {
        SiliconTests.assemblyTablePagesRecipesAndDropsStaleOutputs(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void advancedCraftingTableCraftsBlueprint(GameTestHelper helper) { SiliconTests.advancedCraftingTableCraftsBlueprint(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public void integrationTableIntegratesAroundCentre(GameTestHelper helper) { SiliconTests.integrationTableIntegratesAroundCentre(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void recipesAndTags(GameTestHelper helper) { BCTests.recipesAndTags(helper); }
}
