/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.extremereactors.platform.Platform;

import java.util.List;

/**
 * The Forge entry point of the shared game tests in {@link ExtremeReactorsGameTests}.
 */
@GameTestHolder(Platform.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExtremeReactorsForgeGameTests {

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID,
            timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public static void basicReactorAssembles(final GameTestHelper helper) {
        ExtremeReactorsGameTests.basicReactorAssembles(helper);
    }

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID,
            timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public static void reactorWithoutControllerDoesNotAssemble(final GameTestHelper helper) {
        ExtremeReactorsGameTests.reactorWithoutControllerDoesNotAssemble(helper);
    }

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID,
            timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public static void reactorFuelRodNeedsCasingBase(final GameTestHelper helper) {
        ExtremeReactorsGameTests.reactorFuelRodNeedsCasingBase(helper);
    }

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID,
            timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public static void basicTurbineAssembles(final GameTestHelper helper) {
        ExtremeReactorsGameTests.basicTurbineAssembles(helper);
    }

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID,
            timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public static void turbineLooseBladeDoesNotAssemble(final GameTestHelper helper) {
        ExtremeReactorsGameTests.turbineLooseBladeDoesNotAssemble(helper);
    }

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID,
            timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public static void turbineCoilsBeforeBladesAssembleLikeUpstream(final GameTestHelper helper) {
        ExtremeReactorsGameTests.turbineCoilsBeforeBladesAssembleLikeUpstream(helper);
    }

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID,
            timeoutTicks = ExtremeReactorsGameTests.BOILING_TIMEOUT)
    public static void passiveReactorBurnsYellorium(final GameTestHelper helper) {
        ExtremeReactorsGameTests.passiveReactorBurnsYellorium(helper);
    }

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID,
            timeoutTicks = ExtremeReactorsGameTests.SIMULATION_TIMEOUT)
    public static void controlRodsReduceOutput(final GameTestHelper helper) {
        ExtremeReactorsGameTests.controlRodsReduceOutput(helper);
    }

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID,
            timeoutTicks = ExtremeReactorsGameTests.SIMULATION_TIMEOUT)
    public static void graphiteModeratesBetterThanAir(final GameTestHelper helper) {
        ExtremeReactorsGameTests.graphiteModeratesBetterThanAir(helper);
    }

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID,
            timeoutTicks = ExtremeReactorsGameTests.BOILING_TIMEOUT)
    public static void activeReactorBoilsWater(final GameTestHelper helper) {
        ExtremeReactorsGameTests.activeReactorBoilsWater(helper);
    }

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID,
            timeoutTicks = ExtremeReactorsGameTests.SIMULATION_TIMEOUT)
    public static void turbineSpinsOnSteam(final GameTestHelper helper) {
        ExtremeReactorsGameTests.turbineSpinsOnSteam(helper);
    }

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID,
            timeoutTicks = ExtremeReactorsGameTests.SIMULATION_TIMEOUT)
    public static void dataPacketsUpdateAnAssembledTurbine(final GameTestHelper helper) {
        ExtremeReactorsGameTests.dataPacketsUpdateAnAssembledTurbine(helper);
    }

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID,
            timeoutTicks = ExtremeReactorsGameTests.SIMULATION_TIMEOUT)
    public static void reactorItemsAndEnergyThroughLoaderApis(final GameTestHelper helper) {
        ExtremeReactorsGameTests.reactorItemsAndEnergyThroughLoaderApis(helper, ForgeLoaderApis.INSTANCE);
    }

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID,
            timeoutTicks = ExtremeReactorsGameTests.SIMULATION_TIMEOUT)
    public static void reactorCoolantThroughLoaderApis(final GameTestHelper helper) {
        ExtremeReactorsGameTests.reactorCoolantThroughLoaderApis(helper, ForgeLoaderApis.INSTANCE);
    }

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID,
            timeoutTicks = ExtremeReactorsGameTests.SIMULATION_TIMEOUT)
    public static void computerPortControlsTheReactor(final GameTestHelper helper) {
        ExtremeReactorsGameTests.computerPortControlsTheReactor(helper);
    }

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID)
    public static void recipeConditionsAndTags(final GameTestHelper helper) {
        ExtremeReactorsGameTests.recipeConditionsAndTags(helper);
    }

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID)
    public static void oreFeaturesPlaceAtUpstreamHeights(final GameTestHelper helper) {
        ExtremeReactorsGameTests.oreFeaturesPlaceAtUpstreamHeights(helper);
    }

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID)
    public static void oreGenerationFollowsConfig(final GameTestHelper helper) {
        ExtremeReactorsGameTests.oreGenerationFollowsConfig(helper);
    }

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID)
    public static void everyItemHasAnEmcDecision(final GameTestHelper helper) {

        final List<String> failures = EmcAudit.run(helper.getLevel().getServer(), Platform.MOD_ID);

        if (!failures.isEmpty()) {
            helper.fail(EmcAudit.summary(failures));
        }

        helper.succeed();
    }

    private ExtremeReactorsForgeGameTests() {
    }
}
