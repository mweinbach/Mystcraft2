package ports.buildcraft.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.buildcraft.BuildCraft;

/** The shared {@link BCTests} run on Forge. */
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
    public static void oilWellsAreRareAndDeterministic(GameTestHelper helper) { BCTests.oilWellsAreRareAndDeterministic(helper); }

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
