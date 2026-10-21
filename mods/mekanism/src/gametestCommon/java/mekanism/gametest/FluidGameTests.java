/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import mekanism.common.registration.impl.FluidRegistryObject;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Mekanism's fluids place as source blocks, and structural glass asks for the fluid overlay those blocks draw against.
 */
public class FluidGameTests {

    @GameTest(template = TEMPLATE)
    public static void mekanismFluidsPlaceAsSources(GameTestHelper helper) {
        int index = 0;
        for (FluidRegistryObject<?, ?, ?, ?, ?> fluid : MekanismFluids.FLUIDS.getAllFluids()) {
            BlockPos pos = new BlockPos(index % 6, 1, index / 6);
            helper.setBlock(pos, fluid.getBlock());
            FluidState state = helper.getBlockState(pos).getFluidState();
            check(state.isSource() && state.getType() == fluid.getStillFluid(), fluid.getStillFluid() + " placed as " + state);
            index++;
        }
        check(index > 0, "Mekanism registered no fluids");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void structuralGlassRequestsFluidOverlay(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockState state = MekanismBlocks.STRUCTURAL_GLASS.getBlock().defaultBlockState();
        helper.setBlock(pos, state);
        boolean overlay = MekanismBlocks.STRUCTURAL_GLASS.getBlock().shouldDisplayFluidOverlay(state, helper.getLevel(), helper.absolutePos(pos),
              MekanismFluids.BRINE.getStillFluid().defaultFluidState());
        check(overlay, "Structural glass does not request the fluid overlay");
        helper.succeed();
    }
}
