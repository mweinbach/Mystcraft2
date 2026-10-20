package ports.buildcraft.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.buildcraft.BuildCraft;

/** The shared {@link BCTests} and {@link FillerTests} run on Forge. */
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

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 400)
    public static void kinesisPipesCarryEnginePower(GameTestHelper helper) { BCTests.kinesisPipesCarryEnginePower(helper); }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void recipesAndTags(GameTestHelper helper) { BCTests.recipesAndTags(helper); }
}
