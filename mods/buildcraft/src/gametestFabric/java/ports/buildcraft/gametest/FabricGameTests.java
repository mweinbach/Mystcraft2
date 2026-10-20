package ports.buildcraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import ports.buildcraft.BuildCraft;

/** The shared {@link BCTests} run on Fabric, with the same timeouts as the Forge wrappers. */
public final class FabricGameTests implements FabricGameTest {
    /** Fabric uses the template name verbatim and reads {@code data/buildcraft/gametest/structures/arena.snbt}. */
    static final String TEMPLATE = BuildCraft.MOD_ID + ":" + BCTests.TEMPLATE;

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void mjConversionRatio(GameTestHelper helper) { BCTests.mjConversionRatio(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void fluidViscosityScale(GameTestHelper helper) { BCTests.fluidViscosityScale(helper); }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void guiTanksTradeContainers(GameTestHelper helper) { BCTests.guiTanksTradeContainers(helper); }

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
    public void oilWellsAreRareAndDeterministic(GameTestHelper helper) { BCTests.oilWellsAreRareAndDeterministic(helper); }

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

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public void recipesAndTags(GameTestHelper helper) { BCTests.recipesAndTags(helper); }
}
