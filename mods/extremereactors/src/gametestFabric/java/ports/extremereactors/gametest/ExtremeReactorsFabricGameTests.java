/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import ports.extremereactors.platform.Platform;

import java.util.List;

/**
 * The Fabric entry point of the shared game tests in {@link ExtremeReactorsGameTests}.
 */
public final class ExtremeReactorsFabricGameTests implements FabricGameTest {

    private static final String EMPTY = Platform.MOD_ID + ":" + ExtremeReactorsGameTests.EMPTY_TEMPLATE;

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public void basicReactorAssembles(final GameTestHelper helper) {
        ExtremeReactorsGameTests.basicReactorAssembles(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public void reactorWithoutControllerDoesNotAssemble(final GameTestHelper helper) {
        ExtremeReactorsGameTests.reactorWithoutControllerDoesNotAssemble(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public void reactorFuelRodNeedsCasingBase(final GameTestHelper helper) {
        ExtremeReactorsGameTests.reactorFuelRodNeedsCasingBase(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public void basicTurbineAssembles(final GameTestHelper helper) {
        ExtremeReactorsGameTests.basicTurbineAssembles(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public void turbineLooseBladeDoesNotAssemble(final GameTestHelper helper) {
        ExtremeReactorsGameTests.turbineLooseBladeDoesNotAssemble(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public void turbineCoilsBeforeBladesAssembleLikeUpstream(final GameTestHelper helper) {
        ExtremeReactorsGameTests.turbineCoilsBeforeBladesAssembleLikeUpstream(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.BOILING_TIMEOUT)
    public void passiveReactorBurnsYellorium(final GameTestHelper helper) {
        ExtremeReactorsGameTests.passiveReactorBurnsYellorium(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SIMULATION_TIMEOUT)
    public void controlRodsReduceOutput(final GameTestHelper helper) {
        ExtremeReactorsGameTests.controlRodsReduceOutput(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SIMULATION_TIMEOUT)
    public void graphiteModeratesBetterThanAir(final GameTestHelper helper) {
        ExtremeReactorsGameTests.graphiteModeratesBetterThanAir(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.BOILING_TIMEOUT)
    public void activeReactorBoilsWater(final GameTestHelper helper) {
        ExtremeReactorsGameTests.activeReactorBoilsWater(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SIMULATION_TIMEOUT)
    public void turbineSpinsOnSteam(final GameTestHelper helper) {
        ExtremeReactorsGameTests.turbineSpinsOnSteam(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SIMULATION_TIMEOUT)
    public void dataPacketsUpdateAnAssembledTurbine(final GameTestHelper helper) {
        ExtremeReactorsGameTests.dataPacketsUpdateAnAssembledTurbine(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SIMULATION_TIMEOUT)
    public void reactorItemsAndEnergyThroughLoaderApis(final GameTestHelper helper) {
        ExtremeReactorsGameTests.reactorItemsAndEnergyThroughLoaderApis(helper, FabricLoaderApis.INSTANCE);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SIMULATION_TIMEOUT)
    public void reactorCoolantThroughLoaderApis(final GameTestHelper helper) {
        ExtremeReactorsGameTests.reactorCoolantThroughLoaderApis(helper, FabricLoaderApis.INSTANCE);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.BOILING_TIMEOUT)
    public void reactorMekanismPortFollowsMekanism(final GameTestHelper helper) {
        ExtremeReactorsGameTests.reactorMekanismPortFollowsMekanism(helper, FabricLoaderApis.INSTANCE);
    }

    @GameTest(template = EMPTY)
    public void fluidBucketsThroughLoaderApis(final GameTestHelper helper) {
        ExtremeReactorsGameTests.fluidBucketsThroughLoaderApis(helper, FabricLoaderApis.INSTANCE);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SIMULATION_TIMEOUT)
    public void computerPortControlsTheReactor(final GameTestHelper helper) {
        ExtremeReactorsGameTests.computerPortControlsTheReactor(helper);
    }

    @GameTest(template = EMPTY)
    public void recipeConditionsAndTags(final GameTestHelper helper) {
        ExtremeReactorsGameTests.recipeConditionsAndTags(helper);
    }

    @GameTest(template = EMPTY)
    public void oreFeaturesPlaceAtUpstreamHeights(final GameTestHelper helper) {
        ExtremeReactorsGameTests.oreFeaturesPlaceAtUpstreamHeights(helper);
    }

    @GameTest(template = EMPTY)
    public void oreGenerationFollowsConfig(final GameTestHelper helper) {
        ExtremeReactorsGameTests.oreGenerationFollowsConfig(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public void swimmableFluidDrowns(final GameTestHelper helper) {
        ExtremeReactorsGameTests.swimmableFluidDrowns(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public void nonSwimmableFluidDoesNotDrown(final GameTestHelper helper) {
        ExtremeReactorsGameTests.nonSwimmableFluidDoesNotDrown(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public void swimmableFluidSetsTheSwimmingPose(final GameTestHelper helper) {
        ExtremeReactorsGameTests.swimmableFluidSetsTheSwimmingPose(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public void nonSwimmableFluidDoesNotSetTheSwimmingPose(final GameTestHelper helper) {
        ExtremeReactorsGameTests.nonSwimmableFluidDoesNotSetTheSwimmingPose(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public void swimmableFluidSupportsBoats(final GameTestHelper helper) {
        ExtremeReactorsGameTests.swimmableFluidSupportsBoats(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public void fluidPathTypesMatchForge(final GameTestHelper helper) {
        ExtremeReactorsGameTests.fluidPathTypesMatchForge(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public void swimmableFluidPushesEntities(final GameTestHelper helper) {
        ExtremeReactorsGameTests.swimmableFluidPushesEntities(helper);
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public void nonSwimmableFluidDoesNotPushEntities(final GameTestHelper helper) {
        ExtremeReactorsGameTests.nonSwimmableFluidDoesNotPushEntities(helper);
    }

    @GameTest(template = EMPTY)
    public void everyBlockWithAnItemHasALootTable(final GameTestHelper helper) {
        ExtremeReactorsGameTests.everyBlockWithAnItemHasALootTable(helper);
    }

    @GameTest(template = EMPTY)
    public void everyItemHasAnEmcDecision(final GameTestHelper helper) {

        final List<String> failures = EmcAudit.run(helper.getLevel().getServer(), Platform.MOD_ID);

        if (!failures.isEmpty()) {
            helper.fail(EmcAudit.summary(failures));
        }

        helper.succeed();
    }
}
