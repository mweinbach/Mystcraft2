/*
 * Mekanism 1.19.2 port - Forge-only in-world tests.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;
import static mekanism.gametest.CraftTweakerGameTests.EXPECTED;
import static mekanism.gametest.CraftTweakerGameTests.expectAdded;

import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.GasStack;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.registries.MekanismFluids;
import mekanism.common.registries.MekanismGases;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/**
 * The parts of the CraftTweaker integration that need CraftTweaker's fluid stacks or the content loader's Forge hook,
 * which CraftTweaker for Fabric 1.19.2 does not have (see {@code parity/19-crafttweaker.md}). The scripts are
 * {@code src/gametestForge/crafttweaker/*.zs}.
 */
public class ForgeCraftTweakerGameTests {

    /**
     * The evaporating manager and the rotary manager's gas-to-fluid overloads take or return CraftTweaker fluid stacks.
     */
    @GameTest(template = TEMPLATE)
    public static void fluidStackOverloadsAddRecipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FluidStack tenEthene = new FluidStack(MekanismFluids.ETHENE.getStillFluid(), 10);
        var evaporating = MekanismRecipeType.EVAPORATING.getInputCache().findFirstRecipe(level, tenEthene);
        if (expectAdded(evaporating, "evaporating")) {
            FluidStack oxygen = evaporating.getOutput(tenEthene);
            check(oxygen.getFluid() == MekanismFluids.OXYGEN.getStillFluid() && oxygen.getAmount() == 1, "The evaporating recipe gives " + oxygen.getAmount() + " " + oxygen.getFluid());
        }

        GasStack antimatter = new GasStack(MekanismGases.ANTIMATTER.get(), 1);
        var condensing = MekanismRecipeType.ROTARY.getInputCache().findFirstRecipe(level, antimatter);
        if (expectAdded(condensing, "rotary_condensing")) {
            check(condensing.hasGasToFluid() && !condensing.hasFluidToGas(), "The rotary recipe is not gas to fluid only");
            FluidStack lava = condensing.getFluidOutput(antimatter);
            check(lava.getFluid() == Fluids.LAVA && lava.getAmount() == 1000, "The rotary recipe gives " + lava.getAmount() + " " + lava.getFluid());
        }
        helper.succeed();
    }

    /**
     * {@code #loader mekanismcontent} scripts run inside Mekanism's first registry event and register their chemicals in
     * CraftTweaker's namespace.
     */
    @GameTest(template = TEMPLATE)
    public static void contentScriptsRegisterChemicals(GameTestHelper helper) {
        ResourceLocation gas = new ResourceLocation("crafttweaker", "gametest_gas");
        ResourceLocation slurry = new ResourceLocation("crafttweaker", "gametest_slurry");
        boolean hasGas = MekanismAPI.gasRegistry().containsKey(gas);
        boolean hasSlurry = MekanismAPI.slurryRegistry().containsKey(slurry);
        check(hasGas == EXPECTED, "Gas " + gas + " registered is " + hasGas + ", expected " + EXPECTED);
        check(hasSlurry == EXPECTED, "Slurry " + slurry + " registered is " + hasSlurry + ", expected " + EXPECTED);
        if (EXPECTED) {
            int color = MekanismAPI.gasRegistry().getValue(gas).getTint() & 0xFFFFFF;
            check(color == 0x3366CC, "The scripted gas has tint " + Integer.toHexString(color));
        }
        helper.succeed();
    }
}
